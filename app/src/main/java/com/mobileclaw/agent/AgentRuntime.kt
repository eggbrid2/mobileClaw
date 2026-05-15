package com.mobileclaw.agent

import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.memory.WorkingMemory
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillRegistry
import com.mobileclaw.skill.SkillResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

/**
 * Core ReAct loop: Reason → Act → Observe → repeat until done or exhausted.
 */
class AgentRuntime(
    private val llm: LlmGateway,
    private val registry: SkillRegistry,
    private val semanticMemory: SemanticMemory? = null,
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events
    private val loopGuard = LoopGuard()

    suspend fun run(
        goal: String,
        taskType: TaskType = TaskType.GENERAL,
        priorContext: String = "",
        episodicContext: String = "",
        language: String = "auto",
        imageBase64: String? = null,
        role: Role? = null,
        userProfileContext: String = "",
        onToken: ((String) -> Unit)? = null,
        onThinkToken: ((String) -> Unit)? = null,
    ): AgentResult {
        val taskSession = TaskSession(goal = goal, type = taskType)
        val ctx = AgentContext(
            taskId = taskSession.id,
            goal = goal,
            maxSteps = taskSession.maxSteps,
            imageBase64 = imageBase64,
        )
        val workingMemory = WorkingMemory()
        emit(AgentEvent.Started(ctx.taskId, goal))

        // Build once per task — these don't change across steps
        val injectedSkills = TaskToolPolicy.select(registry, taskType, role?.forcedSkillIds.orEmpty())
        val tools = injectedSkills.toToolDefinitions()
        val semanticContext = runCatching { semanticMemory?.toPromptContext() ?: "" }.getOrDefault("")
        val taskPlan = TaskPlanner.plan(
            llm = llm,
            goal = goal,
            taskType = taskType,
            language = language,
            priorContext = priorContext,
        )
        emit(AgentEvent.PlanCreated(taskPlan))
        val systemPrompt = buildSystemPrompt(
            skills = injectedSkills,
            priorContext = priorContext,
            episodicContext = episodicContext,
            semanticContext = semanticContext,
            language = language,
            role = role,
            userProfileContext = userProfileContext,
            taskType = taskType,
            taskPlan = taskPlan,
        )

        var completedSegments = 0
        var lastReviewActionCount = 0
        while (completedSegments < MAX_SEGMENTS) {
            val segmentActionStart = actionStepCount(ctx.steps)

            while (actionStepCount(ctx.steps) - segmentActionStart < ctx.maxSteps) {
                if (loopGuard.check(ctx.steps)) {
                    val reflectionStep = AgentStep(
                        index = ctx.steps.size,
                        thought = "Repeated action reflection",
                        toolCallId = null,
                        skillId = null,
                        skillParams = null,
                        observation = """
                            Reflection checkpoint:
                            The last ${loopGuard.windowSize} actions used the same tool with the same parameters. Do not stop the task.
                            Analyze why the repeated operation did not make progress, then choose a different strategy, different parameters, a different tool, or finish if the goal is already complete.
                        """.trimIndent(),
                    )
                    ctx.steps.add(reflectionStep)
                    workingMemory.push(reflectionStep)
                    emit(AgentEvent.ThinkingComplete("检测到重复操作，正在反思并切换策略。"))
                    if (actionStepCount(ctx.steps) - segmentActionStart >= ctx.maxSteps) break
                }

                val messages = ctx.toMessages(systemPrompt, workingMemory.steps())

                emit(AgentEvent.Thinking)

                val response = runCatching {
                    llm.chat(ChatRequest(
                        messages = messages,
                        tools = tools,
                        stream = onToken != null || onThinkToken != null,
                        onToken = onToken,
                        onThinkToken = onThinkToken,
                    ))
                }.getOrElse { e ->
                    val msg = "LLM error: ${e.message}"
                    emit(AgentEvent.Error(msg))
                    return AgentResult(success = false, summary = msg, context = ctx)
                }

                // No tool call → task complete
                if (response.toolCall == null) {
                    val summary = response.content ?: "Task completed."
                    emit(AgentEvent.Completed(summary))
                    return AgentResult(success = true, summary = summary, context = ctx)
                }

                // Emit the thought text so the UI can display it before the action
                val thought = response.content?.trim() ?: ""
                if (thought.isNotBlank()) emit(AgentEvent.ThinkingComplete(thought))

                val tc = response.toolCall
                emit(AgentEvent.SkillCalling(tc.skillId, tc.params))

                val skill = registry.get(tc.skillId)
                val skillResult = repeatedPerceptionResult(ctx.steps, tc.skillId)
                    ?: if (skill == null) {
                        SkillResult(success = false, output = "Error: skill '${tc.skillId}' not found.")
                    } else {
                        runCatching { skill.execute(tc.params) }
                            .getOrElse { e -> SkillResult(success = false, output = "Error executing ${tc.skillId}: ${e.message}") }
                    }

                emit(AgentEvent.Observation(
                    text = skillResult.output,
                    imageBase64 = skillResult.imageBase64,
                    attachment = skillResult.data as? SkillAttachment,
                ))

                val truncatedObservation = skillResult.output.let {
                    if (it.length > 4000) it.take(4000) + "\n…[truncated ${it.length - 4000} chars]" else it
                }
                val step = AgentStep(
                    index = ctx.steps.size,
                    thought = response.content ?: "",
                    toolCallId = tc.id,
                    skillId = tc.skillId,
                    skillParams = tc.params,
                    observation = truncatedObservation,
                    isError = !skillResult.success,
                    imageBase64 = skillResult.imageBase64,
                )
                ctx.steps.add(step)
                workingMemory.push(step)

                val actionCount = actionStepCount(ctx.steps)
                val segmentActionCount = actionCount - segmentActionStart
                if (
                    actionCount > 0 &&
                    actionCount % REVIEW_INTERVAL_STEPS == 0 &&
                    actionCount != lastReviewActionCount &&
                    segmentActionCount < ctx.maxSteps
                ) {
                    lastReviewActionCount = actionCount
                    val review = createFiveStepReview(
                        systemPrompt = systemPrompt,
                        ctx = ctx,
                        workingMemory = workingMemory,
                    )
                    val reviewStep = AgentStep(
                        index = ctx.steps.size,
                        thought = "5-step execution review",
                        toolCallId = null,
                        skillId = null,
                        skillParams = null,
                        observation = review,
                    )
                    ctx.steps.add(reviewStep)
                    workingMemory.push(reviewStep)
                    emit(AgentEvent.ThinkingComplete("已完成 5 步复盘，检查目标满足度并调整下一步。"))
                }
            }

            completedSegments += 1
            if (completedSegments < MAX_SEGMENTS) {
                val checkpoint = createContinuationCheckpoint(
                    systemPrompt = systemPrompt,
                    ctx = ctx,
                    workingMemory = workingMemory,
                )
                val checkpointStep = AgentStep(
                    index = ctx.steps.size,
                    thought = "20-step continuation checkpoint",
                    toolCallId = null,
                    skillId = null,
                    skillParams = null,
                    observation = checkpoint,
                )
                ctx.steps.add(checkpointStep)
                workingMemory.push(checkpointStep)
                emit(AgentEvent.ThinkingComplete("已完成 20 步检查点，整理进展并继续。"))
            }
        }

        val finalSummary = runCatching {
            llm.chat(ChatRequest(
                messages = ctx.toMessages(
                    systemPrompt + "\n\nYou have reached the internal action budget. Do not call tools. Summarize the useful progress and current state for the user, and say what remains only if it is genuinely unresolved.",
                    workingMemory.steps(),
                ),
                tools = emptyList(),
                stream = false,
            )).content.orEmpty()
        }.getOrDefault("")
            .ifBlank { "已完成当前可执行步骤。" }
        emit(AgentEvent.Completed(finalSummary))
        return AgentResult(success = true, summary = finalSummary, context = ctx)
    }

    private suspend fun emit(event: AgentEvent) = _events.emit(event)

    private suspend fun createFiveStepReview(
        systemPrompt: String,
        ctx: AgentContext,
        workingMemory: WorkingMemory,
    ): String {
        val review = runCatching {
            llm.chat(ChatRequest(
                messages = ctx.toMessages(
                    systemPrompt + """

You are at an internal 5-action execution review. Do not call tools and do not answer the user.
Review the actual executed tool steps against the original task plan and user goal.
Output a concise internal note with:
1. Done: which todo items or success criteria are already satisfied.
2. Errors: failed calls, wrong assumptions, repeated actions, missing context, or signs of being off-track.
3. Correction: what to change to avoid repeating mistakes.
4. Next: the next 2-4 concrete tool actions or final-answer condition.
This note is for your own next step, so be direct and operational.
                    """.trimIndent(),
                    workingMemory.steps(),
                ),
                tools = emptyList(),
                stream = false,
            )).content.orEmpty()
        }.getOrDefault("")

        return review.ifBlank {
            "5-step review: compare the latest tool results with the user goal, avoid repeating failed actions, and choose the next concrete action that closes the remaining gap."
        }
    }

    private suspend fun createContinuationCheckpoint(
        systemPrompt: String,
        ctx: AgentContext,
        workingMemory: WorkingMemory,
    ): String {
        val summary = runCatching {
            llm.chat(ChatRequest(
                messages = ctx.toMessages(
                    systemPrompt + "\n\nYou are at an internal 20-step checkpoint. Do not call tools. Summarize the useful progress, current screen/state, blockers if any, and write a direct self-instruction for the next segment. This is internal context for yourself, not a final answer to the user.",
                    workingMemory.steps(),
                ),
                tools = emptyList(),
                stream = false,
            )).content.orEmpty()
        }.getOrDefault("")

        return summary.ifBlank {
            "20-step checkpoint: continue the same user task from the latest observation. Avoid repeating failed actions; choose the next concrete action based on current state."
        }
    }

    private fun actionStepCount(steps: List<AgentStep>): Int = steps.count { it.skillId != null }

    private fun repeatedPerceptionResult(steps: List<AgentStep>, skillId: String): SkillResult? {
        if (skillId !in perceptionSkillIds) return null
        val last = steps.lastOrNull() ?: return null
        if (last.skillId !in perceptionSkillIds) return null
        if (skillId == "screenshot" && last.isError && last.skillId in screenshotFallbackSourceIds) return null
        return SkillResult(
            success = false,
            output = "Repeated screen-reading blocked. You already observed the screen in the previous step. " +
                "Use that observation to take a concrete action now: tap, scroll, input_text, navigate(back/home), " +
                "or finish if the task is complete. Only read the screen again after an action changes the UI.",
        )
    }

    private companion object {
        val perceptionSkillIds = setOf("see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen")
        val screenshotFallbackSourceIds = setOf("see_screen", "read_screen", "bg_read_screen")
        const val MAX_SEGMENTS = 5
        const val REVIEW_INTERVAL_STEPS = 5
    }
}

data class AgentResult(
    val success: Boolean,
    val summary: String,
    val context: AgentContext,
)

sealed class AgentEvent {
    data class Started(val taskId: String, val goal: String) : AgentEvent()
    object Thinking : AgentEvent()
    data class ThinkingToken(val text: String) : AgentEvent()   // streaming reasoning token
    data class SkillCalling(val skillId: String, val params: Map<String, Any>) : AgentEvent()
    data class Observation(
        val text: String,
        val imageBase64: String? = null,
        val attachment: SkillAttachment? = null,
    ) : AgentEvent()
    data class PlanCreated(val plan: TaskPlan) : AgentEvent()
    data class Completed(val summary: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class Token(val text: String) : AgentEvent()
    data class ThinkingComplete(val thought: String) : AgentEvent()
}

private fun List<SkillMeta>.toToolDefinitions(): List<ToolDefinition> = map { skill ->
    ToolDefinition(
        name = skill.id,
        description = skill.description,
        parameters = ToolParameters(
            properties = skill.parameters.associate { p ->
                p.name to ToolProperty(type = p.type, description = p.description)
            },
            required = skill.parameters.filter { it.required }.map { it.name },
        )
    )
}

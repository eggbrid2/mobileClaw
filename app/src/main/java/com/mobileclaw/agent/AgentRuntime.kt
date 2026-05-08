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

        while (!ctx.isExhausted()) {
            if (loopGuard.check(ctx.steps)) {
                val msg = "Loop detected — same action repeated ${loopGuard.windowSize} times. Stopping."
                emit(AgentEvent.Error(msg))
                return AgentResult(success = false, summary = msg, context = ctx)
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
        }

        val msg = "Reached step limit (${ctx.maxSteps}). Task may be incomplete."
        emit(AgentEvent.Error(msg))
        return AgentResult(success = false, summary = msg, context = ctx)
    }

    private suspend fun emit(event: AgentEvent) = _events.emit(event)

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

package com.mobileclaw.agent

import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.memory.MemoryContextBuilder
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.memory.WorkingMemory
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillRegistry
import com.mobileclaw.skill.SkillResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.coroutines.coroutineContext
import java.util.UUID

/**
 * Core ReAct loop: Reason → Act → Observe → repeat until done or exhausted.
 */
class AgentRuntime(
    private val llm: LlmGateway,
    private val registry: SkillRegistry,
    private val semanticMemory: SemanticMemory? = null,
    private val memoryContextBuilder: MemoryContextBuilder? = null,
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
        preferFastLocalVision: Boolean = false,
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
        val foundationalMemoryContext = runCatching {
            memoryContextBuilder?.build(goal, taskType)?.toPrompt().orEmpty()
        }.getOrDefault("")
        val injectedSkills = if (preferFastLocalVision && taskType in setOf(TaskType.CHAT, TaskType.GENERAL)) {
            emptyList()
        } else {
            TaskToolPolicy.select(registry, taskType, role?.forcedSkillIds.orEmpty(), foundationalMemoryContext)
        }
        val tools = injectedSkills.toToolDefinitions()
        val semanticContext = foundationalMemoryContext.ifBlank {
            runCatching { semanticMemory?.toPromptContext() ?: "" }.getOrDefault("")
        }
        val runtimePriorContext = listOf(foundationalMemoryContext, priorContext)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n\n")
        val useFastPhoneLoop = taskType == TaskType.PHONE_CONTROL
        val taskPlan = if (preferFastLocalVision || useFastPhoneLoop) {
            TaskPlanner.fallback(goal, taskType)
        } else {
            TaskPlanner.plan(
                llm = llm,
                goal = goal,
                taskType = taskType,
                language = language,
                priorContext = runtimePriorContext,
            )
        }
        emit(AgentEvent.PlanCreated(taskPlan))
        val systemPrompt = buildSystemPrompt(
            skills = injectedSkills,
            priorContext = runtimePriorContext,
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
        var pendingReview: Deferred<String>? = null
        suspend fun finish(result: AgentResult): AgentResult {
            pendingReview?.cancelAndJoin()
            pendingReview = null
            return result
        }
        while (completedSegments < MAX_SEGMENTS) {
            val segmentActionStart = actionStepCount(ctx.steps)

            while (actionStepCount(ctx.steps) - segmentActionStart < ctx.maxSteps) {
                val readyReview = pendingReview?.takeIf { it.isCompleted }
                if (readyReview != null) {
                    val review = runCatching { readyReview.await() }.getOrDefault("")
                        .ifBlank {
                            "5-step review: compare the latest tool results with the user goal, avoid repeating failed actions, and choose the next concrete action that closes the remaining gap."
                        }
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
                    pendingReview = null
                    emit(AgentEvent.ThinkingComplete("后台复盘已完成，已更新下一步执行上下文。"))
                }

                val deterministicPhoneLaunch = deterministicPhoneLaunchCall(taskType, goal, ctx.steps)
                if (deterministicPhoneLaunch != null) {
                    val (skillId, params, finalAfterSuccess) = deterministicPhoneLaunch
                    emit(AgentEvent.SkillCalling(skillId, params))
                    val skillResult = registry.get(skillId)
                        ?.let { skill ->
                            runCatching { skill.execute(params) }
                                .getOrElse { e -> SkillResult(false, "Error executing $skillId: ${e.message}") }
                        }
                        ?: SkillResult(false, "Error: skill '$skillId' not found.")
                    emit(AgentEvent.Observation(
                        text = skillResult.output,
                        imageBase64 = skillResult.imageBase64,
                        attachment = skillResult.data as? SkillAttachment,
                    ))
                    val step = AgentStep(
                        index = ctx.steps.size,
                        thought = "Deterministic phone app launch",
                        toolCallId = "deterministic-phone-${ctx.steps.size}",
                        skillId = skillId,
                        skillParams = params,
                        observation = skillResult.output.let {
                            if (it.length > 4000) it.take(4000) + "\n…[truncated ${it.length - 4000} chars]" else it
                        },
                        isError = !skillResult.success,
                        imageBase64 = skillResult.imageBase64,
                    )
                    ctx.steps.add(step)
                    workingMemory.push(step)
                    if (finalAfterSuccess && skillResult.success) {
                        val appName = extractRequestedAppName(goal).orEmpty()
                        val summary = if (appName.isNotBlank()) "已打开$appName。" else "已打开目标应用。"
                        emit(AgentEvent.Completed(summary))
                        return finish(AgentResult(success = true, summary = summary, context = ctx))
                    }
                    if (!skillResult.success && skillResult.data is SkillAttachment.AccessibilityRequest) {
                        val summary = "需要先开启无障碍服务，才能执行打开应用和手机操作。"
                        emit(AgentEvent.Completed(summary))
                        return finish(AgentResult(success = false, summary = summary, context = ctx))
                    }
                    continue
                }

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
                    return finish(AgentResult(success = false, summary = msg, context = ctx))
                }

                // No tool call → task complete
                if (response.toolCall == null) {
                    val summary = response.content ?: "Task completed."
                    emit(AgentEvent.Completed(summary))
                    return finish(AgentResult(success = true, summary = summary, context = ctx))
                }

                // Emit the thought text so the UI can display it before the action
                val thought = response.content?.trim() ?: ""
                if (thought.isNotBlank()) emit(AgentEvent.ThinkingComplete(thought))

                val tc = response.toolCall
                emit(AgentEvent.SkillCalling(tc.skillId, tc.params))

                val skill = registry.get(tc.skillId)
                val skillResult = phoneControlGuardResult(taskType, ctx.steps, tc.skillId)
                    ?: repeatedPerceptionResult(ctx.steps, tc.skillId)
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
                    segmentActionCount < ctx.maxSteps &&
                    pendingReview == null
                ) {
                    lastReviewActionCount = actionCount
                    val reviewJob = if (preferFastLocalVision) {
                        CoroutineScope(coroutineContext).async {
                            "5-step review: compare the latest observation with the user goal, avoid repeated screen reading, and choose the next concrete phone action or final answer."
                        }
                    } else {
                        val reviewSystemPrompt = systemPrompt
                        val reviewContext = ctx.copySnapshot()
                        val reviewMemory = workingMemory.copySnapshot()
                        CoroutineScope(coroutineContext).async {
                            createFiveStepReview(
                                systemPrompt = reviewSystemPrompt,
                                ctx = reviewContext,
                                workingMemory = reviewMemory,
                            )
                        }
                    }
                    pendingReview = reviewJob
                    emit(AgentEvent.ThinkingComplete("已启动后台复盘，当前手机操作继续执行。"))
                }
            }

            completedSegments += 1
            if (completedSegments < MAX_SEGMENTS) {
                val checkpoint = if (preferFastLocalVision || useFastPhoneLoop) {
                    "20-step phone checkpoint: continue from the latest phone state without another planning request. Do not restart; choose the next concrete action or finish if complete."
                } else {
                    createContinuationCheckpoint(
                        systemPrompt = systemPrompt,
                        ctx = ctx,
                        workingMemory = workingMemory,
                    )
                }
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
            pendingReview?.cancelAndJoin()
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
        return finish(AgentResult(success = true, summary = finalSummary, context = ctx))
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

    private fun AgentContext.copySnapshot(): AgentContext =
        copy(steps = steps.map { it.copy() }.toMutableList())

    private fun WorkingMemory.copySnapshot(): WorkingMemory =
        WorkingMemory().also { copy ->
            steps().forEach { copy.push(it.copy()) }
        }

    private data class DeterministicToolCall(
        val skillId: String,
        val params: Map<String, Any>,
        val finalAfterSuccess: Boolean = false,
    )

    private fun deterministicPhoneLaunchCall(
        taskType: TaskType,
        goal: String,
        steps: List<AgentStep>,
    ): DeterministicToolCall? {
        if (taskType != TaskType.PHONE_CONTROL) return null
        val requestedApp = extractRequestedAppName(goal) ?: return null
        if (steps.none { it.skillId == "list_apps" }) {
            return DeterministicToolCall("list_apps", emptyMap())
        }
        if (steps.any { it.skillId == "navigate" && !it.isError }) return null
        val appList = steps.lastOrNull { it.skillId == "list_apps" && !it.isError }?.observation.orEmpty()
        val packageName = resolvePackageNameFromListApps(appList, requestedApp) ?: return null
        return DeterministicToolCall(
            skillId = "navigate",
            params = mapOf("action" to "launch", "package_name" to packageName, "foreground" to true),
            finalAfterSuccess = true,
        )
    }

    private fun extractRequestedAppName(goal: String): String? {
        val text = goal.lineSequence().firstOrNull().orEmpty().trim()
        val match = Regex("""(?:帮我)?(?:打开|启动|开启|进入)\s*([^，。,.!?！？\n]+)""").find(text) ?: return null
        val appName = match.groupValues.getOrNull(1)
            ?.replace(Regex("""\s*(app|APP|应用|软件)$"""), "")
            ?.trim()
            .orEmpty()
        if (appName.isBlank()) return null
        if (appName.contains("网页") || appName.contains("链接") || appName.contains("文件")) return null
        return appName.take(40)
    }

    private fun resolvePackageNameFromListApps(appList: String, requestedApp: String): String? {
        val normalizedRequest = requestedApp.normalizeAppNameForMatch()
        val candidates = appList.lineSequence().mapNotNull { line ->
            val appName = line.substringBefore(':', "").trim()
            val packageName = line.substringAfter(':', "").trim()
            if (appName.isBlank() || packageName.isBlank()) return@mapNotNull null
            val normalizedApp = appName.normalizeAppNameForMatch()
            val score = when {
                normalizedApp == normalizedRequest -> 100
                normalizedApp.contains(normalizedRequest) -> 80
                normalizedRequest.contains(normalizedApp) -> 70
                appName.contains(requestedApp, ignoreCase = true) -> 60
                else -> 0
            }
            if (score <= 0) null else score to packageName
        }.toList()
        return candidates.maxByOrNull { it.first }?.second
    }

    private fun String.normalizeAppNameForMatch(): String =
        lowercase()
            .replace(Regex("""[\s·._-]+"""), "")
            .removeSuffix("app")
            .removeSuffix("应用")
            .removeSuffix("软件")

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

    private fun phoneControlGuardResult(taskType: TaskType, steps: List<AgentStep>, skillId: String): SkillResult? {
        if (taskType != TaskType.PHONE_CONTROL) return null
        if (skillId !in passivePhoneSkillIds) return null

        val lastActionIndex = steps.indexOfLast { it.skillId in uiChangingSkillIds && !it.isError }
        val lastSuccessfulPerceptionIndex = steps.indexOfLast { it.skillId in perceptionSkillIds && !it.isError }
        if (lastSuccessfulPerceptionIndex < 0 || lastSuccessfulPerceptionIndex < lastActionIndex) return null

        val lastStep = steps.lastOrNull()
        if (skillId == "screenshot" && lastStep?.isError == true && lastStep.skillId in screenshotFallbackSourceIds) return null

        val lastObservation = steps.getOrNull(lastSuccessfulPerceptionIndex)?.observation.orEmpty()
        val compactObservation = lastObservation
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(18)
            .joinToString("\n")
            .take(1800)

        return SkillResult(
            success = false,
            output = """
                Passive phone check blocked: the latest successful screen observation has not been followed by a concrete UI-changing action.
                Do not keep checking/opening/looking. Use the current screenshot and coordinate list already in context.
                Next step must be one of: tap, scroll, input_text, long_click, navigate(back/home/launch), or final answer/blocker.

                Latest usable observation:
                $compactObservation
            """.trimIndent(),
        )
    }

    private companion object {
        val perceptionSkillIds = setOf("see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen")
        val screenshotFallbackSourceIds = setOf("see_screen", "read_screen", "bg_read_screen")
        val passivePhoneSkillIds = perceptionSkillIds + setOf("phone_status", "list_apps", "check_permissions")
        val uiChangingSkillIds = setOf("tap", "scroll", "input_text", "long_click", "navigate", "vpn_control")
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

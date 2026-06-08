package com.mobileclaw.agent

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    companion object {
        private const val TAG = "AgentRuntime"
        private val artifactSkillIds = setOf("app_manager", "ui_builder")
        private val artifactStructuredActions = setOf("create", "update", "validate", "inspect_logs", "inspect_runtime", "analyze_change", "get")
        private val perceptionSkillIds = setOf("see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen")
        private val screenshotFallbackSourceIds = setOf("see_screen", "read_screen", "bg_read_screen")
        private val passivePhoneSkillIds = perceptionSkillIds + setOf("phone_status", "list_apps", "check_permissions")
        private val uiChangingSkillIds = setOf("tap", "scroll", "input_text", "long_click", "navigate", "vpn_control")
        private const val MAX_SEGMENTS = 5
        private const val REVIEW_INTERVAL_STEPS = 5
    }

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events
    private val loopGuard = LoopGuard()

    suspend fun run(
        goal: String,
        taskType: TaskType = TaskType.GENERAL,
        priorContext: String = "",
        episodicContext: String = "",
        executionContext: String = "",
        language: String = "auto",
        imageBase64: String? = null,
        role: Role? = null,
        userProfileContext: String = "",
        allowedToolIds: List<String> = emptyList(),
        preferFastLocalVision: Boolean = false,
        preferFastPlan: Boolean = false,
        onToken: ((String) -> Unit)? = null,
        onThinkToken: ((String) -> Unit)? = null,
        onWorkspaceUpdate: ((AgentWorkspaceUpdate) -> Unit)? = null,
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
        onWorkspaceUpdate?.invoke(
            AgentWorkspaceUpdate(
                stage = "task_started",
                taskType = taskType.name,
                label = "task_started",
                summary = goal.take(240),
                details = "Goal:\n${goal.take(4000)}",
            )
        )

        // Build once per task — these don't change across steps
        val foundationalMemoryContext = try {
            memoryContextBuilder?.build(goal, taskType)?.toPrompt().orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build foundational memory context for taskType=$taskType", t)
            ""
        }
        val injectedSkills = if (preferFastLocalVision && taskType in setOf(TaskType.CHAT, TaskType.GENERAL)) {
            emptyList()
        } else {
            TaskToolPolicy.select(
                registry = registry,
                taskType = taskType,
                goal = goal,
                forcedSkillIds = role?.forcedSkillIds.orEmpty(),
                memoryContext = foundationalMemoryContext,
            ).let { skills ->
                val allowed = allowedToolIds.toSet()
                val forced = role?.forcedSkillIds.orEmpty().toSet()
                if (allowed.isEmpty()) {
                    skills
                } else {
                    skills.filter { it.id in allowed || it.id in forced || !it.isBuiltin }
                        .ifEmpty { skills }
                }
            }
        }
        val tools = injectedSkills.toToolDefinitions()
        val semanticContext = foundationalMemoryContext.ifBlank {
            try {
                semanticMemory?.toPromptContext() ?: ""
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to build semantic prompt context for taskType=$taskType", t)
                ""
            }
        }
        val runtimePriorContext = listOf(foundationalMemoryContext, priorContext)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n\n")
        val useFastPhoneLoop = taskType == TaskType.PHONE_CONTROL
        val artifactPatchContract = parseArtifactPatchContract(goal)
        val taskPlan = if (preferFastPlan || preferFastLocalVision || useFastPhoneLoop) {
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
        onWorkspaceUpdate?.invoke(
            AgentWorkspaceUpdate(
                stage = "plan_created",
                taskType = taskType.name,
                label = "plan_created",
                summary = taskPlan.summary.take(240),
                details = taskPlan.toPrompt().take(4000),
            )
        )
        val systemPrompt = buildSystemPrompt(
            skills = injectedSkills,
            priorContext = runtimePriorContext,
            episodicContext = episodicContext,
            semanticContext = semanticContext,
            executionContext = executionContext,
            language = language,
            role = role,
            userProfileContext = userProfileContext,
            taskType = taskType,
            taskPlan = taskPlan,
        )

        var completedSegments = 0
        var lastReviewActionCount = 0
        var pendingReview: Deferred<String>? = null
        val artifactRepairAttempts = mutableMapOf<String, Int>()
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
                    val review = try {
                        readyReview.await()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Background five-step review failed for taskId=${ctx.taskId}", t)
                        ""
                    }
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
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "review_completed",
                            taskType = taskType.name,
                            label = "review_completed",
                            summary = review.take(240),
                            details = review.take(4000),
                        )
                    )
                }

                val deterministicPhoneLaunch = deterministicPhoneLaunchCall(taskType, goal, ctx.steps)
                if (deterministicPhoneLaunch != null) {
                    val (skillId, params, finalAfterSuccess, deterministicThought) = deterministicPhoneLaunch
                    emit(AgentEvent.SkillCalling(skillId, params))
                    val skillResult = executeSkillWithDiagnostics(
                        taskId = ctx.taskId,
                        taskType = taskType,
                        skillId = skillId,
                        params = params,
                        stage = "deterministic_phone_launch",
                    )
                    emit(AgentEvent.Observation(
                        text = skillResult.output,
                        imageBase64 = skillResult.imageBase64,
                        attachment = skillResult.data as? SkillAttachment,
                    ))
                    val step = AgentStep(
                        index = ctx.steps.size,
                        thought = deterministicThought.ifBlank { "Deterministic phone app launch" },
                        toolCallId = "deterministic-phone-${ctx.steps.size}",
                        skillId = skillId,
                        skillParams = params,
                        observation = storedObservationForStep(skillId, params, skillResult.output),
                        isError = !skillResult.success,
                        imageBase64 = skillResult.imageBase64,
                    )
                    ctx.steps.add(step)
                    workingMemory.push(step)
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "skill_observation",
                            taskType = taskType.name,
                            label = "deterministic_${skillId}",
                            skillId = skillId,
                            summary = skillResult.output.take(240),
                            details = skillResult.output.take(4000),
                        )
                    )
                    if (finalAfterSuccess && skillResult.success) {
                        val appName = extractRequestedAppName(goal).orEmpty()
                        val summary = if (appName.isNotBlank()) "已打开$appName。" else "已打开目标应用。"
                        emit(AgentEvent.Completed(summary))
                        onWorkspaceUpdate?.invoke(
                            AgentWorkspaceUpdate(
                                stage = "task_completed",
                                taskType = taskType.name,
                                label = "task_completed",
                                summary = summary.take(240),
                                details = "success=true",
                                success = true,
                            )
                        )
                        return finish(AgentResult(success = true, summary = summary, context = ctx))
                    }
                    if (!skillResult.success && skillResult.data is SkillAttachment.AccessibilityRequest) {
                        val summary = "需要先开启无障碍服务，才能执行打开应用和手机操作。"
                        emit(AgentEvent.Completed(summary))
                        onWorkspaceUpdate?.invoke(
                            AgentWorkspaceUpdate(
                                stage = "task_completed",
                                taskType = taskType.name,
                                label = "task_completed",
                                summary = summary.take(240),
                                details = "success=false",
                                success = false,
                            )
                        )
                        return finish(AgentResult(success = false, summary = summary, context = ctx))
                    }
                    continue
                }

                val deterministicArtifactPatch = deterministicArtifactPatchCall(taskType, artifactPatchContract, ctx.steps)
                if (deterministicArtifactPatch != null) {
                    val (skillId, params, _, thought) = deterministicArtifactPatch
                    emit(AgentEvent.SkillCalling(skillId, params))
                    val skillResult = executeSkillWithDiagnostics(
                        taskId = ctx.taskId,
                        taskType = taskType,
                        skillId = skillId,
                        params = params,
                        stage = "deterministic_artifact_patch",
                    )
                    emit(AgentEvent.Observation(
                        text = skillResult.output,
                        imageBase64 = skillResult.imageBase64,
                        attachment = skillResult.data as? SkillAttachment,
                    ))
                    val step = AgentStep(
                        index = ctx.steps.size,
                        thought = thought,
                        toolCallId = "deterministic-artifact-${ctx.steps.size}",
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
                    updateArtifactRepairState(params, skillResult, artifactRepairAttempts)
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "skill_observation",
                            taskType = taskType.name,
                            label = "deterministic_${skillId}",
                            skillId = skillId,
                            summary = skillResult.output.take(240),
                            details = skillResult.output.take(4000),
                        )
                    )
                    if (skillResult.success && params["action"] == "analyze_change") {
                        buildArtifactAnalyzeBrief(skillResult.output)?.let { brief ->
                            val briefStep = AgentStep(
                                index = ctx.steps.size,
                                thought = "Artifact patch brief",
                                toolCallId = null,
                                skillId = null,
                                skillParams = null,
                                observation = brief,
                            )
                            ctx.steps.add(briefStep)
                            workingMemory.push(briefStep)
                        }
                    }
                    if (skillResult.success && params["action"] == "update") {
                        buildArtifactUpdateBrief(skillResult.output)?.let { brief ->
                            val briefStep = AgentStep(
                                index = ctx.steps.size,
                                thought = "Artifact update summary",
                                toolCallId = null,
                                skillId = null,
                                skillParams = null,
                                observation = brief,
                            )
                            ctx.steps.add(briefStep)
                            workingMemory.push(briefStep)
                        }
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
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "reflection",
                            taskType = taskType.name,
                            label = "reflection",
                            summary = "Detected repeated actions and inserted a reflection checkpoint.",
                            details = reflectionStep.observation.take(4000),
                        )
                    )
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
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "task_error",
                            taskType = taskType.name,
                            label = "task_error",
                            summary = msg.take(240),
                            details = msg.take(4000),
                            success = false,
                        )
                    )
                    return finish(AgentResult(success = false, summary = msg, context = ctx))
                }

                val unresolvedArtifactValidation = unresolvedArtifactValidationIssue(taskType, ctx.steps, artifactRepairAttempts)
                val unresolvedArtifactDraft = unresolvedArtifactDraftIssue(taskType, ctx.steps, artifactRepairAttempts)
                if (response.toolCall == null && unresolvedArtifactDraft != null) {
                    val repairStep = AgentStep(
                        index = ctx.steps.size,
                        thought = "Artifact draft repair checkpoint",
                        toolCallId = null,
                        skillId = null,
                        skillParams = null,
                        observation = unresolvedArtifactDraft,
                    )
                    ctx.steps.add(repairStep)
                    workingMemory.push(repairStep)
                    emit(AgentEvent.ThinkingComplete("发现产物只是草稿状态，继续按诊断修复，不把超时当成完成。"))
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "draft_repair",
                            taskType = taskType.name,
                            label = "draft_repair",
                            summary = unresolvedArtifactDraft.take(240),
                            details = unresolvedArtifactDraft.take(4000),
                        )
                    )
                    continue
                }

                if (response.toolCall == null && unresolvedArtifactValidation != null) {
                    val repairStep = AgentStep(
                        index = ctx.steps.size,
                        thought = "Artifact validation repair checkpoint",
                        toolCallId = null,
                        skillId = null,
                        skillParams = null,
                        observation = unresolvedArtifactValidation,
                    )
                    ctx.steps.add(repairStep)
                    workingMemory.push(repairStep)
                    emit(AgentEvent.ThinkingComplete("发现本轮页面/应用校验未通过，正在继续修补。"))
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "validation_repair",
                            taskType = taskType.name,
                            label = "validation_repair",
                            summary = unresolvedArtifactValidation.take(240),
                            details = unresolvedArtifactValidation.take(4000),
                        )
                    )
                    continue
                }

                val unresolvedArtifactRuntimeLogs = unresolvedArtifactRuntimeLogIssue(taskType, ctx.steps, artifactRepairAttempts)
                if (response.toolCall == null && unresolvedArtifactRuntimeLogs != null) {
                    val repairStep = AgentStep(
                        index = ctx.steps.size,
                        thought = "Artifact runtime log repair checkpoint",
                        toolCallId = null,
                        skillId = null,
                        skillParams = null,
                        observation = unresolvedArtifactRuntimeLogs,
                    )
                    ctx.steps.add(repairStep)
                    workingMemory.push(repairStep)
                    emit(AgentEvent.ThinkingComplete("发现 MiniAPP 运行日志仍有错误，继续按日志修复。"))
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "runtime_log_repair",
                            taskType = taskType.name,
                            label = "runtime_log_repair",
                            summary = unresolvedArtifactRuntimeLogs.take(240),
                            details = unresolvedArtifactRuntimeLogs.take(4000),
                        )
                    )
                    continue
                }

                // No tool call → task complete
                if (response.toolCall == null) {
                    val summary = response.content ?: "Task completed."
                    emit(AgentEvent.Completed(summary))
                    onWorkspaceUpdate?.invoke(
                        AgentWorkspaceUpdate(
                            stage = "task_completed",
                            taskType = taskType.name,
                            label = "task_completed",
                            summary = summary.take(240),
                            details = summary.take(4000),
                            success = true,
                        )
                    )
                    return finish(AgentResult(success = true, summary = summary, context = ctx))
                }

                // Emit the thought text so the UI can display it before the action
                val thought = response.content?.trim() ?: ""
                if (thought.isNotBlank()) emit(AgentEvent.ThinkingComplete(thought))

                val tc = response.toolCall
                emit(AgentEvent.SkillCalling(tc.skillId, tc.params))

                val phoneGuard = phoneControlGuardResult(taskType, ctx.steps, tc.skillId)
                if (phoneGuard != null) {
                    logGuardBlocked(
                        taskId = ctx.taskId,
                        taskType = taskType,
                        skillId = tc.skillId,
                        stage = "phone_control_guard",
                        reason = phoneGuard.output,
                    )
                }
                val repeatedPerceptionGuard = if (phoneGuard == null) repeatedPerceptionResult(ctx.steps, tc.skillId) else null
                if (repeatedPerceptionGuard != null) {
                    logGuardBlocked(
                        taskId = ctx.taskId,
                        taskType = taskType,
                        skillId = tc.skillId,
                        stage = "repeated_perception_guard",
                        reason = repeatedPerceptionGuard.output,
                    )
                }
                val skillResult = phoneGuard
                    ?: repeatedPerceptionGuard
                    ?: executeSkillWithDiagnostics(
                        taskId = ctx.taskId,
                        taskType = taskType,
                        skillId = tc.skillId,
                        params = tc.params,
                        stage = "tool_call",
                    )

                emit(AgentEvent.Observation(
                    text = skillResult.output,
                    imageBase64 = skillResult.imageBase64,
                    attachment = skillResult.data as? SkillAttachment,
                ))

                val storedObservation = storedObservationForStep(tc.skillId, tc.params, skillResult.output)
                val truncatedObservation = visibleObservationForUi(storedObservation)
                val step = AgentStep(
                    index = ctx.steps.size,
                    thought = response.content ?: "",
                    toolCallId = tc.id,
                    skillId = tc.skillId,
                    skillParams = tc.params,
                    observation = storedObservation,
                    isError = !skillResult.success,
                    imageBase64 = skillResult.imageBase64,
                )
                ctx.steps.add(step)
                workingMemory.push(step)
                updateArtifactRepairState(tc.params, skillResult, artifactRepairAttempts)
                onWorkspaceUpdate?.invoke(
                    AgentWorkspaceUpdate(
                        stage = "skill_observation",
                        taskType = taskType.name,
                        label = "skill_observation",
                        skillId = tc.skillId,
                        summary = truncatedObservation.take(240),
                        details = buildString {
                            appendLine("Skill: ${tc.skillId}")
                            appendLine("Params: ${tc.params}")
                            appendLine()
                            append(truncatedObservation.take(3800))
                        }.trim(),
                    )
                )

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
                onWorkspaceUpdate?.invoke(
                    AgentWorkspaceUpdate(
                        stage = "continuation_checkpoint",
                        taskType = taskType.name,
                        label = "continuation_checkpoint",
                        summary = checkpoint.take(240),
                        details = checkpoint.take(4000),
                    )
                )
            }
        }

        val finalSummary = try {
            pendingReview?.cancelAndJoin()
            llm.chat(ChatRequest(
                messages = ctx.toMessages(
                    systemPrompt + "\n\nYou have reached the internal action budget. Do not call tools. Summarize the useful progress and current state for the user, and say what remains only if it is genuinely unresolved.",
                    workingMemory.steps(),
                ),
                tools = emptyList(),
                stream = false,
            )).content.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Final summary generation failed for taskId=${ctx.taskId}", t)
            ""
        }
            .ifBlank { "已完成当前可执行步骤。" }
        emit(AgentEvent.Completed(finalSummary))
        onWorkspaceUpdate?.invoke(
            AgentWorkspaceUpdate(
                stage = "task_completed",
                taskType = taskType.name,
                label = "task_completed",
                summary = finalSummary.take(240),
                details = finalSummary.take(4000),
                success = true,
            )
        )
        return finish(AgentResult(success = true, summary = finalSummary, context = ctx))
    }

    private suspend fun emit(event: AgentEvent) = _events.emit(event)

    private suspend fun executeSkillWithDiagnostics(
        taskId: String,
        taskType: TaskType,
        skillId: String,
        params: Map<String, Any>,
        stage: String,
    ): SkillResult {
        val skill = registry.get(skillId)
        if (skill == null) {
            val message = "Error: skill '$skillId' not found."
            Log.e(
                TAG,
                "Missing skill during $stage. taskId=$taskId taskType=$taskType skillId=$skillId params=${params.toString().take(400)}"
            )
            emit(AgentEvent.Error(message))
            return SkillResult(success = false, output = message)
        }
        return try {
            skill.execute(params)
        } catch (t: Throwable) {
            val message = "Error executing $skillId: ${t.message ?: t::class.java.simpleName}"
            Log.e(
                TAG,
                "Skill execution failed during $stage. taskId=$taskId taskType=$taskType skillId=$skillId params=${params.toString().take(400)}",
                t
            )
            emit(AgentEvent.Error(message))
            SkillResult(success = false, output = message)
        }
    }

    private suspend fun logGuardBlocked(
        taskId: String,
        taskType: TaskType,
        skillId: String,
        stage: String,
        reason: String,
    ) {
        Log.w(
            TAG,
            "Guard blocked skill during $stage. taskId=$taskId taskType=$taskType skillId=$skillId reason=${reason.take(320)}"
        )
        // Guard 拦截代表当前动作不合适，不代表任务失败；单独降级成 warning，避免 UI 一片报错红条。
        emit(AgentEvent.Warning("Guard blocked $skillId: ${reason.lineSequence().firstOrNull().orEmpty()}"))
    }

    private suspend fun createFiveStepReview(
        systemPrompt: String,
        ctx: AgentContext,
        workingMemory: WorkingMemory,
    ): String {
        val review = try {
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
        } catch (t: Throwable) {
            Log.e(TAG, "Five-step review generation failed for taskId=${ctx.taskId}", t)
            ""
        }

        return review.ifBlank {
            "5-step review: compare the latest tool results with the user goal, avoid repeating failed actions, and choose the next concrete action that closes the remaining gap."
        }
    }

    private suspend fun createContinuationCheckpoint(
        systemPrompt: String,
        ctx: AgentContext,
        workingMemory: WorkingMemory,
    ): String {
        val summary = try {
            llm.chat(ChatRequest(
                messages = ctx.toMessages(
                    systemPrompt + "\n\nYou are at an internal 20-step checkpoint. Do not call tools. Summarize the useful progress, current screen/state, blockers if any, and write a direct self-instruction for the next segment. This is internal context for yourself, not a final answer to the user.",
                    workingMemory.steps(),
                ),
                tools = emptyList(),
                stream = false,
            )).content.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Continuation checkpoint generation failed for taskId=${ctx.taskId}", t)
            ""
        }

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
        val thought: String = "",
    )

    private fun deterministicPhoneLaunchCall(
        taskType: TaskType,
        goal: String,
        steps: List<AgentStep>,
    ): DeterministicToolCall? {
        if (taskType != TaskType.PHONE_CONTROL) return null
        val requestedApp = extractRequestedAppName(goal) ?: return null
        if (steps.none { it.skillId == "list_apps" }) {
            return DeterministicToolCall("list_apps", emptyMap(), thought = "Deterministic phone app discovery")
        }
        val appList = steps.lastOrNull { it.skillId == "list_apps" && !it.isError }?.observation.orEmpty()
        val packageName = resolvePackageNameFromListApps(appList, requestedApp) ?: return null
        val lastLaunchIndex = steps.indexOfLast {
            it.skillId == "navigate" &&
                it.skillParams?.get("action") == "launch" &&
                it.skillParams["package_name"] == packageName &&
                !it.isError
        }
        val foregroundPackage = latestForegroundPackage(steps)
        if (lastLaunchIndex >= 0 && foregroundPackage == packageName) return null
        if (lastLaunchIndex >= 0 && foregroundPackage.isBlank()) return null
        if (lastLaunchIndex >= 0 && foregroundPackage != packageName) {
            return DeterministicToolCall(
                skillId = "navigate",
                params = mapOf("action" to "launch", "package_name" to packageName, "foreground" to true),
                finalAfterSuccess = false,
                thought = "Deterministic target app foreground recovery",
            )
        }
        return DeterministicToolCall(
            skillId = "navigate",
            params = mapOf("action" to "launch", "package_name" to packageName, "foreground" to true),
            finalAfterSuccess = isLaunchOnlyPhoneGoal(goal),
            thought = "Deterministic phone app launch",
        )
    }

    private fun latestForegroundPackage(steps: List<AgentStep>): String {
        val regex = Regex("""Foreground app:\s*package=([^,\s]+)""")
        return steps.asReversed()
            .firstNotNullOfOrNull { step ->
                regex.find(step.observation)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() && it != "unknown" }
            }
            .orEmpty()
    }

    private data class ArtifactPatchContract(
        val artifactType: String,
        val targetId: String,
        val targetTitle: String,
        val latestChangeRequest: String,
    ) {
        val toolId: String
            get() = if (artifactType == "ai_native_page") "ui_builder" else "app_manager"
    }

    private fun parseArtifactPatchContract(goal: String): ArtifactPatchContract? {
        val marker = "[artifact_update_contract]"
        val raw = goal.substringAfter(marker, "")
        if (raw.isBlank()) return null
        fun find(key: String): String =
            raw.lineSequence()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
        val artifactType = find("artifact_type")
        val targetId = find("target_id")
        if (artifactType.isBlank() || targetId.isBlank()) return null
        return ArtifactPatchContract(
            artifactType = artifactType,
            targetId = targetId,
            targetTitle = find("target_title"),
            latestChangeRequest = raw.lineSequence()
                .firstOrNull { it.startsWith("Latest change request:") }
                ?.substringAfter(":")
                ?.trim()
                .orEmpty(),
        )
    }

    private fun deterministicArtifactPatchCall(
        taskType: TaskType,
        contract: ArtifactPatchContract?,
        steps: List<AgentStep>,
    ): DeterministicToolCall? {
        if (taskType != TaskType.APP_BUILD || contract == null) return null
        val toolId = contract.toolId
        val targetId = contract.targetId

        if (contract.artifactType == "ai_native_page" &&
            steps.none { it.skillId == toolId && it.skillParams?.get("action") == "get" && it.skillParams["id"] == targetId }
        ) {
            return DeterministicToolCall(
                skillId = toolId,
                params = mapOf("action" to "get", "id" to targetId),
                thought = "Deterministic artifact preload",
            )
        }

        if (steps.none { it.skillId == toolId && it.skillParams?.get("action") == "analyze_change" && it.skillParams["id"] == targetId }) {
            return DeterministicToolCall(
                skillId = toolId,
                params = mapOf(
                    "action" to "analyze_change",
                    "id" to targetId,
                    "change_request" to contract.latestChangeRequest.ifBlank { "Refine the existing artifact without losing current features." },
                ),
                thought = "Deterministic artifact change analysis",
            )
        }

        val lastUpdateIndex = steps.indexOfLast {
            it.skillId == toolId &&
                it.skillParams?.get("action") == "update" &&
                it.skillParams["id"] == targetId &&
                !it.isError
        }
        if (lastUpdateIndex >= 0) {
            if (toolId == "app_manager") {
                val lastInspectLogsIndex = steps.indexOfLast {
                    it.skillId == toolId &&
                        it.skillParams?.get("action") == "inspect_logs" &&
                        it.skillParams["id"] == targetId
                }
                if (lastInspectLogsIndex < lastUpdateIndex) {
                    return DeterministicToolCall(
                        skillId = toolId,
                        params = mapOf("action" to "inspect_logs", "id" to targetId, "limit" to 80),
                        thought = "Deterministic miniapp runtime log inspection",
                    )
                }
            }
            if (toolId == "ui_builder") {
                val lastInspectRuntimeIndex = steps.indexOfLast {
                    it.skillId == toolId &&
                        it.skillParams?.get("action") == "inspect_runtime" &&
                        it.skillParams["id"] == targetId
                }
                if (lastInspectRuntimeIndex < lastUpdateIndex) {
                    return DeterministicToolCall(
                        skillId = toolId,
                        params = mapOf("action" to "inspect_runtime", "id" to targetId),
                        thought = "Deterministic native page runtime inspection",
                    )
                }
            }
            val lastValidateIndex = steps.indexOfLast {
                it.skillId == toolId &&
                    it.skillParams?.get("action") == "validate" &&
                    it.skillParams["id"] == targetId
            }
            if (lastValidateIndex < lastUpdateIndex) {
                return DeterministicToolCall(
                    skillId = toolId,
                    params = mapOf("action" to "validate", "id" to targetId),
                    thought = "Deterministic artifact regression check",
                )
            }
        }
        return null
    }

    private fun extractRequestedAppName(goal: String): String? {
        val text = goal.lineSequence().firstOrNull().orEmpty().trim()
        val match = Regex("""(?:帮我)?(?:打开|启动|开启|进入)\s*([^，。,.!?！？\n]+)""").find(text) ?: return null
        val rawName = match.groupValues.getOrNull(1)
            ?: return null
        val normalizedName = rawName
            ?.replace(Regex("""\s*(app|APP|应用|软件)$"""), "")
            ?.trim()
            .orEmpty()
        val appName = normalizedName.substringBeforeAny(
            "然后", "并且", "并", "之后", "以后", "后",
            "搜索", "查找", "点击", "点", "选择", "输入", "下单", "购买", "发", "看",
        ).trim()
        if (appName.isBlank()) return null
        if (appName.contains("网页") || appName.contains("链接") || appName.contains("文件")) return null
        return appName.take(40)
    }

    private fun isLaunchOnlyPhoneGoal(goal: String): Boolean {
        val text = goal.lineSequence().firstOrNull().orEmpty().trim()
        val afterLaunchVerb = Regex("""(?:帮我)?(?:打开|启动|开启|进入)\s*([^，。,.!?！？\n]+)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (afterLaunchVerb.isBlank()) return false
        val continuationSignals = listOf(
            "然后", "并且", "并", "之后", "以后", "后",
            "搜索", "查找", "点击", "点", "选择", "输入", "下单", "购买", "发", "看",
            "帮我", "操作", "滑动", "进入", "筛选",
        )
        return continuationSignals.none { afterLaunchVerb.contains(it) }
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

    private fun String.substringBeforeAny(vararg delimiters: String): String {
        val firstIndex = delimiters
            .mapNotNull { delimiter -> indexOf(delimiter).takeIf { it >= 0 } }
            .minOrNull()
            ?: return this
        return substring(0, firstIndex)
    }

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

    private fun unresolvedArtifactValidationIssue(
        taskType: TaskType,
        steps: List<AgentStep>,
        repairAttempts: Map<String, Int>,
    ): String? {
        if (taskType != TaskType.APP_BUILD) return null
        val lastValidateIndex = steps.indexOfLast {
            (it.skillId == "ui_builder" || it.skillId == "app_manager") &&
                it.skillParams?.get("action") == "validate"
        }
        if (lastValidateIndex < 0) return null
        val validateStep = steps[lastValidateIndex]
        if (!validateStep.isError) return null
        val lastUpdateAfterValidate = steps.indexOfLast {
            it.index > validateStep.index &&
                (it.skillId == "ui_builder" || it.skillId == "app_manager") &&
                it.skillParams?.get("action") == "update" &&
                !it.isError
        }
        if (lastUpdateAfterValidate > validateStep.index) return null
        val payload = parseArtifactJsonObject(validateStep.observation)
        val artifactId = payload?.stringValue("id").orEmpty()
        val attemptCount = repairAttempts[artifactId] ?: 0
        val missingRequired = payload?.getStringList("missing_required_features").orEmpty()
        val missingSnapshot = payload?.getStringList("missing_snapshot_features").orEmpty()
        val runtimeIssues = payload?.getStringList("runtime_issues").orEmpty()
        val recentLogs = payload?.getStringList("recent_logs").orEmpty()
        val summary = payload?.stringValue("summary")?.takeIf { it.isNotBlank() } ?: validateStep.observation.take(1600)
        return """
            Artifact validation failed and the artifact has not been repaired yet.
            Repair attempts on this artifact in the current run: $attemptCount
            Do not finish. Read the validation warning carefully, identify the missing preserved features, runtime issues, and recent logs, then issue one focused update that repairs only the missing parts without rewriting unrelated sections.
            ${if (attemptCount >= 2) "You have already tried repairing this artifact more than once. Do not repeat the same fix. Choose a narrower or clearly different update strategy." else ""}
            Missing required features: ${missingRequired.ifEmpty { listOf("none listed") }.joinToString(", ")}
            Missing snapshot features: ${missingSnapshot.ifEmpty { listOf("none listed") }.joinToString(", ")}
            Runtime issues: ${runtimeIssues.ifEmpty { listOf("none listed") }.joinToString(", ")}
            Recent logs: ${recentLogs.takeLast(8).ifEmpty { listOf("none listed") }.joinToString(" | ")}
            Latest validation result:
            $summary
        """.trimIndent()
    }

    private fun unresolvedArtifactDraftIssue(
        taskType: TaskType,
        steps: List<AgentStep>,
        repairAttempts: Map<String, Int>,
    ): String? {
        if (taskType != TaskType.APP_BUILD) return null
        val lastDraftIndex = steps.indexOfLast { step ->
            (step.skillId == "ui_builder" || step.skillId == "app_manager") &&
                (step.skillParams?.get("action") == "create" || step.skillParams?.get("action") == "update") &&
                parseArtifactJsonObject(step.observation)?.let { payload ->
                    payload.stringValue("saved_as_draft").equals("true", ignoreCase = true) ||
                        (
                            payload.stringValue("ok").equals("false", ignoreCase = true) &&
                                payload.stringValue("open_blocked").equals("true", ignoreCase = true)
                            )
                } == true
        }
        if (lastDraftIndex < 0) return null
        val draftStep = steps[lastDraftIndex]
        val payload = parseArtifactJsonObject(draftStep.observation) ?: return null
        val artifactId = payload.stringValue("id")
        val toolId = draftStep.skillId.orEmpty()
        if (artifactId.isBlank() || toolId.isBlank()) return null
        val repairedAfterDraft = steps.any {
            it.index > draftStep.index &&
                it.skillId == toolId &&
                it.skillParams?.get("id") == artifactId &&
                (
                    it.skillParams?.get("action") == "update" ||
                        it.skillParams?.get("action") == "validate" ||
                        it.skillParams?.get("action") == "open"
                    ) &&
                !it.isError
        }
        if (repairedAfterDraft) return null
        val attemptCount = repairAttempts[artifactId] ?: 0
        val summary = payload.stringValue("summary").ifBlank { draftStep.observation.take(1200) }
        val preflightIssues = payload.getStringList("preflight_issues")
        val preflightWarnings = payload.getStringList("preflight_warnings")
        val errorLogs = payload.getStringList("error_logs")
        val recentLogs = payload.getStringList("recent_logs")
        val runtimeHint = if (toolId == "app_manager") {
            "Next steps: inspect_logs -> update -> validate -> open."
        } else {
            "Next steps: update -> inspect_runtime/validate -> open."
        }
        return """
            The artifact was saved only as a draft and is not ready yet.
            Do not treat this as a final timeout/failure message. Continue repairing the existing artifact instead of creating a new one.
            Artifact id: $artifactId
            Repair attempts on this artifact in the current run: $attemptCount
            ${if (attemptCount >= 2) "You have already tried repairing this draft more than once. Change strategy and patch the exact failing startup/runtime path." else ""}
            Preflight issues: ${preflightIssues.ifEmpty { listOf("none listed") }.joinToString(" | ")}
            Preflight warnings: ${preflightWarnings.ifEmpty { listOf("none listed") }.joinToString(" | ")}
            Error logs: ${errorLogs.ifEmpty { listOf("none listed") }.joinToString(" | ")}
            Recent logs: ${recentLogs.takeLast(8).ifEmpty { listOf("none listed") }.joinToString(" | ")}
            $runtimeHint
            Latest draft result:
            $summary
        """.trimIndent()
    }

    private fun unresolvedArtifactRuntimeLogIssue(
        taskType: TaskType,
        steps: List<AgentStep>,
        repairAttempts: Map<String, Int>,
    ): String? {
        if (taskType != TaskType.APP_BUILD) return null
        val lastInspectIndex = steps.indexOfLast {
            it.skillId == "app_manager" &&
                it.skillParams?.get("action") == "inspect_logs"
        }
        if (lastInspectIndex < 0) return null
        val inspectStep = steps[lastInspectIndex]
        val lastUpdateAfterInspect = steps.indexOfLast {
            it.index > inspectStep.index &&
                it.skillId == "app_manager" &&
                it.skillParams?.get("action") == "update" &&
                !it.isError
        }
        if (lastUpdateAfterInspect > inspectStep.index) return null
        val payload = parseArtifactJsonObject(inspectStep.observation) ?: return null
        val artifactId = payload.stringValue("id")
        val errorLogs = payload.getStringList("error_logs")
        if (artifactId.isBlank() || errorLogs.isEmpty()) return null
        val attemptCount = repairAttempts[artifactId] ?: 0
        val warningLogs = payload.getStringList("warning_logs")
        val summary = payload.stringValue("summary").ifBlank { inspectStep.observation.take(1200) }
        return """
            MiniAPP runtime log inspection still shows errors after the latest update.
            Repair attempts on this artifact in the current run: $attemptCount
            Do not stop after a green-looking edit if the runtime logs still contain errors.
            Fix the concrete runtime problem shown in the logs, then inspect logs again and validate again.
            ${if (attemptCount >= 2) "You have already repaired this artifact more than once. Change strategy and patch the exact failing code path instead of broad rewrites." else ""}
            Error logs: ${errorLogs.takeLast(8).joinToString(" | ")}
            Warning logs: ${warningLogs.takeLast(6).ifEmpty { listOf("none listed") }.joinToString(" | ")}
            Latest runtime log result:
            $summary
        """.trimIndent()
    }

    private fun buildArtifactAnalyzeBrief(output: String): String? {
        val payload = parseArtifactJsonObject(output) ?: return null
        val goal = payload.stringValue("goal")
        val currentFeatures = payload.getStringList("current_features")
        val preserveFeatures = payload.getStringList("preserve_features")
        val recentLogs = payload.getStringList("recent_logs")
        val patchFocus = payload.stringValue("patch_focus")
        val changeType = payload.stringValue("change_type")
        val diff = payload.stringValue("last_diff_summary")
        return buildString {
            appendLine("Artifact patch brief:")
            appendLine("Goal: ${goal.ifBlank { "unspecified" }}")
            appendLine("Patch focus: ${patchFocus.ifBlank { "targeted_patch" }} | change type: ${changeType.ifBlank { "modify" }}")
            appendLine("Keep: ${preserveFeatures.ifEmpty { currentFeatures }.take(10).joinToString(", ").ifBlank { "all current visible behavior unless explicitly removed" }}")
            if (currentFeatures.isNotEmpty()) appendLine("Current features: ${currentFeatures.take(10).joinToString(", ")}")
            if (recentLogs.isNotEmpty()) appendLine("Recent logs: ${recentLogs.takeLast(6).joinToString(" | ").take(800)}")
            if (diff.isNotBlank()) append("Last diff: $diff")
        }.trim()
    }

    private fun buildArtifactUpdateBrief(output: String): String? {
        val payload = parseArtifactJsonObject(output) ?: return null
        val summary = payload.stringValue("summary")
        val currentFeatures = payload.getStringList("current_features")
        val requiredFeatures = payload.getStringList("required_features")
        val diff = payload.stringValue("last_diff_summary")
        val openHint = payload.stringValue("open_hint")
        val preflightRecentLogs = payload.getStringList("preflight_recent_logs")
        val preflightWarnings = payload.getStringList("preflight_warnings")
        val debugProtocol = payload.stringValue("debug_protocol")
        return buildString {
            appendLine("Artifact update result:")
            appendLine(summary.ifBlank { "Artifact updated." })
            if (currentFeatures.isNotEmpty()) appendLine("Current features: ${currentFeatures.take(10).joinToString(", ")}")
            if (requiredFeatures.isNotEmpty()) appendLine("Required features: ${requiredFeatures.take(10).joinToString(", ")}")
            if (preflightWarnings.isNotEmpty()) appendLine("Preflight warnings: ${preflightWarnings.take(8).joinToString(" | ")}")
            if (preflightRecentLogs.isNotEmpty()) appendLine("Preflight logs: ${preflightRecentLogs.takeLast(6).joinToString(" | ").take(800)}")
            if (diff.isNotBlank()) appendLine("Diff: $diff")
            if (debugProtocol.isNotBlank()) appendLine("Debug protocol: $debugProtocol")
            if (openHint.isNotBlank()) append("Open hint: $openHint")
        }.trim()
    }

    private fun parseArtifactJsonObject(output: String): JsonObject? =
        runCatching { JsonParser.parseString(output).asJsonObject }.getOrNull()

    private fun storedObservationForStep(
        skillId: String,
        params: Map<String, Any>,
        output: String,
    ): String {
        val action = params["action"] as? String
        return if (skillId in artifactSkillIds && action in artifactStructuredActions) output else visibleObservationForUi(output)
    }

    private fun visibleObservationForUi(output: String): String =
        if (output.length > 4000) output.take(4000) + "\n…[truncated ${output.length - 4000} chars]" else output

    private fun updateArtifactRepairState(
        params: Map<String, Any>,
        skillResult: SkillResult,
        repairAttempts: MutableMap<String, Int>,
    ) {
        val action = params["action"] as? String ?: return
        if (action !in setOf("create", "update", "validate")) return
        val payload = parseArtifactJsonObject(skillResult.output) ?: return
        val artifactId = payload.stringValue("id")
        if (artifactId.isBlank()) return
        val savedAsDraft = payload.stringValue("saved_as_draft").equals("true", ignoreCase = true)
        val okFalse = payload.stringValue("ok").equals("false", ignoreCase = true)
        when {
            action == "validate" && skillResult.success -> repairAttempts.remove(artifactId)
            action == "validate" && !skillResult.success -> repairAttempts[artifactId] = (repairAttempts[artifactId] ?: 0) + 1
            savedAsDraft || okFalse -> repairAttempts[artifactId] = (repairAttempts[artifactId] ?: 0) + 1
        }
    }

    private fun JsonObject.stringValue(key: String): String =
        runCatching { get(key)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else it.toString() } ?: "" }
            .getOrDefault("")

    private fun JsonObject.getStringList(key: String): List<String> =
        runCatching {
            getAsJsonArray(key)?.mapNotNull { element ->
                runCatching {
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }.orEmpty()
        }.getOrDefault(emptyList())
}

data class AgentResult(
    val success: Boolean,
    val summary: String,
    val context: AgentContext,
)

data class AgentWorkspaceUpdate(
    val stage: String,
    val taskType: String,
    val label: String,
    val summary: String,
    val details: String = "",
    val skillId: String? = null,
    val success: Boolean? = null,
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
    data class Warning(val message: String) : AgentEvent()
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

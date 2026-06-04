package com.mobileclaw.ui

import android.util.Log
import com.mobileclaw.agent.AiTaskRouteDecision
import com.mobileclaw.agent.ChannelType
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskType
import com.mobileclaw.app.MiniApp
import com.mobileclaw.artifact.ArtifactHistoryEntry
import com.mobileclaw.artifact.ArtifactSpec
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.ui.chat.ChatMessage
import com.mobileclaw.ui.chat.MessageRole
import com.mobileclaw.workspace.WorkspaceExecutionContext

data class ContextualTaskIntent(
    val classificationGoal: String,
    val taskTypeOverride: TaskType? = null,
    val aiPage: AiPageDef? = null,
    val miniApp: MiniApp? = null,
    val fileAttachment: SkillAttachment.FileData? = null,
    val htmlAttachment: SkillAttachment.HtmlData? = null,
    val executionHint: String = "",
    val aiPrimaryChannel: ChannelType? = null,
    val aiSupportingChannels: List<ChannelType> = emptyList(),
    val aiToolHints: List<String> = emptyList(),
    val userVisibleSteps: List<String> = emptyList(),
    val aiRouteConfidence: Float? = null,
    val aiRouteReason: String = "",
    val aiRouteTargetApp: String = "",
    val disableToolNarrowing: Boolean = false,
)

enum class TaskRouteSource {
    CLASSIFIER,
    AI_ROUTER,
    ACTIVE_WORKFLOW,
    RECENT_CONTEXT,
}

data class TaskRoute(
    val taskType: TaskType,
    val contextualIntent: ContextualTaskIntent,
    val goalForExecution: String,
    val source: TaskRouteSource,
    val goalToRemember: String = goalForExecution,
    val debugReason: String = "",
)

data class ActiveWorkflow(
    val originalGoal: String,
    val taskType: TaskType,
    val roleId: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

class TaskRouter(
    private val aiPagesProvider: () -> List<AiPageDef>,
    private val miniAppsProvider: () -> List<MiniApp>,
    private val messagesProvider: () -> List<ChatMessage>,
    private val currentRoleProvider: () -> Role,
    private val workspaceContextProvider: () -> WorkspaceExecutionContext?,
) {
    companion object {
        private const val TAG = "TaskRouter"
        private const val CONFIRM_TASK_PREFIX = "确认执行:"
        private const val CONFIRM_ACCESSIBILITY_TASK_PREFIX = "确认无障碍并执行:"
        private const val OPEN_ACCESSIBILITY_PREFIX = "打开无障碍设置:"
        private const val CONFIRM_ROLE_PREFIX = "确认切换角色:"
        private const val CANCEL_CONFIRMATION_TEXT = "取消"
        private val PHONE_CONTROL_SKILLS = setOf(
            "see_screen", "screenshot", "tap", "scroll", "input_text", "long_click",
            "navigate", "list_apps", "phone_status", "check_permissions",
        )
        private val APP_BUILD_SKILLS = setOf("ui_builder", "app_manager", "create_html")
        private val FILE_SKILLS = setOf("generate_document", "create_file", "read_file", "list_files")
        private val WEB_SKILLS = setOf("web_search", "fetch_url", "web_browse", "web_content", "web_js")
        private val IMAGE_SKILLS = setOf("generate_image", "generate_icon", "generate_video")
        private val CODE_SKILLS = setOf("shell", "run_python", "pip_install")
    }

    fun resolveWithAiDecision(
        goal: String,
        effectiveGoal: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
        decision: AiTaskRouteDecision,
    ): TaskRoute? {
        explicitArtifactIntent(goal)?.let { forcedIntent ->
            return TaskRoute(
                taskType = TaskType.APP_BUILD,
                contextualIntent = forcedIntent,
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.AI_ROUTER,
                goalToRemember = effectiveGoal,
                debugReason = "Explicit artifact intent overrode AI router output.",
            )
        }
        if (decision.taskType == null || decision.confidence < 0.52f) {
            Log.w(TAG, "Rejecting AI route because taskType=${decision.taskType} confidence=${decision.confidence} goal=${goal.take(160)}")
            return null
        }
        val normalizedGoal = decision.normalizedGoal.ifBlank { effectiveGoal }
        val executionHint = buildString {
            appendLine("AI router selected this execution path from the latest user message and recent context.")
            appendLine("Router reason: ${decision.reason.ifBlank { "No extra reason." }}")
            decision.targetApp.takeIf { it.isNotBlank() }?.let { appendLine("Target app: $it") }
            appendLine("Normalized goal: ${normalizedGoal.take(1000)}")
            if (decision.userVisibleSteps.isNotEmpty()) {
                appendLine("User-visible step plan:")
                decision.userVisibleSteps.take(6).forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
            }
        }.trim()
        return TaskRoute(
            taskType = decision.taskType,
            contextualIntent = ContextualTaskIntent(
                classificationGoal = normalizedGoal,
                taskTypeOverride = decision.taskType,
                executionHint = executionHint,
                aiPrimaryChannel = decision.primaryChannel,
                aiSupportingChannels = decision.supportingChannels,
                aiToolHints = decision.toolHints,
                userVisibleSteps = normalizeUserVisibleSteps(decision.userVisibleSteps, decision.taskType, normalizedGoal, decision.targetApp),
                aiRouteConfidence = decision.confidence,
                aiRouteReason = decision.reason,
                aiRouteTargetApp = decision.targetApp,
            ),
            goalForExecution = normalizedGoal,
            source = TaskRouteSource.AI_ROUTER,
            goalToRemember = normalizedGoal,
            debugReason = "AI router accepted with confidence=${"%.2f".format(decision.confidence)} reason=${decision.reason.take(180)}",
        )
    }

    fun resolveAsAgentFallback(
        goal: String,
        effectiveGoal: String,
        reason: String,
    ): TaskRoute {
        val steps = listOf(
            "先根据你刚才的话确认最合适的处理入口",
            "再直接处理当前最核心的内容",
            "如果中途需要网页、文件、手机或记忆能力，会自动接上对应能力",
        )
        return TaskRoute(
            taskType = TaskType.GENERAL,
            contextualIntent = ContextualTaskIntent(
                classificationGoal = effectiveGoal,
                taskTypeOverride = TaskType.GENERAL,
                executionHint = buildString {
                    appendLine("AI router fallback is active.")
                    appendLine("Fallback reason: $reason")
                    appendLine("User goal: ${goal.take(1000)}")
                    appendLine("Do not answer that tools are unavailable. Inspect available capabilities and choose the best channel/tool path.")
                }.trim(),
                aiRouteConfidence = 0f,
                aiRouteReason = reason,
                userVisibleSteps = steps,
                disableToolNarrowing = true,
            ),
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.AI_ROUTER,
            debugReason = "AI router fallback: $reason",
        )
    }

    fun resolve(
        goal: String,
        effectiveGoal: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
    ): TaskRoute {
        if (!hasImage && !hasFile && activeWorkflow != null && shouldContinueActiveWorkflow(goal, activeWorkflow)) {
            val continueGoal = activeWorkflow.originalGoal.ifBlank { effectiveGoal }
            return TaskRoute(
                taskType = activeWorkflow.taskType,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goal,
                    taskTypeOverride = activeWorkflow.taskType,
                    userVisibleSteps = continueVisibleSteps(activeWorkflow.taskType, continueGoal, goal, ""),
                    executionHint = buildString {
                        appendLine("The user is continuing an active task in this chat.")
                        appendLine("Active task type: ${activeWorkflow.taskType}.")
                        appendLine("Original task: ${activeWorkflow.originalGoal.take(900)}")
                        appendLine("Latest user message: $goal")
                        append("Continue the active task from the latest observed state. Do not revive older unrelated artifacts or tasks.")
                    },
                ),
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.ACTIVE_WORKFLOW,
                goalToRemember = activeWorkflow.originalGoal,
                debugReason = "Continuing active workflow ${activeWorkflow.taskType}.",
            )
        }

        latestContinueOnlyRoute(goal, effectiveGoal)?.let { return it }

        val contextualIntent = resolveContextualTaskIntent(goal, hasImage, hasFile)
        val explicitArtifactTaskType = explicitArtifactTaskType(goal)
        val classifiedTaskType = TaskClassifier.classify(
            goal = contextualIntent.classificationGoal.ifBlank { effectiveGoal },
            hasImage = hasImage,
            hasFile = hasFile,
        )
        val taskType = inferFollowUpTaskType(goal, classifiedTaskType)
            ?: explicitArtifactTaskType
            ?: contextualIntent.taskTypeOverride
            ?: if (contextualIntent.aiPage != null && !hasImage && !hasFile) TaskType.APP_BUILD else classifiedTaskType
        return TaskRoute(
            taskType = taskType,
            contextualIntent = contextualIntent,
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.CLASSIFIER,
            debugReason = buildString {
                append("Classifier route selected taskType=$taskType")
                explicitArtifactTaskType?.let { append("; explicitArtifactTaskType=$it") }
                contextualIntent.taskTypeOverride?.let { append("; contextualOverride=$it") }
                append("; classifiedTaskType=$classifiedTaskType")
            },
        )
    }

    private fun latestContinueOnlyRoute(goal: String, effectiveGoal: String): TaskRoute? {
        if (!isGenericContinueOnly(goal)) return null
        workspaceContinueRoute(goal, effectiveGoal)?.let { return it }
        val recent = effectiveContextMessages(limit = 6)
        val latest = recent.lastOrNull() ?: return null
        val latestAttachment = latest.attachments.asReversed()
            .firstOrNull { it is SkillAttachment.FileData || it is SkillAttachment.HtmlData }
            ?.takeIf { !isLikelyStickerOrMediaAsset(it) }
        if (latestAttachment != null) {
            val intent = recentFileContextIntent(goal, latestAttachment)
            return TaskRoute(
                taskType = intent.taskTypeOverride ?: TaskType.FILE_CREATE,
                contextualIntent = intent,
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.RECENT_CONTEXT,
                goalToRemember = recentAnchorGoal(recent, effectiveGoal),
                debugReason = "Recent attachment context continuation.",
            )
        }

        val inferredTaskType = inferTaskTypeFromMessage(latest)
        if (inferredTaskType != null) {
            val anchorGoal = recentAnchorGoal(recent, effectiveGoal)
            return TaskRoute(
                taskType = inferredTaskType,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goal,
                    taskTypeOverride = inferredTaskType,
                    userVisibleSteps = continueVisibleSteps(inferredTaskType, anchorGoal, goal, inferTargetAppFromMessages(recent)),
                    executionHint = "The user's short follow-up refers to the latest meaningful ${inferredTaskType.name} task in this chat. Continue that task using the newest relevant chat records, not an older unrelated thread.",
                ),
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.RECENT_CONTEXT,
                goalToRemember = anchorGoal,
                debugReason = "Recent message inferred follow-up taskType=$inferredTaskType.",
            )
        }

        val genericAnchorGoal = recentAnchorGoal(recent, effectiveGoal)
        return TaskRoute(
            taskType = TaskType.GENERAL,
            contextualIntent = ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.GENERAL,
                userVisibleSteps = continueVisibleSteps(TaskType.GENERAL, genericAnchorGoal, goal, inferTargetAppFromMessages(recent)),
                executionHint = "The user's short follow-up refers to the latest conversational thread. Answer based on the newest relevant user intent; do not start an unrelated artifact or tool workflow.",
            ),
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.RECENT_CONTEXT,
            debugReason = "Generic continue-only follow-up defaulted to latest conversational thread.",
        )
    }

    private fun workspaceContinueRoute(goal: String, effectiveGoal: String): TaskRoute? {
        val workspace = workspaceContextProvider() ?: return null
        val taskType = workspaceTaskType(workspace) ?: return null
        val intent = ContextualTaskIntent(
            classificationGoal = goal,
            taskTypeOverride = taskType,
            userVisibleSteps = continueVisibleSteps(taskType, workspace.goal.ifBlank { effectiveGoal }, goal, workspace.latestArtifactTitle),
            aiPage = workspaceAiPageTarget(workspace),
            miniApp = workspaceMiniAppTarget(workspace),
            executionHint = buildString {
                appendLine("The user is continuing the current workspace-backed task.")
                appendLine("Always resume from the current workspace state before reading older chat history.")
                appendLine("Workspace: ${workspace.title} (${workspace.workspaceId})")
                appendLine("Workspace goal: ${workspace.goal.take(1000)}")
                workspace.taskType.takeIf { it.isNotBlank() }?.let { appendLine("Workspace task type: $it") }
                workspace.checkpointLabel.takeIf { it.isNotBlank() }?.let { appendLine("Latest checkpoint: $it") }
                workspace.checkpointSummary.takeIf { it.isNotBlank() }?.let { appendLine("Checkpoint summary: ${it.take(800)}") }
                if (workspace.latestArtifactType.isNotBlank() || workspace.latestArtifactId.isNotBlank()) {
                    appendLine(
                        "Current artifact: ${workspace.latestArtifactType.ifBlank { "unknown" }} " +
                            "${workspace.latestArtifactId.ifBlank { "" }} ${workspace.latestArtifactTitle.ifBlank { "" }}".trim()
                    )
                }
                workspace.latestArtifactAction.takeIf { it.isNotBlank() }?.let { appendLine("Latest artifact action: $it") }
                workspace.latestEventSummary.takeIf { it.isNotBlank() }?.let { appendLine("Latest event: ${it.take(800)}") }
                append("Continue the current task. Do not switch to an older unrelated thread.")
            }.trim(),
        )
        return TaskRoute(
            taskType = taskType,
            contextualIntent = intent,
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.RECENT_CONTEXT,
            goalToRemember = workspace.goal.ifBlank { effectiveGoal },
        )
    }

    fun applyContextualTaskConstraints(
        effectiveGoal: String,
        intent: ContextualTaskIntent,
        taskType: TaskType,
    ): String {
        if (taskType != TaskType.APP_BUILD) return effectiveGoal
        intent.aiPage?.let { page ->
            return buildString {
                append(effectiveGoal.trim())
                append("\n\n[artifact_update_contract]")
                append("\nartifact_type=ai_native_page")
                append("\ntarget_id=${page.id}")
                append("\ntarget_title=${page.title}")
                append("\nmode=patch_existing")
                append("\nDo not create a new page unless the user explicitly asks.")
                append("\nUse tool flow: ui_builder(get) -> ui_builder(analyze_change) -> ui_builder(update) -> ui_builder(validate) -> ui_builder(open if user-facing).")
                append("\nOriginal goal: ${page.spec.goal.ifBlank { page.description.ifBlank { page.title } }}")
                append("\nRequired features: ${renderSpecList(page.spec.requiredFeatures, fallback = "preserve all existing visible features unless explicitly removed")}")
                append("\nConstraints: ${renderSpecList(page.spec.constraints, fallback = "keep current artifact style and platform behavior consistent")}")
                append("\nAccepted corrections: ${renderSpecList(page.spec.acceptedCorrections, fallback = "none yet")}")
                append("\nKnown bugs: ${renderSpecList(page.spec.knownBugs, fallback = "none recorded")}")
                append("\nNon-goals: ${renderSpecList(page.spec.nonGoals, fallback = "none recorded")}")
                append("\nRecent artifact history: ${renderHistory(page.history)}")
                append("\nLatest change request: ${effectiveGoal.take(1200)}")
            }
        }
        intent.miniApp?.let { app ->
            return buildString {
                append(effectiveGoal.trim())
                append("\n\n[artifact_update_contract]")
                append("\nartifact_type=miniapp")
                append("\ntarget_id=${app.id}")
                append("\ntarget_title=${app.title}")
                append("\nmode=patch_existing")
                append("\nDo not create a new app unless the user explicitly asks.")
                append("\nUse tool flow: app_manager(analyze_change) -> app_manager(update) -> app_manager(validate) -> app_manager(open if user-facing).")
                append("\nOriginal goal: ${app.spec.goal.ifBlank { app.description.ifBlank { app.title } }}")
                append("\nRequired features: ${renderSpecList(app.spec.requiredFeatures, fallback = "preserve all existing user-visible features unless explicitly removed")}")
                append("\nConstraints: ${renderSpecList(app.spec.constraints, fallback = "keep current artifact style and runtime behavior consistent")}")
                append("\nAccepted corrections: ${renderSpecList(app.spec.acceptedCorrections, fallback = "none yet")}")
                append("\nKnown bugs: ${renderSpecList(app.spec.knownBugs, fallback = "none recorded")}")
                append("\nNon-goals: ${renderSpecList(app.spec.nonGoals, fallback = "none recorded")}")
                append("\nRecent artifact history: ${renderHistory(app.history)}")
                append("\nLatest change request: ${effectiveGoal.take(1200)}")
            }
        }
        return effectiveGoal
    }

    fun buildArtifactContext(intent: ContextualTaskIntent): String {
        val sections = mutableListOf<String>()
        intent.executionHint.takeIf { it.isNotBlank() }?.let {
            sections += "## Current Task Focus\n$it"
        }
        intent.aiPage?.let { page ->
            buildAiPageArtifactContext(page).takeIf { it.isNotBlank() }?.let { sections += it }
        }
        intent.miniApp?.let { mini ->
            buildMiniAppArtifactContext(mini).takeIf { it.isNotBlank() }?.let { sections += it }
        }
        intent.fileAttachment?.let {
            sections += "## Current File Artifact\nActive file target: path=${it.path}, name=${it.name}, mime=${it.mimeType}, size=${it.sizeBytes}. For follow-up edits, read/update this file instead of creating an unrelated artifact."
        }
        intent.htmlAttachment?.let {
            sections += "## Current HTML Artifact\nActive HTML target: path=${it.path}, title=${it.title}. For follow-up edits, continue from this result instead of creating an unrelated artifact."
        }
        return sections.joinToString("\n\n")
    }

    fun effectiveContextMessages(limit: Int): List<ChatMessage> =
        messagesProvider()
            .asReversed()
            .filterNot { it.isContextNoiseMessage() }
            .take(limit)
            .asReversed()

    private fun recentAnchorGoal(messages: List<ChatMessage>, fallback: String): String =
        messages
            .asReversed()
            .firstOrNull { msg ->
                msg.role == MessageRole.USER &&
                    msg.text.isNotBlank() &&
                    !isGenericContinueOnly(msg.text) &&
                    !isMobileClawInternalChatTopic(msg.text.lowercase())
            }
            ?.text
            ?.trim()
            ?.take(1200)
            ?: fallback

    fun summarizeAttachmentsForContext(attachments: List<SkillAttachment>): String {
        if (attachments.isEmpty()) return ""
        return attachments.joinToString("; ") { attachment ->
            when (attachment) {
                is SkillAttachment.ImageData -> buildString {
                    append("image(prompt=${attachment.prompt.orEmpty().take(60)}")
                    if (attachment.localPath.isNotBlank()) append(", local_path=${attachment.localPath}")
                    append(")")
                }
                is SkillAttachment.FileData -> "file(name=${attachment.name}, path=${attachment.path}, mime=${attachment.mimeType}, size=${attachment.sizeBytes})"
                is SkillAttachment.HtmlData -> "html(title=${attachment.title}, path=${attachment.path})"
                is SkillAttachment.WebPage -> "webpage(title=${attachment.title}, url=${attachment.url})"
                is SkillAttachment.SearchResults -> "search_results(query=${attachment.query}, count=${attachment.pages.size})"
                is SkillAttachment.AccessibilityRequest -> "accessibility_request(${attachment.skillName})"
                is SkillAttachment.ActionCard -> "action_card(title=${attachment.title}, actions=${attachment.actions.size})"
                is SkillAttachment.FileList -> "file_list(directory=${attachment.directory}, count=${attachment.files.size})"
            }
        }
    }

    private fun inferFollowUpTaskType(goal: String, classifiedTaskType: TaskType): TaskType? {
        if (classifiedTaskType != TaskType.GENERAL) return null
        if (!isContextualFollowUp(goal)) return null
        val recent = effectiveContextMessages(limit = 6)
        if (currentRoleProvider().id == "phone_operator" && isPhoneContinuationContext(recent)) {
            return TaskType.PHONE_CONTROL
        }
        if (recent.any { msg ->
                msg.senderRoleId == "phone_operator" ||
                    msg.logLines.any { it.skillId in PHONE_CONTROL_SKILLS || it.text.contains("VLM_PHONE_CONTROL") } ||
                    msg.text.contains("手机操控") ||
                    msg.text.contains("打开 App") ||
                    msg.text.contains("看屏幕") ||
                    msg.text.contains("点击")
            }) {
            return TaskType.PHONE_CONTROL
        }
        return null
    }

    private fun isPhoneContinuationContext(messages: List<ChatMessage>): Boolean =
        messages.any { msg ->
            msg.senderRoleId == "phone_operator" ||
                msg.logLines.any { it.skillId in PHONE_CONTROL_SKILLS || it.text.contains("VLM_PHONE_CONTROL") } ||
                msg.text.contains("手机操控") ||
                msg.text.contains("美团") ||
                msg.text.contains("筛选") ||
                msg.text.contains("附近")
        }

    private fun inferTaskTypeFromMessage(msg: ChatMessage): TaskType? {
        val skillIds = msg.logLines.mapNotNull { it.skillId }.toSet()
        val text = messageContextText(msg).lowercase()
        return when {
            msg.senderRoleId == "phone_operator" ||
                skillIds.any { it in PHONE_CONTROL_SKILLS } ||
                text.anyContainsLocal("vlm_phone_control", "手机操控", "前台应用", "foreground app") -> TaskType.PHONE_CONTROL
            skillIds.any { it in APP_BUILD_SKILLS } ||
                text.anyContainsLocal("ui_builder", "app_manager", "ai native page", "miniapp", "原生页面", "应用已创建") -> TaskType.APP_BUILD
            skillIds.any { it in FILE_SKILLS } ||
                text.anyContainsLocal("generate_document", "create_file", "read_file", "file_list", "文件已", "文档") -> TaskType.FILE_CREATE
            skillIds.any { it in WEB_SKILLS } ||
                text.anyContainsLocal("web_search", "fetch_url", "web_browse", "search_results") -> TaskType.WEB_RESEARCH
            skillIds.any { it in IMAGE_SKILLS } ||
                text.anyContainsLocal("generate_image", "generate_icon", "图片已生成") -> TaskType.IMAGE_GENERATION
            skillIds.any { it == "vpn_control" } ||
                text.anyContainsLocal("vpn", "mihomo", "代理") -> TaskType.VPN_CONTROL
            skillIds.any { it in CODE_SKILLS } -> TaskType.CODE_EXECUTION
            else -> null
        }
    }

    private fun workspaceTaskType(workspace: WorkspaceExecutionContext): TaskType? {
        val direct = workspace.taskType
            .takeIf { it.isNotBlank() }
            ?.let { raw -> TaskType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        if (direct != null) return direct
        return when (workspace.latestArtifactType.lowercase()) {
            "ai_native_page", "miniapp" -> TaskType.APP_BUILD
            "document", "pdf", "sheet", "slide", "text", "file", "html" -> TaskType.FILE_CREATE
            else -> when {
                workspace.latestEventCategory == "code_observation" -> TaskType.CODE_EXECUTION
                workspace.latestEventCategory == "file_observation" -> TaskType.FILE_CREATE
                workspace.latestEventCategory == "artifact_observation" -> TaskType.APP_BUILD
                else -> null
            }
        }
    }

    private fun workspaceAiPageTarget(workspace: WorkspaceExecutionContext): AiPageDef? {
        if (!workspace.latestArtifactType.equals("ai_native_page", ignoreCase = true)) return null
        val pages = aiPagesProvider()
        return pages.firstOrNull { it.id == workspace.latestArtifactId }
            ?: pages.firstOrNull { it.title == workspace.latestArtifactTitle }
    }

    private fun workspaceMiniAppTarget(workspace: WorkspaceExecutionContext): MiniApp? {
        if (!workspace.latestArtifactType.equals("miniapp", ignoreCase = true)) return null
        val apps = miniAppsProvider()
        return apps.firstOrNull { it.id == workspace.latestArtifactId }
            ?: apps.firstOrNull { it.title == workspace.latestArtifactTitle }
    }

    private fun shouldContinueActiveWorkflow(goal: String, workflow: ActiveWorkflow): Boolean {
        val text = goal.trim().lowercase()
        if (text.isBlank()) return false
        if (TaskClassifier.classify(goal) !in listOf(TaskType.CHAT, TaskType.GENERAL)) return false
        if (isMobileClawInternalChatTopic(text)) return false
        if (isGenericContinueOnly(text) || isContextualFollowUp(text)) return true
        return false
    }

    private fun resolveContextualTaskIntent(
        goal: String,
        hasImage: Boolean,
        hasFile: Boolean,
    ): ContextualTaskIntent {
        if (hasImage || hasFile) return ContextualTaskIntent(goal)
        val text = goal.lowercase()
        explicitArtifactIntent(goal)?.let { return it }
        val recentArtifact = inferRecentFileContextTarget(goal)
        if (recentArtifact != null && shouldUseRecentFileContext(text, recentArtifact)) {
            return recentFileContextIntent(goal, recentArtifact)
        }
        val miniApp = inferMiniAppContextTarget(goal)
        if (miniApp != null) {
            return ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                miniApp = miniApp,
                executionHint = "The user is referring to an existing MiniAPP. Update/open that MiniAPP instead of creating a new unrelated artifact.",
                aiPrimaryChannel = ChannelType.ARTIFACT,
                aiSupportingChannels = listOf(ChannelType.SKILL, ChannelType.MEMORY),
                aiToolHints = listOf("app_manager", "read_file", "create_file", "list_files", "create_html"),
            )
        }

        val aiPage = inferAiPageContextTarget(goal)
        if (aiPage != null) {
            return ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                aiPage = aiPage,
                executionHint = "The user is referring to an existing AI Native Page. Update that page instead of creating HTML or a new unrelated artifact.",
                aiPrimaryChannel = ChannelType.ARTIFACT,
                aiSupportingChannels = listOf(ChannelType.SKILL, ChannelType.MEMORY),
                aiToolHints = listOf("ui_builder", "read_file", "create_file", "list_files"),
            )
        }

        if (recentArtifact != null) return recentFileContextIntent(goal, recentArtifact)
        return ContextualTaskIntent(goal)
    }

    private fun explicitArtifactIntent(goal: String): ContextualTaskIntent? {
        val text = goal.lowercase()
        return when {
            isExplicitMiniAppIntent(text) -> ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                executionHint = "The user explicitly wants a MiniAPP/program/game/HTML runtime. Route this request through app_manager, not ui_builder, and create or update a persistent MiniAPP artifact.",
                aiPrimaryChannel = ChannelType.ARTIFACT,
                aiSupportingChannels = listOf(ChannelType.SKILL, ChannelType.MEMORY),
                aiToolHints = listOf("app_manager", "read_file", "create_file", "list_files"),
                userVisibleSteps = listOf("先理清这个 MiniAPP 具体要做什么", "把 MiniAPP 的页面和逻辑做出来或修好", "跑一轮检查后直接打开给你看"),
            )
            isExplicitNativePageIntent(text) -> ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                executionHint = "The user explicitly wants an AI Native Page / original page. Route this request through ui_builder, not app_manager.",
                aiPrimaryChannel = ChannelType.ARTIFACT,
                aiSupportingChannels = listOf(ChannelType.SKILL, ChannelType.MEMORY),
                aiToolHints = listOf("ui_builder", "read_file", "create_file", "list_files"),
                userVisibleSteps = listOf("先理清这个原生页面需要展示什么", "把原生页面做出来或改到位", "检查效果后直接打开给你看"),
            )
            else -> null
        }
    }

    private fun normalizeUserVisibleSteps(
        rawSteps: List<String>,
        taskType: TaskType?,
        normalizedGoal: String,
        targetApp: String,
    ): List<String> {
        val cleaned = rawSteps
            .map { it.trim().trimStart('-', '•', '*', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '、') }
            .filter { it.isNotBlank() }
            .map { rewriteGenericStep(it, taskType, normalizedGoal, targetApp) }
            .distinct()
            .take(4)
        return if (cleaned.isNotEmpty()) cleaned else defaultVisibleSteps(taskType, normalizedGoal, targetApp)
    }

    private fun rewriteGenericStep(
        step: String,
        taskType: TaskType?,
        normalizedGoal: String,
        targetApp: String,
    ): String {
        val normalized = step.lowercase()
        val app = targetApp.takeIf { it.isNotBlank() } ?: extractQuotedTopic(normalizedGoal)
        val topic = extractQuotedTopic(normalizedGoal)
        return when {
            normalized.contains("确认目标") || normalized.contains("确认页面") || normalized.contains("确认 miniapp") || normalized.contains("理清") ->
                defaultVisibleSteps(taskType, normalizedGoal, targetApp).first()
            normalized.contains("验证") || normalized.contains("检查结果") || normalized.contains("打开结果") ->
                defaultVisibleSteps(taskType, normalizedGoal, targetApp).last()
            normalized.contains("继续推进") || normalized.contains("完善流程") || normalized.contains("继续处理") ->
                defaultVisibleSteps(taskType, normalizedGoal, targetApp).getOrElse(1) { step }
            taskType == TaskType.PHONE_CONTROL && app != null && (normalized.contains("打开应用") || normalized.contains("目标应用")) ->
                "先打开$app，进入真正要操作的界面"
            taskType == TaskType.WEB_RESEARCH && topic != null && normalized.contains("查找") ->
                "先查“$topic”里最直接有用的信息"
            taskType == TaskType.CODE_EXECUTION && topic != null && normalized.contains("修复") ->
                "先把「$topic」里最关键的代码问题定位出来"
            else -> step
        }
    }

    private fun defaultVisibleSteps(taskType: TaskType?, normalizedGoal: String, targetApp: String): List<String> {
        val topic = extractQuotedTopic(normalizedGoal)
        return when (taskType) {
            TaskType.PHONE_CONTROL -> listOf(
                if (targetApp.isNotBlank()) "先打开$targetApp，找到这次要操作的位置" else "先看清当前界面和要操作的入口",
                if (topic != null) "再直接处理「$topic」相关的操作" else "再直接把这次手机操作做下去",
                "做完后再看界面有没有按预期变化",
            )
            TaskType.WEB_RESEARCH -> listOf(
                if (topic != null) "先查“$topic”里最直接有用的信息" else "先把最相关的网页信息找出来",
                "再筛掉没用的内容，只保留真正有帮助的部分",
                "最后把结果整理成你能直接用的结论",
            )
            TaskType.APP_BUILD -> listOf(
                if (topic != null) "先理清「$topic」到底要做成什么样" else "先理清这次要做的页面或程序长什么样",
                "再把核心页面、逻辑或交互补到位",
                "跑一轮检查后直接打开给你看实际效果",
            )
            TaskType.FILE_CREATE -> listOf(
                if (topic != null) "先整理「$topic」需要写进文件的内容" else "先整理这份文件真正要写什么",
                "再把文件内容生成出来",
                "最后确认它能不能直接打开或继续修改",
            )
            TaskType.CODE_EXECUTION -> listOf(
                if (topic != null) "先定位「$topic」里最关键的问题" else "先定位当前最关键的代码问题",
                "再直接修改代码并补齐缺的部分",
                "跑一轮检查，确认这次改动有没有生效",
            )
            else -> listOf(
                if (topic != null) "先处理「$topic」最核心的部分" else "先把这次需求里最核心的部分拿下来",
                if (topic != null) "再把「$topic」里还没处理对的部分补上" else "再把还没处理对的部分补上",
                "最后给你一个能直接理解和使用的结果",
            )
        }
    }

    private fun extractQuotedTopic(text: String): String? {
        val clean = text.trim().replace('\n', ' ')
        val quoted = Regex("[“\"']([^”\"']{2,32})[”\"']").find(clean)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val compact = clean.split(Regex("[，。；,.;]"))
            .map { it.trim() }
            .firstOrNull { it.length in 4..28 && !it.contains("请") && !it.contains("帮我") && !it.contains("我想") }
        return compact?.takeIf { it.isNotBlank() }
    }

    private fun explicitArtifactTaskType(goal: String): TaskType? {
        val text = goal.lowercase()
        return when {
            isExplicitMiniAppIntent(text) || isExplicitNativePageIntent(text) -> TaskType.APP_BUILD
            else -> null
        }
    }

    private fun isExplicitMiniAppIntent(text: String): Boolean =
        text.anyContainsLocal(
            "miniapp", "mini app", "小程序", "程序", "应用", "app", "game", "游戏",
            "webview", "html", "javascript", "js", "canvas", "sqlite", "python backend",
            "网页应用", "浏览器运行",
        ) &&
            text.anyContainsLocal(
                "创建", "生成", "做一个", "做个", "开发", "写一个", "搭建", "制作",
                "新建", "build", "create", "make", "update", "修改", "改", "优化", "修复",
            )

    private fun isExplicitNativePageIntent(text: String): Boolean =
        text.anyContainsLocal(
            "原生页面", "ai页面", "ai native page", "native page", "页面", "dashboard", "仪表盘", "表单", "管理页",
        ) &&
            text.anyContainsLocal(
                "创建", "生成", "做一个", "做个", "开发", "搭建", "制作",
                "新建", "build", "create", "make", "update", "修改", "改", "优化", "修复",
            )

    private fun recentFileContextIntent(goal: String, recentArtifact: SkillAttachment): ContextualTaskIntent {
        val taskType = when {
            recentArtifact is SkillAttachment.HtmlData && goal.lowercase().anyContainsLocal("页面", "html", "网页", "预览", "样式", "布局") -> TaskType.APP_BUILD
            else -> TaskType.FILE_CREATE
        }
        return ContextualTaskIntent(
            classificationGoal = goal,
            taskTypeOverride = taskType,
            fileAttachment = recentArtifact as? SkillAttachment.FileData,
            htmlAttachment = recentArtifact as? SkillAttachment.HtmlData,
            userVisibleSteps = continueVisibleSteps(taskType, recentArtifactSummary(recentArtifact), goal, ""),
            executionHint = "The user is referring to the latest relevant file or HTML artifact in this chat. Continue from that artifact, unless the user explicitly asks for a new one.",
        )
    }

    private fun continueVisibleSteps(
        taskType: TaskType?,
        anchorGoal: String,
        latestGoal: String,
        targetApp: String,
    ): List<String> {
        val base = if (isGenericContinueOnly(latestGoal) || isContextualFollowUp(latestGoal)) anchorGoal else latestGoal
        val topic = extractQuotedTopic(base).orEmpty()
        return when (taskType) {
            TaskType.PHONE_CONTROL -> listOf(
                if (targetApp.isNotBlank()) "先回到${targetApp}当前这条操作线上" else "先接着当前手机操作往下走",
                if (topic.isNotBlank()) "再直接处理「$topic」这一步" else "再把眼前这一步真正做完",
                "做完后马上看界面变化，再决定下一下点哪里",
            )
            TaskType.APP_BUILD -> listOf(
                if (topic.isNotBlank()) "先接着改「$topic」这块最关键的内容" else "先接着改当前页面或程序最关键的部分",
                "再把这次不对的地方修到位",
                "跑一轮效果检查后继续给你看结果",
            )
            TaskType.FILE_CREATE -> listOf(
                if (topic.isNotBlank()) "先接着补「$topic」相关的文件内容" else "先接着补这份文件里缺的内容",
                "再把这次要改的地方落到文件里",
                "最后确认这份结果能不能直接打开或继续改",
            )
            TaskType.WEB_RESEARCH -> listOf(
                if (topic.isNotBlank()) "先接着查「$topic」还缺的那部分信息" else "先接着找还没补齐的关键信息",
                "再把噪音信息筛掉",
                "最后只给你留下能直接用的结论",
            )
            TaskType.CODE_EXECUTION -> listOf(
                if (topic.isNotBlank()) "先接着修「$topic」这块最关键的问题" else "先接着修当前最关键的代码问题",
                "再补齐缺的逻辑或修掉报错",
                "跑一轮检查，确认这次修复真的生效",
            )
            else -> listOf(
                if (topic.isNotBlank()) "先接着处理「$topic」这件事" else "先接着处理你刚才那件事",
                "再把这次不对或没做完的部分补上",
                "最后给你一个更完整、直接能理解的结果",
            )
        }
    }

    private fun recentArtifactSummary(attachment: SkillAttachment): String =
        when (attachment) {
            is SkillAttachment.FileData -> attachment.name.ifBlank { attachment.path.substringAfterLast('/') }
            is SkillAttachment.HtmlData -> attachment.title.ifBlank { attachment.path.substringAfterLast('/') }
            else -> ""
        }

    private fun inferTargetAppFromMessages(messages: List<ChatMessage>): String =
        messages
            .asReversed()
            .firstNotNullOfOrNull { msg ->
                Regex("Target app: ([^\\n]+)").find(messageContextText(msg))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()

    private fun shouldUseRecentFileContext(text: String, attachment: SkillAttachment): Boolean {
        if (isLikelyStickerOrMediaAsset(attachment)) return false
        val followUpSignals = text.anyContainsLocal(
            "这个", "这份", "这个文件", "这个文档", "这个表", "这个表格", "这个ppt", "这个 ppt",
            "这个pdf", "这个 html", "这个页面", "它", "上面", "刚才", "继续", "接着",
            "修改", "改下", "改一下", "调整", "优化", "美化", "完善", "更新",
            "打开", "保存", "导出", "预览", "另存", "修订",
        )
        if (!followUpSignals) return false
        val newCreationIntent = text.anyContainsLocal(
            "帮我写", "帮我做", "帮我生成", "做一个", "做个", "创建", "生成", "新建", "设计", "整理成",
        ) && text.anyContainsLocal("ppt", "doc", "word", "pdf", "excel", "表格", "文档", "页面", "html")
        return !newCreationIntent
    }

    private fun isLikelyStickerOrMediaAsset(attachment: SkillAttachment): Boolean = when (attachment) {
        is SkillAttachment.FileData -> {
            val path = attachment.path.lowercase()
            val name = attachment.name.lowercase()
            val mime = attachment.mimeType.lowercase()
            mime.startsWith("image/") ||
                path.contains("/stickers/") ||
                path.contains("bqb") ||
                name.contains("sticker") ||
                name.contains("bqb") ||
                name.contains("emoji") ||
                name.contains("表情")
        }
        is SkillAttachment.HtmlData -> false
        else -> false
    }

    private fun buildAiPageArtifactContext(activeAiPage: AiPageDef? = null): String {
        val pages = aiPagesProvider().take(5)
        if (pages.isEmpty() && activeAiPage == null) return ""
        val active = activeAiPage ?: pages.firstOrNull()
        val lines = pages.joinToString("\n") { page ->
            val marker = if (page.id == active?.id) "active" else "recent"
            "- [$marker] id=${page.id}, title=${page.title}, version=${page.version}, description=${page.description.take(80)}, goal=${page.spec.goal.take(80)}, features=${renderSpecList(page.spec.requiredFeatures, fallback = "unspecified")}"
        }
        val activeLine = active?.let {
            """
            
            Current AI Native Page target: id=${it.id}, title=${it.title}, version=${it.version}.
            Goal: ${it.spec.goal.ifBlank { it.description.ifBlank { it.title } }}
            Must preserve: ${renderSpecList(it.spec.requiredFeatures, fallback = "all current visible features unless explicitly removed")}
            Current features: ${renderSpecList(it.spec.currentFeatures, fallback = "not summarized yet")}
            Accepted corrections: ${renderSpecList(it.spec.acceptedCorrections, fallback = "none yet")}
            Known bugs: ${renderSpecList(it.spec.knownBugs, fallback = "none recorded")}
            Last diff: ${it.spec.lastDiffSummary.ifBlank { "none" }}
            Recent history: ${renderHistory(it.history)}
            For follow-up edits like “改一下/优化/继续/调整它”, patch this page instead of creating HTML or a new page.
            Required tool flow: ui_builder(action=get) -> ui_builder(action=analyze_change) -> ui_builder(action=update) -> ui_builder(action=validate) -> ui_builder(action=open if needed).
            """.trimIndent()
        }.orEmpty()
        return "## Current AI Native Page Artifacts\n$lines$activeLine"
    }

    private fun buildMiniAppArtifactContext(activeMiniApp: MiniApp? = null): String {
        val apps = miniAppsProvider().take(5)
        if (apps.isEmpty() && activeMiniApp == null) return ""
        val active = activeMiniApp ?: apps.firstOrNull()
        val lines = apps.joinToString("\n") { mini ->
            val marker = if (mini.id == active?.id) "active" else "recent"
            "- [$marker] id=${mini.id}, title=${mini.title}, description=${mini.description.take(80)}, goal=${mini.spec.goal.take(80)}, features=${renderSpecList(mini.spec.requiredFeatures, fallback = "unspecified")}, updatedAt=${mini.updatedAt}"
        }
        val activeLine = active?.let {
            """
            
            Current MiniAPP target: id=${it.id}, title=${it.title}.
            Goal: ${it.spec.goal.ifBlank { it.description.ifBlank { it.title } }}
            Must preserve: ${renderSpecList(it.spec.requiredFeatures, fallback = "all current visible features unless explicitly removed")}
            Current features: ${renderSpecList(it.spec.currentFeatures, fallback = "not summarized yet")}
            Accepted corrections: ${renderSpecList(it.spec.acceptedCorrections, fallback = "none yet")}
            Known bugs: ${renderSpecList(it.spec.knownBugs, fallback = "none recorded")}
            Last diff: ${it.spec.lastDiffSummary.ifBlank { "none" }}
            Recent history: ${renderHistory(it.history)}
            For follow-up edits like “改一下/优化/继续/调整它”, patch this MiniAPP instead of creating a new app.
            Required tool flow: app_manager(action=analyze_change) -> app_manager(action=update) -> app_manager(action=validate) -> app_manager(action=open if needed).
            """.trimIndent()
        }.orEmpty()
        return "## Current MiniAPP Artifacts\n$lines$activeLine"
    }

    private fun renderSpecList(items: List<String>, fallback: String): String =
        items
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(8)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" | ")
            ?: fallback

    private fun renderHistory(history: List<ArtifactHistoryEntry>): String =
        history
            .takeLast(4)
            .map { entry ->
                listOf(entry.action, entry.request.ifBlank { entry.summary }.take(80))
                    .filter { it.isNotBlank() }
                    .joinToString(": ")
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" -> ")
            ?: "no prior history"

    private fun ChatMessage.isContextNoiseMessage(): Boolean {
        if (attachments.any { it is SkillAttachment.ActionCard || it is SkillAttachment.AccessibilityRequest }) return true
        val normalized = text.trim()
        if (normalized.isBlank() && attachments.isEmpty() && logLines.isEmpty() && imageBase64.isNullOrBlank()) return true
        if (role == MessageRole.AGENT && normalized in setOf("已取消。", "已切换到 手机操控。", "已切换到 手机操控", "已切换到 phone_operator。")) return true
        if (role == MessageRole.AGENT && normalized.startsWith("已打开无障碍设置")) return true
        if (role == MessageRole.USER && normalized in setOf(CANCEL_CONFIRMATION_TEXT, "已开启", "已经开了", "开了", "无障碍已开启", "无障碍开了")) return true
        if (normalized.startsWith(CONFIRM_TASK_PREFIX) ||
            normalized.startsWith(CONFIRM_ACCESSIBILITY_TASK_PREFIX) ||
            normalized.startsWith(OPEN_ACCESSIBILITY_PREFIX) ||
            normalized.startsWith(CONFIRM_ROLE_PREFIX)) return true
        return false
    }

    private fun messageContextText(msg: ChatMessage): String {
        val logSummary = msg.logLines
            .takeLast(4)
            .joinToString("\n") { line ->
                listOfNotNull(line.skillId, line.text.take(180)).joinToString(": ")
            }
        val attachmentSummary = summarizeAttachmentsForContext(msg.attachments)
        return listOf(msg.text, logSummary, attachmentSummary)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun inferAiPageContextTarget(goal: String): AiPageDef? {
        val pages = aiPagesProvider()
        if (pages.isEmpty()) return null
        val text = goal.lowercase()
        if (isMobileClawInternalChatTopic(text)) return null
        val explicit = pages.firstOrNull { page ->
            text.contains(page.id.lowercase()) ||
                page.title.isNotBlank() && text.contains(page.title.lowercase())
        }
        if (explicit != null) return explicit
        val refersToPrevious = text.anyContainsLocal(
            "它", "这个页面", "这个ui", "这个原生", "这个 aipage", "刚才", "上面", "修改",
            "改下", "改一下", "调整", "优化", "美化", "完善", "更新", "继续", "接着",
            "别这样", "不是这样", "换成", "改成",
        )
        val pageIntent = text.anyContainsLocal("页面", "原生", "native", "aipage", "ai page", "ui")
        val recentPageContext = currentConversationMentionsAiPage(pages)
        val shortFollowUp = text.length <= 30 && refersToPrevious
        return if (recentPageContext != null && (refersToPrevious && pageIntent || shortFollowUp)) recentPageContext else null
    }

    private fun inferMiniAppContextTarget(goal: String): MiniApp? {
        val apps = miniAppsProvider()
        if (apps.isEmpty()) return null
        val text = goal.lowercase()
        if (isMobileClawInternalChatTopic(text)) return null
        val explicit = apps.firstOrNull { mini ->
            (mini.id.isNotBlank() && text.contains(mini.id.lowercase())) ||
                (mini.title.isNotBlank() && text.contains(mini.title.lowercase()))
        }
        if (explicit != null) return explicit
        val refersToPrevious = isContextualFollowUp(text)
        val appIntent = text.anyContainsLocal("app", "miniapp", "mini app", "小应用", "小程序", "程序", "应用", "游戏", "网页应用")
        val recentMiniAppContext = currentConversationMentionsMiniApp(apps)
        val shortFollowUp = text.length <= 30 && refersToPrevious
        return if (recentMiniAppContext != null && ((refersToPrevious && appIntent) || shortFollowUp)) recentMiniAppContext else null
    }

    private fun inferRecentFileContextTarget(goal: String): SkillAttachment? {
        val text = goal.lowercase()
        if (isMobileClawInternalChatTopic(text)) return null
        if (!isContextualFollowUp(text)) return null
        if (isGenericContinueOnly(text) && recentEffectiveUserMessageBeforeCurrent()?.let { TaskClassifier.classify(it) } == TaskType.PHONE_CONTROL) {
            return null
        }
        if (text.anyContainsLocal("ppt", "doc", "word", "excel", "pdf", "表格", "文档", "页面", "html") &&
            text.anyContainsLocal("帮我写", "帮我做", "帮我生成", "创建", "生成", "新建", "设计")) {
            return null
        }
        val fileIntent = text.anyContainsLocal(
            "文件", "文档", "附件", "这个", "它", "上面", "刚才", "继续", "修改", "改下", "改一下",
            "优化", "更新", "打开", "保存", "导出", "ppt", "docx", "xlsx", "pdf", "csv", "markdown", "html",
        )
        if (!fileIntent) return null
        return effectiveContextMessages(limit = 8)
            .asReversed()
            .flatMap { it.attachments.asReversed() }
            .firstOrNull { it is SkillAttachment.FileData || it is SkillAttachment.HtmlData }
            ?.takeIf { !isLikelyStickerOrMediaAsset(it) }
    }

    private fun currentConversationMentionsAiPage(pages: List<AiPageDef>): AiPageDef? {
        val recent = effectiveContextMessages(limit = 4)
        if (recent.isEmpty()) return null
        val text = recent.joinToString("\n") { messageContextText(it).lowercase() }
        val explicit = pages.firstOrNull { page ->
            (page.id.isNotBlank() && text.contains(page.id.lowercase())) ||
                (page.title.isNotBlank() && text.contains(page.title.lowercase()))
        }
        if (explicit != null) return explicit
        if (text.anyContainsLocal("ai native page", "原生页面", "ai页面", "aipage") &&
            !text.anyContainsLocal("miniapp", "mini app", "小应用", "小程序", "app_manager", "应用已创建", "app '")
        ) {
            return pages.firstOrNull()
        }
        return null
    }

    private fun currentConversationMentionsMiniApp(apps: List<MiniApp>): MiniApp? {
        val recent = effectiveContextMessages(limit = 4)
        if (recent.isEmpty()) return null
        val text = recent.joinToString("\n") { msg ->
            messageContextText(msg).lowercase() + "\n" + summarizeAttachmentsForContext(msg.attachments).lowercase()
        }
        val explicit = apps.firstOrNull { mini ->
            (mini.id.isNotBlank() && text.contains(mini.id.lowercase())) ||
                (mini.title.isNotBlank() && text.contains(mini.title.lowercase()))
        }
        if (explicit != null) return explicit
        if (text.anyContainsLocal("miniapp", "mini app", "小应用", "小程序", "app_manager", "应用已创建", "app '")) {
            return apps.firstOrNull()
        }
        return null
    }

    private fun isContextualFollowUp(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized.anyContainsLocal(
            "它", "这个", "这页", "这个页面", "这个ui", "这个应用", "这个app", "这个文件", "这个文档",
            "刚才", "上面", "上一版", "前面", "继续", "接着", "然后", "改下", "改一下", "修改",
            "调整", "优化", "美化", "完善", "更新", "换成", "改成", "别这样", "不是这样", "不对",
            "重做", "再来", "继续做", "接着做", "沿用", "基于", "好", "可以", "行", "就这样", "就这个",
            "按这个", "照这个", "没问题", "嗯", "嗯嗯", "ok", "okay",
            "it", "this", "that", "previous", "continue", "change it", "update it", "optimize", "not this",
        )
    }

    private fun isGenericContinueOnly(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in setOf(
            "继续", "继续啊", "接着", "接着啊", "继续做", "继续执行", "好", "可以", "行",
            "就这样", "就这个", "按这个", "照这个", "ok", "okay", "continue", "go on",
        )
    }

    private fun recentEffectiveUserMessageBeforeCurrent(): String? =
        effectiveContextMessages(limit = 8)
            .asReversed()
            .firstOrNull { it.role == MessageRole.USER }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun isMobileClawInternalChatTopic(text: String): Boolean =
        text.anyContainsLocal(
            "聊天", "chat", "群聊", "单聊", "对话", "上下文", "乱切", "角色", "记忆",
            "vlm", "执行链路", "工具", "模型", "本地模型", "云端模型", "bug", "闪退",
        )

    private fun String.anyContainsLocal(vararg needles: String): Boolean = needles.any { contains(it) }

}

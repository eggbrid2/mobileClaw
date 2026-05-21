package com.mobileclaw.ui

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskType
import com.mobileclaw.app.MiniApp
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.aipage.AiPageDef

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
) {
    fun resolveWithAiDecision(
        goal: String,
        effectiveGoal: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
        decision: AiTaskRouteDecision,
    ): TaskRoute? {
        if (decision.taskType == null || decision.confidence < 0.52f) return null
        if (!hasImage && !hasFile && activeWorkflow != null && shouldContinueActiveWorkflow(goal, activeWorkflow)) {
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
                userVisibleSteps = decision.userVisibleSteps,
                aiRouteConfidence = decision.confidence,
                aiRouteReason = decision.reason,
                aiRouteTargetApp = decision.targetApp,
            ),
            goalForExecution = normalizedGoal,
            source = TaskRouteSource.AI_ROUTER,
            goalToRemember = normalizedGoal,
        )
    }

    fun resolveAsAgentFallback(
        goal: String,
        effectiveGoal: String,
        reason: String,
    ): TaskRoute {
        val steps = listOf(
            "路由模型暂时没有给出可靠判断，先进入通用执行模式",
            "检查当前可用能力，再选择最合适的通道继续",
            "如果需要手机、网页、文件或记忆能力，会在执行中自动调用",
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
            return TaskRoute(
                taskType = activeWorkflow.taskType,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goal,
                    taskTypeOverride = activeWorkflow.taskType,
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
            )
        }

        latestContinueOnlyRoute(goal, effectiveGoal)?.let { return it }

        val contextualIntent = resolveContextualTaskIntent(goal, hasImage, hasFile)
        val classifiedTaskType = TaskClassifier.classify(
            goal = contextualIntent.classificationGoal.ifBlank { effectiveGoal },
            hasImage = hasImage,
            hasFile = hasFile,
        )
        val taskType = inferFollowUpTaskType(goal, classifiedTaskType)
            ?: contextualIntent.taskTypeOverride
            ?: if (contextualIntent.aiPage != null && !hasImage && !hasFile) TaskType.APP_BUILD else classifiedTaskType
        return TaskRoute(
            taskType = taskType,
            contextualIntent = contextualIntent,
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.CLASSIFIER,
        )
    }

    private fun latestContinueOnlyRoute(goal: String, effectiveGoal: String): TaskRoute? {
        if (!isGenericContinueOnly(goal)) return null
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
            )
        }

        val inferredTaskType = inferTaskTypeFromMessage(latest)
        if (inferredTaskType != null) {
            return TaskRoute(
                taskType = inferredTaskType,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goal,
                    taskTypeOverride = inferredTaskType,
                    executionHint = "The user's short follow-up refers to the latest meaningful ${inferredTaskType.name} task in this chat. Continue that task using the newest relevant chat records, not an older unrelated thread.",
                ),
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.RECENT_CONTEXT,
                goalToRemember = recentAnchorGoal(recent, effectiveGoal),
            )
        }

        return TaskRoute(
            taskType = TaskType.GENERAL,
            contextualIntent = ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.GENERAL,
                executionHint = "The user's short follow-up refers to the latest conversational thread. Answer based on the newest relevant user intent; do not start an unrelated artifact or tool workflow.",
            ),
            goalForExecution = effectiveGoal,
            source = TaskRouteSource.RECENT_CONTEXT,
        )
    }

    fun applyContextualTaskConstraints(
        effectiveGoal: String,
        intent: ContextualTaskIntent,
        taskType: TaskType,
    ): String = effectiveGoal

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
                is SkillAttachment.ImageData -> "image(prompt=${attachment.prompt.orEmpty().take(60)})"
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

    private fun shouldContinueActiveWorkflow(goal: String, workflow: ActiveWorkflow): Boolean {
        val text = goal.trim().lowercase()
        if (text.isBlank()) return false
        if (TaskClassifier.classify(goal) !in listOf(TaskType.CHAT, TaskType.GENERAL)) return false
        if (isMobileClawInternalChatTopic(text)) return false
        if (isGenericContinueOnly(text) || isContextualFollowUp(text)) return true
        if (text.length <= 28 && workflow.taskType == TaskType.PHONE_CONTROL) return true
        return false
    }

    private fun resolveContextualTaskIntent(
        goal: String,
        hasImage: Boolean,
        hasFile: Boolean,
    ): ContextualTaskIntent {
        if (hasImage || hasFile) return ContextualTaskIntent(goal)
        val text = goal.lowercase()
        val recentArtifact = inferRecentFileContextTarget(goal)
        if (recentArtifact != null && shouldUseRecentFileContext(text, recentArtifact)) {
            return recentFileContextIntent(goal, recentArtifact)
        }
        val aiPage = inferAiPageContextTarget(goal)
        if (aiPage != null) {
            return ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                aiPage = aiPage,
                executionHint = "The user is referring to an existing AI Native Page. Update that page instead of creating HTML or a new unrelated artifact.",
            )
        }

        val miniApp = inferMiniAppContextTarget(goal)
        if (miniApp != null) {
            return ContextualTaskIntent(
                classificationGoal = goal,
                taskTypeOverride = TaskType.APP_BUILD,
                miniApp = miniApp,
                executionHint = "The user is referring to an existing MiniAPP. Update/open that MiniAPP instead of creating a new unrelated artifact.",
            )
        }

        if (recentArtifact != null) return recentFileContextIntent(goal, recentArtifact)
        return ContextualTaskIntent(goal)
    }

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
            executionHint = "The user is referring to the latest relevant file or HTML artifact in this chat. Continue from that artifact, unless the user explicitly asks for a new one.",
        )
    }

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
            "- [$marker] id=${page.id}, title=${page.title}, version=${page.version}, description=${page.description.take(80)}"
        }
        val activeLine = active?.let {
            "\nCurrent AI Native Page target: id=${it.id}, title=${it.title}, version=${it.version}. For follow-up edits like “改一下/优化/继续/调整它”, use ui_builder(action=get/update/open) with this id. Do not use create_html and do not create a new page unless the user explicitly asks."
        }.orEmpty()
        return "## Current AI Native Page Artifacts\n$lines$activeLine"
    }

    private fun buildMiniAppArtifactContext(activeMiniApp: MiniApp? = null): String {
        val apps = miniAppsProvider().take(5)
        if (apps.isEmpty() && activeMiniApp == null) return ""
        val active = activeMiniApp ?: apps.firstOrNull()
        val lines = apps.joinToString("\n") { mini ->
            val marker = if (mini.id == active?.id) "active" else "recent"
            "- [$marker] id=${mini.id}, title=${mini.title}, description=${mini.description.take(80)}, updatedAt=${mini.updatedAt}"
        }
        val activeLine = active?.let {
            "\nCurrent MiniAPP target: id=${it.id}, title=${it.title}. For follow-up edits like “改一下/优化/继续/调整它”, use app_manager(action=update/open) with this id. Do not create a new app unless the user explicitly asks."
        }.orEmpty()
        return "## Current MiniAPP Artifacts\n$lines$activeLine"
    }

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
        if (text.anyContainsLocal("ai native page", "原生页面", "ai页面", "aipage", "ui_builder")) {
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

    private companion object {
        const val CONFIRM_TASK_PREFIX = "确认执行:"
        const val CONFIRM_ACCESSIBILITY_TASK_PREFIX = "确认无障碍并执行:"
        const val OPEN_ACCESSIBILITY_PREFIX = "打开无障碍设置:"
        const val CONFIRM_ROLE_PREFIX = "确认切换角色:"
        const val CANCEL_CONFIRMATION_TEXT = "取消"
        val PHONE_CONTROL_SKILLS = setOf(
            "see_screen", "screenshot", "tap", "scroll", "input_text", "long_click",
            "navigate", "list_apps", "phone_status", "check_permissions",
        )
        val APP_BUILD_SKILLS = setOf("ui_builder", "app_manager", "create_html")
        val FILE_SKILLS = setOf("generate_document", "create_file", "read_file", "list_files")
        val WEB_SKILLS = setOf("web_search", "fetch_url", "web_browse", "web_content", "web_js")
        val IMAGE_SKILLS = setOf("generate_image", "generate_icon", "generate_video")
        val CODE_SKILLS = setOf("shell", "run_python", "pip_install")
    }
}

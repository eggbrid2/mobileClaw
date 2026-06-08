package com.mobileclaw.ui.chat

import com.mobileclaw.agent.TaskType
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.ContextualTaskIntent

internal class ChatContextComposer(
    private val effectiveMessages: () -> List<ChatMessage>,
    private val summarizeAttachments: (List<SkillAttachment>) -> String,
    private val buildArtifactContext: (ContextualTaskIntent) -> String,
    private val buildWorkspaceContext: () -> String,
    private val buildUserMemoryContext: (String, TaskType) -> String,
) {
    fun buildPriorContext(
        goal: String,
        taskType: TaskType = TaskType.GENERAL,
        intent: ContextualTaskIntent = ContextualTaskIntent(goal),
        includeMemory: Boolean = true,
        includeRecentMessages: Boolean = true,
    ): String {
        val messages = effectiveMessages().takeLast(12)
        val userMemoryContext = if (includeMemory) buildUserMemoryContext(goal, taskType) else ""
        val artifactContext = buildArtifactContext(intent)
        val workspaceContext = buildWorkspaceContext()
        if (!includeRecentMessages || messages.isEmpty()) {
            return listOf(userMemoryContext, workspaceContext, artifactContext).filter { it.isNotBlank() }.joinToString("\n\n")
        }

        val recent = if (taskType == TaskType.APP_BUILD) {
            val artifactFiltered = messages.filter { msg -> isArtifactRelevantMessage(msg, intent) }
            (artifactFiltered.ifEmpty { messages.takeLast(6) }).takeLast(6)
        } else {
            messages.takeLast(10)
        }
        val lines = recent.map { msg -> historyText(msg, compact = true, includeAttachmentSummary = true) }
        val full = lines.joinToString("\n")
        val capped = if (full.length > 1800) full.takeLast(1800) else full
        val recentContext = if (capped.isNotBlank()) {
            """
            ## Recent Chat Context
            Use these records only to resolve references in the latest user message. Newer records are stronger than older ones. Do not revive an older task when the latest user intent points elsewhere.
            $capped

            Latest user message: ${goal.take(220)}
            """.trimIndent()
        } else ""
        return listOf(userMemoryContext, workspaceContext, artifactContext, recentContext).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun buildStructuredDirectChatMessages(
        sessionMessages: List<ChatMessage>,
        systemPrompt: String,
        currentGoal: String,
        imageBase64: String? = null,
    ): List<Message> {
        val history = sessionMessages
            .dropLast(1)
            .filter { it.text.isNotBlank() || it.imageBase64 != null || it.attachments.isNotEmpty() }
            .takeLast(12)
            .mapNotNull { msg ->
                val text = historyText(msg, compact = false, includeAttachmentSummary = true)
                if (text.isBlank() && msg.imageBase64.isNullOrBlank()) return@mapNotNull null
                Message(
                    role = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content = text.ifBlank { null },
                    imageBase64 = msg.imageBase64,
                )
            }
        return listOf(Message(role = "system", content = systemPrompt)) +
            history +
            Message(role = "user", content = currentGoal, imageBase64 = imageBase64)
    }

    fun historyText(
        msg: ChatMessage,
        compact: Boolean,
        includeAttachmentSummary: Boolean,
    ): String {
        val base = msg.text
            .replace(Regex("```[\\s\\S]*?```"), "[code/file content omitted]")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val rolePrefix = if (compact && msg.role == MessageRole.AGENT && msg.senderRoleName.isNotBlank()) {
            "Assistant (${msg.senderRoleName}): "
        } else ""
        val attachmentSummary = if (includeAttachmentSummary) {
            summarizeAttachments(msg.attachments)
                .takeIf { it.isNotBlank() }
                ?.let { if (compact) "\n  attachments: $it" else "\n[attachments: $it]" }
                .orEmpty()
        } else {
            ""
        }
        val text = "$rolePrefix$base$attachmentSummary".trim()
        val limit = if (compact) 260 else 900
        return if (text.length > limit) text.take(limit) + "…" else text
    }

    private fun isArtifactRelevantMessage(
        msg: ChatMessage,
        intent: ContextualTaskIntent,
    ): Boolean {
        if (msg.attachments.any { it is SkillAttachment.HtmlData || it is SkillAttachment.FileData }) return true
        if (msg.logLines.any { it.skillId in setOf("ui_builder", "app_manager", "create_html", "create_file", "read_file") }) return true
        val text = historyText(msg, compact = false, includeAttachmentSummary = true).lowercase()
        val targetPage = intent.aiPage
        val targetApp = intent.miniApp
        if (targetPage != null && (text.contains(targetPage.id.lowercase()) || text.contains(targetPage.title.lowercase()))) return true
        if (targetApp != null && (text.contains(targetApp.id.lowercase()) || text.contains(targetApp.title.lowercase()))) return true
        return text.contains("页面") ||
            text.contains("ui_builder") ||
            text.contains("miniapp") ||
            text.contains("app_manager") ||
            text.contains("html") ||
            text.contains("原生页面") ||
            text.contains("应用")
    }
}

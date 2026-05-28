package com.mobileclaw.ui.group

import com.mobileclaw.ClawApplication
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskToolPolicy
import com.mobileclaw.agent.TaskType
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillRegistry
import com.mobileclaw.skill.builtin.ChineseBqbStickerRepository
import kotlinx.coroutines.withTimeoutOrNull

internal data class GroupTurnResult(
    val text: String,
    val attachments: List<SkillAttachment> = emptyList(),
)

internal class GroupTurnExecutor(
    private val app: ClawApplication,
    private val llmProvider: () -> LlmGateway,
    private val registry: SkillRegistry,
) {
    private val recentGroupStickerPathsLock = Any()
    private val recentGroupStickerPaths = ArrayDeque<String>()

    suspend fun runTurn(
        role: Role,
        baseMessages: List<Message>,
        taskType: TaskType,
        memoryContext: String = "",
        allowedToolIds: List<String> = emptyList(),
        maxSkillCalls: Int = 3,
        requireResponse: Boolean = false,
        shouldStop: () -> Boolean,
        onToolStart: (skillId: String, params: Map<String, Any>) -> Unit = { _, _ -> },
        onToolEnd: (output: String) -> Unit = {},
    ): GroupTurnResult {
        val groupMetas = TaskToolPolicy.select(
            registry = registry,
            taskType = taskType,
            goal = baseMessages.lastOrNull()?.content.orEmpty(),
            forcedSkillIds = role.forcedSkillIds,
            memoryContext = memoryContext,
        )
            .let { metas ->
                val allowed = allowedToolIds.toSet()
                val forced = role.forcedSkillIds.toSet()
                if (allowed.isEmpty()) {
                    metas
                } else {
                    metas.filter { it.id in allowed || it.id in forced || !it.isBuiltin }
                        .ifEmpty { metas }
                }
            }
            .let { metas ->
                if (taskType in listOf(TaskType.CHAT, TaskType.GENERAL)) {
                    (metas + listOfNotNull(registry.get("role_manager")?.meta)).distinctBy { it.id }
                } else {
                    metas
                }
            }

        val tools = groupMetas.map { meta ->
            ToolDefinition(
                name = meta.id,
                description = meta.description,
                parameters = ToolParameters(
                    properties = meta.parameters.associate { param -> param.name to ToolProperty(param.type, param.description) },
                    required = meta.parameters.filter { it.required }.map { it.name },
                ),
            )
        }

        val llm = llmProvider()
        val messages = baseMessages.toMutableList()
        var finalText = ""
        val attachments = mutableListOf<SkillAttachment>()

        repeat(maxSkillCalls + 1) { iteration ->
            if (shouldStop()) return GroupTurnResult(finalText, attachments)

            var accumulated = ""
            val resp = runCatching {
                withTimeoutOrNull(35_000L) {
                    llm.chat(
                        ChatRequest(
                            messages = messages,
                            tools = if (iteration < maxSkillCalls) tools else emptyList(),
                            stream = true,
                            onToken = { token -> accumulated += token },
                            onThinkToken = null,
                        ),
                    )
                }
            }.getOrElse {
                return GroupTurnResult(finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "[PASS]" }, attachments)
            } ?: return GroupTurnResult(finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "[PASS]" }, attachments)

            if (resp.toolCall == null) {
                finalText = accumulated.ifBlank { resp.content ?: "" }
                if (requireResponse && (finalText.isBlank() || finalText.trim().equals("[PASS]", ignoreCase = true))) {
                    finalText = fallbackGroupReply(role, baseMessages)
                }
                val cleanedAttachments = normalizeAttachments(attachments)
                val autoSticker = if (cleanedAttachments.none { it.isStickerLikeAttachment() || it is SkillAttachment.ImageData }) {
                    maybeCreateStickerAttachment(finalText, role.id)
                } else {
                    null
                }
                return GroupTurnResult(
                    text = finalText,
                    attachments = normalizeAttachments(
                        if (autoSticker != null) cleanedAttachments + autoSticker else cleanedAttachments,
                    ),
                )
            }

            val toolCall = resp.toolCall
            onToolStart(toolCall.skillId, toolCall.params)
            val skillResult = registry.get(toolCall.skillId)
                ?.let { runCatching { it.execute(toolCall.params) }.getOrElse { error -> com.mobileclaw.skill.SkillResult(false, "Error: ${error.message}") } }
                ?: com.mobileclaw.skill.SkillResult(false, "Skill '${toolCall.skillId}' not found")
            onToolEnd(skillResult.output.take(3000))

            (skillResult.data as? SkillAttachment)?.let { attachment ->
                if (attachment.isStickerLikeAttachment() || attachment is SkillAttachment.ImageData) {
                    attachments.removeAll { it.isStickerLikeAttachment() || it is SkillAttachment.ImageData }
                }
                attachments += attachment
                rememberGroupStickerAttachment(attachment)
            }
            skillResult.imageBase64?.takeIf { it.isNotBlank() }?.let { base64 ->
                attachments += SkillAttachment.ImageData(base64, prompt = "Generated by ${toolCall.skillId}")
            }

            messages += Message(role = "assistant", content = accumulated.ifBlank { null }, toolCalls = listOf(toolCall))
            messages += Message(role = "tool", content = skillResult.output.take(3000), toolCallId = toolCall.id)
        }

        return GroupTurnResult(
            text = finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "" },
            attachments = normalizeAttachments(attachments),
        )
    }

    private suspend fun maybeCreateStickerAttachment(text: String, roleId: String): SkillAttachment.FileData? {
        val query = stickerQueryForText(text) ?: return null
        return withTimeoutOrNull<SkillAttachment.FileData?>(2500L) {
            runCatching {
                val entries = ChineseBqbStickerRepository.search(app, query, limit = 48)
                    .let { list ->
                        if (list.isEmpty()) {
                            list
                        } else {
                            val window = minOf(list.size, 7)
                            val salt = kotlin.math.abs((roleId + query + System.currentTimeMillis() / 30_000L).hashCode())
                            list.drop(salt % window) + list.take(salt % window)
                        }
                    }
                for (entry in entries.take(16)) {
                    val sticker = ChineseBqbStickerRepository.download(app, entry)
                    val isRecent = synchronized(recentGroupStickerPathsLock) {
                        sticker.path in recentGroupStickerPaths
                    }
                    if (!isRecent) {
                        rememberGroupStickerAttachment(sticker)
                        return@withTimeoutOrNull sticker
                    }
                }
                entries.firstOrNull()?.let { ChineseBqbStickerRepository.download(app, it).also(::rememberGroupStickerAttachment) }
            }.getOrNull()
        }
    }

    private fun normalizeAttachments(attachments: List<SkillAttachment>): List<SkillAttachment> {
        var stickerOrImageAdded = false
        return attachments.asReversed().filter { attachment ->
            if (attachment.isStickerLikeAttachment() || attachment is SkillAttachment.ImageData) {
                if (stickerOrImageAdded) {
                    false
                } else {
                    stickerOrImageAdded = true
                    rememberGroupStickerAttachment(attachment)
                    true
                }
            } else {
                true
            }
        }.asReversed()
    }

    private fun SkillAttachment.isStickerLikeAttachment(): Boolean =
        this is SkillAttachment.FileData && (
            path.contains("/stickers/", ignoreCase = true) ||
                name.contains("bqb", ignoreCase = true) ||
                mimeType.startsWith("image/")
        )

    private fun rememberGroupStickerAttachment(attachment: SkillAttachment) {
        val path = when (attachment) {
            is SkillAttachment.FileData -> attachment.path
            is SkillAttachment.ImageData -> attachment.base64.take(48)
            else -> return
        }
        if (path.isBlank()) return
        synchronized(recentGroupStickerPathsLock) {
            recentGroupStickerPaths.remove(path)
            recentGroupStickerPaths.addLast(path)
            while (recentGroupStickerPaths.size > 18) recentGroupStickerPaths.removeFirst()
        }
    }
}

package com.mobileclaw.ui.chat

import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.MainUiState
import java.util.UUID

// 单聊会话运行态独立出来，避免 UiState 文件继续承载聊天 feature 的细节模型。
data class SessionRunState(
    val isRunning: Boolean = false,
    val runStartedAt: Long = 0L,
    val messages: List<ChatMessage> = emptyList(),
    val activeLogLines: List<LogLine> = emptyList(),
    val streamingToken: String = "",
    val streamingThought: String = "",
    val activeAttachments: List<SkillAttachment> = emptyList(),
)

val MainUiState.currentRunState: SessionRunState
    get() = sessionStates[currentSessionId] ?: SessionRunState()

data class FileAttachment(
    val name: String,
    val content: String,
    val isText: Boolean,
    val mimeType: String = "text/plain",
)

data class ChatMessage(
    val role: MessageRole,
    val text: String,
    val logLines: List<LogLine> = emptyList(),
    val imageBase64: String? = null,
    val attachments: List<SkillAttachment> = emptyList(),
    val imageLocalPath: String = "",
    val senderRoleId: String = "",
    val senderRoleName: String = "",
    val senderRoleAvatar: String = "",
)

enum class MessageRole { USER, AGENT }

data class LogLine(
    val entryId: String = UUID.randomUUID().toString(),
    val type: LogType,
    val text: String,
    val skillId: String? = null,
    val imageBase64: String? = null,
    val details: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val isRunning: Boolean = false,
)

data class AiQuizQuestion(
    val question: String,
    val hint: String,
    val answers: List<String>,
    val factKey: String,
)

enum class LogType { INFO, ACTION, OBSERVATION, SUCCESS, ERROR, THINKING }

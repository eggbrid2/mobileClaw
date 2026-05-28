package com.mobileclaw.ui.group

import com.mobileclaw.agent.Group
import com.mobileclaw.skill.SkillAttachment

// 群聊消息模型单独抽到 feature 包，避免继续把 group 细节塞在全局 UiState 文件里。
data class GroupMessage(
    val id: Long = 0,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val text: String,
    val attachments: List<SkillAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

// 群列表预览同样归属 group feature，后续群列表页面迁包时可以直接复用。
data class GroupPreview(
    val senderName: String,
    val text: String,
    val createdAt: Long,
)

// 群聊 feature 运行时状态统一归档，后续迁 GroupsPage / GroupChatScreen 到 feature 包时直接复用。
data class GroupUiState(
    val groups: List<Group> = emptyList(),
    val previews: Map<String, GroupPreview> = emptyMap(),
    val openGroup: Group? = null,
    val messages: List<GroupMessage> = emptyList(),
    val historyOffset: Int = 0,
    val historyHasMore: Boolean = false,
    val historyLoading: Boolean = false,
    val isRunning: Boolean = false,
    val typingAgents: Set<String> = emptySet(),
    val workingAgents: Set<String> = emptySet(),
    val pendingMessages: List<String> = emptyList(),
    val unreadCount: Int = 0,
)

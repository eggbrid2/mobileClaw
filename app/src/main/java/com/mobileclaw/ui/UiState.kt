package com.mobileclaw.ui

import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role
import com.mobileclaw.app.MiniApp
import com.mobileclaw.config.ConfigEntry
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.memory.db.EpisodeEntity
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import kotlinx.coroutines.flow.Flow

enum class AppPage { HOME, CHAT, SETTINGS, SKILLS, SKILL_MARKET, PROFILE, ROLES, USER_CONFIG, APPS, CONSOLE, HELP, GROUPS, GROUP_CHAT, BROWSER }

data class MainUiState(
    val isRunning: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val activeLogLines: List<LogLine> = emptyList(),
    val streamingToken: String = "",
    val streamingThought: String = "",
    val currentPage: AppPage = AppPage.CHAT,
    val canNavigateBack: Boolean = false,
    val userAvatarUri: String? = null,
    val promotableSkills: List<SkillMeta> = emptyList(),
    val allSkills: List<SkillMeta> = emptyList(),
    val config: Flow<ConfigSnapshot>,
    val isConfigured: Boolean = false,
    val virtualDisplayTestResult: String? = null,
    val inputImageBase64: String? = null,
    val profileFacts: Map<String, String> = emptyMap(),
    val recentEpisodes: List<EpisodeEntity> = emptyList(),
    val profileLoading: Boolean = false,
    val profileExtracting: Boolean = false,
    val conversationCount: Int = 0,
    val privServerConnected: Boolean = false,
    val currentModel: String = "gpt-4o",
    val availableModels: List<String> = emptyList(),
    val activeAttachments: List<SkillAttachment> = emptyList(),
    // Sessions
    val currentSessionId: String = "",
    val sessions: List<SessionEntity> = emptyList(),
    // Roles
    val currentRole: Role = Role.DEFAULT,
    val availableRoles: List<Role> = emptyList(),
    // Dynamic user config (value + optional description per key)
    val userConfigEntries: Map<String, ConfigEntry> = emptyMap(),
    // History-based recommendations
    val recommendations: List<String> = emptyList(),
    // AI personality summary
    val personalitySummary: String = "",
    val personalitySummaryLoading: Boolean = false,
    // AI-generated quiz questions per dimension (loaded on demand per session)
    val dimensionQuizzes: Map<String, List<AiQuizQuestion>> = emptyMap(),
    val dimensionQuizLoading: String? = null,
    // Mini-apps
    val miniApps: List<MiniApp> = emptyList(),
    val openAppId: String? = null,
    // HTML attachment viewer (shown at activity level to keep addJavascriptInterface binding)
    val openHtmlAttachment: SkillAttachment.HtmlData? = null,
    // LAN console server URL
    val consoleServerUrl: String = "",
    // Per-skill user notes
    val skillNotes: Map<String, String> = emptyMap(),
    val skillNoteGenerating: String? = null,
    // Built-in browser
    val browserUrl: String = "",
    // Group chat
    val groups: List<Group> = emptyList(),
    val openGroup: Group? = null,
    val groupMessages: List<GroupMessage> = emptyList(),
    val groupRunning: Boolean = false,
    val groupTypingAgentId: String? = null,   // role id currently streaming
    val groupStreamingText: String = "",       // partial streamed text for typing agent
    val groupUnreadCount: Int = 0,            // messages received while away from GROUP_CHAT page
)

data class GroupMessage(
    val id: Long = 0,
    val groupId: String,
    val senderId: String,      // role id or "user"
    val senderName: String,
    val senderAvatar: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ChatMessage(
    val role: MessageRole,
    val text: String,
    val logLines: List<LogLine> = emptyList(),
    val imageBase64: String? = null,
    val attachments: List<SkillAttachment> = emptyList(),
)

enum class MessageRole { USER, AGENT }

data class LogLine(val type: LogType, val text: String, val skillId: String? = null, val imageBase64: String? = null)

data class AiQuizQuestion(
    val question: String,
    val hint: String,
    val answers: List<String>,
    val factKey: String,
)

enum class LogType { INFO, ACTION, OBSERVATION, SUCCESS, ERROR, THINKING }

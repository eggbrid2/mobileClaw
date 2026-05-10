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
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.vpn.VpnStatus
import com.mobileclaw.vpn.VpnSubscription
import kotlinx.coroutines.flow.Flow

enum class AppPage { HOME, CHAT, SETTINGS, SKILLS, SKILL_MARKET, PROFILE, ROLES, ROLE_EDIT, USER_CONFIG, APPS, CONSOLE, HELP, GROUPS, GROUP_CHAT, BROWSER, AI_PAGES, VPN }

const val LATENCY_TESTING = -1L
const val LATENCY_ERROR   = -2L

/** Per-session running state — each session can have an independent task running. */
data class SessionRunState(
    val isRunning: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val activeLogLines: List<LogLine> = emptyList(),
    val streamingToken: String = "",
    val streamingThought: String = "",
    val activeAttachments: List<SkillAttachment> = emptyList(),
)

val MainUiState.currentRunState: SessionRunState
    get() = sessionStates[currentSessionId] ?: SessionRunState()

data class MainUiState(
    // Per-session run states (task may run in multiple sessions simultaneously)
    val sessionStates: Map<String, SessionRunState> = emptyMap(),
    // Convenience flat accessors kept as computed: use currentRunState extension instead
    val currentPage: AppPage = AppPage.CHAT,
    val canNavigateBack: Boolean = false,
    val userAvatarUri: String? = null,
    val promotableSkills: List<SkillMeta> = emptyList(),
    val allSkills: List<SkillMeta> = emptyList(),
    val config: Flow<ConfigSnapshot>,
    val isConfigured: Boolean = false,
    val supportsMultimodal: Boolean = true,
    val virtualDisplayTestResult: String? = null,
    val inputImageBase64: String? = null,
    val inputFileAttachment: FileAttachment? = null,
    val profileFacts: Map<String, String> = emptyMap(),
    val recentEpisodes: List<EpisodeEntity> = emptyList(),
    val profileLoading: Boolean = false,
    val profileExtracting: Boolean = false,
    val conversationCount: Int = 0,
    val privServerConnected: Boolean = false,
    val currentModel: String = "gpt-4o",
    val availableModels: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    // Sessions
    val currentSessionId: String = "",
    val sessions: List<SessionEntity> = emptyList(),
    // Roles
    val currentRole: Role = Role.DEFAULT,
    val availableRoles: List<Role> = emptyList(),
    val editingRole: Role? = null,
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
    val htmlAttachmentStack: List<SkillAttachment.HtmlData> = emptyList(),
    // LAN console server URL
    val consoleServerUrl: String = "",
    // Per-skill user notes
    val skillNotes: Map<String, String> = emptyMap(),
    val skillNoteGenerating: String? = null,
    // Per-skill injection level overrides (built-in skills can be demoted to save tokens)
    val skillLevelOverrides: Map<String, Int> = emptyMap(),
    // Built-in browser
    val browserUrl: String = "",
    // History pagination
    val historyLoading: Boolean = false,
    val historyHasMore: Boolean = false,
    val historyOffset: Int = 0,
    // AI-created native pages
    val aiPages: List<AiPageDef> = emptyList(),
    val openAiPageId: String? = null,
    // VPN
    val vpnStatus: VpnStatus = VpnStatus.IDLE,
    val vpnSubscriptions: List<VpnSubscription> = emptyList(),
    val vpnActiveProxyName: String? = null,
    val vpnAddingSubscription: Boolean = false,
    // Speed test: proxyId -> latency ms; LATENCY_TESTING = in progress, LATENCY_ERROR = failed
    val vpnLatencies: Map<String, Long> = emptyMap(),
    // Group chat
    val groups: List<Group> = emptyList(),
    val groupPreviews: Map<String, GroupPreview> = emptyMap(),
    val openGroup: Group? = null,
    val groupMessages: List<GroupMessage> = emptyList(),
    val groupRunning: Boolean = false,
    val groupTypingAgents: Set<String> = emptySet(),   // roleIds currently running inference
    val groupWorkingAgents: Set<String> = emptySet(),  // roleIds currently using tools / doing long work
    val groupPendingMessages: List<String> = emptyList(), // user msgs queued while agents are active
    val groupUnreadCount: Int = 0,            // messages received while away from GROUP_CHAT page
)

data class FileAttachment(
    val name: String,
    val content: String,   // text content, or base64 for binary
    val isText: Boolean,   // true = text file (inject into prompt), false = image (use vision)
    val mimeType: String = "text/plain",
)

data class GroupMessage(
    val id: Long = 0,
    val groupId: String,
    val senderId: String,      // role id or "user"
    val senderName: String,
    val senderAvatar: String,
    val text: String,
    val attachments: List<SkillAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class GroupPreview(
    val senderName: String,
    val text: String,
    val createdAt: Long,
)

data class ChatMessage(
    val role: MessageRole,
    val text: String,
    val logLines: List<LogLine> = emptyList(),
    val imageBase64: String? = null,
    val attachments: List<SkillAttachment> = emptyList(),
)

enum class MessageRole { USER, AGENT }

data class LogLine(
    val type: LogType,
    val text: String,
    val skillId: String? = null,
    val imageBase64: String? = null,
    val details: List<String> = emptyList(),   // fine-grained sub-details for the detail sheet
)

data class AiQuizQuestion(
    val question: String,
    val hint: String,
    val answers: List<String>,
    val factKey: String,
)

enum class LogType { INFO, ACTION, OBSERVATION, SUCCESS, ERROR, THINKING }

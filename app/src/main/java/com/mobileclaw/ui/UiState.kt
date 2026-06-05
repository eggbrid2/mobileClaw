package com.mobileclaw.ui

import com.mobileclaw.agent.Role
import com.mobileclaw.app.MiniApp
import com.mobileclaw.config.ConfigEntry
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.llm.LocalModelInfo
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.memory.db.VideoGenerationTaskEntity
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.town.AgentTownState
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.ui.chat.FileAttachment
import com.mobileclaw.ui.chat.SessionRunState
import com.mobileclaw.ui.group.GroupUiState
import com.mobileclaw.ui.profile.ProfileUiState
import com.mobileclaw.vpn.VpnStatus
import com.mobileclaw.vpn.VpnSubscription
import com.mobileclaw.ui.workspace.WorkspaceUiState
import kotlinx.coroutines.flow.Flow

enum class AppPage { HOME, CHAT, SETTINGS, SKILLS, SKILL_MARKET, PROFILE, ROLES, ROLE_DETAIL, ROLE_EDIT, USER_CONFIG, APPS, CONSOLE, HELP, GROUPS, GROUP_CHAT, BROWSER, AI_PAGES, VPN, AI_TOWN, WORKSPACE, IMAGE_GENERATOR, VIDEO_GENERATOR }

const val LATENCY_TESTING = -1L
const val LATENCY_ERROR   = -2L

data class MainUiState(
    // Per-session run states (task may run in multiple sessions simultaneously)
    val sessionStates: Map<String, SessionRunState> = emptyMap(),
    // Convenience flat accessors kept as computed: use currentRunState extension instead
    val currentPage: AppPage = AppPage.HOME,
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
    val profileState: ProfileUiState = ProfileUiState(),
    val privServerConnected: Boolean = false,
    val currentModel: String = "gpt-4o",
    val availableModels: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val localModels: List<LocalModelInfo> = emptyList(),
    // Role Home data
    val agentTown: AgentTownState = AgentTownState(),
    val openTownRoleId: String? = null,
    val rolePortraitGeneratingIds: Set<String> = emptySet(),
    val workspaceState: WorkspaceUiState = WorkspaceUiState(),
    // Sessions
    val currentSessionId: String = "",
    val sessions: List<SessionEntity> = emptyList(),
    val codexDesktopMode: Boolean = false,
    val codexDesktopSessionIds: Set<String> = emptySet(),
    // Roles
    val currentRole: Role = Role.DEFAULT,
    val availableRoles: List<Role> = emptyList(),
    val detailRole: Role? = null,
    val editingRole: Role? = null,
    // Dynamic user config (value + optional description per key)
    val userConfigEntries: Map<String, ConfigEntry> = emptyMap(),
    // History-based recommendations
    val recommendations: List<String> = emptyList(),
    // Mini-apps
    val miniApps: List<MiniApp> = emptyList(),
    val openAppId: String? = null,
    val chatMiniAppPreviewId: String? = null,
    val chatMiniAppPreviewMode: String = "",
    val chatMiniAppPreviewSessionId: String? = null,
    val chatMiniAppPreviewStatus: String = "",
    val chatMiniAppPreviewHealthy: Boolean = true,
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
    val groupState: GroupUiState = GroupUiState(),
    val videoTasks: List<VideoGenerationTaskEntity> = emptyList(),
    val videoTaskRefreshingIds: Set<String> = emptySet(),
    val videoTasksRefreshing: Boolean = false,
    val videoGenerationRunning: Boolean = false,
    val videoPromptAiRunning: Boolean = false,
    val imageGenerationRunning: Boolean = false,
    val imagePromptAiRunning: Boolean = false,
    val imageGenerationPreviewBase64: String = "",
    val imageGenerationPreviewPrompt: String = "",
)

package com.mobileclaw.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.app.MiniAppPreflightValidator
import com.mobileclaw.agent.AgentEvent
import com.mobileclaw.agent.AiIntentRouter
import com.mobileclaw.agent.AgentRuntime
import com.mobileclaw.agent.AgentWorkspaceUpdate
import com.mobileclaw.agent.ChatBubbleStyle
import com.mobileclaw.agent.ChannelType
import com.mobileclaw.agent.ChannelPermissionPolicy
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleAvatarDefaults
import com.mobileclaw.agent.RoleScheduler
import com.mobileclaw.agent.TaskOrchestrator
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskToolPolicy
import com.mobileclaw.agent.TaskType
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.GatewayCapabilityConfig
import com.mobileclaw.config.capabilityApiKey
import com.mobileclaw.config.capabilityEndpoint
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.config.hasCapability
import com.mobileclaw.config.supportsCapabilityMultimodal
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.Message
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.llm.cleanLocalGeneratedText
import com.mobileclaw.llm.decodeLocalTokenizerSpacing
import com.mobileclaw.memory.EpisodicMemory
import com.mobileclaw.memory.MemoryContextBuilder
import com.mobileclaw.memory.MemoryWriter
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.memory.db.SessionMessageEntity
import com.mobileclaw.perception.ClawAccessibilityService
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.builtin.BgLaunchSkill
import com.mobileclaw.skill.builtin.BgReadScreenSkill
import com.mobileclaw.skill.builtin.BgScreenshotSkill
import com.mobileclaw.skill.builtin.BgStopSkill
import com.mobileclaw.skill.builtin.VirtualDisplaySetupSkill
import com.mobileclaw.skill.builtin.ClipboardSkill
import com.mobileclaw.skill.builtin.ChineseBqbStickerSkill
import com.mobileclaw.skill.builtin.ChineseBqbStickerRepository
import com.mobileclaw.skill.builtin.CloudinaryImageUploader
import com.mobileclaw.skill.builtin.CodexDesktopSkill
import com.mobileclaw.skill.builtin.CreateFileSkill
import com.mobileclaw.skill.builtin.CreateHtmlSkill
import com.mobileclaw.skill.builtin.DeviceInfoSkill
import com.mobileclaw.skill.builtin.ListFilesSkill
import com.mobileclaw.skill.builtin.ReadFileSkill
import com.mobileclaw.skill.builtin.ShowToastSkill
import com.mobileclaw.skill.builtin.FetchUrlSkill
import com.mobileclaw.skill.builtin.GenerateDocumentSkill
import com.mobileclaw.skill.builtin.GenerateIconSkill
import com.mobileclaw.skill.builtin.GenerateImageSkill
import com.mobileclaw.skill.builtin.GenerateVideoSkill
import com.mobileclaw.skill.builtin.HouseArtistSkill
import com.mobileclaw.skill.builtin.InputTextSkill
import com.mobileclaw.skill.builtin.ListAppsSkill
import com.mobileclaw.skill.builtin.LongClickSkill
import com.mobileclaw.skill.builtin.MemorySkill
import com.mobileclaw.skill.builtin.MetaSkill
import com.mobileclaw.skill.builtin.McpClientSkill
import com.mobileclaw.skill.builtin.NavigateSkill
import com.mobileclaw.skill.builtin.PageControlSkill
import com.mobileclaw.skill.builtin.PermissionSkill
import com.mobileclaw.skill.builtin.PhoneStatusSkill
import com.mobileclaw.skill.builtin.PgyerReleaseSkill
import com.mobileclaw.skill.builtin.RoleManagerSkill
import com.mobileclaw.skill.builtin.SessionManagerSkill
import com.mobileclaw.skill.builtin.VideoGenerationTaskManager
import com.mobileclaw.skill.builtin.VideoTaskStatuses
import com.mobileclaw.skill.builtin.WorkspaceManagerSkill
import com.mobileclaw.ui.chat.AiQuizQuestion
import com.mobileclaw.ui.chat.AgentSenderMeta
import com.mobileclaw.ui.chat.ChatMessage
import com.mobileclaw.ui.chat.ChatContextComposer
import com.mobileclaw.ui.chat.ConfirmationFlow
import com.mobileclaw.ui.chat.ExplicitRoleSwitch
import com.mobileclaw.ui.chat.FileAttachment
import com.mobileclaw.ui.chat.buildNarrativeAgentMessages
import com.mobileclaw.ui.common.VISUAL_SKILL_IDS
import com.mobileclaw.ui.common.friendlyObservationDescription
import com.mobileclaw.ui.common.friendlySkillDescription
import com.mobileclaw.ui.common.friendlyThinkingUpdate
import com.mobileclaw.ui.common.buildSmartRecommendations
import com.mobileclaw.ui.common.nextStepHint
import com.mobileclaw.ui.common.plannedStageForAction
import com.mobileclaw.ui.common.stageAwareSkillDescription
import com.mobileclaw.ui.common.stringListOrEmpty
import com.mobileclaw.ui.common.stringOrNull
import com.mobileclaw.ui.common.userFacingActionResult
import com.mobileclaw.ui.common.userFacingActionNext
import com.mobileclaw.ui.common.userFacingInitialIntent
import com.mobileclaw.ui.common.userFacingPlanResult
import com.mobileclaw.ui.common.userFacingSkillStart
import com.mobileclaw.ui.common.userFacingThinkingResult
import com.mobileclaw.ui.chat.LogLine
import com.mobileclaw.ui.chat.LogType
import com.mobileclaw.ui.chat.MessageRole
import com.mobileclaw.ui.chat.SessionRunState
import com.mobileclaw.ui.chat.currentRunState
import com.mobileclaw.ui.group.buildGroupTurnInstruction
import com.mobileclaw.ui.group.fallbackGroupReply
import com.mobileclaw.ui.image.ImageGenerationRequest
import com.mobileclaw.ui.image.ImagePromptAiAction
import com.mobileclaw.ui.video.VideoGenerationRequest
import com.mobileclaw.ui.video.VideoPromptAiAction
import com.mobileclaw.workspace.WorkspaceArtifactState
import com.mobileclaw.workspace.WorkspaceCheckpoint
import com.mobileclaw.workspace.WorkspaceEvent
import com.mobileclaw.skill.builtin.SessionRequest
import com.mobileclaw.skill.builtin.SkillNotesSkill
import com.mobileclaw.skill.builtin.QuickSkillSkill
import com.mobileclaw.skill.builtin.ReadScreenSkill
import com.mobileclaw.skill.builtin.ScreenshotSkill
import com.mobileclaw.skill.builtin.ScrollSkill
import com.mobileclaw.skill.builtin.SeeScreenSkill
import com.mobileclaw.skill.builtin.SkillCheckSkill
import com.mobileclaw.skill.builtin.SkillMarketSkill
import com.mobileclaw.skill.builtin.AppManagerSkill
import com.mobileclaw.skill.builtin.AiHomeAssetSkill
import com.mobileclaw.skill.builtin.SwitchModelSkill
import com.mobileclaw.skill.builtin.SwitchRoleSkill
import com.mobileclaw.skill.builtin.TapSkill
import com.mobileclaw.skill.builtin.TaskRecipeSkill
import com.mobileclaw.skill.builtin.TownBuilderSkill
import com.mobileclaw.skill.builtin.UserConfigSkill
import com.mobileclaw.skill.builtin.WebBrowseSkill
import com.mobileclaw.skill.builtin.WebContentSkill
import com.mobileclaw.skill.builtin.WebJsSkill
import com.mobileclaw.skill.builtin.WebSearchSkill
import com.mobileclaw.town.AgentSpritePack
import com.mobileclaw.town.RoomArtifact
import com.mobileclaw.town.RoomTool
import com.mobileclaw.server.PrivilegedClient
import com.mobileclaw.skill.builtin.PipInstallSkill
import com.mobileclaw.vpn.VpnManager
import com.mobileclaw.skill.builtin.RunPythonSkill
import com.mobileclaw.skill.executor.ShellSkill
import com.mobileclaw.ui.group.GroupConversationStore
import com.mobileclaw.ui.group.GroupHistoryStore
import com.mobileclaw.ui.group.GroupMessage
import com.mobileclaw.ui.group.GroupPreview
import com.mobileclaw.ui.group.GroupRuntimeDiagnostics
import com.mobileclaw.ui.group.GroupTurnExecutor
import com.mobileclaw.ui.group.GroupTurnLaunch
import com.mobileclaw.ui.group.GroupTurnResult
import com.mobileclaw.ui.group.GroupTurnScheduler
import com.mobileclaw.ui.group.buildGroupSystemPrompt
import com.mobileclaw.ui.group.defaultGroupBubbleStyleFor
import com.mobileclaw.ui.group.groupAttachmentPrompt
import com.mobileclaw.ui.group.groupPreviewText
import com.mobileclaw.ui.group.isLowValueGroupReply
import com.mobileclaw.ui.group.isOrganicGroupTrigger
import com.mobileclaw.ui.group.parseMentions
import com.mobileclaw.ui.group.shouldUseStickerAwareChat
import com.mobileclaw.ui.group.shouldContinueGroupThread
import com.mobileclaw.ui.group.shouldInviteMultipleGroupVoices
import com.mobileclaw.ui.group.shouldRequireGroupReaction
import com.mobileclaw.ui.group.stickerQueryForText
import com.mobileclaw.ui.profile.ProfileDimension
import com.mobileclaw.ui.workspace.SemanticFactLike
import com.mobileclaw.ui.workspace.WorkspaceRuntimeCoordinator
import com.mobileclaw.ui.workspace.WorkspaceRuntimeRecorder
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID
import com.mobileclaw.R
import com.mobileclaw.str

private const val ROLE_PORTRAIT_STYLE_VERSION = "role_self_portrait_v5"
private const val ROLE_SPRITE_STYLE_VERSION = "role_self_sprite_v1"
private const val TAG = "MainViewModel"
private const val MINI_APP_AUTO_REPAIR_MAX_ATTEMPTS = 2
private const val LLM_RETRY_MAX_ATTEMPTS = 2
private const val VIDEO_TASK_AUTO_REFRESH_INTERVAL_MS = 12_000L

// 聊天内 MiniAPP 预览如果暴露出运行问题，这里把“继续修”的意图挂起到 session 级队列里。
// 这样不用把 UI 状态硬塞回正在执行的 runtime，也不会要求用户手动再说一次“继续”。
private data class PendingMiniAppAutoRepair(
    val sessionId: String,
    val appId: String,
    val previewStatus: String,
    val attempt: Int,
    val enqueuedAt: Long = System.currentTimeMillis(),
)

private fun codexDesktopExecutionGoal(userGoal: String): String = """
Use the codex_desktop tool to run this task on the user's desktop Codex CLI.

Required behavior:
- Call codex_desktop with action="run".
- Put the user's request in the prompt parameter.
- Do not use Android shell, Android files, phone UI control, or local Python for this task.
- If the bridge is not configured or unreachable, report that clearly and ask the user to configure Codex Bridge.

User request:
$userGoal
""".trimIndent()

class MainViewModel : ViewModel() {

    private val app = ClawApplication.instance
    private val config = app.agentConfig
    private val registry = app.skillRegistry
    private val loader = SkillLoader(app, registry)
    private val overlay = app.overlayManager
    private val auroraOverlay = app.auroraOverlayManager
    private val miniAppValidationOverlay = app.miniAppValidationOverlayManager
    private val episodicMemory = EpisodicMemory(app.database.episodeDao(), app.createLlmGateway())
    private val conversationMemory = app.conversationMemory
    private val profileExtractor = app.userProfileExtractor
    private val roleManager = app.roleManager
    private val townStore = app.agentTownStore
    private val userConfig = app.userConfig
    private val memoryContextBuilder = MemoryContextBuilder(app.semanticMemory, userConfig)
    private val memoryWriter = MemoryWriter(app.semanticMemory, userConfig)
    private val database = app.database
    private val llm get() = app.createLlmGateway()

    // 只对模型异常做轻量重试，避免正常业务失败也被机械重复执行。
    private fun shouldRetryAfterAgentRun(result: com.mobileclaw.agent.AgentResult?, error: Throwable?): Boolean {
        if (error is kotlinx.coroutines.CancellationException) return false
        if (isNonRetryableLlmFailure(error?.message ?: result?.summary.orEmpty())) return false
        if (error != null) return true
        return result?.success == false &&
            result.summary.trim().startsWith("LLM error:") &&
            !isNonRetryableLlmFailure(result.summary)
    }

    // 直接聊天没有工具链保护，所以同样补一层模型异常重试。
    private fun shouldRetryDirectChat(error: Throwable?): Boolean =
        error != null &&
            error !is kotlinx.coroutines.CancellationException &&
            !isNonRetryableLlmFailure(error.message.orEmpty())

    // 每次重试前都显式告诉用户当前不是卡死，而是在重新发起这一轮模型请求。
    private fun appendRetryLogLine(sessionId: String, message: String) {
        updateSession(sessionId) { s ->
            s.copy(
                streamingToken = "",
                streamingThought = "",
                activeLogLines = s.activeLogLines.finishLatestRunningLine() + LogLine(
                    type = LogType.INFO,
                    text = message,
                    details = listOf(
                        uiDetailLine("本步目的", "Purpose", uiText("恢复这次模型生成", "Recover this model generation")),
                        uiDetailLine("本步结果", "Result", uiText("上一次模型返回异常，正在按相同目标重新发起请求", "The previous model response failed; retrying the same goal")),
                    ),
                ).withLifecycle(running = false),
            )
        }
    }

    // Role switch requests emitted by SwitchRoleSkill / RoleManagerSkill, consumed in init
    private val roleRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val switchRoleSkill = SwitchRoleSkill(roleManager, roleRequests)
    private val recentGroupStickerPaths = ArrayDeque<String>()
    private var pendingAccessibilityTaskGoal: String? = null
    private var pendingRoleSwitchTaskGoal: String? = null
    private val activeWorkflows = mutableMapOf<String, ActiveWorkflow>()
    private val pendingMiniAppAutoRepairs = mutableMapOf<String, PendingMiniAppAutoRepair>()
    private val workspaceRuntime by lazy {
        WorkspaceRuntimeCoordinator(app.workspaceStore)
    }
    private val workspaceRecorder by lazy {
        WorkspaceRuntimeRecorder(
            workspaceStore = app.workspaceStore,
            memoryWriter = memoryWriter,
            resolveWorkspaceId = { sessionId -> workspaceRuntime.resolveSessionWorkspaceId(sessionId) },
        )
    }
    private val chatContextComposer by lazy {
        ChatContextComposer(
            effectiveMessages = { taskRouter.effectiveContextMessages(limit = 12) },
            summarizeAttachments = { taskRouter.summarizeAttachmentsForContext(it) },
            buildArtifactContext = { taskRouter.buildArtifactContext(it) },
            buildWorkspaceContext = {
                val workspace = workspaceRuntime.currentWorkspaceContext(
                    sessionId = _uiState.value.currentSessionId,
                    semanticFacts = currentSemanticFactsForWorkspace,
                )
                val preview = _uiState.value.chatMiniAppPreviewId?.let { appId ->
                    buildString {
                        appendLine("## Chat MiniAPP Validation Preview")
                        appendLine("MiniAPP id: $appId")
                        appendLine("Mode: ${_uiState.value.chatMiniAppPreviewMode.ifBlank { "unknown" }}")
                        appendLine("Status: ${_uiState.value.chatMiniAppPreviewStatus.ifBlank { "unknown" }}")
                        appendLine("Healthy: ${if (_uiState.value.chatMiniAppPreviewHealthy) "yes" else "no"}")
                        appendLine("This panel is a chat-linked validation tool, not the final delivery surface.")
                        appendLine("If preview shows a blank screen or runtime issue, close the preview mentally, then use inspect_logs -> focused repair -> validate.")
                    }.trim()
                }.orEmpty()
                listOf(workspace, preview).filter { it.isNotBlank() }.joinToString("\n\n")
            },
            buildUserMemoryContext = { goal, taskType ->
                buildUserMemoryContextForPrompt(
                    goal = goal,
                    taskType = taskType,
                    activeWorkspaceId = workspaceRuntime.resolveSessionWorkspaceId(_uiState.value.currentSessionId),
                )
            },
        )
    }
    private val taskRouter by lazy {
        TaskRouter(
            aiPagesProvider = { app.aiPageStore.getAll() },
            miniAppsProvider = { runCatching { app.miniAppStore.all() }.getOrDefault(emptyList()) },
            messagesProvider = { _uiState.value.currentRunState.messages },
            currentRoleProvider = { _uiState.value.currentRole },
            workspaceContextProvider = { workspaceRuntime.currentExecutionContext(_uiState.value.currentSessionId) },
        )
    }
    private val taskOrchestrator = TaskOrchestrator()
    private val videoTaskManager = VideoGenerationTaskManager(app, database.videoGenerationTaskDao())
    private val videoImageUploader = CloudinaryImageUploader(app, app.userConfig)
    private val videoPromptLlmClient = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val codexBridgeStreamClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Mini-app open requests emitted by AppManagerSkill
    private val appOpenRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val miniAppPreflightValidator = MiniAppPreflightValidator(app, app.miniAppStore, app.userConfig, app.semanticMemory)
    private val appManagerSkill = AppManagerSkill(app.miniAppStore, miniAppPreflightValidator, appOpenRequests)

    // AI native page open/pin requests emitted by UiBuilderSkill
    private val aiPageOpenRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val aiPagePinRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val uiBuilderSkill = com.mobileclaw.skill.builtin.UiBuilderSkill(app.aiPageStore, aiPageOpenRequests, aiPagePinRequests)

    // Page navigation requests emitted by NavigateSkill
    private val pageRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // Session operation requests emitted by SessionManagerSkill
    private val sessionRequests = MutableSharedFlow<SessionRequest>(extraBufferCapacity = 8)

    private val consoleServer = app.consoleServer
    private val userPrefs = app.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)

    private val _languageChanged = MutableSharedFlow<String>()
    val languageChanged: SharedFlow<String> = _languageChanged.asSharedFlow()

    private val _uiState = MutableStateFlow(
        MainUiState(
            config = config.configFlow,
            isConfigured = config.isConfigured(),
            currentPage = AppPage.HOME,
            currentModel = config.model,
            currentRole = Role.DEFAULT,
            consoleServerUrl = if (app.consoleServer.isEnabled()) app.consoleServer.getAccessUrl() else "",
            localModels = app.localModelManager.models.value,
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    // Per-session task management (multiple sessions can run simultaneously)
    private val taskJobs = mutableMapOf<String, Job>()
    private val runtimes = mutableMapOf<String, AgentRuntime>()
    private val pendingConfirmedRoutes = mutableMapOf<String, TaskRoute>()
    private var videoTaskAutoRefreshJob: Job? = null

    // 每个会话一个运行代次：新一轮任务接管会话时递增。被取消的旧协程恢复后必须先校验代次，
    // 过期回调不得再改写 isRunning/activeLogLines/任务句柄，否则会击穿 loadSession 的防覆盖守卫。
    private val runGenerations = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun beginRunGeneration(sessionId: String): Long =
        runGenerations.merge(sessionId, 1L) { old, _ -> old + 1 } ?: 1L

    private fun adoptRunGeneration(sessionId: String, generation: Long) {
        if (sessionId.isNotBlank()) runGenerations[sessionId] = generation
    }

    private fun isRunGenerationCurrent(sessionId: String, generation: Long): Boolean =
        runGenerations[sessionId] == generation

    // 任务执行中禁止 agent 新建/切走当前会话或删除正在运行的会话：
    // 切换会触发 loadSessionMessages 重载，删除会物理清掉整段聊天记录，都表现为“聊天记录被覆盖/丢失”。
    private fun guardSessionMutation(request: SessionRequest): String? {
        fun isBusy(id: String) = id.isNotBlank() && _uiState.value.sessionStates[id]?.isRunning == true
        val currentId = _uiState.value.currentSessionId
        return when (request) {
            is SessionRequest.Create, is SessionRequest.Switch -> {
                if (isBusy(currentId)) {
                    "Rejected: a task is still running in the current session. Do not create or switch sessions while a task is executing; finish the current task first."
                } else null
            }
            is SessionRequest.Delete -> {
                if (isBusy(request.id)) {
                    "Rejected: session '${request.id}' is executing a task and cannot be deleted. Deleting it would destroy the chat history the user is watching."
                } else null
            }
            is SessionRequest.Rename -> null
        }
    }

    private fun updateSession(sessionId: String, transform: (SessionRunState) -> SessionRunState) {
        _uiState.update { state ->
            val current = state.sessionStates[sessionId] ?: SessionRunState()
            state.copy(sessionStates = state.sessionStates + (sessionId to transform(current)))
        }
    }

    init {
        registerBuiltinSkills()
        loadDynamicSkills()
        _uiState.update { it.copy(allSkills = registry.userVisibleMetasWithTaxonomy()) }
        loadMiniApps()
        loadUserAvatar()
        loadVideoTasks()

        viewModelScope.launch {
            config.configFlow.collect { snap ->
                val configured = (snap.chatEndpoint.isNotBlank() && snap.chatApiKey.isNotBlank()) ||
                    ((snap.localModelEnabled || snap.localNativeOnly) && app.localModelManager.modelPath(snap.localModelId) != null)
                _uiState.update { it.copy(
                    isConfigured = configured,
                    currentModel = snap.model,
                    supportsMultimodal = supportsCurrentMultimodal(snap),
                ) }
            }
        }

        viewModelScope.launch {
            app.localModelManager.models.collect { models ->
                val snap = config.snapshot()
                _uiState.update { it.copy(
                    localModels = models,
                    isConfigured = (snap.chatEndpoint.isNotBlank() && snap.chatApiKey.isNotBlank()) ||
                        ((snap.localModelEnabled || snap.localNativeOnly) && app.localModelManager.modelPath(snap.localModelId) != null),
                    supportsMultimodal = supportsCurrentMultimodal(snap),
                ) }
            }
        }

        viewModelScope.launch {
            app.appForeground.collect { foreground ->
                onAppForegroundChanged(foreground)
            }
        }

        miniAppValidationOverlay.onStatusChanged = { appId, status, healthy ->
            updateChatMiniAppPreviewStatus(appId, status, healthy)
        }
        miniAppValidationOverlay.onDismissed = { appId ->
            val snapshot = _uiState.value
            if (snapshot.chatMiniAppPreviewId == appId) {
                clearChatMiniAppPreview()
            }
        }

        // React to role switch requests from the SwitchRoleSkill
        viewModelScope.launch {
            roleRequests.collect { roleId ->
                roleManager.get(roleId)?.let { setActiveRole(it) }
            }
        }

        // React to mini-app open requests from AppManagerSkill
        viewModelScope.launch {
            app.pendingAgentTask.collect { task -> runTask(task) }
        }

        viewModelScope.launch {
            appOpenRequests.collect { appId ->
                loadMiniApps()
                val shownInOverlay = miniAppValidationOverlay.show(appId, validationMode = true)
                _uiState.update {
                    if (shownInOverlay) {
                        it.copy(
                            chatMiniAppPreviewId = appId,
                            chatMiniAppPreviewMode = "overlay_validation",
                            chatMiniAppPreviewSessionId = it.currentSessionId,
                            chatMiniAppPreviewStatus = "Validation preview loading",
                            chatMiniAppPreviewHealthy = true,
                            openAppId = null,
                        )
                    } else {
                        it.copy(
                            openAppId = null,
                            chatMiniAppPreviewId = appId,
                            chatMiniAppPreviewMode = "validation",
                            chatMiniAppPreviewSessionId = it.currentSessionId,
                            chatMiniAppPreviewStatus = "Validation preview loading",
                            chatMiniAppPreviewHealthy = true,
                        )
                    }
                }
            }
        }

        // Sync AI pages from store
        viewModelScope.launch {
            app.aiPageStore.pages.collect { pages ->
                _uiState.update { it.copy(aiPages = pages) }
            }
        }

        // React to AI page open requests from UiBuilderSkill
        viewModelScope.launch {
            aiPageOpenRequests.collect { pageId ->
                _uiState.update { it.copy(openAiPageId = pageId) }
            }
        }

        // React to AI page pin requests from UiBuilderSkill
        viewModelScope.launch {
            aiPagePinRequests.collect { pageId ->
                _uiState.update { it.copy(openAiPageId = "pin:$pageId") }
            }
        }

        // React to page navigation requests from NavigateSkill
        viewModelScope.launch {
            pageRequests.collect { pageName ->
                val page = when (pageName) {
                    "chat"        -> AppPage.CHAT
                    "settings"    -> AppPage.SETTINGS
                    "skills"      -> AppPage.SKILLS
                    "profile"     -> AppPage.PROFILE
                    "roles"       -> AppPage.ROLES
                    "user_config" -> AppPage.USER_CONFIG
                    "apps"        -> AppPage.APPS
                    "console"     -> AppPage.CONSOLE
                    "help"        -> AppPage.HELP
                    "workspace"   -> AppPage.WORKSPACE
                    "town", "ai_town" -> AppPage.ROLES
                    else          -> AppPage.HOME
                }
                navigate(page)
            }
        }

        // React to session operation requests from SessionManagerSkill
        viewModelScope.launch(Dispatchers.IO) {
            sessionRequests.collect { req ->
                when (req) {
                    is SessionRequest.Create -> {
                        createNewSessionInternal()
                        if (req.title != str(R.string.vm_new_)) {
                            val sid = _uiState.value.currentSessionId
                            if (sid.isNotBlank()) database.sessionDao().updateTitle(sid, req.title)
                        }
                    }
                    is SessionRequest.Switch -> loadSession(req.id)
                    is SessionRequest.Delete -> deleteSession(req.id)
                    is SessionRequest.Rename -> database.sessionDao().updateTitle(req.id, req.title)
                }
            }
        }

        // React to messages sent from the LAN console browser
        viewModelScope.launch {
            consoleServer.messageRequests.collect { message -> runTask(message) }
        }

        // Keep user config entries in sync
        viewModelScope.launch {
            userConfig.entriesFlow.collect { entries ->
                _uiState.update { it.copy(userConfigEntries = entries) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            refreshProfileFacts()
        }

        // Keep skill notes in sync
        viewModelScope.launch {
            app.skillNotesStore.notesFlow.collect { notes ->
                _uiState.update { it.copy(skillNotes = notes) }
            }
        }

        // Load skill level overrides and apply to registry
        viewModelScope.launch(Dispatchers.IO) {
            app.skillLevelStore.overridesFlow.collect { overrides ->
                overrides.forEach { (id, level) -> registry.setLevelOverride(id, level) }
                _uiState.update { it.copy(skillLevelOverrides = overrides) }
            }
        }

        // Keep roles in sync — updates whenever RoleManager.save/delete is called (e.g. from AI)
        viewModelScope.launch {
            roleManager.rolesFlow.collect { roles ->
                townStore.ensureRooms(roles)
                _uiState.update { it.copy(availableRoles = roles) }
                ensureRolePortraits(roles)
            }
        }

        viewModelScope.launch {
            townStore.state.collect { town ->
                _uiState.update { it.copy(agentTown = town) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) { loadGroups() }

        viewModelScope.launch(Dispatchers.IO) {
            loadSessions()
            // If no session exists yet, create one
            if (database.sessionDao().count() == 0) {
                createNewSessionInternal()
            } else {
                val sessions = database.sessionDao().recent(limit = 50)
                val latest = sessions.firstOrNull()
                if (latest != null) {
                    _uiState.update { it.copy(currentSessionId = latest.id) }
                    loadSessionMessages(latest.id)
                }
            }
        }

        // Smart recommendations: app continuations + emotional context + history
        viewModelScope.launch(Dispatchers.IO) {
            val recent = runCatching { database.episodeDao().recent(limit = 24) }.getOrDefault(emptyList())
            val profileFacts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
            val miniApps = runCatching { app.miniAppStore.all() }.getOrDefault(emptyList())
            val recentUserMsgs = runCatching { database.conversationDao().recentUserMessages(limit = 20) }.getOrDefault(emptyList()).map { it.content }
            val recs = buildSmartRecommendations(recent, profileFacts, miniApps, recentUserMsgs)
            _uiState.update { it.copy(recommendations = recs) }
        }

        checkPrivServer()
    }

    // ── Session Management ───────────────────────────────────────────────────

    fun createNewSession() {
        viewModelScope.launch(Dispatchers.IO) {
            createNewSessionInternal()
        }
    }

    fun createNewSessionAndOpen() {
        viewModelScope.launch(Dispatchers.IO) {
            createNewSessionInternal()
            navigate(AppPage.CHAT)
        }
    }

    fun createNewCodexDesktopSession() {
        viewModelScope.launch(Dispatchers.IO) {
            createNewSessionInternal(codexDesktopMode = true)
        }
    }

    fun setCodexDesktopMode(enabled: Boolean) {
        val sessionId = _uiState.value.currentSessionId
        _uiState.update { state ->
            state.copy(
                codexDesktopMode = enabled,
                codexDesktopSessionIds = if (enabled) {
                    if (sessionId.isBlank()) state.codexDesktopSessionIds else state.codexDesktopSessionIds + sessionId
                } else {
                    if (sessionId.isBlank()) state.codexDesktopSessionIds else state.codexDesktopSessionIds - sessionId
                },
            )
        }
    }

    private suspend fun createNewSessionInternal(codexDesktopMode: Boolean = false) {
        val id = UUID.randomUUID().toString()
        val roleId = _uiState.value.currentRole.id
        database.sessionDao().insert(SessionEntity(
            id = id,
            title = if (codexDesktopMode) "Codex 会话" else str(R.string.vm_new_),
            roleId = roleId,
        ))
        _uiState.update {
            it.copy(
                currentSessionId = id,
                codexDesktopMode = codexDesktopMode,
                codexDesktopSessionIds = if (codexDesktopMode) it.codexDesktopSessionIds + id else it.codexDesktopSessionIds - id,
            )
        }
        loadSessions()
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(
                currentSessionId = sessionId,
                codexDesktopMode = sessionId in it.codexDesktopSessionIds,
            ) }
            // Only load DB messages if the session is NOT currently running (running state is live)
            val isAlreadyRunning = _uiState.value.sessionStates[sessionId]?.isRunning == true || taskJobs[sessionId] != null
            if (!isAlreadyRunning) {
                loadSessionMessages(sessionId)
            }
            loadSessions()
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { sessionRequests.emit(SessionRequest.Rename(id, title)) }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.sessionDao().delete(sessionId)
            database.sessionMessageDao().deleteForSession(sessionId)
            _uiState.update { it.copy(codexDesktopSessionIds = it.codexDesktopSessionIds - sessionId) }
            if (_uiState.value.currentSessionId == sessionId) {
                createNewSessionInternal()
            } else {
                loadSessions()
            }
        }
    }

    private suspend fun loadSessions() {
        val sessions = runCatching { database.sessionDao().recent(limit = 50) }.getOrDefault(emptyList())
        _uiState.update { it.copy(sessions = sessions) }
    }

    private suspend fun loadSessionMessages(sessionId: String, pageSize: Int = 20) {
        val total = runCatching { database.sessionMessageDao().countForSession(sessionId) }.getOrDefault(0)
        val entities = runCatching {
            database.sessionMessageDao().forSessionPaged(sessionId, pageSize, 0)
        }.getOrDefault(emptyList()).reversed()  // DESC→reversed gives ASC order
        val messages = entities.map { it.toChatMessage() }
        val hasMore = total > pageSize
        // 合并而非整体替换：内存里可能还有未落库的消息（正在执行/刚被打断的轮次），整体替换会把它们冲掉。
        updateSession(sessionId) { it.copy(messages = mergeLoadedMessages(messages, it.messages)) }
        _uiState.update { it.copy(
            historyOffset = pageSize,
            historyHasMore = hasMore,
            historyLoading = false,
        )}
    }

    // 会话消息是只追加的：DB 页是已持久化的最新一段，内存尾部可能有尚未落库的新消息。
    // 以 DB 页为基底，把内存中比 DB 更新的尾部接回去，避免加载时冲掉未持久化的消息。
    private fun mergeLoadedMessages(dbMessages: List<ChatMessage>, inMemory: List<ChatMessage>): List<ChatMessage> {
        if (inMemory.isEmpty()) return dbMessages
        if (dbMessages.isEmpty()) return inMemory
        val anchor = inMemory.indexOfLast { it == dbMessages.last() }
        val unsavedTail = if (anchor >= 0) {
            inMemory.drop(anchor + 1)
        } else {
            // DB 最后一条不在内存中（内存窗口落后于 DB）：取内存中最后一条已入库消息之后的部分，
            // 再排除 DB 页里已有的，剩下的就是未持久化的尾部。
            val lastSharedIdx = inMemory.indexOfLast { mem -> dbMessages.any { it == mem } }
            inMemory.drop(lastSharedIdx + 1).filter { mem -> dbMessages.none { it == mem } }
        }
        return dbMessages + unsavedTail
    }

    fun loadMoreHistory() {
        val sessionId = _uiState.value.currentSessionId
        if (sessionId.isBlank() || _uiState.value.historyLoading || !_uiState.value.historyHasMore) return
        _uiState.update { it.copy(historyLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val offset = _uiState.value.historyOffset
                val pageSize = 20
                val total = runCatching { database.sessionMessageDao().countForSession(sessionId) }.getOrDefault(0)
                val entities = runCatching {
                    database.sessionMessageDao().forSessionPaged(sessionId, pageSize, offset)
                }.getOrDefault(emptyList()).reversed()
                val older = entities.map { it.toChatMessage() }
                updateSession(sessionId) { it.copy(messages = older + it.messages) }
                _uiState.update { state ->
                    if (state.currentSessionId != sessionId) state else state.copy(
                        historyOffset = offset + pageSize,
                        historyHasMore = offset + pageSize < total,
                        historyLoading = false,
                    )
                }
            } finally {
                _uiState.update { state ->
                    if (state.currentSessionId == sessionId) state.copy(historyLoading = false) else state
                }
            }
        }
    }

    private suspend fun persistMessages(sessionId: String, userMsg: ChatMessage?, agentMsgs: List<ChatMessage>) {
        if (sessionId.isBlank()) return
        val gson = Gson()

        if (userMsg != null) {
            database.sessionMessageDao().insert(SessionMessageEntity(
                sessionId = sessionId,
                role = "user",
                text = userMsg.text,
                attachmentsJson = serializeAttachments(userMsg.attachments),
                imageBase64 = userMsg.imageBase64,
            ))
        }
        agentMsgs.forEach { agentMsg ->
            // Strip imageBase64 from log lines to avoid bloating the DB.
            val sanitizedLogLines = agentMsg.logLines.map { it.copy(imageBase64 = null) }
            database.sessionMessageDao().insert(SessionMessageEntity(
                sessionId = sessionId,
                role = "agent",
                text = agentMsg.text,
                logLinesJson = gson.toJson(sanitizedLogLines),
                attachmentsJson = serializeAttachments(agentMsg.attachments),
                senderRoleId = agentMsg.senderRoleId,
                senderRoleName = agentMsg.senderRoleName,
                senderRoleAvatar = agentMsg.senderRoleAvatar,
            ))
        }

        // Update session title from first user message if still default
        val session = database.sessionDao().recent(50).find { it.id == sessionId }
        if (session != null && session.title == str(R.string.vm_new_) && userMsg != null) {
            val title = userMsg.text.take(30).ifBlank {
                if (userMsg.imageBase64 != null) str(R.string.sticker_button) else str(R.string.vm_new_)
            }
            database.sessionDao().updateTitle(sessionId, title)
            loadSessions()
        }
    }

    private suspend fun persistUserOnlyMessage(
        sessionId: String,
        userMsg: ChatMessage,
        fallbackTitle: String,
    ) {
        if (sessionId.isBlank()) return
        database.sessionMessageDao().insert(SessionMessageEntity(
            sessionId = sessionId,
            role = "user",
            text = userMsg.text,
            attachmentsJson = serializeAttachments(userMsg.attachments),
            imageBase64 = userMsg.imageBase64,
        ))

        val session = database.sessionDao().recent(50).find { it.id == sessionId }
        if (session != null && session.title == str(R.string.vm_new_)) {
            val title = userMsg.text.take(30).ifBlank { fallbackTitle }
            database.sessionDao().updateTitle(sessionId, title)
            loadSessions()
        }
    }

    private fun buildAgentMessages(
        summary: String,
        logLines: List<LogLine>,
        attachments: List<SkillAttachment>,
        senderRole: Role,
    ): List<ChatMessage> {
        return buildNarrativeAgentMessages(
            summary = summary,
            logLines = logLines,
            attachments = attachments,
            sender = AgentSenderMeta(
                id = senderRole.id,
                name = senderRole.name,
                avatar = senderRole.avatar,
            ),
            isRunning = false,
        )
    }

    // ── Role Management ──────────────────────────────────────────────────────

    fun setActiveRole(role: Role) {
        _uiState.update { it.copy(currentRole = role) }
        if (role.modelOverride != null) {
            viewModelScope.launch {
                val snap = config.snapshot()
                val model = role.modelOverride
                if (model.startsWith("local:")) {
                    config.update(snap.copy(localModelEnabled = true, localModelId = model.removePrefix("local:")))
                } else {
                    val updatedGateways = snap.gateways.map {
                        if (it.id == snap.activeGatewayId || (snap.activeGatewayId == null && it == snap.gateways.firstOrNull()))
                            it.copy(model = model)
                        else it
                    }
                    config.update(snap.copy(gateways = updatedGateways, localModelEnabled = false, localNativeOnly = false))
                }
            }
        }
    }

    fun saveCustomRole(role: Role) {
        viewModelScope.launch(Dispatchers.IO) {
            roleManager.save(role)  // triggers rolesFlow → UI auto-updates via collector above
            roleManager.get(role.id)?.let { savedRole ->
                _uiState.update { state ->
                    if (state.currentRole.id == savedRole.id) state.copy(currentRole = savedRole) else state
                }
            }
        }
    }

    fun setUserConfigEntry(key: String, value: String, description: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { memoryWriter.syncUserConfig(key, value, description) }
            refreshProfileFacts()
        }
    }

    fun deleteUserConfigEntry(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { memoryWriter.deleteUserConfig(key) }
            refreshProfileFacts()
        }
    }

    private suspend fun refreshProfileFacts() {
        val loadedCount = _uiState.value.profileState.semanticFacts.size.coerceAtLeast(PROFILE_MEMORY_PAGE_SIZE)
        val semanticFacts = runCatching { app.semanticMemory.pageIncludingDisabled(limit = loadedCount, offset = 0) }.getOrDefault(emptyList())
        val facts = semanticFacts.filter { it.enabled }.associate { it.key to it.value }
        _uiState.update {
            it.copy(
                profileState = it.profileState.copy(
                    facts = facts,
                    semanticFacts = semanticFacts,
                    memoryHasMore = semanticFacts.size >= loadedCount,
                )
            )
        }
    }

    private suspend fun recordUserMemoryHints(text: String, workspaceId: String? = null) {
        runCatching { memoryWriter.recordExplicitUserText(text) }
        workspaceId?.takeIf { it.isNotBlank() }?.let { id ->
            runCatching { memoryWriter.recordScopedUserText(id, text) }
        }
        refreshProfileFacts()
    }

    fun loadUserAvatar() {
        val uri = userPrefs.getString("avatar_uri", null)
        _uiState.update { it.copy(userAvatarUri = uri) }
    }

    fun setUserAvatarUri(uri: String) {
        app.contentResolver.runCatching {
            takePersistableUriPermission(android.net.Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        userPrefs.edit().putString("avatar_uri", uri).apply()
        _uiState.update { it.copy(userAvatarUri = uri) }
    }

    fun deleteCustomRole(roleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            roleManager.delete(roleId)  // triggers rolesFlow → UI auto-updates
            if (_uiState.value.currentRole.id == roleId) {
                _uiState.update { it.copy(currentRole = Role.DEFAULT) }
            }
        }
    }

    fun editRole(role: Role) {
        _uiState.update { it.copy(editingRole = role) }
        navigate(AppPage.ROLE_EDIT)
        if (_uiState.value.availableModels.isEmpty()) fetchModels()
    }

    fun copyBuiltinRoleForEditing(role: Role) {
        val copyName = role.name.ifBlank { role.id } + str(R.string.role_copy_suffix)
        editRole(
            role.copy(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                name = copyName,
                isBuiltin = false,
            )
        )
    }

    fun openRoleDetail(role: Role) {
        townStore.ensureRooms(roleManager.all())
        _uiState.update { it.copy(detailRole = role) }
        navigate(AppPage.ROLE_DETAIL)
    }

    fun openRoleHome(role: Role) {
        townStore.ensureRooms(roleManager.all())
        _uiState.update { it.copy(detailRole = role, openTownRoleId = role.id) }
        navigate(AppPage.AI_TOWN)
    }

    fun generateRolePortrait(role: Role) {
        if (role.id in _uiState.value.rolePortraitGeneratingIds) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(rolePortraitGeneratingIds = it.rolePortraitGeneratingIds + role.id) }
            val result = runCatching {
                val selfBrief = createRoleSelfPortraitBrief(role)
                // 角色页的“生图”现在只生成静态肖像，彻底避免落回动态 spritesheet。
                val basePrompt = selfBrief["portrait_prompt"]?.asString?.takeIf { it.isNotBlank() }
                    ?: selfBrief["render_prompt"]?.asString?.takeIf { it.isNotBlank() }
                    ?: error("role visual brief did not return a portrait prompt")
                val prompt = """
                    $basePrompt

                    Runtime constraints only:
                    - Output one single complete role portrait image.
                    - Keep the full body visible inside frame.
                    - Compose the full figure with breathing room around head, hands, feet, and major props.
                    - Do not simulate animation frames, sprite strips, repeated poses, or multi-panel sheets.
                    - No text, UI, multi-view sheet, lineup, or poster layout.
                """.trimIndent()
                val pack = rolePortraitPack(
                    role = role,
                    notes = "$ROLE_PORTRAIT_STYLE_VERSION. Static role portrait from the role's own identity brief.",
                )
                val configuredModel = configuredRolePortraitImageModelOrNull()
                val generatedDataUri = configuredModel?.let { model ->
                    val imageGenerator = registry.get("generate_image")
                    if (imageGenerator == null) {
                        Log.w(TAG, "Role portrait image model is configured, but generate_image tool is unavailable.")
                        null
                    } else {
                        imageGenerator.execute(
                            mapOf(
                                "prompt" to prompt,
                                "model" to model,
                                "size" to "1024x1024",
                                "quality" to "high",
                            )
                        ).takeIf { it.success }?.let { image ->
                            image.imageBase64 ?: (image.data as? SkillAttachment.ImageData)?.base64
                        }.also { dataUri ->
                            if (dataUri == null) {
                                Log.w(TAG, "Role portrait image generation failed or returned empty data. model=$model")
                            }
                        }
                    }
                }
                val dataUri = generatedDataUri ?: createFallbackRolePortraitDataUri(role)
                val saved = townStore.registerSpritePack(
                    if (generatedDataUri == null) {
                        pack.copy(notes = "$ROLE_PORTRAIT_STYLE_VERSION. Local fallback role portrait; image generation model was not configured or failed.")
                    } else {
                        pack
                    },
                    dataUri,
                )
                // 静态肖像单独绑定到 portrait 槽位，避免角色详情继续播放房间动画。
                townStore.assignRolePortraitPack(role.id, saved.id)
                applyRoleHomeLayout(role)
                RolePortraitGenerationResult(
                    pack = saved,
                    usedFallback = generatedDataUri == null,
                    hadConfiguredImageModel = configuredModel != null,
                )
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(rolePortraitGeneratingIds = it.rolePortraitGeneratingIds - role.id) }
                result.onSuccess { portrait ->
                    val message = when {
                        !portrait.usedFallback -> str(R.string.role_portrait_generation_done)
                        portrait.hadConfiguredImageModel -> "图片模型生成失败，已使用本地兜底形象"
                        else -> "未配置图片生成模型，已使用本地兜底形象"
                    }
                    Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(app, str(R.string.role_portrait_generation_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private data class RolePortraitGenerationResult(
        val pack: AgentSpritePack,
        val usedFallback: Boolean,
        val hadConfiguredImageModel: Boolean,
    )

    private fun rolePortraitPack(role: Role, notes: String): AgentSpritePack =
        AgentSpritePack(
            id = "portrait_${role.id.replace(Regex("[^a-zA-Z0-9_]+"), "_")}",
            name = "${role.name.ifBlank { role.id }} Portrait",
            kind = "portrait",
            frameWidth = 512,
            frameHeight = 512,
            columns = 1,
            rows = 1,
            notes = notes,
        )

    private fun createFallbackRolePortraitDataUri(role: Role): String {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(Color.rgb(250, 250, 247), Color.rgb(230, 232, 224), Color.rgb(252, 252, 249)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val frame = RectF(28f, 28f, size - 28f, size - 28f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.rgb(17, 17, 17)
        }
        canvas.drawRoundRect(frame, 42f, 42f, borderPaint)

        val accent = roleAccentColor(role)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accent
            alpha = 42
        }
        canvas.drawCircle(size * 0.5f, size * 0.42f, 158f, haloPaint)

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(18, 18, 18)
        }
        canvas.drawRoundRect(RectF(150f, 116f, 362f, 328f), 72f, 72f, facePaint)

        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(218f, 218f, 13f, eyePaint)
        canvas.drawCircle(294f, 218f, 13f, eyePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(34, 34, 34)
        }
        canvas.drawRoundRect(RectF(126f, 312f, 386f, 468f), 60f, 60f, bodyPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            color = accent
        }
        canvas.drawLine(178f, 362f, 334f, 362f, accentPaint)
        canvas.drawLine(214f, 406f, 298f, 406f, accentPaint)

        val initial = role.name.trim().takeIf { it.isNotBlank() }
            ?.let { it.first().uppercaseChar().toString() }
            ?: role.id.trim().takeIf { it.isNotBlank() }?.first()?.uppercaseChar()?.toString()
            ?: "A"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 92f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textBounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        canvas.drawText(initial, size * 0.5f, 254f - textBounds.exactCenterY(), textPaint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun roleAccentColor(role: Role): Int {
        val palette = intArrayOf(
            Color.rgb(199, 244, 58),
            Color.rgb(86, 158, 255),
            Color.rgb(255, 113, 91),
            Color.rgb(164, 123, 255),
            Color.rgb(0, 184, 148),
        )
        val source = (role.id.ifBlank { role.name }).fold(0) { acc, c -> acc * 31 + c.code }
        return palette[kotlin.math.abs(source) % palette.size]
    }

    private suspend fun applyRoleHomeLayout(role: Role) {
        val builder = registry.get("town_builder") ?: return
        val plan = builder.execute(
            mapOf(
                "action" to "plan_room_layout",
                "role_id" to role.id,
            )
        )
        if (!plan.success) return
        val json = runCatching { JsonParser.parseString(plan.output).asJsonObject }.getOrNull() ?: return
        builder.execute(
            mapOf(
                "action" to "decorate_room",
                "role_id" to role.id,
                "house_name" to str(R.string.role_detail_home_for, role.name.ifBlank { role.id }),
                "style" to json.stringOrNull("style").orEmpty(),
                "house_sprite" to json.stringOrNull("house_sprite").orEmpty(),
                "accent" to json.stringOrNull("accent").orEmpty(),
                "motto" to json.stringOrNull("motto").orEmpty(),
                "idle_line" to json.stringOrNull("idle_line").orEmpty(),
                "working_line" to json.stringOrNull("working_line").orEmpty(),
            ).filterValues { value -> value.isNotBlank() }
        )
        json.getAsJsonArray("furniture")?.forEach { element ->
            val item = element.asJsonObject
            val id = item.stringOrNull("id") ?: return@forEach
            val type = item.stringOrNull("type") ?: return@forEach
            builder.execute(
                mapOf<String, Any>(
                    "action" to "place_furniture",
                    "role_id" to role.id,
                    "id" to id,
                    "type" to type,
                    "x" to (item.intOrNull("x") ?: 0),
                    "y" to (item.intOrNull("y") ?: 0),
                    "width" to (item.intOrNull("width") ?: 2),
                    "height" to (item.intOrNull("height") ?: 2),
                    "layer_name" to item.stringOrNull("layer").orEmpty(),
                    "variant" to item.stringOrNull("variant").orEmpty(),
                    "color" to item.stringOrNull("color").orEmpty(),
                ).filterValues { value -> value !is String || value.isNotBlank() }
            )
        }
    }

    private suspend fun generateRoleSpritePack(
        role: Role,
        imageGenerator: com.mobileclaw.skill.Skill,
        selfBrief: JsonObject,
        model: String,
    ): AgentSpritePack? {
        val houseArtist = registry.get("house_artist") ?: return null
        val style = selfBrief["style"]?.asString?.takeIf { it.isNotBlank() }
            ?: "transparent RPG desktop-pet spritesheet"
        val metadataJson = Gson().toJson(
            AgentSpritePack(
                id = "sprite_${role.id.replace(Regex("[^a-zA-Z0-9_]+"), "_")}",
                name = "${role.name.ifBlank { role.id }} Sprite Sheet",
                kind = "character",
                frameWidth = 192,
                frameHeight = 208,
                columns = 8,
                rows = 9,
                notes = "$ROLE_SPRITE_STYLE_VERSION. Generated RPG desktop-pet spritesheet from the role's own identity.",
            )
        )
        val prompt = selfBrief["sprite_prompt"]?.asString?.takeIf { it.isNotBlank() }
            ?: selfBrief["render_prompt"]?.asString?.takeIf { it.isNotBlank() }
            ?: return null
        val spritePrompt = """
            $prompt

            Runtime constraints only:
            - Output one complete 8x9 spritesheet.
            - Keep one consistent character identity across all cells.
            - Chroma key background must remain pure #00FF00 across empty areas.
            - No text, labels, room background, or poster layout.
        """.trimIndent()
        val image = imageGenerator.execute(
            mapOf(
                "prompt" to spritePrompt,
                "model" to model,
                "size" to "1536x1872",
                "quality" to "high",
            )
        ).let { primary ->
            if (primary.success || model == "pollinations") primary
            else imageGenerator.execute(
                mapOf(
                    "prompt" to spritePrompt,
                    "model" to "pollinations",
                    "size" to "1536x1872",
                    "quality" to "high",
                )
            )
        }
        if (!image.success) return null
        val dataUri = image.imageBase64
            ?: (image.data as? SkillAttachment.ImageData)?.base64
            ?: return null
        val reviewed = houseArtist.execute(
            mapOf(
                "action" to "review_sprite_image",
                "metadata_json" to metadataJson,
                "image_data_uri" to dataUri,
            )
        )
        if (!reviewed.success || reviewed.output.lineSequence().any { it.startsWith("ERROR") }) return null
        val register = houseArtist.execute(
            mapOf(
                "action" to "register_sprite_pack",
                "metadata_json" to metadataJson,
                "image_data_uri" to dataUri,
            )
        )
        if (!register.success) return null
        val jsonStart = register.output.indexOf('{')
        if (jsonStart < 0) return null
        return runCatching {
            Gson().fromJson(register.output.substring(jsonStart), AgentSpritePack::class.java)
        }.getOrNull()
    }

    private fun ensureRolePortraits(roles: List<Role>) {
        // 角色页首次打开不再自动生成形象。用户点击“生成形象”时再根据图片模型配置决定
        // 调用真实生图模型或使用本地兜底头像，避免无配置时静默触发外部兜底接口。
    }

    private suspend fun configuredRolePortraitImageModelOrNull(): String? {
        userConfig.get("role_portrait_image_model")
            ?.takeIf { it.isNotBlank() && it.isConfiguredImageModel() }
            ?.let { return it }
        userConfig.get("image_model")
            ?.takeIf { it.isNotBlank() && it.isConfiguredImageModel() }
            ?.let { return it }
        val snap = config.snapshot()
        snap.imageModel
            ?.takeIf { it.isNotBlank() && it.isConfiguredImageModel() }
            ?.let { return it }
        userConfig.get("image_api_model")
            ?.takeIf { it.isNotBlank() && it.isConfiguredImageModel() }
            ?.let { return it }
        val endpoint = (
            snap.activeGateway?.capabilityEndpoint("image")?.takeIf { it.isNotBlank() }
                ?: userConfig.get("image_api_endpoint")?.takeIf { it.isNotBlank() }
                ?: snap.endpoint.takeIf { snap.activeGateway?.hasCapability("image") == true }
                ?: ""
            ).lowercase()
        val currentModel = snap.activeGateway
            ?.takeIf { it.hasCapability("image") }
            ?.model
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        return when {
            currentModel.isConfiguredImageModel() -> currentModel
            "api.openai.com" in endpoint || "openai" in endpoint -> "gpt-image-2"
            "siliconflow" in endpoint -> "black-forest-labs/FLUX.1-schnell"
            "together" in endpoint -> "black-forest-labs/FLUX.1-schnell-Free"
            userConfig.get("huggingface_api_key")?.isNotBlank() == true -> "hf-flux-schnell"
            else -> null
        }
    }

    private fun decorateRoleHomeWithTool(role: Role, skillId: String, purpose: String) {
        if (role.id.isBlank() || skillId in setOf("town_builder", "house_artist")) return
        val meta = registry.get(skillId)?.meta
        runCatching {
            townStore.pinSkill(
                role.id,
                RoomTool(
                    id = skillId,
                    title = meta?.nameZh?.takeIf { it.isNotBlank() } ?: meta?.name ?: skillId,
                    category = purpose.take(40),
                )
            )
        }
    }

    private fun decorateRoleHomeWithAttachment(
        role: Role,
        skillId: String?,
        purpose: String,
        attachment: SkillAttachment,
    ) {
        if (role.id.isBlank()) return
        val artifact = when (attachment) {
            is SkillAttachment.ImageData -> RoomArtifact(
                id = stableHomeId("image", attachment.prompt ?: purpose),
                type = "image",
                title = attachment.prompt?.take(36)?.ifBlank { null } ?: "生成图片",
                subtitle = purpose.take(80),
            )
            is SkillAttachment.FileData -> RoomArtifact(
                id = stableHomeId("file", attachment.path),
                type = attachment.mimeType.toArtifactType(),
                title = attachment.name,
                subtitle = "${attachment.mimeType} · ${formatBytesForHome(attachment.sizeBytes)}",
            )
            is SkillAttachment.HtmlData -> RoomArtifact(
                id = stableHomeId("html", attachment.path),
                type = "html",
                title = attachment.title,
                subtitle = "HTML preview",
            )
            is SkillAttachment.WebPage -> RoomArtifact(
                id = stableHomeId("web", attachment.url),
                type = "web",
                title = attachment.title.ifBlank { attachment.url },
                subtitle = attachment.excerpt.take(90),
            )
            is SkillAttachment.SearchResults -> RoomArtifact(
                id = stableHomeId("search", attachment.query),
                type = "search",
                title = "搜索：${attachment.query}".take(50),
                subtitle = "${attachment.engine} · ${attachment.pages.size} results",
            )
            is SkillAttachment.FileList -> RoomArtifact(
                id = stableHomeId("file_list", attachment.directory),
                type = "files",
                title = attachment.directory.ifBlank { "文件列表" }.take(50),
                subtitle = "${attachment.files.size} files",
            )
            is SkillAttachment.ActionCard,
            is SkillAttachment.AccessibilityRequest -> null
        }
        if (artifact != null) {
            runCatching { townStore.pinArtifact(role.id, artifact) }
        }
        if (!skillId.isNullOrBlank()) {
            decorateRoleHomeWithTool(role, skillId, purpose)
        }
    }

    private fun decorateRoleHomeWithTaskSummary(role: Role, goal: String, summary: String, success: Boolean) {
        if (role.id.isBlank() || summary.isBlank()) return
        runCatching {
            townStore.pinMemory(
                roleId = role.id,
                title = if (success) "完成：${goal.take(34)}" else "未完成：${goal.take(34)}",
                body = summary.take(140),
                source = "task",
            )
            townStore.updateRoom(role.id) { room ->
                room.copy(
                    mood = if (success) "focused" else "review",
                    idleLine = if (success) "我刚把一个任务成果放进了房间。" else "我在复盘刚才没有完成好的地方。",
                    workingLine = "我正在把任务产物整理进 Home。",
                )
            }
        }
    }

    private fun String.toArtifactType(): String = when {
        startsWith("image/") -> "image"
        contains("html") -> "html"
        contains("pdf") -> "pdf"
        contains("spreadsheet") || contains("excel") -> "sheet"
        contains("presentation") || contains("powerpoint") -> "slide"
        contains("word") || contains("document") -> "document"
        startsWith("text/") -> "text"
        else -> "file"
    }

    private fun stableHomeId(prefix: String, raw: String): String =
        "${prefix}_${raw.ifBlank { prefix }.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48).ifBlank { System.currentTimeMillis().toString() }}"

    private fun formatBytesForHome(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    private suspend fun createRoleSelfPortraitBrief(role: Role): JsonObject {
        val fallback = fallbackRoleSelfPortraitBrief(role)
        return runCatching {
            val response = llm.chat(
                ChatRequest(
                    stream = false,
                    messages = listOf(
                        Message(
                            "system",
                            """
                            You are the AI role itself. Design your own embodied visual identity for MobileClaw.
                            Return only compact JSON. Do not explain.
                            This is not a user avatar. This is your own body and identity.
                            Avoid generic templates. Use your role prompt, job, tools, memory hints, and chat style.
                            Prefer an appealing playable game companion: expressive face, readable full body, strong silhouette, tasteful outfit/materials.
                            Default to a human-like or fantasy companion with clear job identity. Do not default to robot/android/mecha.
                            Do not choose a sterile mannequin, faceless concept armor, generic robot shell, mascot template, decorative logo, or cold product render.
                            Do not choose dog/cat/fox/wolf/pet/animal unless your role explicitly says you are an animal.
                            Do not choose three-view, lineup, character sheet, multiple variants, text, logo, or icon.
                            Required JSON keys:
                            self_concept, body_type, silhouette, outfit_materials, signature_object,
                            expression, palette, role_symbolism, style, hard_no, render_prompt, portrait_prompt, sprite_prompt
                            `portrait_prompt` must be a final image prompt for one polished full-body portrait.
                            `sprite_prompt` must be a final image prompt for one full 8x9 spritesheet sheet of the same character.
                            The prompts should reflect your own chosen style, not generic assistant defaults.
                            """.trimIndent(),
                        ),
                        Message(
                            "user",
                            """
                            Role id: ${role.id}
                            Role name: ${role.name}
                            Role description: ${role.description}
                            Role system prompt: ${role.systemPromptAddendum}
                            Forced skills: ${role.forcedSkillIds.joinToString(", ")}
                            Preferred task types: ${role.preferredTaskTypes.joinToString(", ") { it.name }}
                            Keywords: ${role.keywords.joinToString(", ")}
                            Chat bubble style: ${gson.toJson(role.chatBubbleStyle)}

                            Create your own visual identity and final prompts for yourself.
                            """.trimIndent(),
                        ),
                    ),
                )
            )
            val text = response.content.orEmpty()
            val jsonText = text.substringAfter("{", "").substringBeforeLast("}", "")
            if (jsonText.isBlank()) fallback else JsonParser.parseString("{$jsonText}").asJsonObject
        }.getOrDefault(fallback)
    }

    private fun fallbackRoleSelfPortraitBrief(role: Role): JsonObject = JsonObject().apply {
        addProperty("self_concept", role.description.ifBlank { role.name.ifBlank { role.id } })
        addProperty("body_type", "")
        addProperty("silhouette", "")
        addProperty("outfit_materials", "")
        addProperty("signature_object", "")
        addProperty("expression", "")
        addProperty("palette", "")
        addProperty("role_symbolism", role.description)
        addProperty("style", "")
        addProperty("hard_no", "")
        addProperty("render_prompt", "Create the embodied visual identity that this AI role would choose for itself: ${role.name.ifBlank { role.id }}. Let the role's own description, system prompt, tools, and personality decide the look.")
        addProperty("portrait_prompt", "Generate one complete portrait image for the AI role ${role.name.ifBlank { role.id }} based on the role's own self-defined identity and style.")
        addProperty("sprite_prompt", "Generate one complete 8x9 spritesheet for the AI role ${role.name.ifBlank { role.id }} based on the role's own self-defined identity and style.")
    }

    fun restoreBuiltinRole(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            roleManager.restore(id)
            // If the active role was the overridden built-in, refresh it from the restored default
            if (_uiState.value.currentRole.id == id) {
                roleManager.get(id)?.let { setActiveRole(it) }
            }
        }
    }

    // ── Task Execution ───────────────────────────────────────────────────────

    fun runTask(goal: String) {
        val hasPendingAttachment = _uiState.value.inputImageBase64 != null || _uiState.value.inputFileAttachment != null
        val trimmed = goal.trim().ifBlank {
            if (hasPendingAttachment) "请查看这个附件。" else ""
        }
        if (trimmed.isBlank()) return
        if (_uiState.value.codexDesktopMode) {
            runTaskInternal(trimmed)
            return
        }
        when {
            trimmed.startsWith(OPEN_ACCESSIBILITY_PREFIX) -> {
                val originalGoal = trimmed.removePrefix(OPEN_ACCESSIBILITY_PREFIX).trim()
                pendingAccessibilityTaskGoal = originalGoal.ifBlank { pendingAccessibilityTaskGoal }
                app.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                appendConfirmationResolution("已打开无障碍设置。开启 MobileClaw 后，回到这里点“已开启并继续”。")
                return
            }
            trimmed.startsWith(CONFIRM_ACCESSIBILITY_TASK_PREFIX) -> {
                val originalGoal = trimmed.removePrefix(CONFIRM_ACCESSIBILITY_TASK_PREFIX).trim()
                    .ifBlank { pendingAccessibilityTaskGoal.orEmpty() }
                if (originalGoal.isBlank()) {
                    appendConfirmationResolution("没有找到要继续的手机操作任务。")
                    return
                }
                if (!ClawAccessibilityService.isEnabled()) {
                    requestTaskExecutionConfirmation(originalGoal, TaskType.PHONE_CONTROL)
                    return
                }
                pendingAccessibilityTaskGoal = null
                viewModelScope.launch(Dispatchers.IO) {
                    val route = resolveRouteWithAi(
                        goal = originalGoal,
                        effectiveGoal = originalGoal,
                        hasImage = _uiState.value.inputImageBase64 != null,
                        hasFile = _uiState.value.inputFileAttachment != null,
                        activeWorkflow = activeWorkflowForCurrentSession(),
                    )
                    withContext(Dispatchers.Main) { runTaskInternal(originalGoal, routeOverride = route, showUserMessage = false) }
                }
                return
            }
            trimmed.startsWith(CONFIRM_TASK_PREFIX) -> {
                val confirmedGoal = trimmed.removePrefix(CONFIRM_TASK_PREFIX).trim()
                viewModelScope.launch(Dispatchers.IO) {
                    val route = synchronized(pendingConfirmedRoutes) {
                        pendingConfirmedRoutes.remove(confirmedGoal)
                    } ?: resolveRouteWithAi(
                        goal = confirmedGoal,
                        effectiveGoal = confirmedGoal,
                        hasImage = _uiState.value.inputImageBase64 != null,
                        hasFile = _uiState.value.inputFileAttachment != null,
                        activeWorkflow = activeWorkflowForCurrentSession(),
                    )
                    withContext(Dispatchers.Main) { runTaskInternal(confirmedGoal, routeOverride = route, showUserMessage = false) }
                }
                return
            }
            trimmed == CANCEL_CONFIRMATION_TEXT -> {
                pendingAccessibilityTaskGoal = null
                pendingRoleSwitchTaskGoal = null
                synchronized(pendingConfirmedRoutes) { pendingConfirmedRoutes.clear() }
                appendConfirmationResolution("已取消。")
                return
            }
            trimmed.startsWith(CONFIRM_ROLE_PREFIX) -> {
                val payload = trimmed.removePrefix(CONFIRM_ROLE_PREFIX).trim()
                val roleId = payload.substringBefore("::", payload).trim()
                val originalGoal = payload.substringAfter("::", "").trim()
                    .ifBlank { pendingRoleSwitchTaskGoal.orEmpty() }
                val role = roleManager.get(roleId)
                if (role != null) {
                    setActiveRole(role)
                    if (originalGoal.isNotBlank()) {
                        pendingRoleSwitchTaskGoal = null
                        viewModelScope.launch(Dispatchers.IO) {
                            val route = resolveRouteWithAi(
                                goal = originalGoal,
                                effectiveGoal = originalGoal,
                                hasImage = _uiState.value.inputImageBase64 != null,
                                hasFile = _uiState.value.inputFileAttachment != null,
                                activeWorkflow = activeWorkflowForCurrentSession(),
                            )
                            withContext(Dispatchers.Main) { runTaskInternal(originalGoal, routeOverride = route, showUserMessage = false) }
                        }
                    } else {
                        appendConfirmationResolution("已切换到 ${role.name}。")
                    }
                } else {
                    appendConfirmationResolution("没有找到这个角色：$roleId")
                }
                return
            }
        }

        if (pendingAccessibilityTaskGoal != null && ConfirmationFlow.isAccessibilityResumeText(trimmed)) {
            val originalGoal = pendingAccessibilityTaskGoal.orEmpty()
            if (ClawAccessibilityService.isEnabled()) {
                pendingAccessibilityTaskGoal = null
                viewModelScope.launch(Dispatchers.IO) {
                    val route = resolveRouteWithAi(
                        goal = originalGoal,
                        effectiveGoal = originalGoal,
                        hasImage = _uiState.value.inputImageBase64 != null,
                        hasFile = _uiState.value.inputFileAttachment != null,
                        activeWorkflow = activeWorkflowForCurrentSession(),
                    )
                    withContext(Dispatchers.Main) { runTaskInternal(originalGoal, routeOverride = route, showUserMessage = false) }
                }
            } else {
                requestTaskExecutionConfirmation(originalGoal, TaskType.PHONE_CONTROL)
            }
            return
        }

        val roleSwitchIntent = inferExplicitRoleSwitch(trimmed)
        if (roleSwitchIntent != null) {
            setActiveRole(roleSwitchIntent.role)
            if (roleSwitchIntent.remainingGoal.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val route = resolveRouteWithAi(
                        goal = roleSwitchIntent.remainingGoal,
                        effectiveGoal = roleSwitchIntent.remainingGoal,
                        hasImage = _uiState.value.inputImageBase64 != null,
                        hasFile = _uiState.value.inputFileAttachment != null,
                        activeWorkflow = activeWorkflowForCurrentSession(),
                    )
                    withContext(Dispatchers.Main) { runTaskInternal(roleSwitchIntent.remainingGoal, routeOverride = route) }
                }
            } else {
                appendConfirmationResolution("已切换到 ${roleSwitchIntent.role.name}。")
            }
            return
        }

        val pendingTurn = beginVisibleUserTurn(trimmed)
        if (pendingTurn == null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!ClawAccessibilityService.isEnabled() && shouldPushAccessibilityCardForGoal(trimmed)) {
                withContext(Dispatchers.Main) {
                    removePendingVisibleTurn(pendingTurn)
                    requestTaskExecutionConfirmation(trimmed, TaskType.PHONE_CONTROL)
                }
                return@launch
            }
            val hasImage = pendingTurn.imageBase64 != null
            val hasFile = pendingTurn.fileAttachment != null
            val activeWorkflow = activeWorkflowForCurrentSession()
            val route = resolveRouteWithAi(
                goal = trimmed,
                effectiveGoal = trimmed,
                hasImage = hasImage,
                hasFile = hasFile,
                activeWorkflow = activeWorkflow,
            )
            if (requiresUserExecutionConfirmation(route) &&
                route.source != TaskRouteSource.ACTIVE_WORKFLOW &&
                !isRecentContinuationRoute(route, trimmed)
            ) {
                withContext(Dispatchers.Main) {
                    removePendingVisibleTurn(pendingTurn)
                    requestTaskExecutionConfirmation(route.goalForExecution, route.taskType, route)
                }
                return@launch
            }

            withContext(Dispatchers.Main) { runTaskInternal(trimmed, routeOverride = route, pendingTurn = pendingTurn) }
        }
    }

    private data class PendingUserTurn(
        val sessionId: String,
        val userMessage: ChatMessage,
        val imageBase64: String?,
        val imageLocalPath: String,
        val fileAttachment: FileAttachment?,
        val runGeneration: Long,
    )

    private fun beginVisibleUserTurn(goal: String): PendingUserTurn? {
        val sessionId = _uiState.value.currentSessionId
        if (goal.isBlank()) return null
        if (_uiState.value.sessionStates[sessionId]?.isRunning == true || taskJobs[sessionId] != null) {
            stopCurrentRunForNewUserTurn(sessionId)
        }
        val attachedImage = _uiState.value.inputImageBase64
        val attachedFile = _uiState.value.inputFileAttachment
        val imageLocalPath = attachedImage?.let { persistUserImageForWorkspace(sessionId, it) }.orEmpty()
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            text = goal,
            imageBase64 = if (attachedImage != null) attachedImage
            else if (attachedFile != null && !attachedFile.isText) attachedFile.content
            else null,
            attachments = if (imageLocalPath.isNotBlank()) {
                listOf(SkillAttachment.ImageData(attachedImage.orEmpty(), prompt = "user image", localPath = imageLocalPath))
            } else emptyList(),
            imageLocalPath = imageLocalPath,
        )
        _uiState.update { it.copy(inputImageBase64 = null, inputFileAttachment = null) }
        val runGeneration = beginRunGeneration(sessionId)
        updateSession(sessionId) { s ->
            s.copy(
                isRunning = true,
                runStartedAt = System.currentTimeMillis(),
                messages = s.messages + userMessage,
                activeLogLines = listOf(
                    LogLine(
                        type = LogType.THINKING,
                        text = "",
                        details = emptyList(),
                    )
                ),
                activeAttachments = emptyList(),
                streamingToken = "",
                streamingThought = "",
            )
        }
        return PendingUserTurn(sessionId, userMessage, attachedImage, imageLocalPath, attachedFile, runGeneration)
    }

    private fun stopCurrentRunForNewUserTurn(sessionId: String) {
        taskJobs[sessionId]?.cancel()
        taskJobs.remove(sessionId)
        runtimes.remove(sessionId)
        overlay.hide()
        auroraOverlay.hide()
        val salvaged = salvageInterruptedRunMessages(sessionId)
        updateSession(sessionId) { state ->
            state.copy(
                isRunning = false,
                runStartedAt = 0L,
                messages = state.messages + salvaged,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
                streamingToken = "",
                streamingThought = "",
            )
        }
        if (salvaged.isNotEmpty() && sessionId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { persistMessages(sessionId, null, salvaged) } }
        }
    }

    // 被打断的运行可能已有执行日志/流式输出；折叠成一条 agent 消息保留并落库，避免“打断即丢失”。
    private fun salvageInterruptedRunMessages(sessionId: String): List<ChatMessage> {
        val state = _uiState.value.sessionStates[sessionId] ?: return emptyList()
        val hasContent = state.activeLogLines.any { it.text.isNotBlank() || it.details.isNotEmpty() } ||
            state.streamingToken.isNotBlank() ||
            state.activeAttachments.isNotEmpty()
        if (!hasContent) return emptyList()
        return buildAgentMessages(
            summary = state.streamingToken.ifBlank { "Task stopped." },
            logLines = state.activeLogLines.finishLatestRunningLine(),
            attachments = state.activeAttachments,
            senderRole = _uiState.value.currentRole,
        )
    }

    private fun removePendingVisibleTurn(turn: PendingUserTurn) {
        updateSession(turn.sessionId) { s ->
            s.copy(
                isRunning = false,
                runStartedAt = 0L,
                messages = s.messages.dropLastWhile { it === turn.userMessage || it == turn.userMessage }.ifEmpty { s.messages },
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
                streamingToken = "",
                streamingThought = "",
            )
        }
    }

    private suspend fun resolveRouteWithAi(
        goal: String,
        effectiveGoal: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
    ): TaskRoute {
        val workspaceContext = workspaceRuntime.currentWorkspaceContext(
            sessionId = _uiState.value.currentSessionId,
            semanticFacts = currentSemanticFactsForWorkspace,
        )
        val recentContext = taskRouter.effectiveContextMessages(limit = 10)
            .joinToString("\n") { msg ->
                val speaker = if (msg.role == MessageRole.USER) "User" else msg.senderRoleName.ifBlank { "Assistant" }
                val attachmentText = taskRouter.summarizeAttachmentsForContext(msg.attachments).ifBlank { "none" }
                "$speaker: ${msg.text.take(500)}\nattachments: $attachmentText"
            }
        val routerContext = listOf(workspaceContext, recentContext)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val aiDecision = AiIntentRouter(app.createLlmGateway()).decide(
            goal = goal,
            recentContext = routerContext,
            hasImage = hasImage,
            hasFile = hasFile,
            activeWorkflow = activeWorkflow,
        )
        val fallback by lazy {
            taskRouter.resolve(
                goal = goal,
                effectiveGoal = effectiveGoal,
                hasImage = hasImage,
                hasFile = hasFile,
                activeWorkflow = activeWorkflow,
            )
        }
        if (aiDecision == null) {
            val fallbackRoute = fallback
            Log.w(TAG, "AI route decision unavailable. Using fallback. goal=${goal.take(160)} fallbackReason=${fallbackRoute.debugReason.take(240)}")
            return fallbackRoute
        }
        val aiRoute = taskRouter.resolveWithAiDecision(
            goal = goal,
            effectiveGoal = effectiveGoal,
            hasImage = hasImage,
            hasFile = hasFile,
            activeWorkflow = activeWorkflow,
            decision = aiDecision,
        )
        if (aiRoute == null) {
            val fallbackRoute = fallback
            Log.w(
                TAG,
                "AI route rejected. taskType=${aiDecision.taskType} confidence=${aiDecision.confidence} reason=${aiDecision.reason.take(180)}; using fallback=${fallbackRoute.taskType}/${fallbackRoute.source}"
            )
            return fallbackRoute
        }
        Log.d(
            TAG,
            "AI route accepted. source=${aiRoute.source} taskType=${aiRoute.taskType} reason=${aiRoute.debugReason.take(240)} goal=${goal.take(160)}"
        )
        return aiRoute
    }

    private fun runTaskInternal(
        goal: String,
        imageOverride: String? = null,
        visibleUserText: String = goal,
        routeOverride: TaskRoute? = null,
        pendingTurn: PendingUserTurn? = null,
        showUserMessage: Boolean = true,
    ) {
        val currentSessionId = pendingTurn?.sessionId ?: _uiState.value.currentSessionId
        if (goal.isBlank() || (pendingTurn == null && _uiState.value.sessionStates[currentSessionId]?.isRunning == true)) return
        val codexDesktopMode = _uiState.value.codexDesktopMode || currentSessionId in _uiState.value.codexDesktopSessionIds
        val goalForRouting = if (codexDesktopMode) codexDesktopExecutionGoal(goal) else goal

        val attachedImage = imageOverride ?: pendingTurn?.imageBase64 ?: _uiState.value.inputImageBase64
        val attachedFile = pendingTurn?.fileAttachment ?: _uiState.value.inputFileAttachment
        val sessionIdAtStart = pendingTurn?.sessionId ?: _uiState.value.currentSessionId
        val attachedImageLocalPath = pendingTurn?.imageLocalPath
            ?: attachedImage?.let { persistUserImageForWorkspace(sessionIdAtStart, it) }
            ?: ""
        // Prepend text file content directly into the LLM goal
        val effectiveGoal = if (attachedFile != null && attachedFile.isText) {
            "[附件: ${attachedFile.name}]\n```\n${attachedFile.content.take(10_000)}\n```\n\n$goalForRouting"
        } else if (attachedImageLocalPath.isNotBlank()) {
            "[图片已保存到本地工作区]\npath: $attachedImageLocalPath\n后续需要引用这张图片时，直接把这个 path 传给相关工具，例如 generate_video.image。不要要求用户重新发送图片，也不要说只能使用 HTTP 链接；系统会自动上传本地图片。\n\n$goalForRouting"
        } else goalForRouting
        val userMessage = pendingTurn?.userMessage ?: ChatMessage(
            role = MessageRole.USER,
            text = visibleUserText,
            imageBase64 = if (attachedImage != null) attachedImage
                          else if (attachedFile != null && !attachedFile.isText) attachedFile.content
                          else null,
            attachments = if (attachedImageLocalPath.isNotBlank()) {
                listOf(SkillAttachment.ImageData(attachedImage.orEmpty(), prompt = "user image", localPath = attachedImageLocalPath))
            } else emptyList(),
            imageLocalPath = attachedImageLocalPath,
        )
        val runGeneration = pendingTurn?.runGeneration ?: beginRunGeneration(sessionIdAtStart)
        val userMessageVisible = pendingTurn != null || showUserMessage
        // 用户消息在执行启动时即落库，而不是等任务结束：任务被取消/打断后重新加载会话也不会丢这条消息。
        // 仅当会话 id 暂时为空（将由 ensureRunnableSession 新建）时退回旧的“结束时持久化”路径。
        val userMessagePersistedEarly = userMessageVisible && sessionIdAtStart.isNotBlank()
        if (userMessagePersistedEarly) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    persistUserOnlyMessage(
                        sessionId = sessionIdAtStart,
                        userMsg = userMessage,
                        fallbackTitle = if (userMessage.imageBase64 != null) str(R.string.sticker_button) else str(R.string.vm_new_),
                    )
                }
            }
        }
        if (codexDesktopMode && routeOverride == null) {
            runCodexDesktopDirect(
                sessionId = sessionIdAtStart,
                userMessage = userMessage,
                userGoal = goal,
                prompt = goal,
                imageBase64 = attachedImage,
                imageLocalPath = attachedImageLocalPath,
                fileAttachment = attachedFile,
                showUserMessage = showUserMessage,
                persistUserMessage = userMessageVisible && !userMessagePersistedEarly,
                runGeneration = runGeneration,
            )
            return
        }
        val route = routeOverride ?: if (codexDesktopMode) {
            TaskRoute(
                taskType = TaskType.CODE_EXECUTION,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goalForRouting,
                    taskTypeOverride = TaskType.CODE_EXECUTION,
                    aiPrimaryChannel = ChannelType.CODE,
                    aiToolHints = listOf("codex_desktop"),
                    userVisibleSteps = if (isEnglishUiText()) {
                        listOf("Connect to desktop Codex", "Send the task", "Return the result")
                    } else {
                        listOf("连接电脑 Codex", "发送任务", "返回结果")
                    },
                ),
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.CLASSIFIER,
                goalToRemember = goal,
                debugReason = "Codex desktop session mode enabled.",
            )
        } else {
            TaskRoute(
                taskType = if (attachedImage != null) TaskType.GENERAL else TaskType.CHAT,
                contextualIntent = ContextualTaskIntent(
                    classificationGoal = goal,
                    taskTypeOverride = if (attachedImage != null) TaskType.GENERAL else TaskType.CHAT,
                    aiPrimaryChannel = ChannelType.CHAT,
                    aiSupportingChannels = emptyList(),
                    aiToolHints = emptyList(),
                ),
                goalForExecution = effectiveGoal,
                source = TaskRouteSource.CLASSIFIER,
                goalToRemember = goal,
                debugReason = "Internal direct-chat fallback because routeOverride was missing.",
            )
        }
        val contextualIntent = route.contextualIntent
        val inferredAiPageTarget = contextualIntent.aiPage
        val taskType = route.taskType
        val resolvedWorkspaceSessionId = sessionIdAtStart.ifBlank { _uiState.value.currentSessionId }
        workspaceRuntime.ensureSessionBinding(
            sessionId = resolvedWorkspaceSessionId,
            taskType = taskType,
            goal = route.goalForExecution,
            intent = contextualIntent,
        )
        val executionGoal = if (attachedFile?.isText == true && routeOverride != null && route.source != TaskRouteSource.ACTIVE_WORKFLOW) {
            effectiveGoal
        } else {
            route.goalForExecution
        }
        val workspaceResumeGoal = workspaceRuntime.augmentGoalWithWorkspaceResume(
            sessionId = resolvedWorkspaceSessionId,
            userGoal = goal,
            executionGoal = executionGoal,
        )
        val stickerAwareChat = attachedImage == null &&
            attachedFile == null &&
            (taskType == TaskType.GENERAL || taskType == TaskType.CHAT) &&
            route.primaryChannelForExecution() == ChannelType.CHAT &&
            shouldUseStickerAwareChat(goal)
        val executionTaskType = if (stickerAwareChat) TaskType.CHAT else taskType
        val contextualGoal = taskRouter.applyContextualTaskConstraints(workspaceResumeGoal, contextualIntent, taskType)
        val directPriorContext = buildPriorContext(
            goal = goal,
            taskType = executionTaskType,
            intent = contextualIntent,
            includeMemory = true,
            includeRecentMessages = false,
        )
        val agentPriorContext = buildPriorContext(goal, executionTaskType, contextualIntent, includeMemory = false)
        val isPhoneControlTask = executionTaskType == TaskType.PHONE_CONTROL
        val currentRole = _uiState.value.currentRole
        val schedulingContext = buildPriorContext(
            goal = goal,
            taskType = executionTaskType,
            intent = contextualIntent,
            includeMemory = true,
            includeRecentMessages = true,
        )
        val schedulingGoal = listOf(contextualGoal, schedulingContext.take(1200)).filter { it.isNotBlank() }.joinToString("\n\n")
        val scheduleDecision = RoleScheduler.schedule(
            taskType = taskType,
            goal = schedulingGoal,
            availableRoles = _uiState.value.availableRoles,
            currentRole = currentRole,
            memoryContext = directPriorContext,
        )
        val scheduledRole = if (shouldUseScheduledRoleForRun(goal, executionTaskType, currentRole, scheduleDecision.role)) {
            scheduleDecision.role
        } else {
            currentRole
        }
        val orchestration = taskOrchestrator.orchestrate(
            route = route,
            goal = contextualGoal,
            hasImage = attachedImage != null,
            hasFile = attachedFile != null,
            role = scheduledRole,
            language = config.language,
        )
        val allowedToolIds = resolveAllowedToolIds(route, orchestration.channelDecision.toolHints, contextualGoal)
        val executionContext = orchestration.toPromptBlock()
        val visibleGoalLabel = visibleUserText.ifBlank {
            if (attachedImage != null) str(R.string.sticker_button) else goal
        }

        if (pendingTurn == null) {
            _uiState.update { it.copy(inputImageBase64 = null, inputFileAttachment = null) }
            updateSession(sessionIdAtStart) { s -> s.copy(
                isRunning = true,
                runStartedAt = System.currentTimeMillis(),
                messages = if (showUserMessage) s.messages + userMessage else s.messages,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
                streamingToken = "",
                streamingThought = "",
            )}
        } else {
            updateSession(sessionIdAtStart) { s -> s.copy(streamingToken = "", streamingThought = "") }
        }
        if (attachedImage == null &&
            attachedFile == null &&
            route.primaryChannelForExecution() == ChannelType.INFO) {
            runInfoChannelAnswer(sessionIdAtStart, userMessage, goal, scheduledRole, persistUserMessage = userMessageVisible && !userMessagePersistedEarly, runGeneration = runGeneration)
            return
        }
        // Fast path: image understanding is a VLM chat, not an agentic web/search task.
        if (attachedImage != null &&
            attachedFile == null &&
            executionTaskType == TaskType.GENERAL &&
            shouldAnswerImageDirectly(goal)) {
            runDirectChat(sessionIdAtStart, userMessage, goal, scheduledRole, directPriorContext, executionContext, attachedImage, persistUserMessage = userMessageVisible && !userMessagePersistedEarly, runGeneration = runGeneration)
            return
        }

        // Fast path: conversational message with no attachments → skip agent loop
        if (!stickerAwareChat &&
            attachedImage == null && attachedFile == null &&
            shouldRunDirectChat(route)) {
            runDirectChat(sessionIdAtStart, userMessage, goal, scheduledRole, directPriorContext, executionContext, persistUserMessage = userMessageVisible && !userMessagePersistedEarly, runGeneration = runGeneration)
            return
        }

        val llm = app.createLlmGateway()
        val rt = AgentRuntime(llm, registry, app.semanticMemory, memoryContextBuilder)
        runtimes[sessionIdAtStart] = rt

        overlay.show(visibleGoalLabel)
        val phoneAuroraOverlayShown = if (isPhoneControlTask) {
            auroraOverlay.beginTask()
        } else {
            false
        }
        if (isPhoneControlTask) {
            Log.d(TAG, "Phone aurora overlay begin requested. shown=$phoneAuroraOverlayShown")
        }

        consoleServer.broadcast("task_started", visibleGoalLabel)

        val newJob = viewModelScope.launch {
            val resolvedSessionId = ensureRunnableSession(sessionIdAtStart)
            if (resolvedSessionId != sessionIdAtStart) adoptRunGeneration(resolvedSessionId, runGeneration)
            val channelSummary = orchestration.userVisibleSummary
            rememberActiveWorkflow(resolvedSessionId, route.goalToRemember, executionTaskType, scheduledRole)
            workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)?.let { workspaceId ->
                runCatching {
                    app.workspaceStore.recordEvent(
                        workspaceId,
                        WorkspaceEvent(
                            category = "task_plan",
                            source = "main_view_model",
                            title = "Task plan",
                            summary = contextualGoal.take(160),
                            payload = buildString {
                                appendLine("Role: ${scheduledRole.name}")
                                appendLine("Task type: ${executionTaskType.name}")
                                appendLine("Goal: ${contextualGoal.take(2000)}")
                                appendLine()
                                appendLine("Channel summary:")
                                appendLine(channelSummary)
                            }.trim(),
                        ),
                    )
                }
            }
            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) {
                    runCatching {
                        database.sessionDao().updateRole(resolvedSessionId, scheduledRole.id)
                        loadSessions()
                    }
                }
            }

            val episodicContext = runCatching {
                episodicMemory.retrieve(contextualGoal)
                    .filter { it.reflexionSummary.isNotBlank() }
                    .joinToString("\n") { "- ${it.reflexionSummary}" }
            }.getOrDefault("")

            updateSession(resolvedSessionId) { s ->
                val firstStep = route.contextualIntent.userVisibleSteps.firstOrNull()
                val secondStep = route.contextualIntent.userVisibleSteps.drop(1).firstOrNull()
                val overlayWarning = if (isPhoneControlTask && !phoneAuroraOverlayShown) {
                    listOf(
                        LogLine(
                            type = LogType.INFO,
                            text = uiText("极光悬浮框没有显示，请检查悬浮窗权限", "Aurora overlay is not visible. Check overlay permission."),
                            details = listOf(
                                uiDetailLine("本步结果", "Result", uiText(
                                    "系统没有允许 MobileClaw 显示悬浮窗，手机操作仍会继续，但你看不到极光边框提示。",
                                    "The system has not allowed MobileClaw to show overlays. Phone control will continue, but the Aurora border will not be visible.",
                                )),
                                uiDetailLine("接下来", "Next", uiText(
                                    "在系统设置里开启 MobileClaw 的悬浮窗 / Display over other apps 权限。",
                                    "Enable MobileClaw overlay / Display over other apps permission in system settings.",
                                )),
                            ),
                        ).withLifecycle(running = false)
                    )
                } else emptyList()
                s.copy(
                    activeLogLines = s.activeLogLines.finishLatestRunningLine() + overlayWarning + LogLine(
                        type = LogType.THINKING,
                        text = userFacingInitialIntent(firstStep, secondStep, channelSummary),
                        details = emptyList(),
                    ).withLifecycle(running = true),
                )
            }

            // Collect NetworkTracer events and append to the most recent active log line's details
            val networkTraceJob = launch {
                com.mobileclaw.agent.NetworkTracer.events.collect { msg ->
                    updateSession(resolvedSessionId) { s ->
                        val lines = s.activeLogLines.toMutableList()
                        if (lines.isNotEmpty()) {
                            val last = lines.last()
                            lines[lines.size - 1] = last.copy(details = last.details + msg)
                        }
                        s.copy(activeLogLines = lines)
                    }
                }
            }

            val runtimeEventJob = launch {
                rt.events.collect { event ->
                    when (event) {
                        is AgentEvent.Started -> {
                            if (visibleUserText.isNotBlank()) {
                                event.toLogLine()?.let { line ->
                                    updateSession(resolvedSessionId) {
                                        it.copy(activeLogLines = it.activeLogLines.finishLatestRunningLine() + line.withLifecycle(running = false))
                                    }
                                }
                            }
                        }
                        is AgentEvent.ThinkingToken -> {
                            overlay.onToken(event.text)
                            updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + event.text) }
                        }
                        is AgentEvent.SkillCalling -> {
                            val actionIndex = _uiState.value.sessionStates[resolvedSessionId]
                                ?.activeLogLines
                                ?.count { it.type == LogType.ACTION }
                                ?: 0
                            val stageText = plannedStageForAction(route.contextualIntent.userVisibleSteps, actionIndex)
                            val debugPurposeText = stageAwareSkillDescription(stageText, event.skillId, event.params)
                            val purposeText = userFacingSkillStart(stageText, event.skillId, event.params)
                            overlay.onSkillCalling(event.skillId, event.params)
                            if (isPhoneControlTask || event.skillId in VISUAL_SKILL_IDS) {
                                if (event.skillId == "see_screen") auroraOverlay.flashFullScreen()
                                else auroraOverlay.flash()
                            }
                            // Build full details: formatted params for the detail sheet
                            val paramDetails = event.params.entries.map { (k, v) ->
                                "  $k: ${Gson().toJson(v).take(300)}"
                            }
                            val lineDetails = buildList {
                                add(uiDetailLine("本步目的", "Purpose", purposeText))
                                userFacingActionResult(event.skillId, stageText)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { add(uiDetailLine("本步结果", "Result", it)) }
                                // 这类信息用户能理解，但不该压过主要结论，所以放在二级说明位。
                                if (stageText.isNotBlank() && stageText != debugPurposeText) add(uiDetailLine("这样安排", "Plan", stageText))
                                // 原始参数保留在调试区，默认阅读不需要看到键值列表。
                                add(uiDetailLine("调试", "Debug", str(R.string.vm_c96809)))
                                add(uiDetailLine("调试", "Debug", "${uiText("意图", "intent")}=$debugPurposeText"))
                                addAll(paramDetails.map { uiDetailLine("调试", "Debug", it) })
                            }
                            val line = event.toLogLine()?.copy(text = purposeText, details = lineDetails)
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    streamingThought = "",
                                    activeLogLines = if (line != null) {
                                        s.activeLogLines.finishLatestRunningLine() + line.withLifecycle(running = true)
                                    } else {
                                        s.activeLogLines
                                    },
                                )
                            }
                            launch(Dispatchers.IO) {
                                decorateRoleHomeWithTool(scheduledRole, event.skillId, purposeText)
                            }
                            consoleServer.broadcast("skill_called", purposeText)
                        }
                        is AgentEvent.Observation -> {
                            val previousSkill = _uiState.value.sessionStates[resolvedSessionId]
                                ?.activeLogLines
                                ?.lastOrNull { it.type == LogType.ACTION }
                                ?.skillId
                            val purposeText = friendlyObservationDescription(previousSkill, event.text, event.imageBase64 != null)
                            val actionStage = _uiState.value.sessionStates[resolvedSessionId]
                                ?.activeLogLines
                                ?.lastOrNull { it.type == LogType.ACTION }
                                ?.details
                                ?.firstOrNull {
                                    it.startsWith(uiDetailPrefix("这样安排", "Plan")) ||
                                        it.startsWith(uiDetailPrefix("这样安排", "Plan", englishOverride = false))
                                }
                                ?.let { detail ->
                                    detail.removePrefix(uiDetailPrefix("这样安排", "Plan"))
                                        .removePrefix(uiDetailPrefix("这样安排", "Plan", englishOverride = false))
                                }
                                ?.trim()
                            overlay.onObservation(purposeText)
                            if (event.attachment is SkillAttachment.ActionCard && event.attachment.tone == "role") {
                                pendingRoleSwitchTaskGoal = contextualGoal
                            }
                            val attachment = when (event.attachment) {
                                is SkillAttachment.AccessibilityRequest -> {
                                    pendingAccessibilityTaskGoal = contextualGoal
                                    ConfirmationFlow.accessibilityActionCard(
                                        goal = contextualGoal,
                                        confirmAccessibilityTaskPrefix = CONFIRM_ACCESSIBILITY_TASK_PREFIX,
                                        openAccessibilityPrefix = OPEN_ACCESSIBILITY_PREFIX,
                                        cancelText = CANCEL_CONFIRMATION_TEXT,
                                        skillName = event.attachment.skillName,
                                    )
                                }
                                else -> event.attachment
                            }
                            val lineDetails = buildList {
                                actionStage?.takeIf { it.isNotBlank() }?.let { add(uiDetailLine("本步目的", "Purpose", it)) }
                                add(uiDetailLine("本步结果", "Result", purposeText))
                                userFacingActionNext(actionStage.orEmpty(), previousSkill.orEmpty(), event.text)
                                    ?.let { add(uiDetailLine("接下来", "Next", it)) }
                                if (event.text.isNotBlank()) {
                                    summarizeTechnicalResultForUser(previousSkill, event.text)?.let { add(uiDetailLine("补充判断", "Note", it)) }
                                    add(uiDetailLine("调试", "Debug", uiText("完整结果 (${event.text.length} 字符)", "Full result (${event.text.length} chars)")))
                                    add(uiDetailLine("调试", "Debug", event.text.take(2000)))
                                }
                            }
                            val line = LogLine(
                                type = LogType.OBSERVATION,
                                text = purposeText,
                                imageBase64 = event.imageBase64,
                                details = lineDetails,
                            ).withLifecycle(running = false)
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    activeLogLines = s.activeLogLines.finishLatestRunningLine() + line,
                                    activeAttachments = if (attachment != null)
                                        s.activeAttachments + attachment
                                    else s.activeAttachments,
                                )
                            }
                            if (attachment != null) {
                                launch(Dispatchers.IO) {
                                    decorateRoleHomeWithAttachment(scheduledRole, previousSkill, purposeText, attachment)
                                }
                            }
                            runCatching {
                                persistWorkspaceObservation(
                                    sessionId = resolvedSessionId,
                                    skillId = previousSkill,
                                    rawOutput = event.text,
                                )
                            }
                        }
                        is AgentEvent.Error -> {
                            overlay.onError(event.message)
                            event.toLogLine()?.let { line ->
                                updateSession(resolvedSessionId) {
                                    it.copy(activeLogLines = it.activeLogLines.finishLatestRunningLine() + line.withLifecycle(running = false))
                                }
                            }
                        }
                        is AgentEvent.Warning -> {
                            overlay.onWarning(event.message)
                            event.toLogLine()?.let { line ->
                                updateSession(resolvedSessionId) {
                                    it.copy(activeLogLines = it.activeLogLines.finishLatestRunningLine() + line.withLifecycle(running = false))
                                }
                            }
                        }
                        is AgentEvent.ThinkingComplete -> {
                            overlay.onThinkingComplete()
                            val friendlyThought = friendlyThinkingUpdate(event.thought, route.contextualIntent.userVisibleSteps)
                            val userFacingThought = userFacingThinkingResult(event.thought, route.contextualIntent.userVisibleSteps)
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    activeLogLines = s.activeLogLines.finishLatestRunningLine() + LogLine(
                                        type = LogType.THINKING,
                                        text = userFacingThought,
                                        details = listOf(
                                            uiDetailLine("本步目的", "Purpose", userFacingThought),
                                            uiDetailLine("本步结果", "Result", friendlyThought),
                                            uiDetailLine("调试", "Debug", event.thought.take(1200)),
                                        ),
                                    ).withLifecycle(running = false),
                                    streamingToken = "",
                                    streamingThought = "",
                                )
                            }
                        }
                        is AgentEvent.PlanCreated -> {
                            val steps = route.contextualIntent.userVisibleSteps.ifEmpty { event.plan.steps }
                            val text = steps.firstOrNull() ?: event.plan.summary
                            val secondStep = steps.drop(1).firstOrNull { it.isNotBlank() }
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    activeLogLines = s.activeLogLines.finishLatestRunningLine() + LogLine(
                                        type = LogType.THINKING,
                                        text = text,
                                        details = buildList {
                                            add(uiDetailLine("本步目的", "Purpose", text))
                                            add(uiDetailLine("本步结果", "Result", userFacingPlanResult(steps, event.plan.summary)))
                                            if (!secondStep.isNullOrBlank()) add(uiDetailLine("接下来", "Next", secondStep.trim()))
                                            add(uiDetailLine("调试", "Debug", "${uiText("角色", "role")}=${scheduledRole.name} (${scheduledRole.id})"))
                                            add(uiDetailLine("调试", "Debug", scheduleDecision.reason))
                                            add(uiDetailLine("调试", "Debug", event.plan.toPrompt().take(1600)))
                                        },
                                    ).withLifecycle(running = true)
                                )
                            }
                        }
                        else -> event.toLogLine()?.let { line ->
                            updateSession(resolvedSessionId) {
                                it.copy(activeLogLines = it.activeLogLines.finishLatestRunningLine() + line.withLifecycle(running = false))
                            }
                        }
                    }
                }
            }

            val userProfileContext = runCatching {
                app.semanticMemory.all()
                    .filter { it.key.startsWith("profile.") }
                    .entries
                    .joinToString("\n") { (k, v) -> "- ${k.removePrefix("profile.")}: $v" }
                    .let { if (it.isNotBlank()) "当前用户画像（请据此调整沟通风格和内容深度）：\n$it" else "" }
            }.getOrDefault("")

            var result: Result<com.mobileclaw.agent.AgentResult> = Result.failure(IllegalStateException("LLM did not start."))
            repeat(LLM_RETRY_MAX_ATTEMPTS) { attemptIndex ->
                result = runCatching {
                    val snap = config.snapshot()
                    rt.run(
                        goal             = contextualGoal,
                        taskType         = executionTaskType,
                        priorContext     = agentPriorContext,
                        episodicContext  = episodicContext,
                        executionContext = executionContext,
                        language         = config.language,
                        imageBase64      = attachedImage,
                        role             = scheduledRole,
                        userProfileContext = userProfileContext,
                        allowedToolIds = allowedToolIds,
                        preferFastLocalVision = attachedImage != null && (snap.localNativeOnly || snap.localModelEnabled),
                        preferFastPlan = route.source != TaskRouteSource.CLASSIFIER,
                        onToken       = { token ->
                            val clean = token.cleanLocalStreamDelta()
                            if (clean.isNotEmpty()) {
                                overlay.onToken(clean)
                                updateSession(resolvedSessionId) { it.copy(streamingToken = (it.streamingToken + clean).cleanLocalStreamingText()) }
                                consoleServer.broadcast("token", clean)
                            }
                        },
                        onThinkToken  = { token ->
                            overlay.onToken(token)
                            updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + token) }
                        },
                        onWorkspaceUpdate = { update ->
                            persistRuntimeWorkspaceUpdate(
                                sessionId = resolvedSessionId,
                                goal = contextualGoal,
                                update = update,
                            )
                        },
                    )
                }
                val shouldRetry = attemptIndex < LLM_RETRY_MAX_ATTEMPTS - 1 &&
                    shouldRetryAfterAgentRun(result.getOrNull(), result.exceptionOrNull())
                if (!shouldRetry) return@repeat
                appendRetryLogLine(
                    resolvedSessionId,
                    if (attemptIndex == 0) "模型这一步返回异常，我正在自动重试一次"
                    else "模型仍然不稳定，我正在再次整理请求",
                )
                delay(700L * (attemptIndex + 1))
            }
            networkTraceJob.cancel()
            runtimeEventJob.cancel()
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) {
                // 新一轮任务已接管本会话：过期回调不得清 isRunning/日志/句柄，否则会击穿 loadSession 的防覆盖守卫，
                // 并把新任务刚注册的 runtime 摘掉。
                if (!isRunGenerationCurrent(resolvedSessionId, runGeneration)) return@launch
                overlay.hide()
                if (isPhoneControlTask) auroraOverlay.endTask()
                updateSession(resolvedSessionId) { s ->
                    s.copy(
                        isRunning = false,
                        runStartedAt = 0L,
                        streamingToken = "",
                        streamingThought = "",
                        activeLogLines = emptyList(),
                        activeAttachments = emptyList(),
                    )
                }
                clearRuntimeHandles(sessionIdAtStart, resolvedSessionId)
                return@launch
            }
            if (!isRunGenerationCurrent(resolvedSessionId, runGeneration)) {
                Log.w(TAG, "Run superseded before completion; dropping stale UI mutations. session=$resolvedSessionId generation=$runGeneration")
                return@launch
            }

            if (isPhoneControlTask) auroraOverlay.endTask()

            val summary = result.getOrNull()?.summary?.let { raw ->
                if (raw.trim().startsWith("LLM error:")) friendlyRuntimeNotice(raw) else raw
            } ?: result.exceptionOrNull()?.message?.let(::friendlyLlmFailureMessage) ?: "Task failed."
            consoleServer.broadcast("task_completed", summary)
            showCompletionOverlayIfNeeded(summary)

            launch {
                val agentResult = result.getOrNull() ?: return@launch
                runCatching { episodicMemory.record(agentResult) }
                runCatching {
                    val replay = app.taskReplayStore.record(agentResult, executionTaskType, scheduledRole)
                    if (agentResult.success && replay.steps.any { !it.skillId.isNullOrBlank() && !it.isError }) {
                        app.taskRecipeStore.createFromReplay(replay)
                    }
                    workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)?.let { workspaceId ->
                        app.workspaceStore.writeJson(
                            workspaceId,
                            "task_replay_${replay.id}",
                            replay,
                        )
                    }
                }
                runCatching {
                    workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)?.let { workspaceId ->
                        app.workspaceStore.recordRun(
                            id = workspaceId,
                            summary = summary,
                            success = agentResult.success,
                            taskType = executionTaskType.name,
                        )
                        app.workspaceStore.writeCheckpoint(
                            workspaceId,
                            WorkspaceCheckpoint(
                                label = "task_complete",
                                taskType = executionTaskType.name,
                                summary = summary.take(300),
                                details = buildString {
                                    appendLine("Goal: ${contextualGoal.take(1000)}")
                                    appendLine()
                                    appendLine("Success: ${agentResult.success}")
                                    appendLine()
                                    appendLine("Summary: $summary")
                                }.trim(),
                            ),
                        )
                        app.workspaceStore.recordEvent(
                            workspaceId,
                            WorkspaceEvent(
                                category = "task_complete",
                                source = "main_view_model",
                                title = "Task complete",
                                summary = summary.take(300),
                                payload = "success=${agentResult.success}, taskType=${executionTaskType.name}",
                            ),
                        )
                    }
                }
            }
            launch(Dispatchers.IO) {
                runCatching {
                    val workspaceId = workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)
                    conversationMemory.addUserMessage(goal, taskId = workspaceId)
                    conversationMemory.addAgentMessage(summary, taskId = workspaceId)
                    recordUserMemoryHints(goal, workspaceId)
                    profileExtractor.extractAndUpdate(goal, summary, taskId = workspaceId)
                    workspaceId?.let {
                        memoryWriter.recordTaskSnapshot(
                            scopeId = it,
                            goal = goal,
                            summary = summary,
                            taskType = executionTaskType.name,
                            success = result.getOrNull()?.success,
                        )
                    }
                    decorateRoleHomeWithTaskSummary(scheduledRole, goal, summary, result.getOrNull()?.success == true)
                }
            }

            val currentRunState = _uiState.value.sessionStates[resolvedSessionId] ?: SessionRunState()
            val finalAgentMessages = run {
                val finalLogLines = buildList {
                    addAll(currentRunState.activeLogLines.finishLatestRunningLine())
                    if (currentRunState.streamingThought.isNotBlank()) {
                        add(LogLine(type = LogType.THINKING, text = currentRunState.streamingThought).withLifecycle(running = false))
                    }
                }
                buildAgentMessages(summary, finalLogLines, currentRunState.activeAttachments, scheduledRole)
            }
            updateSession(resolvedSessionId) { s -> s.copy(
                isRunning = false,
                runStartedAt = 0L,
                streamingToken = "",
                streamingThought = "",
                messages = s.messages + finalAgentMessages,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )}
            clearRuntimeHandles(sessionIdAtStart, resolvedSessionId)

            // Persist the exchange to the session DB（用户消息若已在启动时落库，这里只补 agent 消息）
            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage.takeIf { userMessageVisible && !userMessagePersistedEarly }, finalAgentMessages) }
            }

            // Refresh recommendations after task completes
            launch(Dispatchers.IO) {
                val recent = runCatching { database.episodeDao().recent(limit = 24) }.getOrDefault(emptyList())
                val profileFacts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
                val miniApps = runCatching { app.miniAppStore.all() }.getOrDefault(emptyList())
                val recentUserMsgs = runCatching { database.conversationDao().recentUserMessages(limit = 20) }.getOrDefault(emptyList()).map { it.content }
                val recs = buildSmartRecommendations(recent, profileFacts, miniApps, recentUserMsgs)
                _uiState.update { it.copy(recommendations = recs) }
            }

            // 如果聊天内预览在上一轮暴露了真实运行问题，这里直接续跑自动修复，不等用户补一句“继续”。
            resumePendingMiniAppAutoRepair(resolvedSessionId)
        }
        if (isPhoneControlTask) {
            newJob.invokeOnCompletion {
                auroraOverlay.hide()
            }
        }
        taskJobs[sessionIdAtStart] = newJob
    }

    private fun runDirectChat(
        sessionIdAtStart: String,
        userMessage: ChatMessage,
        goal: String,
        currentRole: Role,
        priorContext: String,
        executionContext: String = "",
        imageBase64: String? = null,
        persistUserMessage: Boolean = true,
        runGeneration: Long,
    ) {
        val newJob = viewModelScope.launch {
            var resolvedSessionId = sessionIdAtStart
            try {
            resolvedSessionId = ensureRunnableSession(sessionIdAtStart)
            if (resolvedSessionId != sessionIdAtStart) adoptRunGeneration(resolvedSessionId, runGeneration)
            Log.d(
                TAG,
                "Direct chat started. session=$resolvedSessionId role=${currentRole.id} hasImage=${imageBase64 != null} goal=${goal.take(160)}"
            )
            workspaceRuntime.ensureSessionBinding(
                sessionId = resolvedSessionId,
                taskType = if (imageBase64 != null) TaskType.GENERAL else TaskType.CHAT,
                goal = goal,
                intent = ContextualTaskIntent(goal),
            )
            persistRuntimeWorkspaceUpdate(
                sessionId = resolvedSessionId,
                goal = goal,
                update = AgentWorkspaceUpdate(
                    stage = "direct_chat_started",
                    taskType = if (imageBase64 != null) TaskType.GENERAL.name else TaskType.CHAT.name,
                    label = "direct_chat_started",
                    summary = goal.take(240),
                    details = "Direct chat turn started.",
                ),
            )
            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) {
                    runCatching {
                        database.sessionDao().updateRole(resolvedSessionId, currentRole.id)
                        loadSessions()
                    }
                }
            }

            val langSection = when (config.language) {
                "zh" -> str(R.string.vm_00cf2c)
                "en" -> "\nYou MUST respond in English.\n"
                else -> ""
            }
            val roleSection = if (currentRole.id != "general" && currentRole.systemPromptAddendum.isNotBlank()) {
                "\n## Your Persona\n${currentRole.systemPromptAddendum.trim()}\n"
            } else ""
            val contextSection = if (priorContext.isNotBlank()) "\n## Stable Memory And Active Artifacts\n$priorContext\n" else ""
            val localChatMode = config.snapshot().localNativeOnly || config.snapshot().localModelEnabled
            val imageInstruction = if (imageBase64 != null) {
                if (config.language == "en") {
                    "\nThe user attached an image. Answer from the image itself. Do not search the web, do not call tools, and do not say you need external lookup unless the user explicitly asks for web research.\n"
                } else {
                    "\n用户附带了一张图片。请直接根据图片本身回答。不要网页搜索，不要调用工具；除非用户明确要求联网查询，否则不要说需要外部检索。\n"
                }
            } else ""
            val directExecutionContext = if (imageBase64 != null) executionContext else ""
            val capabilityInfoInstruction = if (config.language == "en") {
                "If the user asks what MobileClaw can do or which tools are available, do not guess from memory; that request is handled by the INFO capability directory."
            } else {
                "如果用户询问 MobileClaw 能做什么、有哪些工具或某类任务是否支持，不要凭记忆展开能力清单；这类请求由 INFO 能力目录处理。"
            }
            val systemPrompt = if (localChatMode) {
                buildString {
                    appendLine("You are ${currentRole.name}, MobileClaw's on-device assistant.")
                    append(langSection)
                    appendLine(capabilityInfoInstruction)
                    if (directExecutionContext.isNotBlank()) {
                        appendLine(directExecutionContext.trim())
                    }
                    appendLine("Execution channels are separate: chat for conversation, memory for stable facts, skill/self-evolution for capability changes, and artifact/tool routes for actions. Do not merge them into one blob.")
                    append(imageInstruction)
                    if (currentRole.id != "general" && currentRole.systemPromptAddendum.isNotBlank()) {
                        appendLine("Persona: ${currentRole.systemPromptAddendum.trim().take(180)}")
                    }
                    if (priorContext.isNotBlank()) {
                        appendLine("Stable memory and active artifacts:")
                        appendLine(priorContext.take(1200))
                    }
                    appendLine("Answer directly when the user only needs conversation. Short follow-ups refer to the recent context.")
                    appendLine("If the latest user message clearly requires memory, skills, artifacts, files, web, or phone execution, do not behave as if chat is the only available path.")
                }.trim()
            } else {
                """You are ${currentRole.name}, a helpful AI assistant inside MobileClaw.$langSection$imageInstruction$roleSection$contextSection
$capabilityInfoInstruction
${if (directExecutionContext.isNotBlank()) directExecutionContext + "\n" else ""}
## Execution Channels
Chat, memory, skills, and self-evolution are separate channels. Use the right channel for the user's request instead of mixing everything into one response.

## Context Rules
Use the current user message as the source of truth. Treat recent conversation as supporting context only.
Short follow-ups like “继续/改一下/不是这个/换个方式” refer to the most relevant recent message or artifact.
Do not start building pages, HTML, MiniAPPs, or UI artifacts unless the user clearly asks to create or modify one.
If the latest user message clearly requires memory, skills, artifacts, files, web, or phone execution, do not behave as if chat is the only available path.

## Optional Interactive UI
For normal conversation, reply in plain text.
Only embed a ${"```"}ui block when the user explicitly asks for interactive choices, forms, tables, comparisons, dashboards, or says they want buttons/cards.
If you use the UI DSL, it MUST be wrapped exactly as:
${"```"}ui
{"type":"column","children":[...]}
${"```"}
Never output raw UI JSON, `+ ui`, string concatenation, or Kotlin/JavaScript snippets in a chat answer.
Types: column/row(gap,padding,children) | card(title,children) | text(content,size,bold,color,align) | button(label,action,style) | input(key,placeholder) | select(key,options:[]) | table(headers:[],rows:[[]]) | chart_bar/chart_line(data:[],labels:[],title) | progress(value,label) | badge(text,color) | divider | spacer(size)
Actions: "send:message" | "submit:text with {key}" | "copy:text"
For pure conversational replies, greetings, explanations, and simple factual answers, do not output a ui block.""".trimIndent()
            }

            val llm = app.createLlmGateway()
            val chatMessages = buildStructuredDirectChatMessages(
                sessionId = resolvedSessionId,
                systemPrompt = systemPrompt,
                currentGoal = goal,
                imageBase64 = imageBase64,
            )

            var result: Result<com.mobileclaw.llm.ChatResponse> = Result.failure(IllegalStateException("LLM did not start."))
            repeat(LLM_RETRY_MAX_ATTEMPTS) { attemptIndex ->
                result = runCatching {
                    llm.chat(ChatRequest(
                        messages = chatMessages,
                        tools = emptyList(),
                        stream = true,
                        onToken = { token ->
                            val clean = token.cleanLocalStreamDelta()
                            if (clean.isNotEmpty()) {
                                updateSession(resolvedSessionId) { it.copy(streamingToken = (it.streamingToken + clean).cleanLocalStreamingText()) }
                            }
                        },
                    ))
                }
                Log.d(
                    TAG,
                    "Direct chat request finished. session=$resolvedSessionId attempt=${attemptIndex + 1} success=${result.isSuccess} contentLength=${result.getOrNull()?.content?.length ?: 0} error=${result.exceptionOrNull()?.message?.take(160).orEmpty()}"
                )
                val shouldRetry = attemptIndex < LLM_RETRY_MAX_ATTEMPTS - 1 && shouldRetryDirectChat(result.exceptionOrNull())
                if (!shouldRetry) return@repeat
                appendRetryLogLine(
                    resolvedSessionId,
                    if (attemptIndex == 0) "这次回复生成异常，我正在重新生成"
                    else "回复仍然异常，我再试一次更稳的生成",
                )
                delay(500L * (attemptIndex + 1))
            }

            val summary = (result.getOrNull()?.content
                ?: _uiState.value.sessionStates[resolvedSessionId]?.streamingToken?.ifBlank { null }
                ?: result.exceptionOrNull()?.message?.let(::friendlyLlmFailureMessage) ?: "Error.").cleanLocalTurnTokens()
            persistRuntimeWorkspaceUpdate(
                sessionId = resolvedSessionId,
                goal = goal,
                update = AgentWorkspaceUpdate(
                    stage = "direct_chat_completed",
                    taskType = if (imageBase64 != null) TaskType.GENERAL.name else TaskType.CHAT.name,
                    label = "direct_chat_completed",
                    summary = summary.take(240),
                    details = summary.take(4000),
                    success = result.isSuccess,
                ),
            )

            val finalAgentMsg = ChatMessage(
                role = MessageRole.AGENT,
                text = summary,
                senderRoleId = currentRole.id,
                senderRoleName = currentRole.name,
                senderRoleAvatar = currentRole.avatar,
            )
            val isCurrentRun = isRunGenerationCurrent(resolvedSessionId, runGeneration)
            updateSession(resolvedSessionId) { s ->
                // 已被新一轮任务接管时只追加回复，不碰新任务的运行态字段。
                if (isCurrentRun) s.copy(
                    isRunning = false,
                    streamingToken = "",
                    streamingThought = "",
                    messages = s.messages + finalAgentMsg,
                    activeLogLines = emptyList(),
                    activeAttachments = emptyList(),
                ) else s.copy(messages = s.messages + finalAgentMsg)
            }
            Log.d(
                TAG,
                "Direct chat completed. session=$resolvedSessionId success=${result.isSuccess} summaryLength=${summary.length}"
            )

            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage.takeIf { persistUserMessage }, listOf(finalAgentMsg)) }
            }
            launch(Dispatchers.IO) {
                runCatching {
                    val workspaceId = workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)
                    conversationMemory.addUserMessage(goal, taskId = workspaceId)
                    conversationMemory.addAgentMessage(summary, taskId = workspaceId)
                    recordUserMemoryHints(goal, workspaceId)
                    profileExtractor.extractAndUpdate(goal, summary, taskId = workspaceId)
                    workspaceId?.let {
                        memoryWriter.recordTaskSnapshot(
                            scopeId = it,
                            goal = goal,
                            summary = summary,
                            taskType = if (imageBase64 != null) TaskType.GENERAL.name else TaskType.CHAT.name,
                            success = result.isSuccess,
                        )
                    }
                }
            }
            showCompletionOverlayIfNeeded(summary)
            } catch (e: Throwable) {
                val cleanupSessionId = resolvedSessionId.ifBlank { sessionIdAtStart }
                if (e is kotlinx.coroutines.CancellationException) {
                    if (isRunGenerationCurrent(cleanupSessionId, runGeneration)) {
                        updateSession(cleanupSessionId) { s ->
                            s.copy(isRunning = false, runStartedAt = 0L, streamingToken = "", streamingThought = "")
                        }
                    }
                    return@launch
                }
                if (isRunGenerationCurrent(cleanupSessionId, runGeneration)) {
                    updateSession(cleanupSessionId) { s ->
                        s.copy(
                            isRunning = false,
                            runStartedAt = 0L,
                            streamingToken = "",
                            streamingThought = "",
                            messages = s.messages + ChatMessage(
                                role = MessageRole.AGENT,
                                text = e.message ?: "Error.",
                                senderRoleId = currentRole.id,
                                senderRoleName = currentRole.name,
                                senderRoleAvatar = currentRole.avatar,
                            ),
                            activeLogLines = emptyList(),
                            activeAttachments = emptyList(),
                        )
                    }
                }
                Log.e(TAG, "Direct chat failed. session=$cleanupSessionId goal=${goal.take(160)}", e)
            } finally {
                if (isRunGenerationCurrent(resolvedSessionId.ifBlank { sessionIdAtStart }, runGeneration)) {
                    clearRuntimeHandles(sessionIdAtStart, resolvedSessionId)
                }
            }
        }
        taskJobs[sessionIdAtStart] = newJob
    }

    private fun runInfoChannelAnswer(
        sessionIdAtStart: String,
        userMessage: ChatMessage,
        goal: String,
        currentRole: Role,
        persistUserMessage: Boolean = true,
        runGeneration: Long,
    ) {
        val newJob = viewModelScope.launch {
            var resolvedSessionId = sessionIdAtStart
            try {
                resolvedSessionId = ensureRunnableSession(sessionIdAtStart)
                if (resolvedSessionId != sessionIdAtStart) adoptRunGeneration(resolvedSessionId, runGeneration)
                Log.d(TAG, "Info channel answer started. session=$resolvedSessionId goal=${goal.take(160)}")
                val summary = withContext(Dispatchers.IO) { buildMobileClawCapabilityDirectory(goal) }
                val finalAgentMsg = ChatMessage(
                    role = MessageRole.AGENT,
                    text = summary,
                    senderRoleId = currentRole.id,
                    senderRoleName = currentRole.name,
                    senderRoleAvatar = currentRole.avatar,
                )
                val isCurrentRun = isRunGenerationCurrent(resolvedSessionId, runGeneration)
                updateSession(resolvedSessionId) { s ->
                    // 已被新一轮任务接管时只追加回复，不碰新任务的运行态字段。
                    if (isCurrentRun) s.copy(
                        isRunning = false,
                        runStartedAt = 0L,
                        streamingToken = "",
                        streamingThought = "",
                        messages = s.messages + finalAgentMsg,
                        activeLogLines = emptyList(),
                        activeAttachments = emptyList(),
                    ) else s.copy(messages = s.messages + finalAgentMsg)
                }
                if (resolvedSessionId.isNotBlank()) {
                    launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage.takeIf { persistUserMessage }, listOf(finalAgentMsg)) }
                }
                launch(Dispatchers.IO) {
                    runCatching {
                        val workspaceId = workspaceRuntime.resolveSessionWorkspaceId(resolvedSessionId)
                        conversationMemory.addUserMessage(goal, taskId = workspaceId)
                        conversationMemory.addAgentMessage(summary, taskId = workspaceId)
                    }
                }
                Log.d(TAG, "Info channel answer completed. session=$resolvedSessionId summaryLength=${summary.length}")
            } catch (e: Throwable) {
                val cleanupSessionId = resolvedSessionId.ifBlank { sessionIdAtStart }
                if (e is kotlinx.coroutines.CancellationException) {
                    if (isRunGenerationCurrent(cleanupSessionId, runGeneration)) {
                        updateSession(cleanupSessionId) { s ->
                            s.copy(isRunning = false, runStartedAt = 0L, streamingToken = "", streamingThought = "")
                        }
                    }
                    return@launch
                }
                if (isRunGenerationCurrent(cleanupSessionId, runGeneration)) {
                    updateSession(cleanupSessionId) { s ->
                        s.copy(
                            isRunning = false,
                            runStartedAt = 0L,
                            streamingToken = "",
                            streamingThought = "",
                            messages = s.messages + ChatMessage(
                                role = MessageRole.AGENT,
                                text = e.message ?: "能力目录读取失败。",
                                senderRoleId = currentRole.id,
                                senderRoleName = currentRole.name,
                                senderRoleAvatar = currentRole.avatar,
                            ),
                            activeLogLines = emptyList(),
                            activeAttachments = emptyList(),
                        )
                    }
                }
                Log.e(TAG, "Info channel answer failed. session=$cleanupSessionId goal=${goal.take(160)}", e)
            } finally {
                if (isRunGenerationCurrent(resolvedSessionId.ifBlank { sessionIdAtStart }, runGeneration)) {
                    clearRuntimeHandles(sessionIdAtStart, resolvedSessionId)
                }
            }
        }
        taskJobs[sessionIdAtStart] = newJob
    }

    private suspend fun ensureRunnableSession(sessionIdAtStart: String): String {
        if (sessionIdAtStart.isNotBlank()) return sessionIdAtStart
        withContext(Dispatchers.IO) { createNewSessionInternal() }
        val resolvedSessionId = _uiState.value.currentSessionId
        val previousState = _uiState.value.sessionStates[sessionIdAtStart]
        if (previousState != null && resolvedSessionId != sessionIdAtStart) {
            _uiState.update { state ->
                state.copy(sessionStates = (state.sessionStates - sessionIdAtStart) + (resolvedSessionId to previousState))
            }
        }
        return resolvedSessionId
    }

    private fun clearRuntimeHandles(sessionIdAtStart: String, resolvedSessionId: String) {
        taskJobs.remove(resolvedSessionId)
        runtimes.remove(resolvedSessionId)
        if (sessionIdAtStart != resolvedSessionId) {
            taskJobs.remove(sessionIdAtStart)
            runtimes.remove(sessionIdAtStart)
        }
    }

    // 预览失败后，把自动修复挂到当前会话上；若当前没有任务在跑，则直接续跑。
    private fun enqueueMiniAppAutoRepair(sessionId: String, appId: String, previewStatus: String) {
        if (sessionId.isBlank() || appId.isBlank()) return
        val current = pendingMiniAppAutoRepairs[sessionId]
        if (current?.appId == appId && current.previewStatus == previewStatus) return
        val nextAttempt = if (current?.appId == appId) current.attempt + 1 else 1
        if (nextAttempt > MINI_APP_AUTO_REPAIR_MAX_ATTEMPTS) return
        pendingMiniAppAutoRepairs[sessionId] = PendingMiniAppAutoRepair(
            sessionId = sessionId,
            appId = appId,
            previewStatus = previewStatus,
            attempt = nextAttempt,
        )
        if (_uiState.value.sessionStates[sessionId]?.isRunning != true && taskJobs[sessionId] == null) {
            resumePendingMiniAppAutoRepair(sessionId)
        }
    }

    // 当前轮结束后，自动把“查日志 -> 小范围修复 -> 校验 -> 重新打开”继续跑完。
    private fun resumePendingMiniAppAutoRepair(sessionId: String) {
        val pending = pendingMiniAppAutoRepairs.remove(sessionId) ?: return
        if (_uiState.value.sessionStates[sessionId]?.isRunning == true || taskJobs[sessionId] != null) {
            pendingMiniAppAutoRepairs[sessionId] = pending
            return
        }
        val rememberedGoal = activeWorkflows[sessionId]?.originalGoal
            ?.takeIf { it.isNotBlank() }
            ?: "Repair the existing MiniAPP without losing finished features."
        val autoRepairGoal = buildString {
            appendLine("Continue the current MiniAPP task automatically.")
            appendLine("Target MiniAPP id: ${pending.appId}")
            appendLine("Preview status: ${pending.previewStatus}")
            appendLine("Original goal: ${rememberedGoal.take(1200)}")
            appendLine("Required behavior:")
            appendLine("1. Inspect the latest runtime logs for this MiniAPP.")
            appendLine("2. Repair only the failing path shown by logs or preview behavior.")
            appendLine("3. Validate the MiniAPP.")
            appendLine("4. Re-open it and confirm the chat preview renders correctly.")
            appendLine("5. Do not create a new MiniAPP. Do not ask the user to continue.")
            if (pending.attempt > 1) {
                appendLine("This is repair attempt ${pending.attempt}. Change strategy and patch the exact failing code path.")
            }
        }.trim()
        val route = TaskRoute(
            taskType = TaskType.APP_BUILD,
            contextualIntent = ContextualTaskIntent(
                classificationGoal = autoRepairGoal,
                taskTypeOverride = TaskType.APP_BUILD,
                userVisibleSteps = listOf(
                    "我先定位聊天内预览为什么没有正常渲染",
                    "然后只修出错的那一小段逻辑",
                    "修完后重新校验并再次打开确认",
                ),
                executionHint = buildString {
                    appendLine("Automatic MiniAPP repair continuation triggered by chat preview feedback.")
                    appendLine("App id: ${pending.appId}")
                    appendLine("Preview status: ${pending.previewStatus}")
                    appendLine("Keep the existing artifact and continue from its latest runtime state.")
                }.trim(),
            ),
            goalForExecution = autoRepairGoal,
            source = TaskRouteSource.ACTIVE_WORKFLOW,
            goalToRemember = rememberedGoal,
            debugReason = "Auto-continued MiniAPP repair from unhealthy chat preview.",
        )
        runTaskInternal(
            goal = autoRepairGoal,
            visibleUserText = "",
            routeOverride = route,
            showUserMessage = false,
        )
    }

    private fun runCodexDesktopDirect(
        sessionId: String,
        userMessage: ChatMessage,
        userGoal: String,
        prompt: String,
        imageBase64: String?,
        imageLocalPath: String,
        fileAttachment: FileAttachment?,
        showUserMessage: Boolean,
        persistUserMessage: Boolean,
        runGeneration: Long,
    ) {
        if (sessionId.isBlank()) return
        _uiState.update { it.copy(inputImageBase64 = null, inputFileAttachment = null) }
        updateSession(sessionId) { state ->
            state.copy(
                isRunning = true,
                runStartedAt = System.currentTimeMillis(),
                messages = if (showUserMessage) state.messages + userMessage else state.messages,
                activeLogLines = listOf(
                    LogLine(
                        type = LogType.ACTION,
                        text = "发送到电脑 Codex",
                        skillId = "codex_desktop",
                        details = listOf("目标：${userGoal.take(500)}"),
                    ).withLifecycle(running = true),
                ),
                activeAttachments = emptyList(),
                streamingToken = "",
                streamingThought = "",
            )
        }
        overlay.show(userGoal)
        consoleServer.broadcast("task_started", userGoal)

        val job = viewModelScope.launch {
            val result = streamCodexDesktop(
                sessionId = sessionId,
                prompt = prompt,
                attachments = buildCodexDesktopAttachments(imageBase64, imageLocalPath, fileAttachment),
            )
            val finishedAt = System.currentTimeMillis()
            val statusLine = LogLine(
                type = if (result.success) LogType.SUCCESS else LogType.ERROR,
                text = if (result.success) {
                    uiText("电脑 Codex 已返回结果", "Desktop Codex returned a result")
                } else {
                    uiText("电脑 Codex 执行失败", "Desktop Codex failed")
                },
                skillId = "codex_desktop",
                details = listOf(result.output.take(2000)),
                finishedAt = finishedAt,
            )
            val progressLines = _uiState.value.sessionStates[sessionId]
                ?.activeLogLines
                ?.finishLatestRunningLine()
                ?.filter { it.skillId == "codex_desktop" && it.text.isNotBlank() }
                .orEmpty()
            val agentMessage = ChatMessage(
                role = MessageRole.AGENT,
                text = result.output.ifBlank { if (result.success) "电脑 Codex 已完成。" else "电脑 Codex 没有返回内容。" },
                logLines = progressLines + statusLine,
                senderRoleId = _uiState.value.currentRole.id,
                senderRoleName = _uiState.value.currentRole.name,
                senderRoleAvatar = _uiState.value.currentRole.avatar,
            )
            val isCurrentRun = isRunGenerationCurrent(sessionId, runGeneration)
            updateSession(sessionId) { state ->
                // 已被新一轮任务接管时只追加回复，不碰新任务的运行态字段与任务句柄。
                if (isCurrentRun) state.copy(
                    isRunning = false,
                    runStartedAt = 0L,
                    messages = state.messages + agentMessage,
                    activeLogLines = emptyList(),
                    activeAttachments = emptyList(),
                    streamingToken = "",
                    streamingThought = "",
                ) else state.copy(messages = state.messages + agentMessage)
            }
            if (isCurrentRun) {
                taskJobs.remove(sessionId)
                overlay.hide()
            }
            consoleServer.broadcast("task_completed", result.output.take(500))
            withContext(Dispatchers.IO) {
                persistMessages(sessionId, userMessage.takeIf { persistUserMessage }, listOf(agentMessage))
                database.sessionDao().updateTitle(sessionId, userGoal.take(40).ifBlank { "Codex 会话" })
                loadSessions()
            }
        }
        taskJobs[sessionId] = job
    }

    private suspend fun streamCodexDesktop(
        sessionId: String,
        prompt: String,
        attachments: JsonArray = JsonArray(),
    ): com.mobileclaw.skill.SkillResult = withContext(Dispatchers.IO) {
        val endpoint = userConfig.get("codex_desktop_endpoint")?.trim()?.trimEnd('/').orEmpty()
        val token = userConfig.get("codex_desktop_token")?.trim().orEmpty()
        val cwd = userConfig.get("codex_desktop_cwd").orEmpty()
        val model = userConfig.get("codex_desktop_model").orEmpty()
        val provider = userConfig.get("codex_desktop_provider").orEmpty()
        val approval = userConfig.get("codex_desktop_approval").orEmpty()
        val sandbox = userConfig.get("codex_desktop_sandbox").orEmpty()
        if (endpoint.isBlank() || token.isBlank()) {
            return@withContext com.mobileclaw.skill.SkillResult(
                false,
                "Codex desktop bridge is not configured. Please set Bridge URL and Token in Codex Bridge settings.",
            )
        }

        val body = JsonObject().apply {
            addProperty("prompt", prompt)
            addProperty("mobile_session_id", sessionId)
            if (attachments.size() > 0) add("attachments", attachments)
            addProperty("cwd", cwd)
            add("config", JsonObject().apply {
                addProperty("cwd", cwd)
                addProperty("model", model)
                addProperty("provider", provider)
                addProperty("approval", approval)
                addProperty("sandbox", sandbox)
            })
        }
        val req = Request.Builder()
            .url("$endpoint/run_stream")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            codexBridgeStreamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    return@use com.mobileclaw.skill.SkillResult(false, "Codex bridge HTTP ${resp.code}: ${text.take(2000)}")
                }
                val output = StringBuilder()
                var ok = true
                var finalOutput = ""
                resp.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                    lines.forEach { rawLine ->
                        if (rawLine.isBlank()) return@forEach
                        val json = runCatching { JsonParser.parseString(rawLine).asJsonObject }.getOrNull()
                        when (json?.get("type")?.asString) {
                            "output" -> {
                                val text = json.get("text")?.asString.orEmpty()
                                val chunkSize = when {
                                    text.length > 8_000 -> 160
                                    text.length > 3_000 -> 96
                                    else -> 36
                                }
                                val chunkDelayMs = when {
                                    text.length > 8_000 -> 3L
                                    text.length > 3_000 -> 6L
                                    else -> 12L
                                }
                                text.chunked(chunkSize).forEach { chunk ->
                                    output.append(chunk)
                                    val visible = output.toString().cleanCodexDesktopOutput(prompt).takeLast(24_000)
                                    updateSession(sessionId) { state ->
                                        state.copy(streamingToken = visible)
                                    }
                                    overlay.onToken(chunk)
                                    if (chunkDelayMs > 0L && text.length > chunkSize) {
                                        Thread.sleep(chunkDelayMs)
                                    }
                                }
                            }
                            "progress" -> {
                                val fallbackText = json.get("text")?.asString.orEmpty()
                                val text = json.get("label")?.asString?.ifBlank { null } ?: fallbackText
                                val detail = json.get("detail")?.asString?.ifBlank { null }
                                    ?: json.get("output")?.asString?.ifBlank { null }
                                    ?: json.get("command")?.asString?.ifBlank { null }
                                    ?: ""
                                val running = json.get("status")?.asString == "running"
                                if (text.isNotBlank()) {
                                    val line = LogLine(
                                        type = LogType.ACTION,
                                        text = text,
                                        skillId = "codex_desktop",
                                        details = listOf(detail).filter { it.isNotBlank() },
                                    ).withLifecycle(running = running)
                                    updateSession(sessionId) { state ->
                                        state.copy(
                                            activeLogLines = state.activeLogLines.finishLatestRunningLine() + line,
                                        )
                                    }
                                }
                            }
                            "done" -> {
                                ok = json.get("ok")?.asBoolean ?: true
                                finalOutput = json.get("output")?.asString.orEmpty()
                            }
                        }
                    }
                }
                val resolvedOutput = finalOutput.ifBlank { output.toString().trim() }.cleanCodexDesktopOutput(prompt)
                com.mobileclaw.skill.SkillResult(
                    ok,
                    resolvedOutput.ifBlank { if (ok) "Codex finished with no output." else "Codex failed with no output." },
                )
            } ?: com.mobileclaw.skill.SkillResult(false, "Codex bridge returned an empty response.")
        }.getOrElse {
            com.mobileclaw.skill.SkillResult(false, "Codex bridge stream failed: ${it.message}")
        }
    }

    private fun buildCodexDesktopAttachments(
        imageBase64: String?,
        imageLocalPath: String,
        fileAttachment: FileAttachment?,
    ): JsonArray = JsonArray().apply {
        if (!imageBase64.isNullOrBlank()) {
            add(JsonObject().apply {
                addProperty("kind", "image")
                addProperty("name", imageLocalPath.substringAfterLast('/').ifBlank { "mobileclaw-image.jpg" })
                addProperty("mime_type", imageBase64.substringAfter("data:", "").substringBefore(";").ifBlank { "image/jpeg" })
                addProperty("data_uri", imageBase64)
            })
        }
        if (fileAttachment != null) {
            add(JsonObject().apply {
                addProperty("kind", "file")
                addProperty("name", fileAttachment.name.ifBlank { "attachment" })
                addProperty("mime_type", fileAttachment.mimeType.ifBlank { "application/octet-stream" })
                addProperty("is_text", fileAttachment.isText)
                if (fileAttachment.isText) {
                    addProperty("text", fileAttachment.content)
                } else {
                    addProperty("base64", fileAttachment.content)
                }
            })
        }
    }

    private fun shouldAnswerImageDirectly(goal: String): Boolean {
        val text = goal.trim().lowercase()
        if (text.isBlank()) return true
        val explicitAgentIntent = listOf(
            "网页搜索", "联网搜索", "搜索网页", "搜一下", "查一下资料", "找来源", "来源",
            "打开", "启动", "点击", "滑动", "滚动", "输入", "长按", "返回", "操作手机", "控制手机",
            "生成图片", "画图", "创建", "生成页面", "做个页面", "做一个页面", "保存", "下载",
            "web search", "search web", "browse", "open ", "launch ", "click ", "tap ", "scroll ",
        )
        if (explicitAgentIntent.any { text.contains(it) }) return false
        val visualQuestion = listOf(
            "这是什么", "是什么", "图里", "图片", "照片", "截图", "看图", "识别", "描述", "分析这张",
            "what is", "what's", "describe", "identify", "image", "picture", "photo", "screenshot",
        )
        return visualQuestion.any { text.contains(it) } || text.length <= 20 || text.contains("?") || text.contains("？")
    }

    fun stopTask() {
        val sessionId = _uiState.value.currentSessionId
        val shouldStopDesktopCodex = _uiState.value.codexDesktopMode || sessionId in _uiState.value.codexDesktopSessionIds
        taskJobs[sessionId]?.cancel()
        taskJobs.remove(sessionId)
        runtimes.remove(sessionId)
        if (shouldStopDesktopCodex) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    registry.get("codex_desktop")?.execute(mapOf("action" to "stop"))
                }
            }
        }
        overlay.hide()
        auroraOverlay.hide()
        consoleServer.broadcast("task_stopped", "")
        val salvaged = salvageInterruptedRunMessages(sessionId)
        updateSession(sessionId) { state ->
            state.copy(
                isRunning = false,
                runStartedAt = 0L,
                streamingToken = "",
                streamingThought = "",
                messages = state.messages + salvaged,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )
        }
        if (salvaged.isNotEmpty() && sessionId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { persistMessages(sessionId, null, salvaged) } }
        }
    }

    fun setInputImage(imageBase64: String?) {
        _uiState.update { it.copy(inputImageBase64 = imageBase64) }
    }

    fun sendImageMessage(imageBase64: String, prompt: String = "") {
        val hiddenPrompt = prompt.ifBlank {
            "用户发送了一张表情包图片。请根据图片内容、情绪和当前聊天上下文自然回应；不要复述这段系统提示，也不要说“我看到了一个附件”。"
        }
        val sessionId = _uiState.value.currentSessionId
        if (_uiState.value.sessionStates[sessionId]?.isRunning == true || taskJobs[sessionId] != null) {
            stopCurrentRunForNewUserTurn(sessionId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val route = resolveRouteWithAi(
                goal = hiddenPrompt,
                effectiveGoal = hiddenPrompt,
                hasImage = true,
                hasFile = false,
                activeWorkflow = activeWorkflowForCurrentSession(),
            )
            withContext(Dispatchers.Main) {
                runTaskInternal(hiddenPrompt, imageOverride = imageBase64, visibleUserText = "", routeOverride = route)
            }
        }
    }

    fun setFileAttachment(attachment: FileAttachment?) {
        _uiState.update { it.copy(inputFileAttachment = attachment) }
    }

    private val backStack = ArrayDeque<AppPage>().apply { add(AppPage.HOME) }

    fun consumeSettingsLaunchTarget() {
        _uiState.update { it.copy(settingsLaunchTarget = null) }
    }

    fun openGatewayConfig() {
        checkPrivServer()
        loadVideoTasks()
        if (backStack.isEmpty() || backStack.last() != AppPage.SETTINGS) {
            backStack.addLast(AppPage.SETTINGS)
        }
        _uiState.update {
            it.copy(
                currentPage = AppPage.SETTINGS,
                canNavigateBack = backStack.size > 1,
                settingsLaunchTarget = SettingsLaunchTarget.GATEWAY,
            )
        }
    }

    fun navigate(page: AppPage) {
        val targetPage = page
        if (targetPage == AppPage.SKILLS) refreshPromotableSkills()
        if (targetPage == AppPage.PROFILE) loadProfileData()
        if (targetPage == AppPage.SETTINGS) {
            checkPrivServer()
            loadVideoTasks()
        }
        if (targetPage == AppPage.VIDEO_GENERATOR) loadVideoTasks()
        if (targetPage == AppPage.WORKSPACE) loadCurrentWorkspaceSnapshot()
        if (targetPage == AppPage.APPS) loadMiniApps()
        if (targetPage == AppPage.ROLES || targetPage == AppPage.ROLE_DETAIL || targetPage == AppPage.AI_TOWN) townStore.ensureRooms(roleManager.all())
        if (targetPage == AppPage.GROUPS) loadGroups()
        if (targetPage == AppPage.GROUP_CHAT) {
            _uiState.update { it.copy(groupState = it.groupState.copy(unreadCount = 0)) }
        }

        if (targetPage == AppPage.HOME) {
            backStack.clear()
            backStack.addLast(targetPage)
        } else if (backStack.isEmpty() || backStack.last() != targetPage) {
            backStack.addLast(targetPage)
        }

        if (targetPage == AppPage.BROWSER && _uiState.value.browserUrl.isBlank()) {
            _uiState.update { it.copy(browserUrl = "https://www.bing.com", currentPage = targetPage, canNavigateBack = backStack.size > 1) }
            return
        }
        _uiState.update { it.copy(currentPage = targetPage, canNavigateBack = backStack.size > 1) }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
            val page = backStack.last()
            if (page == AppPage.APPS) loadMiniApps()
            val clearEdit = page != AppPage.ROLE_EDIT
            _uiState.update { it.copy(
                currentPage = page,
                canNavigateBack = backStack.size > 1,
                editingRole = if (clearEdit) null else it.editingRole,
            ) }
        }
    }

    fun navigateToBrowser(url: String) {
        val currentPage = _uiState.value.currentPage
        if (backStack.isEmpty()) {
            backStack.addLast(if (currentPage == AppPage.BROWSER) AppPage.HOME else currentPage)
        } else if (backStack.last() != currentPage && currentPage != AppPage.BROWSER) {
            backStack.addLast(currentPage)
        }
        if (backStack.last() != AppPage.BROWSER) backStack.addLast(AppPage.BROWSER)
        _uiState.update { it.copy(browserUrl = url, currentPage = AppPage.BROWSER, canNavigateBack = backStack.size > 1) }
    }

    fun loadMiniApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = runCatching { app.miniAppStore.all() }.getOrDefault(emptyList())
            _uiState.update { it.copy(miniApps = apps) }
        }
    }

    fun clearPendingAppOpen() {
        _uiState.update { it.copy(openAppId = null) }
    }

    fun clearChatMiniAppPreview() {
        miniAppValidationOverlay.hide(notifyDismissed = false)
        _uiState.update {
            it.copy(
                chatMiniAppPreviewId = null,
                chatMiniAppPreviewMode = "",
                chatMiniAppPreviewSessionId = null,
                chatMiniAppPreviewStatus = "",
                chatMiniAppPreviewHealthy = true,
            )
        }
    }

    fun updateChatMiniAppPreviewStatus(appId: String, status: String, healthy: Boolean) {
        val normalized = status.trim().ifBlank {
            if (healthy) "Validation preview passed" else "Validation preview found issues"
        }
        val snapshot = _uiState.value
        if (snapshot.chatMiniAppPreviewId != appId) return
        val isValidationPreview = snapshot.chatMiniAppPreviewMode == "validation" || snapshot.chatMiniAppPreviewMode == "overlay_validation"
        val previewSessionId = snapshot.chatMiniAppPreviewSessionId ?: snapshot.currentSessionId
        val changed = snapshot.chatMiniAppPreviewStatus != normalized || snapshot.chatMiniAppPreviewHealthy != healthy
        _uiState.update {
            if (it.chatMiniAppPreviewId != appId) it
            else it.copy(
                chatMiniAppPreviewStatus = normalized,
                chatMiniAppPreviewHealthy = healthy,
            )
        }
        if (!changed || healthy) return
        updateSession(previewSessionId) { state ->
            if (!state.isRunning) return@updateSession state
            val line = LogLine(
                type = LogType.OBSERVATION,
                text = normalized.take(220),
                skillId = "app_manager",
                details = listOf(
                    uiDetailLine("本步结果", "Result", uiText(
                        "聊天里的验证预览已经看到运行问题",
                        "The validation preview in chat has detected a runtime issue",
                    )),
                    uiDetailLine("补充判断", "Note", uiText(
                        "先收起这个验证窗口，继续查日志并做一轮针对性修复，不要直接重写整个 MiniAPP",
                        "Close the validation preview, inspect logs, and make a targeted fix instead of rewriting the whole MiniAPP",
                    )),
                ),
            )
            if (state.activeLogLines.lastOrNull()?.text == line.text) state
            else state.copy(activeLogLines = state.activeLogLines + line)
        }
        if (isValidationPreview) {
            miniAppValidationOverlay.hide(notifyDismissed = false)
            _uiState.update {
                if (it.chatMiniAppPreviewId != appId) it
                else it.copy(
                    chatMiniAppPreviewId = null,
                    chatMiniAppPreviewMode = "",
                    chatMiniAppPreviewSessionId = null,
                    chatMiniAppPreviewStatus = "",
                    chatMiniAppPreviewHealthy = true,
                )
            }
        }
        enqueueMiniAppAutoRepair(sessionId = previewSessionId, appId = appId, previewStatus = normalized)
    }

    fun clearAiPageOpen() {
        _uiState.update { it.copy(openAiPageId = null) }
    }

    fun openTownRole(roleId: String) {
        _uiState.update { it.copy(openTownRoleId = roleId) }
    }

    fun closeTownRole() {
        _uiState.update { it.copy(openTownRoleId = null) }
    }

    fun deleteAiPage(id: String) {
        viewModelScope.launch(Dispatchers.IO) { app.aiPageStore.delete(id) }
    }

    fun openHtmlViewer(attachment: SkillAttachment.HtmlData) {
        _uiState.update {
            val stack = it.htmlAttachmentStack + attachment
            it.copy(openHtmlAttachment = attachment, htmlAttachmentStack = stack)
        }
    }

    fun closeHtmlViewer() {
        _uiState.update {
            val stack = it.htmlAttachmentStack.dropLast(1)
            it.copy(openHtmlAttachment = stack.lastOrNull(), htmlAttachmentStack = stack)
        }
    }

    // ── Group chat ────────────────────────────────────────────────────────────

    private val groupManager: com.mobileclaw.agent.GroupManager
        get() = runCatching { app.groupManager }
            .getOrElse {
                android.util.Log.w("MainViewModel", "GroupManager was not initialized; creating fallback instance", it)
                com.mobileclaw.agent.GroupManager(app)
            }
    private val groupAgentJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    @Volatile private var groupHistoryStoreRef: GroupHistoryStore? = null
    private val groupHistoryStore: GroupHistoryStore
        get() = groupHistoryStoreRef ?: synchronized(this) {
            groupHistoryStoreRef ?: GroupHistoryStore(
                context = app,
                gson = gson,
                serializeAttachments = ::serializeAttachments,
                deserializeAttachments = ::deserializeAttachments,
            ).also { groupHistoryStoreRef = it }
        }

    @Volatile private var groupConversationStoreRef: GroupConversationStore? = null
    private val groupConversationStore: GroupConversationStore
        get() = groupConversationStoreRef ?: synchronized(this) {
            groupConversationStoreRef ?: GroupConversationStore(
                groupMessageDao = database.groupMessageDao(),
                groupHistoryStore = groupHistoryStore,
                deserializeAttachments = ::deserializeAttachments,
                serializeAttachments = ::serializeAttachments,
            ).also { groupConversationStoreRef = it }
        }

    private val groupTurnExecutor by lazy {
        GroupTurnExecutor(
            app = app,
            llmProvider = { llm },
            registry = registry,
        )
    }
    @Volatile private var groupRuntimeDiagnosticsRef: GroupRuntimeDiagnostics? = null
    private val groupRuntimeDiagnostics: GroupRuntimeDiagnostics
        get() = groupRuntimeDiagnosticsRef ?: synchronized(this) {
            groupRuntimeDiagnosticsRef ?: GroupRuntimeDiagnostics(
                groupMessageDao = database.groupMessageDao(),
                groupHistoryStore = groupHistoryStore,
                rolesProvider = { roleManager.all() },
            ).also { groupRuntimeDiagnosticsRef = it }
        }
    @Volatile private var groupChatStopped = false
    private val GROUP_TASK_POOL_LIMIT = 4
    private val groupTurnScheduler by lazy {
        GroupTurnScheduler(
            taskPoolLimit = GROUP_TASK_POOL_LIMIT,
            initialFanOut = GROUP_CHAT_INITIAL_MAX,
            buildMemoryContext = ::groupSchedulingText,
            resolveTaskType = ::resolveTaskTypeForGroup,
        )
    }

    private fun clearPendingGroupTurns() {
        groupTurnScheduler.clear()
    }

    private fun pendingGroupTurnsIsEmpty(): Boolean =
        groupTurnScheduler.isEmpty()
    private val GROUP_CHAT_INITIAL_MAX = 1
    private val GROUP_CHAT_ORGANIC_MAX = 1
    private val GROUP_MESSAGE_PAGE_SIZE = 20
    private val currentSemanticFactsForWorkspace: List<SemanticFactLike>
        get() = _uiState.value.profileState.semanticFacts.map { fact ->
            object : SemanticFactLike {
                override val key: String = fact.key
                override val value: String = fact.value
                override val enabled: Boolean = fact.enabled
                override val updatedAt: Long = fact.updatedAt
            }
        }

    fun loadGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = groupManager.all()
            val previews = groupConversationStore.loadPreviews(groups)
            android.util.Log.d("ClawGroup", "loadGroups groups=${groups.size} previews=${previews.size} ids=${groups.joinToString { it.id }}")
            groupRuntimeDiagnostics.log("loadGroups", groups)
            _uiState.update { state ->
                state.copy(groupState = state.groupState.copy(groups = groups, previews = previews))
            }
        }
    }

    fun createGroup(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            initializeGroupBubbleStyles(group)
            groupManager.save(group)
            loadGroups()
        }
    }

    fun updateGroupMembers(groupId: String, memberRoleIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanMembers = memberRoleIds.distinct().filter { roleId ->
                roleManager.get(roleId) != null
            }
            if (cleanMembers.isEmpty()) return@launch
            val current = groupManager.get(groupId) ?: _uiState.value.groupState.openGroup?.takeIf { it.id == groupId } ?: return@launch
            val updated = current.copy(
                memberRoleIds = cleanMembers,
                updatedAt = System.currentTimeMillis(),
            )
            initializeGroupBubbleStyles(updated)
            groupManager.save(updated)
            _uiState.update { state ->
                state.copy(
                    groupState = state.groupState.copy(
                        groups = groupManager.all(),
                        openGroup = if (state.groupState.openGroup?.id == groupId) updated else state.groupState.openGroup,
                    ),
                )
            }
            loadGroups()
        }
    }

    private fun initializeGroupBubbleStyles(group: com.mobileclaw.agent.Group) {
        group.memberRoleIds.forEachIndexed { index, roleId ->
            val role = roleManager.get(roleId) ?: return@forEachIndexed
            if (role.chatBubbleStyle != ChatBubbleStyle()) return@forEachIndexed
            roleManager.save(role.copy(chatBubbleStyle = defaultGroupBubbleStyleFor(role, index)))
        }
    }


    fun deleteGroup(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager.delete(id)
            database.groupMessageDao().deleteForGroup(id)
            runCatching { groupHistoryStore.historyFile(id).delete() }
            loadGroups()
            if (_uiState.value.groupState.openGroup?.id == id) closeGroupChat()
        }
    }

    fun openGroupChat(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            groupChatStopped = false
            val pageSize = GROUP_MESSAGE_PAGE_SIZE
            val snapshot = groupConversationStore.openGroup(group.id, pageSize)
            val currentRoles = roleManager.all()
            val missingRoles = group.memberRoleIds.filter { roleId -> currentRoles.none { it.id == roleId } }
            android.util.Log.d(
                "ClawGroup",
                "openGroup id=${group.id} name=${group.name} members=${group.memberRoleIds.joinToString()} " +
                    "missingRoles=${missingRoles.joinToString()} dbMessages=${snapshot.dbMessages.size} backupMessages=${snapshot.backupMessages.size} " +
                    "merged=${snapshot.mergedMessages.size} dbLatest=${snapshot.dbMessages.lastOrNull()?.createdAt} backupLatest=${snapshot.backupMessages.lastOrNull()?.createdAt}"
            )
            groupRuntimeDiagnostics.log(
                marker = "openGroup",
                groups = groupManager.all(),
                activeGroup = group,
                activeMessages = snapshot.mergedMessages,
                activeBackupMessages = snapshot.backupMessages,
            )
            val running = groupAgentJobs.isNotEmpty()
            _uiState.update { state ->
                state.copy(
                    groupState = state.groupState.copy(
                        openGroup = group,
                        messages = snapshot.mergedMessages,
                        historyOffset = pageSize,
                        historyHasMore = snapshot.total > pageSize,
                        historyLoading = false,
                        isRunning = running,
                        typingAgents = if (running) state.groupState.typingAgents else emptySet(),
                        workingAgents = if (running) state.groupState.workingAgents else emptySet(),
                        unreadCount = 0,
                    ),
                )
            }
            navigate(AppPage.GROUP_CHAT)
        }
    }

    fun loadOlderGroupMessages() {
        val group = _uiState.value.groupState.openGroup ?: return
        val state = _uiState.value
        if (state.groupState.historyLoading || !state.groupState.historyHasMore) return
        _uiState.update { it.copy(groupState = it.groupState.copy(historyLoading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pageSize = GROUP_MESSAGE_PAGE_SIZE
                val offset = _uiState.value.groupState.historyOffset
                val page = groupConversationStore.loadOlder(group.id, pageSize, offset, _uiState.value.groupState.messages)
                _uiState.update {
                    if (it.groupState.openGroup?.id != group.id) it else it.copy(
                        groupState = it.groupState.copy(
                            messages = page.messages,
                            historyOffset = page.nextOffset,
                            historyHasMore = page.hasMore,
                            historyLoading = false,
                        ),
                    )
                }
            } finally {
                _uiState.update {
                    if (it.groupState.openGroup?.id == group.id) {
                        it.copy(groupState = it.groupState.copy(historyLoading = false))
                    } else it
                }
            }
        }
    }

    fun closeGroupChat() {
        _uiState.update {
            it.copy(
                groupState = it.groupState.copy(
                    openGroup = null,
                    messages = emptyList(),
                    historyOffset = 0,
                    historyHasMore = false,
                    historyLoading = false,
                    pendingMessages = emptyList(),
                ),
            )
        }
        navigateBack()
    }

    fun stopGroupChat() {
        groupChatStopped = true
        groupAgentJobs.values.forEach { it.cancel() }
        groupAgentJobs.clear()
        clearPendingGroupTurns()
        overlay.hide()
        _uiState.update {
            it.copy(groupState = it.groupState.copy(isRunning = false, typingAgents = emptySet(), workingAgents = emptySet(), pendingMessages = emptyList()))
        }
    }

    fun stopGroupAgent(roleId: String) {
        groupAgentJobs[roleId]?.cancel()
        groupAgentJobs.remove(roleId)
        _uiState.update {
            it.copy(groupState = it.groupState.copy(typingAgents = it.groupState.typingAgents - roleId, workingAgents = it.groupState.workingAgents - roleId))
        }
        if (groupAgentJobs.isEmpty()) {
            _uiState.update { it.copy(groupState = it.groupState.copy(isRunning = false)) }
        }
    }

    fun sendGroupMessage(text: String, attachments: List<SkillAttachment> = emptyList()) {
        val group = _uiState.value.groupState.openGroup ?: return
        if (text.isBlank() && attachments.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = GroupMessage(groupId = group.id, senderId = "user", senderName = str(R.string.group_chat_df1fd9), senderAvatar = _uiState.value.userAvatarUri.orEmpty(), text = text, attachments = attachments)
            val rowId = database.groupMessageDao().insert(userMsg.toEntity())
            val savedUserMsg = userMsg.copy(id = rowId)
            groupHistoryStore.appendBackup(savedUserMsg)
            android.util.Log.d("ClawGroup", "insert user groupId=${group.id} rowId=$rowId textLen=${text.length} attachments=${attachments.size}")
            _uiState.update { it.copy(groupState = it.groupState.copy(messages = it.groupState.messages + savedUserMsg)) }
            recordUserMemoryHints(text, workspaceRuntime.resolveSessionWorkspaceId(_uiState.value.currentSessionId))
            conversationMemory.addUserMessage(text)

            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isEmpty()) return@launch

            groupChatStopped = false
            _uiState.update { it.copy(groupState = it.groupState.copy(isRunning = true)) }
            groupManager.touch(group.id)
            loadGroups()

            if (groupAgentJobs.size >= GROUP_TASK_POOL_LIMIT) {
                enqueueUserGroupTurn(allMembers, text)
            } else {
                startGroupTurns(group, allMembers, text)
            }
        }
    }

    private suspend fun startGroupTurns(group: com.mobileclaw.agent.Group, allMembers: List<Role>, userText: String) {
        groupTurnScheduler.buildInitialTurns(allMembers, userText).forEach { launch ->
            launchScheduledGroupTurn(group, allMembers, launch)
        }
    }

    private suspend fun enqueueUserGroupTurn(allMembers: List<Role>, userText: String) {
        val pendingMessages = groupTurnScheduler.enqueueUserTurn(allMembers, userText)
        _uiState.update { it.copy(groupState = it.groupState.copy(pendingMessages = pendingMessages)) }
    }

    private fun groupSchedulingText(userText: String, taskType: TaskType): String {
        val memory = buildUserMemoryContextForPrompt(userText, taskType, workspaceRuntime.resolveSessionWorkspaceId(_uiState.value.currentSessionId)).take(1200)
        return listOf(userText, memory).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private suspend fun resolveTaskTypeForGroup(userText: String): TaskType =
        resolveRouteWithAi(
            goal = userText,
            effectiveGoal = userText,
            hasImage = false,
            hasFile = false,
            activeWorkflow = null,
        ).taskType

    /**
     * chainDepth controls how many "organic reaction" hops are allowed after a message is posted.
     * User-triggered turns start at depth 2.
     * Each non-PASS reply invites all idle members to optionally react (they self-filter via [PASS]).
     * Depth 0 = no more auto-reactions; explicit @mentions always chain regardless of depth.
     */
    private fun launchGroupAgentTurn(
        group: com.mobileclaw.agent.Group,
        role: Role,
        allMembers: List<Role>,
        delayMs: Long = 0,
        chainDepth: Int = 0,
        longTask: Boolean = false,
        triggerText: String = "",
        requireResponse: Boolean = false,
    ) {
        if (groupAgentJobs.containsKey(role.id)) return
        if (groupAgentJobs.size >= GROUP_TASK_POOL_LIMIT) {
            val pendingMessages = groupTurnScheduler.enqueueDeferredTurn(
                role = role,
                triggerText = triggerText,
                chainDepth = chainDepth,
                longTask = longTask,
                requireResponse = requireResponse,
            )
            _uiState.update { it.copy(groupState = it.groupState.copy(pendingMessages = pendingMessages)) }
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            val taskType = TaskClassifier.classify(triggerText.ifBlank { _uiState.value.groupState.messages.lastOrNull()?.text.orEmpty() })
            val workspaceId = workspaceRuntime.ensureGroupBinding(group.id, role.id, taskType, triggerText.ifBlank { group.name })
            val groupRoute = TaskRoute(
                taskType = taskType,
                contextualIntent = ContextualTaskIntent(classificationGoal = triggerText),
                goalForExecution = triggerText,
                source = TaskRouteSource.CLASSIFIER,
            )
            val groupOrchestration = taskOrchestrator.orchestrate(
                route = groupRoute,
                goal = triggerText,
                hasImage = false,
                hasFile = false,
                role = role,
                language = config.language,
            )
            val groupAllowedToolIds = resolveAllowedToolIds(groupRoute, groupOrchestration.channelDecision.toolHints, triggerText)
            val phoneTask = taskType == TaskType.PHONE_CONTROL
            try {
                if (delayMs > 0) delay(delayMs)
                if (groupChatStopped) return@launch
                val startsWorking = longTask || phoneTask || taskType !in listOf(TaskType.CHAT, TaskType.GENERAL)
                _uiState.update { state ->
                    if (startsWorking) {
                        state.copy(groupState = state.groupState.copy(workingAgents = state.groupState.workingAgents + role.id))
                    } else {
                        state.copy(groupState = state.groupState.copy(typingAgents = state.groupState.typingAgents + role.id))
                    }
                }
                if (phoneTask) {
                    overlay.showCompact("${role.name}: ${triggerText.take(40)}")
                    auroraOverlay.beginTask()
                }
                val history = _uiState.value.groupState.messages
                memoryWriter.updateTaskState(workspaceId, if (startsWorking) "executing" else "thinking", triggerText.take(200))
                val memoryPrompt = buildUserMemoryContextForPrompt(triggerText, taskType, workspaceId)
                val systemPrompt = buildGroupSystemPrompt(
                    role = role,
                    groupName = group.name,
                    allMembers = allMembers,
                    memoryPrompt = memoryPrompt,
                    executionContext = groupOrchestration.toPromptBlock(),
                )
                conversationMemory.addUserMessage(triggerText, taskId = workspaceId)
                recordUserMemoryHints(triggerText, workspaceId)
                // Each agent sees its own past messages as "assistant"; everyone else (user + other AIs) as "user"
                val historyMsgs = history.takeLast(30).map { msg ->
                    val attachmentText = if (msg.attachments.isEmpty()) "" else {
                        "\n[附件] " + msg.attachments.joinToString("; ") { groupAttachmentPrompt(it) }
                    }
                    val image = msg.attachments.firstOrNull { it is SkillAttachment.ImageData } as? SkillAttachment.ImageData
                    if (msg.senderId == role.id) {
                        Message(role = "assistant", content = msg.text + attachmentText)
                    } else {
                        Message(role = "user", content = "[${msg.senderName}]: ${msg.text}$attachmentText", imageBase64 = image?.base64)
                    }
                }
                val callMessages = listOf(Message(role = "system", content = systemPrompt)) +
                    historyMsgs +
                    listOf(Message(role = "user", content = buildGroupTurnInstruction(role.name, triggerText, requireResponse)))

                val response = groupTurnExecutor.runTurn(
                    role = role,
                    baseMessages = callMessages,
                    taskType = taskType,
                    memoryContext = memoryPrompt,
                    allowedToolIds = groupAllowedToolIds,
                    maxSkillCalls = if (longTask || taskType == TaskType.PHONE_CONTROL) 12 else 4,
                    requireResponse = requireResponse,
                    shouldStop = { groupChatStopped },
                    onToolStart = { skillId, params ->
                        _uiState.update { st ->
                            st.copy(groupState = st.groupState.copy(
                                typingAgents = st.groupState.typingAgents - role.id,
                                workingAgents = st.groupState.workingAgents + role.id,
                            ))
                        }
                        if (phoneTask || skillId in VISUAL_SKILL_IDS) {
                            overlay.onSkillCalling(skillId, params)
                            if (skillId == "see_screen") auroraOverlay.flashFullScreen() else auroraOverlay.flash()
                        }
                    },
                    onToolEnd = { output ->
                        if (phoneTask) overlay.onObservation(output)
                        if (!startsWorking) {
                            _uiState.update { st ->
                                st.copy(groupState = st.groupState.copy(
                                    workingAgents = st.groupState.workingAgents - role.id,
                                    typingAgents = st.groupState.typingAgents + role.id,
                                ))
                            }
                        }
                    },
                )
                val cleanResponse = response.text.trim()
                val shouldSkipLowValueReply = !requireResponse && isLowValueGroupReply(cleanResponse, response.attachments)

                // [PASS] means the agent found the message irrelevant to them — skip silently
                if ((cleanResponse.isNotBlank() || response.attachments.isNotEmpty()) &&
                    !cleanResponse.equals("[PASS]", ignoreCase = true) &&
                    !shouldSkipLowValueReply
                ) {
                    val agentMsg = GroupMessage(groupId = group.id, senderId = role.id,
                        senderName = role.name, senderAvatar = role.avatar, text = cleanResponse, attachments = response.attachments)
                    val rowId = database.groupMessageDao().insert(agentMsg.toEntity())
                    val savedAgentMsg = agentMsg.copy(id = rowId)
                    groupHistoryStore.appendBackup(savedAgentMsg)
                    conversationMemory.addAgentMessage(cleanResponse, taskId = workspaceId)
                    profileExtractor.extractAndUpdate(triggerText, cleanResponse, taskId = workspaceId)
                    memoryWriter.recordTaskSnapshot(
                        scopeId = workspaceId,
                        goal = triggerText,
                        summary = cleanResponse,
                        taskType = taskType.name,
                        success = true,
                    )
                    memoryWriter.updateTaskState(workspaceId, "completed", cleanResponse.take(200))
                    android.util.Log.d("ClawGroup", "insert agent groupId=${group.id} role=${role.id} rowId=$rowId textLen=${cleanResponse.length} attachments=${response.attachments.size}")
                    groupManager.touch(group.id)
                    loadGroups()
                    val onScreen = _uiState.value.currentPage == AppPage.GROUP_CHAT && _uiState.value.groupState.openGroup?.id == group.id
                    _uiState.update { st ->
                        if (st.groupState.openGroup?.id == group.id) {
                            st.copy(groupState = st.groupState.copy(
                                messages = st.groupState.messages + savedAgentMsg,
                                unreadCount = if (onScreen) 0 else st.groupState.unreadCount + 1,
                            ))
                        } else {
                            st.copy(groupState = st.groupState.copy(unreadCount = st.groupState.unreadCount + 1))
                        }
                    }

                    if (!groupChatStopped) {
                        val mentions = parseMentions(cleanResponse)

                        // Explicit @mentions: always chain, no depth limit, immediate
                        allMembers
                            .filter { r -> r.id != role.id && mentions.any { m ->
                                r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true)
                            }}
                                .forEach { launchGroupAgentTurn(group, it, allMembers, chainDepth = chainDepth, triggerText = cleanResponse) }

                        // Organic reactions: allow a short natural thread. The depth and
                        // low-value filter prevent noisy pile-ons, but idle members still
                        // get room to keep the room alive when nobody is speaking.
                        if (chainDepth > 0) {
                            val reactionProb = when (chainDepth) {
                                6 -> 0.78f
                                5 -> 0.66f
                                4 -> 0.52f
                                3 -> 0.38f
                                2 -> 0.24f
                                else -> 0.10f
                            }
                            allMembers
                                .filter { r -> r.id != role.id && !groupAgentJobs.containsKey(r.id) &&
                                    mentions.none { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) }
                                }
                                .shuffled()
                                .take(GROUP_CHAT_ORGANIC_MAX)
                                .forEachIndexed { index, reactor ->
                                    // Relevance boost: if the response text mentions this reactor's name, higher chance
                                    val nameHit = cleanResponse.contains(reactor.name, ignoreCase = true)
                                    val shouldContinue = shouldContinueGroupThread(cleanResponse)
                                    val effectiveProb = when {
                                        shouldContinue && index == 0 -> 0.92f
                                        nameHit -> minOf(reactionProb + 0.28f, 1.0f)
                                        shouldContinue -> minOf(reactionProb + 0.20f, 1.0f)
                                        else -> reactionProb
                                    }
                                    if (kotlin.random.Random.nextFloat() < effectiveProb) {
                                        val reactionDelay = (300L..1600L).random()
                                        launchGroupAgentTurn(group, reactor, allMembers,
                                            delayMs = reactionDelay,
                                            chainDepth = chainDepth - 1,
                                            triggerText = cleanResponse,
                                            requireResponse = shouldRequireGroupReaction(cleanResponse, chainDepth, index),
                                        )
                                    }
                                }
                        }
                    }
                }
            } finally {
                groupAgentJobs.remove(role.id)
                _uiState.update {
                    it.copy(groupState = it.groupState.copy(
                        typingAgents = it.groupState.typingAgents - role.id,
                        workingAgents = it.groupState.workingAgents - role.id,
                    ))
                }
                val finishedPhoneTask = TaskClassifier.classify(triggerText.ifBlank { "" }) == TaskType.PHONE_CONTROL
                runCatching {
                    val finalState = if (groupChatStopped) "blocked" else if (groupAgentJobs.containsKey(role.id)) "executing" else "completed"
                    memoryWriter.updateTaskState(workspaceId, finalState, triggerText.take(200))
                }
                if (finishedPhoneTask) {
                    auroraOverlay.endTask()
                }

                drainPendingGroupTurns(group, allMembers)
                if (groupAgentJobs.isEmpty() && pendingGroupTurnsIsEmpty()) {
                    auroraOverlay.hide()
                    if (finishedPhoneTask) {
                        showCompletionOverlayIfNeeded("手机操作已完成，可以回到 MobileClaw 查看结果。")
                    } else {
                        overlay.hide()
                    }
                    _uiState.update {
                        it.copy(groupState = it.groupState.copy(
                            isRunning = false,
                            pendingMessages = emptyList(),
                            typingAgents = emptySet(),
                            workingAgents = emptySet(),
                        ))
                    }
                }
            }
        }
        groupAgentJobs[role.id] = job
        job.invokeOnCompletion {
            groupAgentJobs.remove(role.id, job)
            if (groupAgentJobs.isEmpty() && pendingGroupTurnsIsEmpty()) {
                _uiState.update { state ->
                    state.copy(groupState = state.groupState.copy(
                        isRunning = false,
                        pendingMessages = emptyList(),
                        typingAgents = state.groupState.typingAgents - role.id,
                        workingAgents = state.groupState.workingAgents - role.id,
                    ))
                }
            }
        }
    }

    private fun launchScheduledGroupTurn(
        group: com.mobileclaw.agent.Group,
        allMembers: List<Role>,
        launch: GroupTurnLaunch,
    ) {
        launchGroupAgentTurn(
            group = group,
            role = launch.role,
            allMembers = allMembers,
            delayMs = launch.delayMs,
            chainDepth = launch.chainDepth,
            longTask = launch.longTask,
            triggerText = launch.triggerText,
            requireResponse = launch.requireResponse,
        )
    }

    private fun drainPendingGroupTurns(group: com.mobileclaw.agent.Group, allMembers: List<Role>) {
        val batch = groupTurnScheduler.drainPendingTurns(
            allMembers = allMembers,
            busyRoleIds = groupAgentJobs.keys,
            stopped = groupChatStopped,
        )
        _uiState.update {
            it.copy(groupState = it.groupState.copy(
                pendingMessages = batch.pendingMessages,
                isRunning = it.groupState.isRunning || batch.launches.isNotEmpty(),
            ))
        }
        batch.launches.forEach { launch ->
            launchScheduledGroupTurn(group, allMembers, launch)
        }
    }

    /** Triggers a random member to proactively start a conversation only when the user explicitly asks for it. */
    fun sparkGroupChat() {
        val group = _uiState.value.groupState.openGroup ?: return
        if (_uiState.value.groupState.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isEmpty()) return@launch
            groupChatStopped = false
            _uiState.update { it.copy(groupState = it.groupState.copy(isRunning = true)) }
            allMembers.shuffled().take(1).forEach { role ->
                launchGroupAgentTurn(
                    group = group,
                    role = role,
                    allMembers = allMembers,
                    delayMs = 0L,
                    chainDepth = 6,
                    triggerText = "用户希望群成员自然开启一个新话题。",
                    requireResponse = true,
                )
            }
        }
    }

    private fun GroupMessage.toEntity() = com.mobileclaw.memory.db.GroupMessageEntity(
        id = id,
        groupId = groupId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        text = text,
        attachmentsJson = serializeAttachments(attachments),
        createdAt = createdAt,
    )

    fun deleteApp(appId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.miniAppStore.delete(appId) }
            loadMiniApps()
        }
    }

    fun checkPrivServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val connected = PrivilegedClient.isAvailable()
            _uiState.update { it.copy(privServerConnected = connected) }
        }
    }

    fun setProfileFact(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.semanticMemory.set(key = key, value = value, source = "profile_ui") }
            refreshProfileFacts()
        }
    }

    fun setMemoryPinned(key: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.semanticMemory.setPinned(key, pinned) }
            refreshProfileFacts()
        }
    }

    fun setMemoryEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.semanticMemory.setEnabled(key, enabled) }
            refreshProfileFacts()
        }
    }

    fun deleteMemoryFact(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.semanticMemory.delete(key) }
            refreshProfileFacts()
        }
    }

    fun loadProfileData() {
        viewModelScope.launch {
            _uiState.update { it.copy(profileState = it.profileState.copy(isLoading = true)) }
            val episodes = runCatching { app.database.episodeDao().recent(limit = 20) }.getOrDefault(emptyList())
            val convCount = runCatching { conversationMemory.messageCount() }.getOrDefault(0)
            val semanticFacts = runCatching { app.semanticMemory.pageIncludingDisabled(limit = PROFILE_MEMORY_PAGE_SIZE, offset = 0) }.getOrDefault(emptyList())
            val facts = semanticFacts.filter { it.enabled }.associate { it.key to it.value }
            _uiState.update {
                it.copy(profileState = it.profileState.copy(
                    facts = facts,
                    semanticFacts = semanticFacts,
                    memoryHasMore = semanticFacts.size >= PROFILE_MEMORY_PAGE_SIZE,
                    memoryLoadingMore = false,
                    recentEpisodes = episodes,
                    conversationCount = convCount,
                    isLoading = false,
                ))
            }
        }
    }

    fun loadMoreProfileMemory() {
        val state = _uiState.value.profileState
        if (state.memoryLoadingMore || !state.memoryHasMore) return
        viewModelScope.launch {
            val offset = _uiState.value.profileState.semanticFacts.size
            _uiState.update { it.copy(profileState = it.profileState.copy(memoryLoadingMore = true)) }
            val next = runCatching {
                app.semanticMemory.pageIncludingDisabled(limit = PROFILE_MEMORY_PAGE_SIZE, offset = offset)
            }.getOrDefault(emptyList())
            _uiState.update { current ->
                val merged = (current.profileState.semanticFacts + next)
                    .distinctBy { it.key }
                    .sortedWith(compareByDescending<com.mobileclaw.memory.MemoryFact> { it.pinned }.thenByDescending { it.updatedAt }.thenBy { it.key })
                current.copy(
                    profileState = current.profileState.copy(
                        semanticFacts = merged,
                        facts = merged.filter { it.enabled }.associate { it.key to it.value },
                        memoryHasMore = next.size >= PROFILE_MEMORY_PAGE_SIZE,
                        memoryLoadingMore = false,
                    )
                )
            }
        }
    }

    fun triggerProfileExtraction() {
        viewModelScope.launch {
            _uiState.update { it.copy(profileState = it.profileState.copy(isExtracting = true)) }
            val episodes = runCatching { app.database.episodeDao().recent(limit = 20) }.getOrDefault(emptyList())
            val convJob = launch { runCatching { profileExtractor.extractAndUpdate("", "") } }
            val epJob   = launch { runCatching { profileExtractor.extractFromEpisodes(episodes) } }
            convJob.join(); epJob.join()
            val loadedCount = _uiState.value.profileState.semanticFacts.size.coerceAtLeast(PROFILE_MEMORY_PAGE_SIZE)
            val semanticFacts = runCatching { app.semanticMemory.pageIncludingDisabled(limit = loadedCount, offset = 0) }.getOrDefault(emptyList())
            val facts = semanticFacts.filter { it.enabled }.associate { it.key to it.value }
            _uiState.update {
                it.copy(profileState = it.profileState.copy(
                    facts = facts,
                    semanticFacts = semanticFacts,
                    memoryHasMore = semanticFacts.size >= loadedCount,
                    isExtracting = false,
                ))
            }
        }
    }

    // ── AI Profile Analysis ──────────────────────────────────────────────────

    fun generatePersonalitySummary() {
        if (_uiState.value.profileState.personalitySummaryLoading) return
        val facts = _uiState.value.profileState.facts.filter { it.key.startsWith("profile.") }
        if (facts.isEmpty()) return
        _uiState.update {
            it.copy(profileState = it.profileState.copy(personalitySummaryLoading = true, personalitySummary = ""))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val factsText = facts.entries.joinToString("\n") { (k, v) -> "- ${k.removePrefix("profile.")}: $v" }
            val foundationalMemory = buildUserMemoryContextForPrompt("生成用户画像总结", TaskType.GENERAL).take(1600)
            val prompt = """
你是一位专业心理分析师。基于以下用户画像数据，写一段人格分析（约200字），用第二人称（str(R.string.group_chat_df1fd9)）表达，语气温暖而专业。

请包含：
1. MBTI人格类型推断（如 INTJ、ENFP 等）及一句核心说明
2. 3-4个核心人格特质关键词
3. 沟通与社交风格特点
4. 主要优势与潜在成长空间
5. 一句画龙点睛的总结

用户画像数据：
$factsText

全局记忆约束：
$foundationalMemory

注意：直接开始分析，用流畅自然的语言，不要说str(R.string.vm_008363)之类的套话。如果数据较少，基于已有信息大胆推断。
""".trimIndent()
            val summary = runCatching {
                llm.chat(ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = str(R.string.vm_1b9366)),
                        Message(role = "user", content = prompt),
                    ),
                    stream = false,
                )).content?.trim() ?: ""
            }.getOrDefault("")
            _uiState.update {
                it.copy(profileState = it.profileState.copy(personalitySummary = summary, personalitySummaryLoading = false))
            }
        }
    }

    private suspend fun fetchDimensionQuiz(dimensionId: String, dimensionTitle: String): List<AiQuizQuestion> {
        val facts = _uiState.value.profileState.facts
        val relevantFacts = facts.entries
            .filter { it.key.startsWith("profile.$dimensionId.") || it.key.startsWith("profile.personality.") || it.key.startsWith("profile.cognitive.") }
            .joinToString("\n") { (k, v) -> "- ${k.removePrefix("profile.")}: $v" }
            .ifBlank { str(R.string.vm_empty) }
        val foundationalMemory = buildUserMemoryContextForPrompt("生成 $dimensionTitle 心理测试题", TaskType.GENERAL).take(1600)
        val prompt = """
你是专业心理学家。请为"$dimensionTitle"维度生成5道深度心理测试题，持续深入了解用户的潜在特征。

当前已知用户信息：
$relevantFacts

全局记忆约束：
$foundationalMemory

要求：
- 问题要有深度，能揭示潜在心理特征，避免表面化
- 每题4个答案选项，各选项之间有细微差别，能区分不同心理倾向
- 结合已知信息，探索尚未了解的方面，不要重复已知内容
- 语言自然，贴近真实心理量表的风格

严格输出JSON数组（无markdown，无额外文字）：
[{"question":str(R.string.vm_9c4af5),"hint":str(R.string.vm_22565a),"answers":[str(R.string.vm_628112),str(R.string.vm_d37944),str(R.string.vm_ad93b3),str(R.string.vm_2855b8)],"factKey":"profile.${dimensionId}.xxx"}]
""".trimIndent()
        val content = runCatching {
            llm.chat(ChatRequest(
                messages = listOf(
                    Message(role = "system", content = str(R.string.vm_8bf4bd)),
                    Message(role = "user", content = prompt),
                ),
                stream = false,
            )).content?.trim() ?: ""
        }.getOrDefault("")
        return runCatching {
            val raw = content.trimStart().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.map { el ->
                val obj = el.asJsonObject
                AiQuizQuestion(
                    question = obj["question"]?.asString ?: "",
                    hint     = obj["hint"]?.asString ?: "",
                    answers  = obj["answers"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                    factKey  = obj["factKey"]?.asString ?: "profile.$dimensionId.misc",
                )
            }.filter { it.question.isNotBlank() && it.answers.size >= 2 }
        }.getOrDefault(emptyList())
    }

    fun generateDimensionQuiz(dimensionId: String, dimensionTitle: String) {
        if (_uiState.value.profileState.dimensionQuizLoading == dimensionId) return
        _uiState.update { it.copy(profileState = it.profileState.copy(dimensionQuizLoading = dimensionId)) }
        viewModelScope.launch(Dispatchers.IO) {
            val questions = fetchDimensionQuiz(dimensionId, dimensionTitle)
            _uiState.update { state ->
                state.copy(profileState = state.profileState.copy(
                    dimensionQuizzes = state.profileState.dimensionQuizzes + (dimensionId to questions),
                    dimensionQuizLoading = null,
                ))
            }
        }
    }

    fun prewarmAllDimensionQuizzes(dimensions: List<ProfileDimension>) {
        val todo = dimensions.filter { it.id !in _uiState.value.profileState.dimensionQuizzes }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (dim in todo) {
                if (_uiState.value.profileState.dimensionQuizzes.containsKey(dim.id)) continue
                val questions = fetchDimensionQuiz(dim.id, dim.title)
                _uiState.update { state ->
                    state.copy(profileState = state.profileState.copy(
                        dimensionQuizzes = state.profileState.dimensionQuizzes + (dim.id to questions)
                    ))
                }
                delay(400)
            }
        }
    }

    // ── Skill Notes ──────────────────────────────────────────────────────────

    fun saveSkillNote(skillId: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.skillNotesStore.set(skillId, note)
        }
    }

    fun generateSkillNote(skillId: String, skillName: String, description: String) {
        if (_uiState.value.skillNoteGenerating == skillId) return
        _uiState.update { it.copy(skillNoteGenerating = skillId) }
        viewModelScope.launch(Dispatchers.IO) {
            val prompt = "你是AI工具说明撰写专家。请用中文为以下能力写一条简洁实用的用户备注（30字以内），帮助用户理解其使用场景和价值。\n\n能力名称：$skillName\n能力描述：$description\n\n直接输出备注文字，不要加任何前缀或解释。"
            val note = runCatching {
                llm.chat(ChatRequest(
                    messages = listOf(Message(role = "user", content = prompt)),
                    stream = false,
                )).content?.trim() ?: ""
            }.getOrDefault("")
            if (note.isNotBlank()) {
                app.skillNotesStore.set(skillId, note)
            }
            _uiState.update { it.copy(skillNoteGenerating = null) }
        }
    }

    fun showSettings(show: Boolean) = navigate(if (show) AppPage.SETTINGS else AppPage.HOME)
    fun showSkillManager(show: Boolean) = navigate(if (show) AppPage.SKILLS else AppPage.HOME)
    fun openWorkspacePage() {
        loadCurrentWorkspaceSnapshot()
        navigate(AppPage.WORKSPACE)
    }

    fun loadCurrentWorkspaceSnapshot() {
        val workspaceId = workspaceRuntime.resolveSessionWorkspaceId(_uiState.value.currentSessionId)
        val snapshot = workspaceId?.let { app.workspaceStore.inspectorSnapshot(it) }
        val facts = workspaceId?.let { id ->
            _uiState.value.profileState.semanticFacts
                .filter { fact -> fact.key.startsWith("session.$id.") }
                .sortedByDescending { fact -> fact.updatedAt }
        }.orEmpty()
        _uiState.update { it.copy(workspaceState = it.workspaceState.copy(snapshot = snapshot, facts = facts)) }
    }

    fun promoteWorkspaceFact(memoryKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { memoryWriter.promoteScopedMemory(memoryKey) }
            refreshProfileFacts()
            loadCurrentWorkspaceSnapshot()
        }
    }

    fun deleteWorkspaceFact(memoryKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.semanticMemory.delete(memoryKey) }
            refreshProfileFacts()
            loadCurrentWorkspaceSnapshot()
        }
    }

    fun saveConfig(snapshot: ConfigSnapshot) {
        viewModelScope.launch {
            val oldLanguage = config.language
            config.update(snapshot)
            if (snapshot.language != oldLanguage) {
                _languageChanged.emit(snapshot.language)
            }
            navigate(AppPage.HOME)
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch {
            val snap = config.snapshot()
            if (model.startsWith("local:")) {
                config.update(snap.copy(localModelEnabled = true, localModelId = model.removePrefix("local:")))
                _uiState.update { it.copy(currentModel = model) }
                return@launch
            }
            val updatedGateways = snap.gateways.map {
                if (it.id == snap.activeGatewayId || (snap.activeGatewayId == null && it == snap.gateways.firstOrNull())) {
                    val existingCapabilities = it.capabilities.filterNot { cap -> cap.type.equals("chat", ignoreCase = true) }
                    it.copy(
                        model = model,
                        capabilities = existingCapabilities + GatewayCapabilityConfig(type = "chat", model = model),
                    )
                } else it
            }
            config.update(snap.copy(gateways = updatedGateways, localModelEnabled = false, localNativeOnly = false))
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    fun setLocalModelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val snap = config.snapshot()
            config.update(snap.copy(
                localModelEnabled = enabled,
                localNativeOnly = if (enabled) snap.localNativeOnly else false,
                localToolCallingEnabled = if (enabled) snap.localToolCallingEnabled else false,
            ))
        }
    }

    fun setLocalNativeOnly(enabled: Boolean) {
        viewModelScope.launch {
            val snap = config.snapshot()
            config.update(snap.copy(localNativeOnly = enabled, localModelEnabled = if (enabled) true else snap.localModelEnabled))
        }
    }

    fun setLocalToolCallingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val snap = config.snapshot()
            config.update(snap.copy(
                localToolCallingEnabled = enabled,
                localModelEnabled = if (enabled) true else snap.localModelEnabled,
            ))
        }
    }

    fun selectLocalModel(id: String) {
        viewModelScope.launch {
            val normalized = app.localModelManager.runnableModelIdFor(id) ?: id.removePrefix("local:")
            val snap = config.snapshot()
            config.update(snap.copy(localModelEnabled = true, localModelId = normalized))
            _uiState.update { it.copy(currentModel = "local:$normalized") }
        }
    }

    fun downloadLocalModel(id: String, token: String = "", sourceUrl: String = "") {
        viewModelScope.launch { app.localModelManager.download(id, token, sourceUrl) }
    }

    fun importLocalModel(id: String, uri: android.net.Uri) {
        viewModelScope.launch { app.localModelManager.importModel(id, uri) }
    }

    fun deleteLocalModel(id: String) {
        viewModelScope.launch { app.localModelManager.delete(id) }
    }

    fun fetchModels() {
        if (_uiState.value.modelsLoading) return
        _uiState.update { it.copy(modelsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val snap = config.snapshot()
            val remoteModels = if (snap.localNativeOnly) {
                emptyList()
            } else {
                runCatching { OpenAiGateway.fetchModels(snap.chatEndpoint, snap.chatApiKey) }.getOrDefault(emptyList())
            }
            val localModels = app.localModelManager.models.value
                .filter { it.supportsChatRuntime }
                .map { it.modelId }
            val models = (remoteModels + localModels).distinct()
            _uiState.update { it.copy(
                availableModels = if (models.isNotEmpty()) models else it.availableModels,
                modelsLoading = false,
            ) }
        }
    }

    fun testVirtualDisplay() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { app.virtualDisplayManager.testSupport() }
                .getOrElse { "error:${it.message}" }
            _uiState.update { it.copy(virtualDisplayTestResult = result) }
        }
    }

    fun clearVirtualDisplayResult() {
        _uiState.update { it.copy(virtualDisplayTestResult = null) }
    }

    // ── Skill Manager ────────────────────────────────────────────────────────

    fun promoteSkill(skillId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { loader.promote(skillId) }
            refreshPromotableSkills()
        }
    }

    fun demoteSkill(skillId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { loader.demote(skillId) }
            refreshPromotableSkills()
        }
    }

    fun deleteSkill(skillId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { loader.delete(skillId) }
            refreshPromotableSkills()
        }
    }

    fun installMarketSkill(def: com.mobileclaw.skill.SkillDefinition) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { loader.persist(def) }
            refreshPromotableSkills()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(app, str(R.string.installed_skill, def.meta.nameZh ?: def.meta.name), Toast.LENGTH_SHORT).show()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: str(R.string.vm_not_)
                    Toast.makeText(app, str(R.string.install_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshPromotableSkills() {
        val all = registry.userVisibleWithEffectiveLevel()
        val promotable = all.filter { !it.isBuiltin && it.injectionLevel == 2 }
        _uiState.update { it.copy(promotableSkills = promotable, allSkills = all) }
    }

    fun setSkillLevel(skillId: String, level: Int) {
        val defaultLevel = registry.get(skillId)?.meta?.injectionLevel ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (level == defaultLevel) {
                // Reset to default — remove override
                app.skillLevelStore.remove(skillId)
                registry.removeLevelOverride(skillId)
            } else {
                app.skillLevelStore.set(skillId, level)
                registry.setLevelOverride(skillId, level)
            }
            withContext(Dispatchers.Main) { refreshPromotableSkills() }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun onAppForegroundChanged(foreground: Boolean) {
        if (foreground) {
            overlay.hideCompleted()
            return
        }
        if (overlay.state.visible && !overlay.state.completed) {
            overlay.collapseToCompactIfRunning()
        }
    }

    private fun isMobileClawForegroundNow(): Boolean {
        val foregroundPackage = ClawAccessibilityService.getCurrentPackageOrNull()
        return if (foregroundPackage != null) {
            foregroundPackage == app.packageName
        } else {
            app.isAppForeground()
        }
    }

    private fun showCompletionOverlayIfNeeded(summary: String) {
        if (isMobileClawForegroundNow()) {
            overlay.hide()
        } else {
            overlay.showCompleted(summary)
        }
    }

    private fun shouldUseScheduledRoleForRun(
        goal: String,
        taskType: TaskType,
        currentRole: Role,
        scheduledRole: Role,
    ): Boolean {
        if (scheduledRole.id == currentRole.id) return true
        val text = goal.lowercase()
        val explicitRoleMention = text.contains(scheduledRole.id.lowercase()) ||
            scheduledRole.name.isNotBlank() && text.contains(scheduledRole.name.lowercase())
        if (explicitRoleMention) return true
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL)) {
            val currentRoleCanHandle = taskType in currentRole.preferredTaskTypes ||
                (currentRole.id != Role.DEFAULT.id && currentRole.forcedSkillIds.isNotEmpty())
            return !currentRoleCanHandle
        }
        return false
    }

    private fun requiresUserExecutionConfirmation(route: TaskRoute): Boolean {
        if (route.goalForExecution.startsWith(CONFIRM_TASK_PREFIX)) return false
        return ChannelPermissionPolicy.evaluate(
            route = route,
            accessibilityEnabled = ClawAccessibilityService.isEnabled(),
        ).requiresConfirmation
    }

    private fun shouldPushAccessibilityCardForGoal(goal: String): Boolean {
        if (activeWorkflowForCurrentSession()?.taskType == TaskType.PHONE_CONTROL) return false
        val text = goal.lowercase()
        val actionHit = listOf(
            "打开", "启动", "点", "点击", "搜索", "找", "附近", "下单", "发送", "输入", "滑动", "操作",
            "帮我到", "帮我在", "替我", "进入", "切到",
            "open", "launch", "tap", "click", "search", "nearby", "send", "input", "scroll",
        ).any { text.contains(it) }
        val appHit = listOf(
            "美团", "微信", "支付宝", "抖音", "淘宝", "京东", "高德", "百度地图", "小红书", "b站", "哔哩",
            "meituan", "wechat", "alipay", "douyin", "taobao", "jd", "maps",
        ).any { text.contains(it) }
        val phoneControlPhraseHit = listOf("操作手机", "控制手机", "打开app", "打开 app", "手机上", "帮我到").any { text.contains(it) }
        if (phoneControlPhraseHit) return true
        return actionHit && appHit
    }

    private fun shouldRunDirectChat(route: TaskRoute): Boolean {
        if (route.contextualIntent.disableToolNarrowing) return false
        if (route.contextualIntent.aiPrimaryChannel == ChannelType.INFO) return true
        if (route.taskType == TaskType.CHAT) {
            return route.contextualIntent.aiSupportingChannels.isEmpty() &&
                route.contextualIntent.aiToolHints.isEmpty()
        }
        if (route.taskType != TaskType.GENERAL) return false
        if (route.contextualIntent.aiPrimaryChannel != null) {
            return route.contextualIntent.aiPrimaryChannel == ChannelType.CHAT &&
                route.contextualIntent.aiSupportingChannels.isEmpty() &&
                route.contextualIntent.aiToolHints.isEmpty()
        }
        return false
    }

    private fun TaskRoute.primaryChannelForExecution(): ChannelType =
        contextualIntent.aiPrimaryChannel ?: when (taskType) {
            TaskType.PHONE_CONTROL -> ChannelType.PHONE
            TaskType.CHAT, TaskType.GENERAL ->
                if (looksLikeCapabilityInfoQuestion(contextualIntent.classificationGoal)) ChannelType.INFO else ChannelType.CHAT
            TaskType.WEB_RESEARCH -> ChannelType.WEB
            TaskType.FILE_CREATE, TaskType.APP_BUILD -> ChannelType.ARTIFACT
            TaskType.IMAGE_GENERATION -> ChannelType.MEDIA
            TaskType.VPN_CONTROL -> ChannelType.VPN
            TaskType.SKILL_MANAGEMENT -> ChannelType.SKILL
            TaskType.CODE_EXECUTION -> ChannelType.CODE
        }

    private fun looksLikeCapabilityInfoQuestion(goal: String): Boolean {
        val text = goal.trim().lowercase()
        if (text.isBlank() || text.length > 80) return false
        val capabilityNeedles = listOf(
            "你能做什么", "你可以做什么", "你会什么", "你有什么能力", "有哪些能力", "有什么能力",
            "有哪些工具", "有什么工具", "能力列表", "工具列表", "能干什么", "能帮我干嘛",
            "mobileclaw能做什么", "mobileclaw 可以做什么", "支持什么", "能不能做",
            "what can you do", "what are your capabilities", "available tools", "capability list",
        )
        return capabilityNeedles.any { text.contains(it) }
    }

    private suspend fun buildMobileClawCapabilityDirectory(goal: String): String {
        val metas = registry.userVisibleMetasWithTaxonomy()
        val byCategory = metas.flatMap { meta ->
            meta.categories.ifEmpty { emptyList() }.map { category -> category to meta }
        }.groupBy({ it.first }, { it.second })
        val snap = config.snapshot()
        val accessibility = if (ClawAccessibilityService.isEnabled()) "已开启" else "未开启，需要先授权无障碍"
        val codexDesktop = if (
            _uiState.value.userConfigEntries["codex_desktop_endpoint"]?.value.orEmpty().isNotBlank() &&
            _uiState.value.userConfigEntries["codex_desktop_token"]?.value.orEmpty().isNotBlank()
        ) "已配置" else "未配置"
        val imageReady = registry.contains("generate_image") && (
            snap.activeGateway?.hasCapability("image") == true ||
                userConfig.get("image_api_endpoint")?.isNotBlank() == true ||
                userConfig.get("huggingface_api_key")?.isNotBlank() == true
            )
        val videoReady = registry.contains("generate_video") && (
            snap.activeGateway?.hasCapability("video") == true ||
                userConfig.get("video_api_endpoint")?.isNotBlank() == true
            )
        fun examples(category: SkillToolCategory, limit: Int = 4): String {
            val items = byCategory[category].orEmpty()
                .distinctBy { it.id }
                .filterNot { it.internalTool }
                .sortedWith(compareBy<SkillMeta> { it.injectionLevel }.thenBy { it.id })
                .take(limit)
                .map { it.nameZh ?: it.name }
            return items.joinToString("、").ifBlank { "当前未发现可见工具" }
        }
        return """
我可以做这些事，具体会按任务需要再读取对应能力目录，不会每次聊天都塞满上下文：

- 普通聊天和思考：回答问题、解释概念、写作润色、翻译、规划、学习辅导。
- 手机操作：查看屏幕、点击、输入、滑动、打开应用并完成多步骤流程。状态：$accessibility。代表工具：${examples(SkillToolCategory.PHONE)}。
- 创建产物：MiniAPP、原生页面、仪表盘、表单、HTML/CSS/JS、文件和文档。代表工具：${examples(SkillToolCategory.ARTIFACT)}。
- 网页与信息检索：搜索、打开网页、读取页面内容、提炼结论。代表工具：${examples(SkillToolCategory.WEB)}。
- 图片/视频：图片生成状态：${if (imageReady) "可用" else "未完整配置"}；视频生成状态：${if (videoReady) "可用" else "未完整配置"}。代表工具：${examples(SkillToolCategory.MEDIA)}。
- 记忆和配置：记住偏好、读取会话/工作区上下文、管理默认配置。代表工具：${examples(SkillToolCategory.MEMORY)}。
- 技能和自我改进：安装/创建技能、切换角色、调整页面或能力策略。代表工具：${examples(SkillToolCategory.SKILL)}。
- 电脑 Codex 协作：把复杂开发任务交给电脑端 Codex bridge。状态：$codexDesktop。

你可以直接说目标，比如“帮我生成一个记账 MiniAPP”“打开美团搜附近烤肉”“总结这个网页”“记住我喜欢简洁黑白风”。我会先判断是普通聊天、INFO 目录，还是需要进入 agent 执行通道。
        """.trimIndent()
    }

    private fun isRecentContinuationRoute(route: TaskRoute, goal: String): Boolean {
        if (route.source != TaskRouteSource.RECENT_CONTEXT) return false
        if (route.taskType == TaskType.PHONE_CONTROL && !ClawAccessibilityService.isEnabled()) return false
        val normalized = goal.trim().lowercase()
        if (normalized.length > 40) return false
        return normalized in setOf(
            "继续", "接着", "继续执行", "继续操作", "继续吧", "接着来",
            "然后呢", "下一步", "go on", "continue", "next",
        ) || normalized.contains("继续") || normalized.contains("接着")
    }

    private fun resolveAllowedToolIds(
        route: TaskRoute,
        hintedToolIds: List<String>,
        goal: String,
    ): List<String> {
        if (route.contextualIntent.disableToolNarrowing) return emptyList()
        if (route.taskType == TaskType.APP_BUILD) {
            // APP_BUILD is already narrowed by TaskToolPolicy. Do not add a second channel-level
            // whitelist here, or the model can know the correct artifact tool but still be unable
            // to call it in follow-up patch flows.
            return emptyList()
        }
        if (route.taskType == TaskType.GENERAL &&
            route.source in setOf(TaskRouteSource.RECENT_CONTEXT, TaskRouteSource.ACTIVE_WORKFLOW, TaskRouteSource.AI_ROUTER)
        ) {
            if (route.contextualIntent.miniApp != null || goal.contains("artifact_type=miniapp", ignoreCase = true)) {
                return listOf("app_manager", "read_file", "create_file", "list_files", "create_html", "ui_builder")
            }
            if (route.contextualIntent.aiPage != null || goal.contains("artifact_type=ai_native_page", ignoreCase = true)) {
                return listOf("ui_builder", "app_manager", "create_html", "read_file", "create_file", "list_files")
            }
        }
        return hintedToolIds
    }

    private fun requestTaskExecutionConfirmation(goal: String, taskType: TaskType, confirmedRoute: TaskRoute? = null) {
        if (taskType == TaskType.PHONE_CONTROL && !ClawAccessibilityService.isEnabled()) {
            pendingAccessibilityTaskGoal = goal
            appendConfirmationExchange(
                goal,
                ConfirmationFlow.accessibilityActionCard(
                    goal = goal,
                    confirmAccessibilityTaskPrefix = CONFIRM_ACCESSIBILITY_TASK_PREFIX,
                    openAccessibilityPrefix = OPEN_ACCESSIBILITY_PREFIX,
                    cancelText = CANCEL_CONFIRMATION_TEXT,
                ),
            )
            return
        }
        if (confirmedRoute != null) {
            synchronized(pendingConfirmedRoutes) {
                pendingConfirmedRoutes[goal] = confirmedRoute
            }
        }
        appendConfirmationExchange(
            goal,
            ConfirmationFlow.taskConfirmationCard(
                goal = goal,
                taskType = taskType,
                confirmTaskPrefix = CONFIRM_TASK_PREFIX,
                cancelText = CANCEL_CONFIRMATION_TEXT,
            ),
        )
    }

    private fun requestRoleSwitchConfirmation(goal: String, role: Role) {
        pendingRoleSwitchTaskGoal = goal
        appendConfirmationExchange(
            goal,
            ConfirmationFlow.roleSwitchConfirmationCard(
                goal = goal,
                role = role,
                confirmRolePrefix = CONFIRM_ROLE_PREFIX,
                cancelText = CANCEL_CONFIRMATION_TEXT,
            ),
        )
    }

    private fun appendConfirmationExchange(userText: String, card: SkillAttachment.ActionCard) {
        viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            if (sessionId.isBlank()) {
                withContext(Dispatchers.IO) { createNewSessionInternal() }
                sessionId = _uiState.value.currentSessionId
            }
            val userMessage = ChatMessage(MessageRole.USER, userText)
            val agentMessage = ChatMessage(
                role = MessageRole.AGENT,
                text = "",
                attachments = listOf(card),
                senderRoleId = _uiState.value.currentRole.id,
                senderRoleName = _uiState.value.currentRole.name,
                senderRoleAvatar = _uiState.value.currentRole.avatar,
            )
            updateSession(sessionId) { s -> s.copy(messages = s.messages + userMessage + agentMessage) }
            if (sessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(sessionId, userMessage, listOf(agentMessage)) }
            }
        }
    }

    fun checkAppUpdate() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PgyerReleaseSkill(app, userConfig).execute(mapOf("action" to "check_update"))
                }.getOrElse {
                    com.mobileclaw.skill.SkillResult(false, "request failed: ${it.message.orEmpty()}")
                }
            }
            appendConfirmationResolution(formatAppUpdateResult(result.success, result.output))
        }
    }

    private fun formatAppUpdateResult(success: Boolean, rawOutput: String): String {
        val lines = rawOutput
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val current = lines.firstOrNull { it.startsWith("Current:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
        val remote = lines.firstOrNull { it.startsWith("Remote:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
        val notes = lines.firstOrNull { it.startsWith("Notes:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

        if (
            rawOutput.contains("Configure pgyer_api_key", ignoreCase = true) ||
            rawOutput.contains("Configure release channel", ignoreCase = true)
        ) {
            return "还没有配置更新通道，请先到用户配置里保存更新所需的 App Key 和 API Key。"
        }

        if (!success) {
            val cleaned = rawOutput
                .replace(Regex("(?i)pgyer"), "更新服务")
                .replace("蒲公英", "更新服务")
                .lineSequence()
                .filterNot { it.startsWith("Download:", ignoreCase = true) }
                .joinToString("\n")
                .ifBlank { "网络或配置异常" }
            return "检测更新失败：$cleaned"
        }

        val hasNew = rawOutput.contains("newer MobileClaw build", ignoreCase = true)
        return buildString {
            appendLine(if (hasNew) "发现新版本。" else "当前已经是最新版本。")
            current?.takeIf { it.isNotBlank() }?.let { appendLine("当前版本：$it") }
            remote?.takeIf { it.isNotBlank() }?.let { appendLine("最新版本：$it") }
            notes?.takeIf { it.isNotBlank() }?.let { appendLine("更新内容：$it") }
        }.trim()
    }

    private fun appendConfirmationResolution(text: String) {
        viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            if (sessionId.isBlank()) {
                withContext(Dispatchers.IO) { createNewSessionInternal() }
                sessionId = _uiState.value.currentSessionId
            }
            val agentMessage = ChatMessage(
                role = MessageRole.AGENT,
                text = text,
                senderRoleId = _uiState.value.currentRole.id,
                senderRoleName = _uiState.value.currentRole.name,
                senderRoleAvatar = _uiState.value.currentRole.avatar,
            )
            updateSession(sessionId) { s -> s.copy(messages = s.messages + agentMessage) }
            if (sessionId.isNotBlank()) {
                launch(Dispatchers.IO) {
                    database.sessionMessageDao().insert(
                        SessionMessageEntity(
                            sessionId = sessionId,
                            role = "agent",
                            text = agentMessage.text,
                            senderRoleId = agentMessage.senderRoleId,
                            senderRoleName = agentMessage.senderRoleName,
                            senderRoleAvatar = agentMessage.senderRoleAvatar,
                        )
                    )
                }
            }
        }
    }

    private fun inferExplicitRoleSwitch(goal: String): ExplicitRoleSwitch? {
        return ConfirmationFlow.inferExplicitRoleSwitch(goal, _uiState.value.availableRoles + Role.BUILTINS)
    }

    private fun activeWorkflowForCurrentSession(): ActiveWorkflow? {
        val sessionId = _uiState.value.currentSessionId
        return activeWorkflows[sessionId]?.takeIf { System.currentTimeMillis() - it.updatedAt < ACTIVE_WORKFLOW_TTL_MS }
    }

    private fun rememberActiveWorkflow(sessionId: String, goal: String, taskType: TaskType, role: Role) {
        if (sessionId.isBlank()) return
        if (taskType in listOf(TaskType.CHAT, TaskType.GENERAL)) return
        activeWorkflows[sessionId] = ActiveWorkflow(
            originalGoal = sanitizeWorkflowGoal(goal).take(ACTIVE_WORKFLOW_GOAL_LIMIT),
            taskType = taskType,
            roleId = role.id,
        )
    }

    private fun sanitizeWorkflowGoal(goal: String): String =
        goal
            .substringBefore("\n\n[用户继续当前流程]")
            .replace(Regex("""\n\n\[上下文约束]\n[\s\S]*$"""), "")
            .replace(Regex("""\n\n\[resolved_context:[\s\S]*$"""), "")
            .trim()
            .ifBlank { goal.take(ACTIVE_WORKFLOW_GOAL_LIMIT) }

    private fun buildPriorContext(
        goal: String,
        taskType: TaskType = TaskType.GENERAL,
        intent: ContextualTaskIntent = ContextualTaskIntent(goal),
        includeMemory: Boolean = true,
        includeRecentMessages: Boolean = true,
    ): String = chatContextComposer.buildPriorContext(goal, taskType, intent, includeMemory, includeRecentMessages)

    private fun persistWorkspaceObservation(
        sessionId: String,
        skillId: String?,
        rawOutput: String,
    ) {
        workspaceRecorder.persistObservation(sessionId, skillId, rawOutput)
    }

    private fun persistRuntimeWorkspaceUpdate(
        sessionId: String,
        goal: String,
        update: AgentWorkspaceUpdate,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { workspaceRecorder.persistRuntimeUpdate(sessionId, goal, update) }
        }
    }

    private fun buildStructuredDirectChatMessages(
        sessionId: String,
        systemPrompt: String,
        currentGoal: String,
        imageBase64: String? = null,
    ): List<Message> = chatContextComposer.buildStructuredDirectChatMessages(
        sessionMessages = _uiState.value.sessionStates[sessionId]?.messages.orEmpty(),
        systemPrompt = systemPrompt,
        currentGoal = currentGoal,
        imageBase64 = imageBase64,
    )

    private fun buildUserMemoryContextForPrompt(goal: String, taskType: TaskType, activeWorkspaceId: String? = null): String =
        runCatching {
            val state = _uiState.value
            val semanticFacts = state.profileState.semanticFacts
            if (semanticFacts.isNotEmpty()) {
                memoryContextBuilder.buildFromSnapshots(
                    userMessage = goal,
                    taskType = taskType,
                    userConfigEntries = state.userConfigEntries,
                    facts = semanticFacts,
                    activeSessionScopeId = activeWorkspaceId,
                ).toPrompt()
            } else {
                memoryContextBuilder.buildFromSnapshots(
                    userMessage = goal,
                    taskType = taskType,
                    userConfigEntries = state.userConfigEntries,
                    facts = state.profileState.facts,
                    activeSessionScopeId = activeWorkspaceId,
                ).toPrompt()
            }
        }.getOrDefault("")

    private companion object {
        const val CONFIRM_TASK_PREFIX = "确认执行:"
        const val CONFIRM_ACCESSIBILITY_TASK_PREFIX = "确认无障碍并执行:"
        const val OPEN_ACCESSIBILITY_PREFIX = "打开无障碍设置:"
        const val CONFIRM_ROLE_PREFIX = "确认切换角色:"
        const val CANCEL_CONFIRMATION_TEXT = "取消"
        const val ACTIVE_WORKFLOW_TTL_MS = 30 * 60 * 1000L
        const val ACTIVE_WORKFLOW_GOAL_LIMIT = 3000
    }

    private fun registerBuiltinSkills() {
        listOf(
            // Screen perception
            ScreenshotSkill(),
            ReadScreenSkill(),
            SeeScreenSkill(),
            // Interaction
            TapSkill(),
            LongClickSkill(),
            ScrollSkill(),
            InputTextSkill(),
            NavigateSkill(app.virtualDisplayManager),
            ListAppsSkill(),
            PhoneStatusSkill(),
            // Web
            WebSearchSkill(app.webViewManager),
            FetchUrlSkill(),
            WebBrowseSkill(app.webViewManager),
            WebContentSkill(app.webViewManager),
            WebJsSkill(app.webViewManager),
            // Content creation
            GenerateImageSkill(config, app.userConfig),
            GenerateIconSkill(app, config, app.userConfig, app.miniAppStore, roleManager),
            GenerateDocumentSkill(app),
            GenerateVideoSkill(config, app, app.userConfig, videoTaskManager),
            CreateFileSkill(app),
            ReadFileSkill(app),
            ListFilesSkill(app),
            CreateHtmlSkill(app),
            ChineseBqbStickerSkill(app),
            // Virtual display (background execution)
            BgLaunchSkill(app.virtualDisplayManager),
            BgReadScreenSkill(app.virtualDisplayManager),
            BgScreenshotSkill(app.virtualDisplayManager),
            BgStopSkill(app.virtualDisplayManager),
            VirtualDisplaySetupSkill(app.virtualDisplayManager),
            // System
            ShellSkill(),
            PipInstallSkill(),
            RunPythonSkill(),
            CodexDesktopSkill(userConfig),
            PgyerReleaseSkill(app, userConfig),
            ClipboardSkill(),
            ShowToastSkill(),
            DeviceInfoSkill(),
            MemorySkill(app.semanticMemory),
            com.mobileclaw.skill.builtin.UserProfileSkill(app.semanticMemory, userConfig),
            PermissionSkill(app.permissionManager),
            // Skill management
            MetaSkill(loader),
            SkillCheckSkill(registry),
            QuickSkillSkill(llm, loader),
            SkillMarketSkill(loader),
            McpClientSkill(),
            // Dynamic config, model & role
            SwitchModelSkill(config),
            UserConfigSkill(userConfig, app.semanticMemory),
            switchRoleSkill,
            RoleManagerSkill(roleManager, roleRequests),
            HouseArtistSkill(townStore, roleManager),
            AiHomeAssetSkill(townStore, roleManager),
            TownBuilderSkill(townStore, roleManager),
            // Session management
            SessionManagerSkill(database.sessionDao(), sessionRequests, mutationGuard = ::guardSessionMutation),
            // App navigation
            PageControlSkill(pageRequests),
            // Skill notes
            SkillNotesSkill(app.skillNotesStore),
            // Mini-app manager
            appManagerSkill,
            // AI native page builder
            uiBuilderSkill,
            // Task replay, recipes, and quick-entry pages
            TaskRecipeSkill(app.taskReplayStore, app.taskRecipeStore, app.aiPageStore, aiPageOpenRequests, app.pendingAgentTask),
            // User external storage management
            com.mobileclaw.skill.builtin.UserStorageSkill(app.userStorageManager),
            // Internal workspace management
            WorkspaceManagerSkill(app.workspaceStore),
            // LAN console page editor (千人千面)
            com.mobileclaw.skill.builtin.ConsoleEditorSkill(consoleServer),
        ).forEach { registry.register(it) }
    }

    fun loadVideoTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = runCatching { videoTaskManager.recent() }.getOrDefault(emptyList())
            _uiState.update { it.copy(videoTasks = tasks) }
            scheduleVideoTaskAutoRefresh(tasks)
        }
    }

    fun refreshVideoTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(videoTaskRefreshingIds = it.videoTaskRefreshingIds + taskId) }
            runCatching { videoTaskManager.refresh(taskId) }
            val tasks = runCatching { videoTaskManager.recent() }.getOrDefault(_uiState.value.videoTasks)
            _uiState.update {
                it.copy(
                    videoTasks = tasks,
                    videoTaskRefreshingIds = it.videoTaskRefreshingIds - taskId,
                )
            }
            scheduleVideoTaskAutoRefresh(tasks)
        }
    }

    fun refreshPendingVideoTasks() {
        refreshPendingVideoTasksInternal(showSpinner = true)
    }

    fun generateImage(request: ImageGenerationRequest) {
        if (_uiState.value.imageGenerationRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(imageGenerationRunning = true) }
            try {
                val result = GenerateImageSkill(config, app.userConfig)
                    .execute(request.toSkillParams())
                if (result.success && !result.imageBase64.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            imageGenerationPreviewBase64 = result.imageBase64,
                            imageGenerationPreviewPrompt = request.prompt,
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        if (result.success) result.output.ifBlank { "图片已生成" } else result.output.ifBlank { "图片生成失败" },
                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "图片生成失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(imageGenerationRunning = false) }
            }
        }
    }

    fun rewriteImagePrompt(prompt: String, action: ImagePromptAiAction, onResult: (String) -> Unit) {
        if (_uiState.value.imagePromptAiRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(imagePromptAiRunning = true) }
            try {
                val rewritten = rewriteImagePromptInternal(prompt, action)
                withContext(Dispatchers.Main) { onResult(rewritten) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "LLM 处理失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(imagePromptAiRunning = false) }
            }
        }
    }

    private fun rewriteImagePromptInternal(prompt: String, action: ImagePromptAiAction): String {
        val raw = prompt.trim()
        if (raw.isBlank()) return raw
        val systemPrompt = when (action) {
            ImagePromptAiAction.ENRICH -> """
                You enrich short user ideas into a strong image generation prompt.
                Return only one concise English prompt.
                Preserve the user's intent and add helpful subject details, composition, lighting, material, style, and visual constraints.
                Do not add explanations, markdown, JSON, quotes, or safety commentary.
            """.trimIndent()
            ImagePromptAiAction.TRANSLATE -> """
                You translate prompts for image generation models.
                Return only one concise English prompt.
                Preserve the user's exact intent, subject, style, composition, and constraints.
                Do not add new creative details.
                Do not add explanations, markdown, JSON, quotes, or safety commentary.
            """.trimIndent()
        }
        return callVideoPromptLlm(systemPrompt = systemPrompt, userPrompt = raw)
            .trim()
            .trim('"')
            .takeIf { it.isNotBlank() } ?: raw
    }

    fun generateVideo(request: VideoGenerationRequest) {
        if (_uiState.value.videoGenerationRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(videoGenerationRunning = true) }
            try {
                val result = GenerateVideoSkill(config, app, app.userConfig, videoTaskManager)
                    .execute(request.toSkillParams())
                loadVideoTasks()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        if (result.success) result.output.ifBlank { "视频任务已提交" } else result.output.ifBlank { "视频生成失败" },
                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "视频生成失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(videoGenerationRunning = false) }
            }
        }
    }

    fun rewriteVideoPrompt(prompt: String, action: VideoPromptAiAction, onResult: (String) -> Unit) {
        if (_uiState.value.videoPromptAiRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(videoPromptAiRunning = true) }
            try {
                val rewritten = rewriteVideoPromptInternal(prompt, action)
                withContext(Dispatchers.Main) {
                    onResult(rewritten)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "LLM 处理失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(videoPromptAiRunning = false) }
            }
        }
    }

    private suspend fun rewriteVideoPromptInternal(prompt: String, action: VideoPromptAiAction): String {
        val raw = prompt.trim()
        if (raw.isBlank()) return raw
        val systemPrompt = when (action) {
            VideoPromptAiAction.ENRICH -> """
                You enrich short user ideas into a strong video generation prompt.
                Return only one concise English prompt.
                Preserve the user's intent and add helpful visual details, subject action, camera movement, lighting, atmosphere, and style.
                Do not add explanations, markdown, JSON, quotes, or safety commentary.
            """.trimIndent()
            VideoPromptAiAction.TRANSLATE -> """
                You translate prompts for video generation models.
                Return only one concise English prompt.
                Preserve the user's exact intent, subject, motion, style, camera, and constraints.
                Do not add new creative details.
                Do not add explanations, markdown, JSON, quotes, or safety commentary.
            """.trimIndent()
        }
        return callVideoPromptLlm(systemPrompt = systemPrompt, userPrompt = raw)
            .trim()
            .trim('"')
            .takeIf { it.isNotBlank() } ?: raw
    }

    private fun callVideoPromptLlm(systemPrompt: String, userPrompt: String): String {
        val snapshot = config.snapshot()
        val gateway = selectVideoPromptChatGateway(snapshot)
            ?: throw IllegalStateException("没有可用于 AI 丰富/翻译的 chat 网关，请配置一个带 chat 能力的网关。")
        val endpoint = gateway.capabilityEndpoint("chat").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("chat 网关 endpoint 为空。")
        val apiKey = gateway.capabilityApiKey("chat").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("chat 网关 apiKey 为空。")
        val model = gateway.capabilityModel("chat") ?: gateway.model.takeIf { it.isNotBlank() } ?: "gpt-4o"
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", false)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userPrompt)
                })
            })
        }
        val req = Request.Builder()
            .url("${normalizeOpenAiBase(endpoint)}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return videoPromptLlmClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("chat 网关调用失败 HTTP ${resp.code}: ${extractLlmError(raw)}")
            }
            val json = JsonParser.parseString(raw).asJsonObject
            json["choices"]?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.get("message")?.asJsonObject?.get("content")?.asString
                ?: throw IllegalStateException("chat 网关没有返回内容。")
        }
    }

    private fun selectVideoPromptChatGateway(snapshot: ConfigSnapshot): GatewayConfig? {
        val active = snapshot.activeGateway
        return active?.takeIf { it.isUsableExplicitChatGateway() }
            ?: snapshot.gateways.firstOrNull { it.isUsableExplicitChatGateway() }
            ?: active?.takeIf { it.isLegacyChatGatewayCandidate() }
            ?: snapshot.gateways.firstOrNull { it.isLegacyChatGatewayCandidate() }
    }

    private fun GatewayConfig.isUsableExplicitChatGateway(): Boolean =
        hasCapability("chat") && capabilityEndpoint("chat").isNotBlank() && capabilityApiKey("chat").isNotBlank()

    private fun GatewayConfig.isLegacyChatGatewayCandidate(): Boolean {
        val explicitCapabilities = runCatching { capabilities }.getOrNull().orEmpty()
        return explicitCapabilities.isEmpty() && endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    private fun normalizeOpenAiBase(endpoint: String): String {
        val trimmed = endpoint.trim()
            .removeSuffix("/")
            .removeSuffix("/chat/completions")
            .trimEnd('/')
        if (trimmed.isBlank()) return trimmed
        val hasVersionSuffix = Regex("/v\\d+$", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        return if (hasVersionSuffix) trimmed else "$trimmed/v1"
    }

    private fun extractLlmError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return runCatching {
            val obj = JsonParser.parseString(trimmed).asJsonObject
            val error = obj["error"]
            when {
                error == null || error.isJsonNull -> trimmed.take(240)
                error.isJsonObject -> error.asJsonObject["message"]?.asString ?: error.toString().take(240)
                error.isJsonPrimitive -> error.asString
                else -> error.toString().take(240)
            }
        }.getOrDefault(trimmed.take(240))
    }

    fun uploadVideoFrameImage(imageUri: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { videoImageUploader.uploadIfNeeded(imageUri) }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    private fun refreshPendingVideoTasksInternal(showSpinner: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (showSpinner) {
                _uiState.update { it.copy(videoTasksRefreshing = true) }
            }
            runCatching { videoTaskManager.refreshPendingTasks() }
            val tasks = runCatching { videoTaskManager.recent() }.getOrDefault(_uiState.value.videoTasks)
            _uiState.update {
                it.copy(
                    videoTasks = tasks,
                    videoTasksRefreshing = if (showSpinner) false else it.videoTasksRefreshing,
                    videoTaskRefreshingIds = emptySet(),
                )
            }
            scheduleVideoTaskAutoRefresh(tasks)
        }
    }

    fun deleteVideoTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { videoTaskManager.delete(taskId) }
            val tasks = runCatching { videoTaskManager.recent() }.getOrDefault(emptyList())
            _uiState.update { it.copy(videoTasks = tasks, videoTaskRefreshingIds = it.videoTaskRefreshingIds - taskId) }
            scheduleVideoTaskAutoRefresh(tasks)
        }
    }

    private fun scheduleVideoTaskAutoRefresh(tasks: List<com.mobileclaw.memory.db.VideoGenerationTaskEntity>) {
        val hasPending = tasks.any { task ->
            task.status == VideoTaskStatuses.SUBMITTED ||
                task.status == VideoTaskStatuses.RUNNING ||
                task.status == VideoTaskStatuses.TIMED_OUT
        }
        if (!hasPending) {
            videoTaskAutoRefreshJob?.cancel()
            videoTaskAutoRefreshJob = null
            return
        }
        if (videoTaskAutoRefreshJob?.isActive == true) return
        videoTaskAutoRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(VIDEO_TASK_AUTO_REFRESH_INTERVAL_MS)
                refreshPendingVideoTasksInternal(showSpinner = false)
                val latestTasks = _uiState.value.videoTasks
                val stillPending = latestTasks.any { task ->
                    task.status == VideoTaskStatuses.SUBMITTED ||
                        task.status == VideoTaskStatuses.RUNNING ||
                        task.status == VideoTaskStatuses.TIMED_OUT
                }
                if (!stillPending) {
                    videoTaskAutoRefreshJob = null
                    break
                }
            }
        }
    }

    private fun loadDynamicSkills() {
        runCatching { loader.loadAll() }
    }

    // ── Session Serialization ────────────────────────────────────────────────

    private val gson = Gson()

    // ── VPN ──────────────────────────────────────────────────────────────────────

    private val vpnManager = com.mobileclaw.vpn.VpnManager(app)
    private var vpnInitialized = false

    fun initVpn() {
        if (vpnInitialized) return
        vpnInitialized = true
        if (!registry.contains("vpn_control")) {
            registry.register(com.mobileclaw.vpn.VpnControlSkill(vpnManager) {
                _uiState.value.vpnSubscriptions.firstNotNullOfOrNull { sub ->
                    val proxy = sub.proxies.firstOrNull { it.id == sub.entity.selectedProxyId }
                    if (proxy != null) sub to proxy else null
                }
            })
            refreshPromotableSkills()
        }
        viewModelScope.launch {
            vpnManager.subscriptions.collect { subs ->
                _uiState.update { it.copy(vpnSubscriptions = subs) }
            }
        }
        viewModelScope.launch {
            vpnManager.status.collect { status ->
                _uiState.update { it.copy(vpnStatus = status) }
            }
        }
    }

    fun syncVpnStatus() = vpnManager.syncStatus()

    fun vpnPrepareIntent() = vpnManager.prepareIntent()

    fun startVpn(sub: com.mobileclaw.vpn.VpnSubscription, proxy: com.mobileclaw.vpn.ProxyConfig) {
        vpnManager.startVpn(sub, proxy)
    }

    fun stopVpn() = vpnManager.stopVpn()

    fun addVpnSubscription(name: String, url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(vpnAddingSubscription = true) }
            val result = vpnManager.addSubscription(name, url)
            _uiState.update { it.copy(vpnAddingSubscription = false) }
            if (result.isFailure) {
                android.widget.Toast.makeText(app,
                    "Failed: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateVpnSubscription(sub: com.mobileclaw.vpn.VpnSubscription) {
        viewModelScope.launch {
            val result = vpnManager.updateSubscription(sub)
            if (result.isFailure) {
                android.widget.Toast.makeText(app,
                    "Update failed: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteVpnSubscription(id: String) {
        viewModelScope.launch { vpnManager.deleteSubscription(id) }
    }

    fun selectVpnProxy(subId: String, proxyId: String?) {
        viewModelScope.launch { vpnManager.selectProxy(subId, proxyId) }
    }

    fun testAllVpnLatencies(sub: com.mobileclaw.vpn.VpnSubscription) {
        viewModelScope.launch {
            // Mark all proxies as "testing"
            val testing = sub.proxies.associate { it.id to LATENCY_TESTING }
            _uiState.update { it.copy(vpnLatencies = it.vpnLatencies + testing) }
            vpnManager.testAllLatencies(sub) { proxyId, ms ->
                _uiState.update { it.copy(vpnLatencies = it.vpnLatencies + (proxyId to ms)) }
            }
        }
    }

    fun testVpnProxyReachable(onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(vpnManager.testProxyReachable()) }
    }

    private fun deserializeAttachments(raw: String): List<SkillAttachment> {
        return runCatching {
            val arr = JsonParser.parseString(raw.ifBlank { "[]" }).asJsonArray
            arr.mapNotNull { el ->
                val o = el.asJsonObject
                when (o["type"]?.asString) {
                    "image" -> SkillAttachment.ImageData(
                        base64 = o["base64"]?.asString ?: "",
                        prompt = o["prompt"]?.asString?.ifBlank { null },
                        localPath = o["localPath"]?.asString ?: "",
                    )
                    "file" -> SkillAttachment.FileData(
                        path = o["path"]?.asString ?: "",
                        name = o["name"]?.asString ?: "",
                        mimeType = o["mimeType"]?.asString ?: "",
                        sizeBytes = o["sizeBytes"]?.asString?.toLongOrNull() ?: 0L,
                    )
                    "html" -> SkillAttachment.HtmlData(
                        path = o["path"]?.asString ?: "",
                        title = o["title"]?.asString ?: "",
                        htmlContent = o["htmlContent"]?.asString ?: "",
                    )
                    "webpage" -> SkillAttachment.WebPage(
                        url = o["url"]?.asString ?: "",
                        title = o["title"]?.asString ?: "",
                        excerpt = o["excerpt"]?.asString ?: "",
                    )
                    "search_results" -> {
                        val pages = o["pages"]?.asJsonArray?.mapNotNull { pe ->
                            val p = pe.asJsonObject
                            SkillAttachment.WebPage(
                                url = p["url"]?.asString ?: return@mapNotNull null,
                                title = p["title"]?.asString ?: "",
                                excerpt = p["excerpt"]?.asString ?: "",
                            )
                        } ?: emptyList()
                        SkillAttachment.SearchResults(o["query"]?.asString ?: "", o["engine"]?.asString ?: "", pages)
                    }
                    "file_list" -> {
                        val files = o["files"]?.asJsonArray?.mapNotNull { fe ->
                            val f = fe.asJsonObject
                            SkillAttachment.FileList.FileEntry(
                                path = f["path"]?.asString ?: return@mapNotNull null,
                                name = f["name"]?.asString ?: "",
                                mimeType = f["mimeType"]?.asString ?: "text/plain",
                                sizeBytes = f["sizeBytes"]?.asString?.toLongOrNull() ?: 0L,
                            )
                        } ?: emptyList()
                        SkillAttachment.FileList(files, o["directory"]?.asString ?: "")
                    }
                    "action_card" -> {
                        val actions = o["actions"]?.asJsonArray?.mapNotNull { ae ->
                            val a = ae.asJsonObject
                            SkillAttachment.ActionCard.Action(
                                label = a["label"]?.asString ?: return@mapNotNull null,
                                message = a["message"]?.asString ?: return@mapNotNull null,
                                style = a["style"]?.asString ?: "secondary",
                            )
                        }.orEmpty()
                        SkillAttachment.ActionCard(
                            title = o["title"]?.asString ?: "",
                            body = o["body"]?.asString ?: "",
                            actions = actions,
                            tone = o["tone"]?.asString ?: "default",
                        )
                    }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeAttachments(attachments: List<SkillAttachment>): String {
        val list = attachments.map { att ->
            when (att) {
                is SkillAttachment.ImageData -> serializeImageAttachment(att)
                is SkillAttachment.FileData  -> mapOf("type" to "file", "path" to att.path, "name" to att.name, "mimeType" to att.mimeType, "sizeBytes" to att.sizeBytes.toString())
                is SkillAttachment.HtmlData  -> mapOf("type" to "html", "path" to att.path, "title" to att.title)
                is SkillAttachment.WebPage   -> mapOf("type" to "webpage", "url" to att.url, "title" to att.title, "excerpt" to att.excerpt)
                is SkillAttachment.SearchResults -> mapOf(
                    "type" to "search_results",
                    "query" to att.query,
                    "engine" to att.engine,
                    "pages" to att.pages.map { p -> mapOf("url" to p.url, "title" to p.title, "excerpt" to p.excerpt) },
                )
                is SkillAttachment.AccessibilityRequest -> null
                is SkillAttachment.ActionCard -> mapOf(
                    "type" to "action_card",
                    "title" to att.title,
                    "body" to att.body,
                    "tone" to att.tone,
                    "actions" to att.actions.map { action ->
                        mapOf("label" to action.label, "message" to action.message, "style" to action.style)
                    },
                )
                is SkillAttachment.FileList -> mapOf(
                    "type" to "file_list",
                    "directory" to att.directory,
                    "files" to att.files.map { f -> mapOf("path" to f.path, "name" to f.name, "mimeType" to f.mimeType, "sizeBytes" to f.sizeBytes.toString()) },
                )
            }
        }.filterNotNull()
        return gson.toJson(list)
    }

    private fun serializeImageAttachment(att: SkillAttachment.ImageData): Map<String, String> {
        if (att.localPath.isNotBlank()) {
            return mapOf(
                "type" to "image",
                "base64" to att.base64,
                "prompt" to (att.prompt ?: ""),
                "localPath" to att.localPath,
            )
        }
        if (att.base64.length <= 500_000) {
            return mapOf("type" to "image", "base64" to att.base64, "prompt" to (att.prompt ?: ""))
        }
        val file = persistImageDataUri(att.base64)
        return if (file != null) {
            mapOf(
                "type" to "file",
                "path" to file.absolutePath,
                "name" to file.name,
                "mimeType" to mimeTypeFromDataUri(att.base64),
                "sizeBytes" to file.length().toString(),
            )
        } else {
            mapOf("type" to "image", "base64" to "", "prompt" to (att.prompt ?: ""))
        }
    }

    private fun persistImageDataUri(dataUri: String): File? = runCatching {
        val raw = dataUri.substringAfter("base64,", missingDelimiterValue = dataUri.substringAfter(",", dataUri))
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        val mime = mimeTypeFromDataUri(dataUri)
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val dir = File(app.filesDir, "chat_images").also { it.mkdirs() }
        File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext").also { it.writeBytes(bytes) }
    }.getOrNull()

    private fun persistUserImageForWorkspace(sessionId: String, dataUri: String): String? = runCatching {
        val raw = dataUri.substringAfter("base64,", missingDelimiterValue = dataUri.substringAfter(",", dataUri))
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        val mime = mimeTypeFromDataUri(dataUri)
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val workspaceId = workspaceRuntime.resolveSessionWorkspaceId(sessionId)
        val path = if (workspaceId != null) {
            app.workspaceStore.writeBytes(
                id = workspaceId,
                relativeDir = "inputs",
                name = "user_image_${System.currentTimeMillis()}.$ext",
                bytes = bytes,
            )
        } else {
            val dir = File(app.filesDir, "workspace_image_inputs").also { it.mkdirs() }
            File(dir, "user_image_${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext").also { it.writeBytes(bytes) }.absolutePath
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { userConfig.set("latest_image_local_path", path, "Most recent user-attached image local path for image-to-video and media tools") }
        }
        path
    }.getOrNull()

    private fun mimeTypeFromDataUri(dataUri: String): String =
        dataUri.substringAfter("data:", "image/jpeg").substringBefore(";").ifBlank { "image/jpeg" }
}

private fun SessionMessageEntity.toChatMessage(): ChatMessage {
    val gson = Gson()
    val logLines = runCatching {
        val arr = JsonParser.parseString(logLinesJson).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            LogLine(
                entryId = o["entryId"]?.asString ?: "",
                type = runCatching { LogType.valueOf(o["type"]?.asString ?: "INFO") }.getOrDefault(LogType.INFO),
                text = o["text"]?.asString ?: "",
                skillId = o["skillId"]?.asString,
                imageBase64 = null, // stripped on save
                details = runCatching { o["details"]?.asJsonArray?.map { it.asString } ?: emptyList() }.getOrDefault(emptyList()),
                startedAt = o["startedAt"]?.asLong ?: 0L,
                finishedAt = o["finishedAt"]?.asLong ?: 0L,
                isRunning = o["isRunning"]?.asBoolean ?: false,
            ).let { line -> if (line.entryId.isBlank()) line.copy(entryId = java.util.UUID.randomUUID().toString()) else line }
        }
    }.getOrDefault(emptyList())

    val attachments = runCatching {
        val arr = JsonParser.parseString(attachmentsJson).asJsonArray
        arr.mapNotNull { el ->
            val o = el.asJsonObject
            when (o["type"]?.asString) {
                "image" -> SkillAttachment.ImageData(
                    base64 = o["base64"]?.asString ?: "",
                    prompt = o["prompt"]?.asString?.ifBlank { null },
                    localPath = o["localPath"]?.asString ?: "",
                )
                "file" -> SkillAttachment.FileData(
                    path = o["path"]?.asString ?: "",
                    name = o["name"]?.asString ?: "",
                    mimeType = o["mimeType"]?.asString ?: "",
                    sizeBytes = o["sizeBytes"]?.asString?.toLongOrNull() ?: 0L,
                )
                "html" -> SkillAttachment.HtmlData(
                    path = o["path"]?.asString ?: "",
                    title = o["title"]?.asString ?: "",
                    htmlContent = o["htmlContent"]?.asString ?: "",
                )
                "webpage" -> SkillAttachment.WebPage(
                    url = o["url"]?.asString ?: "",
                    title = o["title"]?.asString ?: "",
                    excerpt = o["excerpt"]?.asString ?: "",
                )
                "search_results" -> {
                    val pages = runCatching {
                        o["pages"]?.asJsonArray?.mapNotNull { pe ->
                            val p = pe.asJsonObject
                            SkillAttachment.WebPage(
                                url = p["url"]?.asString ?: return@mapNotNull null,
                                title = p["title"]?.asString ?: "",
                                excerpt = p["excerpt"]?.asString ?: "",
                            )
                        } ?: emptyList()
                    }.getOrDefault(emptyList())
                    SkillAttachment.SearchResults(
                        query = o["query"]?.asString ?: "",
                        engine = o["engine"]?.asString ?: "",
                        pages = pages,
                    )
                }
                "file_list" -> {
                    val files = runCatching {
                        o["files"]?.asJsonArray?.mapNotNull { fe ->
                            val f = fe.asJsonObject
                            SkillAttachment.FileList.FileEntry(
                                path = f["path"]?.asString ?: return@mapNotNull null,
                                name = f["name"]?.asString ?: "",
                                mimeType = f["mimeType"]?.asString ?: "text/plain",
                                sizeBytes = f["sizeBytes"]?.asString?.toLongOrNull() ?: 0L,
                            )
                        } ?: emptyList()
                    }.getOrDefault(emptyList())
                    SkillAttachment.FileList(files, o["directory"]?.asString ?: "")
                }
                else -> null
            }
        }
    }.getOrDefault(emptyList())

    return ChatMessage(
        role = if (role == "user") MessageRole.USER else MessageRole.AGENT,
        text = text,
        logLines = logLines,
        imageBase64 = imageBase64,
        attachments = attachments,
        imageLocalPath = attachments.firstOrNull { it is SkillAttachment.ImageData && it.localPath.isNotBlank() }
            ?.let { (it as SkillAttachment.ImageData).localPath }
            ?: "",
        senderRoleId = senderRoleId,
        senderRoleName = senderRoleName,
        senderRoleAvatar = senderRoleAvatar,
    )
}

private fun AgentEvent.toLogLine(): LogLine? = when (this) {
    is AgentEvent.Started      -> LogLine(type = LogType.INFO, text = "▶ $goal")
    is AgentEvent.Thinking     -> null
    is AgentEvent.ThinkingToken -> null
    is AgentEvent.SkillCalling -> LogLine(type = LogType.ACTION, text = friendlySkillDescription(skillId, params), skillId = skillId)
    is AgentEvent.Observation  -> LogLine(type = LogType.OBSERVATION, text = text.take(400), imageBase64 = imageBase64)
    is AgentEvent.Completed    -> LogLine(type = LogType.SUCCESS, text = summary)
    // Warning 映射为 INFO，让 guard/约束在 UI 里表现为提醒而不是失败。
    is AgentEvent.Warning      -> LogLine(type = LogType.INFO, text = friendlyRuntimeNotice(message))
    is AgentEvent.Error        -> LogLine(type = LogType.ERROR, text = friendlyRuntimeNotice(message))
    is AgentEvent.Token        -> null
    is AgentEvent.ThinkingComplete -> null
    is AgentEvent.PlanCreated -> LogLine(type = LogType.THINKING, text = plan.toPrompt())
}

private fun isEnglishUiText(): Boolean =
    ClawApplication.instance.agentConfig.language == "en"

private fun uiText(zh: String, en: String): String =
    if (isEnglishUiText()) en else zh

private fun uiDetailPrefix(zh: String, en: String, englishOverride: Boolean = isEnglishUiText()): String =
    if (englishOverride) "$en: " else "$zh："

private fun uiDetailLine(zh: String, en: String, value: String): String =
    uiDetailPrefix(zh, en) + value

private fun LogLine.withLifecycle(
    running: Boolean,
    now: Long = System.currentTimeMillis(),
): LogLine = copy(
    startedAt = when {
        startedAt > 0L -> startedAt
        else -> now
    },
    finishedAt = when {
        running -> 0L
        finishedAt > 0L -> finishedAt
        else -> now
    },
    isRunning = running,
)

private fun List<LogLine>.finishLatestRunningLine(now: Long = System.currentTimeMillis()): List<LogLine> {
    val index = indexOfLast { it.isRunning }
    if (index < 0) return this
    return toMutableList().also { lines ->
        lines[index] = lines[index].copy(
            isRunning = false,
            startedAt = if (lines[index].startedAt > 0L) lines[index].startedAt else now,
            finishedAt = now,
        )
    }
}

// 运行时内部报文统一翻译成人话，避免聊天步骤看起来像控制台日志。
private fun friendlyRuntimeNotice(message: String): String {
    val normalized = message.trim()
    return when {
        normalized.startsWith("Guard blocked ") ->
            uiText("这一步当前不适合直接执行，正在换一种更合适的处理方式", "This step is not suitable to run directly, so switching to a better approach")
        normalized.startsWith("LLM error:") ->
            friendlyLlmFailureMessage(normalized.removePrefix("LLM error:").trim())
        normalized.contains("skill '", ignoreCase = true) && normalized.contains("not found", ignoreCase = true) ->
            uiText("这一步工具调用没有成功，正在改走别的处理方式", "This tool call did not succeed, so switching to another approach")
        normalized.startsWith("Error executing ") ->
            uiText("这一步执行时出了问题，正在根据返回结果继续修正", "This step hit an execution issue; continuing to fix it from the result")
        else -> normalized
    }
}

private fun isNonRetryableLlmFailure(message: String): Boolean {
    val normalized = message.lowercase()
    return normalized.contains("authentication error") ||
        normalized.contains("api error 401") ||
        normalized.contains("not connected to the query engine") ||
        normalized.contains("must call connect() before attempting to query data")
}

private fun friendlyLlmFailureMessage(raw: String): String {
    val normalized = raw.trim()
    val lowered = normalized.lowercase()
    return when {
        lowered.contains("not connected to the query engine") ||
            lowered.contains("must call connect() before attempting to query data") ->
            uiText(
                "当前聊天网关没有接到可直接对话的查询引擎，chat 能力配置错了，不能继续拿它做回复总结",
                "The current chat gateway is not connected to a query engine, so it cannot summarize the reply.",
            )
        lowered.contains("authentication error") || lowered.contains("api error 401") ->
            uiText(
                "当前聊天网关鉴权失败，或者你填的接口并不是可直接对话的 chat 入口",
                "The current chat gateway failed authentication, or the endpoint is not a usable chat endpoint.",
            )
        else -> uiText("模型这一步返回异常，当前请求没有完成", "The model response failed, so this request was not completed.")
    }
}

// 从长结果里抽一条用户真正看得懂的判断，避免展开前只看到“读了很多结果”。
private fun summarizeTechnicalResultForUser(skillId: String?, rawText: String): String? {
    val normalized = rawText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotBlank() &&
                !line.startsWith("{") &&
                !line.startsWith("[") &&
                !line.startsWith("data:") &&
                !line.startsWith("http://") &&
                !line.startsWith("https://")
        }
        .orEmpty()
    if (normalized.isBlank()) return null
    return when (skillId) {
        "web_search", "fetch_url", "web_browse", "web_content", "web_js" ->
            uiText("已经拿到候选内容，下一步会筛掉无关信息，只保留结论", "Candidate content is available; next, irrelevant information will be filtered out.")
        "see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen" ->
            uiText("已经看清当前界面，下一步会直接判断该点哪里", "The current screen is visible; next, the agent will decide where to act.")
        "tap", "long_click", "scroll", "input_text", "navigate" ->
            uiText("已经拿到操作后的界面反馈，下一步会确认目标是否达成", "Screen feedback is available; next, the agent will confirm whether the goal was reached.")
        "app_manager", "ui_builder" -> null
        else -> normalized.take(120)
    }
}

private const val PROFILE_MEMORY_PAGE_SIZE = 40

private fun supportsCurrentMultimodal(snapshot: ConfigSnapshot): Boolean {
    if (!snapshot.localModelEnabled && !snapshot.localNativeOnly) return snapshot.activeGateway?.supportsCapabilityMultimodal() ?: snapshot.supportsMultimodal
    val manager = ClawApplication.instance.localModelManager
    val model = manager.modelInfo(snapshot.localModelId) ?: return false
    return model.supportsVision && manager.visionModelPathFor(snapshot.localModelId) != null
}

private fun String.cleanLocalTurnTokens(): String = cleanLocalGeneratedText()

private fun String.cleanLocalStreamingText(): String {
    if (isEmpty()) return ""
    var text = decodeLocalTokenizerSpacing().replace("\r\n", "\n").replace('\r', '\n')
    listOf(
        Regex("""(?i)<\|?/?(?:start_of_turn|end_of_turn|turn|im_start|im_end|eot_id|eos|bos|endoftext|begin_of_text|start_header_id|end_header_id)\|?>"""),
        Regex("""(?i)<\|?/?(?:eot|eom|eod|end)\|?>"""),
    ).forEach { regex -> text = regex.replace(text, "") }
    return text.replace(Regex("""(?i)^\s*(?:assistant|model|ai|bot)\s*[:：]\s*"""), "")
}

private fun String.cleanLocalStreamDelta(): String {
    if (isEmpty()) return ""
    var text = decodeLocalTokenizerSpacing().replace("\r\n", "\n").replace('\r', '\n')
    listOf(
        Regex("""(?i)<\|?/?(?:start_of_turn|end_of_turn|turn|im_start|im_end|eot_id|eos|bos|endoftext|begin_of_text|start_header_id|end_header_id)\|?>"""),
        Regex("""(?i)<\|?/?(?:eot|eom|eod|end)\|?>"""),
    ).forEach { regex -> text = regex.replace(text, "") }
    text = text.replace(Regex("""(?i)^\s*(?:assistant|model|ai|bot)\s*[:：]\s*"""), "")
    return text
}

private fun String.cleanCodexDesktopOutput(prompt: String): String {
    val promptLines = prompt
        .trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val envKeyPattern = Regex(
        """^(workdir|model|provider|approval|sandbox|reasoning effort|reasoning summaries|session id):\s""",
        RegexOption.IGNORE_CASE,
    )
    val timestampWarnPattern = Regex("""^\d{4}-\d{2}-\d{2}T.*\bWARN\b""")
    val cleaned = mutableListOf<String>()
    var skippingHeader = false
    var sawRoleMarker = false
    replace("\r\n", "\n").lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            if (cleaned.isNotEmpty()) cleaned += raw
            return@forEach
        }
        when {
            timestampWarnPattern.containsMatchIn(trimmed) -> return@forEach
            trimmed.startsWith("OpenAI Codex", ignoreCase = true) -> {
                skippingHeader = true
                return@forEach
            }
            skippingHeader && (trimmed == "--------" || envKeyPattern.containsMatchIn(trimmed)) -> return@forEach
            skippingHeader -> skippingHeader = false
        }
        when {
            trimmed == "user" -> {
                sawRoleMarker = true
                return@forEach
            }
            trimmed == "assistant" -> {
                sawRoleMarker = true
                cleaned.clear()
                return@forEach
            }
            sawRoleMarker && trimmed in promptLines -> return@forEach
            trimmed.startsWith("deprecated:", ignoreCase = true) -> return@forEach
            envKeyPattern.containsMatchIn(trimmed) -> return@forEach
            trimmed == "--------" -> return@forEach
            else -> cleaned += raw
        }
    }
    return cleaned.joinToString("\n").trim()
}

private fun String.isSupportedImageModel(): Boolean {
    val value = trim()
    return value == "pollinations" ||
        value == "pollinations-flux" ||
        value == "hf-flux-schnell" ||
        value.startsWith("huggingface:") ||
        value.startsWith("gpt-image-") ||
        value.startsWith("dall-e-") ||
        value.startsWith("black-forest-labs/FLUX.1")
}

private fun String.isConfiguredImageModel(): Boolean {
    val value = trim()
    return value.isNotBlank() && value.isSupportedImageModel() && value != "pollinations" && value != "pollinations-flux"
}

private fun JsonObject.intOrNull(name: String): Int? =
    runCatching {
        get(name)?.takeIf { !it.isJsonNull }?.asInt
    }.getOrNull()

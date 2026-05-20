package com.mobileclaw.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.agent.AgentEvent
import com.mobileclaw.agent.AgentRuntime
import com.mobileclaw.agent.ChatBubbleStyle
import com.mobileclaw.agent.ChatRouter
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleAvatarDefaults
import com.mobileclaw.agent.RoleScheduler
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskToolPolicy
import com.mobileclaw.agent.TaskType
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.Message
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.llm.cleanLocalGeneratedText
import com.mobileclaw.memory.EpisodicMemory
import com.mobileclaw.memory.MemoryContextBuilder
import com.mobileclaw.memory.MemoryWriter
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.memory.db.SessionMessageEntity
import com.mobileclaw.perception.ClawAccessibilityService
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.builtin.BgLaunchSkill
import com.mobileclaw.skill.builtin.BgReadScreenSkill
import com.mobileclaw.skill.builtin.BgScreenshotSkill
import com.mobileclaw.skill.builtin.BgStopSkill
import com.mobileclaw.skill.builtin.VirtualDisplaySetupSkill
import com.mobileclaw.skill.builtin.ClipboardSkill
import com.mobileclaw.skill.builtin.ChineseBqbStickerSkill
import com.mobileclaw.skill.builtin.ChineseBqbStickerRepository
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
import com.mobileclaw.skill.builtin.InputTextSkill
import com.mobileclaw.skill.builtin.ListAppsSkill
import com.mobileclaw.skill.builtin.LongClickSkill
import com.mobileclaw.skill.builtin.MemorySkill
import com.mobileclaw.skill.builtin.MetaSkill
import com.mobileclaw.skill.builtin.NavigateSkill
import com.mobileclaw.skill.builtin.PageControlSkill
import com.mobileclaw.skill.builtin.PermissionSkill
import com.mobileclaw.skill.builtin.PhoneStatusSkill
import com.mobileclaw.skill.builtin.RoleManagerSkill
import com.mobileclaw.skill.builtin.SessionManagerSkill
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
import com.mobileclaw.skill.builtin.SwitchModelSkill
import com.mobileclaw.skill.builtin.SwitchRoleSkill
import com.mobileclaw.skill.builtin.TapSkill
import com.mobileclaw.skill.builtin.TaskRecipeSkill
import com.mobileclaw.skill.builtin.UserConfigSkill
import com.mobileclaw.skill.builtin.WebBrowseSkill
import com.mobileclaw.skill.builtin.WebContentSkill
import com.mobileclaw.skill.builtin.WebJsSkill
import com.mobileclaw.skill.builtin.WebSearchSkill
import com.mobileclaw.server.PrivilegedClient
import com.mobileclaw.skill.builtin.PipInstallSkill
import com.mobileclaw.vpn.VpnManager
import com.mobileclaw.skill.builtin.RunPythonSkill
import com.mobileclaw.skill.executor.ShellSkill
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
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import com.mobileclaw.R
import com.mobileclaw.str

class MainViewModel : ViewModel() {

    private val app = ClawApplication.instance
    private val config = app.agentConfig
    private val registry = app.skillRegistry
    private val loader = SkillLoader(app, registry)
    private val overlay = app.overlayManager
    private val auroraOverlay = app.auroraOverlayManager
    private val episodicMemory = EpisodicMemory(app.database.episodeDao(), app.createLlmGateway())
    private val conversationMemory = app.conversationMemory
    private val profileExtractor = app.userProfileExtractor
    private val roleManager = app.roleManager
    private val userConfig = app.userConfig
    private val memoryContextBuilder = MemoryContextBuilder(app.semanticMemory, userConfig)
    private val memoryWriter = MemoryWriter(app.semanticMemory, userConfig)
    private val database = app.database
    private val llm get() = app.createLlmGateway()

    // Role switch requests emitted by SwitchRoleSkill / RoleManagerSkill, consumed in init
    private val roleRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val switchRoleSkill = SwitchRoleSkill(roleManager, roleRequests)
    private val recentGroupStickerPaths = ArrayDeque<String>()
    private var pendingAccessibilityTaskGoal: String? = null
    private var pendingRoleSwitchTaskGoal: String? = null

    // Mini-app open requests emitted by AppManagerSkill
    private val appOpenRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val appManagerSkill = AppManagerSkill(app.miniAppStore, appOpenRequests)

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
            currentPage = AppPage.CHAT,
            currentModel = config.model,
            currentRole = Role.DEFAULT,
            consoleServerUrl = app.consoleServer.getLanUrl(),
            localModels = app.localModelManager.models.value,
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    // Per-session task management (multiple sessions can run simultaneously)
    private val taskJobs = mutableMapOf<String, Job>()
    private val runtimes = mutableMapOf<String, AgentRuntime>()

    private fun updateSession(sessionId: String, transform: (SessionRunState) -> SessionRunState) {
        _uiState.update { state ->
            val current = state.sessionStates[sessionId] ?: SessionRunState()
            state.copy(sessionStates = state.sessionStates + (sessionId to transform(current)))
        }
    }

    init {
        registerBuiltinSkills()
        loadDynamicSkills()
        _uiState.update { it.copy(allSkills = registry.all().map { s -> s.meta }) }
        loadMiniApps()
        loadUserAvatar()

        viewModelScope.launch {
            config.configFlow.collect { snap ->
                _uiState.update { it.copy(
                    isConfigured = (snap.endpoint.isNotBlank() && snap.apiKey.isNotBlank()) ||
                        ((snap.localModelEnabled || snap.localNativeOnly) && app.localModelManager.modelPath(snap.localModelId) != null),
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
                    isConfigured = (snap.endpoint.isNotBlank() && snap.apiKey.isNotBlank()) ||
                        ((snap.localModelEnabled || snap.localNativeOnly) && app.localModelManager.modelPath(snap.localModelId) != null),
                    supportsMultimodal = supportsCurrentMultimodal(snap),
                ) }
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
                _uiState.update { it.copy(openAppId = appId) }
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
                    else          -> AppPage.CHAT
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
                _uiState.update { it.copy(availableRoles = roles) }
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

    private suspend fun createNewSessionInternal() {
        val id = UUID.randomUUID().toString()
        val roleId = _uiState.value.currentRole.id
        database.sessionDao().insert(SessionEntity(
            id = id,
            title = str(R.string.vm_new_),
            roleId = roleId,
        ))
        _uiState.update { it.copy(currentSessionId = id) }
        loadSessions()
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(currentSessionId = sessionId) }
            // Only load DB messages if the session is NOT currently running (running state is live)
            val isAlreadyRunning = _uiState.value.sessionStates[sessionId]?.isRunning == true
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
        updateSession(sessionId) { it.copy(messages = messages) }
        _uiState.update { it.copy(
            historyOffset = pageSize,
            historyHasMore = hasMore,
            historyLoading = false,
        )}
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

    private suspend fun persistMessages(sessionId: String, userMsg: ChatMessage, agentMsgs: List<ChatMessage>) {
        if (sessionId.isBlank()) return
        val gson = Gson()

        database.sessionMessageDao().insert(SessionMessageEntity(
            sessionId = sessionId,
            role = "user",
            text = userMsg.text,
            imageBase64 = userMsg.imageBase64,
        ))
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
        if (session != null && session.title == str(R.string.vm_new_)) {
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
        val messages = mutableListOf<ChatMessage>()
        if (summary.isNotBlank() || logLines.isNotEmpty()) {
            messages += ChatMessage(
                role = MessageRole.AGENT,
                text = summary,
                logLines = logLines,
                senderRoleId = senderRole.id,
                senderRoleName = senderRole.name,
                senderRoleAvatar = senderRole.avatar,
            )
        }
        attachments.forEach { attachment ->
            messages += ChatMessage(
                role = MessageRole.AGENT,
                text = "",
                attachments = listOf(attachment),
                senderRoleId = senderRole.id,
                senderRoleName = senderRole.name,
                senderRoleAvatar = senderRole.avatar,
            )
        }
        if (messages.isEmpty()) {
            messages += ChatMessage(
                role = MessageRole.AGENT,
                text = "Done.",
                senderRoleId = senderRole.id,
                senderRoleName = senderRole.name,
                senderRoleAvatar = senderRole.avatar,
            )
        }
        return messages
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
        val facts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
        val semanticFacts = runCatching { app.semanticMemory.allFactsIncludingDisabled() }.getOrDefault(emptyList())
        _uiState.update { it.copy(profileFacts = facts, semanticFacts = semanticFacts) }
    }

    private suspend fun recordUserMemoryHints(text: String) {
        runCatching { memoryWriter.recordExplicitUserText(text) }
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
        val trimmed = goal.trim()
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
                runTaskInternal(originalGoal)
                return
            }
            trimmed.startsWith(CONFIRM_TASK_PREFIX) -> {
                runTaskInternal(trimmed.removePrefix(CONFIRM_TASK_PREFIX).trim())
                return
            }
            trimmed == CANCEL_CONFIRMATION_TEXT -> {
                pendingAccessibilityTaskGoal = null
                pendingRoleSwitchTaskGoal = null
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
                        runTaskInternal(originalGoal)
                    } else {
                        appendConfirmationResolution("已切换到 ${role.name}。")
                    }
                } else {
                    appendConfirmationResolution("没有找到这个角色：$roleId")
                }
                return
            }
        }

        if (pendingAccessibilityTaskGoal != null && isAccessibilityResumeText(trimmed)) {
            val originalGoal = pendingAccessibilityTaskGoal.orEmpty()
            if (ClawAccessibilityService.isEnabled()) {
                pendingAccessibilityTaskGoal = null
                runTaskInternal(originalGoal)
            } else {
                requestTaskExecutionConfirmation(originalGoal, TaskType.PHONE_CONTROL)
            }
            return
        }

        val roleSwitchTarget = inferExplicitRoleSwitchTarget(trimmed)
        if (roleSwitchTarget != null) {
            setActiveRole(roleSwitchTarget)
            appendConfirmationResolution("已切换到 ${roleSwitchTarget.name}。")
            return
        }

        val contextualIntent = resolveContextualTaskIntent(trimmed, _uiState.value.inputImageBase64 != null, _uiState.value.inputFileAttachment != null)
        val classifiedTaskType = TaskClassifier.classify(
            goal = contextualIntent.classificationGoal.ifBlank { trimmed },
            hasImage = _uiState.value.inputImageBase64 != null,
            hasFile = _uiState.value.inputFileAttachment != null,
        )
        val taskType = inferFollowUpTaskType(trimmed, classifiedTaskType)
            ?: contextualIntent.taskTypeOverride
            ?: classifiedTaskType
        if (requiresUserExecutionConfirmation(taskType, trimmed)) {
            requestTaskExecutionConfirmation(trimmed, taskType)
            return
        }

        runTaskInternal(goal)
    }

    private fun runTaskInternal(
        goal: String,
        imageOverride: String? = null,
        visibleUserText: String = goal,
    ) {
        val currentSessionId = _uiState.value.currentSessionId
        if (goal.isBlank() || _uiState.value.sessionStates[currentSessionId]?.isRunning == true) return

        val attachedImage = imageOverride ?: _uiState.value.inputImageBase64
        val attachedFile = _uiState.value.inputFileAttachment
        val sessionIdAtStart = _uiState.value.currentSessionId
        // Prepend text file content directly into the LLM goal
        val effectiveGoal = if (attachedFile != null && attachedFile.isText) {
            "[附件: ${attachedFile.name}]\n```\n${attachedFile.content.take(10_000)}\n```\n\n$goal"
        } else goal
        val contextualIntent = resolveContextualTaskIntent(goal, attachedImage != null, attachedFile != null)
        val inferredAiPageTarget = contextualIntent.aiPage
        val classifiedTaskType = TaskClassifier.classify(
            goal = contextualIntent.classificationGoal.ifBlank { effectiveGoal },
            hasImage = attachedImage != null,
            hasFile = attachedFile != null,
        )
        val taskType = inferFollowUpTaskType(goal, classifiedTaskType)
            ?: contextualIntent.taskTypeOverride
            ?: if (inferredAiPageTarget != null && attachedImage == null && attachedFile == null) TaskType.APP_BUILD else classifiedTaskType
        val stickerAwareChat = attachedImage == null &&
            attachedFile == null &&
            taskType == TaskType.GENERAL &&
            ChatRouter.classify(goal) == ChatRouter.Intent.CHAT &&
            shouldUseStickerAwareChat(goal)
        val executionTaskType = if (stickerAwareChat) TaskType.CHAT else taskType
        val contextualGoal = applyContextualTaskConstraints(effectiveGoal, contextualIntent, taskType)
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
        // Build the user message once so we have a stable reference for persisting
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            text = visibleUserText,
            imageBase64 = if (attachedImage != null) attachedImage
                          else if (attachedFile != null && !attachedFile.isText) attachedFile.content
                          else null,
        )
        val visibleGoalLabel = visibleUserText.ifBlank {
            if (attachedImage != null) str(R.string.sticker_button) else goal
        }

        _uiState.update { it.copy(inputImageBase64 = null, inputFileAttachment = null) }
        updateSession(sessionIdAtStart) { s -> s.copy(
            isRunning = true,
            messages = s.messages + userMessage,
            activeLogLines = emptyList(),
            activeAttachments = emptyList(),
            streamingToken = "",
            streamingThought = "",
        )}
        // Fast path: image understanding is a VLM chat, not an agentic web/search task.
        if (attachedImage != null &&
            attachedFile == null &&
            executionTaskType == TaskType.GENERAL &&
            shouldAnswerImageDirectly(goal)) {
            runDirectChat(sessionIdAtStart, userMessage, goal, scheduledRole, directPriorContext, attachedImage)
            return
        }

        // Fast path: conversational message with no attachments → skip agent loop
        if (!stickerAwareChat &&
            attachedImage == null && attachedFile == null &&
            taskType == TaskType.GENERAL &&
            ChatRouter.classify(goal) == ChatRouter.Intent.CHAT) {
            runDirectChat(sessionIdAtStart, userMessage, goal, scheduledRole, directPriorContext)
            return
        }

        val llm = app.createLlmGateway()
        val rt = AgentRuntime(llm, registry, app.semanticMemory, memoryContextBuilder)
        runtimes[sessionIdAtStart] = rt

        overlay.show(visibleGoalLabel)
        if (isPhoneControlTask) {
            auroraOverlay.beginTask()
        }

        consoleServer.broadcast("task_started", visibleGoalLabel)

        val newJob = viewModelScope.launch {
            // Ensure a session exists
            var sessionId = sessionIdAtStart
            if (sessionId.isBlank()) {
                withContext(Dispatchers.IO) { createNewSessionInternal() }
                sessionId = _uiState.value.currentSessionId
                // Move initial state to the real session id
                val prev = _uiState.value.sessionStates[sessionIdAtStart]
                if (prev != null && sessionId != sessionIdAtStart) {
                    _uiState.update { state ->
                        state.copy(sessionStates = (state.sessionStates - sessionIdAtStart) + (sessionId to prev))
                    }
                }
            }
            val resolvedSessionId = sessionId
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
                                    updateSession(resolvedSessionId) { it.copy(activeLogLines = it.activeLogLines + line) }
                                }
                            }
                        }
                        is AgentEvent.ThinkingToken -> {
                            overlay.onToken(event.text)
                            updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + event.text) }
                        }
                        is AgentEvent.SkillCalling -> {
                            overlay.onSkillCalling(event.skillId, event.params)
                            if (isPhoneControlTask || event.skillId in VISUAL_SKILL_IDS) {
                                if (event.skillId == "see_screen") auroraOverlay.flashFullScreen()
                                else auroraOverlay.flash()
                            }
                            // Build full details: formatted params for the detail sheet
                            val paramDetails = event.params.entries.map { (k, v) ->
                                "  $k: ${Gson().toJson(v).take(300)}"
                            }
                            val lineDetails = listOf(str(R.string.vm_c96809)) + paramDetails
                            val line = event.toLogLine()?.copy(details = lineDetails)
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    streamingThought = "",
                                    activeLogLines = if (line != null) s.activeLogLines + line else s.activeLogLines,
                                )
                            }
                            consoleServer.broadcast("skill_called", friendlySkillDescription(event.skillId, event.params))
                        }
                        is AgentEvent.Observation -> {
                            overlay.onObservation(event.text)
                            if (event.attachment is SkillAttachment.ActionCard && event.attachment.tone == "role") {
                                pendingRoleSwitchTaskGoal = contextualGoal
                            }
                            val lineDetails = buildList {
                                if (event.text.isNotBlank()) {
                                    add("完整结果 (${event.text.length} 字符):")
                                    add(event.text.take(2000))
                                }
                            }
                            val line = LogLine(
                                type = LogType.OBSERVATION,
                                text = event.text.take(400),
                                imageBase64 = event.imageBase64,
                                details = lineDetails,
                            )
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    activeLogLines = s.activeLogLines + line,
                                    activeAttachments = if (event.attachment != null)
                                        s.activeAttachments + event.attachment
                                    else s.activeAttachments,
                                )
                            }
                        }
                        is AgentEvent.Error -> {
                            overlay.onError(event.message)
                            event.toLogLine()?.let { line ->
                                updateSession(resolvedSessionId) { it.copy(activeLogLines = it.activeLogLines + line) }
                            }
                        }
                        is AgentEvent.ThinkingComplete -> {
                            overlay.onThinkingComplete()
                            updateSession(resolvedSessionId) { s ->
                                s.copy(
                                    activeLogLines = s.activeLogLines + LogLine(LogType.THINKING, event.thought),
                                    streamingToken = "",
                                    streamingThought = "",
                                )
                            }
                        }
                        is AgentEvent.PlanCreated -> {
                            val text = "Role scheduled: ${scheduledRole.name} (${scheduledRole.id})\n${scheduleDecision.reason}\n\n${event.plan.toPrompt()}"
                            updateSession(resolvedSessionId) { s ->
                                s.copy(activeLogLines = s.activeLogLines + LogLine(LogType.THINKING, text))
                            }
                        }
                        else -> event.toLogLine()?.let { line ->
                            updateSession(resolvedSessionId) { it.copy(activeLogLines = it.activeLogLines + line) }
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

            val result = runCatching {
                val snap = config.snapshot()
                rt.run(
                    goal             = contextualGoal,
                    taskType         = executionTaskType,
                    priorContext     = agentPriorContext,
                    episodicContext  = episodicContext,
                    language         = config.language,
                    imageBase64      = attachedImage,
                    role             = scheduledRole,
                    userProfileContext = userProfileContext,
                    preferFastLocalVision = attachedImage != null && (snap.localNativeOnly || snap.localModelEnabled),
                    onToken       = { token ->
                        val clean = token.cleanLocalTurnTokens()
                        if (clean.isNotEmpty()) {
                            overlay.onToken(clean)
                            updateSession(resolvedSessionId) { it.copy(streamingToken = (it.streamingToken + clean).cleanLocalTurnTokens()) }
                            consoleServer.broadcast("token", clean)
                        }
                    },
                    onThinkToken  = { token ->
                        overlay.onToken(token)
                        updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + token) }
                    },
                )
            }
            networkTraceJob.cancel()
            runtimeEventJob.cancel()
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) {
                overlay.hide()
                if (isPhoneControlTask) auroraOverlay.endTask()
                updateSession(resolvedSessionId) { s ->
                    s.copy(
                        isRunning = false,
                        streamingToken = "",
                        streamingThought = "",
                        activeLogLines = emptyList(),
                        activeAttachments = emptyList(),
                    )
                }
                taskJobs.remove(resolvedSessionId)
                if (sessionIdAtStart != resolvedSessionId) taskJobs.remove(sessionIdAtStart)
                runtimes.remove(resolvedSessionId)
                if (sessionIdAtStart != resolvedSessionId) runtimes.remove(sessionIdAtStart)
                return@launch
            }

            overlay.hide()
            if (isPhoneControlTask) auroraOverlay.endTask()

            val summary = result.getOrNull()?.summary ?: result.exceptionOrNull()?.message ?: "Task failed."
            consoleServer.broadcast("task_completed", summary)

            launch {
                val agentResult = result.getOrNull() ?: return@launch
                runCatching { episodicMemory.record(agentResult) }
                runCatching {
                    val replay = app.taskReplayStore.record(agentResult, executionTaskType, scheduledRole)
                    if (agentResult.success && replay.steps.any { !it.skillId.isNullOrBlank() && !it.isError }) {
                        app.taskRecipeStore.createFromReplay(replay)
                    }
                }
            }
            launch(Dispatchers.IO) {
                runCatching {
                    conversationMemory.addUserMessage(goal)
                    conversationMemory.addAgentMessage(summary)
                    recordUserMemoryHints(goal)
                    profileExtractor.extractAndUpdate(goal, summary)
                }
            }

            val currentRunState = _uiState.value.sessionStates[resolvedSessionId] ?: SessionRunState()
            val finalAgentMessages = run {
                val finalLogLines = buildList {
                    addAll(currentRunState.activeLogLines)
                    if (currentRunState.streamingThought.isNotBlank()) {
                        add(LogLine(LogType.THINKING, currentRunState.streamingThought))
                    }
                }
                buildAgentMessages(summary, finalLogLines, currentRunState.activeAttachments, scheduledRole)
            }
            updateSession(resolvedSessionId) { s -> s.copy(
                isRunning = false,
                streamingToken = "",
                streamingThought = "",
                messages = s.messages + finalAgentMessages,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )}
            taskJobs.remove(resolvedSessionId)
            if (sessionIdAtStart != resolvedSessionId) taskJobs.remove(sessionIdAtStart)
            runtimes.remove(resolvedSessionId)
            if (sessionIdAtStart != resolvedSessionId) runtimes.remove(sessionIdAtStart)

            // Persist the exchange to the session DB
            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage, finalAgentMessages) }
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
        }
        taskJobs[sessionIdAtStart] = newJob
    }

    private fun runDirectChat(
        sessionIdAtStart: String,
        userMessage: ChatMessage,
        goal: String,
        currentRole: Role,
        priorContext: String,
        imageBase64: String? = null,
    ) {
        val newJob = viewModelScope.launch {
            var sessionId = sessionIdAtStart
            var resolvedSessionId = sessionIdAtStart
            try {
            if (sessionId.isBlank()) {
                withContext(Dispatchers.IO) { createNewSessionInternal() }
                sessionId = _uiState.value.currentSessionId
                val prev = _uiState.value.sessionStates[sessionIdAtStart]
                if (prev != null && sessionId != sessionIdAtStart) {
                    _uiState.update { state ->
                        state.copy(sessionStates = (state.sessionStates - sessionIdAtStart) + (sessionId to prev))
                    }
                }
            }
            resolvedSessionId = sessionId
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
            val systemPrompt = if (localChatMode) {
                buildString {
                    appendLine("You are ${currentRole.name}, MobileClaw's on-device assistant.")
                    append(langSection)
                    append(imageInstruction)
                    if (currentRole.id != "general" && currentRole.systemPromptAddendum.isNotBlank()) {
                        appendLine("Persona: ${currentRole.systemPromptAddendum.trim().take(180)}")
                    }
                    if (priorContext.isNotBlank()) {
                        appendLine("Stable memory and active artifacts:")
                        appendLine(priorContext.take(1200))
                    }
                    appendLine("Answer directly. Short follow-ups refer to the recent context. Do not create pages, apps, files, or UI blocks unless the latest user message explicitly asks.")
                }.trim()
            } else {
                """You are ${currentRole.name}, a helpful AI assistant.$langSection$imageInstruction$roleSection$contextSection
## Context Rules
Use the current user message as the source of truth. Treat recent conversation as supporting context only.
Short follow-ups like “继续/改一下/不是这个/换个方式” refer to the most relevant recent message or artifact.
Do not start building pages, HTML, MiniAPPs, or UI artifacts unless the user clearly asks to create or modify one.

## Optional Interactive UI
For normal conversation, reply in plain text.
Only embed a ${"```"}ui block when the user explicitly asks for interactive choices, forms, tables, comparisons, or dashboards. Keep it on as few lines as possible.
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

            val result = runCatching {
                llm.chat(ChatRequest(
                    messages = chatMessages,
                    tools = emptyList(),
                    stream = true,
                    onToken = { token ->
                        val clean = token.cleanLocalTurnTokens()
                        if (clean.isNotEmpty()) {
                            updateSession(resolvedSessionId) { it.copy(streamingToken = (it.streamingToken + clean).cleanLocalTurnTokens()) }
                        }
                    },
                ))
            }

            val summary = (result.getOrNull()?.content
                ?: _uiState.value.sessionStates[resolvedSessionId]?.streamingToken?.ifBlank { null }
                ?: result.exceptionOrNull()?.message ?: "Error.").cleanLocalTurnTokens()

            val finalAgentMsg = ChatMessage(
                role = MessageRole.AGENT,
                text = summary,
                senderRoleId = currentRole.id,
                senderRoleName = currentRole.name,
                senderRoleAvatar = currentRole.avatar,
            )
            updateSession(resolvedSessionId) { s -> s.copy(
                isRunning = false,
                streamingToken = "",
                streamingThought = "",
                messages = s.messages + finalAgentMsg,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )}

            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage, listOf(finalAgentMsg)) }
            }
            launch(Dispatchers.IO) {
                runCatching {
                    conversationMemory.addUserMessage(goal)
                    conversationMemory.addAgentMessage(summary)
                    recordUserMemoryHints(goal)
                }
            }
            } catch (e: Throwable) {
                val cleanupSessionId = resolvedSessionId.ifBlank { sessionIdAtStart }
                if (e is kotlinx.coroutines.CancellationException) {
                    updateSession(cleanupSessionId) { s ->
                        s.copy(isRunning = false, streamingToken = "", streamingThought = "")
                    }
                    return@launch
                }
                updateSession(cleanupSessionId) { s ->
                    s.copy(
                        isRunning = false,
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
            } finally {
                taskJobs.remove(resolvedSessionId)
                if (sessionIdAtStart != resolvedSessionId) taskJobs.remove(sessionIdAtStart)
                runtimes.remove(resolvedSessionId)
                if (sessionIdAtStart != resolvedSessionId) runtimes.remove(sessionIdAtStart)
            }
        }
        taskJobs[sessionIdAtStart] = newJob
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
        taskJobs[sessionId]?.cancel()
        taskJobs.remove(sessionId)
        runtimes.remove(sessionId)
        overlay.hide()
        auroraOverlay.hide()
        consoleServer.broadcast("task_stopped", "")
        val runState = _uiState.value.sessionStates[sessionId] ?: SessionRunState()
        updateSession(sessionId) { state ->
            val agentMsgs = if (state.activeLogLines.isNotEmpty() || state.streamingToken.isNotBlank() || state.activeAttachments.isNotEmpty()) {
                buildAgentMessages(
                    summary = state.streamingToken.ifBlank { "Task stopped." },
                    logLines = state.activeLogLines,
                    attachments = state.activeAttachments,
                    senderRole = _uiState.value.currentRole,
                )
            } else emptyList()
            state.copy(
                isRunning = false,
                streamingToken = "",
                streamingThought = "",
                messages = state.messages + agentMsgs,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )
        }
    }

    fun setInputImage(imageBase64: String?) {
        _uiState.update { it.copy(inputImageBase64 = imageBase64) }
    }

    fun sendImageMessage(imageBase64: String, prompt: String = "") {
        val hiddenPrompt = prompt.ifBlank {
            "用户发送了一张表情包图片。请根据图片内容、情绪和当前聊天上下文自然回应；不要复述这段系统提示，也不要说“我看到了一个附件”。"
        }
        runTaskInternal(hiddenPrompt, imageOverride = imageBase64, visibleUserText = "")
    }

    fun setFileAttachment(attachment: FileAttachment?) {
        _uiState.update { it.copy(inputFileAttachment = attachment) }
    }

    private val backStack = ArrayDeque<AppPage>().apply { add(AppPage.CHAT) }

    fun navigate(page: AppPage) {
        if (page == AppPage.SKILLS) refreshPromotableSkills()
        if (page == AppPage.PROFILE) loadProfileData()
        if (page == AppPage.SETTINGS) checkPrivServer()
        if (page == AppPage.APPS || page == AppPage.HOME) loadMiniApps()
        if (page == AppPage.GROUPS) loadGroups()
        if (page == AppPage.GROUP_CHAT) _uiState.update { it.copy(groupUnreadCount = 0) }

        // CHAT and HOME are root-level peers — switching between them resets the stack
        // so system back doesn't replay the toggle history.
        if (page == AppPage.CHAT || page == AppPage.HOME) {
            backStack.clear()
            backStack.addLast(page)
        } else if (backStack.isEmpty() || backStack.last() != page) {
            backStack.addLast(page)
        }

        if (page == AppPage.BROWSER && _uiState.value.browserUrl.isBlank()) {
            _uiState.update { it.copy(browserUrl = "https://www.bing.com", currentPage = page, canNavigateBack = backStack.size > 1) }
            return
        }
        _uiState.update { it.copy(currentPage = page, canNavigateBack = backStack.size > 1) }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
            val page = backStack.last()
            if (page == AppPage.APPS || page == AppPage.HOME) loadMiniApps()
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
            backStack.addLast(if (currentPage == AppPage.BROWSER) AppPage.CHAT else currentPage)
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

    fun clearAiPageOpen() {
        _uiState.update { it.copy(openAiPageId = null) }
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
    private val pendingGroupTurnsLock = Any()
    private val recentGroupStickerPathsLock = Any()
    private data class PendingGroupTurn(
        val roleId: String,
        val triggerText: String,
        val chainDepth: Int,
        val priority: Int,
        val longTask: Boolean,
        val requireResponse: Boolean = false,
        val queuedUserText: String? = null,
    )
    private data class GroupTurnResult(
        val text: String,
        val attachments: List<SkillAttachment> = emptyList(),
    )

    private fun groupHistoryFile(groupId: String): File =
        File(app.filesDir, "group_history/$groupId.jsonl").also { it.parentFile?.mkdirs() }

    private fun groupHistoryDir(): File =
        File(app.filesDir, "group_history").also { it.mkdirs() }

    private fun appendGroupHistoryBackup(message: GroupMessage) {
        runCatching {
            val payload = mapOf(
                "id" to message.id,
                "groupId" to message.groupId,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderAvatar" to message.senderAvatar,
                "text" to message.text,
                "attachmentsJson" to serializeAttachments(message.attachments),
                "createdAt" to message.createdAt,
            )
            val file = groupHistoryFile(message.groupId)
            file.appendText(gson.toJson(payload) + "\n")
            compactGroupHistoryBackupIfNeeded(file)
        }
    }

    private fun readGroupHistoryBackup(groupId: String): List<GroupMessage> {
        return runCatching {
            val file = groupHistoryFile(groupId)
            if (!file.exists()) return@runCatching emptyList()
            readLastLines(file, maxLines = 300).mapNotNull { line ->
                runCatching {
                    val obj = JsonParser.parseString(line).asJsonObject
                    GroupMessage(
                        id = obj["id"]?.asLong ?: 0L,
                        groupId = obj["groupId"]?.asString ?: groupId,
                        senderId = obj["senderId"]?.asString ?: "",
                        senderName = obj["senderName"]?.asString ?: "",
                        senderAvatar = obj["senderAvatar"]?.asString ?: RoleAvatarDefaults.CUSTOM,
                        text = obj["text"]?.asString ?: "",
                        attachments = deserializeAttachments(obj["attachmentsJson"]?.asString ?: "[]"),
                        createdAt = obj["createdAt"]?.asLong ?: 0L,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun compactGroupHistoryBackupIfNeeded(file: File) {
        if (!file.exists() || file.length() < 2_000_000L) return
        runCatching {
            val recent = readLastLines(file, maxLines = 500)
            file.writeText(recent.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private fun readLastLines(file: File, maxLines: Int): List<String> {
        if (maxLines <= 0 || !file.exists()) return emptyList()
        val lines = ArrayDeque<String>()
        java.io.RandomAccessFile(file, "r").use { raf ->
            var pointer = raf.length() - 1
            val bytes = ByteArrayOutputStream()
            while (pointer >= 0 && lines.size < maxLines) {
                raf.seek(pointer)
                val b = raf.read()
                if (b == '\n'.code) {
                    if (bytes.size() > 0) {
                        lines.addFirst(String(bytes.toByteArray().reversedArray(), Charsets.UTF_8))
                        bytes.reset()
                    }
                } else {
                    bytes.write(b)
                }
                pointer--
            }
            if (bytes.size() > 0 && lines.size < maxLines) {
                lines.addFirst(String(bytes.toByteArray().reversedArray(), Charsets.UTF_8))
            }
        }
        return lines.filter { it.isNotBlank() }
    }

    private fun mergeGroupHistory(primary: List<GroupMessage>, backup: List<GroupMessage>): List<GroupMessage> {
        return (primary + backup)
            .distinctBy(::groupMessageDedupeKey)
            .sortedBy { it.createdAt }
            .takeLast(300)
    }

    private fun groupMessageDedupeKey(message: GroupMessage): String =
        "${message.groupId}:${message.senderId}:${message.createdAt}:${message.text}:${message.attachments.size}"

    private suspend fun logGroupRuntimeDiagnostics(
        marker: String,
        groups: List<com.mobileclaw.agent.Group>,
        activeGroup: com.mobileclaw.agent.Group? = null,
        activeMessages: List<GroupMessage> = emptyList(),
        activeBackupMessages: List<GroupMessage> = emptyList(),
    ) {
        val roles = roleManager.all()
        val roleIds = roles.map { it.id }.toSet()
        val groupIds = groups.map { it.id }.toSet()
        val dbCounts = runCatching { database.groupMessageDao().groupCounts() }.getOrDefault(emptyList())
        val backupFiles = groupHistoryDir().listFiles { file -> file.extension == "jsonl" }?.toList().orEmpty()
        val backupSummary = backupFiles.joinToString(limit = 20) { file ->
            val id = file.nameWithoutExtension
            "$id:${file.length()}b"
        }
        val orphanDbGroups = dbCounts.map { it.groupId }.filterNot { it in groupIds }
        val orphanBackupGroups = backupFiles.map { it.nameWithoutExtension }.filterNot { it in groupIds }
        val missingRoles = groups.associate { group ->
            group.id to group.memberRoleIds.filterNot { it in roleIds }
        }.filterValues { it.isNotEmpty() }
        val possibleHistoryMismatch = activeGroup?.takeIf { activeMessages.isEmpty() }?.let { group ->
            val sameNameGroups = groups.filter { it.name == group.name && it.id != group.id }.map { it.id }
            val orphanCandidates = (orphanDbGroups + orphanBackupGroups).distinct()
            "sameName=${sameNameGroups.joinToString(limit = 10)} orphanCandidates=${orphanCandidates.joinToString(limit = 16)}"
        }.orEmpty()
        val activeSenderSummary = activeMessages
            .groupingBy { it.senderId.ifBlank { "(blank)" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(limit = 16) { "${it.key}:${it.value}" }
        android.util.Log.d(
            "ClawGroup",
            "diag[$marker] groups=${groups.size} groupIds=${groups.joinToString(limit = 20) { it.id }} " +
                "dbGroups=${dbCounts.joinToString(limit = 20) { "${it.groupId}:${it.count}@${it.latestAt}" }} " +
                "backupGroups=$backupSummary orphanDb=${orphanDbGroups.joinToString(limit = 20)} " +
                "orphanBackup=${orphanBackupGroups.joinToString(limit = 20)} missingRoles=$missingRoles " +
                "active=${activeGroup?.id.orEmpty()} activeDbOrMerged=${activeMessages.size} " +
                "activeBackup=${activeBackupMessages.size} activeSenders=$activeSenderSummary " +
                "possibleMismatch=$possibleHistoryMismatch"
        )
    }
    @Volatile private var groupChatStopped = false
    private val pendingGroupTurns = ArrayDeque<PendingGroupTurn>()
    private val GROUP_TASK_POOL_LIMIT = 4

    private fun pendingUserMessagesSnapshot(): List<String> =
        synchronized(pendingGroupTurnsLock) {
            pendingGroupTurns.mapNotNull { it.queuedUserText }.distinct()
        }

    private fun addPendingGroupTurnFirst(turn: PendingGroupTurn) {
        synchronized(pendingGroupTurnsLock) {
            pendingGroupTurns.addFirst(turn)
        }
    }

    private fun addPendingGroupTurnLast(turn: PendingGroupTurn) {
        synchronized(pendingGroupTurnsLock) {
            pendingGroupTurns.addLast(turn)
        }
    }

    private fun clearPendingGroupTurns() {
        synchronized(pendingGroupTurnsLock) {
            pendingGroupTurns.clear()
        }
    }

    private fun pendingGroupTurnsIsEmpty(): Boolean =
        synchronized(pendingGroupTurnsLock) { pendingGroupTurns.isEmpty() }

    private fun pollNextPendingGroupTurn(): PendingGroupTurn? =
        synchronized(pendingGroupTurnsLock) {
            val index = pendingGroupTurns
                .withIndex()
                .filter { (_, turn) -> !groupAgentJobs.containsKey(turn.roleId) }
                .maxByOrNull { (_, turn) -> turn.priority }
                ?.index ?: return@synchronized null
            pendingGroupTurns.removeAt(index)
        }
    private val GROUP_CHAT_INITIAL_MAX = 2
    private val GROUP_CHAT_ORGANIC_MAX = 2
    private val GROUP_MESSAGE_PAGE_SIZE = 20

    fun loadGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = groupManager.all()
            val previews = groups.associate { group ->
                val dbLatest = database.groupMessageDao().latestForGroup(group.id)?.let {
                    GroupMessage(it.id, it.groupId, it.senderId, it.senderName, it.senderAvatar, it.text, deserializeAttachments(it.attachmentsJson), it.createdAt)
                }
                val backupLatest = readGroupHistoryBackup(group.id).maxByOrNull { it.createdAt }
                val latest = listOfNotNull(dbLatest, backupLatest).maxByOrNull { it.createdAt }
                group.id to latest?.let {
                    GroupPreview(
                        senderName = it.senderName,
                        text = groupPreviewText(it.text, serializeAttachments(it.attachments)),
                        createdAt = it.createdAt,
                    )
                }
            }.filterValues { it != null }.mapValues { it.value!! }
            android.util.Log.d("ClawGroup", "loadGroups groups=${groups.size} previews=${previews.size} ids=${groups.joinToString { it.id }}")
            logGroupRuntimeDiagnostics("loadGroups", groups)
            _uiState.update { it.copy(groups = groups, groupPreviews = previews) }
        }
    }

    private fun groupPreviewText(text: String, attachmentsJson: String): String {
        val cleanText = text.trim().replace(Regex("\\s+"), " ")
        if (cleanText.isNotBlank()) return cleanText
        return deserializeAttachments(attachmentsJson).firstOrNull()?.let { attachment ->
            when (attachment) {
                is SkillAttachment.ImageData -> str(R.string.group_label_image)
                is SkillAttachment.FileData -> attachment.name.ifBlank { str(R.string.group_label_file) }
                is SkillAttachment.HtmlData -> attachment.title.ifBlank { str(R.string.group_label_web) }
                is SkillAttachment.WebPage -> attachment.title.ifBlank { str(R.string.group_label_link) }
                is SkillAttachment.SearchResults -> str(R.string.group_label_search)
                is SkillAttachment.FileList -> str(R.string.group_label_file_list)
                is SkillAttachment.AccessibilityRequest -> str(R.string.group_label_permission)
                is SkillAttachment.ActionCard -> attachment.title.ifBlank { "操作确认" }
            }
        }.orEmpty()
    }

    fun createGroup(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            initializeGroupBubbleStyles(group)
            groupManager.save(group)
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

    private fun defaultGroupBubbleStyleFor(role: Role, index: Int): ChatBubbleStyle {
        val palettes = listOf(
            BubbleStyleSeed("#F8F8F6", "#111111", "#D7D7D2", "#111111", "minimal", "contour", "dot", "medium", "neutral", "none"),
            BubbleStyleSeed("#050505", "#FFFFFF", "#050505", "#C7F43A", "ink", "none", "sparkle", "semibold", "cool", "shimmer"),
            BubbleStyleSeed("#FFFFFF", "#121212", "#D8D8D8", "#56D6BA", "outline", "dot", "badge", "medium", "happy", "breath"),
            BubbleStyleSeed("#F7F7F2", "#171717", "#E5E5DE", "#8A8A8A", "paper", "grid", "moon", "regular", "sleepy", "fade"),
            BubbleStyleSeed("#111111", "#F8F8F5", "#242424", "#C7F43A", "glass", "stripe", "star", "medium", "excited", "pop"),
            BubbleStyleSeed("#FAFAF7", "#0C0C0C", "#DBDBD4", "#56D6BA", "minimal", "star", "heart", "semibold", "love", "wave"),
        )
        val seed = palettes[index % palettes.size]
        val radius = 16 + ((role.id.hashCode() and 0x7fffffff) % 9)
        return ChatBubbleStyle(
            preset = seed.preset,
            renderer = "native",
            backgroundColor = seed.background,
            textColor = seed.text,
            borderColor = seed.border,
            accentColor = seed.accent,
            radiusDp = radius,
            radiusTopStartDp = radius,
            radiusTopEndDp = (radius + 4).coerceAtMost(28),
            radiusBottomEndDp = radius,
            radiusBottomStartDp = (radius - 6).coerceAtLeast(6),
            pattern = seed.pattern,
            decoration = seed.decoration,
            decorationText = role.name.take(2),
            decorationPosition = if (index % 2 == 0) "top_end" else "bottom_end",
            decorationAnimation = if (index % 3 == 0) "pulse" else "none",
            emotion = seed.emotion,
            fontFamily = if (index % 3 == 1) "rounded" else "system",
            fontWeight = seed.fontWeight,
            textAnimation = seed.textAnimation,
            fontSizeSp = 14,
            lineHeightSp = 20,
            paddingHorizontalDp = 13,
            paddingVerticalDp = 9,
            shadow = if (index % 2 == 0) "soft" else "none",
            shadowColor = "#000000",
            shadowAlpha = if (index % 2 == 0) 0.12f else -1f,
            shadowElevationDp = if (index % 2 == 0) 4 else -1,
            imageMode = "cover",
        )
    }

    private data class BubbleStyleSeed(
        val background: String,
        val text: String,
        val border: String,
        val accent: String,
        val preset: String,
        val pattern: String,
        val decoration: String,
        val fontWeight: String,
        val emotion: String,
        val textAnimation: String,
    )

    fun deleteGroup(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager.delete(id)
            database.groupMessageDao().deleteForGroup(id)
            runCatching { groupHistoryFile(id).delete() }
            loadGroups()
            if (_uiState.value.openGroup?.id == id) closeGroupChat()
        }
    }

    fun openGroupChat(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            groupChatStopped = false
            val pageSize = GROUP_MESSAGE_PAGE_SIZE
            val total = runCatching { database.groupMessageDao().countForGroup(group.id) }.getOrDefault(0)
            val entities = database.groupMessageDao().forGroupPaged(group.id, pageSize, 0).reversed()
            val messages = entities.map { e ->
                runCatching {
                    GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, deserializeAttachments(e.attachmentsJson), e.createdAt)
                }.getOrElse {
                    GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, emptyList(), e.createdAt)
                }
            }
            val backupMessages = if (messages.isEmpty()) readGroupHistoryBackup(group.id).takeLast(pageSize) else emptyList()
            val mergedMessages = mergeGroupHistory(messages, backupMessages).takeLast(pageSize)
            val currentRoles = roleManager.all()
            val missingRoles = group.memberRoleIds.filter { roleId -> currentRoles.none { it.id == roleId } }
            android.util.Log.d(
                "ClawGroup",
                "openGroup id=${group.id} name=${group.name} members=${group.memberRoleIds.joinToString()} " +
                    "missingRoles=${missingRoles.joinToString()} dbMessages=${messages.size} backupMessages=${backupMessages.size} " +
                    "merged=${mergedMessages.size} dbLatest=${messages.lastOrNull()?.createdAt} backupLatest=${backupMessages.lastOrNull()?.createdAt}"
            )
            logGroupRuntimeDiagnostics(
                marker = "openGroup",
                groups = groupManager.all(),
                activeGroup = group,
                activeMessages = mergedMessages,
                activeBackupMessages = backupMessages,
            )
            val running = groupAgentJobs.isNotEmpty()
            _uiState.update {
                it.copy(
                    openGroup = group,
                    groupMessages = mergedMessages,
                    groupHistoryOffset = pageSize,
                    groupHistoryHasMore = total > pageSize,
                    groupHistoryLoading = false,
                    groupRunning = running,
                    groupTypingAgents = if (running) it.groupTypingAgents else emptySet(),
                    groupWorkingAgents = if (running) it.groupWorkingAgents else emptySet(),
                    groupUnreadCount = 0,
                )
            }
            navigate(AppPage.GROUP_CHAT)

            // Auto-spark: if group has members and conversation is cold (no messages or last msg > 15 min ago)
            val lastMsgTime = mergedMessages.lastOrNull()?.createdAt ?: 0L
            val isCold = System.currentTimeMillis() - lastMsgTime > 15 * 60 * 1000L
            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isNotEmpty() && isCold && !groupChatStopped) {
                kotlinx.coroutines.delay(1800)
                if (_uiState.value.currentPage == AppPage.GROUP_CHAT && !_uiState.value.groupRunning) {
                    sparkGroupChat()
                }
            }
        }
    }

    fun loadOlderGroupMessages() {
        val group = _uiState.value.openGroup ?: return
        val state = _uiState.value
        if (state.groupHistoryLoading || !state.groupHistoryHasMore) return
        _uiState.update { it.copy(groupHistoryLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pageSize = GROUP_MESSAGE_PAGE_SIZE
                val offset = _uiState.value.groupHistoryOffset
                val total = runCatching { database.groupMessageDao().countForGroup(group.id) }.getOrDefault(0)
                val entities = runCatching {
                    database.groupMessageDao().forGroupPaged(group.id, pageSize, offset)
                }.getOrDefault(emptyList()).reversed()
                val older = entities.map { e ->
                    runCatching {
                        GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, deserializeAttachments(e.attachmentsJson), e.createdAt)
                    }.getOrElse {
                        GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, emptyList(), e.createdAt)
                    }
                }
                _uiState.update {
                    if (it.openGroup?.id != group.id) it else it.copy(
                        groupMessages = (older + it.groupMessages)
                            .distinctBy { msg -> groupMessageDedupeKey(msg) }
                            .sortedBy { msg -> msg.createdAt },
                        groupHistoryOffset = offset + pageSize,
                        groupHistoryHasMore = offset + pageSize < total,
                        groupHistoryLoading = false,
                    )
                }
            } finally {
                _uiState.update {
                    if (it.openGroup?.id == group.id) it.copy(groupHistoryLoading = false) else it
                }
            }
        }
    }

    fun closeGroupChat() {
        _uiState.update {
            it.copy(
                openGroup = null,
                groupMessages = emptyList(),
                groupHistoryOffset = 0,
                groupHistoryHasMore = false,
                groupHistoryLoading = false,
                groupPendingMessages = emptyList(),
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
        _uiState.update { it.copy(groupRunning = false, groupTypingAgents = emptySet(), groupWorkingAgents = emptySet(), groupPendingMessages = emptyList()) }
    }

    fun stopGroupAgent(roleId: String) {
        groupAgentJobs[roleId]?.cancel()
        groupAgentJobs.remove(roleId)
        _uiState.update { it.copy(groupTypingAgents = it.groupTypingAgents - roleId, groupWorkingAgents = it.groupWorkingAgents - roleId) }
        if (groupAgentJobs.isEmpty()) {
            _uiState.update { it.copy(groupRunning = false) }
        }
    }

    fun sendGroupMessage(text: String, attachments: List<SkillAttachment> = emptyList()) {
        val group = _uiState.value.openGroup ?: return
        if (text.isBlank() && attachments.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = GroupMessage(groupId = group.id, senderId = "user", senderName = str(R.string.group_chat_df1fd9), senderAvatar = "user", text = text, attachments = attachments)
            val rowId = database.groupMessageDao().insert(userMsg.toEntity())
            val savedUserMsg = userMsg.copy(id = rowId)
            appendGroupHistoryBackup(savedUserMsg)
            android.util.Log.d("ClawGroup", "insert user groupId=${group.id} rowId=$rowId textLen=${text.length} attachments=${attachments.size}")
            _uiState.update { it.copy(groupMessages = it.groupMessages + savedUserMsg) }
            recordUserMemoryHints(text)
            conversationMemory.addUserMessage(text)

            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isEmpty()) return@launch

            groupChatStopped = false
            _uiState.update { it.copy(groupRunning = true) }
            groupManager.touch(group.id)
            loadGroups()

            if (groupAgentJobs.size >= GROUP_TASK_POOL_LIMIT) {
                enqueueUserGroupTurn(allMembers, text)
            } else {
                startGroupTurns(group, allMembers, text)
            }
        }
    }

    private fun startGroupTurns(group: com.mobileclaw.agent.Group, allMembers: List<Role>, userText: String) {
        val taskType = TaskClassifier.classify(userText)
        val schedulingText = groupSchedulingText(userText, taskType)
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH)) {
            val selected = RoleScheduler.schedule(taskType, schedulingText, allMembers, allMembers.first()).role
                .takeIf { role -> allMembers.any { it.id == role.id } }
                ?: allMembers.first()
            launchGroupAgentTurn(group, selected, allMembers, delayMs = 0, chainDepth = 0, longTask = true, triggerText = userText, requireResponse = true)
            return
        }
        val mentioned = parseMentions(userText)
        if (mentioned.isNotEmpty()) {
            // Explicit @mention: those agents respond immediately, full chain depth
            allMembers
                .filter { r -> mentioned.any { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) } }
                .forEachIndexed { index, role ->
                    launchGroupAgentTurn(group, role, allMembers, delayMs = 0, chainDepth = 5, triggerText = userText, requireResponse = index == 0)
                }
        } else {
            // No @mention: choose a primary responder. Extra voices should feel intentional,
            // not like every member is trying to append one more sentence.
            val shuffled = allMembers.shuffled()
            val activeCount = when {
                shuffled.size <= 1 -> 1
                shouldInviteMultipleGroupVoices(userText) -> minOf(2, shuffled.size)
                else -> GROUP_CHAT_INITIAL_MAX
            }
            shuffled.take(activeCount).forEachIndexed { idx, role ->
                val delayMs = when (idx) {
                    0    -> (200L..800L).random()
                    1    -> (1500L..3000L).random()
                    else -> (3000L..5500L).random()
                }
                launchGroupAgentTurn(group, role, allMembers, delayMs = delayMs, chainDepth = if (idx == 0) 4 else 2, triggerText = userText, requireResponse = idx == 0)
            }
        }
    }

    private fun enqueueUserGroupTurn(allMembers: List<Role>, userText: String) {
        val taskType = TaskClassifier.classify(userText)
        val schedulingText = groupSchedulingText(userText, taskType)
        val mentioned = parseMentions(userText)
        val targets = when {
            taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH) -> {
                val selected = RoleScheduler.schedule(taskType, schedulingText, allMembers, allMembers.first()).role
                listOfNotNull(allMembers.firstOrNull { it.id == selected.id } ?: allMembers.firstOrNull())
            }
            mentioned.isNotEmpty() -> allMembers.filter { r -> mentioned.any { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) } }
            else -> allMembers.shuffled().take(if (shouldInviteMultipleGroupVoices(userText)) minOf(2, allMembers.size) else 1)
        }
        targets.forEach { role ->
            addPendingGroupTurnFirst(PendingGroupTurn(
                roleId = role.id,
                triggerText = userText,
                chainDepth = if (mentioned.isNotEmpty()) 3 else 1,
                priority = 100,
                longTask = taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH),
                requireResponse = true,
                queuedUserText = userText,
            ))
        }
        _uiState.update { it.copy(groupPendingMessages = pendingUserMessagesSnapshot()) }
    }

    private fun groupSchedulingText(userText: String, taskType: TaskType): String {
        val memory = buildUserMemoryContextForPrompt(userText, taskType).take(1200)
        return listOf(userText, memory).filter { it.isNotBlank() }.joinToString("\n\n")
    }

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
            addPendingGroupTurnLast(PendingGroupTurn(
                roleId = role.id,
                triggerText = triggerText,
                chainDepth = chainDepth,
                priority = if (longTask) 80 else 10,
                longTask = longTask,
                requireResponse = requireResponse,
                queuedUserText = triggerText.takeIf { requireResponse && it.isNotBlank() },
            ))
            _uiState.update { it.copy(groupPendingMessages = pendingUserMessagesSnapshot()) }
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            val taskType = TaskClassifier.classify(triggerText.ifBlank { _uiState.value.groupMessages.lastOrNull()?.text.orEmpty() })
            val phoneTask = taskType == TaskType.PHONE_CONTROL
            try {
                if (delayMs > 0) delay(delayMs)
                if (groupChatStopped) return@launch
                val startsWorking = longTask || phoneTask || taskType !in listOf(TaskType.CHAT, TaskType.GENERAL)
                _uiState.update {
                    if (startsWorking) it.copy(groupWorkingAgents = it.groupWorkingAgents + role.id)
                    else it.copy(groupTypingAgents = it.groupTypingAgents + role.id)
                }
                if (phoneTask) {
                    overlay.showCompact("${role.name}: ${triggerText.take(40)}")
                    auroraOverlay.beginTask()
                }
                val history = _uiState.value.groupMessages
                val memoryPrompt = buildUserMemoryContextForPrompt(triggerText, taskType)
                val systemPrompt = buildGroupSystemPrompt(role, group.name, allMembers, memoryPrompt)
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

                val response = runGroupAgentTurn(
                    role = role,
                    baseMessages = callMessages,
                    taskType = taskType,
                    memoryContext = memoryPrompt,
                    maxSkillCalls = if (longTask || taskType == TaskType.PHONE_CONTROL) 12 else 4,
                    requireResponse = requireResponse,
                    onToolStart = { skillId, params ->
                        _uiState.update { st ->
                            st.copy(
                                groupTypingAgents = st.groupTypingAgents - role.id,
                                groupWorkingAgents = st.groupWorkingAgents + role.id,
                            )
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
                                st.copy(
                                    groupWorkingAgents = st.groupWorkingAgents - role.id,
                                    groupTypingAgents = st.groupTypingAgents + role.id,
                                )
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
                    appendGroupHistoryBackup(savedAgentMsg)
                    conversationMemory.addAgentMessage(cleanResponse)
                    android.util.Log.d("ClawGroup", "insert agent groupId=${group.id} role=${role.id} rowId=$rowId textLen=${cleanResponse.length} attachments=${response.attachments.size}")
                    groupManager.touch(group.id)
                    loadGroups()
                    val onScreen = _uiState.value.currentPage == AppPage.GROUP_CHAT && _uiState.value.openGroup?.id == group.id
                    _uiState.update { st ->
                        if (st.openGroup?.id == group.id) {
                            st.copy(
                                groupMessages = st.groupMessages + savedAgentMsg,
                                groupUnreadCount = if (onScreen) 0 else st.groupUnreadCount + 1,
                            )
                        } else {
                            st.copy(groupUnreadCount = st.groupUnreadCount + 1)
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
                                6 -> 0.82f
                                5 -> 0.72f
                                4 -> 0.60f
                                3 -> 0.42f
                                2 -> 0.28f
                                else -> 0.14f
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
                _uiState.update { it.copy(groupTypingAgents = it.groupTypingAgents - role.id, groupWorkingAgents = it.groupWorkingAgents - role.id) }
                if (TaskClassifier.classify(triggerText.ifBlank { "" }) == TaskType.PHONE_CONTROL) {
                    auroraOverlay.endTask()
                }
                if (TaskClassifier.classify(triggerText.ifBlank { "" }) == TaskType.PHONE_CONTROL && groupAgentJobs.none { (_, job) -> job.isActive }) {
                    overlay.hide()
                }

                drainPendingGroupTurns(group, allMembers)
                if (groupAgentJobs.isEmpty() && pendingGroupTurnsIsEmpty()) {
                    auroraOverlay.hide()
                    overlay.hide()
                    _uiState.update { it.copy(groupRunning = false, groupPendingMessages = emptyList(), groupTypingAgents = emptySet(), groupWorkingAgents = emptySet()) }
                }
            }
        }
        groupAgentJobs[role.id] = job
        job.invokeOnCompletion {
            groupAgentJobs.remove(role.id, job)
            if (groupAgentJobs.isEmpty() && pendingGroupTurnsIsEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        groupRunning = false,
                        groupPendingMessages = emptyList(),
                        groupTypingAgents = state.groupTypingAgents - role.id,
                        groupWorkingAgents = state.groupWorkingAgents - role.id,
                    )
                }
            }
        }
    }

    private fun drainPendingGroupTurns(group: com.mobileclaw.agent.Group, allMembers: List<Role>) {
        while (!groupChatStopped && groupAgentJobs.size < GROUP_TASK_POOL_LIMIT) {
            val nextTurn = pollNextPendingGroupTurn() ?: return
            _uiState.update { it.copy(groupPendingMessages = pendingUserMessagesSnapshot(), groupRunning = true) }
            allMembers.firstOrNull { it.id == nextTurn.roleId }?.let { nextRole ->
                launchGroupAgentTurn(
                    group = group,
                    role = nextRole,
                    allMembers = allMembers,
                    chainDepth = nextTurn.chainDepth,
                    longTask = nextTurn.longTask,
                    triggerText = nextTurn.triggerText,
                    requireResponse = nextTurn.requireResponse,
                )
            }
        }
    }

    private fun buildGroupTurnInstruction(roleName: String, triggerText: String, requireResponse: Boolean): String {
        val trigger = triggerText.trim().take(500)
        val organicChat = isOrganicGroupTrigger(trigger)
        return if (requireResponse) {
            "[系统]: 用户刚在群里问/说：${trigger.ifBlank { "请你开启一个自然话题" }}\n轮到你了，$roleName。你是本轮主回应者，必须给出一条自然、有内容的群聊回复。不要输出 [PASS]，不要只发表情，也不要用“我补充一句/我也来说两句/接一下”这种尴尬开场。"
        } else if (organicChat) {
            "[系统]: 最新群聊内容：${trigger.ifBlank { "群聊冷启动" }}\n轮到你了，$roleName。现在是自然闲聊，不是考试答题。你可以接住上一句、抛一个新角度、轻微吐槽、开个小话题或点名别人。优先发一条有性格的短回复；只有确实重复、没话说或会打断任务时才输出 [PASS]。禁止“我补充一句/我也觉得/确实/哈哈/有道理”这种空话。"
        } else {
            "[系统]: 最新触发内容：${trigger.ifBlank { "群聊冷启动" }}\n轮到你了，$roleName。你不是主回应者。只有在你能提供明显不同的新信息、专业角度、被点名、或这句话明确邀请大家讨论时才回复；否则输出 [PASS]。禁止只说“我补充一句/我也觉得/确实/哈哈/有道理”。"
        }
    }

    private fun isOrganicGroupTrigger(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return true
        if (clean.contains("群聊冷启动")) return true
        if (TaskClassifier.classify(clean) !in listOf(TaskType.CHAT, TaskType.GENERAL)) return false
        if (clean.length in 8..220 && !isLowValueGroupReply(clean, emptyList())) return true
        return clean.contains("？") || clean.contains("?")
    }

    private fun shouldInviteMultipleGroupVoices(text: String): Boolean {
        val lowered = text.trim().lowercase()
        if (lowered.isBlank()) return false
        return listOf(
            "你们", "大家", "所有人", "都说说", "一起聊", "群里", "各位",
            "怎么看", "有什么想法", "给点建议", "投票", "brainstorm", "讨论",
        ).any { lowered.contains(it) }
    }

    private fun shouldContinueGroupThread(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        val lowered = clean.lowercase()
        if (isLowValueGroupReply(clean, emptyList())) return false
        if (isOrganicGroupTrigger(clean)) return true
        return listOf(
            "怎么看", "你们觉得", "大家觉得", "谁来", "有没有", "可以聊",
            "展开讲", "换个角度", "还有谁", "还有没有", "大家说说",
        ).any { lowered.contains(it) } ||
            (clean.contains("？") || clean.contains("?")) &&
                listOf("你们", "大家", "谁", "怎么", "为什么", "要不要", "有没有").any { lowered.contains(it) }
    }

    private fun shouldRequireGroupReaction(triggerText: String, chainDepth: Int, reactorIndex: Int): Boolean {
        if (chainDepth <= 1) return false
        if (reactorIndex > 0) return false
        return shouldContinueGroupThread(triggerText)
    }

    private fun isLowValueGroupReply(text: String, attachments: List<SkillAttachment>): Boolean {
        val clean = text.trim()
        if (attachments.isNotEmpty()) return false
        if (clean.isBlank()) return true
        val normalized = clean
            .replace(Regex("[\\s，。,.!！?？~～…]+"), "")
            .lowercase()
        val generic = listOf(
            "我补充一句", "补充一下", "我也补充", "我也来说两句", "我接一下",
            "我也觉得", "我同意", "确实", "有道理", "说得对", "哈哈", "笑死",
            "可以", "不错", "挺好", "没错", "俺也一样", "先这样",
        )
        if (generic.any { normalized == it || normalized.startsWith(it) && normalized.length <= it.length + 10 }) {
            return true
        }
        val meaningfulChars = normalized.count { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }
        if (meaningfulChars <= 8) return true
        val fillerHits = listOf("补充", "接一句", "我也", "确实", "有道理").count { normalized.contains(it) }
        return fillerHits >= 2 && meaningfulChars < 28
    }

    /** Triggers a random member to proactively start a conversation — call when group opens or chat is cold. */
    fun sparkGroupChat() {
        val group = _uiState.value.openGroup ?: return
        if (_uiState.value.groupRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isEmpty()) return@launch
            groupChatStopped = false
            _uiState.update { it.copy(groupRunning = true) }
            allMembers.shuffled().take(minOf(2, allMembers.size)).forEachIndexed { index, role ->
                launchGroupAgentTurn(
                    group = group,
                    role = role,
                    allMembers = allMembers,
                    delayMs = if (index == 0) 0L else (1200L..2600L).random(),
                    chainDepth = if (index == 0) 6 else 4,
                    triggerText = "群聊冷启动",
                    requireResponse = true,
                )
            }
        }
    }

    // Mini ReAct loop: buffers internally; no streaming text to UI (only typing indicator shown).
    private suspend fun runGroupAgentTurn(
        role: Role,
        baseMessages: List<Message>,
        taskType: TaskType,
        memoryContext: String = "",
        maxSkillCalls: Int = 3,
        requireResponse: Boolean = false,
        onToolStart: (skillId: String, params: Map<String, Any>) -> Unit = { _, _ -> },
        onToolEnd: (output: String) -> Unit = {},
    ): GroupTurnResult {
        val groupMetas = TaskToolPolicy.select(registry, taskType, role.forcedSkillIds, memoryContext)
            .let { metas ->
                if (taskType in listOf(TaskType.CHAT, TaskType.GENERAL)) {
                    (metas + listOfNotNull(registry.get("role_manager")?.meta)).distinctBy { it.id }
                } else {
                    metas
                }
            }
        val tools = groupMetas.map { m ->
            ToolDefinition(
                name = m.id,
                description = m.description,
                parameters = ToolParameters(
                    properties = m.parameters.associate { p -> p.name to ToolProperty(p.type, p.description) },
                    required = m.parameters.filter { it.required }.map { it.name },
                ),
            )
        }

        val messages = baseMessages.toMutableList()
        var finalText = ""
        val attachments = mutableListOf<SkillAttachment>()

        repeat(maxSkillCalls + 1) { iteration ->
            if (groupChatStopped) return GroupTurnResult(finalText, attachments)
            var accumulated = ""
            val resp = runCatching {
                withTimeoutOrNull(35_000L) {
                    llm.chat(ChatRequest(
                        messages = messages,
                        tools = if (iteration < maxSkillCalls) tools else emptyList(),
                        stream = true,
                        onToken = { tok -> accumulated += tok },  // buffer locally, not shown during inference
                        onThinkToken = null,                      // discard thinking completely
                    ))
                }
            }.getOrElse {
                return GroupTurnResult(finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "[PASS]" }, attachments)
            }
                ?: return GroupTurnResult(finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "[PASS]" }, attachments)

            if (resp.toolCall == null) {
                finalText = accumulated.ifBlank { resp.content ?: "" }
                if (requireResponse && (finalText.isBlank() || finalText.trim().equals("[PASS]", ignoreCase = true))) {
                    finalText = fallbackGroupReply(role, baseMessages)
                }
                val cleanedAttachments = normalizeGroupTurnAttachments(attachments)
                val autoSticker = if (cleanedAttachments.none { it.isStickerLikeAttachment() || it is SkillAttachment.ImageData }) {
                    maybeCreateGroupStickerAttachment(finalText, role.id)
                } else {
                    null
                }
                return GroupTurnResult(
                    finalText,
                    normalizeGroupTurnAttachments(if (autoSticker != null) cleanedAttachments + autoSticker else cleanedAttachments),
                )
            }

            val tc = resp.toolCall
            onToolStart(tc.skillId, tc.params)
            val skillResult = registry.get(tc.skillId)
                ?.let { runCatching { it.execute(tc.params) }.getOrElse { e -> com.mobileclaw.skill.SkillResult(false, "Error: ${e.message}") } }
                ?: com.mobileclaw.skill.SkillResult(false, "Skill '${tc.skillId}' not found")
            onToolEnd(skillResult.output.take(3000))
            (skillResult.data as? SkillAttachment)?.let { attachment ->
                if (attachment.isStickerLikeAttachment() || attachment is SkillAttachment.ImageData) {
                    attachments.removeAll { it.isStickerLikeAttachment() || it is SkillAttachment.ImageData }
                }
                attachments += attachment
                rememberGroupStickerAttachment(attachment)
            }
            skillResult.imageBase64?.takeIf { it.isNotBlank() }?.let {
                attachments += SkillAttachment.ImageData(it, prompt = "Generated by ${tc.skillId}")
            }

            messages.add(Message(role = "assistant", content = accumulated.ifBlank { null }, toolCalls = listOf(tc)))
            messages.add(Message(role = "tool", content = skillResult.output.take(3000), toolCallId = tc.id))
        }

        return GroupTurnResult(finalText.ifBlank { if (requireResponse) fallbackGroupReply(role, baseMessages) else "" }, normalizeGroupTurnAttachments(attachments))
    }

    private fun fallbackGroupReply(role: Role, baseMessages: List<Message>): String {
        val latestUser = baseMessages
            .asReversed()
            .firstOrNull { it.role == "user" && !it.content.orEmpty().startsWith("[系统]:") }
            ?.content
            ?.substringAfter("]:", "")
            ?.trim()
            ?.take(120)
            .orEmpty()
        return when {
            latestUser.contains("?", ignoreCase = true) || latestUser.contains("？") ->
                "我先接一下：这个问题我理解是在问「$latestUser」。我的看法是可以先把目标拆清楚，再看要不要让其他人补充。"
            latestUser.isNotBlank() ->
                "我来接一句：$latestUser。这个点我觉得可以继续往下聊，我先说我的角度。"
            else ->
                "我先开个头：我在，咱们继续聊。"
        }
    }

    private suspend fun maybeCreateGroupStickerAttachment(text: String, roleId: String): SkillAttachment.FileData? {
        val query = stickerQueryForText(text) ?: return null
        return withTimeoutOrNull<SkillAttachment.FileData?>(2500L) {
            runCatching {
                val entries = ChineseBqbStickerRepository.search(app, query, limit = 48)
                    .let { list ->
                        if (list.isEmpty()) list else {
                            val salt = kotlin.math.abs((roleId + query + System.currentTimeMillis() / 30_000L).hashCode())
                            list.drop(salt % minOf(list.size, 7)) + list.take(salt % minOf(list.size, 7))
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

    private fun normalizeGroupTurnAttachments(attachments: List<SkillAttachment>): List<SkillAttachment> {
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

    private fun stickerQueryForText(text: String): String? {
        val clean = text.trim()
        if (clean.length !in 1..90) return null
        val lowered = clean.lowercase()
        val candidates = listOf(
            listOf("哈哈", "笑死", "笑", "绷不住", "乐", "hh", "233") to "哈哈",
            listOf("牛", "太强", "厉害", "666", "绝了") to "牛",
            listOf("离谱", "逆天", "破防", "无语", "尴尬") to "无语",
            listOf("摸鱼", "开摆", "摆烂") to "摸鱼",
            listOf("谢谢", "感谢", "感恩") to "谢谢",
            listOf("庆祝", "恭喜", "赢", "成功") to "庆祝",
            listOf("安慰", "抱抱", "难过", "哭") to "安慰",
            listOf("生气", "气", "怒") to "生气",
        )
        return candidates.firstOrNull { (triggers, _) -> triggers.any { lowered.contains(it) } }?.second
    }

    private fun buildGroupSystemPrompt(
        role: Role,
        groupName: String,
        allMembers: List<Role>,
        memoryPrompt: String = "",
    ): String = buildString {
        // ① 角色身份——最重要，放最前面，让模型先"入戏"
        appendLine(str(R.string.vm_ff3706))
        appendLine(role.name)
        if (role.description.isNotBlank()) appendLine(role.description)
        if (role.systemPromptAddendum.isNotBlank()) {
            appendLine()
            appendLine(role.systemPromptAddendum.trim())
        }
        appendLine()

        // ② 场景
        appendLine(str(R.string.vm_198ed3))
        appendLine("你正在群「$groupName」里发微信消息。其他成员：")
        allMembers.forEach { m ->
            if (m.id != role.id) appendLine("  ${m.name}：${m.description.take(40)}")
        }
        appendLine(str(R.string.vm_090646))
        appendLine()

        if (memoryPrompt.isNotBlank()) {
            appendLine(memoryPrompt)
            appendLine("群聊发言、是否使用工具、是否沉默、表情包和气泡风格，都必须先参考这层记忆。")
            appendLine()
        }

        // ③ 发消息风格
        appendLine(str(R.string.vm_d9e95d))
        appendLine("• 你就是 ${role.name}，用你自己的说话方式，不要跑偏。")
        appendLine(str(R.string.vm_1acb86))
        appendLine(str(R.string.vm_93c334))
        appendLine("• 你的群聊气泡是你的个人装扮。你可以自己选择或生成气泡主题，用户不需要手动编辑。")
        appendLine("• 工具权限由当前任务模式决定；普通群聊只允许轻量表达工具和自己的气泡样式更新，不要搜索网页、操作手机或生成文件，除非用户明确提出对应任务。")
        appendLine("• 如果你还没有满意的气泡，或想让自己更有辨识度，可以调用 role_manager(action=update, id=\"${role.id}\", bubble_style_json={...}) 只更新自己的气泡样式。")
        appendLine("• 默认可以使用原生气泡 renderer=native；如果你想做复杂字体、局部元素、CSS 动画、可爱装饰或不想被 Markdown 分段影响，也可以直接选择 renderer=html。")
        appendLine("• 气泡主题可以包含 renderer、preset、emotion、backgroundColor、backgroundImage、gradient、textColor、borderColor、accentColor、radiusDp、radiusTopStartDp、radiusTopEndDp、radiusBottomEndDp、radiusBottomStartDp、tail、pattern、decoration、decorationText、decorationPosition、decorationAnimation、decorationSizeDp、decorations、animation、fontFamily、fontWeight、textAnimation、fontSizeSp、lineHeightSp、paddingHorizontalDp、paddingVerticalDp、shadow、shadowColor、shadowAlpha、shadowElevationDp、shadowOffsetXDp、shadowOffsetYDp、imageMode。")
        appendLine("• HTML 气泡是开放表达通道，可以配置 htmlTemplate、htmlHeightDp、htmlAllowJs、htmlAllowNetwork、htmlTransparent；适合自定义字体、CSS 动画、局部装饰和多元素气泡。")
        appendLine("• decoration 是旧版单个小装饰；更推荐 decorations 数组，让你自己控制多个小装饰。每个装饰可包含 type/text/position/x/y/animation/sizeDp/color/alpha。")
        appendLine("• decorations.type 可用 dot/sparkle/heart/star/moon/badge/text/firework/glimmer/aurora；position 可用 top_start/top_center/top_end/center_start/center_end/bottom_start/bottom_center/bottom_end/tail。")
        appendLine("• decorations.x/y 是 0..1 的气泡相对坐标，不是像素绝对坐标；例如 x=0.92,y=0.10 表示靠右上角。装饰允许轻微溢出气泡，但要和圆角/文字保持距离，优先放在四角、边缘或尾巴附近，不要挡住正文。")
        appendLine("• decorations.animation 可用 none/pulse/float/sparkle/orbit/firework/glimmer/aurora；鼓励用小而可爱的局部动效，例如花火、灵光、星点、极光描边，但不要让整坨气泡大幅晃动。")
        appendLine("• 圆角是你的设计选择：可以用 radiusTopStartDp/radiusTopEndDp/radiusBottomEndDp/radiusBottomStartDp 单独设置四角；如果视觉上不齐，可以主动更新为统一半径。")
        appendLine("• animation 可用 none/pulse/breath/float/sparkle/shake/pop/tilt/bounce，但应保持克制，优先用局部小装饰和文字动画，不要让整坨气泡大幅移动。")
        appendLine("• emotion 可用 neutral/happy/sad/angry/shy/cool/excited/sleepy/love；fontWeight 可用 light/regular/medium/semibold/bold/extrabold/heavy/black；textAnimation 可用 none/fade/pop/breath/shimmer/typewriter/marquee/wave/glow/neon/flash/jelly。")
        appendLine("• 当你的回复有明显情绪时，可以先用 role_manager 轻量更新自己的原生气泡状态，例如开心用 emotion=happy + decoration=sparkle，吐槽/无语用 emotion=cool 或 sleepy，安慰用 love/shy，庆祝用 excited。")
        appendLine("• 气泡表情适合表达当轮心情；表情包适合更强的梗、斗图、反应和娱乐感。好笑、吐槽、庆祝、尴尬、无语、感谢、安慰时要更主动使用表情包。")
        appendLine("• 只在需要表达人格时调整气泡；不要每次发言都修改主题。")
        appendLine()

        // ④ 行为规则
        appendLine(str(R.string.vm_689bf5))
        appendLine(str(R.string.vm_427e4c))
        appendLine(str(R.string.vm_a8c3ee))
        appendLine(str(R.string.vm_704d6a))
        appendLine(str(R.string.vm_e1d388))
        appendLine(str(R.string.vm_abc7c8))
        appendLine(str(R.string.vm_8f6718))
        appendLine("• 群聊不是抢答，但也不是客服单轮回答。自然闲聊时可以接话、抛梗、转话题、点名别人，让群有生命力。")
        appendLine("• 不要为了存在感补空话。禁止只说“我补充一句/我也觉得/确实/有道理/哈哈/不错/接一下”。")
        appendLine("• 任务型问题如果已经回答完整，你应该安静；闲聊场景则可以用自己的性格继续推进话题。")
        appendLine("• 如果你使用工具生成了图片、文件、网页或搜索结果，可以把这些结果作为附件发到群里。")
        appendLine("• 表情包是你的群聊表达方式之一，不是只有用户明确要求才用。发言前先判断：你的这句话是否有明确情绪、梗、反应或斗图价值。")
        appendLine("• 当你的回复明显适合表情包强化时，可以调用 sticker_bqb(action=\"search\", query=\"简短情绪词\")，例如 哈哈、笑死、牛、离谱、尴尬、无语、摸鱼、生气、谢谢、安慰、庆祝。")
        appendLine("• 表情包必须和你要表达的内容匹配；每轮最多一个，不要连续刷屏，不要和其他 AI 重复同一张。系统也会按你的文字情绪自动补一个合适表情，所以不确定时直接发文字即可。")
        appendLine("• 如果已经发送表情包，文字要短，不要解释“我发送了一个附件/表情包”。严肃任务、长任务结果、专业说明和安全相关内容少用或不用。")
        appendLine("• 任务型请求必须做完再发言；不要做到一半就邀请别人接话。")
    }

    private fun shouldUseStickerAwareChat(goal: String): Boolean {
        val text = goal.lowercase()
        if (text.length > 120) return false
        val triggers = listOf(
            "表情", "表情包", "斗图", "哈哈", "hh", "笑死", "笑", "绷不住", "乐",
            "牛", "666", "离谱", "尴尬", "无语", "摸鱼", "生气", "开心", "谢谢",
            "感谢", "安慰", "难过", "哭", "庆祝", "太强", "太菜", "绝了", "破防",
            "吐槽", "调侃", "整活", "尬", "惊了", "懵"
        )
        return triggers.any { text.contains(it) }
    }

    private fun parseMentions(text: String): List<String> =
        Regex("@([\\w\\u4e00-\\u9fff·]+)").findAll(text).map { it.groupValues[1] }.toList()

    private fun groupAttachmentPrompt(attachment: SkillAttachment): String = when (attachment) {
        is SkillAttachment.ImageData -> str(R.string.group_prompt_image, attachment.prompt ?: "image")
        is SkillAttachment.FileData -> str(R.string.group_prompt_file, attachment.name, attachment.mimeType, attachment.sizeBytes)
        is SkillAttachment.HtmlData -> str(R.string.group_prompt_html, attachment.title, attachment.path)
        is SkillAttachment.WebPage -> str(R.string.group_prompt_web, attachment.title, attachment.url)
        is SkillAttachment.SearchResults -> str(R.string.group_prompt_search, attachment.query, attachment.pages.size)
        is SkillAttachment.FileList -> str(R.string.group_prompt_file_list, attachment.files.size)
        is SkillAttachment.AccessibilityRequest -> str(R.string.group_prompt_permission, attachment.skillName)
        is SkillAttachment.ActionCard -> "操作确认卡片：${attachment.title}"
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
            _uiState.update { it.copy(profileLoading = true) }
            val episodes = runCatching { app.database.episodeDao().recent(limit = 20) }.getOrDefault(emptyList())
            val convCount = runCatching { conversationMemory.messageCount() }.getOrDefault(0)
            val facts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
            val semanticFacts = runCatching { app.semanticMemory.allFactsIncludingDisabled() }.getOrDefault(emptyList())
            _uiState.update { it.copy(
                profileFacts = facts,
                semanticFacts = semanticFacts,
                recentEpisodes = episodes,
                conversationCount = convCount,
                profileLoading = false,
            ) }
        }
    }

    fun triggerProfileExtraction() {
        viewModelScope.launch {
            _uiState.update { it.copy(profileExtracting = true) }
            val episodes = runCatching { app.database.episodeDao().recent(limit = 20) }.getOrDefault(emptyList())
            val convJob = launch { runCatching { profileExtractor.extractAndUpdate("", "") } }
            val epJob   = launch { runCatching { profileExtractor.extractFromEpisodes(episodes) } }
            convJob.join(); epJob.join()
            val facts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
            val semanticFacts = runCatching { app.semanticMemory.allFactsIncludingDisabled() }.getOrDefault(emptyList())
            _uiState.update { it.copy(profileFacts = facts, semanticFacts = semanticFacts, profileExtracting = false) }
        }
    }

    // ── AI Profile Analysis ──────────────────────────────────────────────────

    fun generatePersonalitySummary() {
        if (_uiState.value.personalitySummaryLoading) return
        val facts = _uiState.value.profileFacts.filter { it.key.startsWith("profile.") }
        if (facts.isEmpty()) return
        _uiState.update { it.copy(personalitySummaryLoading = true, personalitySummary = "") }
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
            _uiState.update { it.copy(personalitySummary = summary, personalitySummaryLoading = false) }
        }
    }

    private suspend fun fetchDimensionQuiz(dimensionId: String, dimensionTitle: String): List<AiQuizQuestion> {
        val facts = _uiState.value.profileFacts
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
        if (_uiState.value.dimensionQuizLoading == dimensionId) return
        _uiState.update { it.copy(dimensionQuizLoading = dimensionId) }
        viewModelScope.launch(Dispatchers.IO) {
            val questions = fetchDimensionQuiz(dimensionId, dimensionTitle)
            _uiState.update { state ->
                state.copy(
                    dimensionQuizzes  = state.dimensionQuizzes + (dimensionId to questions),
                    dimensionQuizLoading = null,
                )
            }
        }
    }

    fun prewarmAllDimensionQuizzes(dimensions: List<ProfileDimension>) {
        val todo = dimensions.filter { it.id !in _uiState.value.dimensionQuizzes }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (dim in todo) {
                if (_uiState.value.dimensionQuizzes.containsKey(dim.id)) continue
                val questions = fetchDimensionQuiz(dim.id, dim.title)
                _uiState.update { state ->
                    state.copy(dimensionQuizzes = state.dimensionQuizzes + (dim.id to questions))
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

    fun showSettings(show: Boolean) = navigate(if (show) AppPage.SETTINGS else AppPage.CHAT)
    fun showSkillManager(show: Boolean) = navigate(if (show) AppPage.SKILLS else AppPage.CHAT)

    fun saveConfig(snapshot: ConfigSnapshot) {
        viewModelScope.launch {
            val oldLanguage = config.language
            config.update(snapshot)
            if (snapshot.language != oldLanguage) {
                _languageChanged.emit(snapshot.language)
            }
            navigate(AppPage.CHAT)
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
                if (it.id == snap.activeGatewayId || (snap.activeGatewayId == null && it == snap.gateways.firstOrNull()))
                    it.copy(model = model)
                else it
            }
            config.update(snap.copy(gateways = updatedGateways, localModelEnabled = false, localNativeOnly = false))
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    fun setLocalModelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val snap = config.snapshot()
            config.update(snap.copy(localModelEnabled = enabled, localNativeOnly = if (enabled) snap.localNativeOnly else false))
        }
    }

    fun setLocalNativeOnly(enabled: Boolean) {
        viewModelScope.launch {
            val snap = config.snapshot()
            config.update(snap.copy(localNativeOnly = enabled, localModelEnabled = if (enabled) true else snap.localModelEnabled))
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
                runCatching { OpenAiGateway(config).fetchModels() }.getOrDefault(emptyList())
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
        val all = registry.allWithEffectiveLevel()
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

    private fun shouldUseScheduledRoleForRun(
        goal: String,
        taskType: TaskType,
        currentRole: Role,
        scheduledRole: Role,
    ): Boolean {
        if (scheduledRole.id == currentRole.id) return true
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL)) return true
        val text = goal.lowercase()
        val explicitRoleMention = text.contains(scheduledRole.id.lowercase()) ||
            scheduledRole.name.isNotBlank() && text.contains(scheduledRole.name.lowercase())
        if (explicitRoleMention) return true
        return false
    }

    private fun requiresUserExecutionConfirmation(taskType: TaskType, goal: String): Boolean {
        if (goal.startsWith(CONFIRM_TASK_PREFIX)) return false
        return taskType in setOf(TaskType.PHONE_CONTROL, TaskType.VPN_CONTROL)
    }

    private fun requestTaskExecutionConfirmation(goal: String, taskType: TaskType) {
        if (taskType == TaskType.PHONE_CONTROL && !ClawAccessibilityService.isEnabled()) {
            pendingAccessibilityTaskGoal = goal
            val card = SkillAttachment.ActionCard(
                title = "需要无障碍权限后才能操作手机",
                body = "这个任务会操作你的手机界面。请先开启 MobileClaw 无障碍服务；开启后回到这里继续同一个流程，不会再重复弹角色或执行确认。\n\n$goal",
                tone = "phone",
                actions = listOf(
                    SkillAttachment.ActionCard.Action("打开无障碍", "$OPEN_ACCESSIBILITY_PREFIX$goal", "primary"),
                    SkillAttachment.ActionCard.Action("已开启并继续", "$CONFIRM_ACCESSIBILITY_TASK_PREFIX$goal", "secondary"),
                    SkillAttachment.ActionCard.Action("取消", CANCEL_CONFIRMATION_TEXT, "secondary"),
                ),
            )
            appendConfirmationExchange(goal, card)
            return
        }
        val title = when (taskType) {
            TaskType.PHONE_CONTROL -> "这需要操作你的手机界面。"
            TaskType.VPN_CONTROL -> "这需要修改 VPN/代理状态。"
            else -> "这需要执行敏感操作。"
        }
        val card = SkillAttachment.ActionCard(
            title = title,
            body = "请确认是否继续执行完整流程。确认后，AI 会在本次任务内自行选择合适角色和工具，不再为同一流程反复弹确认。\n\n$goal",
            tone = if (taskType == TaskType.PHONE_CONTROL) "phone" else "warning",
            actions = listOf(
                SkillAttachment.ActionCard.Action("确认执行", "$CONFIRM_TASK_PREFIX$goal", "primary"),
                SkillAttachment.ActionCard.Action("取消", CANCEL_CONFIRMATION_TEXT, "secondary"),
            ),
        )
        appendConfirmationExchange(goal, card)
    }

    private fun requestRoleSwitchConfirmation(goal: String, role: Role) {
        pendingRoleSwitchTaskGoal = goal
        val card = SkillAttachment.ActionCard(
            title = "切换到 ${role.name}",
            body = "切换后会改变当前 AI 的人格、模型或可用能力。请确认是否切换。",
            tone = "role",
            actions = listOf(
                SkillAttachment.ActionCard.Action("确认切换并继续", "$CONFIRM_ROLE_PREFIX${role.id}::$goal", "primary"),
                SkillAttachment.ActionCard.Action("取消", CANCEL_CONFIRMATION_TEXT, "secondary"),
            ),
        )
        appendConfirmationExchange(goal, card)
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

    private fun isAccessibilityResumeText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in setOf("已开启", "已经开了", "开了", "无障碍已开启", "无障碍开了", "enabled") ||
            normalized.contains("已开") ||
            normalized.contains("已经开启") ||
            normalized.contains("无障碍开")
    }

    private fun inferExplicitRoleSwitchTarget(goal: String): Role? {
        val text = goal.lowercase()
        if (!text.anyContainsLocal("切换角色", "换角色", "切到", "切换到", "switch role")) return null
        val roles = _uiState.value.availableRoles + Role.BUILTINS
        return roles.distinctBy { it.id }.firstOrNull { role ->
            text.contains(role.id.lowercase()) ||
                (role.name.isNotBlank() && text.contains(role.name.lowercase()))
        }
    }

    private fun inferFollowUpTaskType(goal: String, classifiedTaskType: TaskType): TaskType? {
        if (classifiedTaskType != TaskType.GENERAL) return null
        if (!isContextualFollowUp(goal)) return null
        val recent = effectiveContextMessages(limit = 6)
        if (_uiState.value.currentRole.id == "phone_operator" && isPhoneContinuationContext(recent)) {
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

    private data class ContextualTaskIntent(
        val classificationGoal: String,
        val taskTypeOverride: TaskType? = null,
        val aiPage: com.mobileclaw.ui.aipage.AiPageDef? = null,
        val miniApp: com.mobileclaw.app.MiniApp? = null,
        val fileAttachment: SkillAttachment.FileData? = null,
        val htmlAttachment: SkillAttachment.HtmlData? = null,
    )

    private fun resolveContextualTaskIntent(
        goal: String,
        hasImage: Boolean,
        hasFile: Boolean,
    ): ContextualTaskIntent {
        if (hasImage || hasFile) return ContextualTaskIntent(goal)
        val text = goal.lowercase()
        val recentArtifact = inferRecentFileContextTarget(goal)
        if (recentArtifact != null && !text.anyContainsLocal("页面", "原生", "native", "aipage", "ai page", "ui", "app", "miniapp", "mini app", "小应用", "小程序", "应用", "程序")) {
            return recentFileContextIntent(goal, recentArtifact)
        }
        val aiPage = inferAiPageContextTarget(goal)
        if (aiPage != null) {
            return ContextualTaskIntent(
                classificationGoal = "$goal\n\n[resolved_context: update existing AI Native Page ${aiPage.id}]",
                taskTypeOverride = TaskType.APP_BUILD,
                aiPage = aiPage,
            )
        }

        val miniApp = inferMiniAppContextTarget(goal)
        if (miniApp != null) {
            return ContextualTaskIntent(
                classificationGoal = "$goal\n\n[resolved_context: update existing MiniAPP ${miniApp.id}]",
                taskTypeOverride = TaskType.APP_BUILD,
                miniApp = miniApp,
            )
        }

        if (recentArtifact != null) {
            return recentFileContextIntent(goal, recentArtifact)
        }

        return ContextualTaskIntent(goal)
    }

    private fun recentFileContextIntent(goal: String, recentArtifact: SkillAttachment): ContextualTaskIntent {
        val taskType = when {
            recentArtifact is SkillAttachment.HtmlData && goal.lowercase().anyContainsLocal("页面", "html", "网页", "预览", "样式", "布局") -> TaskType.APP_BUILD
            else -> TaskType.FILE_CREATE
        }
        return ContextualTaskIntent(
            classificationGoal = "$goal\n\n[resolved_context: continue working with recent file/html attachment]",
            taskTypeOverride = taskType,
            fileAttachment = recentArtifact as? SkillAttachment.FileData,
            htmlAttachment = recentArtifact as? SkillAttachment.HtmlData,
        )
    }

    private fun applyContextualTaskConstraints(
        effectiveGoal: String,
        intent: ContextualTaskIntent,
        taskType: TaskType,
    ): String = buildString {
        append(effectiveGoal)
        if (intent.aiPage != null && taskType == TaskType.APP_BUILD) {
            append("\n\n[上下文约束]\n用户是在修改最近的 AI Native Page，不要创建 HTML，也不要创建新页面。请先调用 ui_builder(action=get, id=\"${intent.aiPage.id}\") 读取当前定义，再调用 ui_builder(action=update, id=\"${intent.aiPage.id}\", ...) 更新它，最后 ui_builder(action=open, id=\"${intent.aiPage.id}\") 打开。")
        }
        if (intent.miniApp != null && taskType == TaskType.APP_BUILD) {
            append("\n\n[上下文约束]\n用户是在修改最近的 MiniAPP，不要新建应用。请先调用 app_manager(action=list) 或读取已有上下文确认，再调用 app_manager(action=update, id=\"${intent.miniApp.id}\", ...) 更新它，最后 app_manager(action=open, id=\"${intent.miniApp.id}\") 打开。")
        }
        if (intent.fileAttachment != null && taskType == TaskType.FILE_CREATE) {
            append("\n\n[上下文约束]\n用户是在继续处理最近的文件：path=${intent.fileAttachment.path}, name=${intent.fileAttachment.name}, mime=${intent.fileAttachment.mimeType}。优先读取或更新这个文件，不要无关新建。")
        }
        if (intent.htmlAttachment != null) {
            append("\n\n[上下文约束]\n用户是在继续处理最近的 HTML 结果：path=${intent.htmlAttachment.path}, title=${intent.htmlAttachment.title}。如需修改，基于这个结果继续，不要切换到无关页面或应用。")
        }
    }

    private fun buildPriorContext(
        goal: String,
        taskType: TaskType = TaskType.GENERAL,
        intent: ContextualTaskIntent = ContextualTaskIntent(goal),
        includeMemory: Boolean = true,
        includeRecentMessages: Boolean = true,
    ): String {
        val msgs = effectiveContextMessages(limit = 12)
        val userMemoryContext = if (includeMemory) buildUserMemoryContextForPrompt(goal, taskType) else ""
        val artifactContext = buildArtifactContext(intent)
        if (!includeRecentMessages || msgs.isEmpty()) {
            return listOf(userMemoryContext, artifactContext).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        val recent = msgs.takeLast(10)
        val lines = recent.map { msg ->
            val attachmentSummary = summarizeAttachmentsForContext(msg.attachments)
                .takeIf { it.isNotBlank() }
                ?.let { "\n  attachments: $it" }
                .orEmpty()
            val raw = when (msg.role) {
                MessageRole.USER -> "User: ${msg.text}$attachmentSummary"
                MessageRole.AGENT -> {
                    val roleName = msg.senderRoleName.ifBlank { "Agent" }
                    "$roleName: ${msg.text}$attachmentSummary"
                }
            }
            // Truncate very long individual messages (e.g. code blocks, agent summaries)
            val cleaned = raw
                .replace(Regex("```[\\s\\S]*?```"), "[code/file content omitted]")
                .replace(Regex("\\n{3,}"), "\n\n")
            if (cleaned.length > 260) cleaned.take(260) + "…" else cleaned
        }
        // Hard cap on total prior context size
        val full = lines.joinToString("\n")
        val capped = if (full.length > 1800) full.takeLast(1800) else full
        val recentContext = if (capped.isNotBlank()) {
            "## Relevant Chat Records Before Current User Message\n$capped\n\nCurrent user request: ${goal.take(220)}"
        } else ""
        return listOf(userMemoryContext, artifactContext, recentContext).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun buildStructuredDirectChatMessages(
        sessionId: String,
        systemPrompt: String,
        currentGoal: String,
        imageBase64: String? = null,
    ): List<Message> {
        val history = _uiState.value.sessionStates[sessionId]
            ?.messages
            .orEmpty()
            .dropLast(1)
            .filter { it.text.isNotBlank() || it.imageBase64 != null || it.attachments.isNotEmpty() }
            .takeLast(12)
            .mapNotNull { msg ->
                val text = buildDirectChatHistoryText(msg)
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

    private fun buildDirectChatHistoryText(msg: ChatMessage): String {
        val base = msg.text
            .replace(Regex("```[\\s\\S]*?```"), "[code/file content omitted]")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val rolePrefix = if (msg.role == MessageRole.AGENT && msg.senderRoleName.isNotBlank()) {
            "[role=${msg.senderRoleName}] "
        } else ""
        val attachmentSummary = summarizeAttachmentsForContext(msg.attachments)
            .takeIf { it.isNotBlank() }
            ?.let { "\n[attachments: $it]" }
            .orEmpty()
        val text = "$rolePrefix$base$attachmentSummary".trim()
        return if (text.length > 900) text.take(900) + "…" else text
    }

    private fun buildUserMemoryContextForPrompt(goal: String, taskType: TaskType): String =
        runCatching {
            val state = _uiState.value
            val semanticFacts = state.semanticFacts
            if (semanticFacts.isNotEmpty()) {
                memoryContextBuilder.buildFromSnapshots(
                    userMessage = goal,
                    taskType = taskType,
                    userConfigEntries = state.userConfigEntries,
                    facts = semanticFacts,
                ).toPrompt()
            } else {
                memoryContextBuilder.buildFromSnapshots(
                    userMessage = goal,
                    taskType = taskType,
                    userConfigEntries = state.userConfigEntries,
                    facts = state.profileFacts,
                ).toPrompt()
            }
        }.getOrDefault("")

    private fun buildArtifactContext(intent: ContextualTaskIntent): String {
        val sections = mutableListOf<String>()
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

    private fun buildAiPageArtifactContext(activeAiPage: com.mobileclaw.ui.aipage.AiPageDef? = null): String {
        val pages = app.aiPageStore.getAll().take(5)
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

    private fun buildMiniAppArtifactContext(activeMiniApp: com.mobileclaw.app.MiniApp? = null): String {
        val apps = runCatching { app.miniAppStore.all().take(5) }.getOrDefault(emptyList())
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

    private fun summarizeAttachmentsForContext(attachments: List<SkillAttachment>): String {
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

    private fun effectiveContextMessages(limit: Int): List<ChatMessage> =
        _uiState.value.currentRunState.messages
            .asReversed()
            .filterNot { it.isContextNoiseMessage() }
            .take(limit)
            .asReversed()

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

    private fun inferAiPageContextTarget(goal: String): com.mobileclaw.ui.aipage.AiPageDef? {
        val pages = app.aiPageStore.getAll()
        if (pages.isEmpty()) return null
        val text = goal.lowercase()
        if (isMobileClawInternalChatTopic(text)) return null
        val explicit = pages.firstOrNull { page ->
            text.contains(page.id.lowercase()) ||
                page.title.isNotBlank() && text.contains(page.title.lowercase())
        }
        if (explicit != null) return explicit
        val refersToPrevious = text.contains("它") ||
            text.contains("这个页面") ||
            text.contains("这个ui") ||
            text.contains("这个原生") ||
            text.contains("这个 aipage") ||
            text.contains("刚才") ||
            text.contains("上面") ||
            text.contains("修改") ||
            text.contains("改下") ||
            text.contains("改一下") ||
            text.contains("调整") ||
            text.contains("优化") ||
            text.contains("美化") ||
            text.contains("完善") ||
            text.contains("更新") ||
            text.contains("继续") ||
            text.contains("接着") ||
            text.contains("别这样") ||
            text.contains("不是这样") ||
            text.contains("换成") ||
            text.contains("改成")
        val pageIntent = text.contains("页面") ||
            text.contains("原生") ||
            text.contains("native") ||
            text.contains("aipage") ||
            text.contains("ai page") ||
            text.contains("ui")
        val recentPageContext = currentConversationMentionsAiPage(pages)
        val shortFollowUp = text.length <= 30 && refersToPrevious
        return if (recentPageContext != null && (refersToPrevious && pageIntent || shortFollowUp)) recentPageContext else null
    }

    private fun inferMiniAppContextTarget(goal: String): com.mobileclaw.app.MiniApp? {
        val apps = runCatching { app.miniAppStore.all() }.getOrDefault(emptyList())
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
        val fileIntent = text.anyContainsLocal(
            "文件", "文档", "附件", "这个", "它", "上面", "刚才", "继续", "修改", "改下", "改一下",
            "优化", "更新", "打开", "保存", "导出", "ppt", "docx", "xlsx", "pdf", "csv", "markdown", "html",
        )
        if (!fileIntent) return null
        return effectiveContextMessages(limit = 8)
            .asReversed()
            .flatMap { it.attachments.asReversed() }
            .firstOrNull { it is SkillAttachment.FileData || it is SkillAttachment.HtmlData }
    }

    private fun currentConversationMentionsAiPage(pages: List<com.mobileclaw.ui.aipage.AiPageDef>): com.mobileclaw.ui.aipage.AiPageDef? {
        val recent = effectiveContextMessages(limit = 4)
        if (recent.isEmpty()) return null
        val text = recent.joinToString("\n") { messageContextText(it).lowercase() }
        val explicit = pages.firstOrNull { page ->
            (page.id.isNotBlank() && text.contains(page.id.lowercase())) ||
                (page.title.isNotBlank() && text.contains(page.title.lowercase()))
        }
        if (explicit != null) return explicit
        if (text.contains("ai native page") ||
            text.contains("原生页面") ||
            text.contains("ai页面") ||
            text.contains("aipage") ||
            text.contains("ui_builder")) {
            return pages.firstOrNull()
        }
        return null
    }

    private fun currentConversationMentionsMiniApp(apps: List<com.mobileclaw.app.MiniApp>): com.mobileclaw.app.MiniApp? {
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
            GenerateIconSkill(app, app.userConfig, app.miniAppStore, roleManager),
            GenerateDocumentSkill(app),
            GenerateVideoSkill(config, app, app.userConfig),
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
            // Dynamic config, model & role
            SwitchModelSkill(config),
            UserConfigSkill(userConfig, app.semanticMemory),
            switchRoleSkill,
            RoleManagerSkill(roleManager, roleRequests),
            // Session management
            SessionManagerSkill(database.sessionDao(), sessionRequests),
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
            // LAN console page editor (千人千面)
            com.mobileclaw.skill.builtin.ConsoleEditorSkill(consoleServer),
        ).forEach { registry.register(it) }
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
                type = runCatching { LogType.valueOf(o["type"]?.asString ?: "INFO") }.getOrDefault(LogType.INFO),
                text = o["text"]?.asString ?: "",
                skillId = o["skillId"]?.asString,
                imageBase64 = null, // stripped on save
                details = runCatching { o["details"]?.asJsonArray?.map { it.asString } ?: emptyList() }.getOrDefault(emptyList()),
            )
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
        senderRoleId = senderRoleId,
        senderRoleName = senderRoleName,
        senderRoleAvatar = senderRoleAvatar,
    )
}

private fun AgentEvent.toLogLine(): LogLine? = when (this) {
    is AgentEvent.Started      -> LogLine(LogType.INFO, "▶ $goal")
    is AgentEvent.Thinking     -> null
    is AgentEvent.ThinkingToken -> null
    is AgentEvent.SkillCalling -> LogLine(LogType.ACTION, friendlySkillDescription(skillId, params), skillId = skillId)
    is AgentEvent.Observation  -> LogLine(LogType.OBSERVATION, text.take(400), imageBase64 = imageBase64)
    is AgentEvent.Completed    -> LogLine(LogType.SUCCESS, summary)
    is AgentEvent.Error        -> LogLine(LogType.ERROR, message)
    is AgentEvent.Token        -> null
    is AgentEvent.ThinkingComplete -> null
    is AgentEvent.PlanCreated -> LogLine(LogType.THINKING, plan.toPrompt())
}

private val VISUAL_SKILL_IDS = setOf("screenshot", "see_screen", "bg_screenshot")

internal fun friendlySkillDescription(skillId: String, params: Map<String, Any>): String {
    fun p(key: String) = params[key]?.toString()?.trim() ?: ""
    return when (skillId) {
        "screenshot"     -> "📷 Taking screenshot"
        "bg_screenshot"  -> "📷 Capturing virtual display"
        "read_screen", "bg_read_screen" -> "🔍 Reading screen layout"
        "see_screen"     -> "👁 Analyzing screen content"
        "tap"            -> {
            val x = p("x"); val y = p("y"); val nid = p("node_id")
            when {
                nid.isNotBlank() -> "👆 Tapping UI element"
                x.isNotBlank()   -> "👆 Tapping at ($x, $y)"
                else             -> "👆 Tapping"
            }
        }
        "long_click"     -> "👆 Long pressing element"
        "scroll"         -> "📜 Scrolling ${p("direction").ifBlank { "down" }}"
        "input_text"     -> {
            val text = p("text").take(32)
            if (text.isNotBlank()) "⌨ Typing: \"$text\"" else "⌨ Entering text"
        }
        "navigate"       -> {
            val pkg = p("package_name"); val action = p("action")
            when {
                pkg.isNotBlank()    -> "📱 Opening $pkg"
                action.isNotBlank() -> "📱 $action"
                else                -> "📱 Navigating"
            }
        }
        "list_apps"      -> "📋 Listing installed apps"
        "web_search"     -> {
            val q = p("query").take(40)
            if (q.isNotBlank()) "🔍 Searching: $q" else "🔍 Web search"
        }
        "fetch_url"      -> {
            val url = p("url").take(50)
            if (url.isNotBlank()) "🌐 Loading: $url" else "🌐 Fetching URL"
        }
        "web_browse"     -> {
            val url = p("url").take(50)
            if (url.isNotBlank()) "🌐 Browsing: $url" else "🌐 Opening browser"
        }
        "web_content"    -> "📄 Reading page content"
        "web_js"         -> "⚡ Running page script"
        "bg_launch"      -> {
            val pkg = p("package_name")
            if (pkg.isNotBlank()) "🚀 Launching $pkg (background)" else "🚀 Background app launch"
        }
        "bg_stop"        -> "⏹ Stopping background app"
        "vd_setup"       -> "🔧 Testing virtual display"
        "memory"         -> {
            val action = p("action"); val key = p("key")
            when (action) {
                "set"    -> if (key.isNotBlank()) "💾 Remembering: $key" else "💾 Saving to memory"
                "get"    -> if (key.isNotBlank()) "🧠 Recalling: $key" else "🧠 Reading memory"
                "delete" -> "🗑 Forgetting: $key"
                "list"   -> "📋 Listing memories"
                else     -> "🧠 Memory operation"
            }
        }
        "shell"          -> {
            val cmd = p("command").take(30)
            if (cmd.isNotBlank()) "⚡ Running: $cmd" else "⚡ Executing command"
        }
        "permission"     -> "🔐 Checking permissions"
        "quick_skill"    -> "⚡ Creating new skill"
        "meta"           -> "🔧 Managing skills"
        "skill_check"    -> "🔍 Checking available skills"
        "skill_market"   -> "🛒 Browsing skill market"
        "generate_image" -> {
            val prompt = p("prompt").take(40)
            if (prompt.isNotBlank()) "🎨 Generating image: $prompt" else "🎨 Generating image"
        }
        "create_file"    -> {
            val name = p("filename")
            if (name.isNotBlank()) "📄 Creating file: $name" else "📄 Creating file"
        }
        "create_html"    -> {
            val title = p("title")
            if (title.isNotBlank()) "🌐 Creating HTML preview: $title" else "🌐 Creating HTML preview"
        }
        "ui_builder"     -> {
            val action = p("action"); val title = p("title").ifBlank { p("id") }
            when (action) {
                "create" -> if (title.isNotBlank()) "📄 Creating native page: $title" else "📄 Creating native page"
                "update" -> if (title.isNotBlank()) "📄 Updating native page: $title" else "📄 Updating native page"
                "open"   -> if (title.isNotBlank()) "📄 Opening native page: $title" else "📄 Opening native page"
                "list"   -> "📋 Listing native pages"
                else     -> "📄 Native page builder"
            }
        }
        "switch_model"   -> {
            val model = p("model")
            if (model.isNotBlank()) "🔄 Switching model: $model" else "🔄 Switching model"
        }
        "switch_role"    -> {
            val roleId = p("role_id")
            if (roleId.isNotBlank()) "🎭 Switching role: $roleId" else "🎭 Switching role"
        }
        "user_config"    -> {
            val action = p("action"); val key = p("key")
            when (action) {
                "set"    -> if (key.isNotBlank()) "⚙️ Config set: $key" else "⚙️ Saving config"
                "get"    -> if (key.isNotBlank()) "⚙️ Config get: $key" else "⚙️ Reading config"
                "delete" -> "⚙️ Config delete: $key"
                "list"   -> "⚙️ Listing config"
                else     -> "⚙️ User config"
            }
        }
        "app_manager"    -> {
            val action = p("action"); val title = p("title").ifBlank { p("id") }
            when (action) {
                "create" -> if (title.isNotBlank()) "📱 Creating MiniAPP program: $title" else "📱 Creating MiniAPP program"
                "update" -> if (title.isNotBlank()) "📱 Updating MiniAPP program: $title" else "📱 Updating MiniAPP program"
                "open"   -> if (title.isNotBlank()) "📱 Opening MiniAPP program: $title" else "📱 Opening MiniAPP program"
                "delete" -> "🗑 Deleting app: $title"
                "list"   -> "📋 Listing mini-apps"
                else     -> "📱 App manager"
            }
        }
        else             -> skillId
    }
}

// ── Smart Recommendations ─────────────────────────────────────────────────────

private fun buildSmartRecommendations(
    episodes: List<com.mobileclaw.memory.db.EpisodeEntity>,
    profileFacts: Map<String, String>,
    miniApps: List<com.mobileclaw.app.MiniApp>,
    recentUserMessages: List<String>,
): List<String> {
    val result = mutableListOf<String>()

    // 1. Mini-app continuations — most specific, highest priority
    miniApps.sortedByDescending { it.updatedAt }.take(2).forEachIndexed { idx, app ->
        result += if (idx == 0) str(R.string.quick_suggest_continue_app, app.icon, app.title)
                  else str(R.string.quick_suggest_add_feature, app.icon, app.title)
    }

    // 2. Emotional context from recent conversations
    detectEmotionSuggestion(recentUserMessages, profileFacts)?.let { result += it }

    // 3. Episode-based continuations — skip app-creation goals
    val appKeywords = listOf("app", str(R.string.drawer_apps), str(R.string.vm_20dce2), str(R.string.vm_1405d7), str(R.string.vm_f0dfc6), str(R.string.vm_785abc), str(R.string.vm_18a481), "html", str(R.string.group_create), str(R.string.vm_8cdf04))
    val nonAppEpisodes = episodes.filter { ep ->
        val g = ep.goalText.lowercase()
        ep.goalText.isNotBlank() && ep.success && appKeywords.none { g.contains(it) }
    }
    nonAppEpisodes
        .groupBy { it.goalText.trim().take(10).lowercase() }
        .entries
        .sortedByDescending { it.value.maxOf { e -> e.createdAt } }
        .mapNotNull { (_, eps) -> transformEpisodeToSuggestion(eps.first().goalText) }
        .distinctBy { it.take(12).lowercase() }
        .take(2)
        .forEach { result += it }

    // 4. Profile-based fallback when history is thin
    if (result.size < 3) {
        result += buildProfileSuggestions(profileFacts).take(3 - result.size)
    }

    return result.distinctBy { it.take(12).lowercase() }.take(5)
}

private fun detectEmotionSuggestion(messages: List<String>, profileFacts: Map<String, String>): String? {
    val text = messages.take(15).joinToString(" ").lowercase()
    return when {
        Regex(str(R.string.vm_7f612d)).containsMatchIn(text) ->
            str(R.string.vm_e391a4)
        Regex(str(R.string.vm_13e6c0)).containsMatchIn(text) ->
            str(R.string.vm_574a74)
        Regex(str(R.string.vm_5fa120)).containsMatchIn(text) ->
            str(R.string.vm_e88c4a)
        Regex(str(R.string.vm_2d4a2a)).containsMatchIn(text) ->
            str(R.string.vm_e53c91)
        Regex(str(R.string.vm_bfd743)).containsMatchIn(text) ->
            str(R.string.vm_2846ae)
        else -> {
            val emotionalVal = profileFacts.entries
                .find { it.key.contains("emotional") || it.key.contains("stability") }
                ?.value?.lowercase()
            if (emotionalVal != null &&
                Regex(str(R.string.vm_893bbc)).containsMatchIn(emotionalVal)
            ) str(R.string.vm_408d69) else null
        }
    }
}

private fun transformEpisodeToSuggestion(goalText: String): String? {
    val lower = goalText.lowercase()
    val topic = goalText.trim()
        .replace(Regex(str(R.string.vm_7851a0)), "")
        .trim()
    if (topic.length < 2) return null
    val short = topic.take(15)
    return when {
        lower.contains(str(R.string.profile_search)) || lower.contains(str(R.string.vm_40f58e)) || lower.contains(str(R.string.vm_144a16)) || lower.contains(str(R.string.vm_2f3652)) ->
            "🔍 继续深入搜索${short.replace(Regex(str(R.string.vm_8e63b3)), "").trim().take(12)}？"
        lower.contains(str(R.string.profile_72fa7c)) || lower.contains(str(R.string.vm_d7656a)) || lower.contains(str(R.string.vm_0d8307)) ->
            str(R.string.quick_suggest_refine, short)
        lower.contains(str(R.string.profile_4d7dc6)) || lower.contains(str(R.string.vm_c8616c)) || lower.contains(str(R.string.vm_6cf774)) || lower.contains(str(R.string.vm_f4b06b)) ->
            str(R.string.quick_suggest_improve, short)
        lower.contains(str(R.string.vm_65f27a)) || lower.contains(str(R.string.profile_aacef1)) || lower.contains(str(R.string.profile_2d4653)) ->
            str(R.string.quick_suggest_learn, short)
        lower.contains(str(R.string.vm_8b3607)) ->
            str(R.string.vm_485c63)
        else -> str(R.string.quick_suggest_explore, short)
    }
}

private fun buildProfileSuggestions(profileFacts: Map<String, String>): List<String> {
    val profession = profileFacts.entries
        .find { it.key.contains("profession") || it.key.contains("job") || it.key.contains("occupation") }
        ?.value?.lowercase() ?: ""
    val interests = profileFacts.entries
        .filter { it.key.contains("interest") || it.key.contains("hobby") || it.key.contains(str(R.string.vm_12081d)) }
        .joinToString(" ") { it.value.lowercase() }

    val suggestions = mutableListOf<String>()

    when {
        profession.contains(str(R.string.vm_3ff3c3)) || profession.contains(str(R.string.vm_1405d7)) || profession.contains(str(R.string.vm_22c8a6)) || profession.contains("dev") -> {
            suggestions += str(R.string.vm_b762d9)
            suggestions += str(R.string.vm_search)
        }
        profession.contains(str(R.string.vm_b08890)) -> {
            suggestions += str(R.string.vm_22fc10)
            suggestions += str(R.string.vm_bd62aa)
        }
        profession.contains(str(R.string.vm_35d996)) || profession.contains(str(R.string.profile_4ef520)) || profession.contains(str(R.string.vm_8640cb)) -> {
            suggestions += str(R.string.vm_fb0c6d)
            suggestions += str(R.string.vm_search_2)
        }
        profession.contains(str(R.string.vm_47df87)) || profession.contains(str(R.string.home_552cac)) || profession.contains(str(R.string.vm_916801)) -> {
            suggestions += str(R.string.vm_search_3)
            suggestions += str(R.string.vm_8f3a74)
        }
        profession.contains(str(R.string.vm_d58e85)) || profession.contains(str(R.string.vm_104ec0)) || profession.contains(str(R.string.vm_content)) -> {
            suggestions += str(R.string.vm_b43302)
            suggestions += str(R.string.vm_search_4)
        }
        profession.contains(str(R.string.vm_60f89a)) || profession.contains(str(R.string.vm_aef5b4)) || profession.contains(str(R.string.vm_b8fe8d)) -> {
            suggestions += str(R.string.vm_search_5)
            suggestions += str(R.string.vm_7636a9)
        }
    }

    when {
        interests.contains(str(R.string.vm_687a7e)) || interests.contains(str(R.string.vm_2f3703)) -> suggestions += str(R.string.vm_da0dfd)
        interests.contains(str(R.string.profile_c24d6f)) || interests.contains(str(R.string.profile_37b6de)) || interests.contains(str(R.string.vm_7b385d)) -> suggestions += str(R.string.vm_1fd770)
        interests.contains(str(R.string.vm_874834)) || interests.contains(str(R.string.vm_0ebbd8)) -> suggestions += str(R.string.vm_26a2c0)
        interests.contains(str(R.string.vm_c89227)) || interests.contains(str(R.string.vm_c5df2d)) || interests.contains(str(R.string.vm_e69a3d)) -> suggestions += str(R.string.vm_27bf8d)
        interests.contains(str(R.string.vm_ba0821)) -> suggestions += str(R.string.vm_search_6)
        interests.contains(str(R.string.vm_4c8bd0)) -> suggestions += str(R.string.vm_fee3c0)
    }

    return suggestions.take(3)
}

private fun addTaskEmoji(text: String): String {
    // Skip if already starts with a non-alphanumeric char (emoji / symbol)
    if (text.isNotEmpty() && !text[0].isLetterOrDigit() && !text[0].isWhitespace()) return text

    val lower = text.lowercase()
    val emoji = when {
        lower.contains(str(R.string.profile_cfbf6f)) || lower.contains("whatsapp") || lower.contains("telegram") -> "💬"
        lower.contains(str(R.string.vm_e9e805)) || lower.contains("email") -> "📧"
        lower.contains(str(R.string.vm_485c3a)) || lower.contains(str(R.string.vm_94e705)) || lower.contains(str(R.string.vm_send)) -> "💬"
        lower.contains(str(R.string.vm_265f27)) || lower.contains(str(R.string.vm_245af8)) -> "🌤"
        lower.contains(str(R.string.vm_8b3607)) -> "🌐"
        lower.contains(str(R.string.vm_new__2)) || lower.contains(str(R.string.vm_4519fe)) || lower.contains(str(R.string.vm_4af215)) -> "📰"
        lower.contains(str(R.string.profile_search)) || lower.contains(str(R.string.vm_dc5d07)) || lower.contains(str(R.string.vm_bee912)) || lower.contains(str(R.string.vm_0a4df2)) -> "🔍"
        lower.contains(str(R.string.profile_06e004)) || lower.contains(str(R.string.vm_41282b)) || lower.contains(str(R.string.vm_1405d7)) || lower.contains("python") || lower.contains(str(R.string.vm_ba311d)) || lower.contains(str(R.string.vm_d23dd7)) || lower.contains(str(R.string.vm_3ff3c3)) -> "💻"
        lower.contains("shell") || lower.contains(str(R.string.vm_ddf7d2)) || lower.contains(str(R.string.vm_4722bc)) -> "⌨️"
        lower.contains(str(R.string.chat_369abf)) || lower.contains(str(R.string.vm_21b8ea)) -> "📸"
        lower.contains(str(R.string.chat_20def7)) || lower.contains(str(R.string.vm_e1b7ce)) || lower.contains(str(R.string.vm_d2fb1e)) -> "🖼️"
        lower.contains(str(R.string.vm_c941bd)) || lower.contains(str(R.string.vm_228785)) || lower.contains(str(R.string.vm_b6b90e)) || lower.contains(str(R.string.vm_1b4670)) -> "🎨"
        lower.contains(str(R.string.vm_7fcf42)) -> "🎬"
        lower.contains(str(R.string.vm_95521b)) || lower.contains(str(R.string.vm_b85270)) || lower.contains(str(R.string.vm_2ecb31)) -> "🎵"
        lower.contains(str(R.string.skills_2a0c47)) || lower.contains(str(R.string.chat_325369)) || lower.contains(str(R.string.role_save)) -> "📁"
        lower.contains(str(R.string.vm_7051dc)) || lower.contains(str(R.string.vm_1b6c77)) && lower.contains(str(R.string.profile_4ef520)) -> "🗒️"
        lower.contains(str(R.string.vm_a5cd4e)) || lower.contains(str(R.string.vm_25f9c7)) || lower.contains(str(R.string.vm_3ae146)) -> "📋"
        lower.contains(str(R.string.profile_4d7dc6)) || lower.contains(str(R.string.vm_c8616c)) || lower.contains(str(R.string.vm_6cf774)) || lower.contains(str(R.string.vm_f4b06b)) || lower.contains(str(R.string.vm_7ec1a5)) -> "📝"
        lower.contains(str(R.string.profile_72fa7c)) -> "📊"
        lower.contains(str(R.string.vm_0d8307)) || lower.contains(str(R.string.vm_d7656a)) -> "📈"
        lower.contains(str(R.string.vm_32c3a8)) || lower.contains(str(R.string.vm_8be34d)) || lower.contains(str(R.string.vm_0e9fd9)) -> "🛒"
        lower.contains(str(R.string.vm_92b9fb)) || lower.contains(str(R.string.vm_82fe1d)) || lower.contains(str(R.string.vm_13d043)) || lower.contains(str(R.string.vm_0f0789)) || lower.contains(str(R.string.vm_f3075d)) -> "💰"
        lower.contains(str(R.string.profile_0debf5)) || lower.contains(str(R.string.vm_c6cceb)) || lower.contains(str(R.string.vm_769ef5)) -> "📅"
        lower.contains(str(R.string.vm_4b027f)) || lower.contains(str(R.string.vm_23fcc3)) -> "⏰"
        lower.contains(str(R.string.perm_open)) || lower.contains(str(R.string.vm_8e54dd)) -> "📱"
        lower.contains(str(R.string.profile_37b6de)) || lower.contains(str(R.string.profile_c24d6f)) || lower.contains(str(R.string.vm_7b385d)) -> "🏃"
        lower.contains(str(R.string.profile_44ebfb)) || lower.contains(str(R.string.vm_dd8264)) || lower.contains(str(R.string.vm_c89227)) || lower.contains(str(R.string.vm_3ee77c)) -> "🍜"
        lower.contains(str(R.string.vm_874834)) || lower.contains(str(R.string.vm_0ebbd8)) || lower.contains(str(R.string.vm_fd5b76)) -> "✈️"
        lower.contains(str(R.string.vm_2f3703)) || lower.contains(str(R.string.vm_687a7e)) || lower.contains(str(R.string.vm_3f9810)) && lower.contains(str(R.string.vm_2f3703)) -> "📚"
        lower.contains(str(R.string.vm_a95dd3)) || lower.contains(str(R.string.vm_6b80d6)) || lower.contains(str(R.string.vm_b6b9ce)) -> "💡"
        lower.contains(str(R.string.vm_18a481)) || lower.contains(str(R.string.vm_beb6c0)) || lower.contains(str(R.string.vm_9c5c5c)) -> "🌐"
        lower.contains(str(R.string.drawer_settings)) || lower.contains(str(R.string.setup_action)) -> "⚙️"
        lower.contains(str(R.string.skill_market_e655a4)) || lower.contains(str(R.string.vm_f26ef9)) -> "📦"
        lower.contains(str(R.string.vm_ba0821)) -> "🎮"
        lower.contains(str(R.string.vm_4c8bd0)) || lower.contains(str(R.string.vm_c38d3f)) -> "📷"
        else -> "✨"
    }
    return "$emoji $text"
}

private fun supportsCurrentMultimodal(snapshot: ConfigSnapshot): Boolean {
    if (!snapshot.localModelEnabled && !snapshot.localNativeOnly) return snapshot.supportsMultimodal
    val manager = ClawApplication.instance.localModelManager
    val model = manager.modelInfo(snapshot.localModelId) ?: return false
    return model.supportsVision && manager.visionModelPathFor(snapshot.localModelId) != null
}

private fun String.cleanLocalTurnTokens(): String = cleanLocalGeneratedText()

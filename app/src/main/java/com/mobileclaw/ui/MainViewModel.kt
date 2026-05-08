package com.mobileclaw.ui

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.agent.AgentEvent
import com.mobileclaw.agent.AgentRuntime
import com.mobileclaw.agent.ChatRouter
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleScheduler
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskType
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.Message
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.llm.ToolDefinition
import com.mobileclaw.llm.ToolParameters
import com.mobileclaw.llm.ToolProperty
import com.mobileclaw.memory.EpisodicMemory
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.memory.db.SessionMessageEntity
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.builtin.BgLaunchSkill
import com.mobileclaw.skill.builtin.BgReadScreenSkill
import com.mobileclaw.skill.builtin.BgScreenshotSkill
import com.mobileclaw.skill.builtin.BgStopSkill
import com.mobileclaw.skill.builtin.VirtualDisplaySetupSkill
import com.mobileclaw.skill.builtin.ClipboardSkill
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
    private val episodicMemory = EpisodicMemory(app.database.episodeDao(), OpenAiGateway(config))
    private val conversationMemory = app.conversationMemory
    private val profileExtractor = app.userProfileExtractor
    private val roleManager = app.roleManager
    private val userConfig = app.userConfig
    private val database = app.database
    private val llm get() = OpenAiGateway(config)

    // Role switch requests emitted by SwitchRoleSkill / RoleManagerSkill, consumed in init
    private val roleRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val switchRoleSkill = SwitchRoleSkill(roleManager, roleRequests)

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
                    isConfigured = snap.endpoint.isNotBlank() && snap.apiKey.isNotBlank(),
                    currentModel = snap.model,
                    supportsMultimodal = snap.supportsMultimodal,
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
            val offset = _uiState.value.historyOffset
            val pageSize = 20
            val total = runCatching { database.sessionMessageDao().countForSession(sessionId) }.getOrDefault(0)
            val entities = runCatching {
                database.sessionMessageDao().forSessionPaged(sessionId, pageSize, offset)
            }.getOrDefault(emptyList()).reversed()
            val older = entities.map { it.toChatMessage() }
            updateSession(sessionId) { it.copy(messages = older + it.messages) }
            _uiState.update { it.copy(
                historyOffset = offset + pageSize,
                historyHasMore = offset + pageSize < total,
                historyLoading = false,
            )}
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
            ))
        }

        // Update session title from first user message if still default
        val session = database.sessionDao().recent(50).find { it.id == sessionId }
        if (session != null && session.title == str(R.string.vm_new_)) {
            val title = userMsg.text.take(30)
            database.sessionDao().updateTitle(sessionId, title)
            loadSessions()
        }
    }

    private fun buildAgentMessages(
        summary: String,
        logLines: List<LogLine>,
        attachments: List<SkillAttachment>,
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (summary.isNotBlank() || logLines.isNotEmpty()) {
            messages += ChatMessage(
                role = MessageRole.AGENT,
                text = summary,
                logLines = logLines,
            )
        }
        attachments.forEach { attachment ->
            messages += ChatMessage(
                role = MessageRole.AGENT,
                text = "",
                attachments = listOf(attachment),
            )
        }
        if (messages.isEmpty()) {
            messages += ChatMessage(role = MessageRole.AGENT, text = "Done.")
        }
        return messages
    }

    // ── Role Management ──────────────────────────────────────────────────────

    fun setActiveRole(role: Role) {
        _uiState.update { it.copy(currentRole = role) }
        if (role.modelOverride != null) {
            viewModelScope.launch {
                val snap = config.snapshot()
                val updatedGateways = snap.gateways.map {
                    if (it.id == snap.activeGatewayId || (snap.activeGatewayId == null && it == snap.gateways.firstOrNull()))
                        it.copy(model = role.modelOverride)
                    else it
                }
                config.update(snap.copy(gateways = updatedGateways))
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
            runCatching { userConfig.set(key, value, description) }
        }
    }

    fun deleteUserConfigEntry(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { userConfig.delete(key) }
        }
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
        val currentSessionId = _uiState.value.currentSessionId
        if (goal.isBlank() || _uiState.value.sessionStates[currentSessionId]?.isRunning == true) return

        val priorContext = buildPriorContext()
        val attachedImage = _uiState.value.inputImageBase64
        val attachedFile = _uiState.value.inputFileAttachment
        val sessionIdAtStart = _uiState.value.currentSessionId
        // Prepend text file content directly into the LLM goal
        val effectiveGoal = if (attachedFile != null && attachedFile.isText) {
            "[附件: ${attachedFile.name}]\n```\n${attachedFile.content.take(10_000)}\n```\n\n$goal"
        } else goal
        val taskType = TaskClassifier.classify(
            goal = effectiveGoal,
            hasImage = attachedImage != null,
            hasFile = attachedFile != null,
        )
        val currentRole = _uiState.value.currentRole
        val scheduleDecision = RoleScheduler.schedule(
            taskType = taskType,
            goal = effectiveGoal,
            availableRoles = _uiState.value.availableRoles,
            currentRole = currentRole,
        )
        val scheduledRole = scheduleDecision.role
        if (scheduledRole.id != currentRole.id) {
            _uiState.update { it.copy(currentRole = scheduledRole) }
        }
        // Build the user message once so we have a stable reference for persisting
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            text = goal,
            imageBase64 = if (attachedImage != null) attachedImage
                          else if (attachedFile != null && !attachedFile.isText) attachedFile.content
                          else null,
        )

        _uiState.update { it.copy(inputImageBase64 = null, inputFileAttachment = null) }
        updateSession(sessionIdAtStart) { s -> s.copy(
            isRunning = true,
            messages = s.messages + userMessage,
            activeLogLines = emptyList(),
            activeAttachments = emptyList(),
            streamingToken = "",
            streamingThought = "",
        )}
        // Fast path: conversational message with no attachments → skip agent loop
        if (attachedImage == null && attachedFile == null &&
            taskType == TaskType.GENERAL &&
            ChatRouter.classify(goal) == ChatRouter.Intent.CHAT) {
            runDirectChat(sessionIdAtStart, userMessage, goal, scheduledRole, priorContext)
            return
        }

        val llm = OpenAiGateway(config)
        val rt = AgentRuntime(llm, registry, app.semanticMemory)
        runtimes[sessionIdAtStart] = rt

        overlay.show(goal)

        consoleServer.broadcast("task_started", goal)

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
                episodicMemory.retrieve(goal)
                    .filter { it.reflexionSummary.isNotBlank() }
                    .joinToString("\n") { "- ${it.reflexionSummary}" }
            }.getOrDefault("")

            // Collect NetworkTracer events and append to the most recent active log line's details
            launch {
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

            launch {
                rt.events.collect { event ->
                    when (event) {
                        is AgentEvent.ThinkingToken -> {
                            overlay.onToken(event.text)
                            updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + event.text) }
                        }
                        is AgentEvent.SkillCalling -> {
                            overlay.onSkillCalling(event.skillId, event.params)
                            if (event.skillId in VISUAL_SKILL_IDS) {
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
                rt.run(
                    goal             = effectiveGoal,
                    taskType         = taskType,
                    priorContext     = priorContext,
                    episodicContext  = episodicContext,
                    language         = config.language,
                    imageBase64      = attachedImage,
                    role             = scheduledRole,
                    userProfileContext = userProfileContext,
                    onToken       = { token ->
                        overlay.onToken(token)
                        updateSession(resolvedSessionId) { it.copy(streamingToken = it.streamingToken + token) }
                        consoleServer.broadcast("token", token)
                    },
                    onThinkToken  = { token ->
                        overlay.onToken(token)
                        updateSession(resolvedSessionId) { it.copy(streamingThought = it.streamingThought + token) }
                    },
                )
            }

            overlay.hide()

            val summary = result.getOrNull()?.summary ?: result.exceptionOrNull()?.message ?: "Task failed."
            consoleServer.broadcast("task_completed", summary)

            launch { runCatching { episodicMemory.record(result.getOrNull() ?: return@launch) } }
            launch {
                runCatching {
                    conversationMemory.addUserMessage(goal)
                    conversationMemory.addAgentMessage(summary)
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
                buildAgentMessages(summary, finalLogLines, currentRunState.activeAttachments)
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
            runtimes.remove(resolvedSessionId)

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
    ) {
        val llm = OpenAiGateway(config)
        val newJob = viewModelScope.launch {
            var sessionId = sessionIdAtStart
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
            val resolvedSessionId = sessionId
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
            val contextSection = if (priorContext.isNotBlank()) "\n## Recent Conversation\n$priorContext\n" else ""
            val systemPrompt = """You are ${currentRole.avatar} ${currentRole.name}, a helpful AI assistant.$langSection$roleSection$contextSection
## Interactive UI — MANDATORY RULES
You MUST render interactive components instead of plain text in these situations:
1. Offering choices/options → use buttons (never write "A. xxx  B. xxx" as text)
2. Asking user to fill in a value → use input + submit button
3. Showing 3+ items with attributes → use a table
4. Comparing options → use a card-based layout
5. Showing numeric progress/stats → use progress or chart

Embed a ${"```"}ui block with a single JSON object. Keep it on as few lines as possible.
Types: column/row(gap,padding,children) | card(title,children) | text(content,size,bold,color,align) | button(label,action,style) | input(key,placeholder) | select(key,options:[]) | table(headers:[],rows:[[]]) | chart_bar/chart_line(data:[],labels:[],title) | progress(value,label) | badge(text,color) | divider | spacer(size)
Actions: "send:message" | "submit:text with {key}" | "copy:text"

Example — when user asks to choose something:
${"```"}ui
{"type":"column","gap":10,"children":[{"type":"text","content":str(R.string.vm_708c9d),"bold":true,"size":15},{"type":"row","gap":8,"children":[{"type":"button","label":str(R.string.vm_05f87b),"action":str(R.string.vm_1152c9)},{"type":"button","label":str(R.string.vm_f38c0a),"action":str(R.string.vm_68185f),"style":"outline"}]}]}
${"```"}

Example — when collecting input:
${"```"}ui
{"type":"column","gap":8,"children":[{"type":"input","key":"q","placeholder":str(R.string.vm_input)},{"type":"button","label":str(R.string.vm_submit),"action":"submit:{q}"}]}
${"```"}

For pure conversational replies (greetings, simple factual answers) — plain text is fine.""".trimIndent()

            val result = runCatching {
                llm.chat(ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = goal),
                    ),
                    tools = emptyList(),
                    stream = true,
                    onToken = { token ->
                        updateSession(resolvedSessionId) { it.copy(streamingToken = it.streamingToken + token) }
                    },
                ))
            }

            val summary = result.getOrNull()?.content
                ?: _uiState.value.sessionStates[resolvedSessionId]?.streamingToken?.ifBlank { null }
                ?: result.exceptionOrNull()?.message ?: "Error."

            val finalAgentMsg = ChatMessage(role = MessageRole.AGENT, text = summary)
            updateSession(resolvedSessionId) { s -> s.copy(
                isRunning = false,
                streamingToken = "",
                streamingThought = "",
                messages = s.messages + finalAgentMsg,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )}
            taskJobs.remove(resolvedSessionId)

            if (resolvedSessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(resolvedSessionId, userMessage, listOf(finalAgentMsg)) }
            }
            launch { runCatching { conversationMemory.addUserMessage(goal); conversationMemory.addAgentMessage(summary) } }
        }
        taskJobs[sessionIdAtStart] = newJob
    }

    fun stopTask() {
        val sessionId = _uiState.value.currentSessionId
        taskJobs[sessionId]?.cancel()
        taskJobs.remove(sessionId)
        runtimes.remove(sessionId)
        overlay.hide()
        consoleServer.broadcast("task_stopped", "")
        val runState = _uiState.value.sessionStates[sessionId] ?: SessionRunState()
        updateSession(sessionId) { state ->
            val agentMsgs = if (state.activeLogLines.isNotEmpty() || state.streamingToken.isNotBlank() || state.activeAttachments.isNotEmpty()) {
                buildAgentMessages(
                    summary = state.streamingToken.ifBlank { "Task stopped." },
                    logLines = state.activeLogLines,
                    attachments = state.activeAttachments,
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
        if (backStack.isEmpty() || backStack.last() != AppPage.BROWSER) backStack.addLast(AppPage.BROWSER)
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
        _uiState.update { it.copy(openHtmlAttachment = attachment) }
    }

    fun closeHtmlViewer() {
        _uiState.update { it.copy(openHtmlAttachment = null) }
    }

    // ── Group chat ────────────────────────────────────────────────────────────

    private val groupManager: com.mobileclaw.agent.GroupManager
        get() = runCatching { app.groupManager }
            .getOrElse {
                android.util.Log.w("MainViewModel", "GroupManager was not initialized; creating fallback instance", it)
                com.mobileclaw.agent.GroupManager(app)
            }
    private val groupAgentJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private data class PendingGroupTurn(
        val roleId: String,
        val triggerText: String,
        val chainDepth: Int,
        val priority: Int,
        val longTask: Boolean,
    )
    private data class GroupTurnResult(
        val text: String,
        val attachments: List<SkillAttachment> = emptyList(),
    )
    @Volatile private var groupChatStopped = false
    private val pendingGroupTurns = ArrayDeque<PendingGroupTurn>()
    private val GROUP_TASK_POOL_LIMIT = 4

    fun loadGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = groupManager.all()
            _uiState.update { it.copy(groups = groups) }
        }
    }

    fun createGroup(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager.save(group)
            loadGroups()
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager.delete(id)
            database.groupMessageDao().deleteForGroup(id)
            loadGroups()
            if (_uiState.value.openGroup?.id == id) closeGroupChat()
        }
    }

    fun openGroupChat(group: com.mobileclaw.agent.Group) {
        viewModelScope.launch(Dispatchers.IO) {
            val entities = database.groupMessageDao().forGroup(group.id)
            val messages = entities.map { e ->
                GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, deserializeAttachments(e.attachmentsJson), e.createdAt)
            }
            _uiState.update { it.copy(openGroup = group, groupMessages = messages, groupRunning = false, groupUnreadCount = 0) }
            navigate(AppPage.GROUP_CHAT)

            // Auto-spark: if group has members and conversation is cold (no messages or last msg > 15 min ago)
            val lastMsgTime = messages.lastOrNull()?.createdAt ?: 0L
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

    fun closeGroupChat() {
        groupChatStopped = true
        groupAgentJobs.values.forEach { it.cancel() }
        groupAgentJobs.clear()
        pendingGroupTurns.clear()
        _uiState.update { it.copy(openGroup = null, groupMessages = emptyList(), groupRunning = false, groupTypingAgents = emptySet(), groupPendingMessages = emptyList()) }
        navigateBack()
    }

    fun stopGroupChat() {
        groupChatStopped = true
        groupAgentJobs.values.forEach { it.cancel() }
        groupAgentJobs.clear()
        pendingGroupTurns.clear()
        _uiState.update { it.copy(groupRunning = false, groupTypingAgents = emptySet(), groupPendingMessages = emptyList()) }
    }

    fun stopGroupAgent(roleId: String) {
        groupAgentJobs[roleId]?.cancel()
        groupAgentJobs.remove(roleId)
        _uiState.update { it.copy(groupTypingAgents = it.groupTypingAgents - roleId) }
        if (groupAgentJobs.isEmpty()) {
            _uiState.update { it.copy(groupRunning = false) }
        }
    }

    fun sendGroupMessage(text: String, attachments: List<SkillAttachment> = emptyList()) {
        val group = _uiState.value.openGroup ?: return
        if (text.isBlank() && attachments.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = GroupMessage(groupId = group.id, senderId = "user", senderName = str(R.string.group_chat_df1fd9), senderAvatar = "👤", text = text, attachments = attachments)
            val rowId = database.groupMessageDao().insert(userMsg.toEntity())
            _uiState.update { it.copy(groupMessages = it.groupMessages + userMsg.copy(id = rowId)) }

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
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH)) {
            val selected = RoleScheduler.schedule(taskType, userText, allMembers, allMembers.first()).role
                .takeIf { role -> allMembers.any { it.id == role.id } }
                ?: allMembers.first()
            launchGroupAgentTurn(group, selected, allMembers, delayMs = 0, chainDepth = 0, longTask = true, triggerText = userText)
            return
        }
        val mentioned = parseMentions(userText)
        if (mentioned.isNotEmpty()) {
            // Explicit @mention: those agents respond immediately, full chain depth
            allMembers
                .filter { r -> mentioned.any { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) } }
                .forEach { launchGroupAgentTurn(group, it, allMembers, delayMs = 0, chainDepth = 5, triggerText = userText) }
        } else {
            // No @mention: shuffle, stagger a random 1-3 agents as initial responders.
            // They self-filter via [PASS]; their responses invite others via probability decay (depth 5→4→…→0).
            val shuffled = allMembers.shuffled()
            val activeCount = (1..minOf(3, shuffled.size)).random()
            shuffled.take(activeCount).forEachIndexed { idx, role ->
                val delayMs = when (idx) {
                    0    -> (200L..800L).random()
                    1    -> (1500L..3000L).random()
                    else -> (3000L..5500L).random()
                }
                launchGroupAgentTurn(group, role, allMembers, delayMs = delayMs, chainDepth = 5, triggerText = userText)
            }
        }
    }

    private fun enqueueUserGroupTurn(allMembers: List<Role>, userText: String) {
        val taskType = TaskClassifier.classify(userText)
        val mentioned = parseMentions(userText)
        val targets = when {
            taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH) -> {
                val selected = RoleScheduler.schedule(taskType, userText, allMembers, allMembers.first()).role
                listOfNotNull(allMembers.firstOrNull { it.id == selected.id } ?: allMembers.firstOrNull())
            }
            mentioned.isNotEmpty() -> allMembers.filter { r -> mentioned.any { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) } }
            else -> allMembers.shuffled().take(minOf(2, allMembers.size))
        }
        targets.forEach { role ->
            pendingGroupTurns.addFirst(PendingGroupTurn(role.id, userText, chainDepth = if (mentioned.isNotEmpty()) 5 else 2, priority = 100, longTask = taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH)))
        }
        _uiState.update { it.copy(groupPendingMessages = it.groupPendingMessages + userText) }
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
    ) {
        if (groupAgentJobs.containsKey(role.id)) return
        if (groupAgentJobs.size >= GROUP_TASK_POOL_LIMIT) {
            pendingGroupTurns.addLast(PendingGroupTurn(role.id, triggerText, chainDepth, priority = if (longTask) 80 else 10, longTask = longTask))
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            if (groupChatStopped) return@launch
            _uiState.update { it.copy(groupTypingAgents = it.groupTypingAgents + role.id) }
            try {
                val history = _uiState.value.groupMessages
                val systemPrompt = buildGroupSystemPrompt(role, group.name, allMembers)
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
                    listOf(Message(role = "user", content = "[系统]: 轮到你了，${role.name}。"))

                val taskType = TaskClassifier.classify(triggerText.ifBlank { history.lastOrNull()?.text.orEmpty() })
                val response = runGroupAgentTurn(
                    role = role,
                    baseMessages = callMessages,
                    maxSkillCalls = if (longTask || taskType == TaskType.PHONE_CONTROL) 12 else 4,
                )
                val cleanResponse = response.text.trim()

                // [PASS] means the agent found the message irrelevant to them — skip silently
                if ((cleanResponse.isNotBlank() || response.attachments.isNotEmpty()) && !cleanResponse.equals("[PASS]", ignoreCase = true) && !groupChatStopped) {
                    val agentMsg = GroupMessage(groupId = group.id, senderId = role.id,
                        senderName = role.name, senderAvatar = role.avatar, text = cleanResponse, attachments = response.attachments)
                    val rowId = database.groupMessageDao().insert(agentMsg.toEntity())
                    val onScreen = _uiState.value.currentPage == AppPage.GROUP_CHAT
                    _uiState.update { st ->
                        st.copy(
                            groupMessages = st.groupMessages + agentMsg.copy(id = rowId),
                            groupUnreadCount = if (onScreen) 0 else st.groupUnreadCount + 1,
                        )
                    }

                    if (!groupChatStopped) {
                        val mentions = parseMentions(cleanResponse)

                        // Explicit @mentions: always chain, no depth limit, immediate
                        allMembers
                            .filter { r -> r.id != role.id && mentions.any { m ->
                                r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true)
                            }}
                                .forEach { launchGroupAgentTurn(group, it, allMembers, chainDepth = chainDepth, triggerText = cleanResponse) }

                        // Organic reactions: let all idle members evaluate whether to join.
                        // Probability decays with depth so the conversation tapers naturally.
                        if (chainDepth > 0) {
                            // Base probability per depth level: 0.80 → 0.65 → 0.50 → 0.35 → 0.20
                            val reactionProb = when (chainDepth) {
                                5 -> 0.80f; 4 -> 0.65f; 3 -> 0.50f; 2 -> 0.35f; else -> 0.20f
                            }
                            allMembers
                                .filter { r -> r.id != role.id && !groupAgentJobs.containsKey(r.id) &&
                                    mentions.none { m -> r.name.contains(m, ignoreCase = true) || m.contains(r.name, ignoreCase = true) }
                                }
                                .forEach { reactor ->
                                    // Relevance boost: if the response text mentions this reactor's name, higher chance
                                    val nameHit = cleanResponse.contains(reactor.name, ignoreCase = true)
                                    val effectiveProb = if (nameHit) minOf(reactionProb + 0.20f, 1.0f) else reactionProb
                                    if (kotlin.random.Random.nextFloat() < effectiveProb) {
                                        val reactionDelay = (300L..1600L).random()
                                        launchGroupAgentTurn(group, reactor, allMembers,
                                            delayMs = reactionDelay, chainDepth = chainDepth - 1, triggerText = cleanResponse)
                                    }
                                }
                        }
                    }
                }
            } finally {
                groupAgentJobs.remove(role.id)
                _uiState.update { it.copy(groupTypingAgents = it.groupTypingAgents - role.id) }

                drainPendingGroupTurns(group, allMembers)
                if (groupAgentJobs.isEmpty() && pendingGroupTurns.isEmpty()) {
                    _uiState.update { it.copy(groupRunning = false, groupPendingMessages = emptyList()) }
                }
            }
        }
        groupAgentJobs[role.id] = job
    }

    private fun drainPendingGroupTurns(group: com.mobileclaw.agent.Group, allMembers: List<Role>) {
        while (!groupChatStopped && groupAgentJobs.size < GROUP_TASK_POOL_LIMIT && pendingGroupTurns.isNotEmpty()) {
            val index = pendingGroupTurns
                .withIndex()
                .filter { (_, turn) -> !groupAgentJobs.containsKey(turn.roleId) }
                .maxByOrNull { (_, turn) -> turn.priority }
                ?.index ?: return
            val nextTurn = pendingGroupTurns.removeAt(index)
            _uiState.update { it.copy(groupPendingMessages = it.groupPendingMessages.drop(1), groupRunning = true) }
            allMembers.firstOrNull { it.id == nextTurn.roleId }?.let { nextRole ->
                launchGroupAgentTurn(
                    group = group,
                    role = nextRole,
                    allMembers = allMembers,
                    chainDepth = nextTurn.chainDepth,
                    longTask = nextTurn.longTask,
                    triggerText = nextTurn.triggerText,
                )
            }
        }
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
            launchGroupAgentTurn(group, allMembers.random(), allMembers, chainDepth = 5)
        }
    }

    // Mini ReAct loop: buffers internally; no streaming text to UI (only typing indicator shown).
    private suspend fun runGroupAgentTurn(
        role: Role,
        baseMessages: List<Message>,
        maxSkillCalls: Int = 3,
    ): GroupTurnResult {
        val forcedMetas = role.forcedSkillIds.mapNotNull { registry.get(it)?.meta }
        val tools = (registry.forInjection(maxLevel = 1) + forcedMetas).distinctBy { it.id }.map { m ->
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
                llm.chat(ChatRequest(
                    messages = messages,
                    tools = if (iteration < maxSkillCalls) tools else emptyList(),
                    stream = true,
                    onToken = { tok -> accumulated += tok },  // buffer locally, not shown during inference
                    onThinkToken = null,                      // discard thinking completely
                ))
            }.getOrElse { return GroupTurnResult(finalText, attachments) }

            if (resp.toolCall == null) {
                finalText = accumulated.ifBlank { resp.content ?: "" }
                return GroupTurnResult(finalText, attachments)
            }

            val tc = resp.toolCall
            val skillResult = registry.get(tc.skillId)
                ?.let { runCatching { it.execute(tc.params) }.getOrElse { e -> com.mobileclaw.skill.SkillResult(false, "Error: ${e.message}") } }
                ?: com.mobileclaw.skill.SkillResult(false, "Skill '${tc.skillId}' not found")
            (skillResult.data as? SkillAttachment)?.let { attachments += it }
            skillResult.imageBase64?.takeIf { it.isNotBlank() }?.let {
                attachments += SkillAttachment.ImageData(it, prompt = "Generated by ${tc.skillId}")
            }

            messages.add(Message(role = "assistant", content = accumulated.ifBlank { null }, toolCalls = listOf(tc)))
            messages.add(Message(role = "tool", content = skillResult.output.take(3000), toolCallId = tc.id))
        }

        return GroupTurnResult(finalText, attachments)
    }

    private fun buildGroupSystemPrompt(
        role: Role,
        groupName: String,
        allMembers: List<Role>,
    ): String = buildString {
        // ① 角色身份——最重要，放最前面，让模型先"入戏"
        appendLine(str(R.string.vm_ff3706))
        appendLine("${role.avatar} ${role.name}")
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
            if (m.id != role.id) appendLine("  ${m.avatar} ${m.name}：${m.description.take(40)}")
        }
        appendLine(str(R.string.vm_090646))
        appendLine()

        // ③ 发消息风格
        appendLine(str(R.string.vm_d9e95d))
        appendLine("• 你就是 ${role.name}，用你自己的说话方式，不要跑偏。")
        appendLine(str(R.string.vm_1acb86))
        appendLine(str(R.string.vm_93c334))
        appendLine()

        // ④ 行为规则
        appendLine(str(R.string.vm_689bf5))
        appendLine(str(R.string.vm_427e4c))
        appendLine(str(R.string.vm_a8c3ee))
        appendLine(str(R.string.vm_704d6a))
        appendLine(str(R.string.vm_e1d388))
        appendLine(str(R.string.vm_abc7c8))
        appendLine(str(R.string.vm_8f6718))
        appendLine("• 如果你使用工具生成了图片、文件、网页或搜索结果，可以把这些结果作为附件发到群里。")
        appendLine("• 任务型请求必须做完再发言；不要做到一半就邀请别人接话。")
    }

    private fun parseMentions(text: String): List<String> =
        Regex("@([\\w\\u4e00-\\u9fff·]+)").findAll(text).map { it.groupValues[1] }.toList()

    private fun groupAttachmentPrompt(attachment: SkillAttachment): String = when (attachment) {
        is SkillAttachment.ImageData -> "图片(${attachment.prompt ?: "image"})"
        is SkillAttachment.FileData -> "文件(${attachment.name}, ${attachment.mimeType}, ${attachment.sizeBytes} bytes)"
        is SkillAttachment.HtmlData -> "HTML(${attachment.title}, ${attachment.path})"
        is SkillAttachment.WebPage -> "网页(${attachment.title}, ${attachment.url})"
        is SkillAttachment.SearchResults -> "搜索结果(${attachment.query}, ${attachment.pages.size} pages)"
        is SkillAttachment.FileList -> "文件列表(${attachment.files.size} files)"
        is SkillAttachment.AccessibilityRequest -> "权限请求(${attachment.skillName})"
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
            runCatching { app.semanticMemory.set(key, value) }
            // Refresh profileFacts so ProfilePage updates immediately
            val facts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
            _uiState.update { it.copy(profileFacts = facts) }
        }
    }

    fun loadProfileData() {
        viewModelScope.launch {
            _uiState.update { it.copy(profileLoading = true) }
            val facts = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
            val episodes = runCatching { app.database.episodeDao().recent(limit = 20) }.getOrDefault(emptyList())
            val convCount = runCatching { conversationMemory.messageCount() }.getOrDefault(0)
            _uiState.update { it.copy(
                profileFacts = facts,
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
            _uiState.update { it.copy(profileFacts = facts, profileExtracting = false) }
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
        val prompt = """
你是专业心理学家。请为"$dimensionTitle"维度生成5道深度心理测试题，持续深入了解用户的潜在特征。

当前已知用户信息：
$relevantFacts

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
            val updatedGateways = snap.gateways.map {
                if (it.id == snap.activeGatewayId || (snap.activeGatewayId == null && it == snap.gateways.firstOrNull()))
                    it.copy(model = model)
                else it
            }
            config.update(snap.copy(gateways = updatedGateways))
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    fun fetchModels() {
        if (_uiState.value.modelsLoading) return
        _uiState.update { it.copy(modelsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val models = runCatching { OpenAiGateway(config).fetchModels() }.getOrDefault(emptyList())
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

    private fun buildPriorContext(): String {
        val msgs = _uiState.value.currentRunState.messages
        if (msgs.isEmpty()) return ""
        val recent = msgs.takeLast(8)
        val lines = recent.map { msg ->
            val raw = when (msg.role) {
                MessageRole.USER -> "User: ${msg.text}"
                MessageRole.AGENT -> "Agent: ${msg.text}"
            }
            // Truncate very long individual messages (e.g. code blocks, agent summaries)
            if (raw.length > 400) raw.take(400) + "…" else raw
        }
        // Hard cap on total prior context size
        val full = lines.joinToString("\n")
        return if (full.length > 2000) full.takeLast(2000) else full
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
            // Web
            WebSearchSkill(app.webViewManager),
            FetchUrlSkill(),
            WebBrowseSkill(app.webViewManager),
            WebContentSkill(app.webViewManager),
            WebJsSkill(app.webViewManager),
            // Content creation
            GenerateImageSkill(config, app.userConfig),
            GenerateIconSkill(app, app.userConfig, app.miniAppStore),
            GenerateDocumentSkill(app),
            GenerateVideoSkill(config, app, app.userConfig),
            CreateFileSkill(app),
            ReadFileSkill(app),
            ListFilesSkill(app),
            CreateHtmlSkill(app),
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
            com.mobileclaw.skill.builtin.UserProfileSkill(app.semanticMemory),
            PermissionSkill(app.permissionManager),
            // Skill management
            MetaSkill(loader),
            SkillCheckSkill(registry),
            QuickSkillSkill(llm, loader),
            SkillMarketSkill(loader),
            // Dynamic config, model & role
            SwitchModelSkill(config),
            UserConfigSkill(userConfig),
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
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeAttachments(attachments: List<SkillAttachment>): String {
        val list = attachments.map { att ->
            when (att) {
                is SkillAttachment.ImageData -> mapOf("type" to "image", "base64" to att.base64, "prompt" to (att.prompt ?: ""))
                is SkillAttachment.FileData  -> mapOf("type" to "file", "path" to att.path, "name" to att.name, "mimeType" to att.mimeType, "sizeBytes" to att.sizeBytes.toString())
                is SkillAttachment.HtmlData  -> mapOf("type" to "html", "path" to att.path, "title" to att.title, "htmlContent" to att.htmlContent)
                is SkillAttachment.WebPage   -> mapOf("type" to "webpage", "url" to att.url, "title" to att.title, "excerpt" to att.excerpt)
                is SkillAttachment.SearchResults -> mapOf(
                    "type" to "search_results",
                    "query" to att.query,
                    "engine" to att.engine,
                    "pages" to att.pages.map { p -> mapOf("url" to p.url, "title" to p.title, "excerpt" to p.excerpt) },
                )
                is SkillAttachment.AccessibilityRequest -> null
                is SkillAttachment.FileList -> mapOf(
                    "type" to "file_list",
                    "directory" to att.directory,
                    "files" to att.files.map { f -> mapOf("path" to f.path, "name" to f.name, "mimeType" to f.mimeType, "sizeBytes" to f.sizeBytes.toString()) },
                )
            }
        }.filterNotNull()
        return gson.toJson(list)
    }
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
            if (title.isNotBlank()) "🌐 Creating page: $title" else "🌐 Creating HTML page"
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
                "create" -> if (title.isNotBlank()) "📱 Creating app: $title" else "📱 Creating mini-app"
                "update" -> if (title.isNotBlank()) "📱 Updating app: $title" else "📱 Updating mini-app"
                "open"   -> if (title.isNotBlank()) "📱 Opening app: $title" else "📱 Opening mini-app"
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

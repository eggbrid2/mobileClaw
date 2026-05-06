package com.mobileclaw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.agent.AgentEvent
import com.mobileclaw.agent.AgentRuntime
import com.mobileclaw.agent.Role
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
import com.mobileclaw.skill.builtin.CreateFileSkill
import com.mobileclaw.skill.builtin.CreateHtmlSkill
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

    private var runtime: AgentRuntime? = null
    private var taskJob: Job? = null

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
                        if (req.title != "新对话") {
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
            title = "新对话",
            roleId = roleId,
        ))
        _uiState.update { it.copy(
            currentSessionId = id,
            messages = emptyList(),
            activeLogLines = emptyList(),
        )}
        loadSessions()
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(currentSessionId = sessionId) }
            loadSessionMessages(sessionId)
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

    private suspend fun loadSessionMessages(sessionId: String) {
        val entities = runCatching {
            database.sessionMessageDao().forSession(sessionId)
        }.getOrDefault(emptyList())
        val messages = entities.map { it.toChatMessage() }
        _uiState.update { it.copy(messages = messages) }
    }

    private suspend fun persistMessages(sessionId: String, userMsg: ChatMessage, agentMsg: ChatMessage) {
        if (sessionId.isBlank()) return
        val gson = Gson()
        // Strip imageBase64 from log lines to avoid bloating the DB
        val sanitizedLogLines = agentMsg.logLines.map { it.copy(imageBase64 = null) }

        database.sessionMessageDao().insert(SessionMessageEntity(
            sessionId = sessionId,
            role = "user",
            text = userMsg.text,
            imageBase64 = userMsg.imageBase64,
        ))
        database.sessionMessageDao().insert(SessionMessageEntity(
            sessionId = sessionId,
            role = "agent",
            text = agentMsg.text,
            logLinesJson = gson.toJson(sanitizedLogLines),
            attachmentsJson = serializeAttachments(agentMsg.attachments),
        ))

        // Update session title from first user message if still default
        val session = database.sessionDao().recent(50).find { it.id == sessionId }
        if (session != null && session.title == "新对话") {
            val title = userMsg.text.take(30)
            database.sessionDao().updateTitle(sessionId, title)
            loadSessions()
        }
    }

    // ── Role Management ──────────────────────────────────────────────────────

    fun setActiveRole(role: Role) {
        _uiState.update { it.copy(currentRole = role) }
        if (role.modelOverride != null) {
            viewModelScope.launch {
                config.update(config.snapshot().copy(model = role.modelOverride))
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

    // ── Task Execution ───────────────────────────────────────────────────────

    fun runTask(goal: String) {
        if (goal.isBlank() || _uiState.value.isRunning) return

        val priorContext = buildPriorContext()
        val currentRole = _uiState.value.currentRole
        val attachedImage = _uiState.value.inputImageBase64
        val sessionIdAtStart = _uiState.value.currentSessionId
        // Build the user message once so we have a stable reference for persisting
        val userMessage = ChatMessage(MessageRole.USER, goal, imageBase64 = attachedImage)

        _uiState.update { it.copy(
            isRunning = true,
            messages = it.messages + userMessage,
            activeLogLines = emptyList(),
            activeAttachments = emptyList(),
            streamingToken = "",
            streamingThought = "",
            inputImageBase64 = null,
        )}
        val llm = OpenAiGateway(config)
        runtime = AgentRuntime(llm, registry, app.semanticMemory)
        val rt = runtime!!

        overlay.show(goal)

        consoleServer.broadcast("task_started", goal)

        taskJob = viewModelScope.launch {
            // Ensure a session exists
            var sessionId = sessionIdAtStart
            if (sessionId.isBlank()) {
                withContext(Dispatchers.IO) { createNewSessionInternal() }
                sessionId = _uiState.value.currentSessionId
            }

            val episodicContext = runCatching {
                episodicMemory.retrieve(goal)
                    .filter { it.reflexionSummary.isNotBlank() }
                    .joinToString("\n") { "- ${it.reflexionSummary}" }
            }.getOrDefault("")

            launch {
                rt.events.collect { event ->
                    when (event) {
                        is AgentEvent.ThinkingToken -> {
                            overlay.onToken(event.text)
                            _uiState.update { it.copy(streamingThought = it.streamingThought + event.text) }
                        }
                        is AgentEvent.SkillCalling -> {
                            overlay.onSkillCalling(event.skillId, event.params)
                            if (event.skillId in VISUAL_SKILL_IDS) {
                                if (event.skillId == "see_screen") auroraOverlay.flashFullScreen()
                                else auroraOverlay.flash()
                            }
                            _uiState.update { it.copy(streamingThought = "") }
                            event.toLogLine()?.let { line ->
                                _uiState.update { it.copy(activeLogLines = it.activeLogLines + line) }
                            }
                            consoleServer.broadcast("skill_called", friendlySkillDescription(event.skillId, event.params))
                        }
                        is AgentEvent.Observation -> {
                            overlay.onObservation(event.text)
                            val line = LogLine(
                                type = LogType.OBSERVATION,
                                text = event.text.take(400),
                                imageBase64 = event.imageBase64,
                            )
                            _uiState.update { state ->
                                state.copy(
                                    activeLogLines = state.activeLogLines + line,
                                    activeAttachments = if (event.attachment != null)
                                        state.activeAttachments + event.attachment
                                    else state.activeAttachments,
                                )
                            }
                        }
                        is AgentEvent.Error -> {
                            overlay.onError(event.message)
                            event.toLogLine()?.let { line ->
                                _uiState.update { it.copy(activeLogLines = it.activeLogLines + line) }
                            }
                        }
                        is AgentEvent.ThinkingComplete -> {
                            overlay.onThinkingComplete()
                            _uiState.update { state ->
                                state.copy(
                                    activeLogLines = state.activeLogLines + LogLine(LogType.THINKING, event.thought),
                                    streamingToken = "",
                                    streamingThought = "",
                                )
                            }
                        }
                        else -> event.toLogLine()?.let { line ->
                            _uiState.update { it.copy(activeLogLines = it.activeLogLines + line) }
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
                    goal             = goal,
                    priorContext     = priorContext,
                    episodicContext  = episodicContext,
                    language         = config.language,
                    imageBase64      = attachedImage,
                    role             = currentRole,
                    userProfileContext = userProfileContext,
                    onToken       = { token ->
                        overlay.onToken(token)
                        _uiState.update { it.copy(streamingToken = it.streamingToken + token) }
                        consoleServer.broadcast("token", token)
                    },
                    onThinkToken  = { token ->
                        overlay.onToken(token)
                        _uiState.update { it.copy(streamingThought = it.streamingThought + token) }
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

            val finalAgentMsg = run {
                val finalLogLines = buildList {
                    addAll(_uiState.value.activeLogLines)
                    if (_uiState.value.streamingThought.isNotBlank()) {
                        add(LogLine(LogType.THINKING, _uiState.value.streamingThought))
                    }
                }
                ChatMessage(
                    role = MessageRole.AGENT,
                    text = summary,
                    logLines = finalLogLines,
                    attachments = _uiState.value.activeAttachments,
                )
            }
            _uiState.update { state ->
                state.copy(
                    isRunning = false,
                    streamingToken = "",
                    streamingThought = "",
                    messages = state.messages + finalAgentMsg,
                    activeLogLines = emptyList(),
                    activeAttachments = emptyList(),
                )
            }

            // Persist the exchange to the session DB
            if (sessionId.isNotBlank()) {
                launch(Dispatchers.IO) { persistMessages(sessionId, userMessage, finalAgentMsg) }
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
    }

    fun stopTask() {
        taskJob?.cancel()
        taskJob = null
        runtime = null
        overlay.hide()
        consoleServer.broadcast("task_stopped", "")
        _uiState.update { state ->
            val agentMsg = if (state.activeLogLines.isNotEmpty() || state.streamingToken.isNotBlank()) {
                listOf(ChatMessage(
                    role = MessageRole.AGENT,
                    text = state.streamingToken.ifBlank { "Task stopped." },
                    logLines = state.activeLogLines,
                    attachments = state.activeAttachments,
                ))
            } else emptyList()
            state.copy(
                isRunning = false,
                streamingToken = "",
                streamingThought = "",
                messages = state.messages + agentMsg,
                activeLogLines = emptyList(),
                activeAttachments = emptyList(),
            )
        }
    }

    fun setInputImage(imageBase64: String?) {
        _uiState.update { it.copy(inputImageBase64 = imageBase64) }
    }

    private val backStack = ArrayDeque<AppPage>().apply { add(AppPage.CHAT) }

    fun navigate(page: AppPage) {
        if (page == AppPage.SKILLS) refreshPromotableSkills()
        if (page == AppPage.PROFILE) loadProfileData()
        if (page == AppPage.SETTINGS) checkPrivServer()
        if (page == AppPage.APPS || page == AppPage.HOME) loadMiniApps()
        if (page == AppPage.GROUPS) loadGroups()
        if (page == AppPage.GROUP_CHAT) _uiState.update { it.copy(groupUnreadCount = 0) }
        if (backStack.isEmpty() || backStack.last() != page) backStack.addLast(page)
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
            _uiState.update { it.copy(currentPage = page, canNavigateBack = backStack.size > 1) }
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

    fun openHtmlViewer(attachment: SkillAttachment.HtmlData) {
        _uiState.update { it.copy(openHtmlAttachment = attachment) }
    }

    fun closeHtmlViewer() {
        _uiState.update { it.copy(openHtmlAttachment = null) }
    }

    // ── Group chat ────────────────────────────────────────────────────────────

    private val groupManager = app.groupManager
    private var groupChatJob: Job? = null
    @Volatile private var groupChatStopped = false

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
                GroupMessage(e.id, e.groupId, e.senderId, e.senderName, e.senderAvatar, e.text, e.createdAt)
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
        groupChatJob?.cancel()
        groupChatJob = null
        _uiState.update { it.copy(openGroup = null, groupMessages = emptyList(), groupRunning = false, groupTypingAgentId = null, groupStreamingText = "") }
        navigateBack()
    }

    fun stopGroupChat() {
        groupChatStopped = true
        groupChatJob?.cancel()
        groupChatJob = null
        _uiState.update { it.copy(groupRunning = false, groupTypingAgentId = null, groupStreamingText = "") }
    }

    fun sendGroupMessage(text: String) {
        val group = _uiState.value.openGroup ?: return
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = GroupMessage(
                groupId = group.id,
                senderId = "user",
                senderName = "你",
                senderAvatar = "👤",
                text = text,
            )
            val rowId = database.groupMessageDao().insert(userMsg.toEntity())
            val savedUser = userMsg.copy(id = rowId)
            _uiState.update { it.copy(groupMessages = it.groupMessages + savedUser) }

            val allMembers = group.memberRoleIds.mapNotNull { roleManager.get(it) }
            if (allMembers.isEmpty()) return@launch

            // Explicitly @mentioned members always respond; others join naturally via [PASS] self-evaluation
            val mentioned = parseMentions(text)
            val forced = if (mentioned.isNotEmpty())
                allMembers.filter { r -> mentioned.any { m -> r.name.contains(m) || m.contains(r.name) || r.id == m } }
            else emptyList()
            // Remaining candidates in shuffled order (adds variety each time)
            val candidates = (forced + allMembers.filter { it !in forced }.shuffled()).distinct()

            groupChatStopped = false
            _uiState.update { it.copy(groupRunning = true) }
            groupManager.touch(group.id)
            loadGroups()

            groupChatJob = viewModelScope.launch(Dispatchers.IO) {
                runGroupLoop(group, allMembers, candidates, forced.map { it.id }.toSet())
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

            val starter = allMembers.random()
            groupChatStopped = false
            _uiState.update { it.copy(groupRunning = true) }

            groupChatJob = viewModelScope.launch(Dispatchers.IO) {
                runGroupLoop(group, allMembers, listOf(starter), setOf(starter.id), spark = true)
            }
        }
    }

    private suspend fun runGroupLoop(
        group: com.mobileclaw.agent.Group,
        allMembers: List<Role>,
        candidates: List<Role>,
        forcedIds: Set<String> = emptySet(),
        spark: Boolean = false,
    ) {
        // Reactions are allowed 1 level deep (a member reacting to another member's message)
        val MAX_REACTION_DEPTH = 1
        // When the user has navigated away from the group chat, cap background messages to avoid surprises
        val MAX_BACKGROUND_MESSAGES = 8

        data class QueueItem(val role: Role, val depth: Int, val isForced: Boolean)

        val queue = ArrayDeque<QueueItem>()
        candidates.forEach { r -> queue.addLast(QueueItem(r, 0, r.id in forcedIds)) }

        val spokenIds = mutableSetOf<String>()
        var backgroundMsgCount = 0

        while (queue.isNotEmpty() && !groupChatStopped) {
            val (role, depth, isForced) = queue.removeFirst()
            if (depth > MAX_REACTION_DEPTH) continue

            // Stop generating if user has left and we've hit the background cap
            val userOnScreen = _uiState.value.currentPage == AppPage.GROUP_CHAT
            if (!userOnScreen && backgroundMsgCount >= MAX_BACKGROUND_MESSAGES) break

            _uiState.update { it.copy(groupTypingAgentId = role.id, groupStreamingText = "") }

            val history = _uiState.value.groupMessages
            val others = allMembers.filter { it.id != role.id }
            val systemPrompt = buildGroupSystemPrompt(role, group.name, allMembers, others, spark && depth == 0)

            val historyMessages = history.takeLast(20).map { msg ->
                Message(role = "user", content = "[${msg.senderName}]: ${msg.text}")
            }
            val callMessages = listOf(Message(role = "system", content = systemPrompt)) +
                historyMessages +
                listOf(Message(role = "user", content = "Your turn as ${role.avatar} ${role.name}:"))

            val responseText = runGroupAgentTurn(role, callMessages)

            // Member chose not to speak
            if (responseText.trim().equals("[PASS]", ignoreCase = true) || responseText.isBlank()) {
                _uiState.update { it.copy(groupTypingAgentId = null, groupStreamingText = "") }
                continue
            }

            val cleanText = responseText.trim()
            val agentMsg = GroupMessage(
                groupId = group.id,
                senderId = role.id,
                senderName = role.name,
                senderAvatar = role.avatar,
                text = cleanText,
            )
            val rowId = database.groupMessageDao().insert(agentMsg.toEntity())

            val nowOnScreen = _uiState.value.currentPage == AppPage.GROUP_CHAT
            _uiState.update {
                it.copy(
                    groupMessages = it.groupMessages + agentMsg.copy(id = rowId),
                    groupStreamingText = "",
                    groupUnreadCount = if (nowOnScreen) 0 else it.groupUnreadCount + 1,
                )
            }

            spokenIds.add(role.id)
            if (!nowOnScreen) backgroundMsgCount++

            // After this member speaks, one random silent member may react naturally
            if (depth < MAX_REACTION_DEPTH && !groupChatStopped) {
                val reactor = allMembers
                    .filter { it.id != role.id && it.id !in spokenIds }
                    .shuffled()
                    .firstOrNull()
                if (reactor != null) queue.addLast(QueueItem(reactor, depth + 1, false))
            }
        }

        _uiState.update { it.copy(groupRunning = false, groupTypingAgentId = null, groupStreamingText = "") }
    }

    // Mini ReAct loop per group-agent turn: up to MAX_SKILL_CALLS tool uses, then a final text reply.
    private suspend fun runGroupAgentTurn(
        role: Role,
        baseMessages: List<Message>,
        maxSkillCalls: Int = 3,
    ): String {
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

        repeat(maxSkillCalls + 1) { iteration ->
            if (groupChatStopped) return finalText
            var accumulated = ""
            val resp = runCatching {
                llm.chat(ChatRequest(
                    messages = messages,
                    tools = if (iteration < maxSkillCalls) tools else emptyList(), // no tools on last pass
                    stream = true,
                    onToken = { tok ->
                        accumulated += tok
                        _uiState.update { it.copy(groupStreamingText = accumulated) }
                    },
                ))
            }.getOrElse { return finalText }

            if (resp.toolCall == null) {
                // Plain text reply — this is the agent's group message
                finalText = accumulated.ifBlank { resp.content ?: "" }
                return finalText
            }

            val tc = resp.toolCall
            _uiState.update { it.copy(groupStreamingText = "🔧 ${tc.skillId}…") }
            val skillResult = registry.get(tc.skillId)
                ?.let { runCatching { it.execute(tc.params) }.getOrElse { e -> com.mobileclaw.skill.SkillResult(false, "Error: ${e.message}") } }
                ?: com.mobileclaw.skill.SkillResult(false, "Skill '${tc.skillId}' not found")

            // Append assistant tool-call + tool result into the conversation for next iteration
            messages.add(Message(role = "assistant", content = accumulated.ifBlank { null }, toolCalls = listOf(tc)))
            messages.add(Message(role = "tool", content = skillResult.output.take(3000), toolCallId = tc.id))
        }

        return finalText
    }

    private fun buildGroupSystemPrompt(
        role: Role,
        groupName: String,
        allMembers: List<Role>,
        others: List<Role>,
        proactive: Boolean = false,
    ): String = buildString {
        appendLine("You are ${role.avatar} ${role.name} in group「$groupName」.")
        if (role.description.isNotBlank()) appendLine("Your character: ${role.description}")
        if (role.systemPromptAddendum.isNotBlank()) {
            appendLine()
            append(role.systemPromptAddendum)
        }
        appendLine()
        appendLine("Group members:")
        appendLine("  👤 User (human)")
        allMembers.forEach { m ->
            if (m.id == role.id) appendLine("  ${m.avatar} ${m.name} — that's you")
            else appendLine("  ${m.avatar} ${m.name} — ${m.description}")
        }
        appendLine()
        if (proactive) {
            appendLine("The group has been quiet. As ${role.name}, bring up something interesting or start a topic that fits your character and the group's vibe. Be casual and natural.")
        } else {
            appendLine("Read the conversation so far. Ask yourself: does ${role.name} genuinely want to say something right now?")
            appendLine("- If YES: write a short, natural reply in character (1-3 sentences). No name prefix.")
            appendLine("- If NO (nothing meaningful to add, or it's not your place): reply with exactly: [PASS]")
        }
        appendLine()
        appendLine("Rules:")
        appendLine("• Stay in character. Be brief and real — like texting, not a speech.")
        appendLine("• Don't summarize what others said. React, question, add something new.")
        appendLine("• Reply in the same language as the conversation.")
        appendLine("• You may use tools (max 3 calls) before writing your message.")
    }

    private fun parseMentions(text: String): List<String> =
        Regex("@([\\w\\u4e00-\\u9fff·]+)").findAll(text).map { it.groupValues[1] }.toList()

    private fun GroupMessage.toEntity() = com.mobileclaw.memory.db.GroupMessageEntity(
        id = id,
        groupId = groupId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        text = text,
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
你是一位专业心理分析师。基于以下用户画像数据，写一段人格分析（约200字），用第二人称（"你"）表达，语气温暖而专业。

请包含：
1. MBTI人格类型推断（如 INTJ、ENFP 等）及一句核心说明
2. 3-4个核心人格特质关键词
3. 沟通与社交风格特点
4. 主要优势与潜在成长空间
5. 一句画龙点睛的总结

用户画像数据：
$factsText

注意：直接开始分析，用流畅自然的语言，不要说"根据数据"之类的套话。如果数据较少，基于已有信息大胆推断。
""".trimIndent()
            val summary = runCatching {
                llm.chat(ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = "你是专业心理分析师，擅长人格分析与自我认知。"),
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
            .ifBlank { "暂无已知信息" }
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
[{"question":"问题文字","hint":"此题探测XX倾向","answers":["A选项","B选项","C选项","D选项"],"factKey":"profile.${dimensionId}.xxx"}]
""".trimIndent()
        val content = runCatching {
            llm.chat(ChatRequest(
                messages = listOf(
                    Message(role = "system", content = "你是专业心理学家，只输出严格JSON。"),
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
            config.update(config.snapshot().copy(model = model))
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    fun fetchModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = runCatching { OpenAiGateway(config).fetchModels() }.getOrDefault(emptyList())
            if (models.isNotEmpty()) _uiState.update { it.copy(availableModels = models) }
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
            runCatching { loader.persist(def) }
            refreshPromotableSkills()
        }
    }

    private fun refreshPromotableSkills() {
        val all = registry.all().map { it.meta }
        val promotable = all.filter { !it.isBuiltin && it.injectionLevel == 2 }
        _uiState.update { it.copy(promotableSkills = promotable, allSkills = all) }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun buildPriorContext(): String {
        val msgs = _uiState.value.messages
        if (msgs.isEmpty()) return ""
        return msgs.takeLast(20).joinToString("\n") { msg ->
            when (msg.role) {
                MessageRole.USER -> "User: ${msg.text}"
                MessageRole.AGENT -> "Agent: ${msg.text}"
            }
        }
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
            MemorySkill(app.semanticMemory),
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
        result += if (idx == 0) "${app.icon} 继续完善「${app.title}」？"
                  else "${app.icon} 给「${app.title}」添加新功能？"
    }

    // 2. Emotional context from recent conversations
    detectEmotionSuggestion(recentUserMessages, profileFacts)?.let { result += it }

    // 3. Episode-based continuations — skip app-creation goals
    val appKeywords = listOf("app", "应用", "工具", "程序", "软件", "界面", "网页", "html", "创建", "制作")
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
        Regex("烦躁|烦恼|烦人|很烦|好烦|心烦|焦虑|焦急|不安|郁闷|难受|崩溃|抓狂").containsMatchIn(text) ->
            "🌿 让我帮你查查如何静心放松？"
        Regex("(?<![不没])累|疲惫|疲劳|没精神|没劲|累死了|好累").containsMatchIn(text) ->
            "😴 要不要聊聊如何快速恢复精力？"
        Regex("压力|stress|deadline|截止日|赶着|加班|忙死|喘不过气").containsMatchIn(text) ->
            "⏳ 帮你梳理一下优先级，先做最重要的？"
        Regex("无聊|闲着|没事做|不知道做什么|没啥事").containsMatchIn(text) ->
            "✨ 要我推荐点有趣的事情来做吗？"
        Regex("迷茫|不知所措|纠结|困惑|想不通").containsMatchIn(text) ->
            "🔍 聊聊你的困惑，帮你理清思路？"
        else -> {
            val emotionalVal = profileFacts.entries
                .find { it.key.contains("emotional") || it.key.contains("stability") }
                ?.value?.lowercase()
            if (emotionalVal != null &&
                Regex("不稳|焦虑|波动|容易崩|经常失眠|较差|消耗大").containsMatchIn(emotionalVal)
            ) "💆 今天感觉怎么样？需要聊聊或帮你减减压？" else null
        }
    }
}

private fun transformEpisodeToSuggestion(goalText: String): String? {
    val lower = goalText.lowercase()
    val topic = goalText.trim()
        .replace(Regex("^(帮我|帮助我|请帮我|请|帮|让我|我想要|我要|能否|可以帮我|你帮我)\\s*"), "")
        .trim()
    if (topic.length < 2) return null
    val short = topic.take(15)
    return when {
        lower.contains("搜索") || lower.contains("搜一下") || lower.contains("查一查") || lower.contains("查查") ->
            "🔍 继续深入搜索${short.replace(Regex("^搜索|^查"), "").trim().take(12)}？"
        lower.contains("分析") || lower.contains("统计") || lower.contains("数据") ->
            "📊 进一步分析${short}的细节？"
        lower.contains("写") || lower.contains("撰写") || lower.contains("起草") || lower.contains("文案") ->
            "📝 继续完善${short}的内容？"
        lower.contains("学") || lower.contains("了解") || lower.contains("研究") ->
            "📚 继续深入学习${short}？"
        lower.contains("翻译") ->
            "🌐 继续翻译剩余内容？"
        else -> "💡 继续探索「${short}」相关话题？"
    }
}

private fun buildProfileSuggestions(profileFacts: Map<String, String>): List<String> {
    val profession = profileFacts.entries
        .find { it.key.contains("profession") || it.key.contains("job") || it.key.contains("occupation") }
        ?.value?.lowercase() ?: ""
    val interests = profileFacts.entries
        .filter { it.key.contains("interest") || it.key.contains("hobby") || it.key.contains("兴趣") }
        .joinToString(" ") { it.value.lowercase() }

    val suggestions = mutableListOf<String>()

    when {
        profession.contains("开发") || profession.contains("程序") || profession.contains("工程") || profession.contains("dev") -> {
            suggestions += "帮我审查这段代码并提出优化建议"
            suggestions += "搜索今日技术圈动态"
        }
        profession.contains("设计") -> {
            suggestions += "帮我搜索近期UI设计灵感"
            suggestions += "生成一张创意设计图"
        }
        profession.contains("学生") || profession.contains("学习") || profession.contains("教育") -> {
            suggestions += "帮我整理今天的学习笔记"
            suggestions += "搜索这个知识点的详细解释"
        }
        profession.contains("运营") || profession.contains("市场") || profession.contains("营销") -> {
            suggestions += "搜索行业最新动态和竞品信息"
            suggestions += "帮我写一篇产品推广文案"
        }
        profession.contains("写作") || profession.contains("媒体") || profession.contains("内容") -> {
            suggestions += "帮我润色这段文字"
            suggestions += "搜索今天的热点话题"
        }
        profession.contains("金融") || profession.contains("投资") || profession.contains("财务") -> {
            suggestions += "搜索今日市场行情和财经资讯"
            suggestions += "帮我整理一份收支分析"
        }
    }

    when {
        interests.contains("阅读") || interests.contains("书") -> suggestions += "推荐一本适合我的书并简介"
        interests.contains("健身") || interests.contains("运动") || interests.contains("跑步") -> suggestions += "帮我制定今天的运动计划"
        interests.contains("旅行") || interests.contains("旅游") -> suggestions += "帮我搜索旅行目的地攻略"
        interests.contains("美食") || interests.contains("烹饪") || interests.contains("做饭") -> suggestions += "推荐一道今天可以做的菜"
        interests.contains("游戏") -> suggestions += "搜索最新的游戏资讯"
        interests.contains("摄影") -> suggestions += "分析这张图片的构图和色彩"
    }

    return suggestions.take(3)
}

private fun addTaskEmoji(text: String): String {
    // Skip if already starts with a non-alphanumeric char (emoji / symbol)
    if (text.isNotEmpty() && !text[0].isLetterOrDigit() && !text[0].isWhitespace()) return text

    val lower = text.lowercase()
    val emoji = when {
        lower.contains("微信") || lower.contains("whatsapp") || lower.contains("telegram") -> "💬"
        lower.contains("邮件") || lower.contains("email") -> "📧"
        lower.contains("短信") || lower.contains("发消息") || lower.contains("发送消息") -> "💬"
        lower.contains("天气") || lower.contains("气温") -> "🌤"
        lower.contains("翻译") -> "🌐"
        lower.contains("新闻") || lower.contains("资讯") || lower.contains("热点") -> "📰"
        lower.contains("搜索") || lower.contains("查找") || lower.contains("查询") || lower.contains("找") -> "🔍"
        lower.contains("代码") || lower.contains("编程") || lower.contains("程序") || lower.contains("python") || lower.contains("脚本") || lower.contains("审查") || lower.contains("开发") -> "💻"
        lower.contains("shell") || lower.contains("命令") || lower.contains("终端") -> "⌨️"
        lower.contains("截图") || lower.contains("屏幕") -> "📸"
        lower.contains("图片") || lower.contains("图像") || lower.contains("照片") -> "🖼️"
        lower.contains("生成图") || lower.contains("画") || lower.contains("设计图") || lower.contains("灵感") -> "🎨"
        lower.contains("视频") -> "🎬"
        lower.contains("音乐") || lower.contains("播放") || lower.contains("歌单") -> "🎵"
        lower.contains("文件") || lower.contains("文档") || lower.contains("保存") -> "📁"
        lower.contains("笔记") || lower.contains("整理") && lower.contains("学习") -> "🗒️"
        lower.contains("报告") || lower.contains("总结") || lower.contains("摘要") -> "📋"
        lower.contains("写") || lower.contains("撰写") || lower.contains("起草") || lower.contains("文案") || lower.contains("润色") -> "📝"
        lower.contains("分析") -> "📊"
        lower.contains("数据") || lower.contains("统计") -> "📈"
        lower.contains("购物") || lower.contains("买") || lower.contains("价格") -> "🛒"
        lower.contains("记账") || lower.contains("收支") || lower.contains("预算") || lower.contains("财经") || lower.contains("行情") -> "💰"
        lower.contains("计划") || lower.contains("日程") || lower.contains("安排") -> "📅"
        lower.contains("提醒") || lower.contains("闹钟") -> "⏰"
        lower.contains("打开") || lower.contains("启动") -> "📱"
        lower.contains("运动") || lower.contains("健身") || lower.contains("跑步") -> "🏃"
        lower.contains("饮食") || lower.contains("食谱") || lower.contains("美食") || lower.contains("菜") -> "🍜"
        lower.contains("旅行") || lower.contains("旅游") || lower.contains("攻略") -> "✈️"
        lower.contains("书") || lower.contains("阅读") || lower.contains("推荐") && lower.contains("书") -> "📚"
        lower.contains("技术") || lower.contains("知识") || lower.contains("解释") -> "💡"
        lower.contains("网页") || lower.contains("网站") || lower.contains("浏览") -> "🌐"
        lower.contains("设置") || lower.contains("配置") -> "⚙️"
        lower.contains("安装") || lower.contains("下载") -> "📦"
        lower.contains("游戏") -> "🎮"
        lower.contains("摄影") || lower.contains("构图") -> "📷"
        else -> "✨"
    }
    return "$emoji $text"
}

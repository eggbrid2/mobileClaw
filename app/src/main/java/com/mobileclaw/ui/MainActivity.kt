package com.mobileclaw.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileclaw.ClawApplication
import com.mobileclaw.permission.PermissionItem
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* onResume fires after dialog closes and will re-check */ }

    override fun attachBaseContext(newBase: Context) {
        val language = ClawApplication.instance.agentConfig.language
        super.attachBaseContext(wrapLocale(newBase, language))
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionManager = ClawApplication.instance.permissionManager

        setContent {
            val vm: MainViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            val initialConfig = remember { ClawApplication.instance.agentConfig.snapshot() }
            val configSnapshot by uiState.config.collectAsState(initial = initialConfig)

            LaunchedEffect(Unit) {
                vm.languageChanged.collect { recreate() }
            }

            ClawTheme(
                darkTheme = configSnapshot.darkTheme,
                accentColor = configSnapshot.accentColor,
            ) {
                // Force white status bar icons on HOME (dark wallpaper), follow theme elsewhere
                val lightStatusBars = uiState.currentPage != AppPage.HOME && !configSnapshot.darkTheme
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = lightStatusBars
                }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    val avatarPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
                        if (uri != null) vm.setUserAvatarUri(uri.toString())
                    }

                    // Launch MiniAppActivity when AI opens an app (e.g. after creation)
                    val pendingAppId = uiState.openAppId
                    LaunchedEffect(pendingAppId) {
                        if (pendingAppId != null) {
                            startActivity(MiniAppActivity.intent(this@MainActivity, pendingAppId))
                            vm.clearPendingAppOpen()
                        }
                    }

                    // Back stack navigation
                    BackHandler(enabled = uiState.canNavigateBack) {
                        vm.navigateBack()
                    }
                    // Drawer close: highest priority (registered last → wins when both are enabled)
                    BackHandler(enabled = drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            DrawerContent(
                                sessions = uiState.sessions,
                                currentSessionId = uiState.currentSessionId,
                                currentRole = uiState.currentRole,
                                userConfigEntries = uiState.userConfigEntries,
                                userAvatarUri = uiState.userAvatarUri,
                                onNewSession = {
                                    vm.createNewSession()
                                    scope.launch { drawerState.close() }
                                },
                                onSelectSession = { sessionId ->
                                    vm.loadSession(sessionId)
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteSession = { vm.deleteSession(it) },
                                onOpenSettings = {
                                    vm.navigate(AppPage.SETTINGS)
                                    scope.launch { drawerState.close() }
                                },
                                onPickAvatar = {
                                    avatarPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                },
                                onOpenUserConfig = {
                                    vm.navigate(AppPage.USER_CONFIG)
                                    scope.launch { drawerState.close() }
                                },
                            )
                        },
                        gesturesEnabled = uiState.currentPage == AppPage.CHAT,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Block all touches to ChatScreen when another page is active.
                            // Without this, the ChatScreen's BasicTextField below receives
                            // key/touch events even when overlaid by another full-screen page.
                            val chatActive = uiState.currentPage == AppPage.CHAT
                            Box(modifier = Modifier.fillMaxSize().pointerInput(chatActive) {
                                if (!chatActive) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                                .changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }) {
                                ChatScreen(
                                    uiState = uiState,
                                    onSendGoal = { vm.runTask(it) },
                                    onStop = { vm.stopTask() },
                                    onOpenSettings = { vm.navigate(AppPage.SETTINGS) },
                                    onOpenSkillManager = { vm.navigate(AppPage.SKILLS) },
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onAttachImage = { vm.setInputImage(it) },
                                    onAttachFile = { vm.setFileAttachment(it) },
                                    onOpenProfile = { vm.navigate(AppPage.PROFILE) },
                                    onModelChange = { vm.setModel(it) },
                                    onFetchModels = { vm.fetchModels() },
                                    onOpenHelp = { vm.navigate(AppPage.HELP) },
                                    onOpenHtmlViewer = { vm.openHtmlViewer(it) },
                                    onOpenBrowser = { vm.navigateToBrowser(it) },
                                    onRenameSession = { id, title -> vm.renameSession(id, title) },
                                    onOpenDesktop = { vm.navigate(AppPage.HOME) },
                                    onSwitchRole = { vm.navigate(AppPage.ROLES) },
                                    onOpenAccessibilitySettings = { startActivity(permissionManager.openAccessibilitySettings()) },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.HOME,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                HomePage(
                                    currentRole = uiState.currentRole,
                                    sessions = uiState.sessions,
                                    miniApps = uiState.miniApps,
                                    darkTheme = configSnapshot.darkTheme,
                                    onNavigate = { vm.navigate(it) },
                                    onOpenApp = { appId -> startActivity(MiniAppActivity.intent(this@MainActivity, appId)) },
                                    onDeleteApp = { vm.deleteApp(it) },
                                    onSelectSession = { vm.loadSession(it) },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.SETTINGS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                SettingsPage(
                                    config = uiState.config,
                                    virtualDisplayManager = ClawApplication.instance.virtualDisplayManager,
                                    vdTestResult = uiState.virtualDisplayTestResult,
                                    privServerConnected = uiState.privServerConnected,
                                    onSave = { vm.saveConfig(it) },
                                    onBack = { vm.navigateBack() },
                                    onTestVirtualDisplay = { vm.testVirtualDisplay() },
                                    onCheckPrivServer = { vm.checkPrivServer() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.SKILLS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                SkillsPage(
                                    allSkills = uiState.allSkills,
                                    skillNotes = uiState.skillNotes,
                                    skillNoteGenerating = uiState.skillNoteGenerating,
                                    onPromote = { vm.promoteSkill(it) },
                                    onDemote = { vm.demoteSkill(it) },
                                    onDelete = { vm.deleteSkill(it) },
                                    onInstallMarketSkill = { vm.installMarketSkill(it) },
                                    onSaveNote = { id, note -> vm.saveSkillNote(id, note) },
                                    onGenerateNote = { id, name, desc -> vm.generateSkillNote(id, name, desc) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.PROFILE,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                ProfilePage(
                                    facts = uiState.profileFacts,
                                    episodes = uiState.recentEpisodes,
                                    isLoading = uiState.profileLoading,
                                    isExtracting = uiState.profileExtracting,
                                    conversationCount = uiState.conversationCount,
                                    onBack = { vm.navigateBack() },
                                    onRefreshExtraction = { vm.triggerProfileExtraction() },
                                    onSetFact = { key, value -> vm.setProfileFact(key, value) },
                                    personalitySummary = uiState.personalitySummary,
                                    personalitySummaryLoading = uiState.personalitySummaryLoading,
                                    onGenerateSummary = { vm.generatePersonalitySummary() },
                                    dimensionQuizzes = uiState.dimensionQuizzes,
                                    dimensionQuizLoading = uiState.dimensionQuizLoading,
                                    onGenerateDimensionQuiz = { id, title -> vm.generateDimensionQuiz(id, title) },
                                    onPrewarmQuizzes = { dims -> vm.prewarmAllDimensionQuizzes(dims) },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.ROLES,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                RolesPage(
                                    availableRoles = uiState.availableRoles,
                                    currentRole = uiState.currentRole,
                                    onActivate = { vm.setActiveRole(it) },
                                    onSave = { vm.saveCustomRole(it) },
                                    onDelete = { vm.deleteCustomRole(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.USER_CONFIG,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                UserConfigPage(
                                    entries = uiState.userConfigEntries,
                                    onSet = { key, value, desc -> vm.setUserConfigEntry(key, value, desc) },
                                    onDelete = { key -> vm.deleteUserConfigEntry(key) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.APPS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                AppLauncherPage(
                                    miniApps = uiState.miniApps,
                                    onOpen = { appId -> startActivity(MiniAppActivity.intent(this@MainActivity, appId)) },
                                    onDelete = { vm.deleteApp(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.CONSOLE,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                ConsolePage(
                                    serverUrl = uiState.consoleServerUrl,
                                    isRunning = uiState.isRunning,
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.HELP,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                HelpPage(onBack = { vm.navigateBack() })
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.GROUPS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                GroupsPage(
                                    groups = uiState.groups,
                                    availableRoles = uiState.availableRoles,
                                    onOpenGroup = { vm.openGroupChat(it) },
                                    onCreateGroup = { vm.createGroup(it) },
                                    onDeleteGroup = { vm.deleteGroup(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.GROUP_CHAT,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                val group = uiState.openGroup
                                if (group != null) {
                                    GroupChatScreen(
                                        group = group,
                                        messages = uiState.groupMessages,
                                        availableRoles = uiState.availableRoles,
                                        isRunning = uiState.groupRunning,
                                        typingAgentId = uiState.groupTypingAgentId,
                                        streamingText = uiState.groupStreamingText,
                                        onSend = { vm.sendGroupMessage(it) },
                                        onStop = { vm.stopGroupChat() },
                                        onBack = { vm.closeGroupChat() },
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.BROWSER,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                BrowserPage(
                                    initialUrl = uiState.browserUrl,
                                    onBack = { vm.navigateBack() },
                                    onSendToAgent = { vm.runTask(it) },
                                )
                            }

                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.SKILL_MARKET,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                SkillMarketPage(
                                    installedIds = uiState.allSkills.map { it.id }.toSet(),
                                    onInstall = { vm.installMarketSkill(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }

                            // HTML attachment viewer at Activity level so addJavascriptInterface binds
                            val htmlAttachment = uiState.openHtmlAttachment
                            if (htmlAttachment != null) {
                                HtmlAttachmentViewer(
                                    attachment = htmlAttachment,
                                    onClose = { vm.closeHtmlViewer() },
                                    onAskAgent = { vm.runTask(it) },
                                )
                            }
                        }
                    }
            }
        }
    }

    companion object {
        fun wrapLocale(context: Context, language: String): Context {
            if (language == "auto" || language.isBlank()) return context
            val locale = Locale.forLanguageTag(language)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}

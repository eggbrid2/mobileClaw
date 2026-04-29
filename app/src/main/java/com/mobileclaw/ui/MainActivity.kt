package com.mobileclaw.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
                val permissionsGranted = remember(resumeTick) { permissionManager.allGranted() }

                if (!permissionsGranted) {
                    PermissionGuideScreen(
                        pending = remember(resumeTick) { permissionManager.pendingPermissions() },
                        onOpenSettings = { item ->
                            when (item) {
                                is PermissionItem.Accessibility ->
                                    startActivity(permissionManager.openAccessibilitySettings())
                                is PermissionItem.Overlay ->
                                    startActivity(permissionManager.openOverlaySettings())
                                is PermissionItem.BatteryOptimization ->
                                    startActivity(permissionManager.openBatteryOptimizationRequest())
                                is PermissionItem.Notification ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                is PermissionItem.RomAutoStart ->
                                    startActivity(permissionManager.openRomSettingFor(item))
                                is PermissionItem.RomBackgroundPop ->
                                    startActivity(permissionManager.openRomSettingFor(item))
                            }
                        }
                    )
                } else {
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    // Sub-page back: lower priority (registered first)
                    BackHandler(enabled = uiState.currentPage != AppPage.CHAT) {
                        vm.navigate(AppPage.CHAT)
                    }
                    // Drawer close back: higher priority (registered last → wins when both are enabled)
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
                                currentPage = uiState.currentPage,
                                onNewSession = {
                                    vm.createNewSession()
                                    scope.launch { drawerState.close() }
                                },
                                onSelectSession = { sessionId ->
                                    vm.loadSession(sessionId)
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteSession = { vm.deleteSession(it) },
                                onNavigate = { page ->
                                    vm.navigate(page)
                                    scope.launch { drawerState.close() }
                                },
                            )
                        },
                        gesturesEnabled = uiState.currentPage == AppPage.CHAT,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ChatScreen(
                                uiState = uiState,
                                onSendGoal = { vm.runTask(it) },
                                onStop = { vm.stopTask() },
                                onOpenSettings = { vm.navigate(AppPage.SETTINGS) },
                                onOpenSkillManager = { vm.navigate(AppPage.SKILLS) },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onAttachImage = { vm.setInputImage(it) },
                                onOpenProfile = { vm.navigate(AppPage.PROFILE) },
                                onModelChange = { vm.setModel(it) },
                                onFetchModels = { vm.fetchModels() },
                                onOpenHelp = { vm.navigate(AppPage.HELP) },
                                onOpenHtmlViewer = { vm.openHtmlViewer(it) },
                            )
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
                                    onBack = { vm.navigate(AppPage.CHAT) },
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
                                    onSaveNote = { id, note -> vm.saveSkillNote(id, note) },
                                    onGenerateNote = { id, name, desc -> vm.generateSkillNote(id, name, desc) },
                                    onBack = { vm.navigate(AppPage.CHAT) },
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
                                    onBack = { vm.navigate(AppPage.CHAT) },
                                    onRefreshExtraction = { vm.triggerProfileExtraction() },
                                    onSetFact = { key, value -> vm.setProfileFact(key, value) },
                                    personalitySummary = uiState.personalitySummary,
                                    personalitySummaryLoading = uiState.personalitySummaryLoading,
                                    onGenerateSummary = { vm.generatePersonalitySummary() },
                                    dimensionQuizzes = uiState.dimensionQuizzes,
                                    dimensionQuizLoading = uiState.dimensionQuizLoading,
                                    onGenerateDimensionQuiz = { id, title -> vm.generateDimensionQuiz(id, title) },
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
                                    onBack = { vm.navigate(AppPage.CHAT) },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.APPS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                AppLauncherPage(
                                    miniApps = uiState.miniApps,
                                    openAppId = uiState.openAppId,
                                    onOpen = { vm.openApp(it) },
                                    onCloseApp = { vm.closeApp() },
                                    onDelete = { vm.deleteApp(it) },
                                    onBack = { vm.navigate(AppPage.CHAT) },
                                    onAskAgent = { vm.runTask(it) },
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
                                    onBack = { vm.navigate(AppPage.CHAT) },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.HELP,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                HelpPage(onBack = { vm.navigate(AppPage.CHAT) })
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

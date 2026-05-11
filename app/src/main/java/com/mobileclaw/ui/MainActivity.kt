package com.mobileclaw.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.key
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileclaw.ClawApplication
import com.mobileclaw.permission.PermissionItem
import kotlinx.coroutines.launch
import java.util.Locale
import com.mobileclaw.R
import com.mobileclaw.str

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
                vm.languageChanged.collect { lang ->
                    ClawApplication.instance.applyLanguage(lang)
                    recreate()
                }
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

                    var userAvatarCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

                    val avatarPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
                        if (uri != null) {
                            applicationContext.contentResolver.runCatching {
                                takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            userAvatarCropUri = uri
                        }
                    }

                    if (userAvatarCropUri != null) {
                        CropImageDialog(
                            imageUri = userAvatarCropUri!!,
                            onDismiss = { userAvatarCropUri = null },
                            onCropped = { path -> vm.setUserAvatarUri(path); userAvatarCropUri = null },
                        )
                    }

                    // Launch MiniAppActivity when AI opens an app (e.g. after creation)
                    val pendingAppId = uiState.openAppId
                    LaunchedEffect(pendingAppId) {
                        if (pendingAppId != null) {
                            startActivity(MiniAppActivity.intent(this@MainActivity, pendingAppId))
                            vm.clearPendingAppOpen()
                        }
                    }

                    // Launch AiPageActivity or pin shortcut when AI requests it
                    val pendingAiPageId = uiState.openAiPageId
                    LaunchedEffect(pendingAiPageId) {
                        if (pendingAiPageId != null) {
                            if (pendingAiPageId.startsWith("pin:")) {
                                val pageId = pendingAiPageId.removePrefix("pin:")
                                val def = uiState.aiPages.firstOrNull { it.id == pageId }
                                if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                            } else {
                                startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, pendingAiPageId))
                            }
                            vm.clearAiPageOpen()
                        }
                    }

                    val htmlViewerOpen = uiState.openHtmlAttachment != null

                    // Back stack navigation
                    BackHandler(enabled = !htmlViewerOpen && uiState.canNavigateBack && uiState.currentPage != AppPage.BROWSER) {
                        vm.navigateBack()
                    }
                    // HOME → CHAT: pressing back on the launcher goes to chat
                    BackHandler(enabled = !htmlViewerOpen && !uiState.canNavigateBack && uiState.currentPage == AppPage.HOME) {
                        vm.navigate(AppPage.CHAT)
                    }
                    // CHAT → exit: double-press within 2 s to exit
                    var lastBackPressMs by remember { mutableStateOf(0L) }
                    BackHandler(enabled = !htmlViewerOpen && !uiState.canNavigateBack && uiState.currentPage == AppPage.CHAT) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastBackPressMs < 2000L) {
                            finish()
                        } else {
                            lastBackPressMs = now
                            Toast.makeText(this@MainActivity, str(R.string.mainactivity_515fdc), Toast.LENGTH_SHORT).show()
                        }
                    }
                    // Drawer close: highest priority (registered last → wins when both are enabled)
                    BackHandler(enabled = drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    }

                    var classicTab by remember { mutableStateOf(ClassicTab.CHAT) }
                    var classicChatTab by remember { mutableStateOf(ClassicChatTab.SINGLE) }
                    var classicSkillTab by remember { mutableStateOf(ClassicSkillTab.LOCAL) }
                    var classicCenterTab by remember { mutableStateOf(ClassicCenterTab.MINI_APP) }
                    var classicCreateGroupKey by remember { mutableIntStateOf(0) }
                    val isClassicStyle = configSnapshot.uiStyle == "classic"
                    LaunchedEffect(uiState.currentPage) {
                        classicTab = when (uiState.currentPage) {
                            AppPage.CHAT -> ClassicTab.CHAT
                            AppPage.GROUPS, AppPage.GROUP_CHAT -> ClassicTab.CHAT
                            AppPage.SKILLS, AppPage.SKILL_MARKET -> ClassicTab.SKILL
                            AppPage.APPS, AppPage.AI_PAGES -> ClassicTab.CENTER
                            AppPage.ROLES, AppPage.ROLE_EDIT -> ClassicTab.ROLES
                            AppPage.PROFILE, AppPage.CONSOLE, AppPage.VPN, AppPage.SETTINGS, AppPage.USER_CONFIG -> ClassicTab.ME
                            else -> classicTab
                        }
                        if (uiState.currentPage == AppPage.GROUPS || uiState.currentPage == AppPage.GROUP_CHAT) classicChatTab = ClassicChatTab.GROUP
                        if (uiState.currentPage == AppPage.SKILL_MARKET) classicSkillTab = ClassicSkillTab.MARKET
                        if (uiState.currentPage == AppPage.AI_PAGES) classicCenterTab = ClassicCenterTab.AI_PAGE
                    }
                    val classicTitle = when (classicTab) {
                        ClassicTab.CHAT -> str(R.string.home_859362)
                        ClassicTab.SKILL -> str(R.string.drawer_skills)
                        ClassicTab.CENTER -> str(R.string.classic_center)
                        ClassicTab.ROLES -> str(R.string.drawer_roles)
                        ClassicTab.ME -> str(R.string.classic_me)
                    }
                    val classicTopTabs = when (classicTab) {
                        ClassicTab.CHAT -> listOf(
                            str(R.string.classic_single_chat) to (classicChatTab == ClassicChatTab.SINGLE),
                            str(R.string.classic_group_chat) to (classicChatTab == ClassicChatTab.GROUP),
                        )
                        ClassicTab.SKILL -> listOf(
                            str(R.string.classic_local) to (classicSkillTab == ClassicSkillTab.LOCAL),
                            str(R.string.skills_0e0282) to (classicSkillTab == ClassicSkillTab.MARKET),
                        )
                        ClassicTab.CENTER -> listOf(
                            str(R.string.classic_mini_apps) to (classicCenterTab == ClassicCenterTab.MINI_APP),
                            str(R.string.home_2d20d5) to (classicCenterTab == ClassicCenterTab.AI_PAGE),
                        )
                        ClassicTab.ROLES,
                        ClassicTab.ME -> emptyList()
                    }
                    val onClassicTopTab: (Int) -> Unit = { index ->
                        when (classicTab) {
                            ClassicTab.CHAT -> {
                                classicChatTab = if (index == 0) ClassicChatTab.SINGLE else ClassicChatTab.GROUP
                                vm.navigate(if (index == 0) AppPage.CHAT else AppPage.GROUPS)
                            }
                            ClassicTab.SKILL -> {
                                classicSkillTab = if (index == 0) ClassicSkillTab.LOCAL else ClassicSkillTab.MARKET
                                vm.navigate(if (index == 0) AppPage.SKILLS else AppPage.SKILL_MARKET)
                            }
                            ClassicTab.CENTER -> {
                                classicCenterTab = if (index == 0) ClassicCenterTab.MINI_APP else ClassicCenterTab.AI_PAGE
                                vm.navigate(if (index == 0) AppPage.APPS else AppPage.AI_PAGES)
                            }
                            ClassicTab.ROLES,
                            ClassicTab.ME -> Unit
                        }
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
                        gesturesEnabled = uiState.currentPage == AppPage.CHAT && uiState.openHtmlAttachment == null,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isClassicStyle) {
                                ClassicScaffold(
                                    selected = classicTab,
                                    onSelect = { tab ->
                                        classicTab = tab
                                        when (tab) {
                                            ClassicTab.CHAT -> vm.navigate(if (classicChatTab == ClassicChatTab.GROUP) AppPage.GROUPS else AppPage.CHAT)
                                            ClassicTab.SKILL -> vm.navigate(if (classicSkillTab == ClassicSkillTab.MARKET) AppPage.SKILL_MARKET else AppPage.SKILLS)
                                            ClassicTab.CENTER -> vm.navigate(if (classicCenterTab == ClassicCenterTab.AI_PAGE) AppPage.AI_PAGES else AppPage.APPS)
                                            ClassicTab.ROLES -> vm.navigate(AppPage.ROLES)
                                            ClassicTab.ME -> Unit
                                        }
                                    },
                                    title = classicTitle,
                                    tabs = classicTopTabs,
                                    onTab = onClassicTopTab,
                                    leadingAction = if (classicTab == ClassicTab.CHAT && classicChatTab == ClassicChatTab.SINGLE) {
                                        { ClassicSessionAction { scope.launch { drawerState.open() } } }
                                    } else null,
                                    trailingAction = if (classicTab == ClassicTab.CHAT && classicChatTab == ClassicChatTab.GROUP) {
                                        { ClassicAddGroupAction { classicCreateGroupKey += 1 } }
                                    } else null,
                                ) {
                                    when (classicTab) {
                                        ClassicTab.CHAT -> {
                                            Column(Modifier.fillMaxSize()) {
                                                Box(Modifier.fillMaxSize()) {
                                                    if (classicChatTab == ClassicChatTab.SINGLE) {
                                                        ChatScreen(
                                                            uiState = uiState,
                                                            onSendGoal = { vm.runTask(it) },
                                                            onStop = { vm.stopTask() },
                                                            onOpenSettings = { vm.navigate(AppPage.SETTINGS) },
                                                            onOpenSkillManager = { vm.navigate(AppPage.SKILLS) },
                                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                                            onAttachImage = { vm.setInputImage(it) },
                                                            onSendImage = { image, prompt -> vm.sendImageMessage(image, prompt) },
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
                                                            onLoadMoreHistory = { vm.loadMoreHistory() },
                                                            classicMode = true,
                                                        )
                                                    } else {
                                                        GroupsPage(
                                                            groups = uiState.groups,
                                                            groupPreviews = uiState.groupPreviews,
                                                            availableRoles = uiState.availableRoles,
                                                            onOpenGroup = { vm.openGroupChat(it) },
                                                            onCreateGroup = { vm.createGroup(it) },
                                                            onDeleteGroup = { vm.deleteGroup(it) },
                                                            onBack = { classicChatTab = ClassicChatTab.SINGLE; vm.navigate(AppPage.CHAT) },
                                                            showHeader = false,
                                                            createRequestKey = classicCreateGroupKey,
                                                            showCreateFab = false,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        ClassicTab.SKILL -> {
                                            Column(Modifier.fillMaxSize()) {
                                                Box(Modifier.fillMaxSize()) {
                                                    if (classicSkillTab == ClassicSkillTab.LOCAL) {
                                                        SkillsPage(
                                                            allSkills = uiState.allSkills,
                                                            skillNotes = uiState.skillNotes,
                                                            skillNoteGenerating = uiState.skillNoteGenerating,
                                                            skillLevelOverrides = uiState.skillLevelOverrides,
                                                            onPromote = { vm.promoteSkill(it) },
                                                            onDemote = { vm.demoteSkill(it) },
                                                            onDelete = { vm.deleteSkill(it) },
                                                            onSetSkillLevel = { id, level -> vm.setSkillLevel(id, level) },
                                                            onInstallMarketSkill = { vm.installMarketSkill(it) },
                                                            onSaveNote = { id, note -> vm.saveSkillNote(id, note) },
                                                            onGenerateNote = { id, name, desc -> vm.generateSkillNote(id, name, desc) },
                                                            onBack = { vm.navigate(AppPage.CHAT) },
                                                            showHeader = false,
                                                        )
                                                    } else {
                                                        SkillMarketPage(
                                                            installedIds = uiState.allSkills.map { it.id }.toSet(),
                                                            onInstall = { vm.installMarketSkill(it) },
                                                            onBack = { classicSkillTab = ClassicSkillTab.LOCAL; vm.navigate(AppPage.SKILLS) },
                                                            showHeader = false,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        ClassicTab.CENTER -> {
                                            Column(Modifier.fillMaxSize()) {
                                                Box(Modifier.fillMaxSize()) {
                                                    if (classicCenterTab == ClassicCenterTab.MINI_APP) {
                                                        AppLauncherPage(
                                                            miniApps = uiState.miniApps,
                                                            onOpen = { appId -> startActivity(MiniAppActivity.intent(this@MainActivity, appId)) },
                                                            onDelete = { vm.deleteApp(it) },
                                                            onBack = { vm.navigate(AppPage.CHAT) },
                                                            showHeader = false,
                                                        )
                                                    } else {
                                                        AiPagesPage(
                                                            pages = uiState.aiPages,
                                                            onOpen = { startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, it)) },
                                                            onDelete = { vm.deleteAiPage(it) },
                                                            onPinShortcut = {
                                                                val def = uiState.aiPages.firstOrNull { p -> p.id == it }
                                                                if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                                                            },
                                                            onBack = { classicCenterTab = ClassicCenterTab.MINI_APP; vm.navigate(AppPage.APPS) },
                                                            showHeader = false,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        ClassicTab.ROLES -> RolesPage(
                                            availableRoles = uiState.availableRoles,
                                            currentRole = uiState.currentRole,
                                            onActivate = { vm.setActiveRole(it) },
                                            onEdit = { vm.editRole(it) },
                                            onDelete = { vm.deleteCustomRole(it) },
                                            onBack = { vm.navigate(AppPage.CHAT) },
                                            showHeader = false,
                                        )
                                        ClassicTab.ME -> ClassicMePage(
                                            userAvatarUri = uiState.userAvatarUri,
                                            userName = uiState.userConfigEntries["user.name"]?.value ?: "",
                                            sessionCount = uiState.sessions.size,
                                            miniApps = uiState.miniApps,
                                            onProfile = { vm.navigate(AppPage.PROFILE) },
                                            onConsole = { vm.navigate(AppPage.CONSOLE) },
                                            onVpn = { vm.navigate(AppPage.VPN) },
                                            onSettings = { vm.navigate(AppPage.SETTINGS) },
                                        )
                                    }
                                }
                            } else {
                                // Block all touches to ChatScreen when another page is active.
                                // Without this, the ChatScreen's BasicTextField below receives
                                // key/touch events even when overlaid by another full-screen page.
                                val chatActive = uiState.currentPage == AppPage.CHAT
                                Box(modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (chatActive) 1f else 0f }
                                    .pointerInput(chatActive) {
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
                                        onSendImage = { image, prompt -> vm.sendImageMessage(image, prompt) },
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
                                        onLoadMoreHistory = { vm.loadMoreHistory() },
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
                                    onOpenHelp = { vm.navigate(AppPage.HELP) },
                                    onTestVirtualDisplay = { vm.testVirtualDisplay() },
                                    onCheckPrivServer = { vm.checkPrivServer() },
                                )
                            }
                            AnimatedVisibility(
                                visible = !isClassicStyle && uiState.currentPage == AppPage.SKILLS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                SkillsPage(
                                    allSkills = uiState.allSkills,
                                    skillNotes = uiState.skillNotes,
                                    skillNoteGenerating = uiState.skillNoteGenerating,
                                    skillLevelOverrides = uiState.skillLevelOverrides,
                                    onPromote = { vm.promoteSkill(it) },
                                    onDemote = { vm.demoteSkill(it) },
                                    onDelete = { vm.deleteSkill(it) },
                                    onSetSkillLevel = { id, level -> vm.setSkillLevel(id, level) },
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
                                    totalSkillCount = uiState.allSkills.size,
                                )
                            }
                            AnimatedVisibility(
                                visible = !isClassicStyle && uiState.currentPage == AppPage.ROLES,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                RolesPage(
                                    availableRoles = uiState.availableRoles,
                                    currentRole = uiState.currentRole,
                                    onActivate = { vm.setActiveRole(it) },
                                    onEdit = { vm.editRole(it) },
                                    onDelete = { vm.deleteCustomRole(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.ROLE_EDIT,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                val role = uiState.editingRole
                                if (role != null) {
                                    key(role.id) {
                                        RoleEditPage(
                                            initial = role,
                                            availableModels = uiState.availableModels,
                                            modelsLoading = uiState.modelsLoading,
                                            allSkills = uiState.allSkills,
                                            onSave = { vm.saveCustomRole(it); vm.navigateBack() },
                                            onRestore = if (role.isBuiltin) ({ vm.restoreBuiltinRole(role.id); vm.navigateBack() }) else null,
                                            onFetchModels = { vm.fetchModels() },
                                            onBack = { vm.navigateBack() },
                                        )
                                    }
                                }
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
                                visible = !isClassicStyle && uiState.currentPage == AppPage.APPS,
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
                                    isRunning = uiState.currentRunState.isRunning,
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
                                visible = !isClassicStyle && uiState.currentPage == AppPage.GROUPS,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                GroupsPage(
                                    groups = uiState.groups,
                                    groupPreviews = uiState.groupPreviews,
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
                                        typingAgentIds = uiState.groupTypingAgents,
                                        workingAgentIds = uiState.groupWorkingAgents,
                                        onSend = { text, attachments -> vm.sendGroupMessage(text, attachments) },
                                        onStop = { vm.stopGroupChat() },
                                        onBack = { vm.closeGroupChat() },
                                        onOpenHtmlViewer = { vm.openHtmlViewer(it) },
                                        onOpenBrowser = { vm.navigateToBrowser(it) },
                                        onOpenAccessibilitySettings = { vm.navigate(AppPage.SETTINGS) },
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
                                visible = !isClassicStyle && uiState.currentPage == AppPage.SKILL_MARKET,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                SkillMarketPage(
                                    installedIds = uiState.allSkills.map { it.id }.toSet(),
                                    onInstall = { vm.installMarketSkill(it) },
                                    onBack = { vm.navigateBack() },
                                )
                            }

                            AnimatedVisibility(
                                visible = !isClassicStyle && uiState.currentPage == AppPage.AI_PAGES,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                AiPagesPage(
                                    pages = uiState.aiPages,
                                    onOpen = { startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, it)) },
                                    onDelete = { vm.deleteAiPage(it) },
                                    onPinShortcut = {
                                        val def = uiState.aiPages.firstOrNull { p -> p.id == it }
                                        if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                                    },
                                    onBack = { vm.navigateBack() },
                                )
                            }

                            AnimatedVisibility(
                                visible = uiState.currentPage == AppPage.VPN,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                VpnPage(
                                    uiState = uiState,
                                    vm = vm,
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

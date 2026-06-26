package com.mobileclaw.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import com.mobileclaw.ClawApplication
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.ui.AppPage
import com.mobileclaw.ui.MainUiState
import com.mobileclaw.ui.MainViewModel
import com.mobileclaw.ui.SettingsLaunchTarget
import com.mobileclaw.ui.MiniAppActivity
import com.mobileclaw.ui.aipage.AiPagesPage
import com.mobileclaw.ui.aipage.AiPageActivity
import com.mobileclaw.ui.aipage.ShortcutHelper
import com.mobileclaw.ui.apps.AppLauncherPage
import com.mobileclaw.ui.chat.ChatScreen
import com.mobileclaw.ui.chat.currentRunState
import com.mobileclaw.ui.group.GroupChatScreen
import com.mobileclaw.ui.group.GroupsPage
import com.mobileclaw.ui.image.ImageGeneratorPage
import com.mobileclaw.ui.profile.ProfilePage
import com.mobileclaw.ui.roles.RoleDetailPage
import com.mobileclaw.ui.roles.RoleEditPage
import com.mobileclaw.ui.roles.RoleHomePage
import com.mobileclaw.ui.roles.RolesPage
import com.mobileclaw.ui.common.HtmlAttachmentViewer
import com.mobileclaw.ui.settings.BrowserPage
import com.mobileclaw.ui.settings.ConsolePage
import com.mobileclaw.ui.settings.HelpPage
import com.mobileclaw.ui.settings.SettingsPage
import com.mobileclaw.ui.settings.UserConfigPage
import com.mobileclaw.ui.settings.VpnPage
import com.mobileclaw.ui.video.VideoGeneratorPage
import com.mobileclaw.ui.skills.SkillMarketPage
import com.mobileclaw.ui.skills.SkillsPage
import com.mobileclaw.ui.workspace.WorkspacePage

@Composable
fun MainPageHost(
    uiState: MainUiState,
    vm: MainViewModel,
    isClassicStyle: Boolean,
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAiPage: (String) -> Unit,
    onPinAiPage: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val permissionManager = ClawApplication.instance.permissionManager
    val chatActive = uiState.currentPage == AppPage.CHAT
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (chatActive) 1f else 0f }
            .pointerInput(chatActive) {
                if (!chatActive) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        ChatScreen(
            uiState = uiState,
            onSendGoal = { vm.runTask(it) },
            onStop = { vm.stopTask() },
            onOpenSettings = { vm.navigate(AppPage.SETTINGS) },
            onOpenSkillManager = { vm.navigate(AppPage.SKILLS) },
            onOpenDrawer = onOpenDrawer,
            onExitDetail = { vm.navigate(AppPage.HOME) },
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
            onSwitchRole = { vm.navigate(AppPage.ROLES) },
            onCodexDesktopModeChange = { vm.setCodexDesktopMode(it) },
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onLoadMoreHistory = { vm.loadMoreHistory() },
            onCloseMiniAppPreview = { vm.clearChatMiniAppPreview() },
            onOpenMiniAppFullscreen = {
                vm.clearChatMiniAppPreview()
                onOpenApp(it)
            },
            onMiniAppPreviewStatusChanged = { appId, status, healthy ->
                vm.updateChatMiniAppPreviewStatus(appId, status, healthy)
            },
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
            onOpenHelp = { vm.navigate(AppPage.HELP) },
            onOpenWorkspace = { vm.openWorkspacePage() },
            onTestVirtualDisplay = { vm.testVirtualDisplay() },
            onCheckPrivServer = { vm.checkPrivServer() },
            localModels = uiState.localModels,
            onLocalModelEnabled = { vm.setLocalModelEnabled(it) },
            onLocalNativeOnly = { vm.setLocalNativeOnly(it) },
            onLocalToolCallingEnabled = { vm.setLocalToolCallingEnabled(it) },
            onSelectLocalModel = { vm.selectLocalModel(it) },
            onDownloadLocalModel = { id, token, sourceUrl -> vm.downloadLocalModel(id, token, sourceUrl) },
            onImportLocalModel = { id, uri -> vm.importLocalModel(id, uri) },
            onDeleteLocalModel = { vm.deleteLocalModel(it) },
            videoTasks = uiState.videoTasks,
            videoTaskRefreshingIds = uiState.videoTaskRefreshingIds,
            videoTasksRefreshing = uiState.videoTasksRefreshing,
            onRefreshVideoTask = { vm.refreshVideoTask(it) },
            onRefreshPendingVideoTasks = { vm.refreshPendingVideoTasks() },
            onDeleteVideoTask = { vm.deleteVideoTask(it) },
            launchGatewaySetup = uiState.settingsLaunchTarget == SettingsLaunchTarget.GATEWAY,
            onLaunchGatewaySetupConsumed = { vm.consumeSettingsLaunchTarget() },
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
            facts = uiState.profileState.facts,
            semanticFacts = uiState.profileState.semanticFacts,
            memoryHasMore = uiState.profileState.memoryHasMore,
            memoryLoadingMore = uiState.profileState.memoryLoadingMore,
            episodes = uiState.profileState.recentEpisodes,
            isLoading = uiState.profileState.isLoading,
            isExtracting = uiState.profileState.isExtracting,
            conversationCount = uiState.profileState.conversationCount,
            onBack = { vm.navigateBack() },
            onRefreshExtraction = { vm.triggerProfileExtraction() },
            onSetFact = { key, value -> vm.setProfileFact(key, value) },
            onPinMemory = { key, pinned -> vm.setMemoryPinned(key, pinned) },
            onEnableMemory = { key, enabled -> vm.setMemoryEnabled(key, enabled) },
            onDeleteMemory = { key -> vm.deleteMemoryFact(key) },
            onLoadMoreMemory = { vm.loadMoreProfileMemory() },
            personalitySummary = uiState.profileState.personalitySummary,
            personalitySummaryLoading = uiState.profileState.personalitySummaryLoading,
            onGenerateSummary = { vm.generatePersonalitySummary() },
            dimensionQuizzes = uiState.profileState.dimensionQuizzes,
            dimensionQuizLoading = uiState.profileState.dimensionQuizLoading,
            onGenerateDimensionQuiz = { id, title -> vm.generateDimensionQuiz(id, title) },
            onPrewarmQuizzes = { dims -> vm.prewarmAllDimensionQuizzes(dims) },
            totalSkillCount = uiState.allSkills.size,
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
            town = uiState.agentTown,
            workingAgentIds = uiState.groupState.workingAgents,
            typingAgentIds = uiState.groupState.typingAgents,
            rolePortraitGeneratingIds = uiState.rolePortraitGeneratingIds,
            onActivate = { vm.setActiveRole(it) },
            onOpenDetail = { vm.openRoleDetail(it) },
            onGeneratePortrait = { vm.generateRolePortrait(it) },
            onEdit = { if (it.isBuiltin) vm.copyBuiltinRoleForEditing(it) else vm.editRole(it) },
            onDelete = { vm.deleteCustomRole(it) },
            onBack = { vm.navigateBack() },
        )
    }
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.ROLE_DETAIL,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        val role = uiState.detailRole
        if (role != null) {
            RoleDetailPage(
                role = role,
                currentRole = uiState.currentRole,
                town = uiState.agentTown,
                isWorking = role.id in uiState.groupState.workingAgents || role.id in uiState.groupState.typingAgents,
                isGeneratingPortrait = role.id in uiState.rolePortraitGeneratingIds,
                onActivate = { vm.setActiveRole(it) },
                onGeneratePortrait = { vm.generateRolePortrait(it) },
                onEdit = { if (it.isBuiltin) vm.copyBuiltinRoleForEditing(it) else vm.editRole(it) },
                onOpenHome = { vm.openRoleHome(it) },
                onBack = { vm.navigateBack() },
            )
        }
    }
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.AI_TOWN,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        val role = uiState.detailRole ?: uiState.availableRoles.firstOrNull { it.id == uiState.openTownRoleId }
        if (role != null) {
            RoleHomePage(
                role = role,
                currentRole = uiState.currentRole,
                town = uiState.agentTown,
                isWorking = role.id in uiState.groupState.workingAgents || role.id in uiState.groupState.typingAgents,
                onBack = { vm.navigateBack() },
                onEdit = { if (it.isBuiltin) vm.copyBuiltinRoleForEditing(it) else vm.editRole(it) },
            )
        }
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
        visible = uiState.currentPage == AppPage.APPS,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        AppLauncherPage(
            miniApps = uiState.miniApps,
            onOpen = onOpenApp,
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
        visible = uiState.currentPage == AppPage.WORKSPACE,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        WorkspacePage(
            snapshot = uiState.workspaceState.snapshot,
            facts = uiState.workspaceState.facts,
            onBack = { vm.navigateBack() },
            onRefresh = { vm.loadCurrentWorkspaceSnapshot() },
            onPromoteFact = { vm.promoteWorkspaceFact(it) },
            onDeleteFact = { vm.deleteWorkspaceFact(it) },
        )
    }
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.IMAGE_GENERATOR,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        val configSnapshot by uiState.config.collectAsState(initial = ConfigSnapshot())
        ImageGeneratorPage(
            isRunning = uiState.imageGenerationRunning,
            promptAiRunning = uiState.imagePromptAiRunning,
            configSnapshot = configSnapshot,
            previewBase64 = uiState.imageGenerationPreviewBase64,
            previewPrompt = uiState.imageGenerationPreviewPrompt,
            onBack = { vm.navigateBack() },
            onGenerate = { request -> vm.generateImage(request) },
            onRewritePrompt = { prompt, action, onResult -> vm.rewriteImagePrompt(prompt, action, onResult) },
        )
    }
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.VIDEO_GENERATOR,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        val configSnapshot by uiState.config.collectAsState(initial = ConfigSnapshot())
        VideoGeneratorPage(
            isRunning = uiState.currentRunState.isRunning || uiState.videoGenerationRunning,
            configSnapshot = configSnapshot,
            videoTasks = uiState.videoTasks,
            refreshingIds = uiState.videoTaskRefreshingIds,
            refreshingAll = uiState.videoTasksRefreshing,
            promptAiRunning = uiState.videoPromptAiRunning,
            onBack = { vm.navigateBack() },
            onGenerate = { request -> vm.generateVideo(request) },
            onRewritePrompt = { prompt, action, onResult -> vm.rewriteVideoPrompt(prompt, action, onResult) },
            onUploadFrameImage = { uri, onResult -> vm.uploadVideoFrameImage(uri, onResult) },
            onRefreshTask = { vm.refreshVideoTask(it) },
            onRefreshAll = { vm.refreshPendingVideoTasks() },
            onDeleteTask = { vm.deleteVideoTask(it) },
        )
    }
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.GROUPS,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        GroupsPage(
            groups = uiState.groupState.groups,
            groupPreviews = uiState.groupState.previews,
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
        val group = uiState.groupState.openGroup
        if (group != null) {
            GroupChatScreen(
                group = group,
                messages = uiState.groupState.messages,
                availableRoles = uiState.availableRoles,
                userAvatarUri = uiState.userAvatarUri,
                isRunning = uiState.groupState.isRunning,
                typingAgentIds = uiState.groupState.typingAgents,
                workingAgentIds = uiState.groupState.workingAgents,
                historyHasMore = uiState.groupState.historyHasMore,
                historyLoading = uiState.groupState.historyLoading,
                onLoadMoreHistory = { vm.loadOlderGroupMessages() },
                onUpdateGroupMembers = { vm.updateGroupMembers(group.id, it) },
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
    AnimatedVisibility(
        visible = uiState.currentPage == AppPage.AI_PAGES,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        AiPagesPage(
            pages = uiState.aiPages,
            onOpen = onOpenAiPage,
            onDelete = { vm.deleteAiPage(it) },
            onPinShortcut = onPinAiPage,
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

    val htmlAttachment = uiState.openHtmlAttachment
    if (htmlAttachment != null) {
        HtmlAttachmentViewer(
            attachment = htmlAttachment,
            onClose = { vm.closeHtmlViewer() },
            onAskAgent = { vm.runTask(it) },
        )
    }
}

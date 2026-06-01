package com.mobileclaw.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobileclaw.ui.AppPage
import com.mobileclaw.ui.MainUiState
import com.mobileclaw.ui.MainViewModel
import com.mobileclaw.ui.aipage.AiPagesPage
import com.mobileclaw.ui.apps.AppLauncherPage
import com.mobileclaw.ui.chat.ChatScreen
import com.mobileclaw.ui.group.GroupsPage
import com.mobileclaw.ui.roles.RoleHomePage
import com.mobileclaw.ui.roles.RolesPage
import com.mobileclaw.ui.skills.SkillMarketPage
import com.mobileclaw.ui.skills.SkillsPage

@Composable
fun ClassicShellContent(
    uiState: MainUiState,
    classicShell: ClassicShellController,
    vm: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAiPage: (String) -> Unit,
    onPinAiPage: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    when (classicShell.tab) {
        ClassicTab.CHAT -> {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    if (classicShell.chatTab == ClassicChatTab.SINGLE) {
                        ChatScreen(
                            uiState = uiState,
                            onSendGoal = { vm.runTask(it) },
                            onStop = { vm.stopTask() },
                            onOpenSettings = { vm.navigate(AppPage.SETTINGS) },
                            onOpenSkillManager = { vm.navigate(AppPage.SKILLS) },
                            onOpenDrawer = onOpenDrawer,
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
                            classicMode = true,
                        )
                    } else {
                        GroupsPage(
                            groups = uiState.groupState.groups,
                            groupPreviews = uiState.groupState.previews,
                            availableRoles = uiState.availableRoles,
                            onOpenGroup = { vm.openGroupChat(it) },
                            onCreateGroup = { vm.createGroup(it) },
                            onDeleteGroup = { vm.deleteGroup(it) },
                            onBack = {
                                classicShell.chatTab = ClassicChatTab.SINGLE
                                vm.navigate(AppPage.CHAT)
                            },
                            showHeader = false,
                            createRequestKey = classicShell.createGroupRequestKey,
                            showCreateFab = false,
                        )
                    }
                }
            }
        }

        ClassicTab.SKILL -> {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    if (classicShell.skillTab == ClassicSkillTab.LOCAL) {
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
                            onBack = {
                                classicShell.skillTab = ClassicSkillTab.LOCAL
                                vm.navigate(AppPage.SKILLS)
                            },
                            showHeader = false,
                        )
                    }
                }
            }
        }

        ClassicTab.CENTER -> {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    if (classicShell.centerTab == ClassicCenterTab.MINI_APP) {
                        AppLauncherPage(
                            miniApps = uiState.miniApps,
                            onOpen = onOpenApp,
                            onDelete = { vm.deleteApp(it) },
                            onBack = { vm.navigate(AppPage.CHAT) },
                            showHeader = false,
                        )
                    } else {
                        AiPagesPage(
                            pages = uiState.aiPages,
                            onOpen = onOpenAiPage,
                            onDelete = { vm.deleteAiPage(it) },
                            onPinShortcut = onPinAiPage,
                            onBack = {
                                classicShell.centerTab = ClassicCenterTab.MINI_APP
                                vm.navigate(AppPage.APPS)
                            },
                            showHeader = false,
                        )
                    }
                }
            }
        }

        ClassicTab.ROLES -> {
            val homeRole = uiState.detailRole ?: uiState.availableRoles.firstOrNull { it.id == uiState.openTownRoleId }
            if (uiState.currentPage == AppPage.AI_TOWN && homeRole != null) {
                RoleHomePage(
                    role = homeRole,
                    currentRole = uiState.currentRole,
                    town = uiState.agentTown,
                    isWorking = homeRole.id in uiState.groupState.workingAgents || homeRole.id in uiState.groupState.typingAgents,
                    onBack = { vm.navigateBack() },
                    onEdit = { vm.editRole(it) },
                )
            } else {
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
                    onEdit = { vm.editRole(it) },
                    onDelete = { vm.deleteCustomRole(it) },
                    onBack = { vm.navigate(AppPage.CHAT) },
                    showHeader = false,
                )
            }
        }

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

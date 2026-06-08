package com.mobileclaw.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobileclaw.ui.AppPage
import com.mobileclaw.ui.MainUiState
import com.mobileclaw.ui.MainViewModel

@Composable
fun ClassicShellContent(
    uiState: MainUiState,
    classicShell: ClassicShellController,
    vm: MainViewModel,
    onOpenApp: (String) -> Unit,
    onOpenAiPage: (String) -> Unit,
    onPinAiPage: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (classicShell.tab) {
            ClassicTab.HOME -> ClassicHomePage(
                sessions = uiState.sessions,
                groups = uiState.groupState.groups,
                groupPreviews = uiState.groupState.previews,
                currentSessionId = uiState.currentSessionId,
                isConfigured = uiState.isConfigured,
                onNewChat = {
                    vm.createNewSessionAndOpen()
                },
                onOpenGroups = { vm.navigate(AppPage.GROUPS) },
                onConfigureGateway = { vm.openGatewayConfig() },
                onOpenSession = { sessionId ->
                    vm.loadSession(sessionId)
                    vm.navigate(AppPage.CHAT)
                },
                onOpenGroup = { group ->
                    vm.openGroupChat(group)
                    vm.navigate(AppPage.GROUP_CHAT)
                },
            )

            ClassicTab.WORKSPACE -> ClassicHubPage(
                miniApps = uiState.miniApps,
                aiPages = uiState.aiPages,
                onOpenApp = onOpenApp,
                onOpenAiPage = onOpenAiPage,
                onOpenWorkspace = { vm.openWorkspacePage() },
                onGenerateImage = { vm.navigate(AppPage.IMAGE_GENERATOR) },
                onGenerateVideo = { vm.navigate(AppPage.VIDEO_GENERATOR) },
            )

            ClassicTab.ME -> ClassicMePage(
                userAvatarUri = uiState.userAvatarUri,
                userName = uiState.userConfigEntries["user.name"]?.value ?: "",
                sessionCount = uiState.sessions.size,
                miniApps = uiState.miniApps,
                roleCount = uiState.availableRoles.size,
                preferenceCount = uiState.userConfigEntries.size,
                gatewayOnline = uiState.isConfigured || uiState.privServerConnected,
                onProfile = { vm.navigate(AppPage.PROFILE) },
                onRoles = { vm.navigate(AppPage.ROLES) },
                onUserConfig = { vm.navigate(AppPage.USER_CONFIG) },
                onVpn = { vm.navigate(AppPage.VPN) },
                onSettings = { vm.navigate(AppPage.SETTINGS) },
                onHelp = { vm.navigate(AppPage.HELP) },
                onSkillMarket = { vm.navigate(AppPage.SKILL_MARKET) },
                onConsole = { vm.navigate(AppPage.CONSOLE) },
                onGatewayConfig = { vm.navigate(AppPage.SETTINGS) },
                onCheckUpdate = {
                    vm.navigate(AppPage.CHAT)
                    vm.checkAppUpdate()
                },
            )
        }
    }
}

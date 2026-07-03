package com.mobileclaw.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val miniAppImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importMiniAppPackage(it) }
    }

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
                onImportMiniApp = { miniAppImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onDeleteMiniApps = { vm.deleteApps(it) },
                onGenerateImage = { vm.navigate(AppPage.IMAGE_GENERATOR) },
                onGenerateVideo = { vm.navigate(AppPage.VIDEO_GENERATOR) },
            )

            ClassicTab.ME -> ClassicMePage(
                userAvatarUri = uiState.userAvatarUri,
                userName = uiState.userConfigEntries["user.name"]?.value ?: "",
                roleCount = uiState.availableRoles.size,
                gatewayOnline = uiState.isConfigured || uiState.privServerConnected,
                onUserInfo = { vm.navigate(AppPage.USER_INFO) },
                onRoles = { vm.navigate(AppPage.ROLES) },
                onAiBasicSettings = { vm.navigate(AppPage.AI_BASIC_SETTINGS) },
                onGeneralSettings = { vm.navigate(AppPage.GENERAL_SETTINGS) },
                onToolsSettings = { vm.navigate(AppPage.TOOLS_SETTINGS) },
                onMemorySettings = { vm.navigate(AppPage.MEMORY_SETTINGS) },
                onCheckUpdate = {
                    vm.checkAppUpdate(showResultInChat = false, showNoUpdateDialog = true)
                },
            )
        }
    }
}

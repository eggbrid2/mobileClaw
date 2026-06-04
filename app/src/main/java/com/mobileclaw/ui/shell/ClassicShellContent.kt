package com.mobileclaw.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobileclaw.R
import com.mobileclaw.str
import com.mobileclaw.ui.AppPage
import com.mobileclaw.ui.MainUiState
import com.mobileclaw.ui.MainViewModel
import com.mobileclaw.ui.chat.ChatScreen

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
    Box(Modifier.fillMaxSize()) {
        when (classicShell.tab) {
            ClassicTab.CHAT -> ChatScreen(
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
                classicMode = true,
            )

            ClassicTab.WORKSPACE -> ClassicHubPage(
                title = str(R.string.classic_workspace),
                subtitle = "文件、应用和生成结果",
                rows = listOf(
                    ClassicHubRow(Icons.Filled.Terminal, str(R.string.workspace_title), "当前项目、文件和沉淀内容", "内容") {
                        vm.openWorkspacePage()
                    },
                    ClassicHubRow(Icons.Filled.Apps, str(R.string.classic_mini_apps), "网页小工具和临时应用", "内容") {
                        vm.navigate(AppPage.APPS)
                    },
                    ClassicHubRow(Icons.Filled.Apps, "页面", "可反复使用的原生页面", "内容") {
                        vm.navigate(AppPage.AI_PAGES)
                    },
                    ClassicHubRow(Icons.Filled.ChatBubbleOutline, str(R.string.role_task_image), "图片生成和历史结果", "生成") {
                        vm.navigate(AppPage.IMAGE_GENERATOR)
                    },
                    ClassicHubRow(Icons.Filled.ChatBubbleOutline, str(R.string.gateway_capability_video), "视频任务和刷新状态", "生成") {
                        vm.navigate(AppPage.VIDEO_GENERATOR)
                    },
                ),
            )

            ClassicTab.AGENTS -> ClassicHubPage(
                title = "团队",
                subtitle = "成员、群聊和个人记录",
                rows = listOf(
                    ClassicHubRow(Icons.Filled.Psychology, str(R.string.drawer_roles), "选择和维护默认助手", "成员") {
                        vm.navigate(AppPage.ROLES)
                    },
                    ClassicHubRow(Icons.Filled.ChatBubbleOutline, str(R.string.classic_group_chat), "多角色对话和任务讨论", "成员") {
                        vm.navigate(AppPage.GROUPS)
                    },
                    ClassicHubRow(Icons.Filled.Person, str(R.string.drawer_profile), "偏好、记忆和长期信息", "记录") {
                        vm.navigate(AppPage.PROFILE)
                    },
                ),
            )

            ClassicTab.TOOLS -> ClassicHubPage(
                title = str(R.string.classic_tools),
                subtitle = "连接、浏览和扩展能力",
                rows = listOf(
                    ClassicHubRow(Icons.Filled.Extension, str(R.string.drawer_skills), "已安装能力和加载规则", "能力") {
                        vm.navigate(AppPage.SKILLS)
                    },
                    ClassicHubRow(Icons.Filled.Extension, str(R.string.skills_0e0282), "安装更多扩展", "能力") {
                        vm.navigate(AppPage.SKILL_MARKET)
                    },
                    ClassicHubRow(Icons.Filled.Terminal, "电脑端", "连接桌面 Codex", "连接") {
                        vm.navigate(AppPage.HELP)
                    },
                    ClassicHubRow(Icons.Filled.Apps, str(R.string.classic_browser), "打开网页和发送页面内容", "连接") {
                        vm.navigate(AppPage.BROWSER)
                    },
                    ClassicHubRow(Icons.Filled.Terminal, str(R.string.drawer_console), "局域网访问和调试", "连接") {
                        vm.navigate(AppPage.CONSOLE)
                    },
                ),
            )

            ClassicTab.ME -> ClassicMePage(
                userAvatarUri = uiState.userAvatarUri,
                userName = uiState.userConfigEntries["user.name"]?.value ?: "",
                sessionCount = uiState.sessions.size,
                miniApps = uiState.miniApps,
                onProfile = { vm.navigate(AppPage.PROFILE) },
                onVpn = { vm.navigate(AppPage.VPN) },
                onSettings = { vm.navigate(AppPage.SETTINGS) },
                onHelp = { vm.navigate(AppPage.HELP) },
            )
        }
    }
}

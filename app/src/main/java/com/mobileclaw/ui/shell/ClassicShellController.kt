package com.mobileclaw.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobileclaw.R
import com.mobileclaw.str
import com.mobileclaw.ui.AppPage

data class ClassicShellTopTab(
    val label: String,
    val selected: Boolean,
)

class ClassicShellController internal constructor() {
    var tab by mutableStateOf(ClassicTab.HOME)

    val title: String
        get() = when (tab) {
            ClassicTab.HOME -> "会话"
            ClassicTab.WORKSPACE -> str(R.string.classic_workspace)
            ClassicTab.ME -> str(R.string.classic_me)
        }

    val topTabs: List<ClassicShellTopTab>
        get() = when (tab) {
            ClassicTab.HOME,
            ClassicTab.WORKSPACE,
            ClassicTab.ME -> emptyList()
        }

    fun syncFromPage(currentPage: AppPage) {
        tab = when (currentPage) {
            AppPage.HOME,
            AppPage.CHAT -> ClassicTab.HOME
            AppPage.SKILLS,
            AppPage.SKILL_MARKET,
            AppPage.CONSOLE,
            AppPage.BROWSER,
            AppPage.APPS,
            AppPage.AI_PAGES,
            AppPage.WORKSPACE,
            AppPage.IMAGE_GENERATOR,
            AppPage.VIDEO_GENERATOR,
            AppPage.AI_TOWN,
            AppPage.GROUPS,
            AppPage.GROUP_CHAT,
            AppPage.ROLES,
            AppPage.ROLE_DETAIL,
            AppPage.ROLE_EDIT -> ClassicTab.WORKSPACE
            AppPage.PROFILE,
            AppPage.VPN,
            AppPage.SETTINGS,
            AppPage.USER_CONFIG,
            AppPage.HELP -> ClassicTab.ME
        }
    }

    fun currentPageForBottomTab(selectedTab: ClassicTab): AppPage? = when (selectedTab) {
        ClassicTab.HOME -> AppPage.HOME
        ClassicTab.WORKSPACE -> null
        ClassicTab.ME -> null
    }

    fun applyTopTabSelection(index: Int): AppPage? = when (tab) {
        ClassicTab.HOME,
        ClassicTab.WORKSPACE,
        ClassicTab.ME -> null
    }

    fun shouldRenderShellRoot(currentPage: AppPage): Boolean = when (tab) {
        ClassicTab.HOME -> currentPage == AppPage.HOME
        ClassicTab.WORKSPACE -> currentPage !in setOf(
            AppPage.APPS,
            AppPage.AI_PAGES,
            AppPage.WORKSPACE,
            AppPage.IMAGE_GENERATOR,
            AppPage.VIDEO_GENERATOR,
            AppPage.SKILLS,
            AppPage.SKILL_MARKET,
            AppPage.CONSOLE,
            AppPage.BROWSER,
            AppPage.ROLES,
            AppPage.GROUPS,
            AppPage.GROUP_CHAT,
            AppPage.AI_TOWN,
            AppPage.ROLE_DETAIL,
            AppPage.ROLE_EDIT,
        )
        ClassicTab.ME -> currentPage !in setOf(
            AppPage.PROFILE,
            AppPage.VPN,
            AppPage.SETTINGS,
            AppPage.USER_CONFIG,
            AppPage.HELP,
        )
    }
}

@Composable
fun rememberClassicShellController(currentPage: AppPage): ClassicShellController {
    val controller = remember { ClassicShellController() }
    LaunchedEffect(currentPage) {
        controller.syncFromPage(currentPage)
    }
    return controller
}

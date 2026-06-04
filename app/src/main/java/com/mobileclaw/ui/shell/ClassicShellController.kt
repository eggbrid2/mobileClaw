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
    var tab by mutableStateOf(ClassicTab.CHAT)

    val title: String
        get() = when (tab) {
            ClassicTab.CHAT -> str(R.string.home_859362)
            ClassicTab.WORKSPACE -> str(R.string.classic_workspace)
            ClassicTab.AGENTS -> str(R.string.classic_agents)
            ClassicTab.TOOLS -> str(R.string.classic_tools)
            ClassicTab.ME -> str(R.string.classic_me)
        }

    val topTabs: List<ClassicShellTopTab>
        get() = when (tab) {
            ClassicTab.CHAT,
            ClassicTab.WORKSPACE,
            ClassicTab.AGENTS,
            ClassicTab.TOOLS,
            ClassicTab.ME -> emptyList()
        }

    fun syncFromPage(currentPage: AppPage) {
        tab = when (currentPage) {
            AppPage.CHAT,
            AppPage.GROUP_CHAT -> ClassicTab.CHAT
            AppPage.SKILLS,
            AppPage.SKILL_MARKET,
            AppPage.CONSOLE,
            AppPage.BROWSER -> ClassicTab.TOOLS
            AppPage.APPS,
            AppPage.AI_PAGES,
            AppPage.WORKSPACE,
            AppPage.IMAGE_GENERATOR,
            AppPage.VIDEO_GENERATOR -> ClassicTab.WORKSPACE
            AppPage.AI_TOWN,
            AppPage.GROUPS,
            AppPage.ROLES,
            AppPage.ROLE_DETAIL,
            AppPage.ROLE_EDIT -> ClassicTab.AGENTS
            AppPage.PROFILE,
            AppPage.VPN,
            AppPage.SETTINGS,
            AppPage.USER_CONFIG,
            AppPage.HELP -> ClassicTab.ME
            else -> tab
        }
    }

    fun currentPageForBottomTab(selectedTab: ClassicTab): AppPage? = when (selectedTab) {
        ClassicTab.CHAT -> AppPage.CHAT
        ClassicTab.WORKSPACE -> null
        ClassicTab.AGENTS -> null
        ClassicTab.TOOLS -> null
        ClassicTab.ME -> null
    }

    fun applyTopTabSelection(index: Int): AppPage? = when (tab) {
        ClassicTab.CHAT,
        ClassicTab.WORKSPACE,
        ClassicTab.AGENTS,
        ClassicTab.TOOLS,
        ClassicTab.ME -> null
    }

    fun shouldRenderShellRoot(currentPage: AppPage): Boolean = when (tab) {
        ClassicTab.CHAT -> currentPage == AppPage.CHAT
        ClassicTab.TOOLS -> currentPage !in setOf(AppPage.SKILLS, AppPage.SKILL_MARKET, AppPage.CONSOLE, AppPage.BROWSER)
        ClassicTab.WORKSPACE -> currentPage !in setOf(AppPage.APPS, AppPage.AI_PAGES, AppPage.WORKSPACE, AppPage.IMAGE_GENERATOR, AppPage.VIDEO_GENERATOR)
        ClassicTab.AGENTS -> currentPage !in setOf(AppPage.ROLES, AppPage.GROUPS, AppPage.GROUP_CHAT, AppPage.AI_TOWN, AppPage.ROLE_DETAIL, AppPage.ROLE_EDIT)
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

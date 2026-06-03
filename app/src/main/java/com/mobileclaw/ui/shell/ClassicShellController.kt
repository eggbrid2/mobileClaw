package com.mobileclaw.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var chatTab by mutableStateOf(ClassicChatTab.SINGLE)
    var skillTab by mutableStateOf(ClassicSkillTab.LOCAL)
    var centerTab by mutableStateOf(ClassicCenterTab.MINI_APP)
    var createGroupRequestKey by mutableIntStateOf(0)

    val title: String
        get() = when (tab) {
            ClassicTab.CHAT -> str(R.string.home_859362)
            ClassicTab.SKILL -> str(R.string.drawer_skills)
            ClassicTab.CENTER -> str(R.string.classic_center)
            ClassicTab.ROLES -> str(R.string.drawer_roles)
            ClassicTab.ME -> str(R.string.classic_me)
        }

    val topTabs: List<ClassicShellTopTab>
        get() = when (tab) {
            ClassicTab.CHAT -> listOf(
                ClassicShellTopTab(str(R.string.classic_single_chat), chatTab == ClassicChatTab.SINGLE),
                ClassicShellTopTab(str(R.string.classic_group_chat), chatTab == ClassicChatTab.GROUP),
            )
            ClassicTab.SKILL -> listOf(
                ClassicShellTopTab(str(R.string.classic_local), skillTab == ClassicSkillTab.LOCAL),
                ClassicShellTopTab(str(R.string.skills_0e0282), skillTab == ClassicSkillTab.MARKET),
            )
            ClassicTab.CENTER -> listOf(
                ClassicShellTopTab(str(R.string.classic_mini_apps), centerTab == ClassicCenterTab.MINI_APP),
                ClassicShellTopTab(str(R.string.home_2d20d5), centerTab == ClassicCenterTab.AI_PAGE),
            )
            ClassicTab.ROLES,
            ClassicTab.ME -> emptyList()
        }

    fun syncFromPage(currentPage: AppPage) {
        tab = when (currentPage) {
            AppPage.CHAT,
            AppPage.GROUPS,
            AppPage.GROUP_CHAT -> ClassicTab.CHAT
            AppPage.SKILLS,
            AppPage.SKILL_MARKET -> ClassicTab.SKILL
            AppPage.APPS,
            AppPage.AI_PAGES -> ClassicTab.CENTER
            AppPage.AI_TOWN,
            AppPage.ROLES,
            AppPage.ROLE_DETAIL,
            AppPage.ROLE_EDIT -> ClassicTab.ROLES
            AppPage.PROFILE,
            AppPage.CONSOLE,
            AppPage.VPN,
            AppPage.SETTINGS,
            AppPage.USER_CONFIG,
            AppPage.WORKSPACE,
            AppPage.IMAGE_GENERATOR,
            AppPage.VIDEO_GENERATOR -> ClassicTab.ME
            else -> tab
        }
        if (currentPage == AppPage.GROUPS || currentPage == AppPage.GROUP_CHAT) {
            chatTab = ClassicChatTab.GROUP
        }
        if (currentPage == AppPage.SKILL_MARKET) {
            skillTab = ClassicSkillTab.MARKET
        }
        if (currentPage == AppPage.AI_PAGES) {
            centerTab = ClassicCenterTab.AI_PAGE
        }
    }

    fun currentPageForBottomTab(selectedTab: ClassicTab): AppPage? = when (selectedTab) {
        ClassicTab.CHAT -> if (chatTab == ClassicChatTab.GROUP) AppPage.GROUPS else AppPage.CHAT
        ClassicTab.SKILL -> if (skillTab == ClassicSkillTab.MARKET) AppPage.SKILL_MARKET else AppPage.SKILLS
        ClassicTab.CENTER -> if (centerTab == ClassicCenterTab.AI_PAGE) AppPage.AI_PAGES else AppPage.APPS
        ClassicTab.ROLES -> AppPage.ROLES
        ClassicTab.ME -> null
    }

    fun applyTopTabSelection(index: Int): AppPage? = when (tab) {
        ClassicTab.CHAT -> {
            chatTab = if (index == 0) ClassicChatTab.SINGLE else ClassicChatTab.GROUP
            if (index == 0) AppPage.CHAT else AppPage.GROUPS
        }
        ClassicTab.SKILL -> {
            skillTab = if (index == 0) ClassicSkillTab.LOCAL else ClassicSkillTab.MARKET
            if (index == 0) AppPage.SKILLS else AppPage.SKILL_MARKET
        }
        ClassicTab.CENTER -> {
            centerTab = if (index == 0) ClassicCenterTab.MINI_APP else ClassicCenterTab.AI_PAGE
            if (index == 0) AppPage.APPS else AppPage.AI_PAGES
        }
        ClassicTab.ROLES,
        ClassicTab.ME -> null
    }

    fun shouldRenderShellRoot(currentPage: AppPage): Boolean = when (tab) {
        // Chat 根页只有“单聊会话”和“群组列表”；群详情属于二级页，应该交回正常页面宿主。
        ClassicTab.CHAT -> when (chatTab) {
            ClassicChatTab.SINGLE -> currentPage == AppPage.CHAT
            ClassicChatTab.GROUP -> currentPage == AppPage.GROUPS
        }
        // Skill 根页只有本地技能和市场列表；其它都属于二级页。
        ClassicTab.SKILL -> when (skillTab) {
            ClassicSkillTab.LOCAL -> currentPage == AppPage.SKILLS
            ClassicSkillTab.MARKET -> currentPage == AppPage.SKILL_MARKET
        }
        // Center 根页只有 miniapp 列表和 AI Page 列表；打开具体内容后走普通页面宿主。
        ClassicTab.CENTER -> when (centerTab) {
            ClassicCenterTab.MINI_APP -> currentPage == AppPage.APPS
            ClassicCenterTab.AI_PAGE -> currentPage == AppPage.AI_PAGES
        }
        // Roles 根页只承载角色总览；home/detail/edit 都是二级页。
        ClassicTab.ROLES -> currentPage == AppPage.ROLES
        // Me 本身是一个壳页，不依赖 currentPage；只有进入明确二级页时才切走。
        ClassicTab.ME -> currentPage !in setOf(
            AppPage.PROFILE,
            AppPage.CONSOLE,
            AppPage.VPN,
            AppPage.SETTINGS,
            AppPage.USER_CONFIG,
            AppPage.WORKSPACE,
            AppPage.IMAGE_GENERATOR,
            AppPage.VIDEO_GENERATOR,
            AppPage.HELP,
            AppPage.BROWSER,
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

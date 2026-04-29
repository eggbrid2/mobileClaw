package com.mobileclaw.skill.builtin

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Navigates the app to a specific built-in page.
 * Emits a page name string consumed by MainViewModel.
 */
class PageControlSkill(val pageRequests: MutableSharedFlow<String>) : Skill {

    override val meta = SkillMeta(
        id = "page_control",
        name = "Page Control",
        description = "Opens a built-in app page for the user. " +
            "Pages: chat (main chat), settings (API/model config), skills (skill manager), " +
            "profile (user profile & personality), roles (role/persona manager), " +
            "user_config (user configuration entries), apps (mini-app launcher), " +
            "console (LAN debug console), help (help & info).",
        parameters = listOf(
            SkillParam("page", "string", "Page to navigate to: chat | settings | skills | profile | roles | user_config | apps | console | help"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val page = (params["page"] as? String)?.lowercase()?.trim()
            ?: return SkillResult(false, "page is required")
        val mapped = when (page) {
            "chat", "home", "main"       -> "chat"
            "settings", "config", "api"  -> "settings"
            "skills", "skill"            -> "skills"
            "profile", "user", "persona" -> "profile"
            "roles", "role"              -> "roles"
            "user_config", "userconfig"  -> "user_config"
            "apps", "app", "miniapp"     -> "apps"
            "console", "debug", "lan"    -> "console"
            "help"                       -> "help"
            else -> return SkillResult(false, "Unknown page '$page'. Available: chat | settings | skills | profile | roles | user_config | apps | console | help")
        }
        pageRequests.emit(mapped)
        return SkillResult(true, "Navigated to page: $mapped")
    }
}

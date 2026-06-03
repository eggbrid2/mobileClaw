package com.mobileclaw.skill

enum class SkillToolCategory {
    CHAT,
    MEMORY,
    SKILL,
    SELF_EVOLUTION,
    ARTIFACT,
    PHONE,
    WEB,
    MEDIA,
    VPN,
    CODE,
    SYSTEM,
}

object SkillToolTaxonomy {
    private val categoryIds: Map<SkillToolCategory, Set<String>> = mapOf(
        SkillToolCategory.CHAT to setOf(
            "sticker_bqb",
            "show_toast",
            "clipboard",
        ),
        SkillToolCategory.MEMORY to setOf(
            "memory",
            "user_profile",
            "user_config",
            "session_manager",
            "task_recipe",
            "skill_notes",
            "workspace_manager",
        ),
        SkillToolCategory.SKILL to setOf(
            "meta",
            "skill_check",
            "quick_skill",
            "create_skill",
            "skill_market",
            "skill_notes",
        ),
        SkillToolCategory.SELF_EVOLUTION to setOf(
            "role_manager",
            "switch_role",
            "switch_model",
            "generate_icon",
            "console_editor",
            "page_control",
            "house_artist",
            "town_builder",
            "ai_home_assets",
        ),
        SkillToolCategory.ARTIFACT to setOf(
            "ui_builder",
            "app_manager",
            "create_html",
            "create_file",
            "read_file",
            "list_files",
            "generate_document",
            "user_storage",
            "house_artist",
            "town_builder",
            "ai_home_assets",
            "workspace_manager",
        ),
        SkillToolCategory.PHONE to setOf(
            "see_screen",
            "read_screen",
            "screenshot",
            "tap",
            "long_click",
            "scroll",
            "input_text",
            "navigate",
            "list_apps",
            "phone_status",
            "check_permissions",
            "bg_launch",
            "bg_read_screen",
            "bg_screenshot",
            "bg_stop",
            "vd_setup",
        ),
        SkillToolCategory.WEB to setOf(
            "web_search",
            "fetch_url",
            "web_browse",
            "web_content",
            "web_js",
        ),
        SkillToolCategory.MEDIA to setOf(
            "generate_image",
            "generate_icon",
            "generate_video",
            "house_artist",
            "ai_home_assets",
        ),
        SkillToolCategory.VPN to setOf(
            "vpn_control",
        ),
        SkillToolCategory.CODE to setOf(
            "shell",
            "run_python",
            "pip_install",
            "codex_desktop",
        ),
        SkillToolCategory.SYSTEM to setOf(
            "device_info",
            "permission",
            "check_permissions",
            "page_control",
            "switch_model",
        ),
    )

    fun idsFor(vararg categories: SkillToolCategory): List<String> =
        categories.flatMap { categoryIds[it].orEmpty() }.distinct()

    fun idsFor(categories: Iterable<SkillToolCategory>): List<String> =
        categories.flatMap { categoryIds[it].orEmpty() }.distinct()

    fun categoriesFor(skillId: String): Set<SkillToolCategory> =
        explicitCategoriesFor(skillId).ifEmpty { heuristicCategoriesFor(skillId) }

    fun categoriesFor(meta: SkillMeta): Set<SkillToolCategory> {
        if (meta.categories.isNotEmpty()) return meta.categories.toSet()
        val seed = buildString {
            append(meta.id)
            append(' ')
            append(meta.name)
            append(' ')
            append(meta.description)
            append(' ')
            append(meta.nameZh.orEmpty())
            append(' ')
            append(meta.descriptionZh.orEmpty())
            append(' ')
            append(meta.tags.joinToString(" "))
        }
        return explicitCategoriesFor(meta.id).ifEmpty { heuristicCategoriesFor(seed) }
    }

    fun primaryCategory(skillId: String): SkillToolCategory =
        categoriesFor(skillId).first()

    fun primaryCategory(meta: SkillMeta): SkillToolCategory =
        categoriesFor(meta).first()

    fun label(category: SkillToolCategory): String = when (category) {
        SkillToolCategory.CHAT -> "Chat Expression"
        SkillToolCategory.MEMORY -> "Memory"
        SkillToolCategory.SKILL -> "Skill Management"
        SkillToolCategory.SELF_EVOLUTION -> "Self Evolution"
        SkillToolCategory.ARTIFACT -> "Artifacts"
        SkillToolCategory.PHONE -> "Phone Control"
        SkillToolCategory.WEB -> "Web"
        SkillToolCategory.MEDIA -> "Media"
        SkillToolCategory.VPN -> "VPN"
        SkillToolCategory.CODE -> "Code"
        SkillToolCategory.SYSTEM -> "System"
    }

    private fun explicitCategoriesFor(skillId: String): Set<SkillToolCategory> =
        categoryIds.filterValues { skillId in it }.keys

    private fun heuristicCategoriesFor(seed: String): Set<SkillToolCategory> {
        val text = seed.lowercase()
        val categories = linkedSetOf<SkillToolCategory>()

        if (text.containsAny("phone", "screen", "tap", "click", "scroll", "input", "navigate", "accessibility", "screenshot", "see_screen", "bg_")) {
            categories += SkillToolCategory.PHONE
        }
        if (text.containsAny("memory", "profile", "config", "session", "history", "remember", "note", "recipe")) {
            categories += SkillToolCategory.MEMORY
        }
        if (text.containsAny("role", "switch_model", "page_control", "console_editor", "self", "upgrade", "evolution", "repair", "persona")) {
            categories += SkillToolCategory.SELF_EVOLUTION
        }
        if (text.containsAny("skill", "skills", "market", "create_skill", "skill_check", "quick_skill", "skill_notes")) {
            categories += SkillToolCategory.SKILL
        }
        if (text.containsAny("ui_builder", "app_manager", "file", "document", "html", "page", "artifact", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "pdf", "csv", "md", "markdown", "storage")) {
            categories += SkillToolCategory.ARTIFACT
        }
        if (text.containsAny("web", "search", "fetch", "browse", "browser", "http", "url", "news", "hot", "weather", "exchange", "calendar", "ip_lookup", "joke", "poem", "hitokoto", "weibo", "zhihu", "douyin", "bilibili", "toutiao", "baidu")) {
            categories += SkillToolCategory.WEB
        }
        if (text.containsAny("image", "video", "icon", "sticker", "bqb", "emoji", "avatar", "photo", "generate")) {
            categories += SkillToolCategory.MEDIA
        }
        if (text.containsAny("vpn", "proxy", "mihomo")) {
            categories += SkillToolCategory.VPN
        }
        if (text.containsAny("shell", "python", "pip", "code", "command")) {
            categories += SkillToolCategory.CODE
        }
        if (text.containsAny("toast", "clipboard", "chat", "message")) {
            categories += SkillToolCategory.CHAT
        }
        if (text.containsAny("permission", "device", "system", "app info")) {
            categories += SkillToolCategory.SYSTEM
        }

        return categories.ifEmpty { setOf(SkillToolCategory.SYSTEM) }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }
}

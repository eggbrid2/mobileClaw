package com.mobileclaw.agent

/**
 * A Role bundles a persona (system prompt addendum), forced skill injection list,
 * and optional model override. The agent operates differently depending on the active role.
 */
data class Role(
    val id: String,
    val name: String,
    val description: String,
    val avatar: String,                         // emoji
    val systemPromptAddendum: String = "",
    val forcedSkillIds: List<String> = emptyList(),
    val modelOverride: String? = null,
    val isBuiltin: Boolean = false,
) {
    companion object {
        val DEFAULT = Role(
            id = "general",
            name = "通用助手",
            description = "无限制通用 AI 助手，适合所有任务",
            avatar = "🦀",
            isBuiltin = true,
        )

        val BUILTINS: List<Role> = listOf(
            DEFAULT,
            Role(
                id = "coder",
                name = "代码专家",
                description = "专注于编程、调试、脚本和自动化任务",
                avatar = "👨‍💻",
                systemPromptAddendum = "You are an expert software engineer. Write clean, efficient code. Always test your solutions. Prefer shell commands for file operations and system tasks.",
                forcedSkillIds = listOf("shell", "create_file"),
                isBuiltin = true,
            ),
            Role(
                id = "web_agent",
                name = "网络助手",
                description = "专注于网络搜索、信息抓取和网页浏览",
                avatar = "🌐",
                systemPromptAddendum = "You excel at finding information online. Always verify facts from multiple sources. Summarize web content clearly and concisely.",
                forcedSkillIds = listOf("web_search", "web_browse", "fetch_url"),
                isBuiltin = true,
            ),
            Role(
                id = "phone_operator",
                name = "手机操控",
                description = "专注于控制 Android 界面、点击、滑动和应用操作",
                avatar = "📱",
                systemPromptAddendum = "You are an expert at controlling Android UIs. Always use see_screen first to understand the current state. Prefer node IDs over coordinates when available.",
                forcedSkillIds = listOf("see_screen", "tap", "scroll", "input_text"),
                isBuiltin = true,
            ),
            Role(
                id = "creator",
                name = "创意助手",
                description = "专注于图片生成、HTML 页面和内容创作",
                avatar = "🎨",
                systemPromptAddendum = "You are a creative assistant specializing in visual content. Generate images with detailed, artistic prompts. Create beautiful, functional HTML pages with modern CSS.",
                forcedSkillIds = listOf("generate_image", "create_html", "create_file"),
                isBuiltin = true,
            ),
        )
    }
}

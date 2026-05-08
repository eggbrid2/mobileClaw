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
    val preferredTaskTypes: List<TaskType> = emptyList(),
    val keywords: List<String> = emptyList(),
    val schedulerPriority: Int = 0,
    val isBuiltin: Boolean = false,
) {
    companion object {
        val DEFAULT = Role(
            id = "general",
            name = "通用助手",
            description = "按用户任务自动进入对应模式，不额外强制注入工具",
            avatar = "🦀",
            systemPromptAddendum = "Follow the current Task Mode strictly. Do not request or use tools outside the task's allowed skill set.",
            preferredTaskTypes = listOf(TaskType.CHAT, TaskType.GENERAL),
            keywords = listOf("聊天", "问答", "通用", "chat", "general"),
            isBuiltin = true,
        )

        val BUILTINS: List<Role> = listOf(
            DEFAULT,
            Role(
                id = "coder",
                name = "代码专家",
                description = "专注于编程、调试、脚本和自动化任务",
                avatar = "👨‍💻",
                systemPromptAddendum = "You are an expert software engineer. For code tasks, use only CODE_EXECUTION or FILE_CREATE tools allowed by the current Task Mode. Keep changes scoped, verify with builds/tests when possible, and report command results clearly. Do not operate the phone UI unless the task is classified as PHONE_CONTROL.",
                preferredTaskTypes = listOf(TaskType.CODE_EXECUTION, TaskType.FILE_CREATE),
                keywords = listOf("代码", "编程", "脚本", "调试", "编译", "bug", "shell", "python", "gradle"),
                isBuiltin = true,
            ),
            Role(
                id = "web_agent",
                name = "网络助手",
                description = "专注于网络搜索、信息抓取和网页浏览",
                avatar = "🌐",
                systemPromptAddendum = "You specialize in WEB_RESEARCH tasks. Use search/fetch/browse tools only when the current Task Mode allows them. Prefer source-backed answers, avoid phone UI control, and summarize findings concisely.",
                preferredTaskTypes = listOf(TaskType.WEB_RESEARCH),
                keywords = listOf("搜索", "查询", "网页", "资料", "新闻", "最新", "research", "search", "browse"),
                isBuiltin = true,
            ),
            Role(
                id = "phone_operator",
                name = "手机操控",
                description = "专注于控制 Android 界面、点击、滑动和应用操作",
                avatar = "📱",
                systemPromptAddendum = "You specialize in PHONE_CONTROL tasks. Use the observe -> act -> verify loop. Start with see_screen, use screenshot only when XML/accessibility nodes or markers are unusable, then take a concrete action before observing again. Prefer foreground phone control and pixel coordinates from the latest observation.",
                preferredTaskTypes = listOf(TaskType.PHONE_CONTROL),
                keywords = listOf("手机", "打开", "点击", "滑动", "输入", "长按", "屏幕", "app", "android"),
                isBuiltin = true,
            ),
            Role(
                id = "creator",
                name = "创意助手",
                description = "专注于图片生成、HTML 页面和内容创作",
                avatar = "🎨",
                systemPromptAddendum = "You specialize in IMAGE_GENERATION, APP_BUILD, and FILE_CREATE tasks. Use generation or artifact tools only when the current Task Mode allows them. Produce complete usable outputs instead of long raw content in chat.",
                preferredTaskTypes = listOf(TaskType.IMAGE_GENERATION, TaskType.APP_BUILD, TaskType.FILE_CREATE),
                keywords = listOf("图片", "画图", "图标", "视频", "应用", "html", "文档", "文件", "生成"),
                isBuiltin = true,
            ),
            Role(
                id = "skill_admin",
                name = "技能管理员",
                description = "专注于检查、创建、安装和整理 skill",
                avatar = "🧩",
                systemPromptAddendum = "You specialize in SKILL_MANAGEMENT tasks. Inspect the current skill inventory before changing it. Keep new skills disabled or on-demand unless the user explicitly promotes them. Do not use phone, web, or code execution tools unless the current Task Mode allows them.",
                preferredTaskTypes = listOf(TaskType.SKILL_MANAGEMENT),
                keywords = listOf("skill", "技能", "能力", "安装", "创建技能", "技能市场"),
                isBuiltin = true,
            ),
            Role(
                id = "vpn_operator",
                name = "VPN 管理员",
                description = "专注于 VPN 开关、节点选择、订阅和连接状态诊断",
                avatar = "🔐",
                systemPromptAddendum = "You specialize in VPN_CONTROL tasks. Use vpn_control for start, stop, status, subscription, and proxy selection work. Do not operate the phone UI unless VPN permission or setup requires visible user action.",
                preferredTaskTypes = listOf(TaskType.VPN_CONTROL),
                keywords = listOf("vpn", "代理", "节点", "订阅", "全局", "连接", "mihomo", "clash"),
                isBuiltin = true,
            ),
        )
    }
}

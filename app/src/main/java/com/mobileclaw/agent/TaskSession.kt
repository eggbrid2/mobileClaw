package com.mobileclaw.agent

import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillRegistry
import java.util.UUID

enum class TaskType {
    CHAT,
    GENERAL,
    PHONE_CONTROL,
    WEB_RESEARCH,
    FILE_CREATE,
    APP_BUILD,
    IMAGE_GENERATION,
    VPN_CONTROL,
    SKILL_MANAGEMENT,
    CODE_EXECUTION,
}

enum class TaskStatus {
    RUNNING,
    DONE,
    BLOCKED,
    NEED_USER,
    FAILED,
}

data class TaskSession(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val type: TaskType,
    val status: TaskStatus = TaskStatus.RUNNING,
    val context: Map<String, String> = emptyMap(),
    val startedAt: Long = System.currentTimeMillis(),
    val maxSteps: Int = 20,
)

data class TaskPlan(
    val taskType: TaskType,
    val summary: String,
    val steps: List<String>,
    val successCriteria: String,
) {
    fun toPrompt(): String = buildString {
        appendLine("## Task Plan")
        appendLine("Task type: $taskType")
        appendLine("Summary: $summary")
        appendLine("Steps:")
        steps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        appendLine("Success criteria: $successCriteria")
        appendLine("Follow this plan, but update your next action based on observations. Do not restart planning unless blocked.")
    }
}

object TaskPlanner {
    fun fallback(goal: String, taskType: TaskType): TaskPlan = fallbackPlan(goal, taskType)

    suspend fun plan(
        llm: LlmGateway,
        goal: String,
        taskType: TaskType,
        language: String = "auto",
        priorContext: String = "",
    ): TaskPlan {
        val prompt = """
You are planning one MobileClaw task before execution.
Return a concise operational plan. Do not call tools.

Task type: $taskType
User goal:
$goal

Recent chat context:
${priorContext.take(2400)}

Output format:
Summary: ...
Steps:
1. ...
2. ...
3. ...
Success criteria: ...

Rules:
- Keep 3-6 steps.
- Generate the todo steps from BOTH the current user goal and the recent chat context, especially the last 10 chat records if provided.
- Treat short follow-ups like "continue", "change it", "optimize", "not this", or "do the previous one" as references to the recent context, not standalone tasks.
- Align success criteria with what the user has been trying to achieve across the recent context, not only the literal latest sentence.
- For PHONE_CONTROL, plan in observe -> act -> verify terms.
- For WEB_RESEARCH, plan source gathering then synthesis.
- For APP_BUILD/FILE_CREATE, plan artifact creation and verification.
${if (language == "zh") "- Write the plan in Simplified Chinese." else ""}
""".trimIndent()

        val response = runCatching {
            llm.chat(
                ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = "Create a short task plan only. No tools."),
                        Message(role = "user", content = prompt),
                    ),
                    tools = emptyList(),
                    stream = false,
                )
            ).content.orEmpty()
        }.getOrDefault("")

        return parsePlan(response, taskType)
            ?: fallbackPlan(goal, taskType)
    }

    private fun parsePlan(raw: String, taskType: TaskType): TaskPlan? {
        if (raw.isBlank()) return null
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val summary = lines.firstOrNull { it.startsWith("Summary:", ignoreCase = true) || it.startsWith("概要") }
            ?.substringAfter(":")
            ?.trim()
            ?.ifBlank { null }
            ?: lines.firstOrNull().orEmpty().take(180)
        val steps = lines
            .filter { it.matches(Regex("""^\d+[\.)、]\s+.+""")) || it.startsWith("- ") }
            .map { it.replace(Regex("""^\d+[\.)、]\s+"""), "").removePrefix("- ").trim() }
            .filter { it.isNotBlank() }
            .take(6)
        val criteria = lines.firstOrNull { it.startsWith("Success criteria:", ignoreCase = true) || it.startsWith("成功") }
            ?.substringAfter(":")
            ?.trim()
            ?.ifBlank { null }
            ?: "The user goal is satisfied and the result is verified."
        return TaskPlan(taskType, summary.ifBlank { "Execute the user task." }, steps.ifEmpty { fallbackPlan("", taskType).steps }, criteria)
    }

    private fun fallbackPlan(goal: String, taskType: TaskType): TaskPlan = when (taskType) {
        TaskType.PHONE_CONTROL -> TaskPlan(
            taskType,
            "Operate the phone UI to complete the requested goal.",
            listOf("Observe the current screen.", "Choose the next visible UI action.", "Execute the action.", "Verify the new screen and continue until complete."),
            "The requested phone-side state is reached, or a clear blocker is reported.",
        )
        TaskType.WEB_RESEARCH -> TaskPlan(taskType, "Research the requested information.", listOf("Search or open relevant pages.", "Extract useful facts.", "Summarize the answer with source context."), "The answer addresses the user's question.")
        TaskType.APP_BUILD -> TaskPlan(taskType, "Build the requested app/artifact.", listOf("Create or update the artifact.", "Check the result.", "Report where it is available."), "The artifact is created and usable.")
        TaskType.FILE_CREATE -> TaskPlan(taskType, "Create or update requested files.", listOf("Determine the needed file format.", "Create or update the file.", "Verify the result."), "The requested file work is complete.")
        TaskType.IMAGE_GENERATION -> TaskPlan(taskType, "Generate the requested media.", listOf("Use the generation tool with the prompt.", "Return the generated result."), "The media result is available.")
        TaskType.VPN_CONTROL -> TaskPlan(taskType, "Change or inspect VPN state.", listOf("Check the requested VPN action.", "Call VPN control.", "Report the resulting state."), "VPN state matches the request.")
        TaskType.SKILL_MANAGEMENT -> TaskPlan(taskType, "Manage skills as requested.", listOf("Inspect current skills if needed.", "Create/install/update the skill.", "Report the change."), "The skill change is complete.")
        TaskType.CODE_EXECUTION -> TaskPlan(taskType, "Run code or commands.", listOf("Choose the right execution tool.", "Run the code/command.", "Report output and errors."), "The command result is clear.")
        TaskType.CHAT, TaskType.GENERAL -> TaskPlan(taskType, "Execute the user task.", listOf("Select the right tool or answer path.", "Act or answer.", "Verify completion."), "The user goal is satisfied.")
    }
}

object TaskClassifier {
    fun classify(goal: String, hasImage: Boolean = false, hasFile: Boolean = false): TaskType {
        val text = goal.lowercase()
        if (hasImage && text.anyContains("打开", "启动", "点击", "滑动", "滚动", "输入", "长按", "返回", "主页", "发微信", "发短信", "打电话", "操作手机", "控制手机", "看屏幕", "读屏幕", "点一下", "按一下", "帮我点", "帮我操作", "open ", "launch ", "click ", "tap ", "scroll ")) {
            return TaskType.PHONE_CONTROL
        }
        if (hasImage) return TaskType.GENERAL
        if (hasFile) return TaskType.FILE_CREATE

        if (text.anyContains("vpn", "代理", "翻墙", "节点", "订阅", "全局")) return TaskType.VPN_CONTROL
        if (text.anyContains("打开", "启动", "点击", "滑动", "滚动", "输入", "长按", "返回", "主页", "发微信", "发短信", "打电话", "操作手机", "控制手机", "看屏幕", "读屏幕", "open ", "launch ", "click ")) {
            return TaskType.PHONE_CONTROL
        }
        if (isFollowUpOnly(text)) return TaskType.GENERAL
        if (hasExplicitWebResearchIntent(text)) return TaskType.WEB_RESEARCH
        if (hasExplicitImageGenerationIntent(text)) return TaskType.IMAGE_GENERATION
        if (hasExplicitPageBuildIntent(text)) return TaskType.APP_BUILD
        if (text.anyContains("做个app", "做一个app", "创建应用", "小应用", "网页应用", "miniapp", "mini app", "html", "game", "calculator", "小游戏", "程序")) return TaskType.APP_BUILD
        if (hasExplicitFileIntent(text)) return TaskType.FILE_CREATE
        if (text.anyContains("role", "persona", "角色", "人设", "创建角色", "新建角色", "修改角色", "角色管理", "切换角色")) return TaskType.SKILL_MANAGEMENT
        if (text.anyContains("skill", "技能", "安装能力", "创建技能", "技能市场")) return TaskType.SKILL_MANAGEMENT
        if (text.anyContains("shell", "python", "脚本", "执行命令", "运行代码", "pip")) return TaskType.CODE_EXECUTION
        return TaskType.GENERAL
    }

    private fun String.anyContains(vararg needles: String): Boolean = needles.any { contains(it) }

    private fun hasExplicitPageBuildIntent(text: String): Boolean {
        if (text.anyContains("创建页面", "生成页面", "做个页面", "做一个页面", "新建页面", "设计页面", "开发页面", "搭建页面")) return true
        if (text.anyContains("创建原生页面", "生成原生页面", "创建ai页面", "生成ai页面", "aipage", "ai native page")) return true
        val pageNoun = text.anyContains("页面", "原生页面", "ai页面", "dashboard", "仪表盘", "表单", "管理页")
        val buildVerb = text.anyContains("创建", "生成", "做个", "做一个", "新建", "设计", "开发", "搭建", "build", "create")
        return pageNoun && buildVerb
    }

    private fun hasExplicitWebResearchIntent(text: String): Boolean {
        if (text.anyContains("不要搜索", "别搜索", "不用搜索", "不要联网", "别联网", "不用联网", "按上面", "基于上面")) return false
        if (text.anyContains("联网搜索", "网页搜索", "搜索网页", "搜一下", "查一下资料", "找来源", "找资料", "浏览网页", "web research", "web search", "search web", "browse web")) return true
        if (text.anyContains("新闻", "最新", "官网", "网页", "research", "search", "browse")) return true
        return text.anyContains("搜索") && !text.anyContains("搜索框", "搜索按钮", "搜索页面")
    }

    private fun hasExplicitImageGenerationIntent(text: String): Boolean {
        if (text.anyContains("这张图片", "这个图片", "图片里", "图里", "看图", "识别图片", "分析图片", "描述图片", "what is in the image", "describe image")) return false
        if (text.anyContains("生成图片", "生成一张", "画图", "画一张", "绘制", "出图", "生图", "做张图", "生成图标", "生成视频", "image generation", "generate image", "draw ")) return true
        val mediaNoun = text.anyContains("图片", "图像", "图标", "海报", "封面", "插画", "视频", "icon", "poster", "video")
        val createVerb = text.anyContains("生成", "创建", "设计", "制作", "画", "做一个", "做个", "create", "generate", "design")
        return mediaNoun && createVerb
    }

    private fun hasExplicitFileIntent(text: String): Boolean {
        if (text.anyContains("这个文件是什么", "解释文件", "读取文件", "看看文件", "文件内容是什么")) return true
        val officeNoun = text.anyContains("ppt", "pptx", "docx", "word", "xlsx", "excel", "pdf", "csv", "markdown", "md")
        val createVerb = text.anyContains("生成", "创建", "写", "导出", "保存", "做一个", "做个", "制作", "整理成", "create", "generate", "export", "save")
        if (officeNoun && createVerb) return true
        return text.anyContains("创建文件", "生成文件", "写入文件", "保存文件", "导出文件", "生成文档", "创建文档")
    }

    private fun isFollowUpOnly(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.length > 40) return false
        return normalized.anyContains(
            "继续", "接着", "然后呢", "详细说", "改一下", "改下", "优化下", "优化一下", "不是这个",
            "不是这样", "不对", "换个方式", "换成", "改成", "按上面", "基于上面", "就这个", "它",
            "this", "that", "continue", "change it", "update it", "not this",
        )
    }
}

object TaskToolPolicy {
    fun select(
        registry: SkillRegistry,
        taskType: TaskType,
        forcedSkillIds: List<String> = emptyList(),
        memoryContext: String = "",
    ): List<SkillMeta> {
        val allowed = allowedSkillIds(taskType)
        val memoryFiltered = applyMemoryToolPolicy(allowed, taskType, memoryContext)
        val taskSkills = memoryFiltered.mapNotNull { registry.get(it)?.meta }
        val forced = forcedSkillIds
            .filter { it in memoryFiltered }
            .mapNotNull { registry.get(it)?.meta }
        return (taskSkills + forced).distinctBy { it.id }
    }

    fun prompt(taskType: TaskType): String = when (taskType) {
        TaskType.PHONE_CONTROL -> """
## Current Task Mode: VLM_PHONE_CONTROL
- This is a pure phone-operation task mode. Treat the current phone screen as your only operating environment.
- Work as: observe -> act -> verify -> continue/done. Do not drift into UI building, web research, or generic chat unless the user explicitly changes the task.
- First inspect with `see_screen` unless the latest step already observed the screen.
- The screenshot coordinates returned by `see_screen`/`screenshot` are image coordinates. Use them directly with `tap`, `scroll`, and `long_click`; those tools map image pixels to real device pixels.
- Use `screenshot` only as a fallback when marker detection is unusable or a raw visual check is needed.
- After an observation, take a concrete action (`tap`, `scroll`, `input_text`, `long_click`, `navigate`) or finish/block. Do not observe twice in a row.
- After launching or navigating, use the foreground package/activity returned by tool results, or call `phone_status`, to verify whether the target app is open.
""".trimIndent()
        TaskType.WEB_RESEARCH -> """
## Current Task Mode: WEB_RESEARCH
- Use web/search tools only. Gather sources, then synthesize a concise answer.
- Do not operate the phone UI unless the user explicitly asks.
""".trimIndent()
        TaskType.APP_BUILD -> """
## Current Task Mode: APP_BUILD
- Build or update runnable UI with tools. NEVER return raw code/HTML as chat text.
- Default route: for pages, dashboards, forms, settings panels, management screens, data viewers, and lightweight tools, use `ui_builder` to create an AI Native Page.
- Use `app_manager` only when the user explicitly asks for an app/mini-app/program/game, or when the artifact needs custom HTML/CSS/JavaScript, canvas, complex browser rendering, Python backend, SQLite, or a WebView-style runtime.
- Use `create_html` only for one-off rich HTML reports/previews shown in chat, not persistent apps or native pages.
""".trimIndent()
        TaskType.IMAGE_GENERATION -> """
## Current Task Mode: IMAGE_GENERATION
- Use image/video/icon generation tools. Ask only if a required prompt detail is missing.
""".trimIndent()
        TaskType.FILE_CREATE -> """
## Current Task Mode: FILE_CREATE
- Use file/document tools to create, read, list, or update files.
- For office documents (PPT/PPTX, Word/DOCX, Excel/XLSX, PDF), you MUST call `generate_document`.
- Do NOT use `create_file` for PPTX/DOCX/XLSX/PDF. Do NOT use `run_python`, `pip_install`, pandas, python-pptx, openpyxl, or xlsxwriter for office generation.
- Use `create_file` only for plain text-like files such as txt, md, json, csv when no office layout is needed.
""".trimIndent()
        TaskType.VPN_CONTROL -> """
## Current Task Mode: VPN_CONTROL
- Use `vpn_control` to start, stop, or check VPN state. Do not use phone UI unless permission/setup requires the user.
""".trimIndent()
        TaskType.SKILL_MANAGEMENT -> """
## Current Task Mode: SKILL_MANAGEMENT
- Use skill inventory, market, or creation tools. Keep new skills disabled/on-demand unless the user promotes them.
- For role/persona requests, use `role_manager` to list, create, update, delete, or activate roles. Use `switch_role` only for simple activation.
""".trimIndent()
        TaskType.CODE_EXECUTION -> """
## Current Task Mode: CODE_EXECUTION
- Use shell/python/pip tools only when needed. Report command results clearly.
""".trimIndent()
        TaskType.CHAT, TaskType.GENERAL -> """
## Current Task Mode: GENERAL
- This is a conversation-first mode. Answer directly unless a very small allowed helper tool is clearly useful.
- Do not search the web, operate the phone, create files/pages, switch roles, or manage VPN from GENERAL. Those require a more specific Task Mode.
- For casual conversation, humor, teasing, celebration, awkwardness, comfort, thanks, surprise, speechless moments, or meme-like replies, actively consider `sticker_bqb`.
- When the user gives durable personal preferences or asks you to remember/configure something, store it with `user_config` for explicit user settings or `user_profile`/`memory` for inferred facts.
- Before personalized advice, prefer the injected User Memory and Configuration context; call `user_config` or `user_profile` only if you need fresher or complete details.
- Only call `sticker_bqb` when the sticker's query matches your intended reaction or emotion. Send at most one sticker per turn, and do not use it for serious, professional, or safety-critical answers.
- If you send a sticker, keep accompanying text short and natural; do not explain the sticker as an attachment.
""".trimIndent()
    }

    private fun allowedSkillIds(taskType: TaskType): List<String> = when (taskType) {
        TaskType.PHONE_CONTROL -> listOf(
            "see_screen", "screenshot", "tap", "scroll", "input_text", "long_click", "navigate", "list_apps",
            "phone_status", "check_permissions", "vpn_control",
        )
        TaskType.WEB_RESEARCH -> listOf("web_search", "fetch_url", "web_browse", "web_content", "web_js", "vpn_control")
        TaskType.FILE_CREATE -> listOf("generate_document", "create_file", "read_file", "list_files", "create_html")
        TaskType.APP_BUILD -> listOf("ui_builder", "app_manager", "create_html", "read_file", "create_file", "list_files")
        TaskType.IMAGE_GENERATION -> listOf("generate_image", "generate_icon", "generate_video", "create_file")
        TaskType.VPN_CONTROL -> listOf("vpn_control")
        TaskType.SKILL_MANAGEMENT -> listOf("skill_check", "quick_skill", "skill_market", "create_skill", "skill_notes", "role_manager", "switch_role", "generate_icon")
        TaskType.CODE_EXECUTION -> listOf("shell", "run_python", "pip_install", "read_file", "create_file", "list_files")
        TaskType.CHAT -> listOf("sticker_bqb")
        TaskType.GENERAL -> listOf("memory", "user_profile", "user_config", "sticker_bqb")
    }

    private fun applyMemoryToolPolicy(
        allowed: List<String>,
        taskType: TaskType,
        memoryContext: String,
    ): List<String> {
        if (memoryContext.isBlank()) return allowed
        val lower = memoryContext.lowercase()
        var result = allowed
        val noWeb = lower.contains("不要搜索") ||
            lower.contains("不要网页搜索") ||
            lower.contains("不要联网") ||
            lower.contains("不用联网") ||
            lower.contains("no web search") ||
            lower.contains("offline only") ||
            lower.contains("no_web_search") ||
            lower.contains("image_understanding.no_web_search") ||
            (lower.contains("网页搜索") && listOf("不该", "不希望", "老是", "总是", "经常", "不停").any { lower.contains(it) })
        if (noWeb && taskType != TaskType.WEB_RESEARCH) {
            result = result.filterNot { it in listOf("web_search", "fetch_url", "web_browse", "web_content", "web_js") }
        }
        val noPhone = lower.contains("不要操作手机") || lower.contains("no phone control")
        if (noPhone && taskType != TaskType.PHONE_CONTROL) {
            result = result.filterNot { it in listOf("see_screen", "screenshot", "tap", "scroll", "input_text", "long_click", "navigate", "phone_status") }
        }
        val preferNativePage = lower.contains("页面优先生成原生页面") ||
            lower.contains("优先生成原生页面") ||
            lower.contains("ai native page") ||
            lower.contains("native page first")
        val preferMiniApp = lower.contains("程序优先生成miniapp") ||
            lower.contains("优先生成miniapp") ||
            lower.contains("prefer miniapp")
        if (taskType == TaskType.APP_BUILD) {
            result = when {
                preferNativePage && !preferMiniApp -> result.sortedBy { if (it == "ui_builder") 0 else 1 }
                preferMiniApp && !preferNativePage -> result.sortedBy { if (it == "app_manager") 0 else 1 }
                else -> result
            }
        }
        return result
    }
}

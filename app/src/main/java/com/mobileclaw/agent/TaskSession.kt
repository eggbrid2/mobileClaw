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
${priorContext.take(1200)}

Output format:
Summary: ...
Steps:
1. ...
2. ...
3. ...
Success criteria: ...

Rules:
- Keep 3-6 steps.
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
        if (hasImage) return TaskType.GENERAL
        if (hasFile) return TaskType.FILE_CREATE

        if (text.anyContains("vpn", "代理", "翻墙", "节点", "订阅", "全局")) return TaskType.VPN_CONTROL
        if (text.anyContains("打开", "启动", "点击", "滑动", "滚动", "输入", "长按", "返回", "主页", "发微信", "发短信", "打电话", "操作手机", "控制手机", "看屏幕", "读屏幕", "open ", "launch ", "click ")) {
            return TaskType.PHONE_CONTROL
        }
        if (text.anyContains("搜索", "查询", "查一下", "网页", "浏览", "新闻", "最新", "资料", "research", "search", "browse")) return TaskType.WEB_RESEARCH
        if (text.anyContains("生成图片", "画图", "图片", "图标", "视频", "image", "icon", "video")) return TaskType.IMAGE_GENERATION
        if (text.anyContains("创建页面", "生成页面", "做个页面", "做一个页面", "原生页面", "ai页面", "aipage", "dashboard", "仪表盘", "表单", "管理页")) return TaskType.APP_BUILD
        if (text.anyContains("做个app", "做一个app", "创建应用", "小应用", "网页应用", "miniapp", "mini app", "html", "game", "calculator", "小游戏", "程序")) return TaskType.APP_BUILD
        if (text.anyContains("文件", "文档", "ppt", "docx", "xlsx", "pdf", "csv", "markdown")) return TaskType.FILE_CREATE
        if (text.anyContains("skill", "技能", "安装能力", "创建技能", "技能市场")) return TaskType.SKILL_MANAGEMENT
        if (text.anyContains("shell", "python", "脚本", "执行命令", "运行代码", "pip")) return TaskType.CODE_EXECUTION
        return TaskType.GENERAL
    }

    private fun String.anyContains(vararg needles: String): Boolean = needles.any { contains(it) }
}

object TaskToolPolicy {
    fun select(registry: SkillRegistry, taskType: TaskType, forcedSkillIds: List<String> = emptyList()): List<SkillMeta> {
        val allowed = allowedSkillIds(taskType)
        val taskSkills = allowed.mapNotNull { registry.get(it)?.meta }
        val forced = forcedSkillIds
            .filter { it in allowed }
            .mapNotNull { registry.get(it)?.meta }
        return (taskSkills + forced).distinctBy { it.id }
    }

    fun prompt(taskType: TaskType): String = when (taskType) {
        TaskType.PHONE_CONTROL -> """
## Current Task Mode: PHONE_CONTROL
- Your context is the current phone task only. Use screen observations and previous actions from this task.
- Allowed pattern: observe -> act -> verify -> continue/done.
- First inspect with `see_screen` unless the latest step already observed the screen.
- Use `screenshot` only as a fallback when XML/accessibility content is empty, marker detection is unusable, or a raw visual check is needed.
- After an observation, you must take a concrete action (`tap`, `scroll`, `input_text`, `long_click`, `navigate`) or finish/block. Do not observe twice in a row.
- Prefer foreground phone control. Use coordinates from `see_screen` directly.
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
""".trimIndent()
        TaskType.CODE_EXECUTION -> """
## Current Task Mode: CODE_EXECUTION
- Use shell/python/pip tools only when needed. Report command results clearly.
""".trimIndent()
        TaskType.CHAT, TaskType.GENERAL -> """
## Current Task Mode: GENERAL
- Use only the tools needed for this task. If the user asks to operate the phone, switch behavior to phone-control style.
- For casual conversation, humor, teasing, celebration, awkwardness, comfort, thanks, surprise, speechless moments, or meme-like replies, actively consider `sticker_bqb`.
- Only call `sticker_bqb` when the sticker's query matches your intended reaction or emotion. Send at most one sticker per turn, and do not use it for serious, professional, or safety-critical answers.
- If you send a sticker, keep accompanying text short and natural; do not explain the sticker as an attachment.
""".trimIndent()
    }

    private fun allowedSkillIds(taskType: TaskType): List<String> = when (taskType) {
        TaskType.PHONE_CONTROL -> listOf(
            "see_screen", "screenshot", "tap", "scroll", "input_text", "long_click", "navigate", "list_apps",
            "check_permissions", "vpn_control",
        )
        TaskType.WEB_RESEARCH -> listOf("web_search", "fetch_url", "web_browse", "web_content", "web_js", "vpn_control")
        TaskType.FILE_CREATE -> listOf("generate_document", "create_file", "read_file", "list_files", "create_html")
        TaskType.APP_BUILD -> listOf("ui_builder", "app_manager", "create_html", "read_file", "create_file", "list_files")
        TaskType.IMAGE_GENERATION -> listOf("generate_image", "generate_icon", "generate_video", "create_file")
        TaskType.VPN_CONTROL -> listOf("vpn_control")
        TaskType.SKILL_MANAGEMENT -> listOf("skill_check", "quick_skill", "skill_market", "create_skill", "skill_notes")
        TaskType.CODE_EXECUTION -> listOf("shell", "run_python", "pip_install", "read_file", "create_file", "list_files")
        TaskType.CHAT -> listOf("sticker_bqb")
        TaskType.GENERAL -> listOf(
            "see_screen", "screenshot", "tap", "scroll", "input_text", "long_click", "navigate",
            "web_search", "fetch_url", "create_file", "read_file", "list_files",
            "ui_builder", "app_manager", "create_html", "memory", "user_profile", "vpn_control", "sticker_bqb",
        )
    }
}

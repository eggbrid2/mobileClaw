package com.mobileclaw.ui.common

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.llm.cleanLocalGeneratedText

internal val VISUAL_SKILL_IDS = setOf("screenshot", "see_screen", "bg_screenshot")

internal fun sanitizeUserFacingNarration(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return ""
    val lowered = text.lowercase()
    return when {
        "video_api_endpoint" in lowered || "video_api_key" in lowered ->
            "先确认视频生成能力是否已经连通"
        "image_api_endpoint" in lowered || "image_api_key" in lowered ->
            "先确认图片生成能力是否已经连通"
        ("gateway" in lowered || "capability" in lowered || "endpoint" in lowered || "api key" in lowered) &&
            ("video" in lowered || "image" in lowered || "图像" in text || "图片" in text || "视频" in text) ->
            when {
                "video" in lowered || "视频" in text -> "先确认视频生成能力是否已经连通"
                else -> "先确认图片生成能力是否已经连通"
            }
        "已正确加载且处于活跃状态" in text ->
            "先确认当前能力已经成功接通"
        else -> text
    }
}

internal fun friendlyThinkingUpdate(rawThought: String, plannedSteps: List<String>): String {
    val clean = sanitizeUserFacingNarration(rawThought.cleanLocalGeneratedText().trim())
    val planned = plannedSteps.firstOrNull { it.isNotBlank() }?.let(::sanitizeUserFacingNarration)
    if (clean.isBlank()) return planned ?: "正在整理当前进展"
    val generic = listOf(
        "思考完成",
        "在分析下一步",
        "后台复盘已完成",
        "已启动后台复盘",
        "正在反思",
        "整理进展并继续",
    )
    if (generic.any { clean.contains(it) }) {
        return planned ?: when {
            clean.contains("重复") -> "发现步骤效果不理想，正在换一种方式继续"
            clean.contains("20 步") || clean.contains("检查点") -> "正在复盘已完成的操作，确认下一段该怎么推进"
            clean.contains("后台复盘") -> "正在把前面的结果整理成下一步依据"
            else -> "正在根据当前结果选择下一步"
        }
    }
    return clean.take(120)
}

internal fun plannedStageForAction(plannedSteps: List<String>, actionIndex: Int): String {
    if (plannedSteps.isEmpty()) return ""
    val index = actionIndex.coerceIn(0, plannedSteps.lastIndex)
    return sanitizeUserFacingNarration(plannedSteps[index].trim())
}

internal fun stageAwareSkillDescription(stage: String, skillId: String, params: Map<String, Any>): String {
    val toolPurpose = friendlySkillDescription(skillId, params)
    if (stage.isBlank()) return toolPurpose
    return when {
        stage.contains(toolPurpose) -> stage
        toolPurpose == skillId -> stage
        else -> "$stage：$toolPurpose"
    }.take(140)
}

internal fun userFacingActionResult(skillId: String, stageText: String): String =
    when (skillId) {
        "tap", "long_click", "scroll", "input_text", "navigate", "bg_launch" ->
            ""
        "see_screen", "read_screen", "bg_read_screen", "screenshot", "bg_screenshot" ->
            ""
        "web_search", "fetch_url", "web_browse", "web_content", "web_js" ->
            ""
        "app_manager" ->
            if (stageText.contains("MiniAPP")) "这一步会直接动 MiniAPP 的内容和结构" else ""
        "ui_builder" ->
            if (stageText.contains("原生页面")) "这一步会直接改原生页面的展示或交互" else ""
        "memory", "user_config" ->
            ""
        else -> ""
    }

internal fun conciseUserPlanSummary(text: String, limit: Int = 90): String {
    val normalized = sanitizeUserFacingNarration(text)
        .lineSequence()
        .map { it.trim().trimStart('-', '•', '*', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '、') }
        .filter { it.isNotBlank() }
        .joinToString("；")
        .replace("  ", " ")
        .trim()
    return when {
        normalized.isBlank() -> ""
        normalized.length <= limit -> normalized
        else -> normalized.take(limit).trimEnd() + "…"
    }
}

internal fun userFacingPlanResult(steps: List<String>, summary: String): String =
    when {
        steps.size >= 2 -> "先做「${steps.first().trim()}」，后面再处理「${steps[1].trim()}」"
        steps.size == 1 -> "先从「${steps.first().trim()}」开始"
        summary.isNotBlank() -> conciseUserPlanSummary(summary)
        else -> "已经整理出本轮的处理顺序"
    }

internal fun userFacingInitialIntent(firstStep: String?, secondStep: String?, channelSummary: String): String =
    when {
        !firstStep.isNullOrBlank() ->
            firstStep.trim()
        channelSummary.isNotBlank() ->
            channelSummary.trim().trimEnd('。', '.')
        !secondStep.isNullOrBlank() ->
            secondStep.trim()
        else -> ""
    }

internal fun userFacingThinkingResult(thought: String, plannedSteps: List<String>): String {
    val current = plannedSteps.firstOrNull { it.isNotBlank() }?.let(::sanitizeUserFacingNarration).orEmpty()
    val next = plannedSteps.drop(1).firstOrNull { it.isNotBlank() }?.let(::sanitizeUserFacingNarration).orEmpty()
    val clean = sanitizeUserFacingNarration(thought.cleanLocalGeneratedText().trim()).take(100)
    return when {
        current.isNotBlank() && next.isNotBlank() -> "$current / $next"
        current.isNotBlank() -> current
        clean.isNotBlank() ->
            clean
        else -> ""
    }
}

internal fun userFacingSkillStart(stageText: String, skillId: String, params: Map<String, Any>): String {
    fun p(key: String) = params[key]?.toString()?.trim().orEmpty()
    val stage = stageText.trim()
    val concrete = when (skillId) {
        "web_search" -> p("query").takeIf { it.isNotBlank() }?.let { "查“$it”相关的内容" }
        "fetch_url", "web_browse" -> p("url").takeIf { it.isNotBlank() }?.let { "打开这个网页，看看里面有没有当前要用的信息" }
        "web_content" -> "把网页正文读出来，筛掉没用的部分"
        "see_screen", "read_screen", "screenshot", "bg_screenshot", "bg_read_screen" -> "先看清当前界面，再决定下一步怎么点"
        "tap" -> p("label").ifBlank { p("text") }.takeIf { it.isNotBlank() }?.let { "点开“$it”看看界面会怎么变化" }
        "long_click" -> p("label").ifBlank { p("text") }.takeIf { it.isNotBlank() }?.let { "长按“$it”看看有没有隐藏操作" }
        "scroll" -> "继续翻一下，看看目标内容是不是在下面"
        "input_text" -> p("text").takeIf { it.isNotBlank() }?.let { "把需要的内容填进去：${it.take(24)}" }
        "navigate" -> when {
            p("package_name").isNotBlank() -> "把目标应用打开到可操作的界面"
            p("action") == "back" -> "退回上一层，确认是不是走错了路径"
            p("action") == "home" -> "回到桌面，从头进入正确的入口"
            else -> "切一下当前界面，确认正确的操作路径"
        }
        "list_apps" -> "看手机里有没有这个应用"
        "app_manager" -> when (p("action")) {
            "create" -> "创建 MiniAPP"
            "update" -> "更新 MiniAPP"
            "validate" -> "检查 MiniAPP"
            "open" -> "打开 MiniAPP"
            else -> stage.ifBlank { "处理 MiniAPP" }
        }
        "ui_builder" -> when (p("action")) {
            "create" -> "创建原生页面"
            "update" -> "更新原生页面"
            "validate" -> "检查原生页面"
            "open" -> "打开原生页面"
            else -> stage.ifBlank { "处理原生页面" }
        }
        "create_file" -> p("filename").takeIf { it.isNotBlank() }?.let { "把文件生出来：$it" }
        "create_html" -> p("title").takeIf { it.isNotBlank() }?.let { "把这个网页结果做出来：$it" }
        "generate_image" -> "把需要的图片生成出来"
        "generate_document" -> "把文档内容生成出来"
        "memory" -> "记忆更新"
        "user_config" -> "用户配置更新"
        "shell", "run_python" -> "执行命令"
        else -> null
    }
    if (!concrete.isNullOrBlank()) return sanitizeUserFacingNarration(concrete)
    if (stage.isNotBlank()) return sanitizeUserFacingNarration(stage)
    return when {
        skillId in VISUAL_SKILL_IDS -> "查看当前界面"
        else -> ""
    }
}

internal fun userFacingActionNext(stageText: String, skillId: String, text: String): String? {
    val explicit = nextStepHint(skillId, text)
    if (!explicit.isNullOrBlank()) return explicit
    return when {
        stageText.isNotBlank() -> stageText.trim()
        skillId in VISUAL_SKILL_IDS -> "查看画面后继续"
        skillId == "web_search" || skillId == "web_content" || skillId == "fetch_url" || skillId == "web_browse" ->
            "筛选信息"
        else -> null
    }
}

internal fun friendlySkillDescription(skillId: String, params: Map<String, Any>): String {
    fun p(key: String) = params[key]?.toString()?.trim() ?: ""
    return when (skillId) {
        "screenshot", "bg_screenshot" -> "看清当前界面，确认下一步该怎么操作"
        "read_screen", "bg_read_screen", "see_screen" -> "识别当前界面里的可操作入口和状态"
        "tap" -> {
            val label = p("label").ifBlank { p("text") }.take(28)
            if (label.isNotBlank()) "点击和“$label”相关的位置" else "点击当前目标入口"
        }
        "long_click" -> "长按目标内容，看看有没有更多可用操作"
        "scroll" -> "继续往下或往上找目标内容"
        "input_text" -> {
            val text = p("text").take(32)
            if (text.isNotBlank()) "填写当前需要输入的内容" else "向当前输入框填写内容"
        }
        "navigate" -> {
            val action = p("action")
            when {
                action == "back" -> "回到上一层，继续沿着正确路径走"
                action == "home" -> "回到桌面，准备重新进入目标应用"
                p("package_name").isNotBlank() -> "打开目标应用，进入真正要操作的界面"
                else -> "切换当前界面，继续推进任务"
            }
        }
        "list_apps" -> "确认手机里有没有目标应用"
        "web_search" -> {
            val q = p("query").take(40)
            if (q.isNotBlank()) "在网上查找“$q”相关信息" else "在网上查找相关信息"
        }
        "fetch_url", "web_browse" -> "打开相关网页，核对里面是否有需要的信息"
        "web_content" -> "阅读网页内容，提取对用户有用的部分"
        "web_js" -> "让网页加载完整内容，方便继续读取"
        "bg_launch" -> {
            if (p("package_name").isNotBlank()) "在后台打开目标应用，避免打断当前界面" else "在后台准备应用运行环境"
        }
        "bg_stop" -> "结束后台应用环境"
        "vd_setup" -> "检查后台运行环境是否可用"
        "memory" -> when (p("action")) {
            "set" -> "记录一个以后会用到的信息"
            "get" -> "读取记忆，确认用户偏好或历史信息"
            "delete" -> "删除一条不再需要的记忆"
            "list" -> "查看已有记忆，避免重复询问"
            else -> "更新记忆信息"
        }
        "shell" -> if (p("command").isNotBlank()) "执行本地命令，检查或完成技术任务" else "执行本地命令"
        "permission" -> "检查所需权限是否已经开启"
        "quick_skill" -> "准备一个新的能力来完成这类任务"
        "meta" -> "整理当前可用能力"
        "skill_check" -> "检查是否已有合适的能力可以使用"
        "skill_market" -> "查找可安装的能力"
        "generate_image" -> {
            val prompt = p("prompt").take(40)
            if (prompt.isNotBlank()) "按需求生成图片内容" else "生成图片内容"
        }
        "create_file" -> {
            val name = p("filename")
            if (name.isNotBlank()) "生成用户需要的文件" else "生成文件"
        }
        "create_html" -> if (p("title").isNotBlank()) "生成可预览的网页结果" else "生成网页预览结果"
        "ui_builder" -> when (p("action")) {
            "create" -> "创建一个原生页面来承载这次结果"
            "analyze_change" -> "分析现有原生页面该怎么改，避免把已有功能改丢"
            "update" -> "修改已有原生页面，让结果更贴合你的要求"
            "validate" -> "检查原生页面改完后还能不能正常用"
            "open" -> "打开原生页面给你查看"
            "list" -> "查找现有原生页面，避免重复做一份"
            "get" -> "读取现有页面内容，准备继续修改"
            else -> "处理原生页面内容"
        }
        "switch_model" -> "切换到更合适的模型来处理当前任务"
        "switch_role" -> "切换到更适合当前任务的角色"
        "user_config" -> when (p("action")) {
            "set" -> "保存用户配置，让后续表现更符合偏好"
            "get" -> "读取用户配置，按偏好调整回答"
            "delete" -> "删除一项用户配置"
            "list" -> "查看用户配置，理解当前使用习惯"
            else -> "处理用户配置"
        }
        "app_manager" -> when (p("action")) {
            "create" -> "生成一个可以直接使用的 MiniAPP"
            "analyze_change" -> "分析现有 MiniAPP 该怎么改，避免把已有功能改丢"
            "update" -> "继续修改 MiniAPP，把缺的部分补齐"
            "validate" -> "检查 MiniAPP 现在能不能正常运行"
            "open" -> "打开 MiniAPP，确认你能不能直接使用"
            "delete" -> "删除不再需要的 MiniAPP"
            "list" -> "查找已有 MiniAPP，避免重复创建"
            else -> "处理 MiniAPP 内容"
        }
        else -> skillId
    }
}

internal fun friendlyObservationDescription(skillId: String?, text: String, hasImage: Boolean): String {
    artifactObservationSummary(skillId, text)?.let { return it }
    if (text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true) || text.contains("失败")) {
        return when (skillId) {
            "app_manager" -> "这次应用处理没有直接通过，正在按报错继续修正"
            "ui_builder" -> "这次页面处理没有直接通过，正在按报错继续修正"
            "navigate", "tap", "scroll", "input_text", "long_click" -> "这次手机操作没有达到预期，正在换一个更合适的动作"
            else -> "这一步没达到预期，正在根据返回结果调整处理方式"
        }
    }
    return when (skillId) {
        "web_search" -> "已经找到一批相关结果，接下来筛出真正有用的部分"
        "fetch_url", "web_browse", "web_content", "web_js" -> "网页内容已经拿到了，接下来提取和当前任务直接相关的部分"
        "see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen" ->
            if (hasImage) "已经看到当前画面了，接下来直接判断该点哪里" else "已经识别出当前界面的状态了"
        "tap", "long_click", "scroll", "input_text", "navigate", "bg_launch" -> "这一步操作已经发出，我马上看界面有没有按预期变化"
        "list_apps" -> "应用列表已经拿到了，接下来找出目标应用"
        "ui_builder" -> "页面改动已经返回，接下来看看这次改动是不是对的"
        "app_manager" -> "MiniAPP 结果已经返回，接下来看看它能不能直接运行"
        "create_file", "create_html", "generate_document" -> "文件已经生成出来了，接下来确认它能不能直接打开"
        "generate_image", "generate_icon", "generate_video" -> "生成结果已经回来了，接下来把能用的内容展示出来"
        "memory", "user_config" -> "个性化信息已经更新，后面的判断会按这个来"
        "shell", "run_python", "pip_install" -> "命令已经跑完了，接下来根据输出继续修"
        "permission" -> "权限状态已经拿到了，接下来判断是不是缺了关键权限"
        else -> if (text.isBlank()) "这一步已经有结果了" else "这一步的结果已经拿到了"
    }
}

private fun artifactObservationSummary(skillId: String?, text: String): String? {
    if (skillId !in setOf("app_manager", "ui_builder")) return null
    val payload = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return null
    val action = payload.stringOrNull("action").orEmpty()
    val savedAsDraft = payload.stringOrNull("saved_as_draft").equals("true", ignoreCase = true)
    val summary = payload.stringOrNull("summary").orEmpty()
    val runtimeIssues = payload.stringListOrEmpty("runtime_issues")
    val preflightIssues = payload.stringListOrEmpty("preflight_issues")
    val errorLogs = payload.stringListOrEmpty("error_logs")
    val warnings = payload.stringListOrEmpty("preflight_warnings")
    return when (skillId) {
        "app_manager" -> when {
            action == "create" && savedAsDraft -> "MiniAPP 已经保存下来，但首轮自检没过，正在继续修启动或运行问题"
            action == "update" && savedAsDraft -> "MiniAPP 修改已保存，但这版还没通过检查，正在继续修正"
            action == "validate" && (runtimeIssues.isNotEmpty() || preflightIssues.isNotEmpty()) -> "MiniAPP 还存在缺项或运行问题，正在按检查结果继续修"
            action == "inspect_logs" && errorLogs.isNotEmpty() -> "MiniAPP 运行日志里还有报错，正在按日志定位并修复"
            action == "open" -> "MiniAPP 已经打开；如果你还在聊天页，会先在右下角预览验证"
            summary.isNotBlank() -> summary.take(90)
            warnings.isNotEmpty() -> "MiniAPP 已生成结果，但还有一些警告需要确认"
            else -> "MiniAPP 这一步已返回结果，正在继续确认是否可用"
        }
        "ui_builder" -> when {
            action == "create" -> "原生页面已经生成，正在确认展示和功能是否正常"
            action == "update" -> "原生页面修改已完成，正在确认这次修改是否生效"
            action == "validate" && (runtimeIssues.isNotEmpty() || preflightIssues.isNotEmpty()) -> "页面还有问题没有处理完，正在按检查结果继续修"
            action == "inspect_runtime" && errorLogs.isNotEmpty() -> "页面运行时还有报错，正在继续修复"
            action == "open" -> "页面已经打开，接下来会确认内容是不是用户想要的"
            summary.isNotBlank() -> summary.take(90)
            else -> "页面处理结果已返回，正在继续确认是否可用"
        }
        else -> null
    }
}

internal fun nextStepHint(skillId: String?, text: String): String? {
    val payload = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
    return when (skillId) {
        "app_manager" -> when {
            payload?.stringOrNull("saved_as_draft").equals("true", ignoreCase = true) ->
                "继续看检查结果和运行日志，把没通过的部分修好后再打开"
            payload?.stringOrNull("action") == "open" ->
                "先看聊天右下角预览有没有正常渲染；有问题就直接查日志并修"
            payload?.stringOrNull("action") == "inspect_logs" && payload.stringListOrEmpty("error_logs").isNotEmpty() ->
                "按日志里报错的位置修一轮，再重新检查"
            payload?.stringOrNull("action") == "validate" ->
                "根据校验结果决定是继续修，还是直接打开给用户"
            else -> "继续确认这次处理后的实际可用性"
        }
        "ui_builder" -> when {
            payload?.stringOrNull("action") == "validate" ->
                "根据校验结果决定是继续修，还是直接打开给用户"
            else -> "继续确认页面是否符合用户刚才的要求"
        }
        "navigate", "tap", "scroll", "input_text", "long_click" ->
            "重新查看界面变化，确认这一步是否把任务推进到了正确位置"
        "see_screen", "screenshot", "read_screen", "bg_screenshot", "bg_read_screen" ->
            "基于当前画面直接做下一步操作，不重复只看不动"
        "web_search", "fetch_url", "web_browse", "web_content", "web_js" ->
            "从已拿到的内容里筛掉噪音，只保留对用户有用的结论"
        else -> null
    }
}

internal fun JsonObject.stringOrNull(name: String): String? =
    runCatching {
        get(name)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()

internal fun JsonObject.stringListOrEmpty(name: String): List<String> =
    runCatching {
        get(name)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                runCatching { element.asString.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
    }.getOrDefault(emptyList())

package com.mobileclaw.ui.group

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.agent.TaskType
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillAttachment

internal fun buildGroupTurnInstruction(roleName: String, triggerText: String, requireResponse: Boolean): String {
    val trigger = triggerText.trim().take(500)
    val organicChat = isOrganicGroupTrigger(trigger)
    return if (trigger.isBlank()) {
        "[系统]: 当前没有新的用户消息，也没有需要继续推进的群聊话题。\n轮到你了，$roleName。请保持安静并输出 [PASS]。不要硬开启话题，不要解释冷启动、待机、系统状态。"
    } else if (requireResponse) {
        "[系统]: 用户刚在群里问/说：$trigger\n轮到你了，$roleName。你是本轮主回应者，必须给出一条自然、有内容的群聊回复。不要输出 [PASS]，不要只发表情，也不要用“我补充一句/我也来说两句/接一下”这种尴尬开场。"
    } else if (organicChat) {
        "[系统]: 最新群聊内容：$trigger\n轮到你了，$roleName。现在是自然闲聊，不是考试答题。你可以接住上一句、抛一个新角度、轻微吐槽、开个小话题或点名别人。优先发一条有性格的短回复；只有确实重复、没话说或会打断任务时才输出 [PASS]。禁止“我补充一句/我也觉得/确实/哈哈/有道理”这种空话。"
    } else {
        "[系统]: 最新触发内容：$trigger\n轮到你了，$roleName。你不是主回应者。只有在你能提供明显不同的新信息、专业角度、被点名、或这句话明确邀请大家讨论时才回复；否则输出 [PASS]。禁止只说“我补充一句/我也觉得/确实/哈哈/有道理”。"
    }
}

internal fun isOrganicGroupTrigger(text: String): Boolean {
    val clean = text.trim()
    if (clean.isBlank()) return false
    if (TaskClassifier.classify(clean) !in listOf(TaskType.CHAT, TaskType.GENERAL)) return false
    if (clean.length in 8..220 && !isLowValueGroupReply(clean, emptyList())) return true
    return clean.contains("？") || clean.contains("?")
}

internal fun shouldInviteMultipleGroupVoices(text: String): Boolean {
    val lowered = text.trim().lowercase()
    if (lowered.isBlank()) return false
    return listOf(
        "你们", "大家", "所有人", "都说说", "一起聊", "群里", "各位",
        "怎么看", "有什么想法", "给点建议", "投票", "brainstorm", "讨论",
    ).any { lowered.contains(it) }
}

internal fun shouldContinueGroupThread(text: String): Boolean {
    val clean = text.trim()
    if (clean.isBlank()) return false
    val lowered = clean.lowercase()
    if (isLowValueGroupReply(clean, emptyList())) return false
    return listOf(
        "怎么看", "你们觉得", "大家觉得", "谁来", "有没有", "可以聊",
        "展开讲", "换个角度", "还有谁", "还有没有", "大家说说",
        "你呢", "你们呢", "要不", "不如", "我想听", "抛给", "点名",
    ).any { lowered.contains(it) } ||
        (clean.contains("？") || clean.contains("?")) &&
            listOf("你们", "大家", "谁", "怎么", "为什么", "要不要", "有没有").any { lowered.contains(it) }
}

internal fun shouldRequireGroupReaction(triggerText: String, chainDepth: Int, reactorIndex: Int): Boolean {
    if (chainDepth <= 1) return false
    if (reactorIndex > 0) return false
    return shouldContinueGroupThread(triggerText)
}

internal fun isLowValueGroupReply(text: String, attachments: List<SkillAttachment>): Boolean {
    val clean = text.trim()
    if (attachments.isNotEmpty()) return false
    if (clean.isBlank()) return true
    val normalized = clean
        .replace(Regex("[\\s，。,.!！?？~～…]+"), "")
        .lowercase()
    val generic = listOf(
        "我补充一句", "补充一下", "我也补充", "我也来说两句", "我接一下",
        "我也觉得", "我同意", "确实", "有道理", "说得对", "哈哈", "笑死",
        "可以", "不错", "挺好", "没错", "俺也一样", "先这样",
    )
    if (generic.any { normalized == it || normalized.startsWith(it) && normalized.length <= it.length + 10 }) {
        return true
    }
    val meaningfulChars = normalized.count { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }
    if (meaningfulChars <= 8) return true
    val fillerHits = listOf("补充", "接一句", "我也", "确实", "有道理").count { normalized.contains(it) }
    return fillerHits >= 2 && meaningfulChars < 28
}

internal fun fallbackGroupReply(role: Role, baseMessages: List<Message>): String {
    val latestUser = baseMessages
        .asReversed()
        .firstOrNull { it.role == "user" && !it.content.orEmpty().startsWith("[系统]:") }
        ?.content
        ?.substringAfter("]:", "")
        ?.trim()
        ?.take(120)
        .orEmpty()
    return when {
        latestUser.contains("?", ignoreCase = true) || latestUser.contains("？") ->
            "${role.name}看法：这个问题核心是「$latestUser」。我会先把目标拆清楚，再判断需要谁继续接力。"
        latestUser.isNotBlank() ->
            "${role.name}看法：我抓到的重点是「$latestUser」。可以顺着这个点继续聊，先给一个明确角度。"
        else ->
            "${role.name}在。可以从一个轻松的话题开始，或者直接抛一个问题给我。"
    }
}

internal fun stickerQueryForText(text: String): String? {
    val clean = text.trim()
    if (clean.length !in 1..90) return null
    val lowered = clean.lowercase()
    val seriousSignals = listOf(
        "步骤", "方案", "代码", "编译", "报错", "权限", "安全", "隐私", "合同", "法律",
        "医疗", "财务", "风险", "必须", "不能", "失败", "异常", "crash", "error",
    )
    if (seriousSignals.any { lowered.contains(it) }) return null
    val candidates = listOf(
        listOf("哈哈", "笑死", "笑", "绷不住", "乐", "hh", "233", "好玩", "太逗") to "哈哈",
        listOf("牛", "太强", "厉害", "666", "绝了", "顶", "nb", "强啊") to "牛",
        listOf("离谱", "逆天", "破防", "无语", "尴尬", "蚌埠住", "懵", "震惊") to "无语",
        listOf("摸鱼", "开摆", "摆烂", "不想动", "偷懒") to "摸鱼",
        listOf("谢谢", "感谢", "感恩", "辛苦", "收到") to "谢谢",
        listOf("庆祝", "恭喜", "赢", "成功", "搞定", "完成", "冲") to "庆祝",
        listOf("安慰", "抱抱", "难过", "哭", "委屈", "心疼") to "安慰",
        listOf("生气", "气", "怒", "烦", "裂开") to "生气",
        listOf("可爱", "喜欢", "贴贴", "萌", "心动") to "可爱",
        listOf("晚安", "困", "睡", "累了") to "晚安",
        listOf("吃饭", "饿", "奶茶", "咖啡") to "吃饭",
    )
    return candidates.firstOrNull { (triggers, _) -> triggers.any { lowered.contains(it) } }?.second
}

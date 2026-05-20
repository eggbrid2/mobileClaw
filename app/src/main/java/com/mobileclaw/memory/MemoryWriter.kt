package com.mobileclaw.memory

import com.mobileclaw.config.UserConfig

/**
 * Central write path for long-term memory.
 *
 * All user-facing entry points should write durable facts, preferences, rules,
 * corrections, and explicit user configuration through this class instead of
 * directly scattering key conventions around UI, bridge, or server code.
 */
class MemoryWriter(
    private val semanticMemory: SemanticMemory,
    private val userConfig: UserConfig? = null,
) {
    suspend fun syncUserConfig(key: String, value: String, description: String = "") {
        userConfig?.set(key, value, description)
        val memoryKey = profileMemoryKeyForUserConfig(key) ?: return
        if (value.isBlank()) {
            semanticMemory.delete(memoryKey)
        } else {
            semanticMemory.set(
                key = memoryKey,
                value = value,
                confidence = 1.0f,
                type = SemanticMemory.inferType(memoryKey),
                source = "user_config",
                sourceRef = key,
                pinned = true,
            )
        }
    }

    suspend fun deleteUserConfig(key: String) {
        userConfig?.delete(key)
        profileMemoryKeyForUserConfig(key)?.let { semanticMemory.delete(it) }
    }

    suspend fun recordExplicitUserText(text: String) {
        val facts = extractExplicitUserFacts(text)
        if (facts.isEmpty()) return
        facts.forEach { (key, value) ->
            semanticMemory.set(
                key = key,
                value = value,
                confidence = confidenceForKey(key),
                type = SemanticMemory.inferType(key),
                source = "user_chat",
            )
            if (key.startsWith("profile.")) {
                userConfig?.set("user.${key.removePrefix("profile.")}", value, "从聊天中识别的用户信息")
            }
        }
    }

    private fun confidenceForKey(key: String): Float = when {
        key.startsWith("rule.") -> 0.98f
        key.startsWith("correction.") -> 0.96f
        key.startsWith("preference.") -> 0.94f
        key.startsWith("profile.") -> 0.92f
        else -> 0.85f
    }

    private fun profileMemoryKeyForUserConfig(key: String): String? = when {
        key.startsWith("user.") -> "profile.${key.removePrefix("user.").replace('.', '_')}"
        key == "task.default_lang" -> "profile.preferred_language"
        else -> null
    }

    private fun extractExplicitUserFacts(text: String): Map<String, String> {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > 500) return emptyMap()
        val facts = linkedMapOf<String, String>()
        fun putClean(key: String, value: String) {
            val cleaned = value
                .trim()
                .trim('。', '.', '，', ',', '；', ';', '！', '!', '？', '?', '"', '\'', '“', '”')
                .take(120)
            if (cleaned.isNotBlank()) {
                val storageKey = if (key.startsWith("rule.") || key.startsWith("correction.") || key.startsWith("preference.user_requirement")) {
                    "$key.${kotlin.math.abs(cleaned.hashCode())}"
                } else {
                    key
                }
                facts[storageKey] = cleaned
            }
        }

        Regex("""(?:我叫|我的名字叫|我是)([^，。！？\n]{1,24})""").find(trimmed)?.let {
            val value = it.groupValues[1].trim()
            if (!value.contains("一个") && !value.contains("开发") && !value.contains("用户")) putClean("profile.name", value)
        }
        Regex("""(?:我在|我住在|我来自)([^，。！？\n]{1,40})""").find(trimmed)?.let {
            putClean("profile.location", it.groupValues[1])
        }
        Regex("""(?:我是|我的职业是|我从事)([^，。！？\n]{1,50})(?:工程师|开发|设计师|产品|学生|老师|医生|律师|运营|自由职业)?""").find(trimmed)?.let {
            val raw = it.value.removePrefix("我是").removePrefix("我的职业是").removePrefix("我从事")
            if (raw.any { ch -> ch in "工程师开发设计师产品学生老师医生律师运营自由职业" }) putClean("profile.profession", raw)
        }
        Regex("""我(?:喜欢|偏好|爱用)([^，。！？\n]{1,80})""").find(trimmed)?.let {
            putClean("profile.preferences", it.groupValues[1])
        }
        Regex("""我(?:不喜欢|讨厌|不想要)([^，。！？\n]{1,80})""").find(trimmed)?.let {
            putClean("profile.dislikes", it.groupValues[1])
        }
        Regex("""以后(?:都|请)?(?:用|以)([^，。！？\n]{1,50})(?:回复|回答|和我说话|跟我说话)""").find(trimmed)?.let {
            putClean("profile.preferred_style", it.groupValues[1])
        }
        Regex("""(?:记住|帮我记住|你要记住)[:： ]?([^。\n]{2,120})""").find(trimmed)?.let {
            putClean("profile.note", it.groupValues[1])
        }
        Regex("""(?:以后|后面|下次)?(?:不要|别|不准)([^。\n]{2,120})""").find(trimmed)?.let {
            putClean("rule.user_do_not", "不要${it.groupValues[1]}")
        }
        Regex("""(?:以后|后面|下次)?(?:必须|一定要|优先)([^。\n]{2,120})""").find(trimmed)?.let {
            putClean("rule.user_must", "必须${it.groupValues[1]}")
        }
        Regex("""(?:你|ai|AI)(?:老是|总是|经常|一直)([^。\n]{2,120})""").find(trimmed)?.let {
            putClean("correction.user_reported_behavior", it.value)
        }
        if (
            trimmed.contains("图片") &&
            listOf("网页搜索", "联网搜索", "搜索网页", "外部检索").any { trimmed.contains(it) } &&
            listOf("不要", "别", "不用", "不该", "不希望", "老是", "总是", "经常", "不停").any { trimmed.contains(it) }
        ) {
            putClean("tool.policy.image_understanding.no_web_search", "图片理解优先直接看图，除非用户明确要求联网，否则不要网页搜索")
        }
        if (
            listOf("uiBuild", "uibuild", "ui_builder", "生成页面", "创建页面").any { trimmed.contains(it, ignoreCase = true) } &&
            listOf("不要", "别", "不该", "乱", "老是", "总是", "经常").any { trimmed.contains(it) }
        ) {
            putClean("tool.policy.general.no_unrequested_ui_build", "普通聊天和上下文追问不要主动 uiBuild；只有用户明确要求创建或修改页面时才进入页面生成")
        }
        if (
            listOf("乱切人", "乱切角色", "自动切角色", "老切角色", "切人").any { trimmed.contains(it) } &&
            listOf("不要", "别", "不该", "乱", "老是", "总是", "经常").any { trimmed.contains(it) }
        ) {
            putClean("agent.behavior.keep_current_role", "不要自动乱切角色；除非用户明确点名角色，否则优先保持当前角色")
        }
        Regex("""(?:我希望|我要求|我想要)([^。\n]{2,120})""").find(trimmed)?.let {
            putClean("preference.user_requirement", it.groupValues[1])
        }
        return facts
    }
}

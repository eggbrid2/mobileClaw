package com.mobileclaw.skill.builtin

import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult

/**
 * Lets the agent read and write persistent semantic memory facts.
 * Facts survive across tasks and are injected into the system prompt automatically.
 * Keys use dot-notation: "app.wechat.package", "user.language", etc.
 */
class MemorySkill(private val memory: SemanticMemory) : Skill {

    override val meta = SkillMeta(
        id = "memory",
        name = "Semantic Memory",
        description = "Read or write persistent key-value facts about the device, user preferences, and app packages. " +
            "Stored facts are automatically available in future tasks. " +
            "action: 'get' to read one key | 'set' to store a value | 'delete' to remove | 'list' to show all stored facts | 'pin'/'unpin' | 'disable'/'enable'.",
        parameters = listOf(
            SkillParam("action", "string", "'get' | 'set' | 'delete' | 'list' | 'pin' | 'unpin' | 'disable' | 'enable'"),
            SkillParam("key", "string", "Dot-notation key, e.g. 'app.wechat.package_name' or 'user.preferred_browser'", required = false),
            SkillParam("value", "string", "Value to store (required for action=set)", required = false),
        ),
        injectionLevel = 0,
        nameZh = "语义记忆",
        descriptionZh = "读写用户的键值对形式长期记忆。",
        tags = listOf("记忆"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        return when (val action = params["action"] as? String) {
            "get" -> {
                val key = params["key"] as? String ?: return SkillResult(false, "key is required for action=get")
                val v = memory.get(key)
                if (v != null) SkillResult(true, "$key = $v")
                else SkillResult(true, "Key '$key' not found in memory.")
            }
            "set" -> {
                val key = params["key"] as? String ?: return SkillResult(false, "key is required for action=set")
                val value = params["value"] as? String ?: return SkillResult(false, "value is required for action=set")
                memory.set(key = key, value = value, source = "memory_skill")
                SkillResult(true, "Stored: $key = $value")
            }
            "delete" -> {
                val key = params["key"] as? String ?: return SkillResult(false, "key is required for action=delete")
                memory.delete(key)
                SkillResult(true, "Deleted key: $key")
            }
            "list" -> {
                val all = memory.facts()
                if (all.isEmpty()) SkillResult(true, "No facts stored yet.")
                else SkillResult(true, all.joinToString("\n") { fact ->
                    val pin = if (fact.pinned) " pinned" else ""
                    "- ${fact.key}: ${fact.value} (${fact.type}, confidence=${"%.2f".format(fact.confidence)}, used=${fact.useCount}$pin)"
                })
            }
            "pin", "unpin" -> {
                val key = params["key"] as? String ?: return SkillResult(false, "key is required for action=$action")
                memory.setPinned(key, action == "pin")
                SkillResult(true, if (action == "pin") "Pinned key: $key" else "Unpinned key: $key")
            }
            "disable", "enable" -> {
                val key = params["key"] as? String ?: return SkillResult(false, "key is required for action=$action")
                memory.setEnabled(key, action == "enable")
                SkillResult(true, if (action == "enable") "Enabled key: $key" else "Disabled key: $key")
            }
            else -> SkillResult(false, "Unknown action '$action'. Use: get, set, delete, list, pin, unpin, disable, enable")
        }
    }
}

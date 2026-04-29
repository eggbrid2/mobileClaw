package com.mobileclaw.skill.builtin

import com.mobileclaw.config.UserConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserConfigSkill(private val userConfig: UserConfig) : Skill {

    override val meta = SkillMeta(
        id = "user_config",
        name = "User Configuration",
        description = "Read or write user's personal configuration entries. " +
            "Use to store and retrieve user preferences, API keys, personal info, or any persistent setting " +
            "the user has provided. Actions: get, set, delete, list.",
        parameters = listOf(
            SkillParam("action", "string", "Action: 'get', 'set', 'delete', or 'list'"),
            SkillParam("key", "string", "Configuration key (required for get/set/delete)", required = false),
            SkillParam("value", "string", "Value to store (required for set)", required = false),
            SkillParam("description", "string", "Human-readable description of what this key stores (optional, for set)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val action = params["action"] as? String
            ?: return@withContext SkillResult(false, "action is required: get | set | delete | list")

        when (action) {
            "list" -> {
                val all = userConfig.allEntries()
                if (all.isEmpty()) {
                    SkillResult(true, "No user config entries stored.")
                } else {
                    val formatted = all.entries.joinToString("\n") { (k, e) ->
                        val desc = if (e.description.isNotBlank()) "  # ${e.description}" else ""
                        "• $k = ${e.value}$desc"
                    }
                    SkillResult(true, "User config (${all.size} entries):\n$formatted")
                }
            }
            "get" -> {
                val key = params["key"] as? String
                    ?: return@withContext SkillResult(false, "key is required for get")
                val entry = userConfig.getEntry(key)
                if (entry != null) {
                    val desc = if (entry.description.isNotBlank()) " (${entry.description})" else ""
                    SkillResult(true, "$key = ${entry.value}$desc")
                } else SkillResult(false, "Key not found: $key")
            }
            "set" -> {
                val key = params["key"] as? String
                    ?: return@withContext SkillResult(false, "key is required for set")
                val value = params["value"] as? String
                    ?: return@withContext SkillResult(false, "value is required for set")
                val description = params["description"] as? String ?: ""
                userConfig.set(key, value, description)
                SkillResult(true, "Saved: $key = $value")
            }
            "delete" -> {
                val key = params["key"] as? String
                    ?: return@withContext SkillResult(false, "key is required for delete")
                userConfig.delete(key)
                SkillResult(true, "Deleted: $key")
            }
            else -> SkillResult(false, "Unknown action: $action. Use get | set | delete | list")
        }
    }
}

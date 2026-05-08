package com.mobileclaw.skill.builtin

import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

private const val PROFILE_PREFIX = "profile."

/**
 * Dedicated tool for reading and updating user profile facts.
 * Profile facts are stored with the "profile." key prefix in SemanticMemory.
 * Not auto-injected — the AI pulls them on demand and updates as new facts emerge.
 */
class UserProfileSkill(private val memory: SemanticMemory) : Skill {

    override val meta = SkillMeta(
        id = "user_profile",
        name = "User Profile",
        description = "Read or update persistent facts about the user (preferences, occupation, interests, habits, etc.). " +
            "Call get_all to retrieve known profile facts before giving personalized advice. " +
            "Call update to record newly learned facts so they persist across conversations. " +
            "action=get_all: returns all profile facts. " +
            "action=update: stores or updates a single fact (key + value). " +
            "action=delete: removes a fact by key. " +
            "Key naming: short English snake_case, e.g. 'occupation', 'language', 'hobby', 'city'.",
        parameters = listOf(
            SkillParam("action", "string", "'get_all' | 'update' | 'delete'"),
            SkillParam("key", "string", "Fact key, e.g. 'occupation' or 'preferred_language' (required for update/delete)", required = false),
            SkillParam("value", "string", "Fact value to store (required for update)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "用户画像",
        descriptionZh = "读取或更新用户的持久化画像信息（职业、兴趣、习惯等）。",
        tags = listOf("记忆"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        return when (val action = params["action"] as? String) {
            "get_all" -> {
                val facts = memory.all().filter { it.key.startsWith(PROFILE_PREFIX) }
                if (facts.isEmpty()) {
                    SkillResult(true, "No user profile facts stored yet. Learn more about the user and call update to record facts.")
                } else {
                    val formatted = facts.entries.joinToString("\n") { (k, v) ->
                        "- ${k.removePrefix(PROFILE_PREFIX)}: $v"
                    }
                    SkillResult(true, "User profile (${facts.size} facts):\n$formatted")
                }
            }
            "update" -> {
                val key = params["key"] as? String
                    ?: return SkillResult(false, "key is required for update")
                val value = params["value"] as? String
                    ?: return SkillResult(false, "value is required for update")
                val storageKey = if (key.startsWith(PROFILE_PREFIX)) key else "$PROFILE_PREFIX$key"
                memory.set(storageKey, value)
                SkillResult(true, "Profile updated: $key = $value")
            }
            "delete" -> {
                val key = params["key"] as? String
                    ?: return SkillResult(false, "key is required for delete")
                val storageKey = if (key.startsWith(PROFILE_PREFIX)) key else "$PROFILE_PREFIX$key"
                memory.delete(storageKey)
                SkillResult(true, "Profile fact deleted: $key")
            }
            else -> SkillResult(false, "Unknown action '$action'. Use: get_all, update, delete")
        }
    }
}

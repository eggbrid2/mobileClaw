package com.mobileclaw.skill.builtin

import com.mobileclaw.config.SkillNotesStore
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillNotesSkill(private val store: SkillNotesStore) : Skill {

    override val meta = SkillMeta(
        id = "skill_notes",
        name = "Skill Notes",
        description = "Read or write user-facing notes (remarks) for specific skills. " +
            "Notes appear in the skill manager UI and help the user understand each skill. " +
            "Actions: get, set, delete, list.",
        parameters = listOf(
            SkillParam("action", "string", "Action: get | set | delete | list"),
            SkillParam("skill_id", "string", "The target skill ID (required for get/set/delete)", required = false),
            SkillParam("note", "string", "The note/remark text to store (required for set)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 2,
        nameZh = "技能备注",
        descriptionZh = "为技能添加注释和使用说明。",
        tags = listOf("技能"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val action = params["action"] as? String
            ?: return@withContext SkillResult(false, "action is required: get | set | delete | list")

        when (action) {
            "list" -> {
                val all = store.all()
                if (all.isEmpty()) return@withContext SkillResult(true, "No skill notes stored yet.")
                val text = all.entries.joinToString("\n") { (id, note) -> "• $id: $note" }
                SkillResult(true, "Skill notes (${all.size}):\n$text")
            }
            "get" -> {
                val id = params["skill_id"] as? String
                    ?: return@withContext SkillResult(false, "skill_id is required for get")
                val note = store.all()[id]
                if (note != null) SkillResult(true, "$id: $note")
                else SkillResult(false, "No note found for skill '$id'")
            }
            "set" -> {
                val id = params["skill_id"] as? String
                    ?: return@withContext SkillResult(false, "skill_id is required for set")
                val note = params["note"] as? String
                    ?: return@withContext SkillResult(false, "note is required for set")
                store.set(id, note)
                SkillResult(true, "Note saved for '$id'.")
            }
            "delete" -> {
                val id = params["skill_id"] as? String
                    ?: return@withContext SkillResult(false, "skill_id is required for delete")
                store.delete(id)
                SkillResult(true, "Note deleted for '$id'.")
            }
            else -> SkillResult(false, "Unknown action: $action. Use get | set | delete | list")
        }
    }
}

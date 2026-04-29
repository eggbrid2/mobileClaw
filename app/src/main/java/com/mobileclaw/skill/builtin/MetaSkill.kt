package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

/**
 * Meta-Skill: allows the Agent to generate and persist new Skills at runtime.
 * Generated skills start at injectionLevel=2 and require user confirmation to promote.
 * Only HTTP and Python types are allowed — Shell and Native are blocked.
 */
class MetaSkill(
    private val loader: SkillLoader,
) : Skill {

    private val gson = Gson()

    override val meta = SkillMeta(
        id = "create_skill",
        name = "Create New Skill",
        description = "Creates and saves a new reusable skill. Use when you need a capability that doesn't exist yet. " +
            "Allowed types: 'http', 'python'. Provide a complete skill definition as JSON.",
        parameters = listOf(
            SkillParam("id", "string", "Unique snake_case skill ID"),
            SkillParam("name", "string", "Human-readable skill name"),
            SkillParam("description", "string", "What this skill does (shown to LLM)"),
            SkillParam("type", "string", "'http' or 'python'"),
            SkillParam("script", "string", "Python script (required for type=python)", required = false),
            SkillParam("http_url", "string", "HTTP endpoint URL (required for type=http)", required = false),
            SkillParam("http_method", "string", "GET or POST (default GET)", required = false),
            SkillParam("parameters_json", "string", "JSON array of {name,type,description,required} objects", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val id = params["id"] as? String ?: return SkillResult(false, "id is required")
        val name = params["name"] as? String ?: return SkillResult(false, "name is required")
        val description = params["description"] as? String ?: return SkillResult(false, "description is required")
        val type = params["type"] as? String ?: return SkillResult(false, "type is required")

        if (type !in listOf("http", "python")) {
            return SkillResult(false, "Only 'http' and 'python' types are allowed. Shell and native skills cannot be generated.")
        }

        val skillParams = runCatching {
            val json = params["parameters_json"] as? String ?: "[]"
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type)
            list.map { p ->
                SkillParam(
                    name = p["name"] as String,
                    type = p["type"] as? String ?: "string",
                    description = p["description"] as? String ?: "",
                    required = p["required"] as? Boolean ?: true,
                )
            }
        }.getOrElse { emptyList() }

        val skillMeta = SkillMeta(
            id = id,
            name = name,
            description = description,
            parameters = skillParams,
            type = SkillType.valueOf(type.uppercase()),
            injectionLevel = 2,     // starts at level 2, user must promote manually
            isBuiltin = false,
        )

        val def = when (type) {
            "python" -> {
                val script = params["script"] as? String
                    ?: return SkillResult(false, "script is required for python type")
                SkillDefinition(meta = skillMeta, script = script)
            }
            "http" -> {
                val url = params["http_url"] as? String
                    ?: return SkillResult(false, "http_url is required for http type")
                val method = params["http_method"] as? String ?: "GET"
                SkillDefinition(
                    meta = skillMeta,
                    httpConfig = com.mobileclaw.skill.HttpSkillConfig(url = url, method = method),
                )
            }
            else -> return SkillResult(false, "Unsupported type: $type")
        }

        return runCatching {
            loader.persist(def)
            SkillResult(
                success = true,
                output = "Skill '$id' created and saved. It is available at injection level 2 (on-demand). " +
                    "A user must manually promote it to level 1 for automatic injection.",
            )
        }.getOrElse {
            SkillResult(success = false, output = "Failed to save skill: ${it.message}")
        }
    }
}

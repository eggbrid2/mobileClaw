package com.mobileclaw.skill

import com.google.gson.annotations.SerializedName

/** Describes a skill's identity and parameters — injected into LLM system prompt. */
data class SkillMeta(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<SkillParam> = emptyList(),
    val type: SkillType = SkillType.NATIVE,
    val injectionLevel: Int = 0,        // 0=always, 1=by task type, 2=on-demand
    val isBuiltin: Boolean = true,
    val minApiLevel: Int = 30,
    val version: String = "1.0.0",
    val nameZh: String? = null,
    val descriptionZh: String? = null,
    val tags: List<String> = emptyList(),
)

data class SkillParam(
    val name: String,
    val type: String,                   // "string" | "number" | "boolean" | "object" | "array"
    val description: String,
    val required: Boolean = true,
)

enum class SkillType {
    @SerializedName("native") NATIVE,
    @SerializedName("python") PYTHON,
    @SerializedName("http") HTTP,
    @SerializedName("shell") SHELL,
}

data class SkillResult(
    val success: Boolean,
    val output: String,                 // text returned to LLM
    val data: Any? = null,
    val imageBase64: String? = null,    // if set, injected as a vision message after the tool result
)

/** All skills implement this interface. */
interface Skill {
    val meta: SkillMeta
    suspend fun execute(params: Map<String, Any>): SkillResult
}

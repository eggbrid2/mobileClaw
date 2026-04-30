package com.mobileclaw.skill.builtin

import com.mobileclaw.config.AgentConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

class SwitchModelSkill(private val config: AgentConfig) : Skill {

    override val meta = SkillMeta(
        id = "switch_model",
        name = "Switch AI Model",
        description = "Switches the active LLM model for all subsequent steps in this session. " +
            "Use when a different model is better for the task: a vision model for image understanding, " +
            "a reasoning model for complex logic, or an image-generation model for creating images. " +
            "The model chip in the top bar will update to reflect the change.",
        parameters = listOf(
            SkillParam("model", "string", "Model ID to switch to, e.g. 'gpt-4o', 'dall-e-3', 'deepseek-reasoner', 'claude-3-5-sonnet-20241022'"),
            SkillParam("reason", "string", "Brief reason for switching (shown to user in the action log)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "切换 AI 模型",
        descriptionZh = "切换当前使用的 AI 语言模型。",
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val model = params["model"] as? String
            ?: return SkillResult(false, "model parameter is required")
        val reason = params["reason"] as? String ?: ""
        config.update(config.snapshot().copy(model = model))
        val msg = if (reason.isNotBlank()) "Switched to $model ($reason)" else "Switched to $model"
        return SkillResult(true, msg)
    }
}

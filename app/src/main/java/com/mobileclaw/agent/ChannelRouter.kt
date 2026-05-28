package com.mobileclaw.agent

import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillToolTaxonomy

enum class ChannelType {
    CHAT,
    MEMORY,
    SKILL,
    SELF_EVOLUTION,
    PLAN,
    ARTIFACT,
    PHONE,
    WEB,
    MEDIA,
    VPN,
    CODE,
}

data class ChannelDecision(
    val primary: ChannelType,
    val supporting: List<ChannelType>,
    val toolHints: List<String>,
    val userVisibleSummary: String,
)

class ChannelRouter {
    fun decide(
        taskType: TaskType,
        goal: String = "",
        hasImage: Boolean = false,
        hasFile: Boolean = false,
        roleId: String = "general",
        aiPrimary: ChannelType? = null,
        aiSupporting: List<ChannelType> = emptyList(),
        aiToolHints: List<String> = emptyList(),
        aiUserVisibleSteps: List<String> = emptyList(),
    ): ChannelDecision {
        val normalizedGoal = goal.lowercase()
        val primary = aiPrimary ?: when (taskType) {
            TaskType.PHONE_CONTROL -> ChannelType.PHONE
            TaskType.WEB_RESEARCH -> ChannelType.WEB
            TaskType.FILE_CREATE -> ChannelType.ARTIFACT
            TaskType.APP_BUILD -> ChannelType.ARTIFACT
            TaskType.IMAGE_GENERATION -> ChannelType.MEDIA
            TaskType.VPN_CONTROL -> ChannelType.VPN
            TaskType.SKILL_MANAGEMENT -> if (isSelfEvolutionGoal(normalizedGoal)) ChannelType.SELF_EVOLUTION else ChannelType.SKILL
            TaskType.CODE_EXECUTION -> ChannelType.CODE
            TaskType.CHAT, TaskType.GENERAL -> when {
                isMemoryGoal(normalizedGoal) -> ChannelType.MEMORY
                isSelfEvolutionGoal(normalizedGoal) -> ChannelType.SELF_EVOLUTION
                hasImage && !hasFile -> ChannelType.CHAT
                else -> ChannelType.CHAT
            }
        }

        val supporting = linkedSetOf<ChannelType>()
        supporting += aiSupporting
        supporting += ChannelType.MEMORY
        if (roleId.isNotBlank() && roleId != "general") supporting += ChannelType.SELF_EVOLUTION
        if (taskType == TaskType.GENERAL && (isMemoryGoal(normalizedGoal) || isSelfEvolutionGoal(normalizedGoal))) {
            supporting += ChannelType.SKILL
        }
        when (primary) {
            ChannelType.PHONE -> supporting += ChannelType.PLAN
            ChannelType.WEB -> supporting += ChannelType.PLAN
            ChannelType.ARTIFACT -> supporting += ChannelType.SKILL
            ChannelType.SKILL -> supporting += ChannelType.SELF_EVOLUTION
            ChannelType.SELF_EVOLUTION -> supporting += ChannelType.SKILL
            ChannelType.CHAT -> {
                if (taskType == TaskType.GENERAL) {
                    supporting += ChannelType.SKILL
                    supporting += ChannelType.ARTIFACT
                }
            }
            ChannelType.PLAN, ChannelType.MEDIA, ChannelType.VPN, ChannelType.CODE, ChannelType.MEMORY -> Unit
        }

        val toolHints = (aiToolHints + (listOf(primary) + supporting).flatMap { toolHintsFor(it) }).distinct()

        return ChannelDecision(
            primary = primary,
            supporting = supporting.filterNot { it == primary },
            toolHints = toolHints,
            userVisibleSummary = aiUserVisibleSteps.takeIf { it.isNotEmpty() }?.joinToString(" / ")
                ?: buildUserSummary(primary, supporting.filterNot { it == primary }),
        )
    }

    private fun toolHintsFor(channel: ChannelType): List<String> = when (channel) {
        ChannelType.PHONE -> SkillToolTaxonomy.idsFor(SkillToolCategory.PHONE, SkillToolCategory.VPN)
        ChannelType.WEB -> SkillToolTaxonomy.idsFor(SkillToolCategory.WEB, SkillToolCategory.VPN)
        ChannelType.ARTIFACT -> SkillToolTaxonomy.idsFor(SkillToolCategory.ARTIFACT, SkillToolCategory.SKILL)
        ChannelType.MEDIA -> SkillToolTaxonomy.idsFor(SkillToolCategory.MEDIA)
        ChannelType.VPN -> SkillToolTaxonomy.idsFor(SkillToolCategory.VPN)
        ChannelType.CODE -> SkillToolTaxonomy.idsFor(SkillToolCategory.CODE, SkillToolCategory.ARTIFACT)
        ChannelType.SKILL -> SkillToolTaxonomy.idsFor(SkillToolCategory.SKILL, SkillToolCategory.SELF_EVOLUTION)
        ChannelType.SELF_EVOLUTION -> SkillToolTaxonomy.idsFor(SkillToolCategory.SELF_EVOLUTION, SkillToolCategory.SKILL)
        ChannelType.MEMORY -> SkillToolTaxonomy.idsFor(SkillToolCategory.MEMORY)
        ChannelType.CHAT -> SkillToolTaxonomy.idsFor(SkillToolCategory.CHAT)
        ChannelType.PLAN -> emptyList()
    }

    private fun isMemoryGoal(text: String): Boolean =
        text.contains("记住") ||
            text.contains("记忆") ||
            text.contains("偏好") ||
            text.contains("配置") ||
            text.contains("默认") ||
            text.contains("忘掉") ||
            text.contains("删除记忆") ||
            text.contains("保存为默认") ||
            text.contains("以后都") ||
            text.contains("user_config") ||
            text.contains("user_profile")

    private fun isSelfEvolutionGoal(text: String): Boolean =
        text.contains("自我升级") ||
            text.contains("自我进化") ||
            text.contains("自我修复") ||
            text.contains("升级自己") ||
            text.contains("修复自己") ||
            text.contains("纠错") ||
            text.contains("改进自身") ||
            text.contains("改造自己") ||
            text.contains("角色") ||
            text.contains("技能") ||
            text.contains("工具")

    private fun buildUserSummary(primary: ChannelType, supporting: List<ChannelType>): String {
        val primaryLabel = when (primary) {
            ChannelType.CHAT -> "聊天"
            ChannelType.MEMORY -> "记忆"
            ChannelType.SKILL -> "技能"
            ChannelType.SELF_EVOLUTION -> "自我升级"
            ChannelType.PLAN -> "计划"
            ChannelType.ARTIFACT -> "产物"
            ChannelType.PHONE -> "手机操作"
            ChannelType.WEB -> "网页查找"
            ChannelType.MEDIA -> "媒体生成"
            ChannelType.VPN -> "VPN"
            ChannelType.CODE -> "代码执行"
        }
        val supportLabel = supporting.joinToString("、") {
            when (it) {
                ChannelType.CHAT -> "聊天"
                ChannelType.MEMORY -> "记忆"
                ChannelType.SKILL -> "技能"
                ChannelType.SELF_EVOLUTION -> "自我升级"
                ChannelType.PLAN -> "计划"
                ChannelType.ARTIFACT -> "产物"
                ChannelType.PHONE -> "手机操作"
                ChannelType.WEB -> "网页查找"
                ChannelType.MEDIA -> "媒体生成"
                ChannelType.VPN -> "VPN"
                ChannelType.CODE -> "代码执行"
            }
        }
        return if (supportLabel.isBlank()) "主通道：$primaryLabel" else "主通道：$primaryLabel；辅助通道：$supportLabel"
    }

    private fun String.anyContains(vararg needles: String): Boolean =
        needles.any { contains(it, ignoreCase = true) }
}

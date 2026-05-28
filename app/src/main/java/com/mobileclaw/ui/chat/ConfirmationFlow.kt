package com.mobileclaw.ui.chat

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType
import com.mobileclaw.skill.SkillAttachment

internal data class ExplicitRoleSwitch(
    val role: Role,
    val remainingGoal: String,
)

internal object ConfirmationFlow {
    fun accessibilityActionCard(
        goal: String,
        confirmAccessibilityTaskPrefix: String,
        openAccessibilityPrefix: String,
        cancelText: String,
        skillName: String = "",
    ): SkillAttachment.ActionCard {
        val title = if (skillName.isNotBlank()) "$skillName 需要无障碍权限" else "需要无障碍权限后才能操作手机"
        return SkillAttachment.ActionCard(
            title = title,
            body = "这个任务会操作你的手机界面。请先开启 MobileClaw 无障碍服务；开启后回到这里点“已开启并继续”，AI 会继续同一个流程。\n\n$goal",
            tone = "phone",
            actions = listOf(
                SkillAttachment.ActionCard.Action("打开无障碍", "$openAccessibilityPrefix$goal", "primary"),
                SkillAttachment.ActionCard.Action("已开启并继续", "$confirmAccessibilityTaskPrefix$goal", "secondary"),
                SkillAttachment.ActionCard.Action("取消", cancelText, "secondary"),
            ),
        )
    }

    fun taskConfirmationCard(
        goal: String,
        taskType: TaskType,
        confirmTaskPrefix: String,
        cancelText: String,
    ): SkillAttachment.ActionCard {
        val title = when (taskType) {
            TaskType.PHONE_CONTROL -> "这需要操作你的手机界面。"
            TaskType.VPN_CONTROL -> "这需要修改 VPN/代理状态。"
            else -> "这需要执行敏感操作。"
        }
        return SkillAttachment.ActionCard(
            title = title,
            body = "请确认是否继续执行完整流程。确认后，AI 会在本次任务内自行选择合适角色和工具，不再为同一流程反复弹确认。\n\n$goal",
            tone = if (taskType == TaskType.PHONE_CONTROL) "phone" else "warning",
            actions = listOf(
                SkillAttachment.ActionCard.Action("确认执行", "$confirmTaskPrefix$goal", "primary"),
                SkillAttachment.ActionCard.Action("取消", cancelText, "secondary"),
            ),
        )
    }

    fun roleSwitchConfirmationCard(
        goal: String,
        role: Role,
        confirmRolePrefix: String,
        cancelText: String,
    ): SkillAttachment.ActionCard =
        SkillAttachment.ActionCard(
            title = "切换到 ${role.name}",
            body = "切换后会改变当前 AI 的人格、模型或可用能力。请确认是否切换。",
            tone = "role",
            actions = listOf(
                SkillAttachment.ActionCard.Action("确认切换并继续", "$confirmRolePrefix${role.id}::$goal", "primary"),
                SkillAttachment.ActionCard.Action("取消", cancelText, "secondary"),
            ),
        )

    fun isAccessibilityResumeText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in setOf("已开启", "已经开了", "开了", "无障碍已开启", "无障碍开了", "enabled") ||
            normalized.contains("已开") ||
            normalized.contains("已经开启") ||
            normalized.contains("无障碍开")
    }

    fun inferExplicitRoleSwitch(goal: String, roles: List<Role>): ExplicitRoleSwitch? {
        val text = goal.lowercase()
        if (!text.anyContainsLocal("切换角色", "换角色", "切到", "切换到", "switch role")) return null
        val role = roles.distinctBy { it.id }.firstOrNull { candidate ->
            text.contains(candidate.id.lowercase()) ||
                (candidate.name.isNotBlank() && text.contains(candidate.name.lowercase()))
        } ?: return null
        return ExplicitRoleSwitch(role, extractRoleSwitchRemainingGoal(goal, role))
    }

    private fun extractRoleSwitchRemainingGoal(goal: String, role: Role): String {
        var rest = goal
        listOf(role.name, role.id).filter { it.isNotBlank() }.forEach { token ->
            rest = rest.replace(token, "", ignoreCase = true)
        }
        return rest
            .replace(Regex("""(?i)switch\s+role\s+to|switch\s+to"""), " ")
            .replace("切换角色", " ")
            .replace("换角色", " ")
            .replace("切换到", " ")
            .replace("切到", " ")
            .replace(Regex("""^[\s，。,.!！:：;；、]*(并|然后|再)?[\s，。,.!！:：;；、]*"""), "")
            .replace(Regex("""^(帮我继续|继续帮我|帮我|来|去|一下)[\s，。,.!！:：;；、]*"""), "")
            .trim()
    }

    private fun String.anyContainsLocal(vararg needles: String): Boolean = needles.any { contains(it) }
}

package com.mobileclaw.skill.builtin

import com.mobileclaw.agent.RoleManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.flow.MutableSharedFlow

class SwitchRoleSkill(
    private val roleManager: RoleManager,
    val roleRequests: MutableSharedFlow<String>,
) : Skill {

    override val meta = SkillMeta(
        id = "switch_role",
        name = "Switch Role / Persona",
        description = "Requests a user-confirmed role/persona switch. Use only when the user explicitly asks for another role, " +
            "or when the current role cannot responsibly complete the task with its authority/expertise. " +
            "The user must confirm before the active role changes. Available roles: general, coder, web_agent, phone_operator, creator, plus any custom roles.",
        parameters = listOf(
            SkillParam("role_id", "string", "Role ID to switch to. Use 'list' to see all available roles."),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 2,
        nameZh = "切换角色",
        descriptionZh = "切换当前活跃的 AI 角色或人设。",
        tags = listOf("角色"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val roleId = params["role_id"] as? String
            ?: return SkillResult(false, "role_id is required")

        if (roleId == "list") {
            val roles = roleManager.all()
            val list = roles.joinToString("\n") { r -> "• ${r.id}: ${r.name} — ${r.description}" }
            return SkillResult(true, "Available roles:\n$list")
        }

        val role = roleManager.get(roleId)
            ?: return SkillResult(false, "Role not found: $roleId. Use role_id='list' to see available roles.")

        return SkillResult(
            success = true,
            output = "Role switch requires user confirmation before execution. Target role: ${role.name} (${role.id}).",
            data = SkillAttachment.ActionCard(
                title = "切换到 ${role.name}",
                body = "切换后会改变当前 AI 的人格、模型或可用能力。请确认是否切换。",
                tone = "role",
                actions = listOf(
                    SkillAttachment.ActionCard.Action("确认切换", "确认切换角色:${role.id}", "primary"),
                    SkillAttachment.ActionCard.Action("取消", "取消", "secondary"),
                ),
            ),
        )
    }
}

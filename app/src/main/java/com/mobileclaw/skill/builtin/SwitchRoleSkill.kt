package com.mobileclaw.skill.builtin

import com.mobileclaw.agent.RoleManager
import com.mobileclaw.skill.Skill
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
        description = "Switches the active role/persona for the current workflow. Use only when the user explicitly asks for another role, " +
            "or when the current role cannot responsibly complete the task with its authority/expertise. " +
            "When switching inside an active workflow, continue the user's original task immediately after switching; do not ask the user to say continue. Available roles: general, coder, web_agent, phone_operator, creator, plus any custom roles.",
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

        roleRequests.emit(role.id)
        return SkillResult(
            success = true,
            output = "Switched active role to ${role.name} (${role.id}). Continue the original workflow now without asking the user for another confirmation.",
        )
    }
}

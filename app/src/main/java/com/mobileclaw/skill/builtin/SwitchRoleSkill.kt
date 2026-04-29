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
        description = "Switches the active agent role, changing the system persona, forced skills, and optional model. " +
            "Available roles: general (🦀), coder (👨‍💻), web_agent (🌐), phone_operator (📱), creator (🎨), plus any custom roles.",
        parameters = listOf(
            SkillParam("role_id", "string", "Role ID to switch to. Use 'list' to see all available roles."),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 2,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val roleId = params["role_id"] as? String
            ?: return SkillResult(false, "role_id is required")

        if (roleId == "list") {
            val roles = roleManager.all()
            val list = roles.joinToString("\n") { r -> "• ${r.id}: ${r.avatar} ${r.name} — ${r.description}" }
            return SkillResult(true, "Available roles:\n$list")
        }

        val role = roleManager.get(roleId)
            ?: return SkillResult(false, "Role not found: $roleId. Use role_id='list' to see available roles.")

        roleRequests.emit(roleId)
        return SkillResult(true, "Switched to role: ${role.avatar} ${role.name}. ${role.description}")
    }
}

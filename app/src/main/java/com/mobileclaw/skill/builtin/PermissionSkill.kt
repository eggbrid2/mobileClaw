package com.mobileclaw.skill.builtin

import com.mobileclaw.permission.PermissionManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult

class PermissionSkill(
    private val permissionManager: PermissionManager,
) : Skill {

    override val meta = SkillMeta(
        id = "check_permissions",
        name = "Check Permissions",
        description = "Checks Android permission and system-access status. Returns a status report with instructions for obtaining any missing permissions. Call this when a skill fails due to missing permissions, when the accessibility service appears unavailable, or when background execution seems to be blocked by the OS.",
        parameters = listOf(
            SkillParam(
                name = "feature",
                type = "string",
                description = "The feature area to diagnose: 'screen_read', 'screenshot', 'tap', 'input', 'overlay', 'floating_window', 'background', 'long_task', or 'general'.",
                required = false,
            )
        ),
        injectionLevel = 0,
        nameZh = "检查权限",
        descriptionZh = "检查并请求应用所需的 Android 权限。",
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val feature = params["feature"] as? String ?: "general"
        return SkillResult(
            success = true,
            output = permissionManager.buildStatusReport(feature),
        )
    }
}

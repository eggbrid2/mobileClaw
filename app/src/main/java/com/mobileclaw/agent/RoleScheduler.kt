package com.mobileclaw.agent

data class RoleScheduleDecision(
    val role: Role,
    val reason: String,
)

private data class RoleScore(
    val role: Role,
    val score: Int,
    val reasons: List<String>,
)

/**
 * Chooses the most appropriate role for a classified task.
 *
 * Roles shape behavior and language, while TaskToolPolicy remains the source of truth
 * for which skills are actually available.
 */
object RoleScheduler {
    fun schedule(
        taskType: TaskType,
        goal: String,
        availableRoles: List<Role>,
        currentRole: Role,
    ): RoleScheduleDecision {
        val roles = (availableRoles + Role.BUILTINS).distinctBy { it.id }
        val scored = roles.map { scoreRole(it, taskType, goal, currentRole) }
        val fallback = fallbackRole(taskType, roles, currentRole)
        val best = scored.maxWithOrNull(
            compareBy<RoleScore> { it.score }
                .thenBy { if (it.role.isBuiltin) 0 else 1 }
                .thenBy { it.role.schedulerPriority }
        )
        val scheduled = best
            ?.takeIf { it.score > 0 || taskType !in listOf(TaskType.CHAT, TaskType.GENERAL) }
            ?.role
            ?: fallback
        val reason = best
            ?.takeIf { it.role.id == scheduled.id }
            ?.let { "TaskType=$taskType, score=${it.score}, ${it.reasons.joinToString("; ").ifBlank { "fallback" }}" }
            ?: "TaskType=$taskType, fallback=${scheduled.id}"
        return RoleScheduleDecision(
            role = scheduled,
            reason = "$reason, goal=${goal.take(80)}",
        )
    }

    private fun scoreRole(role: Role, taskType: TaskType, goal: String, currentRole: Role): RoleScore {
        var score = role.schedulerPriority
        val reasons = mutableListOf<String>()
        val normalizedGoal = goal.lowercase()
        val roleText = listOf(role.id, role.name, role.description, role.systemPromptAddendum)
            .joinToString(" ")
            .lowercase()

        if (normalizedGoal.contains(role.id.lowercase()) || normalizedGoal.contains(role.name.lowercase())) {
            score += 1000
            reasons += "explicit role mention"
        }
        if (taskType in role.preferredTaskTypes) {
            score += 120
            reasons += "preferred task"
        }
        val keywordHits = role.keywords
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && normalizedGoal.contains(it) }
            .distinct()
        if (keywordHits.isNotEmpty()) {
            score += keywordHits.size * 35
            reasons += "keywords=${keywordHits.take(5).joinToString(",")}"
        }
        val inferredHits = inferNeedles(taskType)
            .filter { roleText.contains(it) || normalizedGoal.contains(it) && roleText.contains(it.take(2)) }
            .distinct()
        if (inferredHits.isNotEmpty()) {
            score += inferredHits.size.coerceAtMost(4) * 8
            reasons += "text match=${inferredHits.take(4).joinToString(",")}"
        }
        if (role.id == currentRole.id) {
            score += 3
            reasons += "current role"
        }
        if (role.isBuiltin && taskType in role.preferredTaskTypes) {
            score += 5
        }
        return RoleScore(role, score, reasons)
    }

    private fun fallbackRole(taskType: TaskType, roles: List<Role>, currentRole: Role): Role {
        val targetId = when (taskType) {
            TaskType.PHONE_CONTROL -> "phone_operator"
            TaskType.WEB_RESEARCH -> "web_agent"
            TaskType.APP_BUILD,
            TaskType.FILE_CREATE,
            TaskType.IMAGE_GENERATION -> "creator"
            TaskType.VPN_CONTROL -> "vpn_operator"
            TaskType.SKILL_MANAGEMENT -> "skill_admin"
            TaskType.CODE_EXECUTION -> "coder"
            TaskType.CHAT,
            TaskType.GENERAL -> "general"
        }
        return roles.firstOrNull { it.id == targetId }
            ?: Role.BUILTINS.firstOrNull { it.id == targetId }
            ?: currentRole
    }

    private fun inferNeedles(taskType: TaskType): List<String> = when (taskType) {
        TaskType.PHONE_CONTROL -> listOf("手机", "android", "操作", "点击", "屏幕")
        TaskType.WEB_RESEARCH -> listOf("搜索", "研究", "网页", "资料", "新闻", "research")
        TaskType.FILE_CREATE -> listOf("文件", "文档", "写作", "生成", "file")
        TaskType.APP_BUILD -> listOf("应用", "app", "html", "页面", "工具")
        TaskType.IMAGE_GENERATION -> listOf("图片", "图像", "画图", "设计", "image")
        TaskType.VPN_CONTROL -> listOf("vpn", "代理", "节点", "订阅", "网络")
        TaskType.SKILL_MANAGEMENT -> listOf("skill", "技能", "能力", "安装", "创建")
        TaskType.CODE_EXECUTION -> listOf("代码", "编程", "脚本", "shell", "python")
        TaskType.CHAT,
        TaskType.GENERAL -> listOf("聊天", "通用", "助手")
    }
}

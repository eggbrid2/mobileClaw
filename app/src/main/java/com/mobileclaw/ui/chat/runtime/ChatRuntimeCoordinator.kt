package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType
import com.mobileclaw.ui.TaskRoute

data class ChatRuntimePlanInput(
    val sessionId: String,
    val userGoal: String,
    val visibleUserText: String,
    val route: TaskRoute,
    val taskType: TaskType,
    val role: Role,
    val roleControlPlan: RoleChatControlPlan,
    val executionMode: ChatExecutionMode,
    val directPriorContext: String,
    val agentPriorContext: String,
    val executionContext: String,
    val allowedToolIds: List<String>,
    val hasImage: Boolean,
    val hasFile: Boolean,
)

data class ChatRuntimePlan(
    val sessionId: String,
    val userGoal: String,
    val visibleUserText: String,
    val route: TaskRoute,
    val taskType: TaskType,
    val role: Role,
    val roleControlPlan: RoleChatControlPlan,
    val executionMode: ChatExecutionMode,
    val directPriorContext: String,
    val agentPriorContext: String,
    val executionContext: String,
    val allowedToolIds: List<String>,
    val trace: ChatRuntimeTrace,
) {
    fun toWorkspaceSummary(maxChars: Int = 2200): String = buildString {
        appendLine("Execution mode: $executionMode")
        appendLine("Role: ${role.name} (${role.id})")
        appendLine("Task type: ${taskType.name}")
        appendLine("Route source: ${route.source}")
        appendLine("Route reason: ${route.debugReason.take(360)}")
        if (allowedToolIds.isNotEmpty()) {
            appendLine("Allowed tools: ${allowedToolIds.joinToString(", ")}")
        }
        appendLine()
        appendLine(roleControlPlan.toPromptBlock(maxChars = 900))
        appendLine()
        appendLine(trace.toPromptBlock(maxChars = 900))
    }.trim().take(maxChars)
}

class ChatRuntimeCoordinator {
    fun createPlan(input: ChatRuntimePlanInput): ChatRuntimePlan {
        val trace = ChatRuntimeTrace()
            .append(
                stage = ChatExecutionStage.PREPARE_INPUT,
                summary = "Prepared user turn",
                details = buildString {
                    append("session=${input.sessionId}; ")
                    append("hasImage=${input.hasImage}; hasFile=${input.hasFile}")
                },
            )
            .append(
                stage = ChatExecutionStage.RESOLVE_ROUTE,
                summary = "${input.route.taskType.name} via ${input.route.contextualIntent.aiPrimaryChannel ?: "default"}",
                details = input.route.debugReason,
            )
            .append(
                stage = ChatExecutionStage.RESOLVE_ROLE,
                summary = "Using role ${input.role.name}",
                details = input.roleControlPlan.roleProfile.workspaceRootPath,
            )
            .append(
                stage = ChatExecutionStage.LOAD_ROLE_PROTOCOL,
                summary = "Compiled role control plan",
                details = input.roleControlPlan.toPromptBlock(maxChars = 900),
            )
            .append(
                stage = ChatExecutionStage.BUILD_CONTEXT,
                summary = "Context policy applied",
                details = buildString {
                    append("direct=${input.directPriorContext.length}; ")
                    append("agent=${input.agentPriorContext.length}; ")
                    append("roleBudget=${input.roleControlPlan.contextPolicy.maxRoleContextChars}")
                },
            )
            .append(
                stage = ChatExecutionStage.PLAN_EXECUTION,
                summary = "Execution mode: ${input.executionMode}",
                details = input.executionContext.take(900),
            )
            .append(
                stage = ChatExecutionStage.SELECT_TOOLS,
                summary = if (input.allowedToolIds.isEmpty()) {
                    "Tool set will be selected at execution time"
                } else {
                    "Initial allowed tools: ${input.allowedToolIds.joinToString(", ")}"
                },
                details = input.roleControlPlan.toolPolicy.preferredToolIds.joinToString(", "),
            )
        return ChatRuntimePlan(
            sessionId = input.sessionId,
            userGoal = input.userGoal,
            visibleUserText = input.visibleUserText,
            route = input.route,
            taskType = input.taskType,
            role = input.role,
            roleControlPlan = input.roleControlPlan,
            executionMode = input.executionMode,
            directPriorContext = input.directPriorContext,
            agentPriorContext = input.agentPriorContext,
            executionContext = input.executionContext,
            allowedToolIds = input.allowedToolIds,
            trace = trace,
        )
    }
}

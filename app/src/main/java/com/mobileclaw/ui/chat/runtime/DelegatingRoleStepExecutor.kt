package com.mobileclaw.ui.chat.runtime

class DelegatingRoleStepExecutor(
    private val handlers: RoleStepActionHandlers = RoleStepActionHandlers(),
) : RoleStepExecutor {
    override suspend fun execute(state: RoleRunState, decision: RoleStepDecision): RoleStepResult =
        when (decision.action) {
            RoleStepAction.ANALYZE_INTENT -> handlers.analyzeIntent(state, decision)
            RoleStepAction.READ_ROLE_FILE -> handlers.readRoleFile(state, decision)
            RoleStepAction.SEARCH_MEMORY -> handlers.searchMemory(state, decision)
            RoleStepAction.READ_WORKSPACE -> handlers.readWorkspace(state, decision)
            RoleStepAction.SELECT_SKILL -> handlers.selectSkill(state, decision)
            RoleStepAction.INVOKE_TOOL -> handlers.invokeTool(state, decision)
            RoleStepAction.WRITE_MEMORY -> handlers.writeMemory(state, decision)
            RoleStepAction.COMPOSE_REPLY -> handlers.composeReply(state, decision)
            RoleStepAction.ASK_USER -> handlers.askUser(state, decision)
            RoleStepAction.FINAL_ANSWER -> handlers.finalAnswer(state, decision)
        }
}

data class RoleStepActionHandlers(
    val analyzeIntent: RoleStepHandler = defaultHandler(RoleStepAction.ANALYZE_INTENT),
    val readRoleFile: RoleStepHandler = unsupportedHandler(RoleStepAction.READ_ROLE_FILE),
    val searchMemory: RoleStepHandler = unsupportedHandler(RoleStepAction.SEARCH_MEMORY),
    val readWorkspace: RoleStepHandler = unsupportedHandler(RoleStepAction.READ_WORKSPACE),
    val selectSkill: RoleStepHandler = unsupportedHandler(RoleStepAction.SELECT_SKILL),
    val invokeTool: RoleStepHandler = unsupportedHandler(RoleStepAction.INVOKE_TOOL),
    val writeMemory: RoleStepHandler = unsupportedHandler(RoleStepAction.WRITE_MEMORY),
    val composeReply: RoleStepHandler = defaultHandler(RoleStepAction.COMPOSE_REPLY),
    val askUser: RoleStepHandler = { _, decision ->
        RoleStepResult(
            success = true,
            summary = decision.answer.ifBlank { decision.purpose },
            userSummary = decision.answer.ifBlank { decision.purpose },
            finalAnswer = decision.answer,
        )
    },
    val finalAnswer: RoleStepHandler = { _, decision ->
        RoleStepResult(
            success = true,
            summary = decision.answer.ifBlank { decision.purpose },
            userSummary = decision.answer.ifBlank { decision.purpose },
            finalAnswer = decision.answer,
        )
    },
)

typealias RoleStepHandler = suspend (RoleRunState, RoleStepDecision) -> RoleStepResult

private fun defaultHandler(action: RoleStepAction): RoleStepHandler = { _, decision ->
    RoleStepResult(
        success = true,
        summary = decision.reason.ifBlank { decision.purpose.ifBlank { action.title } },
        userSummary = decision.purpose.ifBlank { action.title },
        finalAnswer = decision.answer,
    )
}

private fun unsupportedHandler(action: RoleStepAction): RoleStepHandler = { _, decision ->
    RoleStepResult(
        success = false,
        summary = "${action.title} is not wired yet.",
        userSummary = "当前步骤还没有接入：${action.title}",
        errorMessage = "Role step action '${action.id}' is not wired yet. purpose=${decision.purpose}",
    )
}

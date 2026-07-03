package com.mobileclaw.ui.chat.runtime

import java.util.UUID

class DefaultRoleRuntimeController(
    private val packetBuilder: RoleStepPacketBuilder,
    private val decider: RoleStepDecider,
    private val executor: RoleStepExecutor,
    private val reducer: RoleRunReducer = DefaultRoleRunReducer(),
    private val maxSteps: Int = 8,
) : RoleRuntimeController {
    override suspend fun start(input: RoleRunInput): RoleRunState =
        RoleRunState(
            id = UUID.randomUUID().toString(),
            input = input,
            selectedToolIds = input.preferredToolIds,
            workingSummary = buildString {
                appendLine("User goal: ${input.userGoal.take(400)}")
                if (input.controlPlanSummary.isNotBlank()) {
                    appendLine()
                    appendLine(input.controlPlanSummary.take(900))
                }
            }.trim(),
        )

    override suspend fun next(state: RoleRunState): RoleRunState {
        if (state.status != RoleRunStatus.RUNNING) return state
        if (state.stepIndex >= maxSteps) {
            return state.copy(
                status = RoleRunStatus.FAILED,
                errorMessage = "Role run exceeded max steps: $maxSteps",
            )
        }
        val packet = packetBuilder.build(state)
        val trace = ChatRuntimeTrace().append(
            stage = ChatExecutionStage.PLAN_EXECUTION,
            summary = "Role step ${state.stepIndex + 1}: waiting for decision",
            details = packet.toPromptBlock(maxChars = 1200),
        )
        val decision = decider.decide(packet, trace)
        val result = executor.execute(state, decision)
        return reducer.reduce(state, decision, result)
    }
}

class DefaultRoleStepPacketBuilder(
    private val availableActions: List<RoleStepAction> = listOf(
        RoleStepAction.ANALYZE_INTENT,
        RoleStepAction.READ_ROLE_FILE,
        RoleStepAction.SEARCH_MEMORY,
        RoleStepAction.READ_WORKSPACE,
        RoleStepAction.SELECT_SKILL,
        RoleStepAction.INVOKE_TOOL,
        RoleStepAction.WRITE_MEMORY,
        RoleStepAction.COMPOSE_REPLY,
        RoleStepAction.ASK_USER,
        RoleStepAction.FINAL_ANSWER,
    ),
) : RoleStepPacketBuilder {
    override fun build(state: RoleRunState): RoleStepPacket {
        val recentStep = state.steps.lastOrNull()
        return RoleStepPacket(
            runId = state.id,
            roleId = state.input.role.id,
            userGoal = state.input.userGoal,
            visibleUserText = state.input.visibleUserText,
            protocolSummary = listOf(
                state.input.protocol.toPromptSummary(maxChars = 900),
                state.input.controlPlanSummary.take(700),
            ).filter { it.isNotBlank() }.joinToString("\n\n"),
            currentStateSummary = buildStateSummary(state),
            recentStepSummary = recentStep?.let {
                "${it.action.id}: ${it.userSummary.ifBlank { it.outputSummary.ifBlank { it.purpose } }}".take(700)
            }.orEmpty(),
            memoryContext = state.selectedMemory,
            workspaceContext = state.selectedWorkspaceContext,
            availableActions = availableActions,
            availableToolIds = state.selectedToolIds,
        )
    }

    private fun buildStateSummary(state: RoleRunState): String = buildString {
        appendLine("Status: ${state.status}")
        appendLine("Step index: ${state.stepIndex}")
        if (state.workingSummary.isNotBlank()) appendLine("Working summary: ${state.workingSummary.take(700)}")
        if (state.finalAnswer.isNotBlank()) appendLine("Final answer drafted: ${state.finalAnswer.take(300)}")
        if (state.errorMessage.isNotBlank()) appendLine("Error: ${state.errorMessage.take(300)}")
    }.trim()
}

class DefaultRoleRunReducer : RoleRunReducer {
    override fun reduce(
        state: RoleRunState,
        decision: RoleStepDecision,
        result: RoleStepResult,
    ): RoleRunState {
        val step = RoleStep(
            index = state.stepIndex,
            action = decision.action,
            visibility = decision.visibility,
            purpose = decision.purpose,
            userSummary = result.userSummary.ifBlank { decision.purpose },
            inputSummary = decision.query
                .ifBlank { decision.targetPath }
                .ifBlank { decision.toolId }
                .ifBlank { decision.reason },
            outputSummary = result.summary.ifBlank { result.errorMessage },
            toolId = decision.toolId,
        )
        val nextStatus = when {
            !result.success -> RoleRunStatus.FAILED
            decision.action == RoleStepAction.ASK_USER -> RoleRunStatus.WAITING_FOR_USER
            decision.shouldFinish || decision.action == RoleStepAction.FINAL_ANSWER -> RoleRunStatus.COMPLETED
            else -> RoleRunStatus.RUNNING
        }
        return state.copy(
            status = nextStatus,
            stepIndex = state.stepIndex + 1,
            steps = state.steps + step,
            workingSummary = mergeSummary(state.workingSummary, result.summary),
            selectedMemory = result.memoryDelta.ifBlank { state.selectedMemory },
            selectedWorkspaceContext = result.workspaceDelta.ifBlank { state.selectedWorkspaceContext },
            selectedToolIds = if (result.selectedToolIds.isNotEmpty()) result.selectedToolIds else state.selectedToolIds,
            finalAnswer = result.finalAnswer.ifBlank { decision.answer.ifBlank { state.finalAnswer } },
            errorMessage = result.errorMessage,
        )
    }

    private fun mergeSummary(previous: String, next: String): String =
        listOf(previous, next)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeLast(1600)
}

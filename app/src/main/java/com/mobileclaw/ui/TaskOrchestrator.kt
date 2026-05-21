package com.mobileclaw.ui

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType

data class TaskExecutionContract(
    val inputSpec: String,
    val outputSpec: String,
    val stopConditions: String,
    val recoveryPlan: String,
)

data class TaskOrchestration(
    val route: TaskRoute,
    val channelDecision: ChannelDecision,
    val contract: TaskExecutionContract,
) {
    val userVisibleSummary: String get() = channelDecision.userVisibleSummary

    fun toPromptBlock(): String = buildString {
        appendLine("## Channel Contract")
        appendLine("Primary channel: ${channelDecision.primary}")
        if (channelDecision.supporting.isNotEmpty()) {
            appendLine("Supporting channels: ${channelDecision.supporting.joinToString(", ")}")
        }
        appendLine("Input: ${contract.inputSpec}")
        appendLine("Output: ${contract.outputSpec}")
        appendLine("Stop conditions: ${contract.stopConditions}")
        appendLine("Recovery: ${contract.recoveryPlan}")
        if (channelDecision.toolHints.isNotEmpty()) {
            appendLine("Tool hints: ${channelDecision.toolHints.joinToString(", ")}")
        }
        if (route.contextualIntent.executionHint.isNotBlank()) {
            appendLine("Task focus: ${route.contextualIntent.executionHint}")
        }
    }
}

class TaskOrchestrator(
    private val channelRouter: ChannelRouter = ChannelRouter(),
) {
    fun orchestrate(
        route: TaskRoute,
        goal: String,
        hasImage: Boolean,
        hasFile: Boolean,
        role: Role,
    ): TaskOrchestration {
        val channelDecision = channelRouter.decide(
            taskType = route.taskType,
            goal = goal,
            hasImage = hasImage,
            hasFile = hasFile,
            roleId = role.id,
            aiPrimary = route.contextualIntent.aiPrimaryChannel,
            aiSupporting = route.contextualIntent.aiSupportingChannels,
            aiToolHints = route.contextualIntent.aiToolHints,
            aiUserVisibleSteps = route.contextualIntent.userVisibleSteps,
        )
        return TaskOrchestration(
            route = route,
            channelDecision = channelDecision,
            contract = buildContract(channelDecision, route.taskType),
        )
    }

    private fun buildContract(decision: ChannelDecision, taskType: TaskType): TaskExecutionContract {
        val inputSpec = when (decision.primary) {
            ChannelType.PHONE -> "Latest phone screen state, latest observation, and user goal."
            ChannelType.WEB -> "User question, any attached reference, and recent facts."
            ChannelType.ARTIFACT -> "Target artifact, user change request, and existing artifact context."
            ChannelType.MEDIA -> "Creative prompt, style hint, and any attached reference."
            ChannelType.VPN -> "Requested VPN action and current VPN state."
            ChannelType.CODE -> "Command intent, file context, and error output if any."
            ChannelType.SKILL -> "Requested capability change, current skill inventory, and role state."
            ChannelType.SELF_EVOLUTION -> "Requested self-change, current role state, and recent failures."
            ChannelType.PLAN -> "Task goal and recent context for planning."
            ChannelType.MEMORY -> "User request and existing remembered facts or preferences."
            ChannelType.CHAT -> "User message and recent conversation context."
        }
        val outputSpec = when (decision.primary) {
            ChannelType.PHONE -> "Give the user a clear result, blocker, or next observed state."
            ChannelType.WEB -> "Return a concise answer with evidence summary."
            ChannelType.ARTIFACT -> "Return where the artifact lives and what was changed."
            ChannelType.MEDIA -> "Return the generated media or its location."
            ChannelType.VPN -> "Return the VPN state and whether it matches the request."
            ChannelType.CODE -> "Return the command result, errors, or next correction."
            ChannelType.SKILL -> "Return what capability changed or what skill should be used next."
            ChannelType.SELF_EVOLUTION -> "Return the role/capability change or the reason it was not applied."
            ChannelType.PLAN -> "Return a short plan only."
            ChannelType.MEMORY -> "Return what was remembered, read, or updated."
            ChannelType.CHAT -> "Return a natural user-facing reply."
        }
        val stopConditions = when (decision.primary) {
            ChannelType.PHONE -> "Stop when the screen state matches the goal, the task is blocked, or the user changes the task."
            ChannelType.WEB -> "Stop when the needed evidence is gathered and synthesized."
            ChannelType.ARTIFACT -> "Stop when the artifact is usable and the user can continue from it."
            ChannelType.MEDIA -> "Stop when the media result is generated or the request is blocked."
            ChannelType.VPN -> "Stop when the VPN state is correct."
            ChannelType.CODE -> "Stop when the command result is clear or the issue is blocked."
            ChannelType.SKILL -> "Stop when the skill or capability change is complete."
            ChannelType.SELF_EVOLUTION -> "Stop when the self-change is applied, confirmed, or safely deferred."
            ChannelType.PLAN -> "Stop after producing a usable plan."
            ChannelType.MEMORY -> "Stop after the memory read/write action is complete."
            ChannelType.CHAT -> "Stop after the user-facing reply is complete."
        }
        val recoveryPlan = when (decision.primary) {
            ChannelType.PHONE -> "If the screen is unclear, re-read once; if that fails, fall back to screenshot; if still blocked, ask the user."
            ChannelType.WEB -> "If a page fails, try another source or use search results; if evidence remains thin, say so."
            ChannelType.ARTIFACT -> "If the current artifact is wrong, update the existing artifact before creating a new one."
            ChannelType.MEDIA -> "If the generation fails, simplify the prompt or ask for the missing style detail."
            ChannelType.VPN -> "If the change fails, report the state and the blocker."
            ChannelType.CODE -> "If the command fails, surface the error and choose the next corrective command."
            ChannelType.SKILL -> "If the capability already exists, reuse it instead of creating a duplicate."
            ChannelType.SELF_EVOLUTION -> "If the self-change is too broad, narrow it to the current role or ask for confirmation."
            ChannelType.PLAN -> "If the plan is unclear, rebuild it from the latest user goal and newest relevant context."
            ChannelType.MEMORY -> "If no memory is available, answer from current context and say what was missing."
            ChannelType.CHAT -> "If the conversation is ambiguous, ask one crisp clarification instead of guessing."
        }
        return TaskExecutionContract(
            inputSpec = inputSpec,
            outputSpec = outputSpec,
            stopConditions = stopConditions,
            recoveryPlan = recoveryPlan,
        )
    }
}

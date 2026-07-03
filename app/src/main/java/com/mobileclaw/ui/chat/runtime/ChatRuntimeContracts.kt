package com.mobileclaw.ui.chat.runtime

import com.google.gson.JsonObject
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType
import com.mobileclaw.ui.ContextualTaskIntent
import com.mobileclaw.ui.TaskRoute

enum class ChatExecutionStage(val id: String, val title: String) {
    PREPARE_INPUT("prepare_input", "Prepare input"),
    RESOLVE_ROUTE("resolve_route", "Resolve route"),
    BIND_WORKSPACE("bind_workspace", "Bind workspace"),
    BUILD_CONTEXT("build_context", "Build context"),
    RESOLVE_ROLE("resolve_role", "Resolve role"),
    LOAD_ROLE_PROTOCOL("load_role_protocol", "Load role protocol"),
    PLAN_EXECUTION("plan_execution", "Plan execution"),
    COMPOSE_PROMPT("compose_prompt", "Compose prompt"),
    SELECT_TOOLS("select_tools", "Select tools"),
    EXECUTE_MODEL("execute_model", "Execute model"),
    HANDLE_EVENTS("handle_events", "Handle events"),
    PERSIST_OUTCOME("persist_outcome", "Persist outcome"),
}

data class ChatExecutionContext(
    val sessionId: String,
    val userGoal: String,
    val effectiveGoal: String,
    val taskType: TaskType,
    val intent: ContextualTaskIntent,
    val route: TaskRoute?,
    val role: Role,
    val roleProtocol: RoleExecutionProtocol?,
    val priorContext: String,
    val workspaceId: String?,
    val allowedToolIds: Set<String>,
)

enum class RoleRunStatus {
    RUNNING,
    WAITING_FOR_USER,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class RoleStepAction(val id: String, val title: String) {
    ANALYZE_INTENT("analyze_intent", "Analyze intent"),
    READ_ROLE_FILE("read_role_file", "Read role file"),
    SEARCH_MEMORY("search_memory", "Search memory"),
    READ_WORKSPACE("read_workspace", "Read workspace"),
    SELECT_SKILL("select_skill", "Select skill"),
    INVOKE_TOOL("invoke_tool", "Invoke tool"),
    WRITE_MEMORY("write_memory", "Write memory"),
    COMPOSE_REPLY("compose_reply", "Compose reply"),
    ASK_USER("ask_user", "Ask user"),
    FINAL_ANSWER("final_answer", "Final answer"),
}

enum class RoleStepVisibility {
    SILENT,
    TRACE,
    USER_TIMELINE,
    CONFIRMATION,
}

data class RoleRunInput(
    val sessionId: String,
    val userGoal: String,
    val visibleUserText: String,
    val role: Role,
    val taskType: TaskType,
    val route: TaskRoute?,
    val protocol: RoleExecutionProtocol,
    val controlPlanSummary: String = "",
    val preferredToolIds: List<String> = emptyList(),
    val workspaceId: String?,
    val imageBase64: String? = null,
    val imageLocalPath: String = "",
)

data class RoleRunState(
    val id: String,
    val input: RoleRunInput,
    val status: RoleRunStatus = RoleRunStatus.RUNNING,
    val stepIndex: Int = 0,
    val steps: List<RoleStep> = emptyList(),
    val workingSummary: String = "",
    val selectedMemory: String = "",
    val selectedWorkspaceContext: String = "",
    val selectedToolIds: List<String> = emptyList(),
    val finalAnswer: String = "",
    val errorMessage: String = "",
)

data class RoleStep(
    val index: Int,
    val action: RoleStepAction,
    val visibility: RoleStepVisibility,
    val purpose: String,
    val userSummary: String,
    val inputSummary: String,
    val outputSummary: String,
    val toolId: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
)

data class RoleStepPacket(
    val runId: String,
    val roleId: String,
    val userGoal: String,
    val visibleUserText: String,
    val protocolSummary: String,
    val currentStateSummary: String,
    val recentStepSummary: String,
    val memoryContext: String,
    val workspaceContext: String,
    val availableActions: List<RoleStepAction>,
    val availableToolIds: List<String>,
    val budget: RoleStepBudget = RoleStepBudget(),
) {
    fun toPromptBlock(maxChars: Int = budget.maxPacketChars): String = buildString {
        appendLine("## Role Step Packet")
        appendLine("- Run id: $runId")
        appendLine("- Role id: $roleId")
        appendLine("- User goal: ${userGoal.take(800)}")
        if (visibleUserText.isNotBlank() && visibleUserText != userGoal) {
            appendLine("- Visible text: ${visibleUserText.take(400)}")
        }
        appendLine()
        appendLine("### Protocol")
        appendLine(protocolSummary.take(budget.maxProtocolChars))
        appendLine()
        appendLine("### Current State")
        appendLine(currentStateSummary.take(budget.maxStateChars))
        if (recentStepSummary.isNotBlank()) {
            appendLine()
            appendLine("### Recent Step")
            appendLine(recentStepSummary.take(budget.maxRecentStepChars))
        }
        if (memoryContext.isNotBlank()) {
            appendLine()
            appendLine("### Selected Memory")
            appendLine(memoryContext.take(budget.maxMemoryChars))
        }
        if (workspaceContext.isNotBlank()) {
            appendLine()
            appendLine("### Selected Workspace Context")
            appendLine(workspaceContext.take(budget.maxWorkspaceChars))
        }
        appendLine()
        appendLine("### Available Actions")
        availableActions.forEach { appendLine("- ${it.id}: ${it.title}") }
        if (availableToolIds.isNotEmpty()) {
            appendLine()
            appendLine("### Available Tool IDs")
            availableToolIds.forEach { appendLine("- $it") }
        }
    }.trim().take(maxChars)
}

data class RoleStepBudget(
    val maxPacketChars: Int = 6000,
    val maxProtocolChars: Int = 1200,
    val maxStateChars: Int = 900,
    val maxRecentStepChars: Int = 700,
    val maxMemoryChars: Int = 1200,
    val maxWorkspaceChars: Int = 1400,
)

data class RoleStepDecision(
    val action: RoleStepAction,
    val purpose: String,
    val reason: String,
    val visibility: RoleStepVisibility = RoleStepVisibility.TRACE,
    val query: String = "",
    val targetPath: String = "",
    val toolId: String = "",
    val params: JsonObject = JsonObject(),
    val answer: String = "",
    val shouldFinish: Boolean = false,
)

data class RoleStepResult(
    val success: Boolean,
    val summary: String,
    val userSummary: String = "",
    val content: String = "",
    val memoryDelta: String = "",
    val workspaceDelta: String = "",
    val selectedToolIds: List<String> = emptyList(),
    val finalAnswer: String = "",
    val errorMessage: String = "",
)

data class RoleExecutionProtocol(
    val roleId: String,
    val version: Int = 1,
    val inputUnderstanding: String = "",
    val contextReading: String = "",
    val memoryPolicy: String = "",
    val skillPolicy: String = "",
    val responsePolicy: String = "",
    val persistencePolicy: String = "",
    val rawMarkdown: String = "",
) {
    fun toPromptSummary(maxChars: Int = 1800): String = buildString {
        appendLine("## Role Chat Execution Protocol")
        appendLine("- Role id: $roleId")
        appendLine("- Protocol version: $version")
        appendSection("Input Understanding", inputUnderstanding)
        appendSection("Context Reading", contextReading)
        appendSection("Memory Policy", memoryPolicy)
        appendSection("Skill Policy", skillPolicy)
        appendSection("Response Policy", responsePolicy)
        appendSection("Persistence Policy", persistencePolicy)
    }.trim().take(maxChars)

    private fun StringBuilder.appendSection(title: String, content: String) {
        if (content.isBlank()) return
        appendLine()
        appendLine("### $title")
        appendLine(content.trim().take(500))
    }

    fun toMarkdown(): String = buildString {
        appendLine("# Chat Execution Protocol")
        appendLine()
        appendLine("## Runtime Contract")
        appendLine("- Role id: $roleId")
        appendLine("- Protocol version: $version")
        appendMarkdownSection("Input Understanding", inputUnderstanding)
        appendMarkdownSection("Context Reading", contextReading)
        appendMarkdownSection("Memory Policy", memoryPolicy)
        appendMarkdownSection("Skill Policy", skillPolicy)
        appendMarkdownSection("Response Policy", responsePolicy)
        appendMarkdownSection("Persistence Policy", persistencePolicy)
    }.trimEnd() + "\n"

    private fun StringBuilder.appendMarkdownSection(title: String, content: String) {
        appendLine()
        appendLine("## $title")
        appendLine(content.trim().ifBlank { "- 待定义。" })
    }
}

object RoleExecutionProtocolParser {
    fun parse(roleId: String, markdown: String): RoleExecutionProtocol {
        val sections = splitMarkdownSections(markdown)
        return RoleExecutionProtocol(
            roleId = roleId,
            version = sections["Runtime Contract"]?.let(::extractVersion) ?: 1,
            inputUnderstanding = sections["Input Understanding"].orEmpty(),
            contextReading = sections["Context Reading"].orEmpty(),
            memoryPolicy = sections["Memory Policy"].orEmpty(),
            skillPolicy = sections["Skill Policy"].orEmpty(),
            responsePolicy = sections["Response Policy"].orEmpty(),
            persistencePolicy = sections["Persistence Policy"].orEmpty(),
            rawMarkdown = markdown,
        )
    }

    private fun splitMarkdownSections(markdown: String): Map<String, String> {
        val result = linkedMapOf<String, StringBuilder>()
        var currentTitle = ""
        markdown.lineSequence().forEach { line ->
            val title = line.removePrefix("## ").takeIf { line.startsWith("## ") }?.trim()
            if (title != null) {
                currentTitle = title
                result.getOrPut(currentTitle) { StringBuilder() }
            } else if (currentTitle.isNotBlank()) {
                result.getValue(currentTitle).appendLine(line)
            }
        }
        return result.mapValues { it.value.toString().trim() }
    }

    private fun extractVersion(contract: String): Int {
        val regex = Regex("Protocol version:\\s*(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(contract)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }
}

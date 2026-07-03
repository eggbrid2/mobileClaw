package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.ContextualTaskIntent
import com.mobileclaw.ui.TaskRoute
import com.mobileclaw.ui.chat.ChatMessage

data class ChatTurnInput(
    val sessionId: String,
    val visibleUserText: String,
    val rawGoal: String,
    val imageBase64: String? = null,
    val imageLocalPath: String = "",
    val fileName: String = "",
    val fileContent: String = "",
    val fileIsText: Boolean = false,
    val showUserMessage: Boolean = true,
)

data class PreparedChatInput(
    val sessionId: String,
    val userMessage: ChatMessage,
    val goalForRouting: String,
    val effectiveGoal: String,
    val visibleGoalLabel: String,
    val imageBase64: String? = null,
    val imageLocalPath: String = "",
    val attachments: List<SkillAttachment> = emptyList(),
)

data class ChatContextBundle(
    val directPriorContext: String,
    val agentPriorContext: String,
    val schedulingContext: String,
    val workspaceContext: String,
    val artifactContext: String,
    val userMemoryContext: String,
    val recentChatContext: String,
)

data class RoleResolution(
    val currentRole: Role,
    val scheduledRole: Role,
    val reason: String,
    val memoryContextUsed: String,
)

data class RoleProtocolBundle(
    val role: Role,
    val rawWorkspacePrompt: String,
    val protocol: RoleExecutionProtocol,
    val compiledPrompt: String,
)

enum class ChatExecutionMode {
    DIRECT_CHAT,
    INFO,
    AGENT,
    CODEX_DESKTOP,
}

data class ChatExecutionDecision(
    val mode: ChatExecutionMode,
    val taskType: TaskType,
    val intent: ContextualTaskIntent,
    val route: TaskRoute,
    val executionGoal: String,
    val executionContext: String,
    val allowedToolIds: List<String>,
    val reason: String,
)

data class ChatPromptBundle(
    val systemPrompt: String,
    val messages: List<Message>,
    val roleWorkspaceContext: String,
    val priorContext: String,
)

data class ChatExecutionOutcome(
    val success: Boolean,
    val summary: String,
    val rawContent: String,
    val attachments: List<SkillAttachment> = emptyList(),
    val role: Role,
    val taskType: TaskType,
)

data class ChatRuntimeTraceEvent(
    val stage: ChatExecutionStage,
    val title: String,
    val summary: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val details: String = "",
)

data class ChatRuntimeTrace(
    val events: List<ChatRuntimeTraceEvent> = emptyList(),
) {
    fun append(stage: ChatExecutionStage, summary: String, details: String = ""): ChatRuntimeTrace =
        copy(events = events + ChatRuntimeTraceEvent(stage, stage.title, summary, details = details))

    fun toPromptBlock(maxChars: Int = 2400): String = buildString {
        appendLine("## Chat Runtime Trace")
        events.forEach { event ->
            appendLine("- ${event.stage.id}: ${event.summary}")
            if (event.details.isNotBlank()) appendLine("  ${event.details.take(300)}")
        }
    }.trim().take(maxChars)
}

interface ChatInputPreparer {
    fun prepare(input: ChatTurnInput): PreparedChatInput
}

interface ChatRouteResolver {
    fun resolve(prepared: PreparedChatInput): TaskRoute
}

interface ChatWorkspaceBinder {
    fun bind(prepared: PreparedChatInput, route: TaskRoute): String?
}

interface ChatContextBuilder {
    fun build(prepared: PreparedChatInput, route: TaskRoute): ChatContextBundle
}

interface ChatRoleResolver {
    fun resolve(prepared: PreparedChatInput, route: TaskRoute, context: ChatContextBundle): RoleResolution
}

interface ChatRoleProtocolLoader {
    fun load(role: Role): RoleProtocolBundle
}

interface ChatExecutionPlanner {
    fun plan(
        prepared: PreparedChatInput,
        route: TaskRoute,
        context: ChatContextBundle,
        role: RoleResolution,
        protocol: RoleProtocolBundle,
    ): ChatExecutionDecision
}

interface ChatPromptComposer {
    fun composeDirectChat(
        prepared: PreparedChatInput,
        decision: ChatExecutionDecision,
        context: ChatContextBundle,
        role: RoleResolution,
        protocol: RoleProtocolBundle,
    ): ChatPromptBundle
}

interface ChatToolSelector {
    fun select(decision: ChatExecutionDecision, role: RoleResolution, protocol: RoleProtocolBundle): List<String>
}

interface ChatModelExecutor {
    suspend fun execute(
        prepared: PreparedChatInput,
        decision: ChatExecutionDecision,
        prompt: ChatPromptBundle?,
        trace: ChatRuntimeTrace,
    ): ChatExecutionOutcome
}

interface ChatOutcomePersister {
    suspend fun persist(
        prepared: PreparedChatInput,
        decision: ChatExecutionDecision,
        context: ChatContextBundle,
        role: RoleResolution,
        protocol: RoleProtocolBundle,
        outcome: ChatExecutionOutcome,
        trace: ChatRuntimeTrace,
    )
}

interface RoleRuntimeController {
    suspend fun start(input: RoleRunInput): RoleRunState

    suspend fun next(state: RoleRunState): RoleRunState
}

interface RoleStepPacketBuilder {
    fun build(state: RoleRunState): RoleStepPacket
}

interface RoleStepDecider {
    suspend fun decide(packet: RoleStepPacket, trace: ChatRuntimeTrace): RoleStepDecision
}

interface RoleStepExecutor {
    suspend fun execute(state: RoleRunState, decision: RoleStepDecision): RoleStepResult
}

interface RoleRunReducer {
    fun reduce(state: RoleRunState, decision: RoleStepDecision, result: RoleStepResult): RoleRunState
}

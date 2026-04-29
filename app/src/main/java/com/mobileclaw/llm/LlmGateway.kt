package com.mobileclaw.llm

/** Unified interface for all LLM backends. Swap implementations without touching Agent logic. */
interface LlmGateway {
    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun embed(text: String): FloatArray
}

data class ChatRequest(
    val messages: List<Message>,
    val tools: List<ToolDefinition> = emptyList(),
    val stream: Boolean = true,
    val onToken: ((String) -> Unit)? = null,      // regular content tokens
    val onThinkToken: ((String) -> Unit)? = null, // reasoning_content / <think> tokens
)

data class Message(
    val role: String,           // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    val imageBase64: String? = null,  // data:image/jpeg;base64,... injected as vision content (user role only)
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null,
)

data class ChatResponse(
    val content: String?,
    val toolCall: ToolCall? = null,
    val finishReason: String = "stop",
)

data class ToolCall(
    val id: String,
    val skillId: String,
    val params: Map<String, Any>,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
)

data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList(),
)

data class ToolProperty(
    val type: String,
    val description: String,
)

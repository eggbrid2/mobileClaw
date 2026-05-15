package com.mobileclaw.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalGemmaGateway(
    private val context: Context,
    private val manager: LocalModelManager,
    private val modelIdProvider: () -> String,
) : LlmGateway {
    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        if (request.imageBase64Present()) {
            throw IllegalStateException("Local model currently supports text-only requests.")
        }
        val modelId = modelIdProvider().removePrefix("local:")
        val path = manager.modelPath(modelId)
            ?: throw IllegalStateException("Local model is not installed: $modelId")
        val prompt = request.toLocalPrompt()
        val engine = LocalGemmaRuntime.engine(context, path)
        val output = StringBuilder()
        engine.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { piece ->
                val text = piece.toString()
                output.append(text)
                request.onToken?.invoke(text)
            }
        }
        ChatResponse(content = output.toString().ifBlank { null })
    }

    override suspend fun embed(text: String): FloatArray {
        throw UnsupportedOperationException("Local model embeddings are not available.")
    }
}

class HybridLlmGateway(
    private val local: LocalGemmaGateway,
    private val cloud: OpenAiGateway,
    private val useLocal: () -> Boolean,
    private val canUseCloud: () -> Boolean,
) : LlmGateway {
    override suspend fun chat(request: ChatRequest): ChatResponse {
        if (useLocal() && request.tools.isEmpty() && !request.imageBase64Present()) {
            var localTokenEmitted = false
            val localRequest = request.copy(
                onToken = request.onToken?.let { downstream ->
                    { token: String ->
                        localTokenEmitted = true
                        downstream(token)
                    }
                }
            )
            return runCatching { local.chat(localRequest) }.getOrElse { e ->
                if (canUseCloud() && !localTokenEmitted) {
                    cloud.chat(request)
                } else {
                    ChatResponse(content = "本地模型暂不可用：${e.message}\n请先在设置里下载本地模型，或切回云端模型。")
                }
            }
        }
        return cloud.chat(request)
    }

    override suspend fun embed(text: String): FloatArray = cloud.embed(text)
}

private object LocalGemmaRuntime {
    private val mutex = Mutex()
    private var cachedPath: String = ""
    private var cachedEngine: Engine? = null

    suspend fun engine(context: Context, modelPath: String): Engine = mutex.withLock {
        cachedEngine?.takeIf { cachedPath == modelPath } ?: run {
            cachedEngine?.close()
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
            )
            Engine(config).also {
                it.initialize()
                cachedPath = modelPath
                cachedEngine = it
            }
        }
    }
}

private fun ChatRequest.imageBase64Present(): Boolean = messages.any { !it.imageBase64.isNullOrBlank() }

private fun ChatRequest.toLocalPrompt(): String = buildString {
    messages.forEach { raw ->
        val msg: com.mobileclaw.llm.Message = raw
        if (msg.toolCallId != null || msg.toolCalls != null) return@forEach
        val content = msg.content?.takeIf { it.isNotBlank() } ?: return@forEach
        when (msg.role) {
            "system" -> appendLine("System: $content")
            "assistant" -> appendLine("Assistant: $content")
            "user" -> appendLine("User: $content")
            else -> appendLine("${msg.role}: $content")
        }
    }
    append("Assistant:")
}

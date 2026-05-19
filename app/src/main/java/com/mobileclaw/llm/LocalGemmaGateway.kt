package com.mobileclaw.llm

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalApi::class)
class LocalGemmaGateway(
    private val context: Context,
    private val manager: LocalModelManager,
    private val modelIdProvider: () -> String,
) : LlmGateway {
    private val gson = Gson()

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val modelId = modelIdProvider().removePrefix("local:")
        val model = manager.modelInfo(modelId)
            ?: throw IllegalStateException("Local model is not registered: $modelId")
        if (request.imageBase64Present() && !model.supportsVision) {
            throw IllegalStateException("Selected local model does not support vision input: ${model.name}")
        }
        val visionPath = if (request.imageBase64Present()) manager.visionModelPathFor(modelId) else null
        if (request.imageBase64Present() && visionPath == null) {
            val resource = manager.visionResourceFor(modelId)
            val target = resource?.name ?: "${model.family} Web Task"
            throw IllegalStateException("本地图片理解需要先安装视觉资源包：$target。请在设置 > 本地模型中下载或导入对应的 .task 文件。")
        }
        val rawContent = if (request.imageBase64Present()) {
            val prompt = request.toLocalVisionPrompt()
            val input = request.toLocalInputData(prompt)
            val imageCount = input.count { it is InputData.Image }.coerceAtLeast(1)
            val engine = LocalGemmaRuntime.engine(context, visionPath ?: error("Vision resource is missing"), maxNumImages = imageCount)
            Log.i("LocalGemma", "vision request model=$modelId promptChars=${prompt.length} images=$imageCount")
            engine.createSession().use { session ->
                session.generateContent(input)
            }
        } else {
            val prompt = request.toLocalPrompt(includeInlineImageNotes = true)
            val path = manager.modelPath(modelId)
                ?: throw IllegalStateException("Local model is not installed: $modelId")
            val engine = LocalGemmaRuntime.engine(context, path)
            val output = StringBuilder()
            val streamCleaner = if (request.stream && request.onToken != null) LocalStreamingTextCleaner() else null
            Log.i("LocalGemma", "text request model=$modelId promptChars=${prompt.length} messages=${request.messages.size} tools=${request.tools.size} stream=${streamCleaner != null}")
            engine.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { piece ->
                    val text = runCatching { conversation.renderMessageIntoString(piece) }
                        .getOrElse { piece.toString() }
                    output.append(text)
                    streamCleaner?.append(text)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { request.onToken?.invoke(it) }
                }
            }
            streamCleaner?.finalText() ?: output.toString()
        }
        val content = rawContent.cleanLocalGeneratedText()
        Log.i("LocalGemma", "request done model=$modelId rawChars=${rawContent.length} cleanChars=${content.length} cost=${System.currentTimeMillis() - startedAt}ms")
        content.toLocalToolResponse(request.tools) ?: ChatResponse(content = content.ifBlank { null })
    }

    override suspend fun embed(text: String): FloatArray {
        throw UnsupportedOperationException("Local model embeddings are not available.")
    }
}

private fun ChatRequest.toLocalVisionPrompt(): String {
    val lastUser = messages.lastOrNull { it.role == "user" && (!it.content.isNullOrBlank() || !it.imageBase64.isNullOrBlank()) }
    val question = lastUser?.content?.takeIf { it.isNotBlank() }?.middleEllipsize(600)
        ?: "请理解这张图片，并用简体中文简洁回答用户可能想知道的内容。"
    return """
你是 MobileClaw 的本地视觉模型。请只根据用户提供的图片和问题回答。
不要输出 <turn>、<|turn|>、model、user 等模板标记。
如果图片内容不清楚，请说明无法确认，不要编造。

用户问题:
$question

回答:
    """.trimIndent()
}

class HybridLlmGateway(
    private val local: LocalGemmaGateway,
    private val cloud: OpenAiGateway,
    private val useLocal: () -> Boolean,
    private val canUseCloud: () -> Boolean,
    private val nativeOnly: () -> Boolean,
) : LlmGateway {
    override suspend fun chat(request: ChatRequest): ChatResponse {
        if (nativeOnly()) {
            return runCatching { local.chat(request) }.getOrElse { e ->
                ChatResponse(content = "当前处于 Only native 本地模式，无法调用云端模型。本地模型暂不可用：${e.message}\n请先在设置里安装并选择可运行的本地模型。")
            }
        }
        if (useLocal() && request.tools.isEmpty()) {
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

    override suspend fun embed(text: String): FloatArray =
        if (nativeOnly()) FloatArray(384) else cloud.embed(text)
}

private object LocalGemmaRuntime {
    private const val TAG = "LocalGemma"
    private val mutex = Mutex()
    private var cachedPath: String = ""
    private var cachedMaxNumImages: Int? = null
    private var cachedBackendName: String = ""
    private var cachedEngine: Engine? = null

    suspend fun engine(context: Context, modelPath: String, maxNumImages: Int? = null): Engine = mutex.withLock {
        val candidates = preferredBackends()
        cachedEngine?.takeIf {
            cachedPath == modelPath &&
                cachedMaxNumImages == maxNumImages &&
                candidates.any { candidate -> candidate.name == cachedBackendName }
        } ?: run {
            cachedEngine?.close()

            var lastError: Throwable? = null
            for (backend in candidates) {
                val start = System.currentTimeMillis()
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = backend,
                    audioBackend = backend,
                    maxNumImages = maxNumImages,
                    cacheDir = context.cacheDir.absolutePath,
                )
                val engine = Engine(config)
                runCatching {
                    engine.initialize()
                    Log.i(TAG, "LiteRT-LM initialized backend=${backend.name}, maxNumImages=$maxNumImages, cost=${System.currentTimeMillis() - start}ms")
                    cachedPath = modelPath
                    cachedMaxNumImages = maxNumImages
                    cachedBackendName = backend.name
                    cachedEngine = engine
                    return@run engine
                }.onFailure { e ->
                    lastError = e
                    runCatching { engine.close() }
                    Log.w(TAG, "LiteRT-LM backend=${backend.name} unavailable, fallback next: ${e.message}")
                }
            }
            throw IllegalStateException("LiteRT-LM engine initialization failed: ${lastError?.message}", lastError)
        }
    }

    private fun preferredBackends(): List<Backend> {
        val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        return listOf(
            Backend.GPU(),
            Backend.CPU(cpuThreads),
        )
    }
}

private fun ChatRequest.imageBase64Present(): Boolean = messages.any { !it.imageBase64.isNullOrBlank() }

private fun ChatRequest.toLocalPrompt(includeInlineImageNotes: Boolean = true): String =
    if (tools.isEmpty()) toLocalDirectChatPrompt(includeInlineImageNotes) else toLocalToolPrompt(includeInlineImageNotes)

private fun ChatRequest.toLocalDirectChatPrompt(includeInlineImageNotes: Boolean = true): String = buildString {
    val language = preferredLocalLanguage()
    if (language == "zh") {
        appendLine("你是 MobileClaw 的本地助手。")
        appendLine("请直接、自然、简洁地回答用户。")
        appendLine("不要输出 <turn>、<|turn|>、model、user、assistant 等模板标记。")
        appendLine("不要生成 JSON、HTML、代码块或 UI 组件，除非用户明确要求。")
    } else {
        appendLine("You are MobileClaw's local assistant.")
        appendLine("Answer directly, naturally, and concisely.")
        appendLine("Do not output template markers such as <turn>, <|turn|>, model, user, or assistant.")
        appendLine("Do not generate JSON, HTML, code blocks, or UI components unless explicitly requested.")
    }
    appendLine()
    messages
        .filter { it.role != "system" && it.toolCallId == null && it.toolCalls == null }
        .takeLast(6)
        .forEach { msg ->
            val content = msg.content?.takeIf { it.isNotBlank() }?.middleEllipsize(500).orEmpty()
            if (content.isBlank() && msg.imageBase64.isNullOrBlank()) return@forEach
            val withImageNote = if (includeInlineImageNotes && !msg.imageBase64.isNullOrBlank()) {
                "$content\n[Image attached]"
            } else content
            val role = if (msg.role == "assistant") {
                if (language == "zh") "助手" else "Assistant"
            } else {
                if (language == "zh") "用户" else "User"
            }
            appendLine("$role: $withImageNote")
        }
    append(if (language == "zh") "助手:" else "Assistant:")
}

private fun ChatRequest.toLocalToolPrompt(includeInlineImageNotes: Boolean = true): String = buildString {
    if (tools.isNotEmpty()) {
        appendLine("You are MobileClaw's local task model. Use tools only when necessary.")
        appendLine("If a tool is needed, return only strict JSON:")
        appendLine("""{"tool_call":{"id":"local-call","name":"tool_name","arguments":{}},"content":"short reason"}""")
        appendLine("If no tool is needed, answer normally. Available tools:")
        tools.take(8).forEach { tool ->
            appendLine("- ${tool.name}: ${tool.description}")
            if (tool.parameters.properties.isNotEmpty()) {
                appendLine("  parameters: ${tool.parameters.properties.keys.take(8).joinToString(", ")}")
            }
        }
        if (tools.size > 8) appendLine("- ... ${tools.size - 8} more tools omitted")
        appendLine()
    }
    val compactMessages = messages.compactForLocalModel()
    compactMessages.forEach { raw ->
        val msg: com.mobileclaw.llm.Message = raw
        if (msg.toolCallId != null || msg.toolCalls != null) return@forEach
        val maxChars = when (msg.role) {
            "system" -> 420
            "tool" -> 300
            else -> 520
        }
        val content = msg.content?.takeIf { it.isNotBlank() }?.middleEllipsize(maxChars).orEmpty()
        if (content.isBlank() && msg.imageBase64.isNullOrBlank()) return@forEach
        val withImageNote = if (includeInlineImageNotes && !msg.imageBase64.isNullOrBlank()) {
            "$content\n[Image attached]"
        } else content
        when (msg.role) {
            "system" -> appendLine("System: $withImageNote")
            "assistant" -> appendLine("Assistant: $withImageNote")
            "user" -> appendLine("User: $withImageNote")
            else -> appendLine("${msg.role}: $withImageNote")
        }
    }
    append("Assistant:")
}

private fun List<com.mobileclaw.llm.Message>.compactForLocalModel(): List<com.mobileclaw.llm.Message> {
    val system = firstOrNull { it.role == "system" }
    val nonSystem = filterNot { it.role == "system" }
    val recent = nonSystem.takeLast(6)
    return listOfNotNull(system) + recent
}

private fun ChatRequest.preferredLocalLanguage(): String {
    val recent = messages.takeLast(4).joinToString("\n") { it.content.orEmpty() }
    return if (recent.any { it in '\u4e00'..'\u9fff' }) "zh" else "en"
}

private fun ChatRequest.toLocalInputData(prompt: String): List<InputData> {
    val parts = mutableListOf<InputData>(InputData.Text(prompt))
    messages.mapNotNull { it.imageBase64?.decodeDataUriBytesOrNull() }
        .take(4)
        .forEach { bytes -> parts.add(InputData.Image(bytes)) }
    return parts
}

private fun String.decodeDataUriBytesOrNull(): ByteArray? = runCatching {
    val raw = substringAfter(',', this)
    Base64.decode(raw, Base64.DEFAULT)
}.getOrNull()

private fun String.middleEllipsize(maxChars: Int): String {
    if (length <= maxChars) return this
    val head = (maxChars * 0.65f).toInt().coerceAtLeast(1)
    val tail = (maxChars - head - 24).coerceAtLeast(1)
    return take(head) + "\n...[local context trimmed]...\n" + takeLast(tail)
}

private fun String.toLocalToolResponse(tools: List<ToolDefinition>): ChatResponse? {
    if (tools.isEmpty()) return null
    val jsonText = extractJsonObject() ?: return null
    return runCatching {
        val root = JsonParser.parseString(jsonText).asJsonObject
        val call = root["tool_call"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val name = call["name"]?.asString?.takeIf { raw -> tools.any { it.name == raw } } ?: return null
        val id = call["id"]?.asString?.takeIf { it.isNotBlank() } ?: "local-call"
        val argsJson = call["arguments"]?.takeIf { it.isJsonObject }?.asJsonObject
        @Suppress("UNCHECKED_CAST")
        val args = if (argsJson != null) Gson().fromJson(argsJson, Map::class.java) as Map<String, Any> else emptyMap()
        val content = root["content"]?.takeIf { !it.isJsonNull }?.asString
        ChatResponse(content = content, toolCall = ToolCall(id = id, skillId = name, params = args))
    }.getOrNull()
}

private fun String.extractJsonObject(): String? {
    val fenceStart = indexOf("```")
    if (fenceStart >= 0) {
        val bodyStartLine = indexOf('\n', startIndex = fenceStart + 3)
        val bodyStart = if (bodyStartLine >= 0) bodyStartLine + 1 else fenceStart + 3
        val fenceEnd = indexOf("```", startIndex = bodyStart)
        if (fenceEnd > bodyStart) {
            val fenced = substring(bodyStart, fenceEnd).trim()
                .removePrefix("json")
                .trim()
            if (fenced.startsWith("{") && fenced.endsWith("}")) return fenced
        }
    }
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end > start) substring(start, end + 1) else null
}

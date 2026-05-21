package com.mobileclaw.llm

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.mobileclaw.config.normalizedResponseLanguage
import com.mobileclaw.config.responseLanguageShortInstruction
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
        val requestedModelId = modelIdProvider().removePrefix("local:")
        val modelId = if (request.imageBase64Present()) {
            requestedModelId
        } else {
            manager.runnableModelIdFor(requestedModelId) ?: requestedModelId
        }
        val model = manager.modelInfo(modelId)
            ?: throw IllegalStateException("Local model is not registered: $modelId")
        if (!request.imageBase64Present() && !model.supportsChatRuntime) {
            throw IllegalStateException("Selected local resource is not a chat model: ${model.name}")
        }
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
            val prompt = if (request.tools.isEmpty()) request.toLocalVisionPrompt()
            else request.toLocalVisionToolPrompt()
            val contents = request.toLocalVisionContents(prompt)
            val imageCount = contents.contents.count { it is Content.ImageBytes }.coerceAtLeast(1)
            val engine = LocalGemmaRuntime.engine(context, visionPath ?: error("Vision resource is missing"), maxNumImages = imageCount)
            Log.i("LocalGemma", "vision request model=$modelId promptChars=${prompt.length} images=$imageCount tools=${request.tools.size}")
            engine.createConversation().use { conversation ->
                val message = conversation.sendMessage(contents)
                conversation.renderMessageIntoString(message)
            }
        } else {
            val prompt = request.toLocalPrompt(includeInlineImageNotes = true)
            val path = manager.modelPath(modelId)
                ?: throw IllegalStateException("Local model is not installed: $modelId")
            val engine = LocalGemmaRuntime.engine(context, path)
            val output = StringBuilder()
            val streamCleaner = if (request.tools.isEmpty() && request.stream && request.onToken != null) {
                LocalStreamingTextCleaner()
            } else {
                null
            }
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
    val language = preferredLocalLanguage()
    val lastTextUser = messages.lastOrNull { it.role == "user" && !it.content.isNullOrBlank() }
    val question = lastTextUser?.content?.takeIf { it.isNotBlank() }?.middleEllipsize(600)
        ?: if (language == "en") "Understand this image and answer concisely based on what the user may want to know." else "请理解这张图片，并用简体中文简洁回答用户可能想知道的内容。"
    return """
你是 MobileClaw 的本地视觉模型。请只根据用户提供的图片和问题回答。
不要输出 <turn>、<|turn|>、model、user 等模板标记。
如果图片内容不清楚，请说明无法确认，不要编造。
${responseLanguageShortInstruction(language)}

用户问题:
$question

回答:
    """.trimIndent()
}

private fun ChatRequest.toLocalVisionToolPrompt(): String {
    val language = preferredLocalLanguage()
    val lastTextUser = messages.lastOrNull { it.role == "user" && !it.content.isNullOrBlank() }
    val question = lastTextUser?.content?.takeIf { it.isNotBlank() }?.middleEllipsize(700)
        ?: "Analyze the latest screenshot/image and choose the next action."
    val recentText = messages
        .filter { it.role != "system" && it.toolCalls == null }
        .takeLast(8)
        .mapNotNull { msg ->
            val content = msg.content?.takeIf { it.isNotBlank() }?.middleEllipsize(420) ?: return@mapNotNull null
            val role = if (msg.toolCallId != null) "tool(${msg.toolCallId})" else msg.role
            "$role: $content"
        }
        .joinToString("\n")
    return buildString {
        appendLine("You are MobileClaw's local VLM phone-control model.")
        appendLine("Use the provided image/screenshot plus recent context to decide the next concrete action.")
        appendLine(responseLanguageShortInstruction(language))
        appendLine("Operate in an observe -> act -> verify loop.")
        appendLine("If the latest image shows a phone UI and the goal requires interaction, choose one concrete tool action such as tap, scroll, input_text, long_click, navigate, or phone_status.")
        appendLine("Coordinates from screenshots are image pixels. Use visible target centers from the image for x/y; the app tools map them to device coordinates.")
        appendLine("Do not call see_screen/screenshot twice in a row unless the previous observation failed or an action changed the UI.")
        appendLine("If a tool is needed, return ONLY strict JSON in this exact shape:")
        appendLine("""{"tool_call":{"id":"local-vlm-call","name":"tool_name","arguments":{}},"content":"short reason"}""")
        appendLine("If the goal is already complete, answer normally with a concise final summary and do not call tools.")
        appendLine("Do not output markdown fences, <turn>, model, user, or assistant markers.")
        appendLine()
        appendLine("Available tools:")
        tools.take(10).forEach { tool ->
            appendLine("- ${tool.name}: ${tool.description}")
            if (tool.parameters.properties.isNotEmpty()) {
                appendLine("  parameters: ${tool.parameters.properties.keys.take(10).joinToString(", ")}")
            }
        }
        if (tools.size > 10) appendLine("- ... ${tools.size - 10} more tools omitted")
        appendLine()
        if (recentText.isNotBlank()) {
            appendLine("Recent context:")
            appendLine(recentText)
            appendLine()
        }
        appendLine("Current user goal/question:")
        appendLine(question)
        appendLine()
        appendLine("Your next output:")
    }
}

class HybridLlmGateway(
    private val local: LocalGemmaGateway,
    private val cloud: OpenAiGateway,
    private val useLocal: () -> Boolean,
    private val canUseCloud: () -> Boolean,
    private val nativeOnly: () -> Boolean,
    private val language: () -> String,
) : LlmGateway {
    override suspend fun chat(request: ChatRequest): ChatResponse {
        val localizedRequest = request.withResponseLanguage(language())
        if (nativeOnly()) {
            return runCatching { local.chat(localizedRequest) }.getOrElse { e ->
                ChatResponse(content = if (normalizedResponseLanguage(language()) == "en") {
                    "Only native mode is enabled, so cloud fallback is disabled.\nLocal model call failed: ${e.message}\nPlease confirm that a runnable local model is installed and selected. For image/VLM tasks, make sure the main .litertlm model is usable and its matching vision resource is installed."
                } else {
                    "当前处于 Only native 本地模式，已禁止云端回退。\n本地模型调用失败：${e.message}\n请确认已安装并选择可运行的本地模型；如果是图片/VLM，请优先确认主 .litertlm 模型完整可用。"
                })
            }
        }
        if (useLocal() && (localizedRequest.tools.isEmpty() || localizedRequest.imageBase64Present())) {
            var localTokenEmitted = false
            val localRequest = localizedRequest.copy(
                onToken = localizedRequest.onToken?.let { downstream ->
                    { token: String ->
                        localTokenEmitted = true
                        downstream(token)
                    }
                }
            )
            return runCatching { local.chat(localRequest) }.getOrElse { e ->
                if (canUseCloud() && !localTokenEmitted) {
                    cloud.chat(localizedRequest)
                } else {
                    ChatResponse(content = if (normalizedResponseLanguage(language()) == "en") {
                        "Local model call failed: ${e.message}\nPlease confirm the local model files are complete, or switch back to a cloud model."
                    } else {
                        "本地模型调用失败：${e.message}\n请确认本地模型文件完整，或切回云端模型。"
                    })
                }
            }
        }
        return cloud.chat(localizedRequest)
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
    val systemText = messages.filter { it.role == "system" }.joinToString("\n") { it.content.orEmpty() }
    if (systemText.contains("app language is English", ignoreCase = true) ||
        systemText.contains("MUST write all user-visible assistant text in English", ignoreCase = true) ||
        systemText.contains("MUST respond in English", ignoreCase = true)
    ) return "en"
    if (systemText.contains("应用语言是中文") ||
        systemText.contains("简体中文") ||
        systemText.contains("Simplified Chinese", ignoreCase = true)
    ) return "zh"
    val recent = messages.takeLast(4).joinToString("\n") { it.content.orEmpty() }
    return if (recent.any { it in '\u4e00'..'\u9fff' }) "zh" else "en"
}

private fun ChatRequest.toLocalVisionContents(prompt: String): Contents {
    val parts = mutableListOf<Content>(Content.Text(prompt))
    messages.mapNotNull { it.imageBase64?.decodeDataUriBytesOrNull() }
        .takeLast(4)
        .forEach { bytes -> parts.add(Content.ImageBytes(bytes)) }
    return Contents.Companion.of(parts)
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
        val call = root.localToolCallObject() ?: return null
        val name = call.localToolName()?.takeIf { raw -> tools.any { it.name == raw } } ?: return null
        val id = call["id"]?.asString?.takeIf { it.isNotBlank() } ?: "local-call"
        val argsJson = call.localToolArguments()
        @Suppress("UNCHECKED_CAST")
        val args = if (argsJson != null) Gson().fromJson(argsJson, Map::class.java) as Map<String, Any> else emptyMap()
        val content = root["content"]?.takeIf { !it.isJsonNull }?.asString
        ChatResponse(content = content, toolCall = ToolCall(id = id, skillId = name, params = args))
    }.getOrNull()
}

private fun JsonObject.localToolCallObject(): JsonObject? {
    val wrapped = get("tool_call")?.takeIf { it.isJsonObject }?.asJsonObject
    if (wrapped != null) return wrapped
    if (localToolName() != null) return this
    val functionCall = get("function_call")?.takeIf { it.isJsonObject }?.asJsonObject
    if (functionCall != null) return functionCall
    return null
}

private fun JsonObject.localToolName(): String? =
    listOf("name", "tool", "tool_name", "function")
        .firstNotNullOfOrNull { key ->
            get(key)
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

private fun JsonObject.localToolArguments(): JsonObject? =
    listOf("arguments", "args", "parameters", "params")
        .firstNotNullOfOrNull { key ->
            val value = get(key) ?: return@firstNotNullOfOrNull null
            when {
                value.isJsonObject -> value.asJsonObject
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> runCatching {
                    JsonParser.parseString(value.asString).takeIf { it.isJsonObject }?.asJsonObject
                }.getOrNull()
                else -> null
            }
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

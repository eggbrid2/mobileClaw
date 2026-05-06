package com.mobileclaw.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.agent.NetworkTracer
import com.mobileclaw.config.AgentConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OpenAiGateway(private val config: AgentConfig) : LlmGateway {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val host = req.url.host
            val path = req.url.encodedPath.takeLast(40)
            val startMs = System.currentTimeMillis()
            NetworkTracer.log("🌐 → ${req.method} $host$path")
            try {
                val response = chain.proceed(req)
                val ms = System.currentTimeMillis() - startMs
                NetworkTracer.log("🌐 ← ${response.code} (${ms}ms)")
                response
            } catch (e: Exception) {
                NetworkTracer.log("🌐 ✗ ${e.message?.take(60)}")
                throw e
            }
        })
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val body = buildRequestBody(request)
        val snapshot = config.snapshot()
        val httpRequest = Request.Builder()
            .url("${snapshot.endpoint.trimEnd('/')}/v1/chat/completions")
            .header("Authorization", "Bearer ${snapshot.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return if (request.stream && (request.onToken != null || request.onThinkToken != null)) {
            chatStreaming(httpRequest, request)
        } else {
            chatBlocking(httpRequest)
        }
    }

    override suspend fun embed(text: String): FloatArray {
        val snapshot = config.snapshot()
        val bodyJson = JsonObject().apply {
            addProperty("model", snapshot.embeddingModel)
            addProperty("input", text)
        }
        val httpRequest = Request.Builder()
            .url("${snapshot.endpoint.trimEnd('/')}/v1/embeddings")
            .header("Authorization", "Bearer ${snapshot.apiKey}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine { cont ->
            client.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val json = JsonParser.parseString(response.body!!.string()).asJsonObject
                        val arr = json["data"].asJsonArray[0].asJsonObject["embedding"].asJsonArray
                        cont.resume(FloatArray(arr.size()) { arr[it].asFloat })
                    } catch (e: Exception) { cont.resumeWithException(e) }
                }
            })
        }
    }

    private fun buildRequestBody(request: ChatRequest): JsonObject {
        val snapshot = config.snapshot()
        // New OpenAI Responses API models (gpt-4.1+, gpt-5.x, o3, o4-mini) use
        // {type:"input_image", image_url:"data:..."} — a flat string, not a nested object.
        // Older models use {type:"image_url", image_url:{url:"...", detail:"auto"}}.
        val useNewImageFormat = snapshot.model.matches(
            Regex("gpt-4\\.1.*|gpt-4\\.5.*|gpt-5.*|o3.*|o4.*", RegexOption.IGNORE_CASE)
        )
        val messages = JsonArray()
        request.messages.forEach { msg ->
            val obj = JsonObject()
            obj.addProperty("role", msg.role)
            when {
                msg.toolCalls != null -> {
                    // Assistant tool-call message: content must be null per OpenAI spec
                    obj.add("content", JsonNull.INSTANCE)
                    val arr = JsonArray()
                    msg.toolCalls.forEach { tc ->
                        arr.add(JsonObject().apply {
                            addProperty("id", tc.id)
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", tc.skillId)
                                addProperty("arguments", gson.toJson(tc.params))
                            })
                        })
                    }
                    obj.add("tool_calls", arr)
                }
                msg.toolCallId != null -> {
                    // Tool result: text-only (images cannot go in tool role)
                    obj.addProperty("content", msg.content ?: "")
                    obj.addProperty("tool_call_id", msg.toolCallId)
                }
                msg.imageBase64 != null -> {
                    // Vision message: multipart content array
                    val contentArr = JsonArray()
                    if (!msg.content.isNullOrBlank()) {
                        contentArr.add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", msg.content)
                        })
                    }
                    contentArr.add(JsonObject().apply {
                        if (useNewImageFormat) {
                            // New Responses API format: flat string value
                            addProperty("type", "input_image")
                            addProperty("image_url", msg.imageBase64)
                        } else {
                            // Legacy Chat Completions format: nested object
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", msg.imageBase64)
                            })
                        }
                    })
                    obj.add("content", contentArr)
                }
                else -> obj.addProperty("content", msg.content ?: "")
            }
            messages.add(obj)
        }
        return JsonObject().apply {
            addProperty("model", snapshot.model)
            add("messages", messages)
            addProperty("stream", request.stream)
            if (request.tools.isNotEmpty()) {
                add("tools", gson.toJsonTree(request.tools.map { tool ->
                    mapOf(
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "parameters" to mapOf(
                                "type" to "object",
                                "properties" to tool.parameters.properties.mapValues { (_, p) ->
                                    if (p.type == "array") mapOf("type" to p.type, "description" to p.description, "items" to emptyMap<String, Any>())
                                    else mapOf("type" to p.type, "description" to p.description)
                                },
                                "required" to tool.parameters.required,
                            )
                        )
                    )
                }))
                addProperty("tool_choice", "auto")
            }
        }
    }

    private suspend fun chatStreaming(
        request: Request,
        chatRequest: ChatRequest,
    ): ChatResponse = suspendCancellableCoroutine { cont ->
        val onToken = chatRequest.onToken
        val onThinkToken = chatRequest.onThinkToken

        val contentBuilder = StringBuilder()
        val argsBuilder = StringBuilder()
        var toolCallId = ""
        var toolCallName = ""

        // State machine for <think>...</think> tag stripping in the content stream.
        var inThinkTag = false

        fun routeContentToken(raw: String) {
            var s = raw
            while (s.isNotEmpty()) {
                if (inThinkTag) {
                    val closeIdx = s.indexOf("</think>")
                    if (closeIdx >= 0) {
                        onThinkToken?.invoke(s.substring(0, closeIdx))
                        inThinkTag = false
                        s = s.substring(closeIdx + 8) // skip </think>
                    } else {
                        onThinkToken?.invoke(s)
                        return
                    }
                } else {
                    val openIdx = s.indexOf("<think>")
                    if (openIdx >= 0) {
                        // content before <think> is regular
                        val before = s.substring(0, openIdx)
                        if (before.isNotEmpty()) {
                            contentBuilder.append(before)
                            onToken?.invoke(before)
                        }
                        inThinkTag = true
                        s = s.substring(openIdx + 7) // skip <think>
                    } else {
                        contentBuilder.append(s)
                        onToken?.invoke(s)
                        return
                    }
                }
            }
        }

        val factory = EventSources.createFactory(client)
        val source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    val toolCall = if (toolCallName.isNotBlank()) {
                        val params = runCatching {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(argsBuilder.toString(), Map::class.java) as Map<String, Any>
                        }.getOrDefault(emptyMap())
                        ToolCall(id = toolCallId, skillId = toolCallName, params = params)
                    } else null
                    cont.resume(ChatResponse(content = contentBuilder.toString().ifBlank { null }, toolCall = toolCall))
                    return
                }
                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    json["error"]?.takeIf { !it.isJsonNull }?.let { err ->
                        val msg = runCatching { err.asJsonObject["message"]?.asString }.getOrNull() ?: err.toString()
                        cont.resumeWithException(RuntimeException("API error: $msg"))
                        return
                    }
                    val delta = json["choices"]?.asJsonArray?.get(0)?.asJsonObject?.get("delta")?.asJsonObject ?: return
                    // reasoning_content: DeepSeek-R1 style separate thinking stream
                    delta["reasoning_content"]?.let { rc ->
                        if (!rc.isJsonNull) rc.asString?.let { if (it.isNotBlank()) onThinkToken?.invoke(it) }
                    }
                    // regular content (with <think> tag stripping)
                    delta["content"]?.let { cv ->
                        if (!cv.isJsonNull) cv.asString?.let { routeContentToken(it) }
                    }
                    delta["tool_calls"]?.asJsonArray?.get(0)?.asJsonObject?.let { tc ->
                        tc["id"]?.asString?.let { if (it.isNotBlank()) toolCallId = it }
                        tc["function"]?.asJsonObject?.let { fn ->
                            fn["name"]?.asString?.let { if (it.isNotBlank()) toolCallName = it }
                            fn["arguments"]?.asString?.let { argsBuilder.append(it) }
                        }
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(es: EventSource, t: Throwable?, r: okhttp3.Response?) {
                val body = runCatching { r?.body?.string()?.take(500) }.getOrNull()
                val msg = when {
                    body != null -> "API error ${r?.code}: $body"
                    t != null -> t.message ?: "Connection failed"
                    else -> "SSE connection failed"
                }
                cont.resumeWithException(RuntimeException(msg))
            }
        })
        cont.invokeOnCancellation { source.cancel() }
    }

    suspend fun fetchModels(): List<String> {
        val snapshot = config.snapshot()
        if (snapshot.endpoint.isBlank() || snapshot.apiKey.isBlank()) return emptyList()
        val request = Request.Builder()
            .url("${snapshot.endpoint.trimEnd('/')}/v1/models")
            .header("Authorization", "Bearer ${snapshot.apiKey}")
            .get()
            .build()
        return suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = cont.resume(emptyList())
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val json = JsonParser.parseString(response.body!!.string()).asJsonObject
                        val ids = json["data"].asJsonArray
                            .mapNotNull { it.asJsonObject["id"]?.asString }
                            .filter { it.isNotBlank() }
                            .sorted()
                        cont.resume(ids)
                    } catch (_: Exception) { cont.resume(emptyList()) }
                }
            })
        }
    }

    private suspend fun chatBlocking(request: Request): ChatResponse =
        suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val bodyStr = response.body!!.string()
                        if (!response.isSuccessful) {
                            cont.resumeWithException(RuntimeException("API error ${response.code}: ${bodyStr.take(500)}"))
                            return
                        }
                        val json = JsonParser.parseString(bodyStr).asJsonObject
                        val choice = json["choices"].asJsonArray[0].asJsonObject
                        val msg = choice["message"].asJsonObject
                        val content = msg["content"]?.asString
                        val toolCall = msg["tool_calls"]?.asJsonArray?.get(0)?.asJsonObject?.let { tc ->
                            val fn = tc["function"].asJsonObject
                            val params = gson.fromJson(fn["arguments"].asString, Map::class.java)
                            @Suppress("UNCHECKED_CAST")
                            ToolCall(tc["id"].asString, fn["name"].asString, params as Map<String, Any>)
                        }
                        cont.resume(ChatResponse(content = content, toolCall = toolCall))
                    } catch (e: Exception) { cont.resumeWithException(e) }
                }
            })
        }
}

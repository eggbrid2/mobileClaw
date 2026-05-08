package com.mobileclaw.llm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.agent.NetworkTracer
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.vpn.AppHttpProxy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class OpenAiGateway(private val config: AgentConfig) : LlmGateway {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
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
            .header("Accept", "application/json, text/event-stream")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var lastError: Exception? = null
        for (attempt in 0..2) {
            if (attempt > 0) delay(3000L)
            try {
                return if (request.stream && (request.onToken != null || request.onThinkToken != null)) {
                    chatStreaming(httpRequest, request)
                } else {
                    chatBlocking(httpRequest)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if ("502" in msg || "503" in msg || "bad gateway" in msg.lowercase()) {
                    lastError = e
                } else throw e
            }
        }
        throw lastError!!
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
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
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
                    val safeImage = compressImageForApi(msg.imageBase64)
                    val contentArr = JsonArray()
                    if (!msg.content.isNullOrBlank()) {
                        contentArr.add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", msg.content)
                        })
                    }
                    contentArr.add(JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply {
                            addProperty("url", safeImage)
                            addProperty("detail", "auto")
                        })
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
                val body = runCatching { r?.body?.string() }.getOrNull()
                val msg = when {
                    body != null -> "API error ${r?.code}: ${extractApiMessage(body)}"
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
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
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

    /** Scales to ≤1920px long-edge / JPEG-85 for high fidelity while staying within typical proxy limits. */
    private fun compressImageForApi(dataUri: String): String {
        return try {
            val commaIdx = dataUri.indexOf(',')
            if (commaIdx < 0) return dataUri
            val b64 = dataUri.substring(commaIdx + 1)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return dataUri

            val maxPx = 1920
            val scale = minOf(1f, maxPx.toFloat() / maxOf(original.width, original.height))
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true,
                )
            } else original

            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (bmp !== original) bmp.recycle()
            original.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            dataUri
        }
    }

    // Extract human-readable message from {"error":{"message":"..."}} or fall back to raw body.
    private fun extractApiMessage(body: String): String =
        runCatching {
            JsonParser.parseString(body).asJsonObject["error"]?.asJsonObject?.get("message")?.asString
        }.getOrNull() ?: body.take(200)

    private suspend fun chatBlocking(request: Request): ChatResponse =
        suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val bodyStr = response.body!!.string()
                        if (!response.isSuccessful) {
                            cont.resumeWithException(RuntimeException("API error ${response.code}: ${extractApiMessage(bodyStr)}"))
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

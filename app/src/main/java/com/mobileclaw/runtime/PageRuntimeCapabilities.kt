package com.mobileclaw.runtime

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared runtime surface for generated MiniAPPs and AI Native pages.
 *
 * This intentionally hides gateway credentials. Pages request AI through the app's
 * default LLM gateway and receive only the model output/result metadata.
 */
class PageRuntimeCapabilities(
    context: Context,
    private val llmGatewayProvider: () -> LlmGateway = {
        (context.applicationContext as ClawApplication).createLlmGateway()
    },
) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun fetchBlocking(url: String, method: String, headers: Map<String, String>, body: String): Map<String, Any> =
        runBlocking { fetch(url, method, headers, body) }

    suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String): Map<String, Any> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val upperMethod = method.uppercase()
                val reqBody = if (upperMethod in setOf("POST", "PUT", "PATCH") && body.isNotEmpty()) {
                    val contentType = (headers["Content-Type"] ?: "application/json").toMediaType()
                    body.toRequestBody(contentType)
                } else null
                reqBuilder.method(upperMethod, reqBody)
                http.newCall(reqBuilder.build()).execute().use { response ->
                    val responseHeaders = mutableMapOf<String, String>()
                    response.headers.forEach { (k, v) -> responseHeaders[k] = v }
                    mapOf(
                        "status" to response.code,
                        "ok" to response.isSuccessful,
                        "body" to (response.body?.string() ?: ""),
                        "headers" to responseHeaders,
                    )
                }
            }.getOrElse { e ->
                mapOf("ok" to false, "status" to 0, "error" to (e.message ?: "Network error"))
            }
        }

    fun chatBlocking(inputJson: String): Map<String, Any> = runBlocking { chat(inputJson) }

    suspend fun chat(inputJson: String): Map<String, Any> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = parseChatRequest(inputJson)
                val response = llmGatewayProvider().chat(request)
                mapOf(
                    "ok" to true,
                    "text" to (response.content ?: ""),
                    "content" to (response.content ?: ""),
                    "finishReason" to response.finishReason,
                    "toolCall" to (response.toolCall?.let { gson.toJson(it) } ?: ""),
                )
            }.getOrElse { e ->
                mapOf("ok" to false, "error" to (e.message ?: "AI request failed"))
            }
        }

    private fun parseChatRequest(inputJson: String): ChatRequest {
        val root = runCatching {
            JsonParser.parseString(inputJson.ifBlank { "{}" }).asJsonObject
        }.getOrElse { JsonObject() }

        val messages = mutableListOf<Message>()
        root["system"]?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            messages += Message(role = "system", content = it)
        }
        root["messages"]?.asJsonArrayOrNull()?.let { arr ->
            messages += parseMessages(arr)
        }
        root["prompt"]?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            messages += Message(role = "user", content = it)
        }
        if (messages.none { it.role == "user" }) {
            root["message"]?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
                messages += Message(role = "user", content = it)
            }
        }
        require(messages.isNotEmpty()) { "prompt or messages required" }
        return ChatRequest(messages = messages, stream = false)
    }

    private fun parseMessages(arr: JsonArray): List<Message> =
        (0 until arr.size()).mapNotNull { index ->
            val obj = runCatching { arr[index].asJsonObject }.getOrNull() ?: return@mapNotNull null
            val role = obj["role"]?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: "user"
            val content = obj["content"]?.asStringOrNull()
            val image = obj["imageBase64"]?.asStringOrNull()
            if (content.isNullOrBlank() && image.isNullOrBlank()) null else Message(role = role, content = content, imageBase64 = image)
        }

    private fun com.google.gson.JsonElement.asStringOrNull(): String? =
        runCatching { if (isJsonNull) null else asString }.getOrNull()

    private fun com.google.gson.JsonElement.asJsonArrayOrNull(): JsonArray? =
        runCatching { asJsonArray }.getOrNull()
}

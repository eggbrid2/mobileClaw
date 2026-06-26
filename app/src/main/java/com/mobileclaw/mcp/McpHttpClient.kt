package com.mobileclaw.mcp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MCP_PROTOCOL_VERSION = "2025-06-18"

data class McpSession(
    val endpoint: String,
    val protocolVersion: String,
    val serverInfo: JsonObject?,
    val capabilities: JsonObject?,
    val sessionId: String?,
)

data class McpTool(
    val name: String,
    val title: String?,
    val description: String?,
    val inputSchema: JsonObject?,
)

data class McpToolList(
    val tools: List<McpTool>,
    val nextCursor: String?,
)

data class McpToolCallResult(
    val content: List<JsonObject>,
    val structuredContent: JsonElement?,
    val isError: Boolean,
    val raw: JsonObject,
)

data class McpEndpointConfig(
    val endpoint: String,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun parse(input: String): McpEndpointConfig? {
            val trimmed = input.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return McpEndpointConfig(trimmed)
            val root = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return null
            root.directEndpointConfig()?.let { return it }
            val servers = root["mcpServers"]?.asJsonObjectOrNull()
                ?: root["servers"]?.asJsonObjectOrNull()
                ?: return null
            servers.entrySet().forEach { (_, value) ->
                val obj = value.asJsonObjectOrNull() ?: return@forEach
                obj.directEndpointConfig()?.let { return it }
            }
            return null
        }

        private fun JsonObject.directEndpointConfig(): McpEndpointConfig? {
            val url = listOf("url", "endpoint", "sseUrl", "sse_url")
                .firstNotNullOfOrNull { key ->
                    get(key)
                        ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                        ?.asString
                        ?.trim()
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                } ?: return null
            val headers = get("headers")?.asJsonObjectOrNull()
                ?.entrySet()
                ?.associate { (key, value) -> key to value.asString }
                .orEmpty()
            return McpEndpointConfig(url, headers)
        }

        private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
            if (isJsonObject) asJsonObject else null
    }
}

class McpProtocolException(message: String) : RuntimeException(message)

/**
 * Minimal standard MCP Streamable HTTP client.
 *
 * Implements the lifecycle needed by mobileClaw today:
 * initialize -> notifications/initialized -> tools/list -> tools/call.
 */
class McpHttpClient(
    private val clientName: String = "mobileClaw",
    private val clientVersion: String = "1.0.0",
) {
    private val gson = Gson()
    private val ids = AtomicLong(1)
    private val sessions = mutableMapOf<String, McpSession>()
    private val http = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun initialize(endpoint: String, headers: Map<String, String> = emptyMap(), force: Boolean = false): McpSession =
        withContext(Dispatchers.IO) {
            val normalizedEndpoint = endpoint.trim()
            if (normalizedEndpoint.isSseEndpoint()) {
                return@withContext initializeSse(normalizedEndpoint, headers)
            }
            if (!force) sessions[sessionKey(normalizedEndpoint, headers)]?.let { return@withContext it }

            val params = JsonObject().apply {
                addProperty("protocolVersion", MCP_PROTOCOL_VERSION)
                add("capabilities", JsonObject())
                add("clientInfo", JsonObject().apply {
                    addProperty("name", clientName)
                    addProperty("version", clientVersion)
                })
            }
            val response = postRpc(
                endpoint = normalizedEndpoint,
                method = "initialize",
                params = params,
                headers = headers,
                protocolVersion = null,
                sessionId = null,
                nameHeader = null,
                expectResponse = true,
            )
            val result = response.body.getAsJsonObject("result")
            val negotiated = result["protocolVersion"]?.asString ?: MCP_PROTOCOL_VERSION
            val session = McpSession(
                endpoint = normalizedEndpoint,
                protocolVersion = negotiated,
                serverInfo = result["serverInfo"]?.asJsonObjectOrNull(),
                capabilities = result["capabilities"]?.asJsonObjectOrNull(),
                sessionId = response.sessionId,
            )
            sessions[sessionKey(normalizedEndpoint, headers)] = session

            runCatching {
                postRpc(
                    endpoint = normalizedEndpoint,
                    method = "notifications/initialized",
                    params = null,
                    headers = headers,
                    protocolVersion = negotiated,
                    sessionId = session.sessionId,
                    nameHeader = null,
                    expectResponse = false,
                )
            }

            session
        }

    suspend fun listTools(endpoint: String, headers: Map<String, String> = emptyMap(), cursor: String? = null): McpToolList =
        withContext(Dispatchers.IO) {
            if (endpoint.trim().isSseEndpoint()) {
                return@withContext withSseConnection(endpoint.trim(), headers) {
                    initializeForSse(headers)
                    val params = JsonObject().apply {
                        if (!cursor.isNullOrBlank()) addProperty("cursor", cursor)
                    }
                    parseToolList(sendRpc("tools/list", params, expectResponse = true).getAsJsonObject("result"))
                }
            }
            val session = initialize(endpoint, headers)
            val params = JsonObject().apply {
                if (!cursor.isNullOrBlank()) addProperty("cursor", cursor)
            }
            val response = postRpc(
                endpoint = session.endpoint,
                method = "tools/list",
                params = params,
                headers = headers,
                protocolVersion = session.protocolVersion,
                sessionId = session.sessionId,
                nameHeader = null,
                expectResponse = true,
            )
            parseToolList(response.body.getAsJsonObject("result"))
        }

    suspend fun callTool(
        endpoint: String,
        headers: Map<String, String> = emptyMap(),
        toolName: String,
        arguments: JsonObject = JsonObject(),
    ): McpToolCallResult = withContext(Dispatchers.IO) {
        if (endpoint.trim().isSseEndpoint()) {
            return@withContext withSseConnection(endpoint.trim(), headers) {
                initializeForSse(headers)
                val params = JsonObject().apply {
                    addProperty("name", toolName)
                    add("arguments", arguments)
                }
                parseToolCallResult(sendRpc("tools/call", params, expectResponse = true).getAsJsonObject("result"))
            }
        }
        val session = initialize(endpoint, headers)
        val params = JsonObject().apply {
            addProperty("name", toolName)
            add("arguments", arguments)
        }
        val response = postRpc(
            endpoint = session.endpoint,
            method = "tools/call",
            params = params,
            headers = headers,
            protocolVersion = session.protocolVersion,
            sessionId = session.sessionId,
            nameHeader = toolName,
            expectResponse = true,
        )
        parseToolCallResult(response.body.getAsJsonObject("result"))
    }

    private suspend fun initializeSse(endpoint: String, headers: Map<String, String>): McpSession =
        withSseConnection(endpoint, headers) {
            initializeForSse(headers)
        }

    private suspend fun SseRpcConnection.initializeForSse(headers: Map<String, String>): McpSession {
        val params = JsonObject().apply {
            addProperty("protocolVersion", MCP_PROTOCOL_VERSION)
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", clientName)
                addProperty("version", clientVersion)
            })
        }
        val response = sendRpc("initialize", params, expectResponse = true)
        val result = response.getAsJsonObject("result")
        val negotiated = result["protocolVersion"]?.asString ?: MCP_PROTOCOL_VERSION
        runCatching {
            sendRpc("notifications/initialized", null, expectResponse = false, protocolVersion = negotiated)
        }
        return McpSession(
            endpoint = sseEndpoint,
            protocolVersion = negotiated,
            serverInfo = result["serverInfo"]?.asJsonObjectOrNull(),
            capabilities = result["capabilities"]?.asJsonObjectOrNull(),
            sessionId = transportSessionId,
        )
    }

    private suspend fun <T> withSseConnection(
        endpoint: String,
        headers: Map<String, String>,
        block: suspend SseRpcConnection.() -> T,
    ): T {
        val connection = SseRpcConnection(endpoint, headers)
        return try {
            connection.open()
            connection.block()
        } finally {
            connection.close()
        }
    }

    private fun parseToolList(result: JsonObject): McpToolList {
        val tools = result.getAsJsonArray("tools")?.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            McpTool(
                name = obj["name"]?.asString.orEmpty(),
                title = obj["title"]?.asString,
                description = obj["description"]?.asString,
                inputSchema = obj["inputSchema"]?.asJsonObjectOrNull(),
            )
        }?.filter { it.name.isNotBlank() } ?: emptyList()
        return McpToolList(
            tools = tools,
            nextCursor = result["nextCursor"]?.asString,
        )
    }

    private fun parseToolCallResult(result: JsonObject): McpToolCallResult {
        val content = result.getAsJsonArray("content")?.mapNotNull { it.asJsonObjectOrNull() } ?: emptyList()
        return McpToolCallResult(
            content = content,
            structuredContent = result["structuredContent"],
            isError = result["isError"]?.asBoolean ?: false,
            raw = result,
        )
    }

    private suspend fun postRpc(
        endpoint: String,
        method: String,
        params: JsonObject?,
        headers: Map<String, String>,
        protocolVersion: String?,
        sessionId: String?,
        nameHeader: String?,
        expectResponse: Boolean,
    ): RpcHttpResponse = suspendCancellableCoroutine { cont ->
        val id = if (expectResponse) ids.getAndIncrement() else null
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            if (id != null) addProperty("id", id)
            addProperty("method", method)
            if (params != null) add("params", params)
        }
        val body = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("Mcp-Method", method)
            .header("User-Agent", "mobileClaw MCP Client")

        if (protocolVersion != null) requestBuilder.header("MCP-Protocol-Version", protocolVersion)
        if (!sessionId.isNullOrBlank()) requestBuilder.header("Mcp-Session-Id", sessionId)
        if (!nameHeader.isNullOrBlank()) requestBuilder.header("Mcp-Name", nameHeader)
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) requestBuilder.header(key, value)
        }

        val call = http.newCall(requestBuilder.build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val responseText = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        cont.resumeWithException(McpProtocolException("HTTP ${it.code}: ${responseText.take(500)}"))
                        return
                    }
                    if (!expectResponse) {
                        cont.resume(RpcHttpResponse(JsonObject(), it.mcpSessionId()))
                        return
                    }
                    runCatching {
                        val json = parseRpcResponse(responseText, it.header("Content-Type").orEmpty(), id)
                        if (json.has("error")) {
                            throw McpProtocolException("MCP ${method} error: ${json["error"]}")
                        }
                        if (!json.has("result")) {
                            throw McpProtocolException("MCP ${method} returned no result: ${responseText.take(500)}")
                        }
                        RpcHttpResponse(json, it.mcpSessionId())
                    }.onSuccess { parsed ->
                        cont.resume(parsed)
                    }.onFailure { error ->
                        cont.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun parseRpcResponse(responseText: String, contentType: String, expectedId: Long?): JsonObject {
        if (responseText.isBlank()) throw McpProtocolException("Empty MCP response")
        if (contentType.contains("text/event-stream", ignoreCase = true)) {
            return parseSseMessages(responseText)
                .firstOrNull { message ->
                    expectedId == null || message["id"]?.asLong == expectedId
                }
                ?: throw McpProtocolException("No matching JSON-RPC response in SSE stream")
        }
        return JsonParser.parseString(responseText).asJsonObject
    }

    private fun parseSseMessages(responseText: String): List<JsonObject> {
        val messages = mutableListOf<JsonObject>()
        val data = StringBuilder()
        fun flush() {
            val text = data.toString().trim()
            if (text.isNotBlank() && text != "[DONE]") {
                runCatching { JsonParser.parseString(text).asJsonObject }
                    .onSuccess { messages += it }
            }
            data.clear()
        }
        responseText.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        flush()
        return messages
    }

    private fun sessionKey(endpoint: String, headers: Map<String, String>): String =
        endpoint + "|" + headers.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value.hashCode()}" }

    private fun String.isSseEndpoint(): Boolean =
        runCatching { toHttpUrl().pathSegments.any { it.equals("sse", ignoreCase = true) } }.getOrDefault(false)

    private inner class SseRpcConnection(
        val sseEndpoint: String,
        private val headers: Map<String, String>,
    ) {
        private val endpointDeferred = CompletableDeferred<String>()
        private val pending = mutableMapOf<Long, CompletableDeferred<JsonObject>>()
        private var source: EventSource? = null
        private var messageEndpoint: String? = null
        var transportSessionId: String? = null
            private set

        suspend fun open() {
            val requestBuilder = Request.Builder()
                .url(sseEndpoint)
                .get()
                .header("Accept", "text/event-stream")
                .header("User-Agent", "mobileClaw MCP Client")
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) requestBuilder.header(key, value)
            }
            source = EventSources.createFactory(http).newEventSource(
                requestBuilder.build(),
                object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        val trimmed = data.trim()
                        if ((type == "endpoint" || looksLikeSseMessageEndpoint(trimmed)) && !endpointDeferred.isCompleted) {
                            val resolved = resolveSseMessageEndpoint(sseEndpoint, trimmed)
                            transportSessionId = resolved.substringAfter("sessionId=", "")
                                .substringBefore('&')
                                .takeIf { it.isNotBlank() && it != resolved && !it.contains("/") }
                            endpointDeferred.complete(resolved)
                            return
                        }
                        runCatching { JsonParser.parseString(trimmed).asJsonObject }
                            .onSuccess { json ->
                                val responseId = json["id"]?.takeIf { !it.isJsonNull }?.asLong ?: return@onSuccess
                                pending.remove(responseId)?.complete(json)
                            }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                        val error = McpProtocolException(
                            "MCP SSE connection failed: ${t?.message ?: response?.code?.toString() ?: "unknown"}"
                        )
                        if (!endpointDeferred.isCompleted) endpointDeferred.completeExceptionally(error)
                        pending.values.forEach { it.completeExceptionally(error) }
                        pending.clear()
                    }
                }
            )
            messageEndpoint = withTimeout(15_000) { endpointDeferred.await() }
        }

        suspend fun sendRpc(
            method: String,
            params: JsonObject?,
            expectResponse: Boolean,
            protocolVersion: String? = MCP_PROTOCOL_VERSION,
        ): JsonObject {
            val id = if (expectResponse) ids.getAndIncrement() else null
            val payload = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                if (id != null) addProperty("id", id)
                addProperty("method", method)
                if (params != null) add("params", params)
            }
            val deferred = if (id != null) CompletableDeferred<JsonObject>().also { pending[id] = it } else null
            try {
                postSseRpc(method, payload, protocolVersion)
                if (deferred == null) return JsonObject()
                val json = withTimeout(60_000) { deferred.await() }
                if (json.has("error")) throw McpProtocolException("MCP ${method} error: ${json["error"]}")
                if (!json.has("result")) throw McpProtocolException("MCP ${method} returned no result: $json")
                return json
            } finally {
                if (id != null) pending.remove(id)
            }
        }

        private suspend fun postSseRpc(method: String, payload: JsonObject, protocolVersion: String?) {
            val target = messageEndpoint ?: throw McpProtocolException("MCP SSE message endpoint is not ready")
            val body = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(target)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Mcp-Method", method)
                .header("User-Agent", "mobileClaw MCP Client")
            if (protocolVersion != null) requestBuilder.header("MCP-Protocol-Version", protocolVersion)
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) requestBuilder.header(key, value)
            }
            suspendCancellableCoroutine<Unit> { cont ->
                val call = http.newCall(requestBuilder.build())
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (!cont.isCancelled) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use {
                            if (it.isSuccessful) {
                                cont.resume(Unit)
                            } else {
                                val text = it.body?.string().orEmpty()
                                cont.resumeWithException(McpProtocolException("HTTP ${it.code}: ${text.take(500)}"))
                            }
                        }
                    }
                })
            }
        }

        fun close() {
            source?.cancel()
            source = null
            val closed = McpProtocolException("MCP SSE connection closed")
            pending.values.forEach { if (!it.isCompleted) it.completeExceptionally(closed) }
            pending.clear()
        }
    }

    private fun looksLikeSseMessageEndpoint(data: String): Boolean =
        data.startsWith("/") || data.startsWith("http://") || data.startsWith("https://")

    private fun resolveSseMessageEndpoint(sseEndpoint: String, rawEndpoint: String): String {
        if (rawEndpoint.startsWith("http://") || rawEndpoint.startsWith("https://")) return rawEndpoint
        val base = sseEndpoint.toHttpUrl()
        return base.resolve(rawEndpoint)?.toString()
            ?: throw McpProtocolException("Invalid MCP SSE message endpoint: $rawEndpoint")
    }

    private fun okhttp3.Response.mcpSessionId(): String? =
        header("Mcp-Session-Id") ?: header("MCP-Session-Id") ?: header("mcp-session-id")

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private data class RpcHttpResponse(
        val body: JsonObject,
        val sessionId: String?,
    )
}

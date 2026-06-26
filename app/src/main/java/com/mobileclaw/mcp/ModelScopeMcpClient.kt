package com.mobileclaw.mcp

import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ModelScopeMcpServer(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val views: Int,
)

data class ModelScopeMcpEndpoint(
    val endpoint: String,
    val raw: String,
)

class ModelScopeMcpException(message: String) : RuntimeException(message)

/**
 * Lightweight client for ModelScope's MCP OpenAPI.
 *
 * The public server list can be queried without a token. Runtime SSE URLs are
 * short lived and require a token, so callers should store serverId + token and
 * refresh the endpoint before tool discovery or execution when needed.
 */
class ModelScopeMcpClient(
    private val baseUrl: String = "https://modelscope.cn/openapi/v1",
) {
    private val http = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchServers(query: String, pageSize: Int = 20): List<ModelScopeMcpServer> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                if (query.isNotBlank()) put("search", query)
                put("page_number", 1)
                put("page_size", pageSize.coerceIn(1, 100))
            }.toString()
            val text = request(
                Request.Builder()
                    .url("$baseUrl/mcp/servers")
                    .put(body.toRequestBody(JSON))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
            parseServers(text)
        }

    suspend fun deployAndGetEndpoint(serverId: String, token: String): ModelScopeMcpEndpoint =
        withContext(Dispatchers.IO) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                throw ModelScopeMcpException("ModelScope Token is required to deploy MCP servers")
            }

            val deployText = deploy(serverId, cleanToken)
            extractEndpoint(deployText)?.let { return@withContext ModelScopeMcpEndpoint(it, deployText) }

            repeat(8) { attempt ->
                if (attempt > 0) delay(1500L)
                val infoText = getServer(serverId, cleanToken, getOperationalUrl = true)
                extractEndpoint(infoText)?.let { return@withContext ModelScopeMcpEndpoint(it, infoText) }
            }
            throw ModelScopeMcpException("ModelScope did not return an operational SSE URL for $serverId")
        }

    suspend fun getEndpoint(serverId: String, token: String): ModelScopeMcpEndpoint =
        withContext(Dispatchers.IO) {
            val text = getServer(serverId, token.trim(), getOperationalUrl = true)
            val endpoint = extractEndpoint(text)
                ?: throw ModelScopeMcpException("ModelScope did not return an operational SSE URL for $serverId")
            ModelScopeMcpEndpoint(endpoint, text)
        }

    private fun deploy(serverId: String, token: String): String {
        val body = """{"transport_type":"sse"}""".toRequestBody(JSON)
        return request(
            Request.Builder()
                .url("$baseUrl/mcp/servers/${serverId.encodedPath()}/deploy")
                .post(body)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .build()
        )
    }

    private fun getServer(serverId: String, token: String, getOperationalUrl: Boolean): String =
        request(
            Request.Builder()
                .url("$baseUrl/mcp/servers/${serverId.encodedPath()}?get_operational_url=$getOperationalUrl")
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .build()
        )

    private fun request(request: Request): String {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("message").ifBlank { text.take(500) }
                }.getOrDefault(text.take(500))
                throw ModelScopeMcpException("ModelScope HTTP ${response.code}: $message")
            }
            val root = runCatching { JSONObject(text) }.getOrNull()
            if (root != null && root.optBoolean("success", true) == false) {
                val message = root.optString("message").ifBlank { text.take(500) }
                throw ModelScopeMcpException(message)
            }
            return text
        }
    }

    private fun parseServers(json: String): List<ModelScopeMcpServer> {
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: root
        val arr = data.optJSONArray("mcp_server_list")
            ?: data.optJSONArray("servers")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("results")
            ?: return emptyList()
        val entries = mutableListOf<ModelScopeMcpServer>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
                .ifBlank { item.optString("server_id") }
                .ifBlank { item.optString("repo_id") }
            if (id.isBlank()) continue
            val localesZh = item.optJSONObject("locales")?.optJSONObject("zh")
            val name = localesZh?.optString("name").orEmpty()
                .ifBlank { item.optString("chinese_name") }
                .ifBlank { item.optString("name") }
                .ifBlank { id }
            val description = localesZh?.optString("description").orEmpty()
                .ifBlank { item.optString("description") }
                .ifBlank { "ModelScope MCP Server" }
            val tags = buildList {
                item.optJSONArray("tags")?.let { tags ->
                    for (j in 0 until tags.length()) tags.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                }
                item.optJSONArray("categories")?.let { categories ->
                    for (j in 0 until categories.length()) categories.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct().take(3)
            entries += ModelScopeMcpServer(
                id = id,
                name = name,
                description = description,
                tags = tags.ifEmpty { listOf("MCP") },
                views = item.optInt("view_count", item.optInt("downloads", 0)),
            )
        }
        return entries
    }

    private fun extractEndpoint(json: String): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val data = root.optJSONObject("data") ?: root
        data.endpointFromObject()?.let { return it }
        listOf("deployment", "deploy", "server", "runtime", "result").forEach { key ->
            data.optJSONObject(key)?.endpointFromObject()?.let { return it }
        }
        return null
    }

    private fun JSONObject.endpointFromObject(): String? =
        optString("operational_url")
            .ifBlank { optString("sse_url") }
            .ifBlank { optString("endpoint") }
            .ifBlank { optString("url") }
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun String.encodedPath(): String =
        URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "MobileClaw/1.0 Android"
    }
}

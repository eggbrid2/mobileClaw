package com.mobileclaw.server

import com.google.gson.Gson
import com.mobileclaw.config.UserConfig
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.skill.HttpSkillConfig
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal HTTP/1.1 server on 127.0.0.1:52732.
 * Exposes app APIs so dynamic HTTP skills can call back into the agent.
 * Only accepts loopback connections — no external access.
 *
 * Endpoints:
 *   GET  /api/health          — health check
 *   GET  /api/skills          — list all registered skills
 *   POST /api/skill           — save a new SkillDefinition (JSON body)
 *   GET  /api/memory          — list all semantic memory facts
 *   POST /api/memory          — set a memory fact {key, value}
 *   GET  /api/config          — list all user config entries
 *   POST /api/config          — set a config entry {key, value}
 */
class LocalApiServer(
    private val skillRegistry: SkillRegistry,
    private val skillLoader: SkillLoader,
    private val semanticMemory: SemanticMemory,
    private val userConfig: UserConfig,
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null

    companion object {
        const val PORT = 52732
        const val BASE_URL = "http://127.0.0.1:$PORT"
    }

    fun start() {
        scope.launch {
            runCatching {
                serverSocket = ServerSocket(PORT, 10, java.net.InetAddress.getByName("127.0.0.1"))
                while (!serverSocket!!.isClosed) {
                    val client = runCatching { serverSocket!!.accept() }.getOrNull() ?: break
                    // Only accept loopback connections
                    if (!client.inetAddress.isLoopbackAddress) { client.close(); continue }
                    launch { handleClient(client) }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
    }

    private fun handleClient(socket: Socket) {
        runCatching {
            socket.use {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = PrintWriter(socket.outputStream, false)

                // Read request line
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore("?")

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String
                var contentLength = 0
                while (true) {
                    line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase()
                        val value = line.substring(colon + 1).trim()
                        headers[key] = value
                        if (key == "content-length") contentLength = value.toIntOrNull() ?: 0
                    }
                }

                // Read body if present
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                val (statusCode, responseBody) = route(method, path, body)
                val json = gson.toJson(responseBody)
                val response = buildString {
                    append("HTTP/1.1 $statusCode\r\n")
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${json.toByteArray().size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                    append(json)
                }
                writer.print(response)
                writer.flush()
            }
        }
    }

    private fun route(method: String, path: String, body: String): Pair<String, Any> {
        return when {
            path == "/api/health" -> "200 OK" to mapOf("status" to "ok", "port" to PORT)

            path == "/api/skills" && method == "GET" -> {
                val skills = skillRegistry.all().map { skill ->
                    mapOf(
                        "id" to skill.meta.id,
                        "name" to skill.meta.name,
                        "description" to skill.meta.description,
                        "type" to skill.meta.type.name.lowercase(),
                        "injectionLevel" to skill.meta.injectionLevel,
                        "isBuiltin" to skill.meta.isBuiltin,
                    )
                }
                "200 OK" to mapOf("skills" to skills, "total" to skills.size)
            }

            path == "/api/skill" && method == "POST" -> {
                runCatching {
                    val def = gson.fromJson(body, SkillDefinition::class.java)
                    skillLoader.persist(def)
                    "200 OK" to mapOf("success" to true, "id" to def.meta.id)
                }.getOrElse { e ->
                    "400 Bad Request" to mapOf("error" to (e.message ?: "Invalid skill definition"))
                }
            }

            path.startsWith("/api/skill/") && method == "DELETE" -> {
                val id = path.removePrefix("/api/skill/")
                skillLoader.delete(id)
                "200 OK" to mapOf("success" to true, "deleted" to id)
            }

            path == "/api/memory" && method == "GET" -> {
                val facts = runBlocking { runCatching { semanticMemory.all() }.getOrDefault(emptyMap()) }
                "200 OK" to mapOf("memory" to facts)
            }

            path == "/api/memory" && method == "POST" -> {
                runCatching {
                    val req = gson.fromJson(body, Map::class.java)
                    val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
                    val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
                    runBlocking { semanticMemory.set(key, value) }
                    "200 OK" to mapOf("success" to true, "key" to key)
                }.getOrElse { e ->
                    "400 Bad Request" to mapOf("error" to (e.message ?: "Bad request"))
                }
            }

            path == "/api/config" && method == "GET" -> {
                val entries = runBlocking { runCatching { userConfig.all() }.getOrDefault(emptyMap()) }
                "200 OK" to mapOf("config" to entries)
            }

            path == "/api/config" && method == "POST" -> {
                runCatching {
                    val req = gson.fromJson(body, Map::class.java)
                    val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
                    val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
                    runBlocking { userConfig.set(key, value) }
                    "200 OK" to mapOf("success" to true, "key" to key)
                }.getOrElse { e ->
                    "400 Bad Request" to mapOf("error" to (e.message ?: "Bad request"))
                }
            }

            else -> "404 Not Found" to mapOf("error" to "Not found: $method $path")
        }
    }
}

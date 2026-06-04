package com.mobileclaw.server

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.mobileclaw.agent.TaskClassifier
import com.mobileclaw.config.UserConfig
import com.mobileclaw.memory.MemoryContextBuilder
import com.mobileclaw.memory.MemoryWriter
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.memory.db.ClawDatabase
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * LAN HTTP server on 0.0.0.0:52733.
 * Serves a chat console web UI and a comprehensive REST API for OpenClaw integration.
 *
 * Console endpoints:
 *   GET  /                                   — console web UI
 *   GET  /api/events                         — SSE stream of agent events
 *   POST /api/send                           — send a task {message: string}
 *   GET  /api/sessions                       — list recent sessions
 *   GET  /api/messages?sessionId=<id>        — messages for a session
 *
 * OpenClaw bridge endpoints (all CORS-enabled):
 *   GET  /api/info                           — device info, API version, capabilities
 *   GET  /api/skills                         — list all registered skills
 *   GET  /api/skills/definitions             — download all dynamic skill definitions (JSON bundle)
 *   GET  /api/skill/<id>/definition          — get one skill definition
 *   POST /api/skill                          — install a skill definition
 *   DELETE /api/skill/<id>                   — delete a dynamic skill
 *   GET  /api/memory                         — all semantic memory facts
 *   POST /api/memory/context                 — build foundational memory context {message}
 *   POST /api/memory                         — set memory fact {key, value}
 *   GET  /api/config                         — all user config entries
 *   POST /api/config                         — set config entry {key, value, description?}
 *   DELETE /api/config/<key>                 — delete a config entry
 *   GET  /api/openclaw/cli.sh                — download OpenClaw bash CLI script
 */
class ConsoleServer(
    private val filesDir: File,
    private val database: ClawDatabase,
    private val enabled: Boolean = false,
    private val lanEnabled: Boolean = false,
    private val authToken: String = "",
    val messageRequests: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 16),
    private val skillRegistry: SkillRegistry? = null,
    private val skillLoader: SkillLoader? = null,
    private val semanticMemory: SemanticMemory? = null,
    private val userConfig: UserConfig? = null,
) {
    companion object {
        const val PORT = 52733
        private const val TAG = "ConsoleServer"
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val sseClients = CopyOnWriteArrayList<SseClient>()
    private val gson = Gson()

    private data class SseClient(val output: OutputStream, val socket: Socket)

    fun start() {
        if (!enabled) return
        scope.launch {
            try {
                ensureDefaultHtml()
                val bindAddress = if (lanEnabled) "0.0.0.0" else "127.0.0.1"
                serverSocket = ServerSocket(PORT, 50, InetAddress.getByName(bindAddress))
                while (serverSocket?.isClosed == false) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Console accept loop stopped", t)
                        null
                    } ?: break
                    launch { handleClient(client) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start console server", t)
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to stop console server cleanly", t)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun isLanEnabled(): Boolean = enabled && lanEnabled

    fun getAccessUrl(): String =
        if (authToken.isBlank()) getLanUrl() else "${getLanUrl()}?token=$authToken"

    fun getLanUrl(): String = "http://${getLanIp()}:$PORT"

    fun readHtml(): String {
        val file = File(filesDir, "console_web/index.html")
        return if (file.exists()) file.readText() else DEFAULT_CONSOLE_HTML
    }

    fun writeHtml(html: String) {
        val dir = File(filesDir, "console_web")
        dir.mkdirs()
        File(dir, "index.html").writeText(html, Charsets.UTF_8)
    }

    fun resetHtml() {
        val file = File(filesDir, "console_web/index.html")
        file.writeText(DEFAULT_CONSOLE_HTML, Charsets.UTF_8)
    }

    fun broadcast(type: String, text: String) {
        val event = "data: {\"type\":${gson.toJson(type)},\"d\":${gson.toJson(text)}}\n\n"
        val bytes = event.toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<SseClient>()
        for (client in sseClients) {
            try {
                client.output.write(bytes)
                client.output.flush()
            } catch (t: Throwable) {
                Log.w(TAG, "Dropping dead SSE client during broadcast", t)
                dead += client
            }
        }
        sseClients.removeAll(dead.toSet())
    }

    private fun getLanIp(): String = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
            ?.sortedWith(compareBy { iface ->
                // Prefer WiFi (wlan) → Ethernet (eth/usb) → others → cellular (rmnet) last
                when {
                    iface.name.startsWith("wlan") -> 0
                    iface.name.startsWith("eth") || iface.name.startsWith("usb") -> 1
                    iface.name.startsWith("rmnet") -> 3
                    else -> 2
                }
            })
            ?.flatMap { iface -> iface.inetAddresses.toList().filter { !it.isLoopbackAddress && it is Inet4Address } }
            ?.firstOrNull()
            ?.hostAddress
            ?: "127.0.0.1"
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to resolve LAN IP, fallback to loopback", t)
        "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val query = if ("?" in fullPath) fullPath.substringAfter("?") else ""

            val headers = linkedMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val headerName = line.substring(0, colon).trim().lowercase()
                    val headerValue = line.substring(colon + 1).trim()
                    headers[headerName] = headerValue
                    if (headerName == "content-length") {
                        contentLength = headerValue.toIntOrNull() ?: 0
                    }
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                reader.read(buf, 0, contentLength)
                String(buf)
            } else ""

            // CORS preflight
            if (method == "OPTIONS") { sendCorsOk(socket); return }

            val isLoopbackClient = socket.inetAddress.isLoopbackAddress
            if (!isAuthorized(path, query, headers, isLoopbackClient)) {
                sendJson(socket, "401 Unauthorized", mapOf("error" to "Console API auth required"))
                return
            }

            when {
                path == "/api/events" -> { handleSse(socket); return }
                path == "/api/send" && method == "POST" -> handleSend(socket, body)
                path == "/api/sessions" -> handleSessions(socket)
                path == "/api/messages" -> handleMessages(socket, query)
                // OpenClaw bridge
                path == "/api/info" -> handleInfo(socket)
                path == "/api/skills" && method == "GET" -> handleSkillsList(socket)
                path == "/api/skills/definitions" && method == "GET" -> handleSkillsExport(socket)
                path.matches(Regex("/api/skill/[^/]+/definition")) && method == "GET" -> {
                    val id = path.removePrefix("/api/skill/").removeSuffix("/definition")
                    handleSkillDefinition(socket, id)
                }
                path == "/api/skill" && method == "POST" -> handleSkillInstall(socket, body)
                path.startsWith("/api/skill/") && method == "DELETE" -> {
                    val id = path.removePrefix("/api/skill/")
                    handleSkillDelete(socket, id)
                }
                path == "/api/memory" && method == "GET" -> handleMemoryGet(socket)
                path == "/api/memory/context" && method == "POST" -> handleMemoryContext(socket, body)
                path == "/api/memory" && method == "POST" -> handleMemorySet(socket, body)
                path == "/api/config" && method == "GET" -> handleConfigGet(socket)
                path == "/api/config" && method == "POST" -> handleConfigSet(socket, body)
                path.startsWith("/api/config/") && method == "DELETE" -> {
                    val key = path.removePrefix("/api/config/")
                    handleConfigDelete(socket, key)
                }
                path == "/api/openclaw/cli.sh" -> handleCliScript(socket)
                path == "/" || path == "/index.html" -> handleIndex(socket)
                else -> sendJson(socket, "404 Not Found", mapOf("error" to "Not found: $method $path"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to handle console client ${socket.inetAddress?.hostAddress}", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to handle request"))
        }
    }

    private fun isAuthorized(
        path: String,
        query: String,
        headers: Map<String, String>,
        isLoopbackClient: Boolean,
    ): Boolean {
        if (!path.startsWith("/api/")) return true
        if (isLoopbackClient && !lanEnabled) return true
        if (authToken.isBlank()) return isLoopbackClient
        val bearer = headers["authorization"]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter("Bearer ", "")
            ?.trim()
            .orEmpty()
        val headerToken = headers["x-claw-token"].orEmpty()
        val queryToken = query.split("&")
            .firstOrNull { it.startsWith("token=") }
            ?.substringAfter("token=")
            .orEmpty()
        return listOf(bearer, headerToken, queryToken).any { it == authToken }
    }

    private fun handleSse(socket: Socket) {
        val output = try {
            socket.getOutputStream()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open SSE output stream", t)
            try {
                socket.close()
            } catch (_: Throwable) {
            }
            return
        }
        try {
            output.write((
                "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\nConnection: keep-alive\r\n\r\n" +
                "data: {\"type\":\"connected\"}\n\n"
            ).toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SSE stream", t)
            try {
                socket.close()
            } catch (_: Throwable) {
            }
            return
        }

        val client = SseClient(output, socket)
        sseClients.add(client)
        try {
            val input = socket.getInputStream()
            while (!socket.isClosed) {
                if (input.read() == -1) break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "SSE client loop ended with socket error", t)
        }
        sseClients.remove(client)
        try {
            socket.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close SSE socket", t)
        }
    }

    private fun handleSend(socket: Socket, body: String) {
        val msg = try {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(body, Map::class.java) as? Map<String, Any>)?.get("message") as? String
        } catch (t: Throwable) {
            Log.w(TAG, "Invalid /api/send payload: ${body.take(240)}", t)
            null
        }.orEmpty()
        if (msg.isNotBlank()) scope.launch { messageRequests.emit(msg) }
        sendJson(socket, "200 OK", mapOf("ok" to true))
    }

    private fun handleSessions(socket: Socket) {
        val sessions = try {
            runBlocking { database.sessionDao().recent(50) }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load sessions", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to load sessions"))
            return
        }
        sendJson(socket, "200 OK", mapOf("sessions" to sessions.map {
            mapOf("id" to it.id, "title" to it.title, "updatedAt" to it.updatedAt)
        }))
    }

    private fun handleMessages(socket: Socket, query: String) {
        val sessionId = query.split("&")
            .firstOrNull { it.startsWith("sessionId=") }?.substringAfter("sessionId=") ?: ""
        val msgs = if (sessionId.isNotBlank()) {
            try {
                runBlocking { database.sessionMessageDao().forSession(sessionId) }
                    .map { mapOf("role" to it.role, "text" to it.text, "createdAt" to it.createdAt) }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load messages for session=$sessionId", t)
                sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to load messages"))
                return
            }
        } else emptyList()
        sendJson(socket, "200 OK", mapOf("messages" to msgs))
    }

    private fun handleIndex(socket: Socket) {
        try {
            val html = getHtml()
            val bytes = html.toByteArray(Charsets.UTF_8)
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
            socket.close()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to serve console index", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to render console"))
        }
    }

    private fun sendJson(socket: Socket, status: String, data: Any) {
        try {
            socket.use {
                val json = gson.toJson(data)
                val bytes = json.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write((
                    "HTTP/1.1 $status\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray())
                out.write(bytes)
                out.flush()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send json response status=$status", t)
        }
    }

    // ── OpenClaw bridge handlers ──────────────────────────────────────────────

    private fun handleInfo(socket: Socket) {
        val ip = getLanIp()
        sendJson(socket, "200 OK", mapOf(
            "app" to "MobileClaw",
            "api_version" to 1,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to Build.VERSION.RELEASE,
            "lan_ip" to ip,
            "port" to PORT,
            "base_url" to "http://$ip:$PORT",
            "auth_required" to authToken.isNotBlank(),
            "capabilities" to listOf("send", "sessions", "messages", "events", "skills", "memory", "config"),
            "cli_url" to "http://$ip:$PORT/api/openclaw/cli.sh",
        ))
    }

    private fun handleSkillsList(socket: Socket) {
        val skills = skillRegistry?.all()?.filterNot { it.meta.internalTool }?.map { skill ->
            mapOf(
                "id" to skill.meta.id,
                "name" to skill.meta.name,
                "nameZh" to (skill.meta.nameZh ?: skill.meta.name),
                "description" to skill.meta.description,
                "type" to skill.meta.type.name.lowercase(),
                "injectionLevel" to skill.meta.injectionLevel,
                "isBuiltin" to skill.meta.isBuiltin,
                "internalTool" to skill.meta.internalTool,
                "tags" to skill.meta.tags,
            )
        } ?: emptyList<Any>()
        sendJson(socket, "200 OK", mapOf("skills" to skills, "total" to skills.size))
    }

    private fun handleSkillsExport(socket: Socket) {
        val defs = skillLoader?.allDynamic() ?: emptyList<SkillDefinition>()
        sendJson(socket, "200 OK", mapOf("definitions" to defs, "total" to defs.size))
    }

    private fun handleSkillDefinition(socket: Socket, id: String) {
        val defs = skillLoader?.allDynamic() ?: emptyList()
        val def = defs.firstOrNull { it.meta.id == id }
        if (def != null) sendJson(socket, "200 OK", def)
        else sendJson(socket, "404 Not Found", mapOf("error" to "Skill not found: $id"))
    }

    private fun handleSkillInstall(socket: Socket, body: String) {
        val loader = skillLoader ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Skill management not available")); return
        }
        try {
            val def = gson.fromJson(body, SkillDefinition::class.java)
            loader.persist(def)
            sendJson(socket, "200 OK", mapOf("success" to true, "id" to def.meta.id))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install skill from payload: ${body.take(240)}", t)
            sendJson(socket, "400 Bad Request", mapOf("error" to (t.message ?: "Invalid skill definition")))
        }
    }

    private fun handleSkillDelete(socket: Socket, id: String) {
        val loader = skillLoader ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Skill management not available")); return
        }
        try {
            loader.delete(id)
            sendJson(socket, "200 OK", mapOf("success" to true, "deleted" to id))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to delete skill id=$id", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to delete skill"))
        }
    }

    private fun handleMemoryGet(socket: Socket) {
        val facts = try {
            runBlocking { semanticMemory?.facts() ?: emptyList() }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load memory facts", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to load memory"))
            return
        }
        sendJson(socket, "200 OK", mapOf("memory" to facts.associate { it.key to it.value }, "facts" to facts, "total" to facts.size))
    }

    private fun handleMemoryContext(socket: Socket, body: String) {
        val mem = semanticMemory ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Memory not available")); return
        }
        val cfg = userConfig ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Config not available")); return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body.ifBlank { "{}" }, Map::class.java) as Map<String, Any>
            val message = req["message"] as? String ?: ""
            val taskType = TaskClassifier.classify(message)
            val context = runBlocking {
                MemoryContextBuilder(mem, cfg).build(message, taskType).toPrompt()
            }
            sendJson(socket, "200 OK", mapOf("memoryContext" to context, "taskType" to taskType.name))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build memory context. payload=${body.take(240)}", t)
            sendJson(socket, "400 Bad Request", mapOf("error" to (t.message ?: "Bad request")))
        }
    }

    private fun handleMemorySet(socket: Socket, body: String) {
        val mem = semanticMemory ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Memory not available")); return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
            val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
            val source = req["source"] as? String ?: "console_api"
            runBlocking { mem.set(key = key, value = value, source = source) }
            sendJson(socket, "200 OK", mapOf("success" to true, "key" to key))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set memory. payload=${body.take(240)}", t)
            sendJson(socket, "400 Bad Request", mapOf("error" to (t.message ?: "Bad request")))
        }
    }

    private fun handleConfigGet(socket: Socket) {
        val entries = try {
            runBlocking { userConfig?.all() ?: emptyMap<String, Any>() }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load config entries", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to load config"))
            return
        }
        sendJson(socket, "200 OK", mapOf("config" to entries, "total" to entries.size))
    }

    private fun handleConfigSet(socket: Socket, body: String) {
        val cfg = userConfig ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Config not available")); return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
            val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
            val desc = req["description"] as? String ?: ""
            runBlocking {
                val mem = semanticMemory
                if (mem != null) MemoryWriter(mem, cfg).syncUserConfig(key, value, desc) else cfg.set(key, value, desc)
            }
            sendJson(socket, "200 OK", mapOf("success" to true, "key" to key))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set config. payload=${body.take(240)}", t)
            sendJson(socket, "400 Bad Request", mapOf("error" to (t.message ?: "Bad request")))
        }
    }

    private fun handleConfigDelete(socket: Socket, key: String) {
        val cfg = userConfig ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Config not available")); return
        }
        try {
            runBlocking {
                val mem = semanticMemory
                if (mem != null) MemoryWriter(mem, cfg).deleteUserConfig(key) else cfg.delete(key)
            }
            sendJson(socket, "200 OK", mapOf("success" to true, "deleted" to key))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to delete config key=$key", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to delete config"))
        }
    }

    private fun handleCliScript(socket: Socket) {
        val ip = getLanIp()
        val script = buildOpenClawCliScript(ip)
        val bytes = script.toByteArray(Charsets.UTF_8)
        try {
            val out = socket.getOutputStream()
            out.write((
                "HTTP/1.1 200 OK\r\nContent-Type: text/x-shellscript; charset=utf-8\r\n" +
                "Content-Disposition: attachment; filename=\"openclaw.sh\"\r\n" +
                "Content-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
            ).toByteArray())
            out.write(bytes)
            out.flush()
            socket.close()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to serve OpenClaw CLI script", t)
            sendJson(socket, "500 Internal Server Error", mapOf("error" to "Failed to generate cli script"))
        }
    }

    private fun sendCorsOk(socket: Socket) {
        try {
            socket.use {
                socket.getOutputStream().write((
                    "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to send CORS preflight response", t)
        }
    }

    private fun ensureDefaultHtml() {
        try {
            val dir = File(filesDir, "console_web")
            dir.mkdirs()
            val file = File(dir, "index.html")
            if (!file.exists()) {
                file.writeText(DEFAULT_CONSOLE_HTML)
            } else {
                val current = file.readText()
                val looksLikeOldFactoryConsole =
                    "MobileClaw Console" in current &&
                        (("--accent:#a78bfa" in current && "Enter a task below" in current) ||
                            ("--accent:#c7f43a" in current && "输入任务，MobileClaw 会直接开始执行" in current))
                if (looksLikeOldFactoryConsole) file.writeText(DEFAULT_CONSOLE_HTML)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize console html", t)
            throw t
        }
    }

    private fun getHtml(): String {
        val file = File(filesDir, "console_web/index.html")
        val raw = if (file.exists()) file.readText() else DEFAULT_CONSOLE_HTML
        return raw.replace("__CLAW_TOKEN__", authToken)
    }

    private fun buildOpenClawCliScript(ip: String): String = """
#!/usr/bin/env bash
# openclaw — CLI bridge to MobileClaw
# Usage: openclaw <command> [args]
# Download: curl -o openclaw.sh http://$ip:$PORT/api/openclaw/cli.sh && chmod +x openclaw.sh
# Install:  sudo cp openclaw.sh /usr/local/bin/openclaw

CLAW_HOST="${"$"}{CLAW_HOST:-http://$ip:$PORT}"
CLAW_TOKEN="${"$"}{CLAW_TOKEN:-$authToken}"

_require_curl() { command -v curl >/dev/null 2>&1 || { echo "error: curl is required"; exit 1; }; }
_curl_auth() { [ -n "${"$"}CLAW_TOKEN" ] && printf -- ' -H %q' "X-Claw-Token: ${"$"}CLAW_TOKEN"; }
_get()  { _require_curl; eval "curl -sf$(_curl_auth) \"${"$"}{CLAW_HOST}$1\""; }
_post() { _require_curl; eval "curl -sf -X POST -H \"Content-Type: application/json\"$(_curl_auth) -d \"$2\" \"${"$"}{CLAW_HOST}$1\""; }
_del()  { _require_curl; eval "curl -sf -X DELETE$(_curl_auth) \"${"$"}{CLAW_HOST}$1\""; }
_pp()   { command -v python3 >/dev/null 2>&1 && python3 -m json.tool || cat; }

cmd_info()     { _get /api/info | _pp; }
cmd_send()     { [ -z "$1" ] && echo "Usage: openclaw send <message>" && exit 1; _post /api/send "{\"message\":\"$1\"}" | _pp; }
cmd_events()   { _require_curl; echo "Streaming events (Ctrl+C to stop)..."; curl -sN "${"$"}{CLAW_HOST}/api/events"; }
cmd_sessions() { _get /api/sessions | _pp; }
cmd_messages() { [ -z "$1" ] && echo "Usage: openclaw messages <sessionId>" && exit 1; _get "/api/messages?sessionId=$1" | _pp; }

cmd_skills() {
  case "${"$"}{1:-list}" in
    list)     _get /api/skills | _pp ;;
    export)   _get /api/skills/definitions | _pp ;;
    install)  [ -z "$2" ] && echo "Usage: openclaw skills install <file.json>" && exit 1
              _post /api/skill "$(cat "$2")" | _pp ;;
    delete)   [ -z "$2" ] && echo "Usage: openclaw skills delete <id>" && exit 1
              _del "/api/skill/$2" | _pp ;;
    get)      [ -z "$2" ] && echo "Usage: openclaw skills get <id>" && exit 1
              _get "/api/skill/$2/definition" | _pp ;;
    *)        echo "Usage: openclaw skills [list|export|install <file>|delete <id>|get <id>]" ;;
  esac
}

cmd_memory() {
  case "${"$"}{1:-list}" in
    list)  _get /api/memory | _pp ;;
    set)   [ -z "$2" ] || [ -z "$3" ] && echo "Usage: openclaw memory set <key> <value>" && exit 1
           _post /api/memory "{\"key\":\"$2\",\"value\":\"$3\"}" | _pp ;;
    *)     echo "Usage: openclaw memory [list|set <key> <value>]" ;;
  esac
}

cmd_config() {
  case "${"$"}{1:-list}" in
    list)   _get /api/config | _pp ;;
    set)    [ -z "$2" ] || [ -z "$3" ] && echo "Usage: openclaw config set <key> <value>" && exit 1
            _post /api/config "{\"key\":\"$2\",\"value\":\"$3\"}" | _pp ;;
    delete) [ -z "$2" ] && echo "Usage: openclaw config delete <key>" && exit 1
            _del "/api/config/$2" | _pp ;;
    *)      echo "Usage: openclaw config [list|set <key> <value>|delete <key>]" ;;
  esac
}

cmd_help() {
  cat <<'EOF'
openclaw — MobileClaw CLI bridge

Commands:
  info                          Show device info and API version
  send <message>                Send a task to the AI agent
  events                        Stream real-time agent events (SSE)
  sessions                      List recent chat sessions
  messages <sessionId>          Show messages for a session
  skills list                   List all registered skills
  skills export                 Download all dynamic skill definitions (JSON)
  skills install <file.json>    Install a skill from a local JSON file
  skills delete <id>            Delete a dynamic skill
  skills get <id>               Get a specific skill definition
  memory list                   Show all semantic memory facts
  memory set <key> <value>      Set a memory fact
  config list                   Show all user config entries
  config set <key> <value>      Set a config entry
  config delete <key>           Delete a config entry
  help                          Show this help

Environment:
  CLAW_HOST    Override device URL (default: http://$ip:$PORT)

Examples:
  openclaw info
  openclaw send "Search today's tech news"
  openclaw skills export > skills.json
  CLAW_HOST=http://192.168.1.100:$PORT openclaw info
EOF
}

COMMAND="${"$"}{1:-help}"
shift 2>/dev/null || true

case "${"$"}COMMAND" in
  info)     cmd_info ;;
  send)     cmd_send "$@" ;;
  events)   cmd_events ;;
  sessions) cmd_sessions ;;
  messages) cmd_messages "$@" ;;
  skills)   cmd_skills "$@" ;;
  memory)   cmd_memory "$@" ;;
  config)   cmd_config "$@" ;;
  help|--help|-h) cmd_help ;;
  *)        echo "Unknown command: ${"$"}COMMAND"; cmd_help; exit 1 ;;
esac
""".trimIndent()
}

private val DEFAULT_CONSOLE_HTML = """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>MobileClaw Console</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
:root{
  --bg:#faf9f7;--surface:#ffffff;--surface2:#f2f1ee;
  --border:#e7e4df;--border2:#d9d5ce;
  --ink:#171717;--ink2:#2b2b2b;
  --ok:#6f8f78;--red:#b84b4b;
  --text:#1b1b1a;--muted:#77736d;--dim:#b8b3aa;
  --r:16px
}
body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Inter','Segoe UI',system-ui,sans-serif;
  font-size:14px;line-height:1.5;height:100vh;display:flex;flex-direction:column;overflow:hidden}

/* TOP BAR */
#topbar{height:54px;background:rgba(255,255,255,.94);border-bottom:1px solid var(--border);
  display:flex;align-items:center;padding:0 18px;gap:10px;flex-shrink:0;z-index:10}
.logo{font-size:15px;font-weight:700;letter-spacing:0;color:var(--text)}
.logo em{color:var(--muted);font-style:normal;font-weight:600}
#sdot{width:7px;height:7px;border-radius:50%;background:var(--dim);transition:background .3s,box-shadow .3s;flex-shrink:0}
#sdot.on{background:var(--ok)}
#sdot.run{background:var(--ink);animation:pulse 1.2s ease-in-out infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
#stxt{font-size:12px;color:var(--muted)}
#url{margin-left:auto;background:var(--surface2);border:1px solid var(--border);border-radius:999px;
  padding:3px 10px;font-size:11px;font-family:'Cascadia Code','Fira Code',Consolas,monospace;
  color:var(--ink);cursor:pointer;user-select:all;transition:border-color .2s}
#url:hover{border-color:var(--ink)}

/* LAYOUT */
#main{display:flex;flex:1;overflow:hidden}

/* SIDEBAR */
#sidebar{width:282px;background:var(--surface);border-right:1px solid var(--border);
  display:flex;flex-direction:column;flex-shrink:0;overflow:hidden}
.sh{padding:12px 16px 8px;font-size:11px;font-weight:600;text-transform:uppercase;
  letter-spacing:.08em;color:var(--muted)}
#slist{flex:1;overflow-y:auto;padding:0 8px 8px}
#slist::-webkit-scrollbar{width:3px}
#slist::-webkit-scrollbar-thumb{background:var(--border2);border-radius:3px}
.si{padding:10px 12px;border-radius:14px;cursor:pointer;transition:background .15s;
  margin-bottom:2px;color:var(--muted)}
.si:hover{background:var(--bg);color:var(--text)}
.si.active{background:var(--surface2);color:var(--text)}
.si .t{font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:500}
.si .ts{font-size:11px;margin-top:1px;opacity:.6}
.codexbox{margin:8px;padding:14px;border:1px solid var(--border);border-radius:18px;background:var(--bg)}
.codexbox h3{font-size:13px;margin-bottom:6px;color:var(--text)}
.codexbox p{font-size:11px;color:var(--muted);line-height:1.5;margin-bottom:8px}
.codexcmd{font-family:'Cascadia Code','Fira Code',Consolas,monospace;font-size:11px;color:var(--text);
  background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:9px;
  word-break:break-all;cursor:pointer;margin-bottom:6px}
.codexcmd:hover{border-color:var(--ink)}
.codexhint{font-size:10px;color:var(--dim);line-height:1.4}

/* CHAT */
#cw{flex:1;display:flex;flex-direction:column;overflow:hidden;min-width:0}
#msgs{flex:1;overflow-y:auto;padding:26px 22px;display:flex;flex-direction:column;gap:14px}
#msgs::-webkit-scrollbar{width:4px}
#msgs::-webkit-scrollbar-thumb{background:var(--border2);border-radius:4px}

.mr{display:flex;flex-direction:column;max-width:min(760px,82%)}
.mr.u{align-self:flex-end;align-items:flex-end}
.mr.a{align-self:flex-start;align-items:flex-start}
.ml{font-size:11px;color:var(--muted);margin-bottom:4px;font-weight:500}
.mr.u .ml{color:var(--muted);opacity:.9}
.bbl{padding:10px 14px;border-radius:var(--r);line-height:1.65;word-break:break-word;white-space:pre-wrap;font-size:14px}
.mr.u .bbl{background:var(--ink);border:1px solid var(--ink);color:#fff;
  border-radius:var(--r) var(--r) 4px var(--r)}
.mr.a .bbl{background:var(--surface);border:1px solid var(--border);color:var(--text);
  border-radius:var(--r) var(--r) var(--r) 4px}
.mr.a .bbl.done{color:var(--text);font-family:inherit;font-size:14px}
.cur{display:inline-block;width:7px;height:.9em;background:var(--muted);
  vertical-align:text-bottom;margin-left:1px;animation:blink 1s step-end infinite}
@keyframes blink{50%{opacity:0}}
.skl{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;
  background:transparent;border:1px solid var(--border);border-radius:20px;
  color:var(--muted);font-size:12px;
  align-self:flex-start;margin-top:4px}
.sysmsg{align-self:center;font-size:12px;color:var(--dim);font-style:italic;padding:2px 0}

/* EMPTY STATE */
#empty{display:flex;flex-direction:column;align-items:center;justify-content:center;
  gap:8px;color:var(--muted);padding:40px 20px;text-align:center}
#empty .ico{display:none}
#empty .hint{font-size:13px;max-width:280px;color:var(--muted)}

/* INPUT */
#ia{border-top:1px solid var(--border);background:rgba(255,255,255,.94);padding:12px 16px;
  display:flex;gap:10px;align-items:flex-end;flex-shrink:0}
#iw{flex:1;background:var(--bg);border:1px solid var(--border);border-radius:20px;
  padding:8px 12px;display:flex;transition:border-color .2s}
#iw:focus-within{border-color:var(--ink)}
#inp{flex:1;background:transparent;border:none;outline:none;color:var(--text);
  font-family:inherit;font-size:14px;resize:none;min-height:20px;max-height:120px;line-height:1.5}
#inp::placeholder{color:var(--dim)}
#sbtn{width:40px;height:40px;border-radius:50%;background:var(--ink);border:none;
  cursor:pointer;display:flex;align-items:center;justify-content:center;
  transition:background .15s,opacity .15s;flex-shrink:0;color:#fff}
#sbtn:hover{background:var(--ink2)}
#sbtn:disabled{background:var(--dim);cursor:default;opacity:.5}

@media(max-width:640px){#sidebar{display:none}}
</style>
</head>
<body>
<div id="topbar">
  <div class="logo">Mobile<em>Claw</em></div>
  <div id="sdot"></div>
  <span id="stxt">Connecting…</span>
  <div id="url" title="Click to copy address">This page</div>
</div>
<div id="main">
  <div id="sidebar">
    <div class="sh">History</div>
    <div id="slist"><div class="sysmsg" style="margin:14px 8px">Loading…</div></div>
    <div class="codexbox">
      <h3>Codex 连接</h3>
      <p>电脑安装并登录 Codex 后，在手机端填写桥接地址和 Token。</p>
      <div class="codexcmd" onclick="copyCodexCmd(this)">npm install -g @openai/codex</div>
      <div class="codexcmd" onclick="copyText('codex --login',this)">codex --login</div>
      <div class="codexhint">点击命令可复制。安装完成后，回到手机端配置桥接。</div>
    </div>
  </div>
  <div id="cw">
    <div id="msgs">
      <div id="empty">
        <div class="ico"></div>
        <div class="hint">从这里发送一条任务。</div>
      </div>
    </div>
    <div id="ia">
      <div id="iw"><textarea id="inp" rows="1" placeholder="输入任务，Enter 发送" autocomplete="off" spellcheck="false"></textarea></div>
      <button id="sbtn" onclick="send()" title="Send">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 2L11 13"/><path d="M22 2L15 22 11 13 2 9z"/>
        </svg>
      </button>
    </div>
  </div>
</div>
<script>
var running=false,curSess=null,stBubble=null,stText='';
window.__CLAW_TOKEN__='__CLAW_TOKEN__';

function clawToken(){return (new URLSearchParams(location.search)).get('token')||window.__CLAW_TOKEN__||'';}
function apiUrl(path){
  var token=clawToken();
  return token ? path+(path.indexOf('?')>=0?'&':'?')+'token='+encodeURIComponent(token) : path;
}
function apiFetch(path,opts){
  var token=clawToken();
  var next=opts||{};
  next.headers=next.headers||{};
  if(token)next.headers['X-Claw-Token']=token;
  return fetch(apiUrl(path),next);
}

function copyText(text,el){
  navigator.clipboard&&navigator.clipboard.writeText(text);
  if(el){var old=el.textContent;el.textContent='Copied';setTimeout(function(){el.textContent=old;},1200);}
}
function copyCodexCmd(el){copyText('npm install -g @openai/codex',el);}

window.addEventListener('load',function(){
  loadSessions();
  connectSSE();
  document.getElementById('url').textContent=location.host;
  document.getElementById('url').onclick=function(){
    navigator.clipboard&&navigator.clipboard.writeText(location.href);
    this.textContent='Copied!';setTimeout(function(){document.getElementById('url').textContent=location.host;},1500);
  };
  var inp=document.getElementById('inp');
  inp.addEventListener('keydown',function(e){if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send();}});
  inp.addEventListener('input',function(){this.style.height='auto';this.style.height=Math.min(this.scrollHeight,120)+'px';});
});

function connectSSE(){
  var es=new EventSource(apiUrl('/api/events'));
  es.onopen=function(){dot('on','Connected');};
  es.onmessage=function(e){var m=JSON.parse(e.data);onEvt(m.type,m.d);};
  es.onerror=function(){dot('','Reconnecting…');setTimeout(connectSSE,3000);es.close();};
}

function onEvt(t,d){
  if(t==='connected'){dot('on','Connected');}
  else if(t==='task_started'){
    running=true;stBubble=null;stText='';
    document.getElementById('sbtn').disabled=true;
    dot('run','Running…');
    hideEmpty();
  }
  else if(t==='token'){appendTok(d);}
  else if(t==='skill_called'){addSkill(d);}
  else if(t==='task_completed'){finalizeStream(false);running=false;document.getElementById('sbtn').disabled=false;dot('on','Connected');refreshSess();}
  else if(t==='task_stopped'){finalizeStream(true);running=false;document.getElementById('sbtn').disabled=false;dot('on','Connected');refreshSess();}
}

function send(){
  var inp=document.getElementById('inp'),text=inp.value.trim();
  if(!text||running)return;
  inp.value='';inp.style.height='auto';
  hideEmpty();
  var row=mkRow('u','你');
  var bbl=document.createElement('div');bbl.className='bbl done';bbl.textContent=text;
  row.appendChild(bbl);scrollBot();
  apiFetch('/api/send',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:text})}).catch(function(){
    var err=mkRow('a','MobileClaw');
    var eb=document.createElement('div');eb.className='bbl done';eb.textContent='发送失败，请检查控制台服务连接。';
    err.appendChild(eb);scrollBot();
  });
}

function hideEmpty(){var e=document.getElementById('empty');if(e)e.remove();}

function mkRow(cls,label){
  var row=document.createElement('div');row.className='mr '+cls;
  var lbl=document.createElement('div');lbl.className='ml';lbl.textContent=label;
  row.appendChild(lbl);
  document.getElementById('msgs').appendChild(row);
  return row;
}

function appendTok(tok){
  if(!stBubble){
    var row=mkRow('a','MobileClaw');
    stBubble=document.createElement('div');stBubble.className='bbl';
    var c=document.createElement('span');c.className='cur';c.id='cur';
    stBubble.appendChild(c);row.appendChild(stBubble);
  }
  stText+=tok;
  var c=document.getElementById('cur');
  if(c){stBubble.textContent=stText;stBubble.appendChild(c);}else{stBubble.textContent=stText;}
  scrollBot();
}

function addSkill(desc){
  var el=document.createElement('div');el.className='skl';el.textContent=desc;
  document.getElementById('msgs').appendChild(el);scrollBot();
}

function finalizeStream(stopped){
  if(stBubble){
    var c=document.getElementById('cur');if(c)c.remove();
    stBubble.classList.add('done');
    if(stopped&&!stText)stBubble.textContent='已停止';
    stBubble=null;stText='';
  }
}

function loadSessions(){
  apiFetch('/api/sessions').then(function(r){return r.json();}).then(function(data){
    var list=document.getElementById('slist');list.innerHTML='';
    if(!data.sessions||!data.sessions.length){list.innerHTML='<div class="sysmsg" style="margin:14px 8px">No history</div>';return;}
    data.sessions.forEach(function(s){
      var el=document.createElement('div');el.className='si'+(s.id===curSess?' active':'');el.dataset.id=s.id;
      var d=new Date(s.updatedAt);
      var ts=d.toLocaleDateString(undefined,{month:'numeric',day:'numeric'})+' '+d.toLocaleTimeString(undefined,{hour:'2-digit',minute:'2-digit'});
      el.innerHTML='<div class="t">'+esc(s.title)+'</div><div class="ts">'+ts+'</div>';
      el.onclick=function(){selectSess(s.id);};
      list.appendChild(el);
    });
  }).catch(function(){});
}

function refreshSess(){loadSessions();if(curSess)loadMsgs(curSess);}

function selectSess(id){
  curSess=id;
  document.querySelectorAll('.si').forEach(function(el){el.classList.toggle('active',el.dataset.id===id);});
  loadMsgs(id);
}

function loadMsgs(sid){
  apiFetch('/api/messages?sessionId='+encodeURIComponent(sid)).then(function(r){return r.json();}).then(function(data){
    var msgs=document.getElementById('msgs');
    var existing=msgs.querySelectorAll('.mr,.skl,.sysmsg');existing.forEach(function(e){e.remove();});
    var empty=document.getElementById('empty');
    if(!data.messages||!data.messages.length){if(empty)empty.style.display='';return;}
    if(empty)empty.style.display='none';
    data.messages.forEach(function(m){
      var row=mkRow(m.role==='user'?'u':'a',m.role==='user'?'你':'MobileClaw');
      var bbl=document.createElement('div');bbl.className='bbl done';bbl.textContent=m.text;
      row.appendChild(bbl);
    });
    scrollBot();
  }).catch(function(){});
}

function dot(cls,text){
  var d=document.getElementById('sdot');d.className=cls;
  document.getElementById('stxt').textContent=text;
}

function scrollBot(){var m=document.getElementById('msgs');m.scrollTop=m.scrollHeight;}

function esc(s){return(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
</script>
</body>
</html>
"""

package com.mobileclaw.server

import android.os.Build
import com.google.gson.Gson
import com.mobileclaw.config.UserConfig
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
 *   POST /api/memory                         — set memory fact {key, value}
 *   GET  /api/config                         — all user config entries
 *   POST /api/config                         — set config entry {key, value, description?}
 *   DELETE /api/config/<key>                 — delete a config entry
 *   GET  /api/openclaw/cli.sh                — download OpenClaw bash CLI script
 */
class ConsoleServer(
    private val filesDir: File,
    private val database: ClawDatabase,
    val messageRequests: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 16),
    private val skillRegistry: SkillRegistry? = null,
    private val skillLoader: SkillLoader? = null,
    private val semanticMemory: SemanticMemory? = null,
    private val userConfig: UserConfig? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val sseClients = CopyOnWriteArrayList<SseClient>()
    private val gson = Gson()

    companion object {
        const val PORT = 52733
    }

    private data class SseClient(val output: OutputStream, val socket: Socket)

    fun start() {
        scope.launch {
            ensureDefaultHtml()
            runCatching {
                serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))
                while (!serverSocket!!.isClosed) {
                    val client = runCatching { serverSocket!!.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
    }

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
            runCatching {
                client.output.write(bytes)
                client.output.flush()
            }.onFailure { dead += client }
        }
        sseClients.removeAll(dead.toSet())
    }

    private fun getLanIp(): String = runCatching {
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
    }.getOrNull() ?: "127.0.0.1"

    private fun handleClient(socket: Socket) {
        runCatching {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val query = if ("?" in fullPath) fullPath.substringAfter("?") else ""

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().lowercase() == "content-length") {
                    contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                reader.read(buf, 0, contentLength)
                String(buf)
            } else ""

            // CORS preflight
            if (method == "OPTIONS") { sendCorsOk(socket); return }

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
        }
    }

    private fun handleSse(socket: Socket) {
        val output = socket.getOutputStream()
        runCatching {
            output.write((
                "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\nConnection: keep-alive\r\n\r\n" +
                "data: {\"type\":\"connected\"}\n\n"
            ).toByteArray(Charsets.UTF_8))
            output.flush()
        }.onFailure { runCatching { socket.close() }; return }

        val client = SseClient(output, socket)
        sseClients.add(client)
        runCatching {
            val input = socket.getInputStream()
            while (!socket.isClosed) {
                if (input.read() == -1) break
            }
        }
        sseClients.remove(client)
        runCatching { socket.close() }
    }

    private fun handleSend(socket: Socket, body: String) {
        val msg = runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(body, Map::class.java) as? Map<String, Any>)?.get("message") as? String
        }.getOrNull() ?: ""
        if (msg.isNotBlank()) scope.launch { messageRequests.emit(msg) }
        sendJson(socket, "200 OK", mapOf("ok" to true))
    }

    private fun handleSessions(socket: Socket) {
        val sessions = runCatching { runBlocking { database.sessionDao().recent(50) } }.getOrDefault(emptyList())
        sendJson(socket, "200 OK", mapOf("sessions" to sessions.map {
            mapOf("id" to it.id, "title" to it.title, "updatedAt" to it.updatedAt)
        }))
    }

    private fun handleMessages(socket: Socket, query: String) {
        val sessionId = query.split("&")
            .firstOrNull { it.startsWith("sessionId=") }?.substringAfter("sessionId=") ?: ""
        val msgs = if (sessionId.isNotBlank()) {
            runCatching { runBlocking { database.sessionMessageDao().forSession(sessionId) } }.getOrDefault(emptyList())
                .map { mapOf("role" to it.role, "text" to it.text, "createdAt" to it.createdAt) }
        } else emptyList()
        sendJson(socket, "200 OK", mapOf("messages" to msgs))
    }

    private fun handleIndex(socket: Socket) {
        runCatching {
            val html = getHtml()
            val bytes = html.toByteArray(Charsets.UTF_8)
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
            socket.close()
        }
    }

    private fun sendJson(socket: Socket, status: String, data: Any) {
        runCatching {
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
            "capabilities" to listOf("send", "sessions", "messages", "events", "skills", "memory", "config"),
            "cli_url" to "http://$ip:$PORT/api/openclaw/cli.sh",
        ))
    }

    private fun handleSkillsList(socket: Socket) {
        val skills = skillRegistry?.all()?.map { skill ->
            mapOf(
                "id" to skill.meta.id,
                "name" to skill.meta.name,
                "nameZh" to (skill.meta.nameZh ?: skill.meta.name),
                "description" to skill.meta.description,
                "type" to skill.meta.type.name.lowercase(),
                "injectionLevel" to skill.meta.injectionLevel,
                "isBuiltin" to skill.meta.isBuiltin,
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
        runCatching {
            val def = gson.fromJson(body, SkillDefinition::class.java)
            loader.persist(def)
            sendJson(socket, "200 OK", mapOf("success" to true, "id" to def.meta.id))
        }.onFailure { e ->
            sendJson(socket, "400 Bad Request", mapOf("error" to (e.message ?: "Invalid skill definition")))
        }
    }

    private fun handleSkillDelete(socket: Socket, id: String) {
        val loader = skillLoader ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Skill management not available")); return
        }
        loader.delete(id)
        sendJson(socket, "200 OK", mapOf("success" to true, "deleted" to id))
    }

    private fun handleMemoryGet(socket: Socket) {
        val facts = runBlocking { runCatching { semanticMemory?.all() }.getOrNull() ?: emptyMap<String, String>() }
        sendJson(socket, "200 OK", mapOf("memory" to facts, "total" to facts.size))
    }

    private fun handleMemorySet(socket: Socket, body: String) {
        val mem = semanticMemory ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Memory not available")); return
        }
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
            val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
            runBlocking { mem.set(key, value) }
            sendJson(socket, "200 OK", mapOf("success" to true, "key" to key))
        }.onFailure { e ->
            sendJson(socket, "400 Bad Request", mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    private fun handleConfigGet(socket: Socket) {
        val entries = runBlocking { runCatching { userConfig?.all() }.getOrNull() ?: emptyMap<String, Any>() }
        sendJson(socket, "200 OK", mapOf("config" to entries, "total" to entries.size))
    }

    private fun handleConfigSet(socket: Socket, body: String) {
        val cfg = userConfig ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Config not available")); return
        }
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val key = req["key"] as? String ?: throw IllegalArgumentException("key required")
            val value = req["value"] as? String ?: throw IllegalArgumentException("value required")
            val desc = req["description"] as? String ?: ""
            runBlocking { cfg.set(key, value, desc) }
            sendJson(socket, "200 OK", mapOf("success" to true, "key" to key))
        }.onFailure { e ->
            sendJson(socket, "400 Bad Request", mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    private fun handleConfigDelete(socket: Socket, key: String) {
        val cfg = userConfig ?: run {
            sendJson(socket, "503 Service Unavailable", mapOf("error" to "Config not available")); return
        }
        runBlocking { runCatching { cfg.delete(key) } }
        sendJson(socket, "200 OK", mapOf("success" to true, "deleted" to key))
    }

    private fun handleCliScript(socket: Socket) {
        val ip = getLanIp()
        val script = buildOpenClawCliScript(ip)
        val bytes = script.toByteArray(Charsets.UTF_8)
        runCatching {
            val out = socket.getOutputStream()
            out.write((
                "HTTP/1.1 200 OK\r\nContent-Type: text/x-shellscript; charset=utf-8\r\n" +
                "Content-Disposition: attachment; filename=\"openclaw.sh\"\r\n" +
                "Content-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
            ).toByteArray())
            out.write(bytes)
            out.flush()
            socket.close()
        }
    }

    private fun sendCorsOk(socket: Socket) {
        runCatching {
            socket.use {
                socket.getOutputStream().write((
                    "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray())
            }
        }
    }

    private fun ensureDefaultHtml() {
        val dir = File(filesDir, "console_web")
        dir.mkdirs()
        val file = File(dir, "index.html")
        if (!file.exists()) file.writeText(DEFAULT_CONSOLE_HTML)
    }

    private fun getHtml(): String {
        val file = File(filesDir, "console_web/index.html")
        return if (file.exists()) file.readText() else DEFAULT_CONSOLE_HTML
    }

    private fun buildOpenClawCliScript(ip: String): String = """
#!/usr/bin/env bash
# openclaw — CLI bridge to MobileClaw
# Usage: openclaw <command> [args]
# Download: curl -o openclaw.sh http://$ip:$PORT/api/openclaw/cli.sh && chmod +x openclaw.sh
# Install:  sudo cp openclaw.sh /usr/local/bin/openclaw

CLAW_HOST="${"$"}{CLAW_HOST:-http://$ip:$PORT}"

_require_curl() { command -v curl >/dev/null 2>&1 || { echo "error: curl is required"; exit 1; }; }

_get()  { _require_curl; curl -sf "${"$"}{CLAW_HOST}$1"; }
_post() { _require_curl; curl -sf -X POST -H "Content-Type: application/json" -d "$2" "${"$"}{CLAW_HOST}$1"; }
_del()  { _require_curl; curl -sf -X DELETE "${"$"}{CLAW_HOST}$1"; }
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
  openclaw send "搜索今天的科技新闻"
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
  --bg:#0a0a0b;--surface:#111113;--surface2:#18181b;
  --border:#1e1e22;--border2:#2a2a30;
  --accent:#a78bfa;--accent-bg:#1a0e2e;--accent-border:#4c1d95;
  --green:#86efac;--teal:#34d399;--red:#f87171;--yellow:#fbbf24;
  --text:#e4e4e7;--muted:#71717a;--dim:#3f3f46;
  --skill-bg:#052e16;--skill-border:#166534;--skill-text:#4ade80;
  --r:12px
}
body{background:var(--bg);color:var(--text);font-family:-apple-system,'Inter','Segoe UI',system-ui,sans-serif;
  font-size:14px;line-height:1.5;height:100vh;display:flex;flex-direction:column;overflow:hidden}

/* TOP BAR */
#topbar{height:48px;background:var(--surface);border-bottom:1px solid var(--border);
  display:flex;align-items:center;padding:0 16px;gap:10px;flex-shrink:0;z-index:10}
.logo{font-size:15px;font-weight:700;letter-spacing:-.3px;color:var(--text)}
.logo em{color:var(--accent);font-style:normal}
#sdot{width:7px;height:7px;border-radius:50%;background:var(--dim);transition:background .3s,box-shadow .3s;flex-shrink:0}
#sdot.on{background:var(--green);box-shadow:0 0 6px var(--green)}
#sdot.run{background:var(--accent);box-shadow:0 0 8px var(--accent);animation:pulse 1.2s ease-in-out infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
#stxt{font-size:12px;color:var(--muted)}
#url{margin-left:auto;background:var(--bg);border:1px solid var(--border2);border-radius:6px;
  padding:3px 10px;font-size:11px;font-family:'Cascadia Code','Fira Code',Consolas,monospace;
  color:var(--accent);cursor:pointer;user-select:all;transition:border-color .2s}
#url:hover{border-color:var(--accent)}

/* LAYOUT */
#main{display:flex;flex:1;overflow:hidden}

/* SIDEBAR */
#sidebar{width:256px;background:var(--surface);border-right:1px solid var(--border);
  display:flex;flex-direction:column;flex-shrink:0;overflow:hidden}
.sh{padding:12px 16px 8px;font-size:11px;font-weight:600;text-transform:uppercase;
  letter-spacing:.08em;color:var(--muted)}
#slist{flex:1;overflow-y:auto;padding:0 8px 8px}
#slist::-webkit-scrollbar{width:3px}
#slist::-webkit-scrollbar-thumb{background:var(--border2);border-radius:3px}
.si{padding:8px 10px;border-radius:8px;cursor:pointer;transition:background .15s;
  margin-bottom:2px;color:var(--muted)}
.si:hover{background:var(--bg);color:var(--text)}
.si.active{background:var(--accent-bg);color:var(--accent)}
.si .t{font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:500}
.si .ts{font-size:11px;margin-top:1px;opacity:.6}

/* CHAT */
#cw{flex:1;display:flex;flex-direction:column;overflow:hidden;min-width:0}
#msgs{flex:1;overflow-y:auto;padding:24px 20px;display:flex;flex-direction:column;gap:14px}
#msgs::-webkit-scrollbar{width:4px}
#msgs::-webkit-scrollbar-thumb{background:var(--border2);border-radius:4px}

.mr{display:flex;flex-direction:column;max-width:82%}
.mr.u{align-self:flex-end;align-items:flex-end}
.mr.a{align-self:flex-start;align-items:flex-start}
.ml{font-size:11px;color:var(--muted);margin-bottom:4px;font-weight:500}
.mr.u .ml{color:var(--accent);opacity:.8}
.bbl{padding:10px 14px;border-radius:var(--r);line-height:1.65;word-break:break-word;white-space:pre-wrap;font-size:14px}
.mr.u .bbl{background:var(--accent-bg);border:1px solid var(--accent-border);color:#c4b5fd;
  border-radius:var(--r) var(--r) 4px var(--r)}
.mr.a .bbl{background:transparent;border:1px solid var(--border);color:var(--green);
  font-family:'Cascadia Code','Fira Code',Consolas,monospace;font-size:13px;
  border-radius:var(--r) var(--r) var(--r) 4px}
.mr.a .bbl.done{color:var(--text);font-family:inherit;font-size:14px}
.cur{display:inline-block;width:8px;height:.9em;background:var(--green);
  vertical-align:text-bottom;margin-left:1px;animation:blink 1s step-end infinite}
@keyframes blink{50%{opacity:0}}
.skl{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;
  background:var(--skill-bg);border:1px solid var(--skill-border);border-radius:20px;
  color:var(--skill-text);font-size:12px;font-family:'Cascadia Code','Fira Code',monospace;
  align-self:flex-start;margin-top:4px}
.sysmsg{align-self:center;font-size:12px;color:var(--dim);font-style:italic;padding:2px 0}

/* EMPTY STATE */
#empty{display:flex;flex-direction:column;align-items:center;justify-content:center;
  gap:10px;color:var(--muted);padding:40px 20px;text-align:center}
#empty .ico{font-size:48px;opacity:.35}
#empty .hint{font-size:13px;max-width:280px}

/* INPUT */
#ia{border-top:1px solid var(--border);background:var(--surface);padding:10px 14px;
  display:flex;gap:10px;align-items:flex-end;flex-shrink:0}
#iw{flex:1;background:var(--bg);border:1px solid var(--border2);border-radius:10px;
  padding:8px 12px;display:flex;transition:border-color .2s}
#iw:focus-within{border-color:var(--accent)}
#inp{flex:1;background:transparent;border:none;outline:none;color:var(--text);
  font-family:inherit;font-size:14px;resize:none;min-height:20px;max-height:120px;line-height:1.5}
#inp::placeholder{color:var(--dim)}
#sbtn{width:36px;height:36px;border-radius:9px;background:var(--accent);border:none;
  cursor:pointer;display:flex;align-items:center;justify-content:center;
  transition:background .15s,opacity .15s;flex-shrink:0;color:#fff}
#sbtn:hover{background:#c4b5fd}
#sbtn:disabled{background:var(--dim);cursor:default;opacity:.5}

@media(max-width:640px){#sidebar{display:none}}
</style>
</head>
<body>
<div id="topbar">
  <div class="logo">Mobile<em>Claw</em></div>
  <div id="sdot"></div>
  <span id="stxt">连接中…</span>
  <div id="url" title="点击复制地址">当前页</div>
</div>
<div id="main">
  <div id="sidebar">
    <div class="sh">历史会话</div>
    <div id="slist"><div class="sysmsg" style="margin:14px 8px">加载中…</div></div>
  </div>
  <div id="cw">
    <div id="msgs">
      <div id="empty">
        <div class="ico">🦀</div>
        <div class="hint">在下方输入任务，MobileClaw 将在设备上执行</div>
      </div>
    </div>
    <div id="ia">
      <div id="iw"><textarea id="inp" rows="1" placeholder="输入任务… (Enter 发送，Shift+Enter 换行)" autocomplete="off" spellcheck="false"></textarea></div>
      <button id="sbtn" onclick="send()" title="发送">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 2L11 13"/><path d="M22 2L15 22 11 13 2 9z"/>
        </svg>
      </button>
    </div>
  </div>
</div>
<script>
var running=false,curSess=null,stBubble=null,stText='';

window.addEventListener('load',function(){
  loadSessions();
  connectSSE();
  document.getElementById('url').textContent=location.host;
  document.getElementById('url').onclick=function(){
    navigator.clipboard&&navigator.clipboard.writeText(location.href);
    this.textContent='已复制!';setTimeout(function(){document.getElementById('url').textContent=location.host;},1500);
  };
  var inp=document.getElementById('inp');
  inp.addEventListener('keydown',function(e){if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send();}});
  inp.addEventListener('input',function(){this.style.height='auto';this.style.height=Math.min(this.scrollHeight,120)+'px';});
});

function connectSSE(){
  var es=new EventSource('/api/events');
  es.onopen=function(){dot('on','已连接');};
  es.onmessage=function(e){var m=JSON.parse(e.data);onEvt(m.type,m.d);};
  es.onerror=function(){dot('','重连中…');setTimeout(connectSSE,3000);es.close();};
}

function onEvt(t,d){
  if(t==='connected'){dot('on','已连接');}
  else if(t==='task_started'){
    running=true;stBubble=null;stText='';
    document.getElementById('sbtn').disabled=true;
    dot('run','执行中…');
    hideEmpty();
  }
  else if(t==='token'){appendTok(d);}
  else if(t==='skill_called'){addSkill(d);}
  else if(t==='task_completed'){finalizeStream(false);running=false;document.getElementById('sbtn').disabled=false;dot('on','已连接');refreshSess();}
  else if(t==='task_stopped'){finalizeStream(true);running=false;document.getElementById('sbtn').disabled=false;dot('on','已连接');refreshSess();}
}

function send(){
  var inp=document.getElementById('inp'),text=inp.value.trim();
  if(!text||running)return;
  inp.value='';inp.style.height='auto';
  fetch('/api/send',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:text})}).catch(function(){});
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
  var el=document.createElement('div');el.className='skl';el.textContent='⚡ '+desc;
  document.getElementById('msgs').appendChild(el);scrollBot();
}

function finalizeStream(stopped){
  if(stBubble){
    var c=document.getElementById('cur');if(c)c.remove();
    stBubble.classList.add('done');
    if(stopped&&!stText)stBubble.textContent='⚠️ 已停止';
    stBubble=null;stText='';
  }
}

function loadSessions(){
  fetch('/api/sessions').then(function(r){return r.json();}).then(function(data){
    var list=document.getElementById('slist');list.innerHTML='';
    if(!data.sessions||!data.sessions.length){list.innerHTML='<div class="sysmsg" style="margin:14px 8px">暂无历史</div>';return;}
    data.sessions.forEach(function(s){
      var el=document.createElement('div');el.className='si'+(s.id===curSess?' active':'');el.dataset.id=s.id;
      var d=new Date(s.updatedAt);
      var ts=d.toLocaleDateString('zh',{month:'numeric',day:'numeric'})+' '+d.toLocaleTimeString('zh',{hour:'2-digit',minute:'2-digit'});
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
  fetch('/api/messages?sessionId='+encodeURIComponent(sid)).then(function(r){return r.json();}).then(function(data){
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

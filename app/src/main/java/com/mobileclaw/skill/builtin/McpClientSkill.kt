package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.mcp.McpEndpointConfig
import com.mobileclaw.mcp.McpHttpClient
import com.mobileclaw.mcp.McpToolCallResult
import com.mobileclaw.mcp.McpToolList
import com.mobileclaw.skill.McpSkillConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillToolTaxonomy
import com.mobileclaw.skill.SkillType

/**
 * Standard MCP client bridge for HTTP/Streamable HTTP MCP servers.
 */
class McpClientSkill : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val client = McpHttpClient()

    override val meta = SkillMeta(
        id = "mcp_client",
        name = "MCP Client",
        description = "Connect to a standard MCP HTTP endpoint, list server tools, and call a tool. " +
            "Supports initialize, tools/list, and tools/call over JSON-RPC 2.0 Streamable HTTP or SSE endpoints. " +
            "Pass custom auth headers via headers_json when needed.",
        parameters = listOf(
            SkillParam("endpoint", "string", "MCP HTTP/SSE endpoint or copied MCP config JSON. Examples: https://example.com/mcp, https://example.com/sse, or {\"mcpServers\":{...}}"),
            SkillParam("action", "string", "'initialize' | 'list_tools' | 'call_tool'"),
            SkillParam("headers_json", "string", "Optional JSON object of HTTP headers, e.g. {\"X-Goog-Api-Key\":\"...\"}", required = false),
            SkillParam("tool", "string", "Tool name for action=call_tool", required = false),
            SkillParam("arguments_json", "string", "JSON object arguments for action=call_tool", required = false),
            SkillParam("cursor", "string", "Optional pagination cursor for action=list_tools", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "MCP 客户端",
        descriptionZh = "连接标准 MCP HTTP/SSE 服务，列出工具并调用工具。",
        categories = listOf(SkillToolCategory.SKILL, SkillToolCategory.SYSTEM),
        tags = listOf("MCP", "工具"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val endpointInput = (params["endpoint"] as? String)?.trim().orEmpty()
        val endpointConfig = McpEndpointConfig.parse(endpointInput)
            ?: return SkillResult(false, "endpoint must be a URL or a supported MCP config JSON object")
        val endpoint = endpointConfig.endpoint
        val action = (params["action"] as? String)?.trim()?.lowercase()
            ?: return SkillResult(false, "action is required: initialize | list_tools | call_tool")
        val headers = parseHeaders(params["headers_json"] as? String, endpointConfig.headers)
            ?: return SkillResult(false, "headers_json must be a JSON object when provided")

        return runCatching {
            when (action) {
                "initialize" -> {
                    val session = client.initialize(endpoint, headers, force = true)
                    val server = session.serverInfo?.let { gson.toJson(it) } ?: "{}"
                    SkillResult(
                        success = true,
                        output = buildString {
                            appendLine("MCP initialized.")
                            appendLine("Protocol: ${session.protocolVersion}")
                            appendLine("Session: ${session.sessionId ?: "stateless"}")
                            appendLine("Server: $server")
                        },
                        data = session,
                    )
                }
                "list_tools", "tools/list", "list" -> {
                    val cursor = params["cursor"] as? String
                    val tools = client.listTools(endpoint, headers, cursor)
                    SkillResult(true, formatToolList(tools), data = tools)
                }
                "call_tool", "tools/call", "call" -> {
                    val tool = (params["tool"] as? String)?.trim()
                        ?: return SkillResult(false, "tool is required for call_tool")
                    val args = parseArguments(params["arguments_json"] as? String)
                        ?: return SkillResult(false, "arguments_json must be a JSON object when provided")
                    val result = client.callTool(endpoint, headers, tool, args)
                    SkillResult(
                        success = !result.isError,
                        output = formatToolCall(result),
                        data = result.raw,
                        imageBase64 = firstImageBase64(result),
                    )
                }
                else -> SkillResult(false, "Unknown action: $action. Use initialize, list_tools, or call_tool.")
            }
        }.getOrElse { error ->
            SkillResult(false, "MCP request failed: ${error.message}")
        }
    }

    private fun parseHeaders(json: String?, baseHeaders: Map<String, String>): Map<String, String>? =
        if (json.isNullOrBlank()) {
            baseHeaders
        } else {
            val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
            baseHeaders + obj.entrySet().associate { (key, value) -> key to value.asString }
        }

    private fun parseArguments(json: String?): JsonObject? {
        if (json.isNullOrBlank()) return JsonObject()
        return runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
    }

    private fun formatToolList(list: McpToolList): String = buildString {
        appendLine("MCP tools (${list.tools.size}):")
        list.tools.forEach { tool ->
            appendLine("- **${tool.name}**${tool.title?.let { " ($it)" } ?: ""}")
            if (!tool.description.isNullOrBlank()) appendLine("  ${tool.description}")
            tool.inputSchema?.let { appendLine("  inputSchema: ${gson.toJson(it).take(800)}") }
        }
        if (!list.nextCursor.isNullOrBlank()) appendLine("\nnextCursor: ${list.nextCursor}")
    }

    private fun formatToolCall(result: McpToolCallResult): String = buildString {
        if (result.isError) appendLine("MCP tool returned isError=true\n")
        val textParts = result.content.mapNotNull { content ->
            when (content["type"]?.asString) {
                "text" -> content["text"]?.asString
                "resource" -> content["resource"]?.let { gson.toJson(it) }
                else -> null
            }
        }
        if (textParts.isNotEmpty()) {
            append(textParts.joinToString("\n\n"))
        } else {
            append(gson.toJson(result.raw))
        }
        result.structuredContent?.takeIf { !it.isJsonNull }?.let {
            appendLine()
            appendLine("\nstructuredContent:")
            appendLine(gson.toJson(it))
        }
    }

    private fun firstImageBase64(result: McpToolCallResult): String? =
        result.content.firstNotNullOfOrNull { content ->
            if (content["type"]?.asString == "image") {
                content["data"]?.asString ?: content["image"]?.asJsonObjectOrNull()?.get("data")?.asString
            } else {
                null
            }
        }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null
}

/**
 * Installs tools from a remote MCP endpoint as reusable MobileClaw skills.
 *
 * This is the autonomous onboarding path: once the user provides an MCP
 * endpoint/config, the agent can discover tools and persist them without
 * hand-authoring one skill per tool.
 */
class McpConnectSkill(
    private val loader: SkillLoader,
) : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val client = McpHttpClient()

    override val meta = SkillMeta(
        id = "mcp_connect",
        name = "Connect MCP Server",
        description = "Discover tools from a remote MCP HTTP/SSE endpoint or copied mcpServers JSON, then install each tool as a reusable MobileClaw skill. " +
            "Use this when the user wants to connect, import, or add an MCP server.",
        parameters = listOf(
            SkillParam("endpoint", "string", "MCP HTTP/SSE endpoint or copied MCP config JSON"),
            SkillParam("headers_json", "string", "Optional JSON object of HTTP headers", required = false),
            SkillParam("action", "string", "'install' to persist skills, or 'discover' to preview tools (default: install)", required = false),
            SkillParam("prefix", "string", "Optional skill id prefix for installed tools", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "接入 MCP 服务",
        descriptionZh = "发现远程 MCP 服务的工具，并自动安装成可复用的 MobileClaw 技能。",
        categories = listOf(SkillToolCategory.SKILL, SkillToolCategory.SYSTEM),
        tags = listOf("MCP", "工具"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val endpointInput = (params["endpoint"] as? String)?.trim().orEmpty()
        val endpointConfig = McpEndpointConfig.parse(endpointInput)
            ?: return SkillResult(false, "endpoint must be a URL or a supported MCP config JSON object")
        val headers = parseHeaders(params["headers_json"] as? String, endpointConfig.headers)
            ?: return SkillResult(false, "headers_json must be a JSON object when provided")
        val action = (params["action"] as? String)?.trim()?.lowercase().orEmpty().ifBlank { "install" }
        val prefix = (params["prefix"] as? String)?.trim().orEmpty()

        return runCatching {
            val tools = client.listTools(endpointConfig.endpoint, headers).tools
            if (tools.isEmpty()) return SkillResult(true, "MCP server connected, but it returned no tools.")
            val defs = tools.map { tool ->
                buildSkillDefinition(endpointConfig.endpoint, headers, tool, prefix)
            }
            if (action in setOf("discover", "preview", "list")) {
                return SkillResult(
                    true,
                    buildString {
                        appendLine("MCP tools discovered (${defs.size}):")
                        defs.forEach { def ->
                            appendLine("- ${def.meta.id}: ${def.meta.nameZh ?: def.meta.name}")
                            appendLine("  ${def.meta.descriptionZh ?: def.meta.description}")
                        }
                        appendLine()
                        appendLine("Install all: mcp_connect(action=install, endpoint=..., headers_json=...)")
                    },
                    data = defs.map { it.meta },
                )
            }
            if (action != "install") {
                return SkillResult(false, "Unknown action: $action. Use install or discover.")
            }
            val installed = mutableListOf<String>()
            defs.forEach { def ->
                loader.persist(def)
                installed += def.meta.id
            }
            SkillResult(
                true,
                buildString {
                    appendLine("Installed ${installed.size} MCP tools from ${endpointConfig.endpoint}:")
                    installed.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("They are saved as on-demand skills. Promote frequently used ones if you want task-aware injection.")
                },
                data = installed,
            )
        }.getOrElse { error ->
            SkillResult(false, "MCP connect failed: ${error.message}")
        }
    }

    private fun buildSkillDefinition(
        endpoint: String,
        headers: Map<String, String>,
        tool: com.mobileclaw.mcp.McpTool,
        prefix: String,
    ): SkillDefinition {
        val idPrefix = prefix.ifBlank { "mcp_${endpoint.hostSeed()}" }
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
            .trim('_')
            .ifBlank { "mcp_tool" }
        val safeTool = tool.name
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
            .trim('_')
            .ifBlank { "tool" }
        val id = "${idPrefix}_${safeTool}".take(64).trim('_')
        val parameters = tool.inputSchema?.getAsJsonObject("properties")
            ?.entrySet()
            ?.map { (key, value) ->
                val obj = value.takeIf { it.isJsonObject }?.asJsonObject
                val type = obj?.get("type")?.asString
                    ?.takeIf { it in setOf("string", "number", "boolean", "object", "array") }
                    ?: "string"
                val desc = obj?.get("description")?.asString ?: "MCP parameter"
                val required = tool.inputSchema
                    ?.takeIf { it.has("required") && it["required"].isJsonArray }
                    ?.getAsJsonArray("required")
                    ?.any { it.asString == key } == true
                SkillParam(key, type, desc, required = required)
            }
            .orEmpty()
        val title = tool.title?.takeIf { it.isNotBlank() } ?: tool.name
        val desc = tool.description?.takeIf { it.isNotBlank() } ?: "MCP tool: ${tool.name}"
        val meta = SkillMeta(
            id = id,
            name = title,
            nameZh = title,
            description = desc,
            descriptionZh = desc,
            parameters = parameters,
            type = SkillType.MCP,
            injectionLevel = 2,
            isBuiltin = false,
            tags = listOf("MCP", "工具"),
        )
        return SkillDefinition(
            meta = meta.copy(categories = SkillToolTaxonomy.categoriesFor(meta).toList()),
            mcpConfig = McpSkillConfig(
                endpoint = endpoint,
                tool = tool.name,
                headers = headers,
            ),
        )
    }

    private fun parseHeaders(json: String?, baseHeaders: Map<String, String>): Map<String, String>? =
        if (json.isNullOrBlank()) {
            baseHeaders
        } else {
            val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
            baseHeaders + obj.entrySet().associate { (key, value) -> key to value.asString }
        }

    private fun String.hostSeed(): String =
        removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
            .replace(".", "_")
            .ifBlank { hashCode().toString() }
}

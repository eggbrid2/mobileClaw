package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.mcp.McpEndpointConfig
import com.mobileclaw.mcp.McpHttpClient
import com.mobileclaw.mcp.McpToolCallResult
import com.mobileclaw.mcp.McpToolList
import com.mobileclaw.mcp.ModelScopeMcpClient
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
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
            "Supports initialize, tools/list, and tools/call over JSON-RPC 2.0 Streamable HTTP or SSE endpoints, including ModelScope hosted MCP. " +
            "Pass custom auth headers via headers_json or modelscope_token when needed.",
        parameters = listOf(
            SkillParam("endpoint", "string", "MCP HTTP/SSE endpoint or copied MCP config JSON. Examples: https://example.com/mcp, https://mcp.api-inference.modelscope.net/xxx/sse, or {\"mcpServers\":{...}}", required = false),
            SkillParam("modelscope_server_id", "string", "Optional ModelScope MCP server id such as @modelcontextprotocol/fetch. When provided with modelscope_token, MobileClaw deploys/refreshes the SSE endpoint automatically.", required = false),
            SkillParam("action", "string", "'initialize' | 'list_tools' | 'call_tool'"),
            SkillParam("headers_json", "string", "Optional JSON object of HTTP headers, e.g. {\"X-Goog-Api-Key\":\"...\"}", required = false),
            SkillParam("modelscope_token", "string", "Optional ModelScope token. Sent as Authorization: Bearer <token> when Authorization is not already provided.", required = false),
            SkillParam("tool", "string", "Tool name for action=call_tool", required = false),
            SkillParam("arguments_json", "string", "JSON object arguments for action=call_tool", required = false),
            SkillParam("cursor", "string", "Optional pagination cursor for action=list_tools", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "MCP 客户端",
        descriptionZh = "连接标准 MCP HTTP/SSE 服务，列出工具并调用工具，支持 ModelScope 托管 MCP。",
        categories = listOf(SkillToolCategory.SKILL, SkillToolCategory.SYSTEM),
        tags = listOf("MCP", "工具"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val endpointInput = (params["endpoint"] as? String)?.trim().orEmpty()
        val modelscopeServerId = (params["modelscope_server_id"] as? String)?.trim().orEmpty()
        val modelscopeToken = (params["modelscope_token"] as? String)?.trim().orEmpty()
        val endpointConfig = if (endpointInput.isNotBlank()) {
            McpEndpointConfig.parse(endpointInput)
                ?: return SkillResult(false, "endpoint must be a URL or a supported MCP config JSON object")
        } else {
            if (modelscopeServerId.isBlank()) return SkillResult(false, "endpoint or modelscope_server_id is required")
            if (modelscopeToken.isBlank()) return SkillResult(false, "modelscope_token is required when using modelscope_server_id")
            val endpoint = runCatching {
                ModelScopeMcpClient().deployAndGetEndpoint(modelscopeServerId, modelscopeToken).endpoint
            }.getOrElse { error ->
                return SkillResult(false, "ModelScope MCP deploy failed: ${error.message}")
            }
            McpEndpointConfig(endpoint)
        }
        val endpoint = endpointConfig.endpoint
        val action = (params["action"] as? String)?.trim()?.lowercase()
            ?: return SkillResult(false, "action is required: initialize | list_tools | call_tool")
        val headers = parseHeaders(params["headers_json"] as? String, modelscopeToken, endpointConfig.headers)
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

    private fun parseHeaders(json: String?, modelscopeToken: String?, baseHeaders: Map<String, String>): Map<String, String>? {
        val parsed = if (json.isNullOrBlank()) {
            baseHeaders
        } else {
            val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
            baseHeaders + obj.entrySet().associate { (key, value) -> key to value.asString }
        }
        val token = modelscopeToken?.trim().orEmpty()
        if (token.isBlank() || parsed.keys.any { it.equals("Authorization", ignoreCase = true) }) return parsed
        return parsed + ("Authorization" to "Bearer $token")
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

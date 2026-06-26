package com.mobileclaw.skill.executor

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mobileclaw.mcp.McpHttpClient
import com.mobileclaw.mcp.McpToolCallResult
import com.mobileclaw.mcp.ModelScopeMcpClient
import com.mobileclaw.skill.McpSkillConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillResult

class McpSkillExecutor(
    override val meta: SkillMeta,
    private val config: McpSkillConfig,
) : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val client = McpHttpClient()

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val arguments = JsonObject()
        config.defaultArguments.forEach { (key, value) ->
            if (key.isNotBlank()) arguments.addProperty(key, value)
        }
        params.forEach { (key, value) ->
            if (key.isBlank()) return@forEach
            arguments.add(key, value.toJsonElement())
        }
        val headers = resolvedHeaders()
        return runCatching {
            callConfiguredEndpoint(config.endpoint, headers, arguments)
        }.recoverCatching {
            val refreshedEndpoint = refreshModelScopeEndpoint() ?: throw it
            callConfiguredEndpoint(refreshedEndpoint, headers, arguments)
        }.getOrElse { error ->
            SkillResult(false, "MCP skill failed: ${error.message}")
        }
    }

    private suspend fun callConfiguredEndpoint(
        endpoint: String,
        headers: Map<String, String>,
        arguments: JsonObject,
    ): SkillResult {
        val result = client.callTool(
            endpoint = endpoint,
            headers = headers,
            toolName = config.tool,
            arguments = arguments,
        )
        return SkillResult(
            success = !result.isError,
            output = formatToolCall(result).take(4000),
            data = result.raw,
            imageBase64 = firstImageBase64(result),
        )
    }

    private suspend fun refreshModelScopeEndpoint(): String? {
        val serverId = config.modelscopeServerId.trim()
        val token = config.modelscopeToken.trim()
        if (serverId.isBlank() || token.isBlank()) return null
        return runCatching {
            ModelScopeMcpClient().deployAndGetEndpoint(serverId, token).endpoint
        }.getOrNull()
    }

    private fun resolvedHeaders(): Map<String, String> {
        val token = config.modelscopeToken.trim()
        if (token.isBlank() || config.headers.keys.any { it.equals("Authorization", ignoreCase = true) }) {
            return config.headers
        }
        return config.headers + ("Authorization" to "Bearer $token")
    }

    private fun Any.toJsonElement(): JsonElement =
        gson.toJsonTree(this)

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
                content["data"]?.asString ?: content["image"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.asString
            } else {
                null
            }
        }
}

package com.mobileclaw.agent

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillMeta

data class ToolSelectionInput(
    val goal: String,
    val taskType: TaskType,
    val primaryChannel: ChannelType?,
    val roleSummary: String,
    val contextSummary: String,
    val preferredToolIds: List<String> = emptyList(),
    val blockedToolIds: List<String> = emptyList(),
    val routeToolHints: List<String> = emptyList(),
    val availableSkills: List<SkillMeta>,
)

data class ToolSelectionResult(
    val selectedToolIds: List<String>,
    val reason: String,
    val executionPlan: List<String>,
    val confidence: Float,
)

class AiToolSelector(
    private val llm: LlmGateway,
) {
    suspend fun select(input: ToolSelectionInput): ToolSelectionResult? {
        val knownIds = input.availableSkills.map { it.id }.toSet()
        val raw = try {
            llm.chat(
                ChatRequest(
                    messages = listOf(
                        Message(
                            role = "system",
                            content = "You are MobileClaw's tool selector. Select only the tools needed for this turn. Return one strict JSON object only.",
                        ),
                        Message(role = "user", content = buildPrompt(input)),
                    ),
                    tools = emptyList(),
                    stream = false,
                )
            ).content.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Tool selection failed for taskType=${input.taskType} goal=${input.goal.take(160)}", t)
            return null
        }
        val parsed = parse(raw, knownIds) ?: return null
        val blocked = input.blockedToolIds.toSet()
        val selected = parsed.selectedToolIds
            .filter { it in knownIds }
            .filterNot { it in blocked }
            .distinct()
            .take(MAX_SELECTED_TOOLS)
        return parsed.copy(selectedToolIds = selected)
    }

    private fun buildPrompt(input: ToolSelectionInput): String = """
Select the smallest useful MobileClaw tool set for the current execution goal.

Goal:
${input.goal.take(1200)}

Task type: ${input.taskType}
Primary channel: ${input.primaryChannel ?: "unspecified"}

Role summary:
${input.roleSummary.take(900)}

Context summary:
${input.contextSummary.take(1500)}

Preferred tool ids from role/route:
${(input.preferredToolIds + input.routeToolHints).distinct().joinToString(", ").ifBlank { "none" }}

Blocked tool ids:
${input.blockedToolIds.joinToString(", ").ifBlank { "none" }}

Available tool directory:
${toolDirectory(input.availableSkills)}

Rules:
- This is only a tool selection step. Do not execute tools and do not answer the user.
- Select tools by semantic fit, not keywords.
- Prefer a small set, usually 1-6 tools. Include dependencies needed to complete the job.
- If the role preferred tools are relevant, include them. If they are irrelevant, ignore them.
- Do not select blocked tools.
- Return an empty selected_tool_ids array only when no tool should be injected and a direct model answer is enough.

Return JSON only:
{
  "selected_tool_ids": ["read_file", "create_file"],
  "reason": "short operational reason",
  "execution_plan": ["read the current file", "patch the relevant content", "verify the result"],
  "confidence": 0.82
}
""".trimIndent()

    private fun toolDirectory(skills: List<SkillMeta>): String =
        skills
            .filterNot { it.internalTool }
            .sortedWith(compareBy<SkillMeta> { it.injectionLevel }.thenBy { it.id })
            .take(MAX_DIRECTORY_TOOLS)
            .joinToString("\n") { skill ->
                val categories = skill.categories.joinToString(",") { it.name.lowercase() }
                "- ${skill.id}: ${skill.nameZh ?: skill.name}; type=${skill.type}; categories=${categories.ifBlank { "none" }}; ${skill.descriptionZh ?: skill.description}".take(260)
            }

    private fun parse(raw: String, knownIds: Set<String>): ToolSelectionResult? {
        val jsonText = raw.extractJsonObjectBlock() ?: return null
        val obj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return null
        val selected = obj.stringList("selected_tool_ids")
            .filter { it in knownIds }
            .distinct()
            .take(MAX_SELECTED_TOOLS)
        return ToolSelectionResult(
            selectedToolIds = selected,
            reason = obj.string("reason"),
            executionPlan = obj.stringList("execution_plan").take(8),
            confidence = obj.float("confidence").coerceIn(0f, 1f),
        )
    }

    private fun JsonObject.string(name: String): String =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")

    private fun JsonObject.float(name: String): Float =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f }.getOrDefault(0f)

    private fun JsonObject.stringList(name: String): List<String> {
        val element = get(name)?.takeIf { !it.isJsonNull } ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.toStringList()
            element.isJsonPrimitive -> element.asString.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JsonArray.toStringList(): List<String> =
        mapNotNull { element -> runCatching { element.asString.trim() }.getOrNull() }
            .filter { it.isNotBlank() }

    private fun String.extractJsonObjectBlock(): String? {
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*})\\s*```", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
        if (!fenced.isNullOrBlank()) return fenced
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else null
    }

    private companion object {
        private const val TAG = "AiToolSelector"
        private const val MAX_DIRECTORY_TOOLS = 90
        private const val MAX_SELECTED_TOOLS = 10
    }
}

package com.mobileclaw.ui.chat.runtime

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message

class LlmRoleStepDecider(
    private val llm: LlmGateway,
) : RoleStepDecider {
    override suspend fun decide(packet: RoleStepPacket, trace: ChatRuntimeTrace): RoleStepDecision {
        val response = llm.chat(
            ChatRequest(
                messages = listOf(
                    Message(role = "system", content = SYSTEM_PROMPT),
                    Message(role = "user", content = buildDecisionPrompt(packet, trace)),
                ),
                tools = emptyList(),
                stream = false,
            )
        )
        val content = response.content.orEmpty()
        return parseDecision(content)
            ?: RoleStepDecision(
                action = RoleStepAction.FINAL_ANSWER,
                purpose = "Finish with the model response because no structured decision was returned.",
                reason = "Structured role decision parsing failed.",
                answer = content.ifBlank { "I could not produce a structured role decision." },
                shouldFinish = true,
            )
    }

    private fun buildDecisionPrompt(packet: RoleStepPacket, trace: ChatRuntimeTrace): String = buildString {
        appendLine(packet.toPromptBlock())
        appendLine()
        appendLine(trace.toPromptBlock(maxChars = 1000))
        appendLine()
        appendLine("Return exactly one JSON object. Do not wrap it in markdown.")
        appendLine("Schema:")
        appendLine(
            """
            {
              "action": "analyze_intent | read_role_file | search_memory | read_workspace | select_skill | invoke_tool | write_memory | compose_reply | ask_user | final_answer",
              "visibility": "silent | trace | user_timeline | confirmation",
              "purpose": "short user-visible purpose",
              "reason": "brief operational reason, not hidden chain-of-thought",
              "query": "optional search query",
              "targetPath": "optional workspace or role file path",
              "toolId": "optional tool id",
              "params": {},
              "answer": "optional final answer or question to user",
              "shouldFinish": false
            }
            """.trimIndent()
        )
    }

    private fun parseDecision(text: String): RoleStepDecision? {
        val jsonText = text.extractJsonObjectBlock() ?: return null
        val root = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return null
        val action = root.string("action")
            ?.let(::parseAction)
            ?: return null
        return RoleStepDecision(
            action = action,
            purpose = root.string("purpose").orEmpty().ifBlank { action.title },
            reason = root.string("reason").orEmpty(),
            visibility = root.string("visibility")?.let(::parseVisibility) ?: defaultVisibility(action),
            query = root.string("query").orEmpty(),
            targetPath = root.string("targetPath").orEmpty(),
            toolId = root.string("toolId").orEmpty(),
            params = root["params"]?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject(),
            answer = root.string("answer").orEmpty(),
            shouldFinish = root.boolean("shouldFinish") || action == RoleStepAction.FINAL_ANSWER,
        )
    }

    private fun parseAction(raw: String): RoleStepAction? {
        val normalized = raw.trim().lowercase()
        return RoleStepAction.entries.firstOrNull {
            it.id == normalized || it.name.lowercase() == normalized
        }
    }

    private fun parseVisibility(raw: String): RoleStepVisibility? {
        val normalized = raw.trim().lowercase()
        return RoleStepVisibility.entries.firstOrNull {
            it.name.lowercase() == normalized || it.name.lowercase() == normalized.replace('-', '_')
        }
    }

    private fun defaultVisibility(action: RoleStepAction): RoleStepVisibility =
        when (action) {
            RoleStepAction.ANALYZE_INTENT,
            RoleStepAction.READ_ROLE_FILE,
            RoleStepAction.SEARCH_MEMORY,
            RoleStepAction.SELECT_SKILL,
            RoleStepAction.COMPOSE_REPLY -> RoleStepVisibility.TRACE
            RoleStepAction.READ_WORKSPACE,
            RoleStepAction.INVOKE_TOOL,
            RoleStepAction.WRITE_MEMORY -> RoleStepVisibility.USER_TIMELINE
            RoleStepAction.ASK_USER -> RoleStepVisibility.CONFIRMATION
            RoleStepAction.FINAL_ANSWER -> RoleStepVisibility.USER_TIMELINE
        }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asBoolean ?: false

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
        private const val SYSTEM_PROMPT = """
You are MobileClaw's role runtime step decider.
Choose only the next small operation for this role run.
Do not solve the whole task unless the correct next action is final_answer.
Do not reveal hidden chain-of-thought. Use a short operational reason.
Return valid JSON only.
"""
    }
}

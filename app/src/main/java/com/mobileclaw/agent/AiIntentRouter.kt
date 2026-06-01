package com.mobileclaw.agent

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.ui.ActiveWorkflow

data class AiTaskRouteDecision(
    val taskType: TaskType?,
    val confidence: Float,
    val reason: String,
    val normalizedGoal: String,
    val targetApp: String,
    val primaryChannel: ChannelType?,
    val supportingChannels: List<ChannelType>,
    val toolHints: List<String>,
    val userVisibleSteps: List<String>,
)

class AiIntentRouter(
    private val llm: LlmGateway,
) {
    companion object {
        private const val TAG = "AiIntentRouter"
    }

    suspend fun decide(
        goal: String,
        recentContext: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
    ): AiTaskRouteDecision? {
        val prompt = buildPrompt(goal, recentContext, hasImage, hasFile, activeWorkflow)
        val raw = try {
            llm.chat(
                ChatRequest(
                    messages = listOf(
                        Message(
                            role = "system",
                            content = "You are MobileClaw's intent router. Return one strict JSON object only. Do not answer the user. Do not wrap JSON in markdown.",
                        ),
                        Message(role = "user", content = prompt),
                    ),
                    tools = emptyList(),
                    stream = false,
                )
            ).content.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Primary routing failed for goal=${goal.take(160)}", t)
            ""
        }
        return parseDecision(raw) ?: repairDecision(prompt, raw)
    }

    private suspend fun repairDecision(originalPrompt: String, invalidOutput: String): AiTaskRouteDecision? {
        if (invalidOutput.isBlank()) return null
        val raw = try {
            llm.chat(
                ChatRequest(
                    messages = listOf(
                        Message(
                            role = "system",
                            content = "Repair invalid router output into one strict JSON object only. Do not explain. Do not use markdown.",
                        ),
                        Message(
                            role = "user",
                            content = """
The previous router output was not valid JSON.

Original routing request:
${originalPrompt.take(2200)}

Invalid output:
${invalidOutput.take(1600)}

Return only a valid JSON object with these keys:
task_type, confidence, reason, normalized_goal, target_app, primary_channel, supporting_channels, tool_hints, user_visible_steps

Enum values must be uppercase exactly as documented. Arrays must be JSON arrays of strings.
""".trimIndent(),
                        ),
                    ),
                    tools = emptyList(),
                    stream = false,
                )
            ).content.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "Router repair failed. invalidOutput=${invalidOutput.take(300)}", t)
            ""
        }
        return parseDecision(raw)
    }

    private fun buildPrompt(
        goal: String,
        recentContext: String,
        hasImage: Boolean,
        hasFile: Boolean,
        activeWorkflow: ActiveWorkflow?,
    ): String = """
Route the latest MobileClaw user message into an execution channel.

Latest user message:
$goal

Input flags:
- has_image: $hasImage
- has_file: $hasFile

Active workflow:
${activeWorkflow?.let { "type=${it.taskType}; original_goal=${it.originalGoal.take(800)}" } ?: "none"}

Recent chat context, newest records included:
${recentContext.take(2600)}

Available task_type values:
CHAT, GENERAL, PHONE_CONTROL, WEB_RESEARCH, FILE_CREATE, APP_BUILD, IMAGE_GENERATION, VPN_CONTROL, SKILL_MANAGEMENT, CODE_EXECUTION

Available channel values:
CHAT, MEMORY, SKILL, SELF_EVOLUTION, PLAN, ARTIFACT, PHONE, WEB, MEDIA, VPN, CODE

Routing principles:
- Decide from meaning and context, not fixed keywords.
- If the user wants MobileClaw to operate another phone app or inspect the screen, use PHONE_CONTROL.
- If the user says they want to go into, open, search inside, click inside, buy/order/send in, or interact with a named phone app, use PHONE_CONTROL even if the sentence looks conversational.
- If the user explicitly asks to create/update a mini-app/app/program/game, or asks for HTML/CSS/JavaScript/WebView/canvas/Python-backend/SQLite runtime, use APP_BUILD with primary_channel=ARTIFACT and include `app_manager` in tool_hints.
- If the user explicitly asks for an AI Native Page / native page / dashboard / form / management page, use APP_BUILD with primary_channel=ARTIFACT and include `ui_builder` in tool_hints.
- If the user wants ordinary conversation or explanation, use CHAT or GENERAL.
- If the user attaches an image and asks what it is, use GENERAL with CHAT channel, not WEB, unless they explicitly ask for web lookup.
- If a short follow-up like "continue" refers to the active or latest task, keep that task type.
- Generate 2-4 short user-facing steps. These steps are shown directly in the UI while working.
- Write them like concrete things the AI is about to do, not abstract workflow labels.
- Good: "先查找附近可用的餐厅", "打开美团并进入下单页面", "把钢琴按键代码补全并修掉报错"
- Bad: "确认目标", "继续推进流程", "验证结果", "完善实现"
- Tool hints are optional known tool ids; include only obvious ids. Leave empty if unsure.
- Do not route explicit MiniAPP/program/runtime requests to ui_builder.
- Do not route explicit native-page requests to app_manager unless the user also explicitly asks for runtime features.
- Set confidence above 0.7 when the channel is clear. Use low confidence only when the latest message is genuinely ambiguous.
- Never output placeholder values from the example. Fill every field for the actual latest user message.

Return JSON only:
{
  "task_type": "PHONE_CONTROL",
  "confidence": 0.92,
  "reason": "User wants MobileClaw to operate a named phone app.",
  "normalized_goal": "clear executable goal in the user's language",
  "target_app": "Meituan",
  "primary_channel": "PHONE",
  "supporting_channels": ["MEMORY","PLAN"],
  "tool_hints": ["see_screen","phone_status"],
  "user_visible_steps": ["确认要操作的目标应用", "检查手机操作权限", "打开目标应用并完成请求"]
}
""".trimIndent()

    private fun parseDecision(raw: String): AiTaskRouteDecision? {
        val jsonText = raw.let { text ->
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end >= start) text.substring(start, end + 1) else text
        }
            .trim()
        val obj = try {
            JsonParser.parseString(jsonText).asJsonObject
        } catch (t: Throwable) {
            if (jsonText.isNotBlank()) {
                Log.w(TAG, "Router JSON parse failed: ${jsonText.take(300)}", t)
            }
            return null
        }
        val taskType = obj.string("task_type").toTaskTypeOrNull()
        val primaryChannel = obj.string("primary_channel").toChannelTypeOrNull()
        val rawConfidence = obj.float("confidence").coerceIn(0f, 1f)
        val confidence = when {
            rawConfidence > 0f -> rawConfidence
            taskType != null && primaryChannel != null -> 0.62f
            else -> 0f
        }
        return AiTaskRouteDecision(
            taskType = taskType,
            confidence = confidence,
            reason = obj.string("reason"),
            normalizedGoal = obj.string("normalized_goal"),
            targetApp = obj.string("target_app"),
            primaryChannel = primaryChannel,
            supportingChannels = obj.stringList("supporting_channels").mapNotNull { it.toChannelTypeOrNull() }.distinct(),
            toolHints = obj.stringList("tool_hints").map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            userVisibleSteps = obj.stringList("user_visible_steps").map { it.trim() }.filter { it.isNotBlank() }.take(6),
        )
    }

    private fun JsonObject.string(name: String): String =
        try {
            get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        } catch (_: Throwable) {
            ""
        }

    private fun JsonObject.float(name: String): Float =
        try {
            get(name)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        } catch (_: Throwable) {
            0f
        }

    private fun JsonObject.stringList(name: String): List<String> {
        val element = get(name)?.takeIf { !it.isJsonNull } ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.toStringList()
            element.isJsonPrimitive -> element.asString.split(',').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JsonArray.toStringList(): List<String> =
        mapNotNull { element -> try { element.asString } catch (_: Throwable) { null } }

    private fun String.toTaskTypeOrNull(): TaskType? =
        try { TaskType.valueOf(toEnumToken()) } catch (_: Throwable) { null }

    private fun String.toChannelTypeOrNull(): ChannelType? =
        try { ChannelType.valueOf(toEnumToken()) } catch (_: Throwable) { null }

    private fun String.toEnumToken(): String =
        trim()
            .uppercase()
            .replace('-', '_')
            .replace(' ', '_')
}

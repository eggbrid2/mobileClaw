package com.mobileclaw.ui.aipage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.SkillResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manages per-page runtime state and executes action sequences defined in AiPageDef.
 *
 * State is observable via Compose State (mutableStateMapOf) for reactive UI.
 * Action steps run on IO dispatcher; UI-affecting calls posted to main thread.
 */
class AiPageRuntime(
    private val def: AiPageDef,
    context: Context,
    private val onNavigatePage: ((String) -> Unit)? = null,
) {
    val state = mutableStateMapOf<String, String>().also { it.putAll(def.state) }
    val inputState = mutableStateMapOf<String, String>()
    var isRunning by mutableStateOf(false)
        private set
    private var lastResult: Map<String, Any> = emptyMap()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val capabilities = AiPageCapabilities(context)
    private val appContext = AiPageAppContext(context)
    private val app = context.applicationContext as ClawApplication
    private val gson = Gson()

    fun handleAction(actionName: String) {
        val stepsArr = def.actions[actionName]?.asJsonArray ?: return
        val steps = (0 until stepsArr.size()).mapNotNull {
            runCatching { stepsArr[it].asJsonObject }.getOrNull()
        }
        scope.launch {
            isRunning = true
            try {
                for (step in steps) executeStep(step)
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun executeStep(step: JsonObject) {
        val type = step["type"]?.asString ?: return

        fun ev(key: String): String {
            val raw = step[key]?.asString ?: return ""
            return ExprEval.eval(raw, state.toMap(), inputState.toMap(), lastResult)
        }

        when (type) {
            "set_state" -> {
                val key = ev("key")
                val value = ev("value")
                if (key.isNotBlank()) state[key] = value
            }

            "http" -> {
                val url = ev("url")
                val method = step["method"]?.asString ?: "GET"
                val body = ev("body")
                @Suppress("UNCHECKED_CAST")
                val headers = runCatching {
                    val h = step["headers"]?.asJsonObject
                    h?.entrySet()?.associate { (k, v) -> k to v.asString } ?: emptyMap<String, String>()
                }.getOrDefault(emptyMap())
                val resultKey = step["result_key"]?.asString ?: "response"
                val result = capabilities.httpFetch(url, method, headers, body)
                lastResult = result
                if (resultKey.isNotBlank()) state[resultKey] = result["body"]?.toString() ?: ""
            }

            "shell" -> {
                val cmd = ev("cmd")
                val resultKey = step["result_key"]?.asString ?: "shell_out"
                val result = capabilities.shellExec(cmd)
                lastResult = result
                if (resultKey.isNotBlank()) state[resultKey] = result["stdout"]?.toString() ?: ""
            }

            "notify" -> capabilities.notify(ev("title"), ev("body"))

            "vibrate" -> capabilities.vibrate(step["ms"]?.asLong ?: 100)

            "toast" -> capabilities.toast(ev("text"))

            "launch_app" -> capabilities.launchApp(ev("package"))

            "open_url" -> capabilities.openUrl(ev("url"))

            "open_map" -> capabilities.openMap(ev("query"))

            "call_phone" -> capabilities.callPhone(ev("number"))

            "send_sms" -> capabilities.sendSms(ev("number"), ev("body"))

            "set_alarm" -> {
                val hour = step["hour"]?.asInt ?: 0
                val minute = step["minute"]?.asInt ?: 0
                capabilities.setAlarm(hour, minute, ev("message"))
            }

            "send_intent" -> {
                val action = ev("action")
                val dataUri = step["data"]?.asString?.let { ExprEval.eval(it, state.toMap(), inputState.toMap(), lastResult) }
                @Suppress("UNCHECKED_CAST")
                val extras = runCatching {
                    val ex = step["extras"]?.asJsonObject
                    ex?.entrySet()?.associate { (k, v) -> k to ExprEval.eval(v.asString, state.toMap(), inputState.toMap(), lastResult) }
                        ?: emptyMap<String, String>()
                }.getOrDefault(emptyMap())
                capabilities.sendIntent(action, dataUri, extras)
            }

            "share" -> capabilities.share(ev("text"), step["title"]?.asString?.let { ev("title") })

            "clipboard_set" -> capabilities.clipboardSet(ev("text"))

            "clipboard_get" -> {
                val resultKey = step["result_key"]?.asString ?: "clipboard"
                state[resultKey] = capabilities.clipboardGet()
            }

            "app_context" -> {
                val domain = step["domain"]?.asString?.let { ExprEval.eval(it, state.toMap(), inputState.toMap(), lastResult) } ?: "all"
                val limit = step["limit"]?.asInt ?: 20
                val resultKey = step["result_key"]?.asString ?: "app_context"
                val json = appContext.asJson(domain, limit)
                lastResult = mapOf("body" to json, "domain" to domain, "ok" to true)
                if (resultKey.isNotBlank()) state[resultKey] = json
            }

            "skill_call" -> {
                val skillId = ev("skill")
                val resultKey = step["result_key"]?.asString ?: "${skillId}_result"
                val params = parseParams(step["params"] as? JsonObject)
                val skillResult = app.skillRegistry.get(skillId)
                    ?.let { skill -> runCatching { skill.execute(params) }.getOrElse { e -> SkillResult(false, "Error: ${e.message}") } }
                    ?: SkillResult(false, "Skill '$skillId' not found")
                val result = mapOf(
                    "ok" to skillResult.success,
                    "output" to skillResult.output,
                    "hasImage" to !skillResult.imageBase64.isNullOrBlank(),
                    "data" to (skillResult.data?.let { gson.toJson(it) } ?: ""),
                )
                lastResult = result
                if (resultKey.isNotBlank()) state[resultKey] = skillResult.output
            }

            "navigate_page" -> {
                val pageId = ev("id")
                if (pageId.isNotBlank()) onNavigatePage?.invoke(pageId)
            }
        }
    }

    private fun parseParams(obj: JsonObject?): Map<String, Any> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (key, value) ->
            val resolved = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                ExprEval.eval(value.asString, state.toMap(), inputState.toMap(), lastResult)
            } else {
                value.toString()
            }
            key to parseParamValue(resolved)
        }
    }

    private fun parseParamValue(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.equals("true", ignoreCase = true)) return true
        if (trimmed.equals("false", ignoreCase = true)) return false
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return runCatching { gson.fromJson(JsonParser.parseString(trimmed), Any::class.java) }.getOrDefault(raw)
        }
        return raw
    }

    fun dispose() { scope.cancel() }
}

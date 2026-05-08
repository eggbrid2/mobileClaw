package com.mobileclaw.ui.aipage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
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

            "navigate_page" -> {
                val pageId = ev("id")
                if (pageId.isNotBlank()) onNavigatePage?.invoke(pageId)
            }
        }
    }

    fun dispose() { scope.cancel() }
}

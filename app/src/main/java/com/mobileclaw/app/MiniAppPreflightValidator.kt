package com.mobileclaw.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.config.UserConfig
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.ui.AppJsBridge
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class MiniAppPreflightReport(
    val ok: Boolean,
    val issues: List<String>,
    val warnings: List<String> = emptyList(),
    val title: String = "",
    val recentLogs: List<String> = emptyList(),
)

class MiniAppPreflightValidator(
    private val context: Context,
    private val store: MiniAppStore,
    private val userConfig: UserConfig,
    private val semanticMemory: SemanticMemory,
) {
    private companion object {
        const val PREVIEW_PROBE_DELAY_MS = 150L
        const val PREVIEW_TIMEOUT_MS = 10_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    suspend fun validate(
        appId: String,
        html: String,
        python: String?,
    ): MiniAppPreflightReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val tempId = "__preflight_${appId}_${UUID.randomUUID().toString().take(8)}"
        try {
            python?.takeIf { it.isNotBlank() }?.let { code ->
                issues += validatePythonBackend(code)
                store.savePython(tempId, code)
            }
            val webReport = normalizeTimeoutOnlyPreflight(
                validateHtmlInHiddenWebView(tempId, html)
            )
            issues += webReport.issues
            warnings += webReport.warnings
            return MiniAppPreflightReport(
                ok = issues.isEmpty(),
                issues = issues.distinct(),
                warnings = warnings.distinct(),
                title = webReport.title,
                recentLogs = store.readLogs(tempId, limit = 40),
            )
        } finally {
            runCatching { store.appDataDir(tempId).deleteRecursively() }
            runCatching { File(store.htmlFile(tempId).absolutePath).delete() }
            runCatching { File(store.htmlFile(tempId).parentFile, "$tempId.json").delete() }
        }
    }

    private fun validatePythonBackend(code: String): List<String> = runCatching {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        val py = Python.getInstance()
        val builtins = py.getModule("builtins")
        val ns = builtins.callAttr("dict")
        builtins.callAttr("compile", code, "<backend.py>", "exec")
        builtins.callAttr("exec", code, ns)
        val handle = ns.callAttr("get", "handle")
        if (handle == null || handle.toString() == "None") {
            listOf("Python backend validation failed: missing handle(input_json) function.")
        } else {
            emptyList()
        }
    }.getOrElse { e ->
        listOf("Python backend validation failed: ${e.message ?: "unknown python error"}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun validateHtmlInHiddenWebView(
        tempAppId: String,
        html: String,
    ): MiniAppPreflightReport = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            var finished = false
            val issues = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            var pageTitle = ""
            var timeoutPosted = false

            val webView = WebView(appContext)
            val bridge = AppJsBridge(
                context = appContext,
                appId = tempAppId,
                store = store,
                userConfig = userConfig,
                semanticMemory = semanticMemory,
                onAskAgent = {},
                onClose = {},
                onSetTitle = { pageTitle = it },
            )

            fun finish() {
                if (finished) return
                finished = true
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
                cont.resume(
                    MiniAppPreflightReport(
                        ok = issues.isEmpty(),
                        issues = issues.distinct(),
                        warnings = warnings.distinct(),
                        title = pageTitle,
                        recentLogs = store.readLogs(tempAppId, limit = 40),
                    )
                )
            }

            fun issue(message: String) {
                val clean = message.trim()
                if (clean.isNotBlank()) issues += clean.take(400)
            }

            fun warn(message: String) {
                val clean = message.trim()
                if (clean.isNotBlank()) warnings += clean.take(400)
            }

            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }
            bridge.bindWebView(webView)
            webView.addJavascriptInterface(bridge, "Android")
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val msg = "Console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> issue(msg)
                        ConsoleMessage.MessageLevel.WARNING -> warn(msg)
                        else -> Unit
                    }
                    return true
                }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        issue("Page load error: ${error?.description ?: "unknown"}")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (finished) return
                    view?.postDelayed({
                        val js = """
                            (function(){
                              try{
                                return JSON.stringify({
                                  title: document.title || "",
                                  readyState: document.readyState || "",
                                  hasClaw: typeof window.Claw !== "undefined",
                                  hasFetch: !!(window.Claw && typeof window.Claw.fetch === "function"),
                                  bodyPresent: !!document.body
                                });
                              }catch(e){
                                return JSON.stringify({error: e.message || String(e)});
                              }
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(js) { raw ->
                            val decoded = runCatching { JsonParser.parseString(raw).asString }.getOrDefault("")
                            val parsed = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
                            if (parsed == null) {
                                issue("Preflight probe failed: invalid JS result.")
                                finish()
                                return@evaluateJavascript
                            }
                            parsed.get("title")?.takeIf { !it.isJsonNull }?.asString?.let { pageTitle = it }
                            parsed.get("error")?.takeIf { !it.isJsonNull }?.asString?.let {
                                issue("Preflight probe failed: $it")
                            }
                            val hasClaw = parsed.get("hasClaw")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val hasFetch = parsed.get("hasFetch")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val bodyPresent = parsed.get("bodyPresent")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val readyState = parsed.get("readyState")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                            if (!hasClaw) issue("Preflight failed: Claw bridge was not injected.")
                            if (!hasFetch) issue("Preflight failed: Claw.fetch bridge is unavailable.")
                            if (!bodyPresent) issue("Preflight failed: document.body is missing after load.")
                            if (readyState != "complete" && readyState != "interactive") {
                                warn("Document readyState is '$readyState' during preflight.")
                            }
                            finish()
                        }
                    }, PREVIEW_PROBE_DELAY_MS)
                }
            }

            val timeout = Runnable {
                if (!finished) {
                    issue("MiniAPP preflight exceeded ${PREVIEW_TIMEOUT_MS / 1000}s before startup checks completed. Possible blocking startup code or an infinite loop.")
                    finish()
                }
            }
            timeoutPosted = true
            mainHandler.postDelayed(timeout, PREVIEW_TIMEOUT_MS)

            cont.invokeOnCancellation {
                if (timeoutPosted) mainHandler.removeCallbacks(timeout)
                runCatching { webView.destroy() }
            }

            val baseUrl = "file://${File(appContext.filesDir, "apps").absolutePath}/"
            val preflightWrapped = injectPreflightHooks(store.injectBridge(html))
            webView.loadDataWithBaseURL(baseUrl, preflightWrapped, "text/html", "UTF-8", null)
        }
    }

    private fun injectPreflightHooks(html: String): String {
        val hook = """
            <script>
            (function(){
              window.addEventListener('error', function(e){
                try{ console.error('PRELOAD_JS_ERROR: ' + (e.message || 'unknown')); }catch(_){}
              });
              window.addEventListener('unhandledrejection', function(e){
                try{
                  var reason = e.reason && (e.reason.message || e.reason) || 'unknown';
                  console.error('PRELOAD_PROMISE_REJECTION: ' + reason);
                }catch(_){}
              });
            })();
            </script>
        """.trimIndent()
        return hook + html
    }

    private fun normalizeTimeoutOnlyPreflight(report: MiniAppPreflightReport): MiniAppPreflightReport {
        if (report.ok || report.issues.isEmpty()) return report
        val timeoutIssue = report.issues.firstOrNull { it.contains("preflight exceeded", ignoreCase = true) }
            ?: return report
        val otherIssues = report.issues.filterNot { it == timeoutIssue }
        if (otherIssues.isNotEmpty()) return report

        val logs = report.recentLogs
        val hasErrorSignals = logs.any { line ->
            val lower = line.lowercase()
            "[error]" in lower ||
                "preload_js_error" in lower ||
                "preload_promise_rejection" in lower ||
                "unhandled promise rejection" in lower ||
                "js error:" in lower
        }
        if (hasErrorSignals) return report

        val hasStartupReadySignal = logs.any { line ->
            val lower = line.lowercase()
            listOf(
                "startup",
                "page mounted",
                "app mounted",
                "mounted",
                "ready",
                "loaded",
                "load complete",
                "loaded complete",
                "rendered",
                "initialized",
                "init complete",
                "已生成",
                "加载完成",
                "已加载",
                "已就绪",
                "初始化完成",
            ).any { token -> token in lower }
        }
        val hasMeaningfulProgress = report.title.isNotBlank() || logs.isNotEmpty()
        if (!hasStartupReadySignal && !hasMeaningfulProgress) return report

        return report.copy(
            ok = true,
            issues = emptyList(),
            warnings = (
                report.warnings +
                    "MiniAPP preflight probe exceeded ${PREVIEW_TIMEOUT_MS / 1000}s, but runtime logs indicate startup completed. Allowing create/open and keeping this as a warning."
                ).distinct(),
        )
    }
}

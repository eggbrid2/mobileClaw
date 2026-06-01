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
    enum class Mode {
        STARTUP,
        STRICT,
    }

    private companion object {
        const val STARTUP_PROBE_DELAY_MS = 200L
        const val STRICT_PROBE_DELAY_MS = 450L
        const val STARTUP_TIMEOUT_MS = 4_000L
        const val STRICT_TIMEOUT_MS = 10_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    suspend fun validate(
        appId: String,
        html: String,
        python: String?,
        mode: Mode = Mode.STRICT,
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
                validateHtmlInHiddenWebView(tempId, html, mode),
                mode,
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
        mode: Mode,
    ): MiniAppPreflightReport = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            var finished = false
            val issues = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            var pageTitle = ""
            var timeoutPosted = false
            val probeDelayMs = if (mode == Mode.STARTUP) STARTUP_PROBE_DELAY_MS else STRICT_PROBE_DELAY_MS
            val timeoutMs = if (mode == Mode.STARTUP) STARTUP_TIMEOUT_MS else STRICT_TIMEOUT_MS

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
                                var body = document.body;
                                var bodyStyle = body ? window.getComputedStyle(body) : null;
                                var text = body && body.innerText ? body.innerText.trim() : "";
                                var interactive = document.querySelectorAll('button,input,select,textarea,a,[role="button"],[onclick]').length;
                                var hasCanvas = document.querySelectorAll('canvas').length > 0;
                                var mediaCount = document.querySelectorAll('img,svg,video').length;
                                var bodyRect = body ? body.getBoundingClientRect() : {width:0,height:0};
                                return JSON.stringify({
                                  title: document.title || "",
                                  readyState: document.readyState || "",
                                  hasClaw: typeof window.Claw !== "undefined",
                                  hasFetch: !!(window.Claw && typeof window.Claw.fetch === "function"),
                                  bodyPresent: !!body,
                                  textLength: text.length,
                                  elementCount: document.querySelectorAll('*').length,
                                  interactiveCount: interactive,
                                  hasCanvas: hasCanvas,
                                  mediaCount: mediaCount,
                                  bodyWidth: Math.round(bodyRect.width || 0),
                                  bodyHeight: Math.round(bodyRect.height || 0),
                                  bodyHidden: !!(bodyStyle && (bodyStyle.display === "none" || bodyStyle.visibility === "hidden" || bodyStyle.opacity === "0"))
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
                            val textLength = parsed.get("textLength")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val elementCount = parsed.get("elementCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val interactiveCount = parsed.get("interactiveCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val hasCanvas = parsed.get("hasCanvas")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val mediaCount = parsed.get("mediaCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val bodyWidth = parsed.get("bodyWidth")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val bodyHeight = parsed.get("bodyHeight")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val bodyHidden = parsed.get("bodyHidden")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val readyState = parsed.get("readyState")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                            if (!hasClaw) issue("Preflight failed: Claw bridge was not injected.")
                            if (!hasFetch) issue("Preflight failed: Claw.fetch bridge is unavailable.")
                            if (!bodyPresent) issue("Preflight failed: document.body is missing after load.")
                            if (bodyHidden) issue("Preflight failed: document.body is hidden by CSS, which can cause a blank screen.")
                            if (bodyPresent && (bodyWidth <= 4 || bodyHeight <= 4)) {
                                issue("Preflight failed: rendered body size is too small (${bodyWidth}x${bodyHeight}), likely causing a blank screen.")
                            }
                            if (bodyPresent && elementCount <= 1) {
                                warn("Preflight warning: page DOM is almost empty after load.")
                            }
                            if (bodyPresent && textLength == 0 && interactiveCount == 0) {
                                val visualSurfaceExists = hasCanvas || mediaCount > 0 || elementCount > 3
                                if (mode == Mode.STRICT && !visualSurfaceExists) {
                                    issue("Preflight failed: page rendered no visible text and no interactive controls after load, likely a blank screen.")
                                } else {
                                    warn("Preflight warning: page has no visible text or controls yet, but a visual surface was mounted. Allow runtime preview to continue validation.")
                                }
                            }
                            if (readyState != "complete" && readyState != "interactive") {
                                warn("Document readyState is '$readyState' during preflight.")
                            }
                            finish()
                        }
                    }, probeDelayMs)
                }
            }

            val timeout = Runnable {
                if (!finished) {
                    issue("MiniAPP preflight exceeded ${timeoutMs / 1000}s before startup checks completed. Possible blocking startup code or an infinite loop.")
                    finish()
                }
            }
            timeoutPosted = true
            mainHandler.postDelayed(timeout, timeoutMs)

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

    private fun normalizeTimeoutOnlyPreflight(
        report: MiniAppPreflightReport,
        mode: Mode,
    ): MiniAppPreflightReport {
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
                    "MiniAPP ${mode.name.lowercase()} preflight probe exceeded the timeout, but runtime logs indicate startup completed. Allowing create/open and keeping this as a warning."
                ).distinct(),
        )
    }
}

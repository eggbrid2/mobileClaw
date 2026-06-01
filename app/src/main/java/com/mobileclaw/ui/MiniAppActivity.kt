package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mobileclaw.ClawApplication
import com.mobileclaw.app.MiniApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobileclaw.R
import com.mobileclaw.str

class MiniAppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return finish()
        val claw = ClawApplication.instance
        val config = claw.agentConfig.snapshot()

        setContent {
            ClawTheme(darkTheme = config.darkTheme, accentColor = config.accentColor) {
                MiniAppScreen(
                    appId = appId,
                    onClose = { finish() },
                    onAskAgent = { task ->
                        claw.pendingAgentTask.tryEmit(task)
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_APP_ID = "app_id"

        fun intent(context: Context, appId: String): Intent =
            Intent(context, MiniAppActivity::class.java).apply {
                putExtra(EXTRA_APP_ID, appId)
            }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniAppScreen(
    appId: String,
    onClose: () -> Unit,
    onAskAgent: (String) -> Unit,
) {
    MiniAppViewport(
        appId = appId,
        onClose = onClose,
        onAskAgent = onAskAgent,
        modifier = Modifier.fillMaxSize(),
        compact = false,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppViewport(
    appId: String,
    onClose: () -> Unit,
    onAskAgent: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    validationMode: Boolean = false,
    onStatusChange: ((String, Boolean) -> Unit)? = null,
    onToggleExpanded: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
) {
    val previewReferenceWidth = 390.dp
    val previewReferenceHeight = 844.dp
    val context = LocalContext.current
    val claw = ClawApplication.instance
    val miniApp = remember(appId) { claw.miniAppStore.get(appId) }
    val c = LocalClawColors.current

    var appTitle by remember(appId) { mutableStateOf(miniApp?.let { "${it.icon} ${it.title}" } ?: appId) }
    var showMore by remember { mutableStateOf(false) }
    var isPageLoading by remember(appId) { mutableStateOf(true) }
    var runtimeIssue by remember(appId) { mutableStateOf<String?>(null) }
    val store = claw.miniAppStore

    LaunchedEffect(isPageLoading, runtimeIssue, appTitle) {
        val status = when {
            isPageLoading -> if (validationMode) "Validation preview loading" else "MiniAPP loading"
            !runtimeIssue.isNullOrBlank() -> runtimeIssue.orEmpty()
            else -> if (validationMode) "Validation preview passed" else "MiniAPP rendered"
        }
        onStatusChange?.invoke(status, runtimeIssue.isNullOrBlank())
    }

    // Create WebView once; destroy when leaving composition
    val webView = remember(appId) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            // Hardware acceleration reduces render jank and improves scroll smoothness
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            // Let the WebView be the scroll container — Compose should not intercept scroll events
            isScrollContainer = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = false
                setSupportZoom(false)
                builtInZoomControls = false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "error"
                        ConsoleMessage.MessageLevel.WARNING -> "warn"
                        else -> "debug"
                    }
                    store.appendLog(
                        appId,
                        level,
                        "console",
                        "${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                    )
                    return true
                }
            }

            val bridge = AppJsBridge(
                context = context,
                appId = appId,
                store = claw.miniAppStore,
                userConfig = claw.userConfig,
                semanticMemory = claw.semanticMemory,
                onAskAgent = onAskAgent,
                onClose = onClose,
                onSetTitle = { appTitle = it },
            )
            addJavascriptInterface(bridge, "Android")
            bridge.bindWebView(this)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isPageLoading = true
                    runtimeIssue = null
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        val message = "Main frame load error: ${error?.description ?: "unknown"}"
                        runtimeIssue = "This MiniAPP failed to load its main page."
                        isPageLoading = false
                        store.appendLog(appId, "error", "webview", message)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        runtimeIssue = "This MiniAPP returned an HTTP error before the page could render."
                        store.appendLog(
                            appId,
                            "warn",
                            "webview",
                            "Main frame HTTP error: ${errorResponse?.statusCode ?: 0} ${request.url}",
                        )
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    runtimeIssue = "The MiniAPP renderer process exited, so this page can no longer continue."
                    isPageLoading = false
                    store.appendLog(
                        appId,
                        "error",
                        "webview",
                        "Render process gone: didCrash=${detail?.didCrash() == true}, priority=${detail?.rendererPriorityAtExit()}",
                    )
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    store.appendLog(appId, "info", "startup", "Page finished: ${url ?: "unknown"}")
                    val reinjection = claw.miniAppStore.clawBridgeSetupJs()
                    view?.evaluateJavascript(
                        "(function(){ if(typeof window.Claw==='undefined'){ $reinjection } })();", null,
                    )
                    view?.postDelayed({
                        val runtimeProbe = """
                            (function(){
                              try{
                                var body = document.body;
                                var bodyStyle = body ? window.getComputedStyle(body) : null;
                                var text = body && body.innerText ? body.innerText.trim() : "";
                                var interactive = document.querySelectorAll('button,input,select,textarea,a,[role="button"],[onclick]').length;
                                var bodyRect = body ? body.getBoundingClientRect() : {width:0,height:0};
                                return JSON.stringify({
                                  title: document.title || "",
                                  readyState: document.readyState || "",
                                  hasClaw: typeof window.Claw !== "undefined",
                                  textLength: text.length,
                                  interactiveCount: interactive,
                                  bodyWidth: Math.round(bodyRect.width || 0),
                                  bodyHeight: Math.round(bodyRect.height || 0),
                                  bodyHidden: !!(bodyStyle && (bodyStyle.display === "none" || bodyStyle.visibility === "hidden" || bodyStyle.opacity === "0"))
                                });
                              }catch(e){
                                return JSON.stringify({error: e.message || String(e)});
                              }
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(runtimeProbe) { raw ->
                            val parsed = runCatching {
                                com.google.gson.JsonParser.parseString(raw).asString
                            }.mapCatching {
                                com.google.gson.JsonParser.parseString(it).asJsonObject
                            }.getOrNull() ?: return@evaluateJavascript
                            isPageLoading = false
                            parsed.get("title")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let {
                                appTitle = it
                            }
                            val bodyHidden = parsed.get("bodyHidden")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            val textLength = parsed.get("textLength")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val interactiveCount = parsed.get("interactiveCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val bodyWidth = parsed.get("bodyWidth")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val bodyHeight = parsed.get("bodyHeight")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val hasClaw = parsed.get("hasClaw")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                            if (!hasClaw) {
                                store.appendLog(appId, "error", "runtime_probe", "Claw bridge missing after page load.")
                            }
                            if (bodyHidden) {
                                store.appendLog(appId, "error", "runtime_probe", "document.body is hidden by CSS.")
                            }
                            if (bodyWidth <= 4 || bodyHeight <= 4) {
                                store.appendLog(appId, "error", "runtime_probe", "Rendered body too small: ${bodyWidth}x${bodyHeight}.")
                            }
                            if (textLength == 0 && interactiveCount == 0) {
                                store.appendLog(appId, "error", "runtime_probe", "Likely blank screen: no visible text and no interactive controls after load.")
                            }
                            runtimeIssue = buildRuntimeIssueMessage(
                                hasClaw = hasClaw,
                                bodyHidden = bodyHidden,
                                bodyWidth = bodyWidth,
                                bodyHeight = bodyHeight,
                                textLength = textLength,
                                interactiveCount = interactiveCount,
                            )
                        }
                    }, 220)
                }
            }

            val htmlFile = claw.miniAppStore.htmlFile(appId)
            val baseUrl = "file://${htmlFile.parent}/"
            if (htmlFile.exists()) {
                loadDataWithBaseURL(baseUrl, claw.miniAppStore.injectBridge(htmlFile.readText()), "text/html", "UTF-8", null)
            } else {
                loadDataWithBaseURL(baseUrl,
                    "<html><body style='padding:24px;font-family:sans-serif'><h3>⚠ App not found</h3><p>id: $appId</p></body></html>",
                    "text/html", "UTF-8", null)
            }

            CoroutineScope(Dispatchers.IO).launch {
                runCatching { if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext)) }
            }
        }
    }

    DisposableEffect(appId) {
        onDispose { webView.destroy() }
    }

    Box(modifier = modifier.background(c.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthScale = maxWidth / previewReferenceWidth
            val heightScale = maxHeight / previewReferenceHeight
            val viewportScale = if (compact) minOf(widthScale, heightScale) else 1f
            Column(
                Modifier
                    .then(
                        if (compact) {
                            Modifier
                                .requiredWidth(previewReferenceWidth)
                                .requiredHeight(previewReferenceHeight)
                                .graphicsLayer(
                                    scaleX = viewportScale,
                                    scaleY = viewportScale,
                                    transformOrigin = TransformOrigin(0f, 0f),
                                )
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ),
            ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .then(if (compact) Modifier else Modifier.statusBarsPadding()),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (compact) 10.dp else 14.dp, end = 4.dp, top = if (compact) 4.dp else 6.dp, bottom = if (compact) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (validationMode) "Validate · $appTitle" else appTitle,
                        color = c.text,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (compact) {
                        IconButton(
                            onClick = { onToggleExpanded?.invoke() },
                            enabled = onToggleExpanded != null,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Filled.OpenInFull,
                                contentDescription = null,
                                tint = if (onToggleExpanded != null) c.subtext else c.subtext.copy(alpha = 0.38f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        IconButton(
                            onClick = { onOpenExternal?.invoke() },
                            enabled = onOpenExternal != null,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = if (onOpenExternal != null) c.subtext else c.subtext.copy(alpha = 0.38f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    } else {
                        IconButton(onClick = { showMore = true }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = null,
                                tint = c.subtext,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(if (compact) 34.dp else 40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = c.text,
                            modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                        )
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)
            }

            // ── WebView ───────────────────────────────────────────────────────
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }
        }

        AnimatedVisibility(
            visible = isPageLoading,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (compact) 52.dp else 74.dp),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            LoadingPill(compact = compact)
        }

        AnimatedVisibility(
            visible = !runtimeIssue.isNullOrBlank(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 10.dp else 18.dp),
            enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(180)),
        ) {
            RuntimeIssueCard(
                message = runtimeIssue.orEmpty(),
                compact = compact,
                onRetry = {
                    runtimeIssue = null
                    isPageLoading = true
                    webView.reload()
                },
                onAskAgent = {
                    onAskAgent(formatMiniAppRepairTask(appId, appTitle, store.readLogs(appId, limit = 12)))
                },
            )
        }

        // ── More info panel ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMore && !compact,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = { showMore = false }),
            )
        }
        AnimatedVisibility(
            visible = showMore && !compact,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MiniAppInfoPanel(
                miniApp = miniApp,
                onDismiss = { showMore = false },
                onClose = { showMore = false; onClose() },
            )
        }
    }
}

private fun buildRuntimeIssueMessage(
    hasClaw: Boolean,
    bodyHidden: Boolean,
    bodyWidth: Int,
    bodyHeight: Int,
    textLength: Int,
    interactiveCount: Int,
): String? {
    if (!hasClaw) return "The MiniAPP lost its native bridge, so its built-in capabilities cannot work."
    if (bodyHidden) return "The MiniAPP rendered its page as hidden, which usually causes a blank screen."
    if (bodyWidth <= 4 || bodyHeight <= 4) return "The MiniAPP rendered into a near-zero layout size and is effectively blank."
    if (textLength == 0 && interactiveCount == 0) return "The MiniAPP finished loading but produced no visible content or controls."
    return null
}

private fun formatMiniAppRepairTask(
    appId: String,
    appTitle: String,
    recentLogs: List<String>,
): String {
    val logSummary = recentLogs.takeLast(8).joinToString(" | ").take(900)
    return buildString {
        append("Repair the MiniAPP '")
        append(appTitle.ifBlank { appId })
        append("' (id: ")
        append(appId)
        append("). ")
        append("It opened into a runtime failure or blank screen. ")
        append("Use the MiniAPP guide, inspect logs first, then do one focused repair pass instead of rewriting unrelated parts. ")
        if (logSummary.isNotBlank()) {
            append("Recent logs: ")
            append(logSummary)
        }
    }
}

@Composable
private fun LoadingPill(compact: Boolean) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface.copy(alpha = 0.96f))
            .border(0.5.dp, c.border, RoundedCornerShape(999.dp))
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            strokeWidth = 1.8.dp,
            color = c.text,
        )
        Text(
            text = if (compact) "Checking preview" else "Loading MiniAPP",
            color = c.text,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RuntimeIssueCard(
    message: String,
    compact: Boolean,
    onRetry: () -> Unit,
    onAskAgent: () -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface.copy(alpha = 0.98f))
            .border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (compact) "Preview needs repair" else "MiniAPP needs attention",
            color = c.text,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            color = c.subtext,
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 15.sp else 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawSecondaryButton(
                text = if (compact) "Retry" else "Retry",
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            )
            ClawPrimaryButton(
                text = if (compact) "Repair" else "Ask AI to Repair",
                onClick = onAskAgent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MiniAppInfoPanel(
    miniApp: MiniApp?,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalClawColors.current
    val dateStr = miniApp?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.createdAt))
    } ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(c.surface)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.border),
        )

        // App identity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClawIconTile(miniApp?.icon ?: "apps", size = 58.dp, iconSize = 30.dp, tint = c.text, background = c.cardAlt, border = c.border)
            Spacer(Modifier.height(6.dp))
            Text(
                text = miniApp?.title ?: str(R.string.drawer_apps),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
            )
            if (!miniApp?.description.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = miniApp!!.description,
                    fontSize = 13.sp,
                    color = c.subtext,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = c.border, thickness = 0.5.dp)

        // Meta info
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (miniApp?.hasPython == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClawSymbolIcon("python", tint = c.subtext, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(R.string.activity_ae1a83), fontSize = 12.sp, color = c.subtext)
                }
            }
            if (dateStr.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClawSymbolIcon("time", tint = c.subtext, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(R.string.created_at, dateStr), fontSize = 12.sp, color = c.subtext)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = c.border, thickness = 0.5.dp)

        // Actions
        Spacer(Modifier.height(6.dp))
        PanelAction(label = str(R.string.activity_close), color = c.red, onClick = onClose)
        PanelAction(label = str(R.string.activity_back), color = c.text, onClick = onDismiss)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PanelAction(label: String, color: Color, onClick: () -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(label, fontSize = 15.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

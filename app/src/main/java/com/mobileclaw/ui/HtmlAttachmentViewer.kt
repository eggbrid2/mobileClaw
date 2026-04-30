package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.SkillAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun attachmentKey(a: SkillAttachment.HtmlData): String =
    "${a.path}:${a.htmlContent.length}:${a.htmlContent.hashCode()}"

private fun loadHtmlAttachment(
    webView: WebView,
    attachment: SkillAttachment.HtmlData,
    store: com.mobileclaw.app.MiniAppStore,
) {
    val htmlContent = attachment.htmlContent.ifBlank {
        runCatching { File(attachment.path).readText() }.getOrDefault("")
    }
    val baseUrl = when {
        attachment.path.isNotBlank() -> "file://${File(attachment.path).parent}/"
        else -> "file://${ClawApplication.instance.filesDir}/html_pages/"
    }
    val injectedHtml = store.injectBridge(htmlContent)
    webView.loadDataWithBaseURL(baseUrl, injectedHtml, "text/html", "UTF-8", null)
}

/**
 * Full-screen HTML viewer rendered at Activity level (NOT inside a Dialog).
 *
 * Rendering inside a Compose Dialog creates a separate Window context where
 * addJavascriptInterface does NOT bind. By placing this composable directly in
 * the Activity's composition tree we guarantee the Java bridge works correctly.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlAttachmentViewer(
    attachment: SkillAttachment.HtmlData,
    onClose: () -> Unit,
    onAskAgent: (String) -> Unit,
) {
    val app = ClawApplication.instance
    val c = LocalClawColors.current

    // Track the content key to detect hot-update changes: path + content hash
    val loadedKey = remember { mutableStateOf("") }
    // Title is mutable — the app can update it via Claw.setTitle()
    var appTitle by remember { mutableStateOf(attachment.title) }

    // Pre-warm Python runtime in the background
    LaunchedEffect(attachment.path) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Python.isStarted()) Python.start(AndroidPlatform(app))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // ── Title bar ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = c.text, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    appTitle,
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        // ── WebView ──────────────────────────────────────────────────────────
        // Must be in Activity's view hierarchy for addJavascriptInterface to bind.
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    webChromeClient = android.webkit.WebChromeClient()
                    // Prevent white flash while page loads
                    setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))

                    // Derive a stable app-like ID from the file path so data is persisted
                    val appId = attachment.path
                        .substringAfterLast("/")
                        .substringBeforeLast(".")
                        .replace(Regex("[^a-zA-Z0-9_]"), "_")
                        .take(40)
                        .ifBlank { "html_viewer" }

                    val bridge = AppJsBridge(
                        context = context,
                        appId = appId,
                        store = app.miniAppStore,
                        userConfig = app.userConfig,
                        semanticMemory = app.semanticMemory,
                        onAskAgent = onAskAgent,
                        onClose = onClose,
                        onSetTitle = { appTitle = it },
                    )
                    addJavascriptInterface(bridge, "Android")
                    bridge.bindWebView(this)

                    // Fallback: re-inject Claw script after page load in case
                    // the inline <script> ran before window.Android was ready
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val js = app.miniAppStore.clawBridgeSetupJs()
                            view?.evaluateJavascript(
                                "(function(){ if(typeof window.Claw==='undefined'){ $js } })();",
                                null,
                            )
                        }
                    }

                    loadHtmlAttachment(this, attachment, app.miniAppStore)
                    loadedKey.value = attachmentKey(attachment)
                }
            },
            update = { webView ->
                // Hot-update: reload only when path or content actually changed
                val key = attachmentKey(attachment)
                if (loadedKey.value != key) {
                    loadedKey.value = key
                    loadHtmlAttachment(webView, attachment, app.miniAppStore)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

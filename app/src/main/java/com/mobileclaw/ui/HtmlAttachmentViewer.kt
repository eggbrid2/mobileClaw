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
                    attachment.title,
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
                    webChromeClient = android.webkit.WebChromeClient()

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
                    )
                    addJavascriptInterface(bridge, "Android")

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

                    // Determine HTML content and base URL
                    val htmlContent = attachment.htmlContent.ifBlank {
                        runCatching { File(attachment.path).readText() }.getOrDefault("")
                    }
                    val baseUrl = when {
                        attachment.path.isNotBlank() -> "file://${File(attachment.path).parent}/"
                        else -> "file://${app.filesDir}/html_pages/"
                    }

                    // Always inject bridge script fresh, then load
                    val injectedHtml = app.miniAppStore.injectBridge(htmlContent)
                    loadDataWithBaseURL(baseUrl, injectedHtml, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobileclaw.ClawApplication
import com.mobileclaw.app.MiniApp

@Composable
fun AppLauncherPage(
    miniApps: List<MiniApp>,
    openAppId: String?,
    onOpen: (String) -> Unit,
    onCloseApp: () -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onAskAgent: (String) -> Unit,
) {
    val c = LocalClawColors.current

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Top bar
        Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
                }
                Text(
                    "我的应用",
                    color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Text(
                    "${miniApps.size} 个",
                    color = c.subtext, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        if (miniApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📱", fontSize = 48.sp)
                    Text("还没有应用", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "让 AI 为你创建第一个应用\n例如：\"帮我创建一个带数据库的记账应用\"\n或：\"创建一个可以爬数据的工具，用 Python 后端\"",
                        color = c.subtext, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(miniApps, key = { it.id }) { app ->
                    AppCard(app = app, onOpen = { onOpen(app.id) }, onDelete = { onDelete(app.id) })
                }
            }
        }
    }

    // App viewer dialog — shown when openAppId is set
    if (openAppId != null) {
        AppViewerDialog(
            appId = openAppId,
            onClose = onCloseApp,
            onAskAgent = onAskAgent,
        )
    }
}

@Composable
private fun AppCard(app: MiniApp, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = LocalClawColors.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Text(app.icon, fontSize = 36.sp)
            if (app.hasPython) {
                Text(
                    "🐍",
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        Text(
            app.title,
            color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        if (app.description.isNotBlank()) {
            Text(
                app.description,
                color = c.subtext, fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (confirmDelete) {
                Text(
                    "确认删除?",
                    color = c.red, fontSize = 10.sp,
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
                IconButton(onClick = { confirmDelete = false }, modifier = Modifier.size(28.dp)) {
                    Text("✕", fontSize = 12.sp, color = c.subtext)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Text("✓", fontSize = 12.sp, color = c.red)
                }
            } else {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete, contentDescription = null,
                        tint = c.subtext.copy(alpha = 0.5f), modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AppViewerDialog(
    appId: String,
    onClose: () -> Unit,
    onAskAgent: (String) -> Unit,
) {
    val app = ClawApplication.instance

    // Ensure Python runtime is started when the app opens, so callPython() works immediately
    LaunchedEffect(appId) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(app))
                }
            }
        }
    }

    // Full-screen overlay inside the Activity window (NOT a Dialog).
    // Dialog{} creates a separate window context where addJavascriptInterface doesn't bind correctly.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

                    // On page finish, evaluate the bridge setup script again as fallback in case
                    // the injected <script> ran before window.Android was available.
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val reinjection = app.miniAppStore.clawBridgeSetupJs()
                            view?.evaluateJavascript(
                                "(function(){ if(typeof window.Claw==='undefined'){ $reinjection } })();",
                                null,
                            )
                        }
                    }

                    // Load HTML content directly via loadDataWithBaseURL so WebView renders
                    // from a string rather than a file path — avoids file-access origin issues
                    // while keeping addJavascriptInterface binding intact.
                    val htmlFile = app.miniAppStore.htmlFile(appId)
                    val baseUrl = "file://${htmlFile.parent}/"
                    if (htmlFile.exists()) {
                        // Always inject fresh bridge script (idempotent IIFE, harmless if already present)
                        val html = app.miniAppStore.injectBridge(htmlFile.readText())
                        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    } else {
                        loadDataWithBaseURL(
                            baseUrl,
                            "<html><body style='background:#1a1a2e;color:#fff;padding:24px;font-family:sans-serif'>" +
                            "<h3>⚠ 应用未找到</h3><p>id: $appId</p></body></html>",
                            "text/html", "UTF-8", null,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

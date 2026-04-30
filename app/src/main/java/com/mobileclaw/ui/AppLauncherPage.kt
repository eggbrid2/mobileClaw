package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobileclaw.ClawApplication
import com.mobileclaw.app.MiniApp
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLauncherPage(
    miniApps: List<MiniApp>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var isEditMode by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditMode) { isEditMode = false }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Top bar using same pattern as other pages
        Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
                }
                Text(
                    "我的应用",
                    color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (miniApps.isNotEmpty()) {
                    Text(
                        if (isEditMode) "完成" else "${miniApps.size} 个",
                        color = if (isEditMode) c.accent else c.subtext,
                        fontSize = 13.sp,
                        fontWeight = if (isEditMode) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isEditMode = !isEditMode }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        if (miniApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("📱", fontSize = 48.sp)
                    Text("还没有应用", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "让 AI 为你创建第一个应用\n例如：\"帮我创建一个带数据库的记账应用\"",
                        color = c.subtext, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(miniApps, key = { it.id }) { app ->
                    AppLauncherIcon(
                        app = app,
                        isEditMode = isEditMode,
                        onOpen = { onOpen(app.id) },
                        onDelete = { onDelete(app.id) },
                        onEnterEditMode = { isEditMode = true },
                    )
                }
            }
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLauncherIcon(
    app: MiniApp,
    isEditMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onEnterEditMode: () -> Unit,
) {
    val c = LocalClawColors.current

    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = { if (isEditMode) { /* tap in edit mode dismisses */ } else onOpen() },
                onLongClick = { onEnterEditMode() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // Icon container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(listOf(c.accent.copy(alpha = 0.18f), c.card)),
                    )
                    .border(0.5.dp, c.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (app.icon.startsWith("/")) {
                    var bitmap by remember(app.icon) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(app.icon) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            bitmap = runCatching { BitmapFactory.decodeFile(app.icon) }.getOrNull()
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text("📱", fontSize = 28.sp)
                    }
                } else {
                    Text(app.icon, fontSize = 28.sp)
                }
            }
            // Python badge
            if (app.hasPython) {
                Text(
                    "🐍",
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp),
                )
            }
            // Delete badge in edit mode
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(c.red)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            app.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.width(64.dp),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppViewerDialog(
    appId: String,
    onClose: () -> Unit,
    onAskAgent: (String) -> Unit,
) {
    val app = ClawApplication.instance
    val c = LocalClawColors.current
    val miniApp = remember(appId) { app.miniAppStore.get(appId) }
    var appTitle by remember(appId) { mutableStateOf(miniApp?.let { "${it.icon} ${it.title}" } ?: appId) }

    LaunchedEffect(appId) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(app))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(c.surface).statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
                }
                Text(
                    appTitle,
                    color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

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
                    setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))

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

                    val htmlFile = app.miniAppStore.htmlFile(appId)
                    val baseUrl = "file://${htmlFile.parent}/"
                    if (htmlFile.exists()) {
                        val html = app.miniAppStore.injectBridge(htmlFile.readText())
                        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    } else {
                        loadDataWithBaseURL(
                            baseUrl,
                            "<html><body style='background:#1a1a2e;color:#fff;padding:24px;font-family:sans-serif'>" +
                            "<h3>⚠ App not found</h3><p>id: $appId</p></body></html>",
                            "text/html", "UTF-8", null,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

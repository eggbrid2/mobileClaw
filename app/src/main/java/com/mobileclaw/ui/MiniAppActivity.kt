package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val context = LocalContext.current
    val claw = ClawApplication.instance
    val miniApp = remember(appId) { claw.miniAppStore.get(appId) }
    val c = LocalClawColors.current

    var appTitle by remember(appId) { mutableStateOf(miniApp?.let { "${it.icon} ${it.title}" } ?: appId) }
    var showMore by remember { mutableStateOf(false) }

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
                @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = false
                setSupportZoom(false)
                builtInZoomControls = false
            }
            webChromeClient = WebChromeClient()

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
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val reinjection = claw.miniAppStore.clawBridgeSetupJs()
                    view?.evaluateJavascript(
                        "(function(){ if(typeof window.Claw==='undefined'){ $reinjection } })();", null,
                    )
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

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appTitle,
                        color = c.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showMore = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = c.subtext,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = c.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)
            }

            // ── WebView ───────────────────────────────────────────────────────
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }

        // ── More info panel ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMore,
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
            visible = showMore,
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

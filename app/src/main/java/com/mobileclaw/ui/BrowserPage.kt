package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobileclaw.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserPage(
    initialUrl: String = "https://www.bing.com",
    onBack: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    val c = LocalClawColors.current
    var currentUrl by remember { mutableStateOf(initialUrl.ensureHttps()) }
    var addressBarText by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // ── Title bar ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close), tint = c.text, modifier = Modifier.size(18.dp))
                }
                // Address bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.cardAlt)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = addressBarText,
                        onValueChange = { addressBarText = it },
                        textStyle = TextStyle(color = c.text, fontSize = 13.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val url = addressBarText.trim().toNavigationUrl()
                                currentUrl = url
                                addressBarText = url
                                webViewRef?.loadUrl(url)
                            },
                        ),
                        decorationBox = { inner ->
                            if (addressBarText.isEmpty()) {
                                Text(stringResource(R.string.browser_address_hint), color = c.subtext, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }
                IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = c.text, modifier = Modifier.size(18.dp))
                }
            }

            // Nav + send row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { webViewRef?.goBack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = c.text, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { webViewRef?.goForward() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "Forward", tint = c.text, modifier = Modifier.size(16.dp))
                }
                if (pageTitle.isNotBlank()) {
                    Text(
                        pageTitle,
                        color = c.subtext,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                // Send page to agent button
                val sendAgentMsgTemplate = stringResource(R.string.browser_send_agent_message)
                val sendAgentLabel = stringResource(R.string.browser_send_agent)
                androidx.compose.material3.TextButton(
                    onClick = {
                        val url = webViewRef?.url ?: currentUrl
                        onSendToAgent(sendAgentMsgTemplate.format(url))
                    },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(sendAgentLabel, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (loadProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accent,
                    trackColor = c.cardAlt,
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        // ── WebView ────────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress / 100f
                        }
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: ""
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            addressBarText = url
                            return false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) addressBarText = url
                        }
                    }
                    webViewRef = this
                    loadUrl(currentUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun String.ensureHttps(): String {
    if (startsWith("http://") || startsWith("https://")) return this
    return "https://$this"
}

private fun String.toNavigationUrl(): String {
    val trimmed = trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    // Looks like a domain?
    if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"
    // Search query
    val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
    return "https://www.bing.com/search?q=$encoded"
}

package com.mobileclaw.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Base64
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Hidden 1×1 WebView attached to the WindowManager overlay.
 * Enables the agent to browse URLs, extract page content, and run JavaScript
 * in the background without any visible UI.
 * All WebView operations must run on the Main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
class InAppWebViewManager(private val context: Context) {

    private var webView: WebView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun ensureWebView() {
        if (webView != null) return
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webChromeClient = android.webkit.WebChromeClient()
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(webView, params)
    }

    /** Loads [url] and suspends until the page finishes loading (or fails). */
    suspend fun browse(url: String): String = withContext(Dispatchers.Main) {
        ensureWebView()
        val wv = webView!!
        suspendCancellableCoroutine { cont ->
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (cont.isActive) cont.resume("Loaded: $url")
                }
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame && cont.isActive)
                        cont.resume("Error loading page: ${error.description}")
                }
            }
            wv.loadUrl(url)
        }
    }

    /** Extracts visible text from the element matching [selector] (default: body). */
    suspend fun getContent(selector: String = "body"): String = withContext(Dispatchers.Main) {
        ensureWebView()
        val safeSelector = selector.replace("'", "\\'")
        val script = """
            (function(){
                var el = document.querySelector('$safeSelector');
                if(!el) return 'Element not found: $safeSelector';
                var cloned = el.cloneNode(true);
                cloned.querySelectorAll('script,style,nav,footer,header,aside').forEach(function(n){n.remove();});
                return cloned.innerText.substring(0, 5000);
            })();
        """.trimIndent()
        suspendCancellableCoroutine { cont ->
            webView!!.evaluateJavascript(script) { raw ->
                val clean = raw
                    ?.trim('"')
                    ?.replace("\\n", "\n")
                    ?.replace("\\\"", "\"")
                    ?: "No content"
                cont.resume(clean)
            }
        }
    }

    /** Evaluates arbitrary JavaScript and returns the result as a string. */
    suspend fun evalJs(script: String): String = withContext(Dispatchers.Main) {
        ensureWebView()
        suspendCancellableCoroutine { cont ->
            webView!!.evaluateJavascript(script) { result ->
                cont.resume(result ?: "null")
            }
        }
    }

    fun currentUrl(): String = webView?.url ?: "about:blank"

    fun currentTitle(): String = webView?.title ?: ""

    /**
     * Captures a JPEG screenshot of the WebView's current content.
     * Uses [capturePicture] so the full document is captured regardless of the 1×1 overlay size.
     * Returns a base64 data URI, or null on failure.
     */
    @Suppress("DEPRECATION")
    suspend fun captureScreenshot(): String? = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext null
        runCatching {
            val picture = wv.capturePicture()
            val picW = picture.width.coerceIn(1, 1080)
            val picH = picture.height.coerceIn(1, 8000)
            // Cap height to ~16:9 equivalent so the thumbnail is not absurdly tall
            val capH = minOf(picH, (picW * 1.78f).toInt())
            val bitmap = Bitmap.createBitmap(picW, capH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            picture.draw(canvas)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 65, out)
            bitmap.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun destroy() {
        val wv = webView ?: return
        webView = null
        try { windowManager.removeView(wv) } catch (_: Exception) {}
        wv.destroy()
    }
}

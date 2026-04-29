package com.mobileclaw.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/** Native bridge exposed to the console WebView as `window.Android`. */
class ClawConsoleInterface(
    private val onSend: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun sendMessage(text: String) {
        if (text.isNotBlank()) mainHandler.post { onSend(text.trim()) }
    }

    @JavascriptInterface
    fun getVersion(): String = "1.0"
}

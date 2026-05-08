package com.mobileclaw.vpn

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController

object WebViewProxy {
    fun useVpnProxy(context: Context) {
        runCatching {
            val config = ProxyConfig.Builder()
                .addProxyRule("http://127.0.0.1:${MihomoConfigBuilder.HTTP_PORT}")
                .addBypassRule("127.0.0.1")
                .addBypassRule("localhost")
                .build()
            ProxyController.getInstance().setProxyOverride(config, context.mainExecutor) {
                android.util.Log.d("ClawVpn", "WebView proxy enabled on 127.0.0.1:${MihomoConfigBuilder.HTTP_PORT}")
            }
        }.onFailure {
            android.util.Log.w("ClawVpn", "WebView proxy enable failed: ${it.message}", it)
        }
    }

    fun clear(context: Context) {
        runCatching {
            ProxyController.getInstance().clearProxyOverride(context.mainExecutor) {
                android.util.Log.d("ClawVpn", "WebView proxy cleared")
            }
        }.onFailure {
            android.util.Log.w("ClawVpn", "WebView proxy clear failed: ${it.message}", it)
        }
    }
}

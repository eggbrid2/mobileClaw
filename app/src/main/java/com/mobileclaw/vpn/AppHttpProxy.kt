package com.mobileclaw.vpn

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object AppHttpProxy {
    private val proxyAddress = InetSocketAddress("127.0.0.1", MihomoConfigBuilder.HTTP_PORT)
    private val proxy = Proxy(Proxy.Type.HTTP, proxyAddress)
    private var previousSelector: ProxySelector? = null
    @Volatile private var enabled = false

    fun enable() {
        if (enabled) return
        previousSelector = ProxySelector.getDefault()
        ProxySelector.setDefault(selector)
        enabled = true
        android.util.Log.d("ClawVpn", "App HTTP proxy enabled on 127.0.0.1:${MihomoConfigBuilder.HTTP_PORT}")
    }

    fun disable() {
        if (!enabled) return
        ProxySelector.setDefault(previousSelector)
        previousSelector = null
        enabled = false
        android.util.Log.d("ClawVpn", "App HTTP proxy disabled")
    }

    fun proxySelector(): ProxySelector = selector

    private val selector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val scheme = uri?.scheme?.lowercase()
            val host = uri?.host?.lowercase()
            if (enabled && (scheme == "http" || scheme == "https") && host != null && !host.isLocalHost()) {
                return listOf(proxy)
            }
            return previousSelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            previousSelector?.connectFailed(uri, sa, ioe)
        }
    }

    private fun String.isLocalHost(): Boolean =
        this == "localhost" ||
            this == "127.0.0.1" ||
            this == "::1" ||
            startsWith("127.")
}

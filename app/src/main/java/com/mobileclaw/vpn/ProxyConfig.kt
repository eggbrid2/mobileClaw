package com.mobileclaw.vpn

import java.util.UUID

sealed class ProxyConfig {
    abstract val id: String
    abstract val name: String
    abstract val server: String
    abstract val port: Int

    data class Http(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProxyConfig()

    data class Socks5(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProxyConfig()

    data class Shadowsocks(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val password: String,
        val cipher: String = "aes-256-gcm",
    ) : ProxyConfig()

    data class ShadowsocksR(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val password: String,
        val cipher: String,
        val protocol: String,
        val protocolParam: String? = null,
        val obfs: String,
        val obfsParam: String? = null,
    ) : ProxyConfig()

    data class VMess(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val uuid: String,
        val alterId: Int = 0,
        val security: String = "auto",
        val network: String = "tcp",
        val tls: Boolean = false,
        val wsPath: String? = null,
        val wsHost: String? = null,
        val sni: String? = null,
        val fingerprint: String? = null,
        val alpn: List<String> = emptyList(),
        val allowInsecure: Boolean = false,
    ) : ProxyConfig()

    data class Trojan(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val password: String,
        val sni: String? = null,
        val network: String = "tcp",
        val wsPath: String? = null,
        val wsHost: String? = null,
        val fingerprint: String? = null,
        val alpn: List<String> = emptyList(),
        val allowInsecure: Boolean = false,
    ) : ProxyConfig()

    data class VLESS(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val uuid: String,
        val flow: String = "",
        val encryption: String = "none",
        val network: String = "tcp",
        val security: String = "none",
        val sni: String? = null,
        val wsPath: String? = null,
        val wsHost: String? = null,
        val fingerprint: String? = null,
        val alpn: List<String> = emptyList(),
        val realityPublicKey: String? = null,
        val realityShortId: String? = null,
        val realitySpiderX: String? = null,
        val allowInsecure: Boolean = false,
    ) : ProxyConfig()

    val typeName: String get() = when (this) {
        is Http -> "HTTP"
        is Socks5 -> "SOCKS5"
        is Shadowsocks -> "SS"
        is ShadowsocksR -> "SSR"
        is VMess -> "VMess"
        is Trojan -> "Trojan"
        is VLESS -> "VLESS"
    }
}

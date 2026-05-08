package com.mobileclaw.vpn

import android.util.Base64
import org.yaml.snakeyaml.Yaml
import java.net.URI

object ClashParser {

    fun parse(content: String): List<ProxyConfig> {
        val trimmed = content.trim()
        return try {
            parseYaml(trimmed)
        } catch (_: Exception) {
            parseUriList(trimmed)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYaml(yaml: String): List<ProxyConfig> {
        val map = Yaml().load<Map<String, Any>>(yaml)
            ?: throw IllegalArgumentException("empty yaml")
        val proxies = map["proxies"] as? List<Map<String, Any>>
            ?: throw IllegalArgumentException("no proxies key")
        return proxies.mapNotNull { p ->
            runCatching { parseYamlProxy(p) }.getOrNull()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlProxy(p: Map<String, Any>): ProxyConfig? {
        val name = p["name"] as? String ?: return null
        if (isSubscriptionInfoNode(name)) return null
        val server = p["server"] as? String ?: return null
        val port = (p["port"] as? Number)?.toInt() ?: return null
        val network = normalizeNetwork(p["network"] as? String ?: "tcp")
        val wsOpts = p["ws-opts"] as? Map<*, *>
        val wsHeaders = wsOpts?.get("headers") as? Map<*, *>
            ?: p["ws-headers"] as? Map<*, *>
        val h2Opts = p["h2-opts"] as? Map<*, *>
        val grpcOpts = p["grpc-opts"] as? Map<*, *>
        val serviceName = grpcOpts?.get("grpc-service-name") as? String
            ?: grpcOpts?.get("service-name") as? String
        val wsPath = wsOpts?.get("path") as? String
            ?: p["ws-path"] as? String
            ?: h2Opts?.get("path") as? String
        val wsHost = headerValue(wsHeaders, "Host")
            ?: firstString(h2Opts?.get("host"))
        val sni = firstString(p["servername"]) ?: firstString(p["sni"])
        val allowInsecure = asBoolean(p["skip-cert-verify"])
        val fingerprint = firstString(p["client-fingerprint"])
            ?: firstString(p["fingerprint"])
            ?: firstString(p["fp"])
        val alpn = stringList(p["alpn"])
        val realityOpts = p["reality-opts"] as? Map<*, *>
        return when (p["type"] as? String) {
            "http" -> ProxyConfig.Http(
                name = name, server = server, port = port,
                username = p["username"] as? String,
                password = p["password"] as? String,
            )
            "socks5" -> ProxyConfig.Socks5(
                name = name, server = server, port = port,
                username = p["username"] as? String,
                password = p["password"] as? String,
            )
            "ss" -> ProxyConfig.Shadowsocks(
                name = name, server = server, port = port,
                password = p["password"] as? String ?: return null,
                cipher = p["cipher"] as? String ?: "aes-256-gcm",
            )
            "ssr" -> ProxyConfig.ShadowsocksR(
                name = name, server = server, port = port,
                password = p["password"] as? String ?: return null,
                cipher = p["cipher"] as? String ?: return null,
                protocol = p["protocol"] as? String ?: return null,
                protocolParam = p["protocol-param"] as? String,
                obfs = p["obfs"] as? String ?: return null,
                obfsParam = p["obfs-param"] as? String,
            )
            "vmess" -> {
                ProxyConfig.VMess(
                    name = name, server = server, port = port,
                    uuid = p["uuid"] as? String ?: return null,
                    alterId = (p["alterId"] as? Number)?.toInt() ?: 0,
                    security = p["cipher"] as? String ?: "auto",
                    network = if (network == "grpc") "grpc" else network,
                    tls = asBoolean(p["tls"]),
                    wsPath = if (network == "grpc") serviceName else wsPath,
                    wsHost = wsHost,
                    sni = sni,
                    fingerprint = fingerprint,
                    alpn = alpn,
                    allowInsecure = allowInsecure,
                )
            }
            "trojan" -> ProxyConfig.Trojan(
                name = name, server = server, port = port,
                password = p["password"] as? String ?: return null,
                sni = sni,
                network = network,
                wsPath = if (network == "grpc") serviceName else wsPath,
                wsHost = wsHost,
                fingerprint = fingerprint,
                alpn = alpn,
                allowInsecure = allowInsecure,
            )
            "vless" -> ProxyConfig.VLESS(
                name = name, server = server, port = port,
                uuid = p["uuid"] as? String ?: return null,
                flow = p["flow"] as? String ?: "",
                network = network,
                security = firstString(p["security"])
                    ?: if (realityOpts != null) "reality"
                    else if (asBoolean(p["tls"])) "tls"
                    else "none",
                sni = sni,
                wsPath = if (network == "grpc") serviceName else wsPath,
                wsHost = wsHost,
                fingerprint = fingerprint,
                alpn = alpn,
                realityPublicKey = realityOpts?.let { firstString(it["public-key"]) }
                    ?: firstString(p["pbk"]),
                realityShortId = realityOpts?.let { firstString(it["short-id"]) }
                    ?: firstString(p["sid"]),
                realitySpiderX = realityOpts?.let { firstString(it["spider-x"]) },
                allowInsecure = allowInsecure,
            )
            else -> null
        }
    }

    private fun parseUriList(content: String): List<ProxyConfig> {
        val decoded = try {
            String(Base64.decode(content, Base64.DEFAULT))
        } catch (_: Exception) {
            content
        }
        return decoded.lines().mapNotNull { line ->
            runCatching { parseUri(line.trim()) }.getOrNull()
        }
    }

    private fun parseUri(uri: String): ProxyConfig? {
        return when {
            uri.startsWith("ss://") -> parseSsUri(uri)
            uri.startsWith("ssr://") -> null
            uri.startsWith("vmess://") -> parseVmessUri(uri)
            uri.startsWith("trojan://") -> parseTrojanUri(uri)
            uri.startsWith("vless://") -> parseVlessUri(uri)
            uri.startsWith("http://") || uri.startsWith("https://") -> parseHttpUri(uri)
            uri.startsWith("socks5://") -> parseSocks5Uri(uri)
            else -> null
        }
    }

    private fun parseSsUri(uri: String): ProxyConfig? {
        // ss://BASE64(method:password)@server:port#name
        // or ss://BASE64(method:password@server:port)#name
        val withoutScheme = uri.removePrefix("ss://")
        val (main, rawName) = withoutScheme.split("#", limit = 2)
            .let { if (it.size == 2) it[0] to it[1] else it[0] to "" }
        val name = java.net.URLDecoder.decode(rawName.ifBlank { "SS" }, "UTF-8")

        return if (main.contains("@")) {
            // SIP002 format: BASE64(method:password)@server:port
            val atIdx = main.lastIndexOf("@")
            val userInfo = String(Base64.decode(main.substring(0, atIdx), Base64.DEFAULT))
            val hostPort = main.substring(atIdx + 1)
            val (method, password) = userInfo.split(":", limit = 2)
            val (server, portStr) = splitHostPort(hostPort)
            ProxyConfig.Shadowsocks(name = name, server = server, port = portStr.toInt(),
                password = password, cipher = method)
        } else {
            // Legacy: BASE64(method:password@server:port)
            val decoded = String(Base64.decode(main, Base64.DEFAULT))
            val atIdx = decoded.lastIndexOf("@")
            val userInfo = decoded.substring(0, atIdx)
            val hostPort = decoded.substring(atIdx + 1)
            val colonIdx = userInfo.indexOf(":")
            val method = userInfo.substring(0, colonIdx)
            val password = userInfo.substring(colonIdx + 1)
            val (server, portStr) = splitHostPort(hostPort)
            ProxyConfig.Shadowsocks(name = name, server = server, port = portStr.toInt(),
                password = password, cipher = method)
        }
    }

    private fun parseVmessUri(uri: String): ProxyConfig? {
        val b64 = uri.removePrefix("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT))
        val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
        val name = obj.get("ps")?.asString ?: "VMess"
        val server = obj.get("add")?.asString ?: return null
        val port = obj.get("port")?.asString?.toIntOrNull() ?: return null
        val uuid = obj.get("id")?.asString ?: return null
        val alterId = obj.get("aid")?.asString?.toIntOrNull() ?: 0
        val network = normalizeNetwork(obj.get("net")?.asString ?: "tcp")
        val tls = obj.get("tls")?.asString == "tls"
        val wsPath = obj.get("path")?.asString
        val wsHost = obj.get("host")?.asString
        val sni = obj.get("sni")?.asString ?: obj.get("serverName")?.asString
        val alpn = obj.get("alpn")?.asString?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val security = obj.get("scy")?.asString ?: "auto"
        val fingerprint = obj.get("fp")?.asString ?: obj.get("fingerprint")?.asString
        return ProxyConfig.VMess(name = name, server = server, port = port, uuid = uuid,
            alterId = alterId, security = security, network = network, tls = tls,
            wsPath = wsPath?.ifBlank { null }, wsHost = wsHost?.ifBlank { null },
            sni = sni?.ifBlank { null }, fingerprint = fingerprint?.ifBlank { null }, alpn = alpn)
    }

    private fun parseTrojanUri(uri: String): ProxyConfig? {
        val parsed = URI(uri)
        val name = java.net.URLDecoder.decode(
            parsed.fragment?.ifBlank { "Trojan" } ?: "Trojan", "UTF-8")
        val password = parsed.userInfo ?: return null
        val server = parsed.host ?: return null
        val port = if (parsed.port > 0) parsed.port else 443
        val query = parseQuery(parsed.rawQuery)
        val sni = query["sni"] ?: query["peer"] ?: query["servername"]
        val network = normalizeNetwork(query["type"] ?: "tcp")
        val wsPath = query["path"] ?: query["serviceName"]
        val wsHost = query["host"]
        val alpn = query["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return ProxyConfig.Trojan(name = name, server = server, port = port,
            password = password, sni = sni, network = network,
            wsPath = wsPath, wsHost = wsHost, fingerprint = query["fp"] ?: query["fingerprint"], alpn = alpn)
    }

    private fun parseVlessUri(uri: String): ProxyConfig? {
        val parsed = URI(uri)
        val name = java.net.URLDecoder.decode(
            parsed.fragment?.ifBlank { "VLESS" } ?: "VLESS", "UTF-8")
        val uuid = parsed.userInfo ?: return null
        val server = parsed.host ?: return null
        val port = if (parsed.port > 0) parsed.port else 443
        val query = parseQuery(parsed.rawQuery)
        val flow = query["flow"] ?: ""
        val network = normalizeNetwork(query["type"] ?: "tcp")
        val security = query["security"] ?: "none"
        val sni = query["sni"] ?: query["servername"]
        val wsPath = query["path"] ?: query["serviceName"]
        val wsHost = query["host"]
        val alpn = query["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return ProxyConfig.VLESS(name = name, server = server, port = port, uuid = uuid,
            flow = flow,
            network = network,
            security = security,
            sni = sni,
            wsPath = wsPath,
            wsHost = wsHost,
            fingerprint = query["fp"],
            alpn = alpn,
            realityPublicKey = query["pbk"] ?: query["publicKey"],
            realityShortId = query["sid"] ?: query["shortId"],
            realitySpiderX = query["spx"])
    }

    private fun parseHttpUri(uri: String): ProxyConfig? {
        val parsed = URI(uri)
        val name = java.net.URLDecoder.decode(
            parsed.fragment?.ifBlank { parsed.host } ?: parsed.host ?: "HTTP", "UTF-8")
        val server = parsed.host ?: return null
        val port = if (parsed.port > 0) parsed.port else 80
        val userInfo = parsed.userInfo
        val username = userInfo?.substringBefore(":")
        val password = userInfo?.substringAfter(":", "")?.ifBlank { null }
        return ProxyConfig.Http(name = name, server = server, port = port,
            username = username, password = password)
    }

    private fun parseSocks5Uri(uri: String): ProxyConfig? {
        val parsed = URI(uri)
        val name = java.net.URLDecoder.decode(
            parsed.fragment?.ifBlank { parsed.host } ?: parsed.host ?: "SOCKS5", "UTF-8")
        val server = parsed.host ?: return null
        val port = if (parsed.port > 0) parsed.port else 1080
        val userInfo = parsed.userInfo
        val username = userInfo?.substringBefore(":")
        val password = userInfo?.substringAfter(":", "")?.ifBlank { null }
        return ProxyConfig.Socks5(name = name, server = server, port = port,
            username = username, password = password)
    }

    private fun splitHostPort(hostPort: String): Pair<String, String> {
        val lastColon = hostPort.lastIndexOf(":")
        return hostPort.substring(0, lastColon) to hostPort.substring(lastColon + 1)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { kv ->
            val eq = kv.indexOf("=")
            if (eq < 0) null
            else java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8") to
                java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
        }.toMap()
    }

    private fun asBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) ||
            value.equals("yes", ignoreCase = true) ||
            value.equals("on", ignoreCase = true) ||
            value.equals("tls", ignoreCase = true) ||
            value == "1"
        is Number -> value.toInt() != 0
        else -> false
    }

    private fun normalizeNetwork(value: String): String = when (value.lowercase()) {
        "h2", "http" -> "http"
        else -> value.lowercase()
    }

    private fun isSubscriptionInfoNode(name: String): Boolean {
        val normalized = name.trim()
        return normalized.startsWith("网址") ||
            normalized.startsWith("剩余流量") ||
            normalized.startsWith("过期时间") ||
            normalized.startsWith("官网") ||
            normalized.startsWith("订阅") ||
            normalized.startsWith("回家页")
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
        is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }

    private fun firstString(value: Any?): String? = when (value) {
        is List<*> -> value.firstOrNull()?.toString()
        is String -> value
        else -> null
    }

    private fun headerValue(headers: Map<*, *>?, name: String): String? {
        if (headers == null) return null
        return headers.entries.firstOrNull { (key, _) ->
            key?.toString()?.equals(name, ignoreCase = true) == true
        }?.value?.toString()
    }
}

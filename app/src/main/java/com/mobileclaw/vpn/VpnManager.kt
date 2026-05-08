package com.mobileclaw.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.google.gson.Gson
import com.mobileclaw.R
import com.mobileclaw.memory.db.ClawDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

enum class VpnStatus { IDLE, CONNECTING, CONNECTED, ERROR }

data class VpnSubscription(
    val entity: SubscriptionEntity,
    val proxies: List<ProxyConfig>,
)

class VpnManager(private val context: Context) {

    companion object {
        const val LATENCY_ERROR = -2L
        const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
        val latencyPort = AtomicInteger(12000)
    }

    private val db = ClawDatabase.getInstance(context).subscriptionDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _status = MutableStateFlow(VpnStatus.IDLE)
    val status: StateFlow<VpnStatus> = _status

    init {
        scope.launch {
            ClawVpnService.isRunningFlow.collect { running ->
                if (running) {
                    _status.value = VpnStatus.CONNECTED
                } else if (_status.value == VpnStatus.CONNECTED || _status.value == VpnStatus.CONNECTING) {
                    _status.value = VpnStatus.IDLE
                }
            }
        }
    }

    val subscriptions: Flow<List<VpnSubscription>> = db.getAll().map { entities ->
        entities.map { entity ->
        val proxies = parseProxiesJson(entity.proxiesJson)
            VpnSubscription(entity, proxies)
        }
    }

    /** Returns a VpnService.prepare() intent if permission is needed, null if already granted. */
    fun prepareIntent(): Intent? = VpnService.prepare(context)

    fun startVpn(sub: VpnSubscription, proxy: ProxyConfig) {
        _status.value = VpnStatus.CONNECTING
        scope.launch {
            runCatching {
                val rawYaml = ensureRawConfig(sub)
                val mihomoConfig = MihomoConfigBuilder.build(rawYaml, proxy.name)
                val configFile = File(context.filesDir, "mihomo-runtime-${sub.entity.id}.yml").apply {
                    writeText(mihomoConfig)
                }
                logMihomoConfigSummary(configFile)
                android.util.Log.d(
                    "ClawVpn",
                    "Start VPN selected=${proxy.name} remote=${proxy.server}:${proxy.port} localMixed=${MihomoConfigBuilder.MIXED_PORT}"
                )
                val intent = Intent(context, ClawVpnService::class.java).apply {
                    action = ClawVpnService.ACTION_START
                    putExtra(ClawVpnService.EXTRA_CLASH_CONFIG_PATH, configFile.absolutePath)
                    putExtra(ClawVpnService.EXTRA_PROXY_NAME, proxy.name)
                }
                context.startService(intent)
            }.onFailure { error ->
                android.util.Log.e("ClawVpn", "Start VPN failed: ${error.message}", error)
                _status.value = VpnStatus.ERROR
            }
        }
    }

    fun stopVpn() {
        val intent = Intent(context, ClawVpnService::class.java).apply {
            action = ClawVpnService.ACTION_STOP
        }
        context.startService(intent)
        _status.value = VpnStatus.IDLE
    }

    fun syncStatus() {
        _status.value = if (ClawVpnService.isRunning) VpnStatus.CONNECTED else VpnStatus.IDLE
    }

    suspend fun addSubscription(name: String, url: String): Result<VpnSubscription> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = fetchUrl(url)
                val proxies = ClashParser.parse(content)
                val entity = SubscriptionEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    updatedAt = System.currentTimeMillis(),
                    proxiesJson = gson.toJson(proxies.map { it.toSerializable() }),
                    configYaml = content,
                )
                db.upsert(entity)
                VpnSubscription(entity, proxies)
            }
        }

    suspend fun updateSubscription(sub: VpnSubscription): Result<VpnSubscription> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = fetchUrl(sub.entity.url)
                val proxies = ClashParser.parse(content)
                val updated = sub.entity.copy(
                    updatedAt = System.currentTimeMillis(),
                    proxiesJson = gson.toJson(proxies.map { it.toSerializable() }),
                    configYaml = content,
                )
                db.upsert(updated)
                VpnSubscription(updated, proxies)
            }
        }

    suspend fun deleteSubscription(id: String) = withContext(Dispatchers.IO) {
        db.delete(id)
    }

    suspend fun selectProxy(subId: String, proxyId: String?) = withContext(Dispatchers.IO) {
        db.selectProxy(subId, proxyId)
    }

    private suspend fun ensureRawConfig(sub: VpnSubscription): String = withContext(Dispatchers.IO) {
        val latest = db.getById(sub.entity.id) ?: sub.entity
        if (latest.configYaml.isNotBlank()) {
            return@withContext latest.configYaml
        }
        android.util.Log.d("ClawVpn", "Backfilling raw Clash YAML for subscription ${latest.name}")
        val content = fetchUrl(latest.url)
        val proxies = ClashParser.parse(content)
        db.upsert(
            latest.copy(
                updatedAt = System.currentTimeMillis(),
                proxiesJson = gson.toJson(proxies.map { it.toSerializable() }),
                configYaml = content,
            )
        )
        content
    }

    /**
     * Diagnostic: SOCKS5 greeting + CONNECT against mihomo's local mixed-port.
     */
    suspend fun testProxyReachable(): String = withContext(Dispatchers.IO) {
        val port = MihomoConfigBuilder.MIXED_PORT
        try {
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress("127.0.0.1", port), 3000)
                sock.soTimeout = 3000
                sock.outputStream.apply { write(byteArrayOf(5, 1, 0)); flush() }
                val buf = ByteArray(2)
                if (sock.inputStream.read(buf) != 2 || buf[0] != 5.toByte()) {
                    return@withContext "SOCKS greeting failed on :$port"
                }
            }
        } catch (e: Exception) {
            return@withContext context.getString(R.string.socks_unreachable, port, e.message?.take(60) ?: "")
        }
        return@withContext try {
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress("127.0.0.1", port), 3000)
                sock.soTimeout = 8000
                val out = sock.outputStream
                val ins = sock.inputStream
                out.write(byteArrayOf(5, 1, 0)); out.flush()
                ins.read(ByteArray(2))
                val host = "www.cloudflare.com".toByteArray()
                val req = mutableListOf<Byte>()
                req.add(5); req.add(1); req.add(0); req.add(3)
                req.add(host.size.toByte()); req.addAll(host.toList())
                req.add(((443 shr 8) and 0xff).toByte()); req.add((443 and 0xff).toByte())
                out.write(req.toByteArray()); out.flush()
                val resp = ByteArray(10)
                val n = ins.read(resp)
                when {
                    n < 2 -> context.getString(R.string.vpn_diag_no_response)
                    resp[1] == 0.toByte() -> context.getString(R.string.vpn_diag_ok)
                    else -> context.getString(R.string.vpn_diag_rejected, resp[1].toInt() and 0xff)
                }
            }
        } catch (e: Exception) {
            context.getString(R.string.vpn_diag_failed, e.message?.take(80) ?: "")
        }
    }

    /**
     * Measures delay through a short-lived mihomo process using the raw Clash config.
     */
    private suspend fun testLatency(sub: VpnSubscription, proxy: ProxyConfig): Long = withContext(Dispatchers.IO) {
        val socksPort = latencyPort.updateAndGet { port -> if (port >= 12980) 12000 else port + 3 }
        val httpPort = socksPort + 1
        val ctrlPort = socksPort + 2
        val workDir = File(context.cacheDir, "mihomo-latency-${proxy.id}")
        val configFile = File(workDir, "config.yml")
        var mihomo: MihomoProcess? = null
        runCatching {
            workDir.mkdirs()
            val rawYaml = ensureRawConfig(sub)
            configFile.writeText(MihomoConfigBuilder.build(rawYaml, proxy.name, socksPort, httpPort, ctrlPort))
            logMihomoConfigSummary(configFile)
            mihomo = MihomoProcess(context)
            android.util.Log.d(
                "ClawVpn",
                "Latency test via mihomo for ${proxy.name}: remote=${proxy.server}:${proxy.port}, localMixed=127.0.0.1:$socksPort"
            )
            mihomo.start(configFile, workDir)
            waitForLocalPort(socksPort)
            measureUrlTestLatency(socksPort)
        }.getOrElse { error ->
            android.util.Log.w("ClawVpn", "Latency test failed for ${proxy.name}: ${error.message}", error)
            LATENCY_ERROR
        }.also {
            mihomo?.stop()
        }
    }

    private fun measureUrlTestLatency(socksPort: Int): Long {
        val start = System.currentTimeMillis()
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(DELAY_TEST_URL).build()).execute().use { response ->
            if (response.code !in 200..399) {
                throw IllegalStateException("URL test HTTP ${response.code}")
            }
        }
        return System.currentTimeMillis() - start
    }

    private fun waitForLocalPort(port: Int) {
        repeat(100) {
            runCatching {
                java.net.Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                }
            }.onSuccess { return }
            Thread.sleep(100)
        }
        throw IllegalStateException("mihomo local port :$port did not start")
    }

    private fun logMihomoConfigSummary(configFile: File) {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load<Map<String, Any?>>(configFile.readText()) ?: emptyMap()
            val interesting = listOf(
                "mixed-port=${root["mixed-port"]}",
                "http-port=${root["port"]}",
                "geodata-mode=${root["geodata-mode"]}",
                "geo-auto-update=${root["geo-auto-update"]}",
            ).joinToString(" | ")
            android.util.Log.d("ClawVpn", "mihomo config summary: $interesting")
        }
    }

    suspend fun testAllLatencies(
        sub: VpnSubscription,
        onResult: (proxyId: String, ms: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        for (proxy in sub.proxies) {
            val ms = testLatency(sub, proxy)
            onResult(proxy.id, ms)
        }
    }

    private fun fetchUrl(url: String): String {
        val client = OkHttpClient()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        return response.body?.string() ?: throw IllegalStateException("Empty response")
    }

    private fun parseProxiesJson(json: String): List<ProxyConfig> {
        return try {
            val list = gson.fromJson(json, Array<SerializableProxy>::class.java)
            list.mapNotNull { it.toProxyConfig() }
                .filterNot { it.name.isSubscriptionInfoName() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private fun String.isSubscriptionInfoName(): Boolean {
    val normalized = trim()
    return normalized.startsWith("网址") ||
        normalized.startsWith("剩余流量") ||
        normalized.startsWith("过期时间") ||
        normalized.startsWith("官网") ||
        normalized.startsWith("订阅") ||
        normalized.startsWith("回家页")
}

/** Flat serializable form of ProxyConfig for JSON storage. */
private data class SerializableProxy(
    val type: String,
    val id: String,
    val name: String,
    val server: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val cipher: String? = null,
    val uuid: String? = null,
    val alterId: Int = 0,
    val security: String? = null,
    val network: String? = null,
    val tls: Boolean = false,
    val wsPath: String? = null,
    val wsHost: String? = null,
    val sni: String? = null,
    val alpn: List<String> = emptyList(),
    val flow: String? = null,
    val encryption: String? = null,
    val fingerprint: String? = null,
    val protocol: String? = null,
    val protocolParam: String? = null,
    val obfs: String? = null,
    val obfsParam: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
    val realitySpiderX: String? = null,
    val allowInsecure: Boolean = false,
)

private fun ProxyConfig.toSerializable() = when (this) {
    is ProxyConfig.Http -> SerializableProxy("http", id, name, server, port,
        username = username, password = password)
    is ProxyConfig.Socks5 -> SerializableProxy("socks5", id, name, server, port,
        username = username, password = password)
    is ProxyConfig.Shadowsocks -> SerializableProxy("ss", id, name, server, port,
        password = password, cipher = cipher)
    is ProxyConfig.ShadowsocksR -> SerializableProxy("ssr", id, name, server, port,
        password = password, cipher = cipher, protocol = protocol, protocolParam = protocolParam,
        obfs = obfs, obfsParam = obfsParam)
    is ProxyConfig.VMess -> SerializableProxy("vmess", id, name, server, port,
        uuid = uuid, alterId = alterId, security = security, network = network,
        tls = tls, wsPath = wsPath, wsHost = wsHost, sni = sni,
        fingerprint = fingerprint, alpn = alpn, allowInsecure = allowInsecure)
    is ProxyConfig.Trojan -> SerializableProxy("trojan", id, name, server, port,
        password = password, sni = sni, network = network, wsPath = wsPath,
        wsHost = wsHost, fingerprint = fingerprint, alpn = alpn, allowInsecure = allowInsecure)
    is ProxyConfig.VLESS -> SerializableProxy("vless", id, name, server, port,
        uuid = uuid, flow = flow, encryption = encryption, network = network,
        security = security, wsPath = wsPath, wsHost = wsHost, sni = sni,
        fingerprint = fingerprint, alpn = alpn, realityPublicKey = realityPublicKey,
        realityShortId = realityShortId, realitySpiderX = realitySpiderX,
        allowInsecure = allowInsecure)
}

private fun SerializableProxy.toProxyConfig(): ProxyConfig? {
    return when (type) {
        "http"   -> ProxyConfig.Http(id, name, server, port, username, password)
        "socks5" -> ProxyConfig.Socks5(id, name, server, port, username, password)
        "ss"     -> password?.let { pw -> ProxyConfig.Shadowsocks(id, name, server, port, pw, cipher ?: "aes-256-gcm") }
        "ssr"    -> password?.let { pw -> ProxyConfig.ShadowsocksR(id, name, server, port,
            pw, cipher ?: return null, protocol ?: return null, protocolParam, obfs ?: return null, obfsParam) }
        "vmess"  -> uuid?.let { uid -> ProxyConfig.VMess(id, name, server, port,
            uid, alterId, security ?: "auto", network ?: "tcp", tls, wsPath, wsHost, sni, fingerprint, alpn, allowInsecure) }
        "trojan" -> password?.let { pw -> ProxyConfig.Trojan(id, name, server, port,
            pw, sni, network ?: "tcp", wsPath, wsHost, fingerprint, alpn, allowInsecure) }
        "vless"  -> uuid?.let { uid -> ProxyConfig.VLESS(id, name, server, port,
            uid, flow ?: "", encryption ?: "none", network ?: "tcp", security ?: if (tls) "tls" else "none",
            sni, wsPath, wsHost, fingerprint, alpn, realityPublicKey, realityShortId, realitySpiderX, allowInsecure) }
        else     -> null
    }
}

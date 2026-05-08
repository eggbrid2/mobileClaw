package com.mobileclaw.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ClawVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.mobileclaw.vpn.START"
        const val ACTION_STOP = "com.mobileclaw.vpn.STOP"
        const val EXTRA_CLASH_CONFIG = "clash_config"
        const val EXTRA_CLASH_CONFIG_PATH = "clash_config_path"
        const val EXTRA_PROXY_NAME = "proxy_name"
        private const val NOTIF_CHANNEL = "vpn_channel"
        private const val NOTIF_ID = 7001
        private const val TUN_IPV4 = "198.18.0.1"
        private const val MAP_DNS_IPV4 = "198.18.0.2"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow

        @Volatile var isRunning = false
            private set
    }

    private var mihomoProcess: MihomoProcess? = null
    private var tunPfd: android.os.ParcelFileDescriptor? = null
    @Volatile private var tproxyRunning = false
    @Volatile private var bootstrapThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanupCurrentSession()
            removeForegroundNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        val clashConfigPath = intent?.getStringExtra(EXTRA_CLASH_CONFIG_PATH)
        val clashConfig = intent?.getStringExtra(EXTRA_CLASH_CONFIG)
        val proxyName = intent?.getStringExtra(EXTRA_PROXY_NAME) ?: "Proxy"
        if (clashConfigPath == null && clashConfig == null) return START_NOT_STICKY
        android.util.Log.d("ClawVpn", "Starting VPN for $proxyName, config=${clashConfigPath ?: "${clashConfig?.length ?: 0} bytes"}")

        ensureNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(proxyName))
        cleanupCurrentSession()

        // Exclude this app's own traffic from the VPN so mihomo's outbound connections
        // to the proxy server don't loop back through the TUN interface.
        val pfd = Builder()
            .addAddress(TUN_IPV4, 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(MAP_DNS_IPV4)
            .setMtu(1500)
            .setSession("MobileClaw VPN")
            .addDisallowedApplication(packageName)
            .setBlocking(true)
            .establish()

        if (pfd == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        tunPfd = pfd

        // dup() gives native code its own fd ownership so it doesn't close our tunPfd's fd underneath us.
        val tunFd = pfd.dup().detachFd()
        android.util.Log.d("ClawVpn", "TUN fd=${pfd.fd}  dupFd=$tunFd")

        bootstrapThread = Thread {
            try {
                val mihomo = MihomoProcess(this)
                mihomoProcess = mihomo
                val mihomoConfig = clashConfigPath?.let { File(it) }
                    ?: writeMihomoConfig(clashConfig ?: throw IllegalStateException("Missing mihomo config"))
                mihomo.start(mihomoConfig, File(filesDir, "mihomo"))
                isRunning = true
                _isRunningFlow.value = true

                waitForLocalPorts()
                logLocalSocksProbe()
                logLocalHttpProbe("before-hev")
                AppHttpProxy.enable()
                WebViewProxy.useVpnProxy(this)
                val configFile = writeTProxyConfig()
                HevTunnelBridge.start(configFile.absolutePath, tunFd)
                tproxyRunning = true
                android.util.Log.d("ClawVpn", "hev-socks5-tunnel started with ${configFile.absolutePath}")
                logLocalHttpProbe("after-hev")
                startTProxyStatsLogger()
            } catch (e: Exception) {
                android.util.Log.e("ClawVpn", "VPN start failed: ${e.message}", e)
                _isRunningFlow.value = false
                isRunning = false
                cleanupCurrentSession()
                removeForegroundNotification()
                stopSelf()
            }
        }.apply { name = "ClawVpnBootstrap"; start() }

        return START_STICKY
    }

    override fun onDestroy() {
        cleanupCurrentSession()
        removeForegroundNotification()
        super.onDestroy()
    }

    private fun cleanupCurrentSession() {
        bootstrapThread?.interrupt()
        bootstrapThread = null
        if (tproxyRunning) {
            try { HevTunnelBridge.stop() } catch (_: Exception) {}
            tproxyRunning = false
        }
        WebViewProxy.clear(this)
        AppHttpProxy.disable()
        mihomoProcess?.stop()
        mihomoProcess = null
        runCatching { tunPfd?.close() }
        tunPfd = null
        isRunning = false
        _isRunningFlow.value = false
    }

    private fun removeForegroundNotification() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        }
    }

    private fun waitForLocalPorts() {
        waitForLocalPort(MihomoConfigBuilder.MIXED_PORT)
        waitForLocalPort(MihomoConfigBuilder.HTTP_PORT)
    }

    private fun waitForLocalPort(port: Int) {
        repeat(40) {
            runCatching {
                java.net.Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                }
            }.onSuccess { return }
            Thread.sleep(50)
        }
        throw IllegalStateException("mihomo local port :$port did not start")
    }

    private fun logLocalSocksProbe() {
        runCatching {
            java.net.Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", MihomoConfigBuilder.MIXED_PORT), 3000)
                socket.soTimeout = 8000
                val out = socket.outputStream
                val input = socket.inputStream
                out.write(byteArrayOf(5, 1, 0))
                out.flush()
                val greeting = ByteArray(2)
                input.read(greeting)
                val host = "www.cloudflare.com".toByteArray(Charsets.UTF_8)
                val req = ByteArray(7 + host.size)
                req[0] = 5
                req[1] = 1
                req[2] = 0
                req[3] = 3
                req[4] = host.size.toByte()
                System.arraycopy(host, 0, req, 5, host.size)
                req[5 + host.size] = ((443 shr 8) and 0xff).toByte()
                req[6 + host.size] = (443 and 0xff).toByte()
                out.write(req)
                out.flush()
                val resp = ByteArray(4)
                input.read(resp)
                android.util.Log.d("ClawVpn", "local SOCKS probe rep=${resp.getOrNull(1)?.toInt()?.and(0xff)}")
            }
        }.onFailure {
            android.util.Log.w("ClawVpn", "local SOCKS probe failed: ${it.message}", it)
        }
    }

    private fun logLocalHttpProbe(stage: String) {
        runCatching {
            val start = System.currentTimeMillis()
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MihomoConfigBuilder.MIXED_PORT)))
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(VpnManager.DELAY_TEST_URL).build()).execute().use { response ->
                android.util.Log.d(
                    "ClawVpn",
                    "local HTTPS probe[$stage] code=${response.code} elapsed=${System.currentTimeMillis() - start}ms"
                )
            }
        }.onFailure {
            android.util.Log.w("ClawVpn", "local HTTPS probe[$stage] failed: ${it.message}", it)
        }
    }

    private fun writeTProxyConfig(): File {
        val file = File(filesDir, "hev-socks5-tunnel.yml")
        file.writeText(
            """
            tunnel:
              name: tun0
              ipv4: $TUN_IPV4
              mtu: 1500
              multi-queue: false
            socks5:
              address: 127.0.0.1
              port: ${MihomoConfigBuilder.MIXED_PORT}
              udp: 'tcp'
              udp-address: 127.0.0.1
            mapdns:
              address: $MAP_DNS_IPV4
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              task-stack-size: 20480
              tcp-buffer-size: 65536
              connect-timeout: 5000
              tcp-read-write-timeout: 60000
              log-level: debug
            """.trimIndent()
        )
        return file
    }

    private fun writeMihomoConfig(config: String): File {
        val file = File(filesDir, "mihomo.yml")
        file.writeText(config)
        return file
    }

    private fun startTProxyStatsLogger() {
        Thread({
            while (tproxyRunning) {
                runCatching {
                    val stats = HevTunnelBridge.stats()
                    android.util.Log.d("ClawVpn", "hev stats=${stats.joinToString(prefix = "[", postfix = "]")}")
                }
                Thread.sleep(5000)
            }
        }, "ClawHevStats").start()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(proxyName: String): Notification {
        val stopIntent = Intent(this, ClawVpnService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("MobileClaw VPN")
            .setContentText("Connected: $proxyName")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .setOngoing(true)
            .build()
    }
}

package com.mobileclaw.vpn

import android.content.Context
import java.io.File

class MihomoProcess(private val context: Context) {
    private var process: Process? = null

    fun start(configFile: File, workDir: File) {
        stop()
        workDir.mkdirs()
        ensureCountryDatabase(workDir)
        val binary = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so")
        android.util.Log.d(
            "ClawVpn",
            "mihomo binary=${binary.absolutePath} exists=${binary.exists()} canExecute=${binary.canExecute()} size=${binary.length()}"
        )
        if (!binary.canExecute()) {
            binary.setExecutable(true)
        }
        process = ProcessBuilder(
            binary.absolutePath,
            "-d", workDir.absolutePath,
            "-f", configFile.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
            .also { proc ->
                Thread({
                    runCatching {
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line -> android.util.Log.d("ClawVpn", "mihomo: $line") }
                        }
                    }.onFailure {
                        android.util.Log.d("ClawVpn", "mihomo log reader stopped: ${it.message}")
                    }
                }, "ClawMihomoLog").start()
            }
        android.util.Log.d("ClawVpn", "mihomo started config=${configFile.absolutePath}")
    }

    private fun ensureCountryDatabase(workDir: File) {
        listOf("GeoIP.dat", "geoip.dat", "geosite.dat", "GeoSite.dat").forEach { name ->
            val stale = File(workDir, name)
            if (stale.exists()) {
                runCatching { stale.delete() }
                android.util.Log.d("ClawVpn", "mihomo removed stale geodata ${stale.name}")
            }
        }

        val target = File(workDir, "Country.mmdb")
        if (target.exists() && target.length() > 0L) {
            android.util.Log.d("ClawVpn", "mihomo Country.mmdb exists size=${target.length()}")
            return
        }

        context.assets.open("mihomo/Country.mmdb").use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        android.util.Log.d("ClawVpn", "mihomo Country.mmdb copied size=${target.length()}")
    }

    fun stop() {
        val proc = process ?: return
        process = null
        runCatching { proc.destroy() }
        runCatching {
            if (!proc.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
            }
        }
        android.util.Log.d("ClawVpn", "mihomo stopped")
    }
}

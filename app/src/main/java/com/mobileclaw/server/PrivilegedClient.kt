package com.mobileclaw.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Client for the bundled privileged server.
 * Each call opens a fresh TCP connection to 127.0.0.1:52730 — no persistent state.
 */
object PrivilegedClient {

    private const val TIMEOUT_MS = 3_000L

    /** Returns true if the server process is running and reachable. */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val reply = sendRaw("ping") ?: return@withContext false
        reply.startsWith("ok:") || reply.startsWith("err:")
    }

    /**
     * Sends "am start --display [displayId] -n [flatComponent]" via the privileged server.
     * Returns null on success, or an error string on failure.
     */
    suspend fun launchOnDisplay(displayId: Int, flatComponent: String): String? =
        withContext(Dispatchers.IO) {
            val cmd = "am start --display $displayId -n $flatComponent -f 0x10200000"
            val reply = sendRaw(cmd)
                ?: return@withContext "特权服务未激活 (TCP 127.0.0.1:${PrivilegedServer.PORT} 不可达)"
            if (reply.startsWith("ok:")) null
            else reply.removePrefix("err:").ifBlank { "server error" }
        }

    private suspend fun sendRaw(command: String): String? =
        withTimeoutOrNull(TIMEOUT_MS) {
            try {
                Socket("127.0.0.1", PrivilegedServer.PORT).use { socket ->
                    socket.soTimeout = TIMEOUT_MS.toInt()
                    PrintWriter(socket.outputStream, true).println(command)
                    BufferedReader(InputStreamReader(socket.inputStream)).readLine()
                }
            } catch (_: Exception) {
                null
            }
        }
}

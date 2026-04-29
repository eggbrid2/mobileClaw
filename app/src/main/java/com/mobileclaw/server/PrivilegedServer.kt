package com.mobileclaw.server

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bundled privileged server — started via app_process as shell uid (2000):
 *
 *   adb shell 'CLASSPATH=$(pm path com.mobileclaw | cut -d: -f2) /system/bin/app_process / com.mobileclaw.server.PrivilegedServer </dev/null >/dev/null 2>&1 &'
 *
 * Listens on TCP 127.0.0.1:52730 (avoids SELinux unix_socket_connect restrictions).
 * Runs WITHOUT Application context.
 * Security gate: only "am start …" commands accepted.
 */
object PrivilegedServer {

    const val PORT = 52730
    private const val CMD_TIMEOUT_MS = 8_000L
    private val pool = Executors.newCachedThreadPool()

    @JvmStatic
    fun main(args: Array<String>) {
        val server = try {
            ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            System.err.println("[PrivServer] bind failed: ${e.message}")
            return
        }
        System.err.println("[PrivServer] listening on 127.0.0.1:$PORT")
        while (true) {
            val client = try { server.accept() } catch (_: Exception) { break }
            pool.submit { handleClient(client) }
        }
        server.close()
        pool.shutdown()
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = PrintWriter(socket.outputStream, true)
                val cmd = reader.readLine()?.trim() ?: run {
                    writer.println("err:empty command")
                    return
                }
                if (!cmd.startsWith("am start ") && cmd != "ping") {
                    writer.println("err:only 'am start' commands accepted")
                    return
                }
                if (cmd == "ping") {
                    writer.println("ok:pong")
                    return
                }
                writer.println(execCommand(cmd))
            }
        } catch (_: Exception) { /* client disconnected */ }
    }

    private fun execCommand(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val finished = proc.waitFor(CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroyForcibly(); return "err:command timed out" }
            if (proc.exitValue() == 0) "ok:launched"
            else "err:exit=${proc.exitValue()} ${proc.errorStream.bufferedReader().readText().trim().take(200)}"
        } catch (e: Exception) {
            "err:${e.message?.take(200)}"
        }
    }
}

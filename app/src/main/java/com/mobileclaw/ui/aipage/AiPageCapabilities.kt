package com.mobileclaw.ui.aipage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.mobileclaw.R
import com.mobileclaw.str
import com.mobileclaw.vpn.AppHttpProxy

/** Exposes all Android system capabilities to AiPageRuntime action steps. */
class AiPageCapabilities(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Network ──────────────────────────────────────────────────────────────

    suspend fun httpFetch(url: String, method: String, headers: Map<String, String>, body: String): Map<String, Any> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val reqBody = if (method.uppercase() in setOf("POST", "PUT", "PATCH") && body.isNotEmpty()) {
                    val ct = (headers["Content-Type"] ?: "application/json").toMediaType()
                    body.toRequestBody(ct)
                } else null
                reqBuilder.method(method.uppercase(), reqBody)
                val response = http.newCall(reqBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""
                mapOf(
                    "status" to response.code,
                    "ok" to response.isSuccessful,
                    "body" to responseBody,
                )
            }.getOrElse { e ->
                mapOf("error" to (e.message ?: "Network error"), "status" to 0, "ok" to false)
            }
        }

    // ── Shell ─────────────────────────────────────────────────────────────────

    suspend fun shellExec(cmd: String): Map<String, Any> = withContext(Dispatchers.IO) {
        runCatching {
            val proc = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
            val done = proc.waitFor(15, TimeUnit.SECONDS)
            val out = proc.inputStream.bufferedReader().readText().take(8192)
            val code = if (done) proc.exitValue() else { proc.destroyForcibly(); -1 }
            mapOf("stdout" to out, "exitCode" to code, "ok" to (code == 0))
        }.getOrElse { e ->
            mapOf("error" to (e.message ?: "Shell error"), "exitCode" to -1, "ok" to false)
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun notify(title: String, body: String) {
        mainHandler.post {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "ai_pages"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.createNotificationChannel(
                        NotificationChannel(channelId, "AI Pages", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val notif = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title.take(50))
                    .setContentText(body.take(200))
                    .setAutoCancel(true)
                    .build()
                nm.notify(System.currentTimeMillis().toInt(), notif)
            }
        }
    }

    // ── Haptics ───────────────────────────────────────────────────────────────

    fun vibrate(ms: Long) {
        mainHandler.post {
            runCatching {
                val duration = ms.coerceIn(10, 2000)
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
                }
            }
        }
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    fun toast(text: String) {
        mainHandler.post {
            runCatching { Toast.makeText(context, text.take(200), Toast.LENGTH_SHORT).show() }
        }
    }

    // ── App Launch ────────────────────────────────────────────────────────────

    fun launchApp(packageName: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    // ── URL / Intent ──────────────────────────────────────────────────────────

    fun openUrl(url: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    fun sendIntent(action: String, dataUri: String?, extras: Map<String, String>): Boolean = runCatching {
        val intent = Intent(action).apply {
            if (!dataUri.isNullOrBlank()) data = Uri.parse(dataUri)
            extras.forEach { (k, v) -> putExtra(k, v) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    // ── Share ─────────────────────────────────────────────────────────────────

    fun share(text: String, title: String?) {
        mainHandler.post {
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(intent, str(R.string.profile_c31f48)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        }
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    fun clipboardSet(text: String) {
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("", text))
            }
        }
    }

    fun clipboardGet(): String = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }.getOrDefault("")

    // ── Device Info ───────────────────────────────────────────────────────────

    fun getDeviceInfo(): Map<String, Any> {
        val dm = context.resources.displayMetrics
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            "screenWidth" to dm.widthPixels,
            "screenHeight" to dm.heightPixels,
            "density" to dm.density,
        )
    }

    // ── Phone / SMS ───────────────────────────────────────────────────────────

    fun callPhone(number: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    fun sendSms(number: String, body: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${number}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    // ── Alarm ─────────────────────────────────────────────────────────────────

    fun setAlarm(hour: Int, minute: Int, message: String): Boolean = runCatching {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)

    // ── Map / Location ────────────────────────────────────────────────────────

    fun openMap(query: String): Boolean = runCatching {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        mainHandler.post { context.startActivity(intent) }
        true
    }.getOrDefault(false)
}

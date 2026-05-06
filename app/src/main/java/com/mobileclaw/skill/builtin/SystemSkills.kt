package com.mobileclaw.skill.builtin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ClipboardSkill : Skill {
    override val meta = SkillMeta(
        id = "clipboard",
        name = "Clipboard",
        nameZh = "剪贴板",
        description = "Read from or write to the system clipboard. Action 'get' returns current clipboard text. Action 'set' writes text to clipboard.",
        descriptionZh = "读写系统剪贴板。action=get 返回当前剪贴板内容，action=set 将文本写入剪贴板。",
        parameters = listOf(
            SkillParam("action", "string", "'get' to read clipboard, 'set' to write to clipboard"),
            SkillParam("text", "string", "Text to write (required when action=set)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required: get | set")
        val ctx = ClawApplication.instance
        return when (action.trim().lowercase()) {
            "get" -> withContext(Dispatchers.Main) {
                runCatching {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    SkillResult(true, if (text.isBlank()) "(clipboard is empty)" else text)
                }.getOrElse { SkillResult(false, it.message ?: "clipboard read error") }
            }
            "set" -> {
                val text = params["text"] as? String ?: return SkillResult(false, "text is required for action=set")
                withContext(Dispatchers.Main) {
                    runCatching {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("", text))
                        SkillResult(true, "Copied ${text.length} characters to clipboard")
                    }.getOrElse { SkillResult(false, it.message ?: "clipboard write error") }
                }
            }
            else -> SkillResult(false, "Unknown action: $action. Use 'get' or 'set'")
        }
    }
}

class ShowToastSkill : Skill {
    override val meta = SkillMeta(
        id = "show_toast",
        name = "Show Toast",
        nameZh = "显示提示",
        description = "Shows a brief toast notification on screen. Useful for notifying the user of background task results.",
        descriptionZh = "在屏幕上显示短暂的 Toast 提示，适合用于后台任务完成后通知用户。",
        parameters = listOf(
            SkillParam("message", "string", "The message to display (max 200 characters)"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val message = params["message"] as? String ?: return SkillResult(false, "message is required")
        val trimmed = message.take(200)
        return suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                runCatching { Toast.makeText(ClawApplication.instance, trimmed, Toast.LENGTH_LONG).show() }
                cont.resume(SkillResult(true, "Toast shown: $trimmed"))
            }
        }
    }
}

class DeviceInfoSkill : Skill {
    override val meta = SkillMeta(
        id = "device_info",
        name = "Device Info",
        nameZh = "设备信息",
        description = "Returns device hardware info, battery level, network type, and screen dimensions.",
        descriptionZh = "返回设备型号、电量、网络类型、屏幕尺寸等信息。",
        parameters = emptyList(),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = ClawApplication.instance
            val dm = ctx.resources.displayMetrics

            // Battery
            val batteryIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) "${(level * 100 / scale)}%" else "unknown"
            val chargingStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargingStatus == BatteryManager.BATTERY_STATUS_FULL

            // Network
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)
            val networkType = when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "other"
            }

            val info = buildString {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Screen: ${dm.widthPixels}×${dm.heightPixels} @ ${dm.density}x density")
                appendLine("Battery: $batteryPct${if (isCharging) " (charging)" else ""}")
                appendLine("Network: $networkType")
                appendLine("Language: ${ctx.resources.configuration.locales[0].language}")
                append("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            }
            SkillResult(true, info)
        }.getOrElse { SkillResult(false, it.message ?: "device info error") }
    }
}

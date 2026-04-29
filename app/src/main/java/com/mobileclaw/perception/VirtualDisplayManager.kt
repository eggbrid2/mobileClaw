package com.mobileclaw.perception

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.util.Base64
import com.mobileclaw.server.PrivilegedClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages a hidden virtual display that apps can be launched onto and observed
 * without appearing on the main screen.
 * Accessibility nodes on the virtual display are fully readable; node-based
 * tap/scroll/input actions work cross-display.
 */
class VirtualDisplayManager(private val context: Context) {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var displayId: Int = -1
        private set

    val isRunning get() = virtualDisplay != null

    /** Creates the virtual display and returns its displayId. No-op if already running. */
    fun start(width: Int = 1080, height: Int = 1920, dpi: Int = 320): Int {
        if (virtualDisplay != null) return displayId

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // PRESENTATION: no special permission needed.
        // TRUSTED (0x40, API 34): allows apps on virtual display to draw over status bar —
        // required on Vivo OriginOS 4+ which rejects untrusted virtual display app launches.
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 0x40 else 0
        val vd = dm.createVirtualDisplay(
            "MobileClawBG",
            width, height, dpi,
            reader.surface,
            flags,
        )
        virtualDisplay = vd
        displayId = vd.display.displayId
        return displayId
    }

    /**
     * Launches [packageName] onto the virtual display. Call [start] first.
     *
     * Tries three escalating strategies:
     *  1. setLaunchDisplayId (standard API — blocked on ColorOS/MIUI/EMUI)
     *  2. `su -c am start --display N` (root)
     *  3. Built-in privileged server via Unix socket (shell uid, activated once via ADB)
     */
    suspend fun launchApp(packageName: String) {
        check(isRunning) { "Virtual display not started. Call bg_launch first." }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("Package not found: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        // Strategy 1: standard API
        val opts = ActivityOptions.makeBasic()
        opts.setLaunchDisplayId(displayId)
        try {
            context.startActivity(intent, opts.toBundle())
            return
        } catch (_: SecurityException) { /* fall through */ }

        val flat = intent.component?.flattenToShortString()
            ?: throw IllegalStateException("Cannot resolve component for $packageName")

        // Strategy 2: root via su
        if (launchViaRoot(flat)) return

        // Strategy 3: built-in privileged server (shell uid, no root required)
        val privError = PrivilegedClient.launchOnDisplay(displayId, flat)
        if (privError == null) return

        throw SecurityException(
            "ROM security policy blocked launch for $packageName.\n" +
            "已尝试: setLaunchDisplayId + su + 特权服务 ($privError)\n\n" +
            "请在「设置 → Virtual Display → 特权服务」复制激活命令并在电脑终端执行一次。"
        )
    }

    // Runs `am start --display N` via su. Returns true if the command exited successfully.
    private fun launchViaRoot(flatComponent: String): Boolean {
        return try {
            val cmd = "am start --display $displayId -n $flatComponent -f 0x10200000"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val finished = proc.waitFor(5, TimeUnit.SECONDS)
            finished && proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun romLaunchHint(): String {
        val brand = Build.BRAND.lowercase()
        return when {
            brand.contains("vivo") ->
                "OriginOS: 开发者选项 → 多任务显示，或 ADB: enable_freeform_support 1"
            brand.contains("xiaomi") || brand.contains("redmi") ->
                "MIUI: 开发者选项 → 自由窗口，或 ADB: enable_freeform_support 1"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                "ColorOS 安全策略拦截。需要 Root 或 Shizuku。" +
                "ADB 命令已不够: enable_freeform_support + force_desktop_mode 均已无效。"
            brand.contains("huawei") || brand.contains("honor") ->
                "EMUI: 开发者选项 → 多窗口，或 ADB: enable_freeform_support 1"
            else ->
                "开发者选项 → 自由窗口，或 ADB: enable_freeform_support 1"
        }
    }

    /**
     * Captures the latest frame rendered to the virtual display.
     * Returns a data-URI JPEG string, or null if no frame has been rendered yet.
     */
    fun captureFrame(): String? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888,
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 80, out)
            cropped.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            image.close()
        }
    }

    /** Reads the accessibility tree of the virtual display as XML. */
    fun readScreenXml(): String {
        if (!isRunning) return "Virtual display not running."
        if (!ClawAccessibilityService.isEnabled()) return "Accessibility service not enabled."
        return ClawAccessibilityService.captureXmlForDisplay(displayId)
            ?: "No UI tree available for display $displayId. App may still be loading."
    }

    /** Tests whether virtual display is supported on this device. Returns "ok:N" or "error:message". */
    fun testSupport(): String {
        val wasRunning = isRunning
        return try {
            if (!wasRunning) start()
            "ok:$displayId"
        } catch (e: SecurityException) {
            val romHint = romSpecificHint()
            "error:Permission denied.$romHint"
        } catch (e: Exception) {
            "error:${e.message}"
        } finally {
            if (!wasRunning) runCatching { stop() }
        }
    }

    private fun romSpecificHint(): String {
        val brand = Build.BRAND.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                " MIUI: 开发者选项→自由窗口"
            brand.contains("huawei") || brand.contains("honor") ->
                " EMUI: 开发者选项→多窗口"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                " ColorOS: 开发者选项→多窗口"
            brand.contains("vivo") ->
                " OriginOS: 开发者选项→多任务"
            else -> " Try: adb shell pm grant ${context.packageName} android.permission.CAPTURE_VIDEO_OUTPUT"
        }
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
        displayId = -1
    }
}

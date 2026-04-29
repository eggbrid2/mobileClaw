package com.mobileclaw.perception

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.util.Xml
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.os.Handler
import android.os.Looper
import android.view.Display
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Handles screen perception: screenshot capture, UI tree parsing, SoM (Set-of-Mark) annotation.
 * Migrated from OmniOperator-Prototype/OmniScreenshotController with ONNX classifier removed.
 */
class ScreenshotController(private val service: ClawAccessibilityService) {

    var currentActivity: String = "unknown"
        private set

    private val screenshotMutex = Mutex()

    private val mainExecutor: Executor = Executor { cmd ->
        Handler(Looper.getMainLooper()).post(cmd)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val cls = event.className?.toString() ?: return
            if (!shouldIgnore(pkg, cls)) currentActivity = cls
        }
    }

    suspend fun captureScreenshot(): ScreenshotData {
        screenshotMutex.lock()
        return try {
            val base64 = suspendCancellableCoroutine { cont ->
                service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            result.hardwareBuffer.use { buf ->
                                try {
                                    val bmp = Bitmap.wrapHardwareBuffer(buf, result.colorSpace)
                                        ?: throw RuntimeException("Failed to wrap hardware buffer")
                                    cont.resume(bitmapToBase64(bmp))
                                } catch (e: Exception) { cont.resumeWithException(e) }
                            }
                        }
                        override fun onFailure(code: Int) =
                            cont.resumeWithException(RuntimeException("Screenshot failed: $code"))
                    })
            }
            CoroutineScope(Dispatchers.Default).launch { delay(300); screenshotMutex.unlock() }
            ScreenshotData(imageBase64 = "data:image/jpeg;base64,$base64")
        } catch (e: Exception) { screenshotMutex.unlock(); throw e }
    }

    suspend fun captureSom(): ScreenshotData {
        screenshotMutex.lock()
        return try {
            val base64 = suspendCancellableCoroutine { cont ->
                service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            result.hardwareBuffer.use { buf ->
                                try {
                                    val bmp = Bitmap.wrapHardwareBuffer(buf, result.colorSpace)
                                        ?: throw RuntimeException("Failed to wrap hardware buffer")
                                    val nodeMap = getNodeMap() ?: emptyMap()
                                    val marked = drawSomMarkings(bmp, nodeMap)
                                    cont.resume(bitmapToBase64(marked))
                                } catch (e: Exception) { cont.resumeWithException(e) }
                            }
                        }
                        override fun onFailure(code: Int) =
                            cont.resumeWithException(RuntimeException("Screenshot failed: $code"))
                    })
            }
            CoroutineScope(Dispatchers.Default).launch { delay(300); screenshotMutex.unlock() }
            ScreenshotData(imageBase64 = "data:image/jpeg;base64,$base64")
        } catch (e: Exception) { screenshotMutex.unlock(); throw e }
    }

    fun captureXml(): String? {
        val root = service.rootInActiveWindow ?: return null
        return serializeXml(root)
    }

    fun captureXmlForDisplay(displayId: Int): String? {
        val allWindows = service.getWindowsOnAllDisplays()
        val windows = allWindows.get(displayId) ?: return null
        val roots = windows.mapNotNull { it.root }
        if (roots.isEmpty()) return null
        return serializeXmlMultiple(roots)
    }

    fun getNodeMap(): Map<String, UiNode>? {
        val root = service.rootInActiveWindow ?: return null
        return buildNodeMap(root)
    }

    // --- Private helpers ---

    private fun shouldIgnore(pkg: String, cls: String): Boolean {
        val ignoredPkgs = listOf("com.android.systemui", "com.android.quickstep")
        val ignoredPatterns = listOf("^android\\.widget\\..*", "^android\\.view\\..*").map(String::toRegex)
        return ignoredPkgs.any { pkg.startsWith(it) } || ignoredPatterns.any { cls.matches(it) }
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 50, out)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    private fun buildNodeMap(root: AccessibilityNodeInfo): Map<String, UiNode> {
        val map = mutableMapOf<String, UiNode>()
        var counter = 0
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val id = (counter++).toString()
            map[id] = UiNode(
                id = id,
                text = node.text?.toString(),
                contentDesc = node.contentDescription?.toString(),
                className = node.className?.toString(),
                bounds = bounds,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable,
                info = node,
            )
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return map
    }

    private fun serializeXmlMultiple(roots: List<AccessibilityNodeInfo>): String {
        val writer = StringWriter()
        val s = Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            startTag(null, "hierarchy")
        }
        fun sanitize(t: String?) = t?.replace(Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]"), "")
        fun write(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            val b = Rect(); node.getBoundsInScreen(b)
            s.startTag(null, "node")
            sanitize(node.text?.toString())?.let { s.attribute(null, "text", it) }
            sanitize(node.contentDescription?.toString())?.let { s.attribute(null, "content-desc", it) }
            if (node.isClickable) s.attribute(null, "clickable", "true")
            if (node.isEditable) s.attribute(null, "editable", "true")
            if (node.isScrollable) s.attribute(null, "scrollable", "true")
            s.attribute(null, "bounds", "[${b.left},${b.top}][${b.right},${b.bottom}]")
            for (i in 0 until node.childCount) write(node.getChild(i))
            s.endTag(null, "node")
        }
        roots.forEach { write(it) }
        s.endTag(null, "hierarchy")
        s.endDocument()
        return writer.toString()
    }

    private fun serializeXml(root: AccessibilityNodeInfo): String {
        val writer = StringWriter()
        val s = Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            startTag(null, "hierarchy")
        }
        fun sanitize(t: String?) = t?.replace(Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]"), "")
        fun write(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            val b = Rect(); node.getBoundsInScreen(b)
            s.startTag(null, "node")
            sanitize(node.text?.toString())?.let { s.attribute(null, "text", it) }
            sanitize(node.contentDescription?.toString())?.let { s.attribute(null, "content-desc", it) }
            if (node.isClickable) s.attribute(null, "clickable", "true")
            if (node.isEditable) s.attribute(null, "editable", "true")
            if (node.isScrollable) s.attribute(null, "scrollable", "true")
            s.attribute(null, "bounds", "[${b.left},${b.top}][${b.right},${b.bottom}]")
            for (i in 0 until node.childCount) write(node.getChild(i))
            s.endTag(null, "node")
        }
        write(root)
        s.endTag(null, "hierarchy")
        s.endDocument()
        return writer.toString()
    }

    private fun drawSomMarkings(bmp: Bitmap, nodes: Map<String, UiNode>): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val rectPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f }
        val textPaint = Paint().apply { color = Color.BLUE; textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
        val bgPaint = Paint().apply { color = Color.argb(160, 255, 255, 255); style = Paint.Style.FILL }

        val interactive = nodes.filter { it.value.isClickable || it.value.isEditable || it.value.isScrollable }
        for ((id, node) in interactive) {
            val b = node.bounds
            canvas.drawRect(b, rectPaint)
            val tw = textPaint.measureText(id)
            canvas.drawRoundRect(RectF(b.left.toFloat(), b.top.toFloat(), b.left + tw + 10, b.top + 44f), 4f, 4f, bgPaint)
            canvas.drawText(id, b.left + 5f, b.top + 36f, textPaint)
        }
        return out
    }
}

data class ScreenshotData(val imageBase64: String)

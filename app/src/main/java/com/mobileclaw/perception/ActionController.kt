package com.mobileclaw.perception

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Point
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Executes physical actions on the device via Accessibility gestures and node actions.
 * Migrated from OmniOperator-Prototype/OmniActionController.
 */
class ActionController(private val service: ClawAccessibilityService) {

    private fun screenSize(): Point {
        val point = Point()
        @Suppress("DEPRECATION")
        service.getSystemService(WindowManager::class.java).defaultDisplay.getRealSize(point)
        if (point.x <= 0 || point.y <= 0) {
            val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            point.set(bounds.width(), bounds.height())
        }
        return point
    }
    private val screenW get() = screenSize().x.toFloat()
    private val screenH get() = screenSize().y.toFloat()

    suspend fun clickNode(nodeId: String) {
        val node = requireNode(nodeId)
        if (!node.isClickable) throw IllegalStateException("Node $nodeId is not clickable")
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw RuntimeException("Click action failed on node $nodeId")
    }

    suspend fun longClickNode(nodeId: String) {
        val node = requireNode(nodeId)
        if (!node.isLongClickable) throw IllegalStateException("Node $nodeId is not long-clickable")
        if (!node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            throw RuntimeException("Long-click action failed on node $nodeId")
    }

    suspend fun scrollNode(nodeId: String, direction: String) {
        val node = requireNode(nodeId)
        if (!node.isScrollable) throw IllegalStateException("Node $nodeId is not scrollable")
        val action = when (direction.lowercase()) {
            "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> throw IllegalArgumentException("direction must be 'forward' or 'backward'")
        }
        if (!node.performAction(action)) throw RuntimeException("Scroll failed on node $nodeId")
    }

    suspend fun inputText(nodeId: String, text: String) {
        val node = requireNode(nodeId)
        if (!node.isEditable) throw IllegalStateException("Node $nodeId is not editable")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) && nodeTextMatchesAfterDelay(node, text)) return
        delay(120)
        if (ClawIME.inputText(text)) return
        if (pasteTextViaClipboard(node, text)) return
        throw RuntimeException("Input text failed on node $nodeId. ACTION_SET_TEXT failed; ${ClawIME.statusSummary()} Clipboard paste and long-press paste menu fallback also failed.")
    }

    suspend fun inputTextFocused(text: String) {
        val nodeMap = ClawAccessibilityService.getNodeMap()
            ?: throw IllegalStateException("No UI tree available")
        val focused = nodeMap.values.firstOrNull { it.info.isFocused && it.isEditable }
            ?: throw IllegalStateException("No focused editable node found")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (focused.info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) && nodeTextMatchesAfterDelay(focused.info, text)) return
        delay(120)
        if (ClawIME.inputText(text)) return
        if (pasteTextViaClipboard(focused.info, text)) return
        throw RuntimeException("Input text failed on focused node. ACTION_SET_TEXT failed; ${ClawIME.statusSummary()} Clipboard paste and long-press paste menu fallback also failed.")
    }

    suspend fun clickCoordinate(x: Float, y: Float) = gestureClick(x, y, 50L)

    suspend fun longClickCoordinate(x: Float, y: Float) = gestureClick(x, y, 1000L)

    suspend fun scrollCoordinate(x: Float, y: Float, direction: String, distance: Float) {
        val (ex, ey) = when (direction.lowercase()) {
            "up" -> x to maxOf(y - distance, 0f)
            "down" -> x to minOf(y + distance, screenH)
            "left" -> maxOf(x - distance, 0f) to y
            "right" -> minOf(x + distance, screenW) to y
            else -> throw IllegalArgumentException("direction must be up/down/left/right")
        }
        val path = Path().apply { moveTo(x, y); lineTo(ex, ey) }
        dispatchGesture(path, 500L)
    }

    fun goHome() = performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
    fun goBack() = performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)

    fun launchApp(packageName: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("App not found: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

    fun listInstalledApps(): List<AppInfo> {
        return service.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { service.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppInfo(it.packageName, it.loadLabel(service.packageManager).toString()) }
            .sortedBy { it.appName }
    }

    fun copyToClipboard(text: String) {
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("claw", text))
    }

    private suspend fun pasteTextViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        copyToClipboard(text)
        delay(120)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE) && nodeTextContainsAfterDelay(node, text)) return true
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val cx = bounds.exactCenterX().takeIf { it > 0f } ?: (screenW / 2f)
        val cy = bounds.exactCenterY().takeIf { it > 0f } ?: (screenH / 2f)
        longClickCoordinate(cx, cy)
        delay(450)
        val pasteNode = ClawAccessibilityService.getNodeMap()
            ?.values
            ?.firstOrNull { candidate ->
                val label = listOfNotNull(candidate.text, candidate.contentDesc)
                    .joinToString(" ")
                    .trim()
                    .lowercase()
                label == "粘贴" || label == "paste" || label.contains("粘贴") || label.contains("paste")
            }
        if (pasteNode != null) {
            val pasteCx = ((pasteNode.bounds.left + pasteNode.bounds.right) / 2).toFloat()
            val pasteCy = ((pasteNode.bounds.top + pasteNode.bounds.bottom) / 2).toFloat()
            clickCoordinate(pasteCx, pasteCy)
            delay(220)
            node.refresh()
            return node.text?.toString()?.contains(text) == true
        }
        return false
    }

    private suspend fun nodeTextMatchesAfterDelay(node: AccessibilityNodeInfo, text: String): Boolean {
        delay(160)
        node.refresh()
        return node.text?.toString() == text
    }

    private suspend fun nodeTextContainsAfterDelay(node: AccessibilityNodeInfo, text: String): Boolean {
        delay(220)
        node.refresh()
        return node.text?.toString()?.contains(text) == true
    }

    // --- Private ---

    private fun requireNode(nodeId: String): AccessibilityNodeInfo {
        return ClawAccessibilityService.getNodeMap()?.get(nodeId)?.info
            ?: throw IllegalArgumentException("Node not found: $nodeId")
    }

    private suspend fun gestureClick(x: Float, y: Float, duration: Long) {
        val size = screenSize()
        val sx = x.coerceIn(0f, maxOf(1, size.x - 1).toFloat())
        val sy = y.coerceIn(0f, maxOf(1, size.y - 1).toFloat())
        val path = Path().apply { moveTo(sx, sy); lineTo(sx + 1f, sy + 1f) }
        dispatchGesture(path, duration)
    }

    private suspend fun dispatchGesture(path: Path, duration: Long) {
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = cont.resume(Unit)
                override fun onCancelled(g: GestureDescription?) =
                    cont.resumeWithException(RuntimeException("Gesture cancelled"))
            }, null)
            if (!dispatched) cont.resumeWithException(RuntimeException("Failed to dispatch gesture"))
        }
    }

    private fun performGlobal(action: Int) {
        if (!service.performGlobalAction(action))
            throw RuntimeException("Global action $action failed")
    }
}

data class AppInfo(val packageName: String, val appName: String)

package com.mobileclaw.perception

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Executes physical actions on the device via Accessibility gestures and node actions.
 * Migrated from OmniOperator-Prototype/OmniActionController.
 */
class ActionController(private val service: ClawAccessibilityService) {

    private val screenBounds: Rect by lazy {
        service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
    }
    private val screenW get() = screenBounds.width().toFloat()
    private val screenH get() = screenBounds.height().toFloat()

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
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
            throw RuntimeException("Input text failed on node $nodeId")
    }

    suspend fun inputTextFocused(text: String) {
        val nodeMap = ClawAccessibilityService.getNodeMap()
            ?: throw IllegalStateException("No UI tree available")
        val focused = nodeMap.values.firstOrNull { it.info.isFocused && it.isEditable }
            ?: throw IllegalStateException("No focused editable node found")
        inputText(focused.id, text)
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

    // --- Private ---

    private fun requireNode(nodeId: String): AccessibilityNodeInfo {
        return ClawAccessibilityService.getNodeMap()?.get(nodeId)?.info
            ?: throw IllegalArgumentException("Node not found: $nodeId")
    }

    private suspend fun gestureClick(x: Float, y: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
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

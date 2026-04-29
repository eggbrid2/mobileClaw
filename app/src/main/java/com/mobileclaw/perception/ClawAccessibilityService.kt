package com.mobileclaw.perception

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Core accessibility service — perception + action entry point for the Agent.
 * Migrated and refactored from OmniOperator-Prototype/OmniOperatorService.
 */
class ClawAccessibilityService : AccessibilityService() {

    private lateinit var screenshotController: ScreenshotController
    private lateinit var actionController: ActionController

    companion object {
        private var instance: ClawAccessibilityService? = null

        fun isEnabled() = instance != null

        suspend fun captureScreenshot() = instance!!.screenshotController.captureScreenshot()
        suspend fun captureScreenshotXml() = instance!!.screenshotController.captureXml()
        suspend fun captureScreenshotSom() = instance!!.screenshotController.captureSom()
        fun captureXmlForDisplay(displayId: Int) = instance!!.screenshotController.captureXmlForDisplay(displayId)
        fun getNodeMap() = instance!!.screenshotController.getNodeMap()
        fun getCurrentPackage() = instance!!.rootInActiveWindow?.packageName?.toString() ?: ""
        fun getCurrentActivity() = instance!!.screenshotController.currentActivity

        suspend fun clickNode(nodeId: String) = instance!!.actionController.clickNode(nodeId)
        suspend fun longClickNode(nodeId: String) = instance!!.actionController.longClickNode(nodeId)
        suspend fun scrollNode(nodeId: String, direction: String) = instance!!.actionController.scrollNode(nodeId, direction)
        suspend fun inputText(nodeId: String, text: String) = instance!!.actionController.inputText(nodeId, text)
        suspend fun inputTextFocused(text: String) = instance!!.actionController.inputTextFocused(text)
        suspend fun clickCoordinate(x: Float, y: Float) = instance!!.actionController.clickCoordinate(x, y)
        suspend fun longClickCoordinate(x: Float, y: Float) = instance!!.actionController.longClickCoordinate(x, y)
        suspend fun scrollCoordinate(x: Float, y: Float, direction: String, distance: Float) =
            instance!!.actionController.scrollCoordinate(x, y, direction, distance)
        suspend fun goHome() = instance!!.actionController.goHome()
        suspend fun goBack() = instance!!.actionController.goBack()
        suspend fun launchApp(packageName: String) = instance!!.actionController.launchApp(packageName)
        fun listInstalledApps() = instance!!.actionController.listInstalledApps()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        screenshotController = ScreenshotController(this)
        actionController = ActionController(this)
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        screenshotController.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {}
}

/** Lightweight node info wrapper used by the Agent to reference UI elements. */
data class UiNode(
    val id: String,
    val text: String?,
    val contentDesc: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val info: AccessibilityNodeInfo,
)

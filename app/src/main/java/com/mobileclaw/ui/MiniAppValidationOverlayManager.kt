package com.mobileclaw.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mobileclaw.ClawApplication

private class MiniAppValidationOverlayState {
    var appId by mutableStateOf<String?>(null)
    var validationMode by mutableStateOf(true)
    var expanded by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    var visible by mutableStateOf(false)
}

class MiniAppValidationOverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hostFrame: FrameLayout? = null
    private var hostParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private val state = MiniAppValidationOverlayState()

    var onStatusChanged: ((String, String, Boolean) -> Unit)? = null
    var onDismissed: ((String) -> Unit)? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(appId: String, validationMode: Boolean = true): Boolean {
        if (!canShow()) return false
        runOnMain {
            state.appId = appId
            state.validationMode = validationMode
            state.expanded = false
            state.minimized = false
            state.visible = true
            ensureWindow()
            positionWindowForCurrentState(forceBottomRight = true)
        }
        return true
    }

    fun hide(notifyDismissed: Boolean = true) {
        runOnMain {
            val closedAppId = state.appId
            runCatching { hostFrame?.let { wm.removeView(it) } }
            hostFrame = null
            hostParams = null
            lifecycleOwner?.stop()
            lifecycleOwner = null
            state.visible = false
            state.appId = null
            state.expanded = false
            state.minimized = false
            if (notifyDismissed && !closedAppId.isNullOrBlank()) {
                onDismissed?.invoke(closedAppId)
            }
        }
    }

    private fun ensureWindow() {
        if (hostFrame != null) {
            hostFrame?.visibility = android.view.View.VISIBLE
            return
        }
        val params = buildLayoutParams().also { hostParams = it }
        val owner = OverlayLifecycleOwner().also { it.start(); lifecycleOwner = it }
        val frame = FrameLayout(context)
        val config = ClawApplication.instance.agentConfig.snapshot()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                ClawTheme(darkTheme = config.darkTheme, accentColor = config.accentColor) {
                    val appId = state.appId
                    if (!state.visible || appId.isNullOrBlank()) return@ClawTheme
                    val baseWidth = when {
                        state.minimized -> 220.dp
                        state.expanded -> 360.dp
                        else -> 280.dp
                    }
                    val baseHeight = when {
                        state.minimized -> 56.dp
                        state.expanded -> 720.dp
                        else -> 520.dp
                    }
                    val overlayScale = when {
                        state.minimized -> 0.82f
                        state.expanded -> 0.56f
                        else -> 0.56f
                    }
                    val cardWidth = baseWidth * overlayScale
                    val cardHeight = baseHeight * overlayScale
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .clip(RoundedCornerShape(24.dp))
                            .background(LocalClawColors.current.surface)
                            .border(1.dp, LocalClawColors.current.border, RoundedCornerShape(24.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(baseWidth)
                                .height(baseHeight)
                                .graphicsLayer(
                                    scaleX = overlayScale,
                                    scaleY = overlayScale,
                                    transformOrigin = TransformOrigin(0f, 0f),
                                ),
                        ) {
                            if (state.minimized) {
                                OverlayMiniAppPreviewPill(
                                    title = appId,
                                    status = "Validation preview ready",
                                    healthy = true,
                                    onRestore = {
                                        state.minimized = false
                                        positionWindowForCurrentState(forceBottomRight = false)
                                    },
                                    onClose = { hide() },
                                )
                            } else {
                                MiniAppViewport(
                                    appId = appId,
                                    onClose = { hide() },
                                    onAskAgent = { task ->
                                        ClawApplication.instance.pendingAgentTask.tryEmit(task)
                                        openMainApp()
                                    },
                                    modifier = Modifier.matchParentSize(),
                                    compact = true,
                                    validationMode = state.validationMode,
                                    onStatusChange = { status, healthy ->
                                        onStatusChanged?.invoke(appId, status, healthy)
                                    },
                                    onMinimize = {
                                        state.minimized = true
                                        positionWindowForCurrentState(forceBottomRight = false)
                                    },
                                    onToggleExpanded = {
                                        state.expanded = !state.expanded
                                        positionWindowForCurrentState(forceBottomRight = false)
                                    },
                                    onOpenExternal = {
                                        openMiniAppFullscreen(appId)
                                        hide(notifyDismissed = true)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        androidx.core.view.ViewCompat.setBackground(frame, null)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.addView(composeView)
        runCatching { wm.addView(frame, params) }
        hostFrame = frame
    }

    private fun openMiniAppFullscreen(appId: String) {
        runCatching {
            val intent = MiniAppActivity.intent(context, appId).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun openMainApp() {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: android.content.Intent(context, MainActivity::class.java)
            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            context.startActivity(intent)
        }
    }

    private fun positionWindowForCurrentState(forceBottomRight: Boolean) {
        val frame = hostFrame ?: return
        val params = hostParams ?: return
        frame.post {
            val bounds = screenBounds()
            val widthPx = when {
                state.minimized -> dpToPx(220f * 0.82f)
                state.expanded -> dpToPx(360f * 0.56f)
                else -> dpToPx(280f * 0.56f)
            }
            val heightPx = when {
                state.minimized -> dpToPx(56f * 0.82f)
                state.expanded -> dpToPx(720f * 0.56f)
                else -> dpToPx(520f * 0.56f)
            }
            val margin = dpToPx(12f)
            params.gravity = Gravity.TOP or Gravity.START
            if (forceBottomRight || params.x == 0 && params.y == 0) {
                params.x = (bounds.first - widthPx - margin).coerceAtLeast(margin)
                params.y = (bounds.second - heightPx - margin * 2).coerceAtLeast(margin)
            } else {
                params.x = params.x.coerceIn(margin, (bounds.first - widthPx - margin).coerceAtLeast(margin))
                params.y = params.y.coerceIn(margin, (bounds.second - heightPx - margin).coerceAtLeast(margin))
            }
            runCatching { wm.updateViewLayout(frame, params) }
        }
    }

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).also {
        it.gravity = Gravity.TOP or Gravity.START
        it.x = 0
        it.y = 0
    }

    private fun screenBounds(): Pair<Int, Int> = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Point().also { wm.defaultDisplay.getSize(it) }.let { it.x to it.y }
        }
    }.getOrDefault(context.resources.displayMetrics.widthPixels to context.resources.displayMetrics.heightPixels)

    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performAttach()
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

@androidx.compose.runtime.Composable
private fun OverlayMiniAppPreviewPill(
    title: String,
    status: String,
    healthy: Boolean,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(999.dp))
            .background(if (c.isDark) androidx.compose.ui.graphics.Color(0xF20B0B0B) else androidx.compose.ui.graphics.Color(0xFAFFFFFF))
            .border(0.8.dp, c.border.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
            .clickable { onRestore() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (healthy) androidx.compose.ui.graphics.Color(0xFF56D6BA) else androidx.compose.ui.graphics.Color(0xFFFF8A65)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                color = c.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                color = c.subtext,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = c.subtext,
            modifier = Modifier.size(15.dp),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = c.subtext,
            modifier = Modifier
                .size(15.dp)
                .clickable { onClose() },
        )
    }
}

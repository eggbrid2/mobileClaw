package com.mobileclaw.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen pass-through overlay that flashes an aurora-glow border when the
 * agent takes a screenshot, or fills the whole screen for VLM/vision analysis.
 *
 * Window flags: NOT_TOUCHABLE + NOT_FOCUSABLE → all touches pass through unchanged.
 */
class AuroraOverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var hostFrame: FrameLayout? = null
    private var lifecycleOwner: AuroraLifecycleOwner? = null
    private var hideJob: Job? = null

    private var visible by mutableStateOf(false)
    private var fullScreen by mutableStateOf(false)

    /** Border-only flash for regular screenshots. */
    fun flash(durationMs: Long = 1_400) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { flash(durationMs) }
            return
        }
        if (!Settings.canDrawOverlays(context)) return
        fullScreen = false
        ensureWindow()
        visible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationMs)
            visible = false
            delay(500)
            removeWindow()
        }
    }

    /** Full-screen aurora fill for VLM / vision analysis steps. */
    fun flashFullScreen(durationMs: Long = 2_400) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { flashFullScreen(durationMs) }
            return
        }
        if (!Settings.canDrawOverlays(context)) return
        fullScreen = true
        ensureWindow()
        visible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationMs)
            visible = false
            delay(600)
            removeWindow()
        }
    }

    private fun ensureWindow() {
        if (hostFrame != null) return
        // Use real display size (includes status bar + nav bar) so the overlay
        // truly covers the full screen system-wide, even when shown over the home screen.
        val realSize = Point()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            realSize.set(bounds.width(), bounds.height())
        } else {
            wm.defaultDisplay.getRealSize(realSize)
        }
        val params = WindowManager.LayoutParams(
            realSize.x,
            realSize.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.x = 0
            it.y = 0
        }
        val owner = AuroraLifecycleOwner().also { it.start(); lifecycleOwner = it }
        val frame = FrameLayout(context)
        val cv = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(220)),
                    exit  = fadeOut(tween(550)),
                ) {
                    // Both modes render only the border frame; fullScreen uses slower animation cycle
                    AuroraBorderCanvas(cycleDurationMs = if (fullScreen) 3_600 else 2_600)
                }
            }
        }
        androidx.core.view.ViewCompat.setBackground(frame, null)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.addView(cv)
        runCatching { wm.addView(frame, params) }
        hostFrame = frame
    }

    private fun removeWindow() {
        runCatching { hostFrame?.let { wm.removeView(it) } }
        hostFrame = null
        lifecycleOwner?.stop(); lifecycleOwner = null
    }

    private class AuroraLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry   = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        fun start() {
            controller.performAttach(); controller.performRestore(null)
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

// ── Aurora border composable ───────────────────────────────────────────────────

// Minimal AI aurora palette: mostly monochrome with a restrained mint/lime pulse.
private val AURORA_PALETTE = listOf(
    Color(0xFFFFFFFF),
    Color(0xFF56D6BA),
    Color(0xFFC7F43A),
    Color(0xFF8A8A8A),
    Color(0xFFFFFFFF),
)

// (thicknessDp, alpha) — widest/most-transparent first so inner layers composite on top
private val AURORA_LAYERS = listOf(
    22f to 0.06f,
    12f to 0.17f,
    6f  to 0.42f,
    2.5f to 1.00f,
)

@Composable
private fun AuroraBorderCanvas(cycleDurationMs: Int = 2_600) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(cycleDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    // Build a shifted version of the palette based on phase
    val shiftedColors = remember(phase) {
        val n = AURORA_PALETTE.size - 1
        List(n + 1) { i ->
            val pos = (phase * n + i) % n
            val lo  = pos.toInt()
            val hi  = (lo + 1) % n
            lerp(AURORA_PALETTE[lo], AURORA_PALETTE[hi], pos - lo)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        AURORA_LAYERS.forEach { (thickDp, alpha) ->
            val thick = thickDp.dp.toPx()
            val fwd  = shiftedColors.map { it.copy(alpha = alpha) }
            val rev  = fwd.reversed()

            val hFwd = Brush.horizontalGradient(fwd)
            val hRev = Brush.horizontalGradient(rev)
            val vFwd = Brush.verticalGradient(fwd)
            val vRev = Brush.verticalGradient(rev)

            // Top (left→right)
            drawRect(hFwd, Offset.Zero, Size(w, thick))
            // Bottom (right→left, so colors flow CW around the border)
            drawRect(hRev, Offset(0f, h - thick), Size(w, thick))
            // Left (top→bottom)
            drawRect(vFwd, Offset.Zero, Size(thick, h))
            // Right (bottom→top)
            drawRect(vRev, Offset(w - thick, 0f), Size(thick, h))
        }
    }
}

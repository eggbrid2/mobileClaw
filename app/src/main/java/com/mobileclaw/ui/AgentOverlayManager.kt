package com.mobileclaw.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

// ── Color palette ─────────────────────────────────────────────────────────────

private val CapsuleBg       = Color(0xF0050505)
private val CapsuleBorder   = Color(0x33FFFFFF)
private val CapsuleText     = Color(0xFFF7F7F4)
private val CapsuleMono     = Color(0xFFA0A0A0)
private val DotOrange       = Color(0xFFC7F43A)
private val DotBlue         = Color(0xFFB7B7B7)
private val DotGreen        = Color(0xFF56D6BA)
private val DotRed          = Color(0xFF8A8A8A)

// ── State ─────────────────────────────────────────────────────────────────────

/** Snapshot state driving the capsule composable. */
class OverlayState {
    var task            by mutableStateOf("")
    var stepCount       by mutableIntStateOf(0)
    var currentSkill    by mutableStateOf("")
    var streamingThought by mutableStateOf("")
    var lastObservation by mutableStateOf("")
    var isError         by mutableStateOf(false)
    var compact         by mutableStateOf(false)
}

// ── Manager ───────────────────────────────────────────────────────────────────

/**
 * Thin capsule-shaped floating overlay (TYPE_APPLICATION_OVERLAY) that shows
 * the agent's live status on top of any app.
 *
 * The window is WRAP_CONTENT and FLAG_NOT_FOCUSABLE, so it is exactly as large
 * as the capsule pill — all touch events outside the pill pass through to the
 * underlying app unchanged.
 */
class AgentOverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hostFrame: FrameLayout? = null
    private var hostParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val state = OverlayState()

    // ── Public API ────────────────────────────────────────────────────────────

    fun show(task: String) {
        runOnMain { showInternal(task, compact = false) }
    }

    fun showCompact(task: String) {
        runOnMain { showInternal(task, compact = true) }
    }

    private fun showInternal(task: String, compact: Boolean) {
        if (!Settings.canDrawOverlays(context)) return
        if (hostFrame != null) {
            state.task = task; state.stepCount = 0; state.currentSkill = ""
            state.streamingThought = ""; state.lastObservation = ""; state.isError = false
            state.compact = compact
            return
        }
        state.task = task; state.stepCount = 0; state.currentSkill = ""
        state.streamingThought = ""; state.isError = false
        state.compact = compact

        val params = buildLayoutParams().also { hostParams = it }
        val owner  = OverlayLifecycleOwner().also { it.start(); lifecycleOwner = it }
        val frame  = FrameLayout(context)

        val cv = ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                CapsuleOverlay(
                    state  = state,
                    onDrag = { dx, dy ->
                        // Gravity.TOP | Gravity.CENTER_HORIZONTAL: x unused, y = top offset
                        params.y = (params.y + dy.toInt()).coerceAtLeast(0)
                        // Allow horizontal drift by switching to absolute gravity
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = (params.x + dx.toInt()).coerceIn(0, 2400)
                        runCatching { wm.updateViewLayout(frame, params) }
                    },
                )
            }
        }

        androidx.core.view.ViewCompat.setBackground(frame, null)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.addView(cv)

        runCatching { wm.addView(frame, params) }
        hostFrame = frame
    }

    fun onSkillCalling(skillId: String, params: Map<String, Any>) {
        runOnMain {
            val brief = params.entries.take(2).joinToString(", ") { "${it.key}=${summarize(it.value)}" }
            state.currentSkill = if (brief.isBlank()) skillId else "$skillId($brief)"
            state.stepCount++
            state.streamingThought = ""
            state.isError = false
        }
    }

    fun onToken(token: String) { runOnMain { state.streamingThought += token } }

    fun onThinkingComplete() { runOnMain { state.streamingThought = "" } }

    fun onObservation(text: String) { runOnMain { state.lastObservation = text.take(100) } }

    fun onError(message: String) { runOnMain { state.isError = true; state.lastObservation = message.take(100) } }

    fun hide() {
        runOnMain {
            runCatching { hostFrame?.let { wm.removeView(it) } }
            hostFrame = null
            lifecycleOwner?.stop(); lifecycleOwner = null
            state.streamingThought = ""
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // FLAG_LAYOUT_IN_SCREEN + FLAG_LAYOUT_NO_LIMITS: allows the capsule to appear
        // in the status bar area (y=0 from physical screen top).
        // FLAG_NOT_FOCUSABLE: other apps keep focus.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 0
    }

    private fun summarize(v: Any): String = v.toString().let {
        if (it.length > 18) it.take(15) + "…" else it
    }

    // ── Lifecycle owner for overlay ComposeView ───────────────────────────────

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
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

// ── Capsule Composable ────────────────────────────────────────────────────────

@Composable
private fun CapsuleOverlay(
    state: OverlayState,
    onDrag: (Float, Float) -> Unit,
) {
    // Status dot color
    val dotColor by animateColorAsState(
        targetValue = when {
            state.isError                      -> DotRed
            state.streamingThought.isNotBlank() -> DotOrange
            state.currentSkill.isNotBlank()    -> DotBlue
            state.stepCount > 0                -> DotGreen
            else                               -> CapsuleMono
        },
        animationSpec = tween(400),
        label = "dotColor",
    )

    // Pulsing scale for the status dot when active
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val isActive = state.currentSkill.isNotBlank() || state.streamingThought.isNotBlank()

    if (state.compact) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
                }
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0xF00A0A0A), Color(0xF0050505))
                    )
                )
                .border(1.dp, dotColor.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            ClawSymbolIcon("profile", tint = CapsuleText, modifier = Modifier.size(23.dp))
        }
        return
    }

    // Primary text: task name (always visible)
    val primaryText = state.task.ifBlank { "MobileClaw" }.let {
        if (it.length > 28) it.take(26) + "…" else it
    }
    // Secondary text: current skill or streaming thought
    val secondaryText = when {
        state.streamingThought.isNotBlank() -> state.streamingThought.takeLast(55)
        state.currentSkill.isNotBlank()     -> state.currentSkill
        else                                -> null
    }
    val secondaryColor = if (state.isError) DotRed
        else if (state.streamingThought.isNotBlank()) CapsuleText.copy(alpha = 0.65f)
        else CapsuleMono

    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .widthIn(min = 180.dp, max = 280.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xF0050505), Color(0xF0101010))
                ),
                shape = RoundedCornerShape(50),
            )
            .border(
                width = 0.8.dp,
                brush = Brush.horizontalGradient(
                    listOf(CapsuleBorder.copy(alpha = 0.7f), CapsuleBorder.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = if (secondaryText != null) 7.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Primary row ──────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Pulsing status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            // Logo
            ClawSymbolIcon("profile", tint = CapsuleText, modifier = Modifier.size(14.dp))
            // Task name
            Text(
                text       = primaryText,
                color      = CapsuleText,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f),
            )
            // Step badge
            if (state.stepCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CapsuleMono.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "S${state.stepCount}",
                        color      = CapsuleMono.copy(alpha = 0.7f),
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // ── Secondary row (skill / thought) ──────────────────────────────
        if (secondaryText != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text       = secondaryText,
                color      = secondaryColor,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                fontStyle  = if (state.streamingThought.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

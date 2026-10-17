package com.eyeblink.app.ui.eye

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.eye.EyeArt.drawEye
import com.eyeblink.app.ui.eye.EyeArt.drawOrbit
import com.eyeblink.app.ui.kit.LocalReducedMotion
import com.eyeblink.app.ui.kit.Motion
import com.eyeblink.app.ui.kit.rememberClockMs
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.BlinkTimeline
import kotlin.math.max
import kotlin.math.sin

/**
 * The living eye on the home screen.
 *
 * It idles (the gaze drifts, the pupil breathes, it blinks now and then on its own) and it
 * mirrors the real overlay: [echo] is a counter that the engine bumps every time EyeBlink
 * actually blinks the screen, and the hero then blinks with the *same* timeline, so you can
 * see what the system is doing. Touching it makes the eye follow your finger; tapping blinks.
 * When EyeBlink is paused ([awake] = false) the lids close and the light dims.
 *
 * [orbit] is the fraction of time left until the next break, or null for no ring.
 */
@Composable
fun EyeHero(
    modifier: Modifier = Modifier,
    awake: Boolean = true,
    orbit: Float? = null,
    echo: Int = 0,
    spec: BlinkSpec = BlinkSpec(),
    description: String = "",
    onTap: () -> Unit = {},
) {
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = !reduced)
    val haptics = LocalHapticFeedback.current
    val timeline = remember(spec) { BlinkTimeline(spec.copy(reducedMotion = false)) }

    var blinkStart by remember { mutableStateOf(-10_000f) }
    var lastEcho by remember { mutableStateOf(echo) }
    if (echo != lastEcho) {
        lastEcho = echo
        blinkStart = clock
    }

    var touch by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(Size.Zero) }

    val sleep by animateFloatAsState(if (awake) 0f else 1f, tween(if (reduced) 1 else 520, easing = if (awake) Motion.Open else Motion.Close), label = "sleep")
    val glow by animateFloatAsState(if (awake) 1f else 0.18f, tween(if (reduced) 1 else 700), label = "glow")

    // The eye's own occasional blink, on top of any echoed ones.
    val period = 6400f
    val cycle = clock % period
    val idleBlinkAt = period - 480f
    val idleElapsed = if (awake && !reduced && cycle >= idleBlinkAt) cycle - idleBlinkAt else -1f

    val since = clock - blinkStart
    val echoed = if (since in 0f..timeline.totalMs.toFloat()) timeline.frameAt(since).closure else 0f
    val idle = if (idleElapsed >= 0f) timeline.frameAt(idleElapsed).closure else 0f
    val closure = max(sleep, max(echoed, idle))

    val t = clock
    val gx = touch?.let { ((it.x / size.width.coerceAtLeast(1f)) - 0.5f) * 2f } ?: (0.55f * sin(t * 0.00031f) + 0.2f * sin(t * 0.00079f))
    val gy = touch?.let { ((it.y / size.height.coerceAtLeast(1f)) - 0.5f) * 2f } ?: (0.4f * sin(t * 0.00023f + 1.3f))
    val gazeX by animateFloatAsState(gx.coerceIn(-1f, 1f), tween(if (touch != null) 120 else 0), label = "gx")
    val gazeY by animateFloatAsState(gy.coerceIn(-1f, 1f), tween(if (touch != null) 120 else 0), label = "gy")

    val look = EyeLook(
        closure = closure, gazeX = gazeX, gazeY = gazeY, glow = glow,
        breath = sin(t * 0.0021f), twist = t * 0.00004f,
    )
    val dpPx = with(LocalDensity.current) { 1.dp.toPx() }

    Canvas(
        modifier.fillMaxWidth().aspectRatio(1.22f)
            .semantics { if (description.isNotEmpty()) contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touch = it },
                    onDragEnd = { touch = null },
                    onDragCancel = { touch = null },
                    onDrag = { change, _ -> touch = change.position; change.consume() },
                )
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                blinkStart = clock
                onTap()
            },
    ) {
        size = this.size
        val w = this.size.width
        val h = this.size.height
        val eyeW = w * 0.80f
        val eyeH = eyeW * 0.50f
        val eyeBounds = Rect(Offset((w - eyeW) / 2f, (h - eyeH) / 2f), Size(eyeW, eyeH))
        if (orbit != null) {
            val ringW = w * 0.97f
            val ringH = h * 0.96f
            drawOrbit(Rect(Offset((w - ringW) / 2f, (h - ringH) / 2f), Size(ringW, ringH)), orbit, dpPx, glow)
        }
        drawEye(eyeBounds, look, dpPx)
    }
}

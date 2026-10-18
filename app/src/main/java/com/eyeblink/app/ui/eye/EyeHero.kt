package com.eyeblink.app.ui.eye

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.Palette
import com.eyeblink.app.ui.kit.raised
import com.eyeblink.app.ui.kit.rememberClockMs
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.BlinkTimeline
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The living eye on the home screen, sitting in a little sky of its own: a sun by day, a moon and
 * stars at night, clouds drifting past.
 *
 * It idles (the gaze drifts, the pupil breathes, it blinks now and then on its own) and it
 * mirrors the real overlay: [echo] is a counter that the engine bumps every time EyeBlink
 * actually blinks the screen, and the hero then blinks with the *same* timeline, so you can
 * see what the system is doing. Touching it makes the eye follow your finger; tapping blinks.
 * When EyeBlink is paused ([awake] = false) the lids close and the colour drains from the iris.
 *
 * [orbit] is the fraction of time left until the next break, or null for no ring. [content] is
 * placed under the eye inside the same card.
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
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val p = P
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = !reduced)
    val haptics = LocalHapticFeedback.current
    val timeline = remember(spec) { BlinkTimeline(spec.copy(reducedMotion = false)) }
    val colors = remember(p) { EyeColors.of(p) }

    var blinkStart by remember { mutableStateOf(-10_000f) }
    var lastEcho by remember { mutableStateOf(echo) }
    if (echo != lastEcho) {
        lastEcho = echo
        blinkStart = clock
    }

    var touch by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(Size.Zero) }

    val sleep by animateFloatAsState(if (awake) 0f else 1f, tween(if (reduced) 1 else 520, easing = if (awake) Motion.Open else Motion.Close), label = "sleep")
    val color by animateFloatAsState(if (awake) 1f else 0.15f, tween(if (reduced) 1 else 700), label = "color")

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

    val look = EyeLook(closure = closure, gazeX = gazeX, gazeY = gazeY, awake = color, breath = sin(t * 0.0021f))
    val dpPx = with(LocalDensity.current) { 1.dp.toPx() }
    val shape = RoundedCornerShape(34.dp)

    Box(modifier.fillMaxWidth().raised(shape, p.sky.soft, p.sky.softLip, depth = 4.dp).clip(shape)) {
        Canvas(Modifier.matchParentSize()) { drawSky(p, clock, dpPx, reduced) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 22.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.72f)
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
                val eyeW = w * 0.62f
                val eyeH = eyeW * 0.62f
                val eyeBounds = Rect(Offset((w - eyeW) / 2f, (h - eyeH) / 2f), Size(eyeW, eyeH))
                if (orbit != null) {
                    val ringW = w * 0.96f
                    val ringH = h * 0.92f
                    drawOrbit(
                        Rect(Offset((w - ringW) / 2f, (h - ringH) / 2f), Size(ringW, ringH)), orbit, dpPx,
                        track = p.card.copy(alpha = if (p.dark) 0.4f else 0.8f), arc = if (p.dark) p.sky.fill else p.sky.lip,
                    )
                }
                drawEye(eyeBounds, look, colors, dpPx)
            }
            content()
        }
    }
}

private fun DrawScope.cloud(cx: Float, cy: Float, s: Float, face: Color, lip: Color, dp: Float) {
    fun body(c: Color, dy: Float) {
        translate(top = dy) {
            drawCircle(c, 0.55f * s, Offset(cx - 0.95f * s, cy + 0.12f * s))
            drawCircle(c, 0.82f * s, Offset(cx, cy - 0.22f * s))
            drawCircle(c, 0.62f * s, Offset(cx + 0.98f * s, cy + 0.06f * s))
            drawRoundRect(c, Offset(cx - 1.5f * s, cy + 0.0f * s), Size(3.1f * s, 0.72f * s), CornerRadius(0.36f * s))
        }
    }
    body(lip, 3f * dp)
    body(face, 0f)
}

/** The sky behind the eye: sun or moon, drifting clouds, a few stars at night. */
private fun DrawScope.drawSky(p: Palette, clockMs: Float, dp: Float, reduced: Boolean) {
    val w = size.width
    val h = size.height
    val t = if (reduced) 0f else clockMs / 1000f
    val face = if (p.dark) Color(0xFF3B5AA3) else Color.White
    val lip = if (p.dark) p.sky.softLip else p.sky.softLip

    // sun or moon, peeking in from the corner
    val r = w * 0.105f
    val c = Offset(w - r * 0.55f, r * 0.55f)
    if (p.dark) {
        drawCircle(p.sun.fill, r, c)
        drawCircle(p.sky.soft, r * 0.86f, Offset(c.x - r * 0.42f, c.y + r * 0.22f))
        for (i in 0 until 9) {
            val sx = w * (0.08f + 0.1f * i + 0.03f * sin(i * 2.3f))
            val sy = h * (0.06f + 0.16f * ((i * 5) % 7) / 7f)
            val a = if (reduced) 0.8f else 0.5f + 0.4f * sin(t * (0.8f + 0.2f * i) + i)
            drawCircle(Color.White.copy(alpha = a), (1.6f + (i % 3) * 0.6f) * dp, Offset(sx, sy))
        }
    } else {
        // rays turn very slowly
        val spin = t * 0.12f
        for (i in 0 until 10) {
            val ang = spin + i * (2f * Math.PI.toFloat() / 10f)
            val r0 = r * 1.2f
            val r1 = r * (if (i % 2 == 0) 1.6f else 1.42f)
            drawLine(
                p.sun.fill,
                Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1),
                strokeWidth = 4.5f * dp, cap = StrokeCap.Round,
            )
        }
        drawCircle(p.sun.lip, r, Offset(c.x, c.y + 3f * dp))
        drawCircle(p.sun.fill, r, c)
    }

    // clouds drift right, wrapping around
    fun drift(base: Float, speed: Float, span: Float): Float = ((base + t * speed) % span + span) % span - span * 0.12f
    val span = w * 1.3f
    cloud(drift(0.16f * span, 6f * dp, span), h * 0.13f, 14f * dp, face, lip, dp)
    cloud(drift(0.80f * span, 4f * dp, span), h * 0.56f, 11f * dp, face, lip, dp)
}

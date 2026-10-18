package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import com.eyeblink.core.anim.EyelidGeometry

/** Springs and curves shared across the UI so that everything moves like one object. */
object Motion {
    /** Lids closing: quick and slightly accelerating, like the real thing. */
    val Close = CubicBezierEasing(0.36f, 0f, 0.24f, 1f)

    /** Lids opening: released fast, settles slowly. */
    val Open = CubicBezierEasing(0.16f, 0f, 0.18f, 1f)

    val Settle = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val Snappy = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
    val Soft = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
}

/**
 * Milliseconds since this composable entered the composition, advanced by the frame clock.
 * Everything that animates continuously (hero eye, aurora) reads this so a screenshot at a
 * given time is exactly reproducible.
 */
@Composable
fun rememberClockMs(running: Boolean = true): State<Float> {
    val ms = remember { mutableStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var start = -1L
        var offset = ms.value
        while (true) {
            withFrameNanos { n ->
                if (start < 0) start = n
                ms.value = offset + (n - start) / 1_000_000f
            }
        }
    }
    return ms
}

/**
 * Press feedback that is ours: no ripple. The element sinks a little and a soft shadow falls
 * from the top like a lid beginning to close. Returns the interaction source to hand to
 * `clickable`, and the press amount for custom drawing.
 */
@Composable
fun rememberPress(): Pair<MutableInteractionSource, State<Float>> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val amount = animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "press")
    return source to amount
}

fun Modifier.pressScale(amount: Float, depth: Float = 0.035f): Modifier =
    this.graphicsLayer {
        val s = 1f - depth * amount
        scaleX = s
        scaleY = s
    }

/**
 * Switches between destinations with a *blink*: lids close over the old screen, the content
 * is swapped while everything is covered, and the lids open on the new one. The app's own signature
 * motion doubles as its page transition. The lids are pale sun-yellow by day and deep indigo at
 * night - the real blink on the screen is pure black, this one only borrows its rhythm. With
 * reduced motion it is a plain cross-fade through the same colour.
 */
@Composable
fun <T> BlinkTransition(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val reduced = LocalReducedMotion.current
    val p = P
    var shown by remember { mutableStateOf(target) }
    val closure = remember { Animatable(0f) }

    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        if (reduced) {
            closure.animateTo(1f, tween(110))
            shown = target
            closure.animateTo(0f, tween(140))
        } else {
            closure.animateTo(1f, tween(150, easing = Motion.Close))
            shown = target
            closure.animateTo(0f, tween(210, easing = Motion.Open))
        }
    }

    Box(modifier) {
        content(shown)
        if (closure.value > 0.001f) {
            Canvas(Modifier.fillMaxSize()) { drawScreenLids(closure.value, reduced, p.lid, p.sun.fill, p.sun.lip) }
        }
    }
}

/** Opaque lids with a seam and a row of lashes, for transitions. The overlay's own look is in BlinkPainter. */
fun DrawScope.drawScreenLids(closure: Float, fade: Boolean, lid: Color, seam: Color, lash: Color) {
    if (fade) {
        drawRect(lid, alpha = closure.coerceIn(0f, 1f))
        return
    }
    val h = size.height
    val w = size.width
    val layers = EyelidGeometry.topLayers(closure, h, curvature = 0.28f, feather = 0.6f)
    for (layer in layers) {
        val m = layer.margin
        val top = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, m.edge)
            quadraticBezierTo(w / 2f, m.control, 0f, m.edge); close()
        }
        val bottom = Path().apply {
            moveTo(0f, h); lineTo(w, h); lineTo(w, h - m.edge)
            quadraticBezierTo(w / 2f, h - m.control, 0f, h - m.edge); close()
        }
        drawPath(top, lid, alpha = layer.alpha)
        drawPath(bottom, lid, alpha = layer.alpha)
    }
    if (closure >= 0.999f) drawRect(lid)

    // The seam where the lids meet, with a few lashes hanging from it, brightest as they touch.
    val glow = ((closure - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (glow > 0f) {
        val y = h / 2f
        val x0 = w * 0.14f
        val x1 = w * 0.86f
        drawLine(seam.copy(alpha = glow), Offset(x0, y), Offset(x1, y), strokeWidth = 5f + 4f * glow, cap = StrokeCap.Round)
        for (i in 0..6) {
            val t = i / 6f
            val x = x0 + (x1 - x0) * t
            val len = (14f + 18f * kotlin.math.sin(Math.PI.toFloat() * t)) * glow
            drawLine(lash.copy(alpha = glow), Offset(x, y + 6f), Offset(x + (t - 0.5f) * 14f, y + 6f + len), strokeWidth = 4f, cap = StrokeCap.Round)
        }
    }
}

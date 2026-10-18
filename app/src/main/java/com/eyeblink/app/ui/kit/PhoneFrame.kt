package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eyeblink.core.anim.BlinkFrame
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.BlinkTimeline
import com.eyeblink.core.anim.EyelidGeometry
import kotlin.math.cos
import kotlin.math.sin

/**
 * The real blink, drawn from a [BlinkFrame]: whole-screen darkening, feathered almond lids, and
 * the pure `#000000` cover. This is the Compose twin of the system overlay's painter; both
 * read the same numbers from `EyelidGeometry` and `BlinkTimeline`.
 */
fun DrawScope.drawBlinkFrame(frame: BlinkFrame, spec: BlinkSpec, rim: Color = Color(0xFFFFC62E)) {
    if (frame.isIdle) return
    if (frame.dim > 0f) drawRect(Color.Black, alpha = frame.dim)
    val w = size.width
    val h = size.height
    if (frame.sweep && frame.closure > 0f) {
        val layers = EyelidGeometry.topLayers(frame.closure, h, spec.curvature, spec.feather)
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
            drawPath(top, Color.Black, alpha = layer.alpha)
            drawPath(bottom, Color.Black, alpha = layer.alpha)
        }
        if (spec.rimLight > 0f && frame.closure < 0.98f) {
            val m = EyelidGeometry.margin(frame.closure, h, spec.curvature)
            val rimColor = rim.copy(alpha = 0.55f * spec.rimLight * (1f - frame.closure))
            val tp = Path().apply { moveTo(0f, m.edge); quadraticBezierTo(w / 2f, m.control, w, m.edge) }
            val bp = Path().apply { moveTo(0f, h - m.edge); quadraticBezierTo(w / 2f, h - m.control, w, h - m.edge) }
            drawPath(tp, rimColor, style = Stroke(2.dp.toPx()))
            drawPath(bp, rimColor, style = Stroke(2.dp.toPx()))
        }
    }
    if (frame.solid > 0f) drawRect(Color.Black, alpha = frame.solid)
}

/** A neutral stand-in for a short-video feed: moving light, a subject, and generic chrome. */
private fun DrawScope.drawFauxReel(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4B3AA8), Color(0xFF136A83), Color(0xFF9A3F5C)), startY = 0f, endY = h))
    val a = t / 1800f
    fun blob(c: Color, cx: Float, cy: Float, r: Float) =
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), radius = r, center = Offset(cx, cy))
    blob(Color(0xFFFFA36B).copy(alpha = 0.75f), w * (0.30f + 0.16f * sin(a)), h * (0.34f + 0.05f * cos(a * 1.3f)), w * 0.62f)
    blob(Color(0xFF52E6D2).copy(alpha = 0.55f), w * (0.72f + 0.12f * cos(a * 0.8f)), h * (0.62f + 0.06f * sin(a)), w * 0.7f)
    blob(Color(0xFFFF6B8E).copy(alpha = 0.5f), w * (0.5f + 0.2f * sin(a * 0.6f)), h * 0.82f, w * 0.6f)
    // the "subject"
    val cx = w * (0.46f + 0.05f * sin(a * 0.9f))
    drawCircle(Color(0xFFFFE9D6).copy(alpha = 0.92f), radius = w * 0.13f, center = Offset(cx, h * 0.38f))
    drawRoundRect(Color(0xFFFFE9D6).copy(alpha = 0.85f), Offset(cx - w * 0.19f, h * 0.47f), Size(w * 0.38f, h * 0.22f), CornerRadius(w * 0.18f))
    // chrome: action column and caption
    for (i in 0..3) drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.045f, center = Offset(w * 0.9f, h * (0.5f + i * 0.095f)))
    drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.1f, h * 0.88f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(w * 0.18f, h * 0.865f), Size(w * 0.3f, h * 0.014f), CornerRadius(8f))
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.07f, h * 0.92f), Size(w * 0.62f, h * 0.012f), CornerRadius(8f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.07f, h * 0.945f), Size(w * 0.44f, h * 0.012f), CornerRadius(8f))
}

/**
 * A phone showing a feed with the blink playing over it, so that every setting in Blink Studio
 * can be judged on something that looks like what it will be drawn on. Loops on its own;
 * tapping blinks at once.
 */
@Composable
fun PhoneFrame(spec: BlinkSpec, modifier: Modifier = Modifier, description: String = "", loopMs: Float = 2_800f) {
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = true)
    val timeline = remember(spec, reduced) { BlinkTimeline(spec.copy(reducedMotion = spec.reducedMotion || reduced)) }
    var tapAt by remember { mutableStateOf(-100_000f) }
    val phaseMs = clock % loopMs
    val auto = timeline.frameAt(phaseMs - 1_300f)
    val manualElapsed = clock - tapAt
    val manual = if (manualElapsed in 0f..timeline.totalMs.toFloat()) timeline.frameAt(manualElapsed) else BlinkFrame.Idle
    val frame = if (!manual.isIdle) manual else auto
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
    val rim = P.sun.fill

    Canvas(
        modifier.fillMaxHeight().aspectRatio(9f / 18.5f)
            .raised(shape, Color(0xFF16254A), Color(0xFF0B1330), depth = 4.dp)
            .clip(shape)
            .semantics { if (description.isNotEmpty()) contentDescription = description }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { tapAt = clock },
    ) {
        val inset = 5.dp.toPx()
        val inner = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(30.dp.toPx())))
        }
        clipPath(inner) {
            drawFauxReel(clock)
            // status bar hint
            drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.42f, 12.dp.toPx()), Size(size.width * 0.16f, 5.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawBlinkFrame(frame, spec, rim)
        }
    }
}

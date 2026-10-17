package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.EaseKind
import kotlin.math.roundToInt

/** Seven short bars with a label under each; the last one is lit. */
@Composable
fun MiniBars(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 84.dp, accent: Color = Dusk.Iris) {
    val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val gap = 10.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val bh = (size.height * (v / max)).coerceAtLeast(4.dp.toPx())
                val x = i * (bw + gap)
                val last = i == values.lastIndex
                drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2f))
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        if (last) listOf(Dusk.IrisGlow, accent, Dusk.IrisDeep) else listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.22f)),
                        startY = size.height - bh, endY = size.height,
                    ),
                    topLeft = Offset(x, size.height - bh), size = Size(bw, bh), cornerRadius = CornerRadius(bw / 2f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, l -> EbText(l, Ink.Eyebrow, color = if (i == labels.lastIndex) Dusk.Text else Dusk.TextFaint, caps = false, maxLines = 1) }
        }
    }
}

/** An easing curve drawn as a little graph, selectable. */
@Composable
fun CurveThumb(kind: EaseKind, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (source, press) = rememberPress()
    Column(
        modifier.pressScale(press.value, 0.05f)
            .glass(RoundedCornerShape(18.dp), glow = if (selected) Dusk.Iris else null, strength = if (selected) 1.2f else 0.7f)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = label }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = 62.dp, height = 40.dp)) {
            val pad = 4.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val p = Path()
            for (i in 0..40) {
                val x = i / 40f
                val y = kind.at(x).coerceIn(-0.25f, 1.25f)
                val px = pad + x * w
                val py = pad + (1f - y) * h
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawLine(Color.White.copy(alpha = 0.10f), Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 1.dp.toPx())
            drawPath(
                p, if (selected) Dusk.Iris else Dusk.TextDim,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        EbText(label, Ink.Eyebrow, color = if (selected) Dusk.Iris else Dusk.TextFaint, caps = false, maxLines = 1, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * The three beats of a blink as one bar: close, hold, open, each as wide as it lasts. Drag the
 * seams between them to retime the blink; the total updates live.
 */
@Composable
fun TimelineEditor(
    spec: BlinkSpec,
    onChange: (closeMs: Int, holdMs: Int, openMs: Int) -> Unit,
    modifier: Modifier = Modifier,
    playhead: Float? = null,
    labels: Triple<String, String, String> = Triple("close", "hold", "open"),
) {
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableStateOf(1f) }
    val specNow by rememberUpdatedState(spec)
    val onChangeNow by rememberUpdatedState(onChange)
    // The bar always represents this many milliseconds so that changing one beat visibly moves the others.
    val scaleMs = 1_000f

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(86.dp).onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val s0 = specNow
                        val x1 = (s0.closeMs / scaleMs) * widthPx
                        val x2 = ((s0.closeMs + s0.holdMs) / scaleMs) * widthPx
                        val grab = 28.dp.toPx()
                        val which = when {
                            kotlin.math.abs(down.position.x - x1) <= grab && kotlin.math.abs(down.position.x - x1) <= kotlin.math.abs(down.position.x - x2) -> 1
                            kotlin.math.abs(down.position.x - x2) <= grab -> 2
                            else -> 0
                        }
                        if (which != 0) {
                            var lastTick = -1
                            drag(down.id) { change ->
                                val ms = ((change.position.x / widthPx) * scaleMs).roundToInt().coerceIn(0, 1_000)
                                val s = specNow
                                if (which == 1) {
                                    val c = ms.coerceIn(BlinkSpec.MIN_PHASE_MS, BlinkSpec.MAX_CLOSE_MS)
                                    onChangeNow(c, s.holdMs, s.openMs)
                                } else {
                                    val h = (ms - s.closeMs).coerceIn(0, BlinkSpec.MAX_HOLD_MS)
                                    onChangeNow(s.closeMs, h, s.openMs)
                                }
                                val tick = ms / 10
                                if (tick != lastTick) { lastTick = tick; if (tick % 5 == 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val c = (spec.closeMs / scaleMs) * w
            val ho = (spec.holdMs / scaleMs) * w
            val o = (spec.openMs / scaleMs) * w
            val top = 8.dp.toPx()
            val bh = h - 30.dp.toPx()
            val r = CornerRadius(14.dp.toPx())

            drawRoundRect(Color.White.copy(alpha = 0.04f), Offset(0f, top), Size(w, bh), r)
            // close: lids sweeping down
            drawRoundRect(
                Brush.horizontalGradient(listOf(Dusk.Iris.copy(alpha = 0.10f), Dusk.Iris.copy(alpha = 0.42f)), 0f, c),
                Offset(0f, top), Size(c, bh), CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            // hold: black
            drawRect(Color.Black, Offset(c, top), Size(ho, bh))
            drawRect(Color.White.copy(alpha = 0.10f), Offset(c, top), Size(ho, bh), style = Stroke(1.dp.toPx()))
            // open: lids lifting
            drawRoundRect(
                Brush.horizontalGradient(listOf(Dusk.Amber.copy(alpha = 0.42f), Dusk.Amber.copy(alpha = 0.10f)), c + ho, c + ho + o),
                Offset(c + ho, top), Size(o, bh), CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            // handles
            for (x in listOf(c, c + ho)) {
                drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x - 2.dp.toPx(), top + 8.dp.toPx()), Size(4.dp.toPx(), bh - 16.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            // ruler
            for (i in 0..10) {
                val x = w * i / 10f
                drawLine(Color.White.copy(alpha = 0.18f), Offset(x, h - 18.dp.toPx()), Offset(x, h - (if (i % 5 == 0) 8f else 12f).dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            playhead?.let {
                val x = (it / scaleMs) * w
                drawLine(Color.White, Offset(x, top - 4.dp.toPx()), Offset(x, top + bh + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                drawCircle(Color.White, 3.dp.toPx(), Offset(x, top - 4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val total = (spec.totalMs).coerceAtLeast(1).toFloat()
            fun w(ms: Int) = (ms / total).coerceAtLeast(0.14f)
            Column(Modifier.weight(w(spec.closeMs))) { EbText(labels.first, Ink.Eyebrow, color = Dusk.Iris, caps = false); EbText("${spec.closeMs} ms", Ink.MonoSmall, caps = false, maxLines = 1) }
            Column(Modifier.weight(w(spec.holdMs)), horizontalAlignment = Alignment.CenterHorizontally) { EbText(labels.second, Ink.Eyebrow, color = Dusk.TextDim, caps = false); EbText("${spec.holdMs} ms", Ink.MonoSmall, caps = false, maxLines = 1) }
            Column(Modifier.weight(w(spec.openMs)), horizontalAlignment = Alignment.End) { EbText(labels.third, Ink.Eyebrow, color = Dusk.Amber, caps = false); EbText("${spec.openMs} ms", Ink.MonoSmall, caps = false, maxLines = 1) }
        }
    }
}

/** A thin gradient meter: how full something is. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, tone: Color = Dusk.Iris) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = CornerRadius(3.dp.toPx()))
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.6f), tone)), size = Size(w, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
    }
}

/** A meter cut into coloured parts - for example verified, unverified and failed pauses. */
@Composable
fun SplitMeter(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = r)
        val live = parts.filter { it.first > 0f }
        val total = live.sumOf { it.first.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (live.size - 1)
        var x = 0f
        live.forEach { (v, c) ->
            val w = usable * v / total
            drawRoundRect(c, Offset(x, 0f), Size(w, size.height), r)
            x += w + gap
        }
    }
}

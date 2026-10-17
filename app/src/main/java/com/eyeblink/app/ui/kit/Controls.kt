package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * A switch that is a small eye. Off, the lid hangs low over the iris; on, it lifts and the
 * iris glows and the pupil opens. The thumb is the iris, so the control reads as "awake / asleep".
 */
@Composable
fun LidSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, if (reduced) spring(stiffness = 3000f) else Motion.Snappy, label = "pos")
    val open by animateFloatAsState(if (checked) 1f else 0f, if (reduced) spring(stiffness = 3000f) else spring(dampingRatio = 0.62f, stiffness = 320f), label = "open")
    val (source, press) = rememberPress()

    Canvas(
        modifier.size(58.dp, 34.dp).pressScale(press.value, 0.04f)
            .semantics { if (description != null) contentDescription = description }
            .toggleable(
                value = checked, interactionSource = source, indication = null, role = Role.Switch,
                onValueChange = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(it) },
            ),
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        val pad = 4.dp.toPx()
        val tr = r - pad
        val trackOff = Dusk.Ink3
        val trackOn = Color(0xFF0E3A40)
        val track = lerp(trackOff, trackOn, pos)

        drawRoundRect(track, cornerRadius = CornerRadius(r, r))
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)), Offset.Zero, Offset(w, h),
            ),
            cornerRadius = CornerRadius(r, r), style = Stroke(1.dp.toPx()),
        )
        if (pos > 0.01f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Dusk.Iris.copy(alpha = 0.0f), Dusk.Iris.copy(alpha = 0.32f * pos))),
                cornerRadius = CornerRadius(r, r),
            )
        }

        val cx = lerp(pad + tr, w - pad - tr, pos)
        val cy = h / 2f
        if (pos > 0.02f) {
            drawCircle(
                Brush.radialGradient(listOf(Dusk.Iris.copy(alpha = 0.55f * pos), Color.Transparent), Offset(cx, cy), tr * 2.2f),
                radius = tr * 2.2f, center = Offset(cx, cy),
            )
        }
        // iris
        drawCircle(
            Brush.radialGradient(
                listOf(Dusk.IrisGlow, Dusk.Iris, Dusk.IrisDeep, Color(0xFF0B3B44)),
                center = Offset(cx - tr * 0.2f, cy - tr * 0.25f), radius = tr * 1.25f,
            ),
            radius = tr, center = Offset(cx, cy),
        )
        val pupil = tr * lerp(0.30f, 0.46f, open)
        drawCircle(Color.Black, radius = pupil, center = Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = tr * 0.13f, center = Offset(cx - tr * 0.34f, cy - tr * 0.36f))

        // the lid over the iris: lifts as the switch turns on
        val lid = (1f - open).coerceIn(0f, 1f)
        if (lid > 0.01f) {
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - tr, cy - tr, cx + tr, cy + tr)) }
            clipPath(circle) {
                val bottom = cy - tr + 2f * tr * (0.18f + 0.50f * lid)
                val p = Path().apply {
                    moveTo(cx - tr, cy - tr); lineTo(cx + tr, cy - tr); lineTo(cx + tr, bottom)
                    quadraticBezierTo(cx, bottom + tr * 0.55f * lid, cx - tr, bottom); close()
                }
                drawPath(p, track)
                drawPath(
                    Path().apply { moveTo(cx - tr, bottom); quadraticBezierTo(cx, bottom + tr * 0.55f * lid, cx + tr, bottom) },
                    Dusk.Amber.copy(alpha = 0.85f * lid), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Slider whose thumb is an iris. A bubble above the thumb shows the value while it is held,
 * detents tick with a haptic, and the whole thing is exposed to accessibility as a real slider.
 */
@Composable
fun Horizon(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    /** Number of equal steps. 0 = continuous. */
    steps: Int = 0,
    format: (Float) -> String = { "${(it * 100).roundToInt()}%" },
    description: String = "",
    accent: Color = Dusk.Iris,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(1f) }
    var held by remember { mutableStateOf(false) }
    val span = range.endInclusive - range.start
    val target = ((value - range.start) / span).coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.9f, stiffness = 900f), label = "h")
    val lift by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
    val padPx = with(density) { 16.dp.toPx() }

    fun fromX(x: Float): Float {
        val f = ((x - padPx) / (widthPx - 2 * padPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val snapped = if (steps > 0) (f * steps).roundToInt() / steps.toFloat() else f
        return range.start + snapped * span
    }

    // The gesture loop must not restart when a recomposition hands us a new lambda mid-drag,
    // so it reads the latest callbacks through state instead of capturing them.
    val onChangeNow by rememberUpdatedState(onChange)
    val valueNow by rememberUpdatedState(value)
    var last by remember { mutableStateOf(value) }

    Box(
        modifier.fillMaxWidth().height(60.dp).onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                role = Role.Button
                if (description.isNotEmpty()) contentDescription = description
                stateDescription = format(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { v -> onChange(v.coerceIn(range.start, range.endInclusive)); true }
            }
            .pointerInput(steps, range.start, range.endInclusive) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    held = true
                    fun move(x: Float, tick: Boolean) {
                        val v = fromX(x)
                        if (v != last) {
                            if (tick && steps > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            last = v
                            onChangeNow(v)
                        }
                    }
                    last = valueNow
                    move(down.position.x, tick = false)
                    drag(down.id) { change ->
                        move(change.position.x, tick = true)
                        change.consume()
                    }
                    held = false
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
            val w = size.width
            val cy = size.height * 0.62f
            val x0 = 16.dp.toPx()
            val x1 = w - 16.dp.toPx()
            val tx = lerp(x0, x1, shown)
            val line = 3.dp.toPx()

            drawLine(Color.White.copy(alpha = 0.12f), Offset(x0, cy), Offset(x1, cy), strokeWidth = line, cap = StrokeCap.Round)
            if (steps in 1..24) {
                for (i in 0..steps) {
                    val x = lerp(x0, x1, i / steps.toFloat())
                    drawLine(Color.White.copy(alpha = if (x <= tx) 0.28f else 0.14f), Offset(x, cy - 7.dp.toPx()), Offset(x, cy - 3.dp.toPx()), strokeWidth = 1.dp.toPx())
                }
            }
            drawLine(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent), startX = x0, endX = tx.coerceAtLeast(x0 + 1f)),
                Offset(x0, cy), Offset(tx, cy), strokeWidth = line, cap = StrokeCap.Round,
            )
            val r = (10f + 2f * lift).dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.40f + 0.2f * lift), Color.Transparent), Offset(tx, cy), r * 2.6f),
                radius = r * 2.6f, center = Offset(tx, cy),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Dusk.IrisGlow, accent, Dusk.IrisDeep, Color(0xFF0B3B44)),
                    center = Offset(tx - r * 0.2f, cy - r * 0.25f), radius = r * 1.3f,
                ),
                radius = r, center = Offset(tx, cy),
            )
            drawCircle(Color.Black, radius = r * (0.34f + 0.12f * lift), center = Offset(tx, cy))
            drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.13f, center = Offset(tx - r * 0.34f, cy - r * 0.36f))
        }

        // value bubble
        val tx = with(density) { lerp(16.dp.toPx(), widthPx - 16.dp.toPx(), shown) }
        Box(
            Modifier.offset { IntOffset((tx - with(density) { 28.dp.toPx() }).roundToInt(), (-4.dp.toPx() * lift).roundToInt()) }
                .width(56.dp).alpha(lift),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.glass(RoundedCornerShape(50), strength = 1.3f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                EbText(format(value), Ink.MonoSmall, color = Dusk.Text, caps = false, maxLines = 1)
            }
        }
    }
}

/** Segmented selector with a glowing seam that slides to the chosen option. */
@Composable
fun Tide(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(selected.toFloat(), Motion.Snappy, label = "tide")
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(50), strength = 0.8f)) {
        val itemW = maxWidth / options.size
        Box(
            Modifier.offset(x = itemW * pos + 4.dp, y = 4.dp).width(itemW - 8.dp).height(40.dp)
                .drawBehind {
                    val shape = RoundedCornerShape(50).createOutline(size, layoutDirection, this)
                    drawOutline(shape, Dusk.Iris.copy(alpha = 0.16f))
                    drawOutline(shape, Dusk.Iris.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
                },
        )
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .semantics { role = Role.Button; stateDescription = if (i == selected) "selected" else "" }
                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                            if (i != selected) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    EbText(label, Ink.Caption, color = if (i == selected) Dusk.Iris else Dusk.TextDim, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** A row in a settings group: title, optional explanation, and a control on the right. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val base = if (onClick != null) modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onClick) else modifier
    Row(base.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            EbText(title, Ink.BodyStrong)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                EbText(subtitle, Ink.Caption, color = Dusk.TextDim)
            }
        }
        trailing()
    }
}

/** Number stepper: a quiet minus, the value in serif, a quiet plus. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusDescription: String = "-", plusDescription: String = "+") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Icon.Minus, minusDescription, onMinus, size = 38.dp)
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { EbText(value, Ink.Mono, caps = false, maxLines = 1) }
        RoundIconButton(Icon.Plus, plusDescription, onPlus, size = 38.dp)
    }
}

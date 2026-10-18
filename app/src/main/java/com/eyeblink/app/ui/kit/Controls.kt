package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * A switch that is a small eye. Off, a lid hangs over the eye in the knob and it looks sleepy;
 * on, the lid lifts, the iris shows and the groove turns mint. The knob is the eye, so the
 * control reads as "awake / asleep" at a glance.
 */
@Composable
fun LidSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val p = P
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
        val pad = 3.dp.toPx()
        val tr = r - pad
        val track = lerp(p.ink.copy(alpha = 0.22f), p.mint.fill, pos)
        val trackLip = lerp(p.ink.copy(alpha = 0.34f), p.mint.lip, pos)

        drawRoundRect(trackLip, Offset(0f, 2.dp.toPx()), Size(w, h), CornerRadius(r, r))
        drawRoundRect(track, size = Size(w, h), cornerRadius = CornerRadius(r, r))

        val cx = lerp(pad + tr, w - pad - tr, pos)
        val cy = h / 2f
        // the knob: a white eyeball on a lip of its own
        drawCircle(trackLip, radius = tr, center = Offset(cx, cy + 1.5.dp.toPx()))
        drawCircle(Color.White, radius = tr, center = Offset(cx, cy))
        val iris = tr * 0.66f
        drawCircle(lerp(p.inkFaint, p.sky.fill, open), radius = iris, center = Offset(cx, cy + tr * 0.06f))
        drawCircle(p.onFill, radius = iris * lerp(0.46f, 0.58f, open), center = Offset(cx, cy + tr * 0.06f))
        drawCircle(Color.White, radius = tr * 0.13f, center = Offset(cx - tr * 0.26f, cy - tr * 0.2f))

        // the lid: lifts as the switch turns on
        val lid = (1f - open).coerceIn(0f, 1f)
        if (lid > 0.01f) {
            val circle = Path().apply { addOval(Rect(cx - tr, cy - tr, cx + tr, cy + tr)) }
            clipPath(circle) {
                val bottom = cy - tr + 2f * tr * (0.16f + 0.52f * lid)
                val lidPath = Path().apply {
                    moveTo(cx - tr, cy - tr); lineTo(cx + tr, cy - tr); lineTo(cx + tr, bottom)
                    quadraticBezierTo(cx, bottom + tr * 0.5f * lid, cx - tr, bottom); close()
                }
                drawPath(lidPath, track)
                drawPath(
                    Path().apply { moveTo(cx - tr, bottom); quadraticBezierTo(cx, bottom + tr * 0.5f * lid, cx + tr, bottom) },
                    p.onFill, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Slider whose thumb is an eye. A tooltip above the thumb shows the value while it is held,
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
    accent: Hue? = null,
) {
    val p = P
    val hue = accent ?: p.sky
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(1f) }
    var held by remember { mutableStateOf(false) }
    val span = range.endInclusive - range.start
    val target = ((value - range.start) / span).coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.9f, stiffness = 900f), label = "h")
    val lift by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
    val padPx = with(density) { 18.dp.toPx() }

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
        modifier.fillMaxWidth().height(64.dp).onSizeChanged { widthPx = it.width.toFloat() }
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
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width
            val cy = size.height * 0.64f
            val x0 = 18.dp.toPx()
            val x1 = w - 18.dp.toPx()
            val tx = lerp(x0, x1, shown)
            val line = 10.dp.toPx()

            drawLine(p.track, Offset(x0, cy), Offset(x1, cy), strokeWidth = line, cap = StrokeCap.Round)
            if (tx > x0) drawLine(hue.fill, Offset(x0, cy), Offset(tx, cy), strokeWidth = line, cap = StrokeCap.Round)
            if (steps in 1..24) {
                for (i in 0..steps) {
                    val x = lerp(x0, x1, i / steps.toFloat())
                    drawCircle(if (x <= tx) hue.lip else p.inkFaint.copy(alpha = 0.5f), radius = 1.8.dp.toPx(), center = Offset(x, cy))
                }
            }

            val r = (13f + 1.5f * lift).dp.toPx()
            val c = Offset(tx, cy - 1.dp.toPx() * lift)
            drawCircle(hue.lip, radius = r, center = Offset(c.x, c.y + 2.5.dp.toPx()))
            drawCircle(Color.White, radius = r, center = c)
            drawCircle(hue.fill, radius = r * 0.64f, center = c)
            drawCircle(p.onFill, radius = r * (0.30f + 0.06f * lift), center = c)
            drawCircle(Color.White, radius = r * 0.12f, center = Offset(c.x - r * 0.22f, c.y - r * 0.24f))
        }

        // value tooltip
        val tx = with(density) { lerp(18.dp.toPx(), widthPx - 18.dp.toPx(), shown) }
        Box(
            Modifier.offset { IntOffset((tx - with(density) { 30.dp.toPx() }).roundToInt(), (-2.dp.toPx() * lift).roundToInt()) }
                .width(60.dp).alpha(lift),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.clip(RoundedCornerShape(50)).drawBehind { drawRect(p.ink) }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                EbText(format(value), Ink.NumSmall, color = p.bg, maxLines = 1)
            }
        }
    }
}

/** Segmented selector: a sun-yellow thumb slides to the chosen option. */
@Composable
fun Tide(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = P
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(selected.toFloat(), Motion.Snappy, label = "tide")
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth().height(50.dp).clip(shape).drawBehind { drawRect(p.sunk) }) {
        val itemW = maxWidth / options.size
        Box(
            Modifier.offset(x = itemW * pos + 4.dp, y = 4.dp).width(itemW - 8.dp).height(42.dp)
                .raised(shape, p.sun.fill, p.sun.lip, depth = 2.dp),
        )
        Row(Modifier.fillMaxWidth().height(50.dp)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(50.dp)
                        .semantics { role = Role.Button; stateDescription = if (i == selected) "selected" else "" }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            if (i != selected) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    EbText(label, Ink.BodyStrong, color = if (i == selected) p.onFill else p.inkSoft, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
    }
}

/** A round tick box: an empty ring, or a sun-yellow disc with a check once it is on. */
@Composable
fun CheckDot(on: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val p = P
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            if (on) {
                drawCircle(p.sun.lip, center = center.copy(y = center.y + 2.dp.toPx()))
                drawCircle(p.sun.fill)
            } else {
                drawCircle(p.line, style = Stroke(2.dp.toPx()))
            }
        }
        if (on) Ico(Icon.Check, size = size * 0.58f, color = p.onFill, weight = 2.8f)
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
    val base = if (onClick != null) modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick) else modifier
    Row(base.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            EbText(title, Ink.BodyStrong)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                EbText(subtitle, Ink.Caption, color = P.inkSoft)
            }
        }
        trailing()
    }
}

/** Number stepper: a round minus, the value, a round plus. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusDescription: String = "-", plusDescription: String = "+") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Icon.Minus, minusDescription, onMinus, size = 40.dp)
        Box(Modifier.width(66.dp), contentAlignment = Alignment.Center) { EbText(value, Ink.Num, maxLines = 1) }
        RoundIconButton(Icon.Plus, plusDescription, onPlus, size = 40.dp)
    }
}

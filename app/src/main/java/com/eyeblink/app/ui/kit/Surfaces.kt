package com.eyeblink.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass surface, lit from within rather than raised: a faint white veil, a hairline
 * that is brighter at the top-left like light catching an edge, and optionally a soft bloom of
 * colour in the corner. No shadows and no elevation anywhere in the app.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    strength: Float = 1f,
    glow: Color? = null,
    border: Boolean = true,
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, brush = Dusk.glass(strength))
    if (glow != null) {
        val clip = Path().apply { addOutline(outline) }
        clipPath(clip) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.20f * strength), glow.copy(alpha = 0.06f * strength), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f), radius = size.width * 0.95f,
                ),
                radius = size.width * 0.95f, center = Offset(size.width * 0.12f, 0f),
            )
        }
    }
    if (border) {
        drawOutline(
            outline,
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.28f * strength), Color.White.copy(alpha = 0.05f * strength), Color.White.copy(alpha = 0.12f * strength)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    glow: Color? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    strength: Float = 1f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (source, press) = rememberPress()
    val base = modifier.pressScale(if (onClick != null) press.value else 0f, depth = 0.02f).glass(shape, strength, glow).clip(shape)
    val clickable = if (onClick != null) base.clickable(interactionSource = source, indication = null, onClick = onClick) else base
    Column(clickable.padding(padding), content = content)
}

/** A small label above a title, the way a museum caption sits above a work. */
@Composable
fun SectionHeader(eyebrow: String, title: String? = null, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            EbText(eyebrow, Ink.Eyebrow, color = Dusk.Amber)
            if (title != null) {
                Spacer(Modifier.height(6.dp))
                EbText(title, Ink.Headline)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Dusk.Hairline))
}

enum class Tone(val color: Color) { Ok(Dusk.Ok), Warn(Dusk.Warn), Bad(Dusk.Bad), Iris(Dusk.Iris), Amber(Dusk.Amber), Quiet(Dusk.TextDim) }

@Composable
fun Badge(text: String, tone: Tone = Tone.Quiet, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(tone.color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(tone.color))
        EbText(text, Ink.MonoSmall, color = tone.color, caps = false, maxLines = 1)
    }
}

/** A dot that breathes, for "alive" states such as the camera indicator. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp, pulsing: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val alpha = if (pulsing && !reduced) a else 1f
    Box(
        modifier.size(size).drawBehind {
            drawCircle(color.copy(alpha = 0.25f * alpha), radius = this.size.minDimension * 0.9f)
            drawCircle(color.copy(alpha = alpha), radius = this.size.minDimension / 2f)
        },
    )
}

/** A click target with no ripple and no visual feedback of its own. */
fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = composed {
    clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
}

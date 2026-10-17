package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How everything raised in EyeBlink is drawn: a flat [face] sitting on a darker [lip] a few dp
 * deep. [sink] is how far it has been pressed (0..1): the face drops onto the lip, which stays
 * where it is - a lid coming down. Nothing blurs, glows or is translucent.
 *
 * The lip is drawn outside the element's bounds, so leave a few dp of room beneath it.
 */
fun Modifier.raised(
    shape: Shape,
    face: Color,
    lip: Color,
    depth: Dp = 3.dp,
    border: Color? = null,
    sink: () -> Float = { 0f },
): Modifier = this
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = depth.toPx()) { drawOutline(outline, lip) }
    }
    .graphicsLayer { translationY = sink() * depth.toPx() }
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, face)
        if (border != null) drawOutline(outline, border, style = Stroke(1.5.dp.toPx()))
    }

/**
 * A card. Plain cards are white (indigo at night); a [tint] paints the face in the pale version of
 * one of the five hues so each part of a screen can have its own colour.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    tint: Hue? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = P
    val (source, press) = rememberPress()
    val base = modifier
        .raised(
            shape, face = tint?.soft ?: p.card, lip = tint?.softLip ?: p.cardLip,
            border = if (tint == null) p.line else null,
            sink = { if (onClick != null) press.value else 0f },
        )
        .clip(shape)
    val clickable = if (onClick != null) {
        base.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    } else base
    Column(clickable.padding(padding), content = content)
}

/** The heading of a card or a block. With a [title] the small [eyebrow] sits above it; alone it is the title. */
@Composable
fun SectionHeader(eyebrow: String, title: String? = null, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (title != null) {
                EbText(eyebrow, Ink.Eyebrow, color = P.sky.text)
                Spacer(Modifier.height(4.dp))
                EbText(title, Ink.Headline)
            } else {
                EbText(eyebrow, Ink.Title)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.5.dp).background(P.line))
}

enum class Tone { Ok, Warn, Bad, Sky, Sun, Grape, Quiet }

@Composable
fun Tone.hue(): Hue? = when (this) {
    Tone.Ok -> P.mint
    Tone.Warn -> P.sun
    Tone.Bad -> P.coral
    Tone.Sky -> P.sky
    Tone.Sun -> P.sun
    Tone.Grape -> P.grape
    Tone.Quiet -> null
}

/** The colour of a tone for text and thin marks. */
@Composable
fun Tone.textColor(): Color = hue()?.text ?: P.inkSoft

@Composable
fun Badge(text: String, tone: Tone = Tone.Quiet, modifier: Modifier = Modifier, dot: Boolean = true) {
    val p = P
    val hue = tone.hue()
    val face = hue?.soft ?: p.sunk
    val fg = hue?.text ?: p.inkSoft
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(face).border(1.5.dp, hue?.softLip ?: p.line, shape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(7.dp).clip(CircleShape).background(hue?.fill ?: p.inkFaint))
        EbText(text, Ink.NumSmall, color = fg, maxLines = 1)
    }
}

/** An icon on a small raised tile in one of the hues - the little coloured square that heads a row or a card. */
@Composable
fun IconBadge(icon: Icon, hue: Hue, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.34f)
    Box(modifier.size(size).raised(shape, hue.fill, hue.lip, depth = 2.dp), contentAlignment = Alignment.Center) {
        Ico(icon, size = size * 0.54f, color = P.onFill, weight = 2.1f)
    }
}

/** A dot that breathes, for "alive" states such as the camera indicator. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp, pulsing: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val alpha = if (pulsing && !reduced) a else 0.5f
    Box(
        modifier.size(size).drawBehind {
            drawCircle(color.copy(alpha = alpha * 0.6f), radius = this.size.minDimension * 0.95f)
            drawCircle(color, radius = this.size.minDimension / 2f)
        },
    )
}

/** A click target with no ripple and no visual feedback of its own. */
fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = composed {
    clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
}

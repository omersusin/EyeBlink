package com.eyeblink.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Glass, Ghost, Danger }

/**
 * Pill button. Primary is the amber of a lid seam catching light; pressing it lowers a soft
 * shadow from the top, as if a lid were beginning to close over it.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: Icon? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val (source, press) = rememberPress()
    val shape = RoundedCornerShape(50)
    val fg = when (kind) {
        ButtonKind.Primary -> Color(0xFF1A1006)
        ButtonKind.Danger -> Dusk.Bad
        ButtonKind.Glass -> Dusk.Text
        ButtonKind.Ghost -> Dusk.TextDim
    }
    val base = modifier
        .alpha(if (enabled) 1f else 0.4f)
        .pressScale(press.value)
        .let { m ->
            when (kind) {
                ButtonKind.Primary -> m.clip(shape).background(Dusk.dawn(Offset.Zero, Offset(400f, 200f)))
                ButtonKind.Danger -> m.glass(shape, glow = Dusk.Bad)
                ButtonKind.Glass -> m.glass(shape)
                ButtonKind.Ghost -> m.clip(shape)
            }
        }
        .drawWithContent {
            drawContent()
            val p = press.value
            if (p > 0.01f) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f * p), Color.Transparent), endY = size.height * 0.7f),
                )
            }
        }
        .semantics { role = Role.Button }
        .let { if (enabled) it.clickable(interactionSource = source, indication = null, onClick = onClick) else it }
        .defaultMinSize(minHeight = if (compact) 40.dp else 52.dp)
        .padding(horizontal = if (compact) 16.dp else 24.dp)

    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (icon != null) Ico(icon, size = 18.dp, color = fg)
        EbText(text, Ink.Button, color = fg, maxLines = 1)
    }
}

@Composable
fun RoundIconButton(
    icon: Icon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Dusk.Text,
    glow: Color? = null,
) {
    val (source, press) = rememberPress()
    Box(
        modifier.size(size).pressScale(press.value).glass(CircleShape, glow = glow).clip(CircleShape)
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, size = size * 0.46f, color = tint) }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon? = null,
) {
    val (source, press) = rememberPress()
    val shape = RoundedCornerShape(50)
    val fg = if (selected) Dusk.Iris else Dusk.TextDim
    Row(
        modifier.pressScale(press.value, 0.05f)
            .let { if (selected) it.clip(shape).background(Dusk.Iris.copy(alpha = 0.14f)).glass(shape, strength = 0.2f, border = true) else it.glass(shape, strength = 0.7f) }
            .semantics { role = Role.Button }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) Ico(icon, size = 16.dp, color = fg)
        EbText(text, Ink.Caption, color = fg, maxLines = 1)
    }
}

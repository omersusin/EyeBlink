package com.eyeblink.app.ui.kit

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Soft, Ghost, Danger }

/**
 * The pill button. Primary is sun-yellow with a deeper yellow lip; Soft is a white (or indigo)
 * pill; Danger is a pale coral one. Pressing any of them sinks the face onto its lip.
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
    val p = P
    val (source, press) = rememberPress()
    val shape = RoundedCornerShape(50)
    val fg = when (kind) {
        ButtonKind.Primary -> p.onFill
        ButtonKind.Danger -> p.coral.text
        ButtonKind.Soft -> p.ink
        ButtonKind.Ghost -> p.inkSoft
    }
    val base = modifier
        .alpha(if (enabled) 1f else 0.45f)
        .let { m ->
            when (kind) {
                ButtonKind.Primary -> m.raised(shape, p.sun.fill, p.sun.lip, sink = { press.value })
                ButtonKind.Danger -> m.raised(shape, p.coral.soft, p.coral.softLip, sink = { press.value })
                ButtonKind.Soft -> m.raised(shape, p.card, p.cardLip, border = p.line, sink = { press.value })
                ButtonKind.Ghost -> m.pressScale(press.value, 0.04f)
            }
        }
        .clip(shape)
        .semantics { role = Role.Button }
        .let { if (enabled) it.clickable(interactionSource = source, indication = null, onClick = onClick) else it }
        .defaultMinSize(minHeight = if (compact) 42.dp else 54.dp)
        .padding(horizontal = if (compact) 18.dp else 26.dp)

    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (icon != null) Ico(icon, size = 20.dp, color = fg, weight = 2.1f)
        EbText(text, Ink.Button, color = fg, maxLines = 1)
    }
}

/** A round button. Plain it is a white disc; with a [hue] it takes that colour. */
@Composable
fun RoundIconButton(
    icon: Icon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color? = null,
    hue: Hue? = null,
) {
    val p = P
    val (source, press) = rememberPress()
    val face = hue?.fill ?: p.card
    val lip = hue?.lip ?: p.cardLip
    Box(
        modifier.size(size)
            .raised(CircleShape, face, lip, depth = 3.dp, border = if (hue == null) p.line else null, sink = { press.value })
            .clip(CircleShape)
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, size = size * 0.46f, color = tint ?: if (hue != null) p.onFill else p.ink, weight = 2.1f) }
}

/** A choice. Unselected it is a white pill; selected it turns sun-yellow, which is the colour of "this one". */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon? = null,
    iconTint: Color? = null,
) {
    val p = P
    val (source, press) = rememberPress()
    val shape = RoundedCornerShape(50)
    val fg = if (selected) p.onFill else p.ink
    Row(
        modifier
            .let {
                if (selected) it.raised(shape, p.sun.fill, p.sun.lip, depth = 2.dp, sink = { press.value })
                else it.raised(shape, p.card, p.cardLip, depth = 2.dp, border = p.line, sink = { press.value })
            }
            .clip(shape)
            .semantics { role = Role.Button }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ico(icon, size = 17.dp, color = iconTint ?: fg, weight = 2.1f)
        EbText(text, Ink.Chip, color = fg, maxLines = 1)
    }
}

/**
 * The language control: a small pill that says which language is on and opens the list when tapped.
 * It is a chip, not a toggle, so it keeps working however many languages the list grows to.
 */
@Composable
fun LanguageChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = P
    val (source, press) = rememberPress()
    val shape = RoundedCornerShape(50)
    Row(
        modifier.raised(shape, p.card, p.cardLip, depth = 2.dp, border = p.line, sink = { press.value })
            .clip(shape)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Ico(Icon.Globe, size = 18.dp, color = p.sky.text, weight = 2.0f)
        EbText(label, Ink.BodyStrong, color = p.ink, maxLines = 1)
        Ico(Icon.ChevronDown, size = 15.dp, color = p.inkSoft, weight = 2.2f)
    }
}

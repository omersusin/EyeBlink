package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

data class DockItem(val icon: Icon, val label: String)

/**
 * Floating navigation: a white bar on a lip, with a sun-yellow pill that slides to the chosen
 * destination and stretches a little while it travels.
 */
@Composable
fun Dock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = P
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(selected.toFloat(), Motion.Snappy, label = "dock")
    val shape = RoundedCornerShape(32.dp)

    BoxWithConstraints(
        modifier.fillMaxWidth().height(70.dp).raised(shape, p.card, p.cardLip, depth = 4.dp, border = p.line),
    ) {
        val itemW = maxWidth / items.size
        val travelling = abs(pos - pos.roundToInt()) * 2f // 0 at a stop, 1 half-way between two
        val pillW = (itemW - 12.dp) * (1f + 0.28f * travelling)
        Box(
            Modifier.offset(x = itemW * (pos + 0.5f) - pillW / 2f, y = 6.dp).width(pillW).height(58.dp)
                .raised(RoundedCornerShape(26.dp), p.sun.fill, p.sun.lip, depth = 2.dp),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val lit by animateFloatAsState(if (on) 1f else 0f, Motion.Soft, label = "lit")
                val fg = lerpColor(p.inkSoft, p.onFill, lit)
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .semantics { role = Role.Tab; this.selected = on }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            if (!on) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Ico(item.icon, size = 24.dp, color = fg, weight = 1.9f + 0.3f * lit)
                    EbText(
                        item.label, Ink.NumSmall, color = fg, align = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

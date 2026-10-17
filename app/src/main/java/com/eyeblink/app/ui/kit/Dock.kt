package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class DockItem(val icon: Icon, val label: String)

/**
 * Floating navigation. A short amber line - the lid seam - rides along the top edge and slides
 * to the selected destination with a spring, stretching a little as it travels.
 */
@Composable
fun Dock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(selected.toFloat(), Motion.Snappy, label = "dock")
    val shape = RoundedCornerShape(34.dp)

    BoxWithConstraints(modifier.fillMaxWidth().height(70.dp).clip(shape).background(Dusk.Ink0.copy(alpha = 0.95f)).glass(shape, strength = 1.4f)) {
        val itemW = maxWidth / items.size
        Canvas(Modifier.fillMaxSize()) {
            val iw = size.width / items.size
            val cx = iw * (pos + 0.5f)
            // stretch while between two stops
            val between = kotlin.math.abs(pos - pos.toInt() - 0.5f) * 2f // 1 at a stop, 0 mid-way
            val half = (14.dp.toPx()) * (1f + 0.7f * (1f - between))
            val y = 1.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Dusk.Amber.copy(alpha = 0f), Dusk.Amber, Dusk.Rose, Dusk.Amber, Dusk.Amber.copy(alpha = 0f)), cx - half, cx + half),
                topLeft = Offset(cx - half, y), size = Size(half * 2f, 3.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // a soft pool of warm light under the seam
            withTransform({ scale(1f, 0.5f, pivot = Offset(cx, 0f)) }) {
                drawCircle(
                    Brush.radialGradient(listOf(Dusk.Amber.copy(alpha = 0.30f), Color.Transparent), center = Offset(cx, 0f), radius = half * 2.8f),
                    radius = half * 2.8f, center = Offset(cx, 0f),
                )
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val lit by animateFloatAsState(if (on) 1f else 0f, Motion.Soft, label = "lit")
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .semantics { role = Role.Tab; this.selected = on }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            if (!on) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        }.padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Ico(item.icon, size = 24.dp, color = lerpColor(Dusk.TextFaint, Dusk.Amber, lit), weight = 1.7f + 0.2f * lit)
                    EbText(
                        item.label, Ink.Eyebrow, color = lerpColor(Dusk.TextFaint, Dusk.Amber, lit), caps = false,
                        align = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

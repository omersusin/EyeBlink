package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 24-hour clock face with the night window lit as an arc and a dot for "now". Midnight is at
 * the top, so a window that wraps past midnight reads as one unbroken arc.
 */
@Composable
fun NightDial(startMin: Int, endMin: Int, nowMin: Int, active: Boolean, center: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(236.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val dia = size.width - inset * 2
            fun angle(min: Int) = -90f + (min.mod(1440) / 1440f) * 360f

            drawArc(Color.White.copy(alpha = 0.06f), 0f, 360f, false, Offset(inset, inset), Size(dia, dia), style = Stroke(stroke))
            for (h in 0 until 24 step 3) {
                val a = Math.toRadians(angle(h * 60).toDouble())
                val r1 = size.width / 2f - 2.dp.toPx()
                val r2 = r1 - (if (h % 6 == 0) 7 else 4).dp.toPx()
                drawLine(Color.White.copy(alpha = 0.25f), Offset(size.width / 2f + cos(a).toFloat() * r1, size.height / 2f + sin(a).toFloat() * r1), Offset(size.width / 2f + cos(a).toFloat() * r2, size.height / 2f + sin(a).toFloat() * r2), strokeWidth = 1.dp.toPx())
            }
            val sweep = (((endMin - startMin).mod(1440)).toFloat() / 1440f) * 360f
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Dusk.Violet, Dusk.Rose, Dusk.Amber, Dusk.Violet), center = Offset(size.width / 2f, size.height / 2f)),
                    startAngle = angle(startMin), sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(dia, dia), style = Stroke(stroke, cap = StrokeCap.Round),
                    alpha = if (active) 1f else 0.55f,
                )
            }
            val a = Math.toRadians(angle(nowMin).toDouble())
            val r = dia / 2f
            val p = Offset(size.width / 2f + cos(a).toFloat() * r, size.height / 2f + sin(a).toFloat() * r)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), p, 16.dp.toPx()), radius = 16.dp.toPx(), center = p)
            drawCircle(Color.White, radius = 5.dp.toPx(), center = p)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

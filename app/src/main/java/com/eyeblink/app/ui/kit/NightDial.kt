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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 24-hour clock face with the night window drawn as a grape-coloured arc and a sun-yellow dot
 * for "now". Midnight is at the top, so a window that wraps past midnight reads as one unbroken arc.
 */
@Composable
fun NightDial(startMin: Int, endMin: Int, nowMin: Int, active: Boolean, center: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val p = P
    Box(modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(236.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val dia = size.width - inset * 2
            val c = Offset(size.width / 2f, size.height / 2f)
            fun angle(min: Int) = -90f + (min.mod(1440) / 1440f) * 360f

            drawArc(p.track, 0f, 360f, false, Offset(inset, inset), Size(dia, dia), style = Stroke(stroke))
            for (h in 0 until 24 step 3) {
                val a = Math.toRadians(angle(h * 60).toDouble())
                val r1 = size.width / 2f - 1.dp.toPx()
                val r2 = r1 - (if (h % 6 == 0) 6f else 3.5f).dp.toPx()
                drawLine(p.inkFaint, Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1), Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
            val sweep = (((endMin - startMin).mod(1440)).toFloat() / 1440f) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = p.grape.fill,
                    startAngle = angle(startMin), sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(dia, dia), style = Stroke(stroke, cap = StrokeCap.Round),
                    alpha = if (active) 1f else 0.7f,
                )
            }
            val a = Math.toRadians(angle(nowMin).toDouble())
            val r = dia / 2f
            val dot = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
            drawCircle(p.card, radius = 11.dp.toPx(), center = dot)
            drawCircle(p.sun.fill, radius = 8.dp.toPx(), center = dot)
            drawCircle(p.sun.lip, radius = 8.dp.toPx(), center = dot, style = Stroke(1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

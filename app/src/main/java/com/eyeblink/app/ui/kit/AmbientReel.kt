package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A calm, neutral moving picture for calibration's "watch" phase: slow pastel waves and a few
 * drifting bubbles. It is video-like - continuous, glanceable, nothing to read - without being
 * a clip that could make anyone laugh, flinch or blink on cue.
 */
@Composable
fun AmbientReel(modifier: Modifier = Modifier, height: Dp = 300.dp) {
    val p = P
    val clock by rememberClockMs()
    Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(30.dp))) {
        val w = size.width
        val h = size.height
        val t = clock / 1000f
        drawRect(p.sky.soft)
        val bands = listOf(p.sky.fill, p.mint.fill, p.sun.fill, p.grape.fill, p.coral.fill)
        for (i in 0 until 5) {
            val path = Path()
            val phase = t * (0.18f + i * 0.05f) + i * 1.7f
            val base = h * (0.18f + i * 0.17f)
            val amp = h * (0.05f + 0.018f * i)
            path.moveTo(-10f, base)
            val steps = 28
            for (s in 0..steps) {
                val x = w * s / steps
                val y = base + amp * sin(2f * PI.toFloat() * (s / steps.toFloat()) * (1.1f + 0.2f * i) + phase)
                path.lineTo(x, y)
            }
            drawPath(path, bands[i], style = Stroke(w * 0.045f, cap = StrokeCap.Round), alpha = 0.85f)
        }
        for (i in 0 until 12) {
            val px = (w * ((i * 0.137f + t * 0.011f * (1 + i % 3)) % 1f))
            val py = h * (0.1f + 0.8f * ((i * 0.331f + 0.07f * sin(t * 0.3f + i)) % 1f))
            drawCircle(p.card.copy(alpha = 0.85f), radius = (3f + (i % 4) * 1.5f).dp.toPx(), center = Offset(px, py))
        }
    }
}

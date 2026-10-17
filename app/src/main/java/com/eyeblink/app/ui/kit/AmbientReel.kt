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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A calm, neutral moving picture for calibration's "watch" phase: slow ribbons of light and a
 * few drifting motes. It is video-like - continuous, glanceable, nothing to read - without being
 * a clip that could make anyone laugh, flinch or blink on cue.
 */
@Composable
fun AmbientReel(modifier: Modifier = Modifier, height: Dp = 300.dp) {
    val clock by rememberClockMs()
    Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(30.dp))) {
        val w = size.width
        val h = size.height
        val t = clock / 1000f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF071A22), Color(0xFF0A1220), Color(0xFF1A0F18))))
        val colors = listOf(Dusk.Iris, Dusk.Amber, Dusk.Violet, Dusk.Rose, Dusk.IrisGlow)
        for (i in 0 until 5) {
            val path = Path()
            val phase = t * (0.18f + i * 0.05f) + i * 1.7f
            val base = h * (0.18f + i * 0.17f)
            val amp = h * (0.06f + 0.02f * i)
            path.moveTo(-10f, base)
            val steps = 28
            for (s in 0..steps) {
                val x = w * s / steps
                val y = base + amp * sin(2f * PI.toFloat() * (s / steps.toFloat()) * (1.1f + 0.2f * i) + phase)
                path.lineTo(x, y)
            }
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, colors[i].copy(alpha = 0.5f), Color.Transparent)), style = Stroke(w * 0.06f, cap = StrokeCap.Round), alpha = 0.55f)
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, colors[i], Color.Transparent)), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        for (i in 0 until 14) {
            val px = (w * ((i * 0.137f + t * 0.011f * (1 + i % 3)) % 1f))
            val py = h * (0.1f + 0.8f * ((i * 0.331f + 0.07f * sin(t * 0.3f + i)) % 1f))
            drawCircle(Color.White.copy(alpha = 0.10f + 0.1f * ((i % 3) / 3f)), radius = (1.5f + (i % 4)).dp.toPx(), center = Offset(px, py))
        }
    }
}

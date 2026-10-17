package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The room the whole app sits in: true black, with two very faint lights drifting in it
 * (an aqua one from the iris, a warm one from the seam) and a film grain that dithers the
 * gradients so they never band on a dark panel.
 *
 * The grain is composited with `Overlay`, which leaves pure black exactly black - so on OLED
 * the empty parts of the screen still emit nothing.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = !reduced)
    val grain = remember { grainBrush() }

    Box(modifier.fillMaxSize().background(Dusk.Void)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = clock / 1000f

            fun light(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                        center = Offset(cx, cy), radius = radius,
                    ),
                    radius = radius, center = Offset(cx, cy),
                )
            }

            light(Dusk.Iris, w * (0.12f + 0.06f * sin(t * 0.21f)), h * (0.07f + 0.03f * cos(t * 0.17f)), w * 1.05f, 0.17f * intensity)
            light(Dusk.Amber, w * (0.98f - 0.07f * cos(t * 0.19f)), h * (0.58f + 0.04f * sin(t * 0.23f)), w * 0.85f, 0.095f * intensity)
            light(Dusk.Violet, w * (0.25f + 0.05f * cos(t * 0.13f)), h * 1.02f, w * 0.95f, 0.085f * intensity)

            drawRect(brush = grain, topLeft = Offset.Zero, size = Size(w, h), alpha = 0.10f, blendMode = BlendMode.Overlay)
        }
        content()
    }
}

/** A 96x96 tile of neutral noise, generated once. Mid-grey so Overlay brightens and darkens equally. */
private fun grainBrush(): ShaderBrush {
    val side = 96
    val bitmap = ImageBitmap(side, side)
    val canvas = Canvas(bitmap)
    val rng = Random(7)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(side.toFloat(), side.toFloat())) {
        drawRect(Color(0xFF808080))
        repeat(side * side) {
            val x = it % side
            val y = it / side
            val v = 0.5f + (rng.nextFloat() - 0.5f) * 0.9f
            drawRect(Color(v, v, v, 1f), topLeft = Offset(x.toFloat(), y.toFloat()), size = Size(1f, 1f))
        }
    }
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

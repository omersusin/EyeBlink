package com.eyeblink.app.ui.eye

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import com.eyeblink.app.ui.kit.Dusk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Everything that varies from frame to frame in the hero eye. */
data class EyeLook(
    /** 0 = open, 1 = the lids have met. */
    val closure: Float = 0f,
    /** Where the eye is looking, each -1..1. */
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    /** 0..1 how "awake" the iris is; dims the light when EyeBlink is paused. */
    val glow: Float = 1f,
    /** Pupil breathing, roughly -1..1. */
    val breath: Float = 0f,
    /** Slow rotation of the iris fibres in radians. */
    val twist: Float = 0f,
)

/**
 * The EyeBlink mark as a drawing: an almond eye whose iris is lit from within and whose lids
 * meet in a line of warm light. The same art is the app icon, the hero on the home screen and
 * the onboarding illustration; only the [EyeLook] changes.
 */
object EyeArt {

    private const val KAPPA = 1.3333f

    /** Almond outline for half-width [a] with the given apex heights above and below the centre line. */
    fun almond(cx: Float, cy: Float, a: Float, top: Float, bottom: Float): Path = Path().apply {
        moveTo(cx - a, cy)
        cubicTo(cx - a * 0.46f, cy - top * KAPPA, cx + a * 0.46f, cy - top * KAPPA, cx + a, cy)
        cubicTo(cx + a * 0.46f, cy + bottom * KAPPA, cx - a * 0.46f, cy + bottom * KAPPA, cx - a, cy)
        close()
    }

    private fun topEdge(cx: Float, cy: Float, a: Float, top: Float): Path = Path().apply {
        moveTo(cx - a, cy)
        cubicTo(cx - a * 0.46f, cy - top * KAPPA, cx + a * 0.46f, cy - top * KAPPA, cx + a, cy)
    }

    private fun bottomEdge(cx: Float, cy: Float, a: Float, bottom: Float): Path = Path().apply {
        moveTo(cx - a, cy)
        cubicTo(cx - a * 0.46f, cy + bottom * KAPPA, cx + a * 0.46f, cy + bottom * KAPPA, cx + a, cy)
    }

    private val fibres: List<FloatArray> = run {
        val rnd = Random(11)
        List(54) { floatArrayOf(rnd.nextFloat() * (2f * PI.toFloat()), 0.36f + rnd.nextFloat() * 0.16f, 0.78f + rnd.nextFloat() * 0.2f, 0.06f + rnd.nextFloat() * 0.16f) }
    }

    /**
     * Draws the eye filling [bounds]. [strokeScale] is the size of one dp in pixels so line weights
     * stay constant whatever the canvas density.
     */
    fun DrawScope.drawEye(bounds: Rect, look: EyeLook, dp: Float, withLashes: Boolean = true) {
        val cx = bounds.center.x
        val cy = bounds.center.y
        val a = bounds.width / 2f
        val b = bounds.height / 2f * 0.94f
        val open = (1f - look.closure).coerceIn(0f, 1f)
        val top = b * open
        val bottom = b * 0.80f * open
        val glow = look.glow.coerceIn(0f, 1f)

        // Halo: the iris lighting the room.
        val haloAlpha = 0.34f * glow * (0.35f + 0.65f * open)
        drawCircle(
            Brush.radialGradient(
                listOf(Dusk.Iris.copy(alpha = haloAlpha), Dusk.IrisDeep.copy(alpha = haloAlpha * 0.35f), Color.Transparent),
                center = Offset(cx, cy), radius = a * 1.25f,
            ),
            radius = a * 1.25f, center = Offset(cx, cy),
        )

        if (open > 0.02f) {
            val lens = almond(cx, cy, a, top, bottom)

            // The eye's interior: deep blue-black, a touch lighter in the middle.
            drawPath(
                lens,
                Brush.radialGradient(listOf(Color(0xFF0D1722), Color(0xFF04070B)), center = Offset(cx, cy), radius = a),
            )

            clipPath(lens) {
                drawIris(cx, cy, a, b, look, glow, dp)

                // The upper lid throws a soft shadow onto the eye.
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.0f)),
                        startY = cy - top * 1.05f, endY = cy - top * 0.05f,
                    ),
                    topLeft = Offset(cx - a, cy - top * 1.1f), size = Size(a * 2f, top * 1.1f),
                )
                // ...and the lower lid a faint one.
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.0f), Color.Black.copy(alpha = 0.45f)),
                        startY = cy + bottom * 0.4f, endY = cy + bottom * 1.05f,
                    ),
                    topLeft = Offset(cx - a, cy + bottom * 0.4f), size = Size(a * 2f, bottom * 0.7f),
                )
            }

            if (withLashes && open > 0.25f) {
                val alpha = ((open - 0.25f) / 0.75f).coerceIn(0f, 1f) * 0.55f * glow
                for (i in 1..9) {
                    val t = i / 10f
                    val x = cx - a + 2f * a * t
                    // Position on the top curve and its outward normal, from the cubic's maths.
                    val u = t
                    val yTop = cy - top * KAPPA * 3f * u * (1f - u) // y of a symmetric cubic with equal-height controls
                    val dy = -top * KAPPA * 3f * (1f - 2f * u)
                    val dx = 2f * a
                    val len = 11f * dp * (0.6f + 0.4f * sin(PI.toFloat() * u))
                    val nx = dy / kotlin.math.hypot(dx, dy)
                    val ny = -dx / kotlin.math.hypot(dx, dy)
                    val sx = x
                    val sy = yTop
                    drawLine(
                        Dusk.Amber.copy(alpha = alpha),
                        Offset(sx + nx * 3f * dp, sy + ny * 3f * dp),
                        Offset(sx + nx * (3f * dp + len), sy + ny * (3f * dp + len)),
                        strokeWidth = 1.4f * dp, cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Lid margins. The top one carries the warm light; the bottom is quieter.
        val lineW = 2.6f * dp
        val seam = Brush.horizontalGradient(
            listOf(Dusk.Amber.copy(alpha = 0.10f), Dusk.Amber, Dusk.Rose, Dusk.Amber, Dusk.Amber.copy(alpha = 0.10f)),
            startX = cx - a, endX = cx + a,
        )
        val bloom = look.closure.coerceIn(0f, 1f)
        if (bloom > 0.5f) {
            // Asleep, the seam slowly breathes so the screen never feels frozen.
            val breathing = if (bloom > 0.95f) 0.82f + 0.18f * look.breath else 1f
            val g = ((bloom - 0.5f) / 0.5f).coerceIn(0f, 1f) * glow.coerceAtLeast(0.55f) * breathing
            // A soft, horizontally stretched bloom: a round radial glow squashed into a lens flare.
            withTransform({ scale(1f, 0.085f + 0.05f * g, pivot = Offset(cx, cy)) }) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Dusk.Amber.copy(alpha = 0.85f * g), Dusk.Rose.copy(alpha = 0.40f * g), Dusk.Amber.copy(alpha = 0.10f * g), Color.Transparent),
                        center = Offset(cx, cy), radius = a * 1.12f,
                    ),
                    radius = a * 1.12f, center = Offset(cx, cy),
                )
            }
        }
        drawPath(topEdge(cx, cy, a, top), seam, style = Stroke(lineW, cap = StrokeCap.Round), alpha = (0.55f + 0.45f * glow))
        drawPath(
            bottomEdge(cx, cy, a, bottom),
            Brush.horizontalGradient(listOf(Dusk.Iris.copy(alpha = 0.0f), Dusk.Iris.copy(alpha = 0.55f), Dusk.Iris.copy(alpha = 0.0f)), startX = cx - a, endX = cx + a),
            style = Stroke(lineW * 0.7f, cap = StrokeCap.Round), alpha = glow.coerceAtLeast(0.4f),
        )
    }

    private fun DrawScope.drawIris(cx: Float, cy: Float, a: Float, b: Float, look: EyeLook, glow: Float, dp: Float) {
        val r = b * 0.86f
        val ix = cx + look.gazeX * a * 0.16f
        val iy = cy + look.gazeY * b * 0.26f
        val c = Offset(ix, iy)
        val g = 0.35f + 0.65f * glow

        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Dusk.IrisGlow.copy(alpha = g),
                    0.34f to Dusk.Iris.copy(alpha = g),
                    0.72f to Dusk.IrisDeep.copy(alpha = g),
                    1.00f to Color(0xFF082A31),
                ),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )

        // Fibres: fine radial strokes that make the iris read as an organic surface.
        withTransform({ rotate(Math.toDegrees(look.twist.toDouble()).toFloat(), c) }) {
            for (f in fibres) {
                val ang = f[0]
                val x0 = c.x + cos(ang) * r * f[1]
                val y0 = c.y + sin(ang) * r * f[1]
                val x1 = c.x + cos(ang) * r * f[2]
                val y1 = c.y + sin(ang) * r * f[2]
                drawLine(Color.White.copy(alpha = f[3] * g), Offset(x0, y0), Offset(x1, y1), strokeWidth = 1.1f * dp)
            }
        }

        // A warm collarette ring: the amber of the seam echoed inside the iris.
        drawCircle(Dusk.Amber.copy(alpha = 0.30f * g), radius = r * 0.47f, center = c, style = Stroke(2.2f * dp))
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Dusk.Amber.copy(alpha = 0.22f * g), Color.Transparent), center = c, radius = r * 0.62f),
            radius = r * 0.62f, center = c,
        )

        // Limbal ring.
        drawCircle(Color(0xFF031116).copy(alpha = 0.9f), radius = r * 0.975f, center = c, style = Stroke(r * 0.09f))

        // Pupil.
        val pupil = r * (0.335f + 0.03f * look.breath)
        drawCircle(
            Brush.radialGradient(listOf(Color.Black, Color.Black, Color(0xFF041014)), center = c, radius = pupil * 1.12f),
            radius = pupil * 1.06f, center = c,
        )

        // Catchlights.
        drawCircle(Color.White.copy(alpha = 0.92f), radius = r * 0.105f, center = Offset(c.x - r * 0.33f, c.y - r * 0.36f))
        drawCircle(Color.White.copy(alpha = 0.38f), radius = r * 0.05f, center = Offset(c.x + r * 0.30f, c.y + r * 0.34f))
    }

    /** Elliptical countdown orbit around the eye: a faint track plus a bright arc with a comet head. */
    fun DrawScope.drawOrbit(bounds: Rect, progress: Float, dp: Float, glow: Float = 1f) {
        val p = progress.coerceIn(0f, 1f)
        val stroke = 2.4f * dp
        drawArc(
            Color.White.copy(alpha = 0.07f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke),
        )
        if (p <= 0.002f) return
        val sweep = 360f * p
        drawArc(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0f to Dusk.Amber.copy(alpha = 0.0f),
                    (0.80f * p).coerceAtLeast(0.01f) to Dusk.Amber.copy(alpha = 0.55f * glow),
                    p.coerceAtLeast(0.02f) to Dusk.Amber.copy(alpha = glow),
                    1f to Dusk.Amber.copy(alpha = 0.0f),
                ),
                center = bounds.center,
            ).let { it },
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // comet head
        val ang = Math.toRadians((-90.0 + sweep))
        val ex = bounds.center.x + cos(ang).toFloat() * bounds.width / 2f
        val ey = bounds.center.y + sin(ang).toFloat() * bounds.height / 2f
        drawCircle(
            Brush.radialGradient(listOf(Dusk.Amber.copy(alpha = 0.9f * glow), Color.Transparent), Offset(ex, ey), 14f * dp),
            radius = 14f * dp, center = Offset(ex, ey),
        )
        drawCircle(Color.White.copy(alpha = 0.95f * glow), radius = 2.4f * dp, center = Offset(ex, ey))
    }
}

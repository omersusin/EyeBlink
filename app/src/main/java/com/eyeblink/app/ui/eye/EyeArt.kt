package com.eyeblink.app.ui.eye

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import com.eyeblink.app.ui.kit.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Everything that varies from frame to frame in the hero eye. */
data class EyeLook(
    /** 0 = open, 1 = the lids have met. */
    val closure: Float = 0f,
    /** Where the eye is looking, each -1..1. */
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    /** 1 = bright and awake; low when EyeBlink is resting, which drains the colour from the iris. */
    val awake: Float = 1f,
    /** Pupil breathing, roughly -1..1. */
    val breath: Float = 0f,
)

/** The few colours the mascot is made of, taken from the current palette. */
class EyeColors(
    val sclera: Color,
    val shade: Color,
    val iris: Color,
    val irisRing: Color,
    val pupil: Color,
    val lid: Color,
    val lidEdge: Color,
    val lash: Color,
    val lip: Color,
    val dim: Color,
) {
    companion object {
        fun of(p: Palette) = EyeColors(
            sclera = if (p.dark) Color(0xFFF6F4FF) else Color.White,
            shade = p.sky.softLip,
            iris = p.sky.fill,
            irisRing = p.sky.lip,
            pupil = p.onFill,
            lid = p.sun.fill,
            lidEdge = p.sun.lip,
            lash = p.onFill,
            lip = p.sky.softLip,
            dim = p.inkFaint,
        )
    }
}

/**
 * The EyeBlink mark as a drawing: a round-cornered eye with a sun-yellow lid, a sky-blue iris and
 * a row of lashes. The same art is the app icon, the hero on the home screen and the onboarding
 * illustration; only the [EyeLook] changes. When it closes the lashes swing down and the seam
 * sags into a little smile, the way a happy cartoon eye shuts.
 */
object EyeArt {

    private const val K = 1.3333f

    /** How far out the curve controls sit: lower is a pointed almond, higher a rounder, friendlier eye. */
    private const val ROUND = 0.78f

    /** A lens: left corner, right corner, a top curve and a bottom curve. [sag] drops both curves' middles. */
    fun lens(cx: Float, cy: Float, a: Float, up: Float, down: Float, sag: Float = 0f, round: Float = ROUND): Path = Path().apply {
        moveTo(cx - a, cy)
        cubicTo(cx - a * round, cy - up * K + sag, cx + a * round, cy - up * K + sag, cx + a, cy)
        cubicTo(cx + a * round, cy + down * K + sag, cx - a * round, cy + down * K + sag, cx - a, cy)
        close()
    }

    private fun topCurve(cx: Float, cy: Float, a: Float, up: Float, sag: Float, round: Float = ROUND): Path = Path().apply {
        moveTo(cx - a, cy)
        cubicTo(cx - a * round, cy - up * K + sag, cx + a * round, cy - up * K + sag, cx + a, cy)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Point on the symmetric top curve at parameter [t], and its outward normal. */
    private fun topPoint(cx: Float, cy: Float, a: Float, up: Float, sag: Float, t: Float, round: Float = ROUND): Pair<Offset, Offset> {
        val x0 = cx - a; val x1 = cx - a * round; val x2 = cx + a * round; val x3 = cx + a
        val y0 = cy; val y1 = cy - up * K + sag; val y2 = y1; val y3 = cy
        val u = 1f - t
        val px = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
        val py = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
        val dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
        val dy = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
        val len = hypot(dx, dy).coerceAtLeast(1e-3f)
        return Offset(px, py) to Offset(dy / len, -dx / len)
    }

    /**
     * Draws the eye filling [bounds]. [dp] is the size of one dp in pixels so line weights stay
     * constant whatever the canvas density.
     */
    fun DrawScope.drawEye(bounds: Rect, look: EyeLook, colors: EyeColors, dp: Float, lashes: Boolean = true, lip: Boolean = true) {
        val cx = bounds.center.x
        val cy = bounds.center.y
        val a = bounds.width / 2f * 0.94f
        val b = bounds.height / 2f
        val c = look.closure.coerceIn(0f, 1f)
        val open = 1f - c

        val upOpen = b * 0.78f
        val downOpen = b * 0.66f
        val up = upOpen * open
        val down = downOpen * open
        val sag = b * 0.30f * c

        // The socket: the sun-yellow lid skin, as big as the open eye plus a rim. It never moves.
        val socket = lens(cx, cy, a * 1.08f, upOpen * 1.30f, downOpen * 1.22f)
        if (lip) translate(top = 4f * dp) { drawPath(socket, colors.lip) }
        drawPath(socket, colors.lid)

        // The opening in it: the white of the eye, narrowing as the lids close.
        val aperture = lens(cx, cy, a, up, down, sag)
        if (open > 0.015f) {
            drawPath(aperture, colors.sclera)
            clipPath(aperture) {
                // the lid throws a soft band of shade onto the eye
                drawRect(
                    colors.shade.copy(alpha = 0.55f),
                    topLeft = Offset(cx - a, cy - up * K),
                    size = androidx.compose.ui.geometry.Size(a * 2f, up * 0.62f),
                )
                drawIris(cx, cy + sag * 0.5f, a, b, look, colors, dp)
            }
        }

        // The lid margin: a soft crease while open, a firm seam once shut.
        val seamColor = lerp(colors.lidEdge, colors.lash, smooth(0.4f, 0.95f, c))
        val seamW = (2.2f + 1.6f * c) * dp
        drawPath(topCurve(cx, cy, a, up, sag), seamColor, style = Stroke(seamW, cap = StrokeCap.Round))
        if (c > 0.6f) {
            val bottom = Path().apply {
                moveTo(cx - a, cy)
                cubicTo(cx - a * ROUND, cy + down * K + sag, cx + a * ROUND, cy + down * K + sag, cx + a, cy)
            }
            drawPath(bottom, seamColor, style = Stroke(seamW, cap = StrokeCap.Round))
        }

        if (lashes) {
            val swing = smooth(0.25f, 0.95f, c)
            for (i in 0..4) {
                val t = 0.13f + i * 0.185f
                val (p0, n) = topPoint(cx, cy, a, up, sag, t)
                val side = if (t < 0.5f) -1f else 1f
                // open: the lash points away from the lid; shut: it hangs down, fanned a little outward
                val dirX = lerp(n.x, side * 0.42f, swing)
                val dirY = lerp(n.y, 0.9f, swing)
                val len = hypot(dirX, dirY).coerceAtLeast(1e-3f)
                val length = a * (0.17f + 0.07f * sin(PI.toFloat() * t))
                val sx = p0.x
                val sy = p0.y
                drawLine(
                    colors.lash,
                    Offset(sx, sy), Offset(sx + dirX / len * length, sy + dirY / len * length),
                    strokeWidth = 2.8f * dp, cap = StrokeCap.Round,
                )
            }
        }
    }

    private fun DrawScope.drawIris(cx: Float, cy: Float, a: Float, b: Float, look: EyeLook, colors: EyeColors, dp: Float) {
        val r = b * 0.60f
        val ix = cx + look.gazeX * a * 0.20f
        val iy = cy + look.gazeY * b * 0.16f
        val ctr = Offset(ix, iy)
        val awake = look.awake.coerceIn(0f, 1f)
        val iris = lerp(colors.dim, colors.iris, awake)
        val ring = lerp(colors.dim, colors.irisRing, awake)

        drawCircle(iris, radius = r, center = ctr)
        drawCircle(ring, radius = r * 0.94f, center = ctr, style = Stroke(r * 0.12f))
        val pupil = r * (0.46f + 0.03f * look.breath)
        drawCircle(colors.pupil, radius = pupil, center = ctr)
        // catchlights
        drawCircle(Color.White, radius = r * 0.2f, center = Offset(ix - r * 0.30f, iy - r * 0.34f))
        drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.085f, center = Offset(ix + r * 0.28f, iy + r * 0.3f))
    }

    /** An elliptical countdown ring around the eye: a pale track and a round-capped arc with a white bead at its head. */
    fun DrawScope.drawOrbit(bounds: Rect, progress: Float, dp: Float, track: Color, arc: Color) {
        val p = progress.coerceIn(0f, 1f)
        val stroke = 7f * dp
        drawArc(
            track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke),
        )
        if (p <= 0.002f) return
        val sweep = 360f * p
        drawArc(arc, -90f, sweep, false, bounds.topLeft, bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
        // the head of the arc
        val ang = Math.toRadians((-90.0 + sweep))
        val ex = bounds.center.x + cos(ang).toFloat() * bounds.width / 2f
        val ey = bounds.center.y + sin(ang).toFloat() * bounds.height / 2f
        drawCircle(Color.White, radius = 3.6f * dp, center = Offset(ex, ey))
    }
}

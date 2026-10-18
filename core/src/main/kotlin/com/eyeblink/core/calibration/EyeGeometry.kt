package com.eyeblink.core.calibration

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns eyelid landmark positions into a number that says how open an eye is.
 *
 * Deliberately arithmetic-only: no MediaPipe, CameraX or Android types, so the geometry can be
 * unit tested without a device or a model file. Turning MediaPipe landmarks into the arrays this
 * needs happens in the camera source.
 *
 * The measure is the *eye aspect ratio*: the average gap between the upper and lower lid, over
 * the width of the eye (corner to corner). Both are lengths inside the same eye, so the ratio does
 * not depend on how close the face is, how the head is rolled, or how tall the face is - which is
 * why it replaced a lid gap measured against face height. What it still depends on is the *person*:
 * wide eyes read about 0.30-0.38 when open, narrow or heavy-lidded ones 0.15-0.22. Nothing in the
 * engine may treat any of those as "the" open value; the blink detector measures every eye against
 * that same eye's own recent open level (see [OpenBaseline]).
 */
object EyeGeometry {

    /**
     * The aspect ratio that maps to openness 1.0. Comfortably above anything a real eye reaches
     * (about 0.40 for very wide eyes), so the 0..1 scale never clips the open end for anybody.
     */
    const val EAR_FULL_SCALE = 0.45f

    /**
     * Mean lid gap over eye width.
     *
     * @param corners x, y of the outer corner, then x, y of the inner corner of the eye
     * @param upper x, y pairs of points on the upper lid
     * @param lower x, y pairs of the points facing them on the lower lid, in the same order
     * @param xScale how much wider a unit of x is than a unit of y. Landmarks are normalised to the
     *   image size, so on a 480 x 640 picture a step in x is 0.75 of the same step in y; pass
     *   `width / height` to measure both in the same unit.
     * @return 0 when the input cannot describe an eye
     */
    fun aspectRatio(corners: FloatArray, upper: FloatArray, lower: FloatArray, xScale: Float = 1f): Float {
        if (corners.size < 4 || upper.isEmpty() || upper.size != lower.size || upper.size % 2 != 0) return 0f
        if (xScale <= 0f) return 0f
        val width = hypot((corners[2] - corners[0]) * xScale, corners[3] - corners[1])
        if (width <= MIN_WIDTH) return 0f
        val pairs = upper.size / 2
        var gap = 0f
        for (i in 0 until pairs) {
            gap += hypot((upper[2 * i] - lower[2 * i]) * xScale, upper[2 * i + 1] - lower[2 * i + 1])
        }
        val ear = gap / pairs / width
        return if (ear.isFinite()) ear else 0f
    }

    /** The aspect ratio on the 0..1 scale the rest of the engine works with. */
    fun openness(ear: Float): Float = if (ear.isFinite()) (ear / EAR_FULL_SCALE).coerceIn(0f, 1f) else 0f

    /** Face height from the forehead-top (10) and chin-bottom (152) landmarks. */
    fun faceHeight(foreheadY: Float, chinY: Float): Float = abs(chinY - foreheadY)

    private const val MIN_WIDTH = 1e-6f
}

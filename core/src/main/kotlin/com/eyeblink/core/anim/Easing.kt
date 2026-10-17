package com.eyeblink.core.anim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Easing curves available for the closing and opening phases of a blink.
 *
 * Pure arithmetic with no Android or Compose types, so the overlay `View`, the
 * Compose preview and the unit tests all evaluate exactly the same curve.
 *
 * Every curve maps 0 -> 0 and 1 -> 1. [BACK_OUT] intentionally overshoots 1 on the
 * way (a slight settle), so callers must not assume the output stays inside 0..1.
 */
enum class EaseKind(val id: String) {
    LINEAR("linear"),
    SINE_IN_OUT("sine"),
    QUAD_OUT("quad-out"),
    CUBIC_IN("cubic-in"),
    CUBIC_OUT("cubic-out"),
    CUBIC_IN_OUT("cubic"),
    EXPO_OUT("expo-out"),
    BACK_OUT("back-out"),

    /** Closing lid: quick start, softens as the margins meet. */
    NATURAL_CLOSE("natural-close"),

    /** Opening lid: releases fast and takes its time to settle. */
    NATURAL_OPEN("natural-open");

    fun at(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> x
            SINE_IN_OUT -> (-(cos(PI * x) - 1.0) / 2.0).toFloat()
            QUAD_OUT -> 1f - (1f - x) * (1f - x)
            CUBIC_IN -> x * x * x
            CUBIC_OUT -> 1f - (1f - x).pow(3)
            CUBIC_IN_OUT -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            EXPO_OUT -> if (x >= 1f) 1f else 1f - 2.0.pow(-10.0 * x).toFloat()
            BACK_OUT -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
            }
            NATURAL_CLOSE -> NATURAL_CLOSE_CURVE.at(x)
            NATURAL_OPEN -> NATURAL_OPEN_CURVE.at(x)
        }
    }

    companion object {
        private val NATURAL_CLOSE_CURVE = CubicBezier(0.36f, 0.0f, 0.24f, 1.0f)
        private val NATURAL_OPEN_CURVE = CubicBezier(0.16f, 0.0f, 0.18f, 1.0f)

        fun fromId(id: String?): EaseKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * CSS-style `cubic-bezier(x1, y1, x2, y2)` easing. Solves x(t) = input for t with a few
 * Newton steps and falls back to bisection when the slope is too flat to trust.
 */
class CubicBezier(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) {
    init {
        require(x1 in 0f..1f && x2 in 0f..1f) { "x control points must be within 0..1" }
    }

    // Polynomial coefficients of the parametric curve (start (0,0), end (1,1)).
    private val cx = 3f * x1
    private val bx = 3f * (x2 - x1) - cx
    private val ax = 1f - cx - bx
    private val cy = 3f * y1
    private val by = 3f * (y2 - y1) - cy
    private val ay = 1f - cy - by

    private fun sampleX(t: Float) = ((ax * t + bx) * t + cx) * t
    private fun sampleY(t: Float) = ((ay * t + by) * t + cy) * t
    private fun slopeX(t: Float) = (3f * ax * t + 2f * bx) * t + cx

    fun at(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f

        var t = x
        repeat(NEWTON_STEPS) {
            val err = sampleX(t) - x
            if (abs(err) < EPSILON) return sampleY(t)
            val slope = slopeX(t)
            if (abs(slope) < 1e-6f) return@repeat
            t -= err / slope
        }

        var lo = 0f
        var hi = 1f
        t = x
        repeat(BISECTION_STEPS) {
            val v = sampleX(t)
            if (abs(v - x) < EPSILON) return sampleY(t)
            if (x > v) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return sampleY(t)
    }

    private companion object {
        const val NEWTON_STEPS = 6
        const val BISECTION_STEPS = 24
        const val EPSILON = 1e-5f
    }
}

/** Hermite smoothstep, used for gentle alpha ramps. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

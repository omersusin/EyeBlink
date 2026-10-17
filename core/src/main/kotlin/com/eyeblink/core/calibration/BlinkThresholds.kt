package com.eyeblink.core.calibration

import kotlinx.serialization.Serializable

/**
 * Per-user detector thresholds, fitted to how far this person's eyes close.
 *
 * Every number is a share of the person's *own* open level (see [BlinkDetector]), so none of them says
 * anything about how wide the eyes are: a person whose lids only ever close by half of their open level
 * gets a lower [start] than one whose lids close completely, and both are judged against themselves.
 *
 * Older builds stored thresholds on a different scale under other names (`close`, `reopen`, `complete`).
 * Those keys are not read any more, so an old file decodes to the defaults rather than to numbers that
 * would now mean something else.
 */
@Serializable
data class BlinkThresholds(
    val start: Float = BlinkDetector.DEFAULT_START,
    val end: Float = BlinkDetector.DEFAULT_END,
    val full: Float = BlinkDetector.DEFAULT_FULL,
    /** True once these were fitted to this person's own blinks; false while they are the population defaults. */
    val fitted: Boolean = false,
    /** Typical openness (0..1 scale) of this person's open eyes, to start a session without a warm-up. 0 = unknown. */
    val openLevel: Float = 0f,
) {
    /** The detector's settings for these thresholds; [sensitivity] below 1 makes it quicker to see a blink, above 1 slower. */
    fun settings(sensitivity: Float = 1f): DetectorSettings {
        val t = sanitised()
        return DetectorSettings(start = t.start, end = t.end, full = t.full).scaled(sensitivity)
    }

    fun newDetector(sensitivity: Float = 1f): BlinkDetector {
        val t = sanitised()
        return BlinkDetector(t.settings(sensitivity)).also { if (t.openLevel > 0f) it.seed(t.openLevel) }
    }

    /** These thresholds if they make sense, the defaults if a damaged or hand-edited file made them nonsense. */
    fun sanitised(): BlinkThresholds {
        val sensible = start in MIN_START..MAX_START && end in MIN_END..(start * 0.9f) && full in start..MAX_FULL
        return if (sensible) this else BlinkThresholds()
    }

    companion object {
        const val MIN_START = 0.12f
        const val MAX_START = 0.60f
        const val MIN_END = 0.04f
        const val MAX_FULL = 0.95f

        /** A deliberate blink must close the eye by at least this much for it to say anything about how it closes. */
        const val MIN_USABLE_DEPTH = 0.25f

        /**
         * @param openLevel typical openness with the eyes open (e.g. a high percentile)
         * @param fullDepth how far this person's deliberate, full blinks closed their eyes, as a share of their open level
         * @return thresholds fitted to that, or the defaults when the data cannot support them
         */
        fun fromCalibration(openLevel: Float?, fullDepth: Float?): BlinkThresholds {
            val seed = openLevel?.takeIf { it.isFinite() && it > 0f } ?: 0f
            if (fullDepth == null || !fullDepth.isFinite() || fullDepth < MIN_USABLE_DEPTH) return BlinkThresholds(openLevel = seed)

            // Half way to this person's shut eye starts a blink, a quarter of the way back ends it, and four fifths of
            // the way there is what makes it a full one.
            val start = (0.50f * fullDepth).coerceIn(0.18f, 0.45f)
            val end = (0.45f * start).coerceAtLeast(MIN_END)
            val full = (0.80f * fullDepth).coerceIn(start + 0.10f, MAX_FULL)
            return BlinkThresholds(start = start, end = end, full = full, fitted = true, openLevel = seed)
        }
    }
}

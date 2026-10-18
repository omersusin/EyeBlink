package com.eyeblink.core.calibration

import kotlinx.serialization.Serializable

/**
 * Per-user detector thresholds, fitted to how far this person's eyes close.
 *
 * Every number is a share of the person's *own* open level (see [BlinkDetector]), so none of them says
 * anything about how wide the eyes are: a person whose lids only ever close by half of their open level
 * gets a lower [start] than one whose lids close completely, and both are judged against themselves.
 *
 * They are fitted to what the detector saw in the deliberate blink test, which includes the face model's own
 * blink score where there is one. A detector that uses them looks at the score as well ([settings]): a lid that
 * moves by a quarter of its open level shows a blink only to the score, and a tracker that looked at the lid alone
 * would be asked to find blinks smaller than the number it is told to look for.
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
        return DetectorSettings(start = t.start, end = t.end, full = t.full, useBlinkScore = t.fitted).scaled(sensitivity)
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
         * How low a fitted start may go. The open level is the high end of what an eye does, so noise alone reaches a good
         * way above zero closure; a start any lower than this is a counter of the tracker's jitter. It gives way only where
         * the person's own blinks are so shallow that nothing higher would see them.
         */
        private const val FLOOR_START = 0.24f
        private const val SHALLOWEST_START = 0.18f

        /**
         * @param openLevel typical openness with the eyes open (e.g. a high percentile)
         * @param fullDepth how far this person's deliberate, full blinks closed their eyes, as a share of their open level
         * @return thresholds fitted to that, or the defaults when the data cannot support them
         */
        fun fromCalibration(openLevel: Float?, fullDepth: Float?): BlinkThresholds {
            val seed = openLevel?.takeIf { it.isFinite() && it > 0f } ?: 0f
            if (fullDepth == null || !fullDepth.isFinite() || fullDepth < MIN_USABLE_DEPTH) return BlinkThresholds(openLevel = seed)

            // Half way to this person's shut eye starts a blink, and a bit under half of that is where it ends. It never
            // asks for more than the population default does (an eye that closes completely has no use for a start above
            // it) and never for less than the noise floor, unless the blinks themselves are so shallow that nothing else would see them.
            // The end is that low only for a low start: the jitter of an open eye does not depend on how deep a blink goes.
            val floor = (0.60f * fullDepth).coerceIn(SHALLOWEST_START, FLOOR_START)
            val start = (0.50f * fullDepth).coerceIn(floor, BlinkDetector.DEFAULT_START)
            val end = (0.45f * start).coerceIn(MIN_END, BlinkDetector.DEFAULT_END)
            val full = (0.80f * fullDepth).coerceIn(start + 0.10f, MAX_FULL)
            return BlinkThresholds(start = start, end = end, full = full, fitted = true, openLevel = seed)
        }
    }
}

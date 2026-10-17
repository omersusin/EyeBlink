package com.eyeblink.core.calibration

import kotlinx.serialization.Serializable

/**
 * Per-user detector thresholds, derived from calibration so that a person whose open eyes
 * read 0.8 and whose closed eyes read 0.2 is judged against their own range, not a
 * population default.
 */
@Serializable
data class BlinkThresholds(
    val close: Float = BlinkDetector.DEFAULT_CLOSE_THRESHOLD,
    val reopen: Float = BlinkDetector.DEFAULT_REOPEN_THRESHOLD,
    val complete: Float = BlinkDetector.DEFAULT_COMPLETE_THRESHOLD,
    val personalised: Boolean = false,
) {
    fun newDetector() = BlinkDetector(
        closeThreshold = close,
        reopenThreshold = reopen,
        completeThreshold = complete,
    )

    companion object {
        /** Open-vs-closed gap below which the calibration data is too weak to personalise. */
        const val MIN_USABLE_GAP = 0.30f

        /**
         * @param openLevel typical openness with the eyes open (e.g. a high percentile)
         * @param closedLevel typical *minimum* openness reached by deliberate full blinks
         * @return personalised thresholds, or the defaults when the data cannot support them
         */
        fun fromCalibration(openLevel: Float, closedLevel: Float): BlinkThresholds {
            val gap = openLevel - closedLevel
            if (openLevel.isNaN() || closedLevel.isNaN() || gap < MIN_USABLE_GAP) return BlinkThresholds()

            val close = (openLevel - 0.65f * gap).coerceIn(0.10f, 0.50f)
            val reopen = (openLevel - 0.35f * gap).coerceIn(close + 0.10f, 0.85f)
            val complete = (closedLevel + 0.25f * gap).coerceIn(0.05f, close - 0.05f)
            return BlinkThresholds(close, reopen, complete, personalised = true)
        }
    }
}

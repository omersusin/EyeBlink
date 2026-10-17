package com.eyeblink.core.calibration

/**
 * One observation of eye openness, derived from a single frame.
 *
 * The openness values are on a fixed 0..1 scale built from the eye aspect ratio (see [EyeGeometry]); they
 * are *not* "1 = this person's open eye". How open the eye is for this person is learned from the stream
 * itself, by the detector. [confidence] and [illuminance] gate whether an observation is trustworthy
 * enough to learn from - bad lighting and low-confidence face tracking must not
 * pollute the personal baseline.
 *
 * [blinkScore] is the face model's own estimate that the eyes are shut (0 = open, 1 = shut), when the
 * platform has one. It is a second opinion that does not depend on the shape of the eye.
 */
data class EyeOpennessSample(
    val timestampMs: Long,
    val leftEyeOpenness: Float,
    val rightEyeOpenness: Float,
    val confidence: Float,
    val illuminance: Float,
    val blinkScore: Float? = null,
) {
    /** The less-open eye drives the value, so a partial blink still registers. */
    val openness: Float get() = minOf(leftEyeOpenness, rightEyeOpenness)

    val isTrackable: Boolean
        get() = confidence >= MIN_CONFIDENCE && illuminance >= MIN_ILLUMINANCE &&
            leftEyeOpenness in 0f..1f && rightEyeOpenness in 0f..1f

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        /** Frame brightness on a 0..255 luma scale; darker frames are not trustworthy. */
        const val MIN_ILLUMINANCE = 30f
    }
}

/**
 * A detected blink: when it closed, how long it stayed shut, and whether it
 * was a complete closure.
 *
 * [instructed] marks blinks the user performed on request during calibration
 * Phase C. Those are excluded from normal-behaviour learning, per spec.
 */
data class BlinkObservation(
    val startMs: Long,
    val durationMs: Long,
    val complete: Boolean,
    val confidence: Float,
    val instructed: Boolean = false,
    /** Lowest openness reached during the closure, 0 = shut tight. */
    val minOpenness: Float = 0f,
    /**
     * How far the eyes closed, as a share of this person's own open level: 0 = not at all, 1 = shut. Narrow
     * eyes cannot close as far in these terms as wide ones, which is why thresholds are fitted to it
     * rather than set to a number. 0 when the blink was not measured this way.
     */
    val depth: Float = 0f,
) {
    /** Good enough to learn from: trustworthy, natural, and a real closure. */
    val isLearnable: Boolean
        get() = !instructed && confidence >= EyeOpennessSample.MIN_CONFIDENCE && durationMs > 0
}
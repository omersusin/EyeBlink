package com.eyeblink.core.calibration

/**
 * One observation of eye openness, derived from a single frame.
 *
 * [eyeOpenness] is normalised per eye in 0..1 (1 = fully open, 0 = closed).
 * [confidence] and [illuminance] gate whether an observation is trustworthy
 * enough to learn from - bad lighting and low-confidence face tracking must not
 * pollute the personal baseline.
 */
data class EyeOpennessSample(
    val timestampMs: Long,
    val leftEyeOpenness: Float,
    val rightEyeOpenness: Float,
    val confidence: Float,
    val illuminance: Float,
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
    /** Lowest openness reached during the closure, 0 = shut tight. Used to personalise thresholds. */
    val minOpenness: Float = 0f,
) {
    /** Good enough to learn from: trustworthy, natural, and a real closure. */
    val isLearnable: Boolean
        get() = !instructed && confidence >= EyeOpennessSample.MIN_CONFIDENCE && durationMs > 0
}
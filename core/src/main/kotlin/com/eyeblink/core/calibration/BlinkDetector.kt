package com.eyeblink.core.calibration

/**
 * Turns a stream of [EyeOpennessSample]s into [BlinkObservation]s.
 *
 * Uses hysteresis: an eye must fall below [closeThreshold] to start a blink
 * and rise back above [reopenThreshold] to end one. Without that gap, tracker
 * jitter around a single threshold would emit a burst of phantom blinks.
 *
 * This class holds no Android or MediaPipe types on purpose - the detection
 * rules are testable without a camera. Frame -> [EyeOpennessSample] conversion
 * lives in a separate source so it can be swapped (or faked) independently.
 */
class BlinkDetector(
    private val closeThreshold: Float = DEFAULT_CLOSE_THRESHOLD,
    private val reopenThreshold: Float = DEFAULT_REOPEN_THRESHOLD,
    private val completeThreshold: Float = DEFAULT_COMPLETE_THRESHOLD,
    private val minDurationMs: Long = DEFAULT_MIN_DURATION_MS,
    private val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {
    private var closedAtMs: Long? = null
    private var confidenceAtClose: Float = 0f
    private var minOpenness: Float = 1f

    /** True while a closure is in progress. */
    val isClosed: Boolean get() = closedAtMs != null

    /**
     * Feed one sample. Returns a [BlinkObservation] on the frame where the eye
     * reopens, or null otherwise.
     *
     * Untrackable samples are ignored: they neither open nor close a blink, so
     * a lighting dip cannot fabricate a closure or split a real one in half.
     */
    fun onSample(sample: EyeOpennessSample): BlinkObservation? {
        if (!sample.isTrackable) return null

        val openness = sample.openness
        val closedSince = closedAtMs

        if (closedSince == null) {
            if (openness <= closeThreshold) {
                closedAtMs = sample.timestampMs
                confidenceAtClose = sample.confidence
                minOpenness = openness
            }
            return null
        }

        if (openness < reopenThreshold) {
            // Still shut; remember how far it went so we can judge completeness.
            if (openness < minOpenness) minOpenness = openness
            return null
        }

        // Reopened: close the blink.
        val duration = sample.timestampMs - closedSince
        val reached = minOpenness
        closedAtMs = null

        // A closure longer than any real blink is a tracking dropout, not a blink.
        if (duration < minDurationMs || duration > maxDurationMs) return null

        return BlinkObservation(
            startMs = closedSince,
            durationMs = duration,
            complete = reached <= completeThreshold,
            confidence = confidenceAtClose,
            minOpenness = reached,
        )
    }

    /** Discards any in-progress closure, e.g. when tracking is lost or a phase ends. */
    fun reset() {
        closedAtMs = null
        confidenceAtClose = 0f
        minOpenness = 1f
    }

    companion object {
        const val DEFAULT_CLOSE_THRESHOLD = 0.30f
        const val DEFAULT_REOPEN_THRESHOLD = 0.55f
        /** Reaching near-zero openness is what separates a full blink from a partial one. */
        const val DEFAULT_COMPLETE_THRESHOLD = 0.12f
        const val DEFAULT_MIN_DURATION_MS = 40L
        const val DEFAULT_MAX_DURATION_MS = 600L
    }
}
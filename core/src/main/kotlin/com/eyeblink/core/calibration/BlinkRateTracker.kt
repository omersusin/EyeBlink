package com.eyeblink.core.calibration

/**
 * What the camera has actually seen recently. Only *trackable* time counts: a minute in
 * which the face was visible for ten seconds is a ten-second observation, not a minute.
 */
data class ObservedRate(
    val blinksPerMinute: Float,
    val trackedMs: Long,
    val blinks: Int,
    /** Trackable time since the last blink. Not wall-clock time: frames without a face don't count. */
    val trackedSinceBlinkMs: Long,
    val meanConfidence: Float,
) {
    val reliable: Boolean get() = trackedMs >= MIN_TRACKED_MS && meanConfidence >= MIN_CONFIDENCE

    companion object {
        const val MIN_TRACKED_MS = 15_000L
        const val MIN_CONFIDENCE = 0.6f
        val None = ObservedRate(0f, 0L, 0, 0L, 0f)
    }
}

/**
 * Sliding-window blink rate from camera observations.
 *
 * Feed it every trackable frame via [onTrackedFrame] and every detected blink via [onBlink].
 * Instructed (calibration) blinks are rejected on purpose: they would inflate the rate.
 */
class BlinkRateTracker(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private class Slice(val atMs: Long, val dtMs: Long, val confidence: Float)

    private val slices = ArrayDeque<Slice>()
    private val blinkTimes = ArrayDeque<Long>()
    private var lastFrameMs: Long? = null
    private var trackedSinceBlink = 0L

    fun onTrackedFrame(timestampMs: Long, confidence: Float) {
        val prev = lastFrameMs
        lastFrameMs = timestampMs
        if (prev == null) return
        val dt = timestampMs - prev
        // A long gap means tracking was lost in between; don't credit that time.
        if (dt <= 0 || dt > MAX_FRAME_GAP_MS) return
        slices.addLast(Slice(timestampMs, dt, confidence))
        trackedSinceBlink += dt
        trim(timestampMs)
    }

    /** Tracking dropped (no face, too dark): the next frame starts a fresh segment. */
    fun onTrackingLost() {
        lastFrameMs = null
    }

    fun onBlink(blink: BlinkObservation) {
        if (blink.instructed) return
        blinkTimes.addLast(blink.startMs)
        trackedSinceBlink = 0L
    }

    fun observed(nowMs: Long): ObservedRate {
        trim(nowMs)
        val tracked = slices.sumOf { it.dtMs }
        if (tracked <= 0) return ObservedRate.None.copy(blinks = blinkTimes.size)
        val conf = slices.sumOf { it.confidence.toDouble() * it.dtMs } / tracked
        return ObservedRate(
            blinksPerMinute = blinkTimes.size * 60_000f / tracked,
            trackedMs = tracked,
            blinks = blinkTimes.size,
            trackedSinceBlinkMs = trackedSinceBlink,
            meanConfidence = conf.toFloat(),
        )
    }

    fun reset() {
        slices.clear()
        blinkTimes.clear()
        lastFrameMs = null
        trackedSinceBlink = 0L
    }

    private fun trim(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (slices.isNotEmpty() && slices.first().atMs < cutoff) slices.removeFirst()
        while (blinkTimes.isNotEmpty() && blinkTimes.first() < cutoff) blinkTimes.removeFirst()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 120_000L
        const val MAX_FRAME_GAP_MS = 400L
    }
}

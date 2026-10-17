package com.eyeblink.core.calibration

/**
 * One camera frame reduced to the numbers the pipeline needs. The platform builds it from the
 * face model's output; no image ever reaches this module.
 */
data class FaceFrame(
    val timestampMs: Long,
    /** Null when no face was found in the frame. */
    val sample: EyeOpennessSample?,
    val quality: QualityReport,
    /** Mean brightness of the frame, 0..255. */
    val luma: Float,
)

/** What one frame told us. */
data class FrameEvents(
    /** A blink that ended on this frame. */
    val blink: BlinkObservation?,
    /** The frame was good enough to count as evidence. */
    val trackable: Boolean,
)

/**
 * Everything between "a frame arrived" and "the schedule may change": blink detection with the
 * user's own thresholds, the sliding blink rate, and the windows handed to the adaptive learner.
 *
 * Only trackable frames count as time. A dark room, a turned head or a hand over the lens
 * produces no evidence rather than a blink rate of zero, which is what keeps a camera that
 * cannot see from ever being mistaken for one that saw you stare.
 */
class TrackingPipeline(thresholds: BlinkThresholds = BlinkThresholds(), sensitivity: Float = 1f) {

    private var detector: BlinkDetector = thresholds.newDetector(sensitivity)
    private var sensitivity: Float = sensitivity

    /** How closed the eyes were on the last frame, as a share of their own open level; for diagnostics. */
    val closure: Float get() = detector.closure

    val rate = BlinkRateTracker()

    private var lastTrackableMs: Long? = null
    private var lastQualityScore: Float? = null

    // The window being collected for the learner.
    private val windowBlinks = ArrayList<BlinkObservation>()
    private var windowTrackedMs = 0L
    private var windowConfidence = 0.0
    private var windowLuma = 0.0

    /**
     * Switches to freshly calibrated thresholds (and, if given, a new sensitivity); an eye that is mid-blink is
     * forgotten, and what "open" looks like is learned again from the next frames.
     */
    fun setThresholds(t: BlinkThresholds, sensitivity: Float = this.sensitivity) {
        this.sensitivity = sensitivity
        detector = t.newDetector(sensitivity)
    }

    fun onFrame(frame: FaceFrame): FrameEvents {
        val s = frame.sample
        if (s == null || !s.isTrackable) {
            lose()
            return FrameEvents(null, trackable = false)
        }

        val prev = lastTrackableMs
        if (prev != null && frame.timestampMs - prev > BlinkRateTracker.MAX_FRAME_GAP_MS) {
            // Tracking was away for a while: whatever closure was in progress is unknowable.
            detector.reset()
            rate.onTrackingLost()
        }
        lastTrackableMs = frame.timestampMs
        lastQualityScore = s.confidence

        rate.onTrackedFrame(frame.timestampMs, s.confidence)
        if (prev != null) {
            val dt = frame.timestampMs - prev
            if (dt in 1..BlinkRateTracker.MAX_FRAME_GAP_MS) {
                windowTrackedMs += dt
                windowConfidence += s.confidence.toDouble() * dt
                windowLuma += frame.luma.toDouble() * dt
            }
        }

        val blink = detector.onSample(s)
        if (blink != null) {
            rate.onBlink(blink)
            if (blink.isLearnable) windowBlinks += blink
        }
        return FrameEvents(blink, trackable = true)
    }

    /** Tracking quality of the latest trackable frame, or null if there has been none lately. */
    fun confidenceAt(nowMs: Long, maxAgeMs: Long = RECENT_MS): Float? {
        val at = lastTrackableMs ?: return null
        return if (nowMs - at <= maxAgeMs) lastQualityScore else null
    }

    /**
     * Hands over the window collected so far if it holds enough evidence to be worth offering to
     * the learner, and starts a new one. With [force] (a context change, the camera stopping) a
     * shorter window is still handed over, but one too short for the learner to use is dropped.
     */
    fun takeLearningWindow(context: ContextProfile, force: Boolean = false): LearningWindow? {
        val enough = windowTrackedMs >= if (force) AdaptiveLearner.MIN_TRACKED_MS else WINDOW_TARGET_MS
        if (!enough) {
            if (force) clearWindow()
            return null
        }
        val tracked = windowTrackedMs
        val window = LearningWindow(
            context = context,
            blinks = windowBlinks.toList(),
            trackedMs = tracked,
            meanConfidence = (windowConfidence / tracked).toFloat(),
            meanLuma = (windowLuma / tracked).toFloat(),
        )
        clearWindow()
        return window
    }

    fun reset() {
        detector.reset()
        rate.reset()
        lastTrackableMs = null
        lastQualityScore = null
        clearWindow()
    }

    private fun lose() {
        detector.reset()
        rate.onTrackingLost()
        lastTrackableMs = null
    }

    private fun clearWindow() {
        windowBlinks.clear()
        windowTrackedMs = 0
        windowConfidence = 0.0
        windowLuma = 0.0
    }

    companion object {
        /** How much trackable time a window gathers before it is offered to the learner. */
        const val WINDOW_TARGET_MS = 60_000L

        /** A confidence reading older than this is stale: the camera may have lost the face since. */
        const val RECENT_MS = 3_000L
    }
}

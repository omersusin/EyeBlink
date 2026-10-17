package com.eyeblink.app.calibration

/**
 * Orchestrates the three-phase guided calibration.
 *
 * Phase A (video) and Phase B (reading) collect *spontaneous* blinks. Phase C
 * collects *instructed* blinks, which the user performs on request and which
 * must never pollute normal-behaviour learning. Keeping those separate is a
 * spec requirement, and it is enforced here by tagging rather than by
 * convention.
 *
 * Contains no Android types, so the phase logic is unit testable.
 */
class CalibrationSession(
    private val videoMs: Long = DEFAULT_VIDEO_MS,
    private val readingMs: Long = DEFAULT_READING_MS,
    private val deliberateBlinksRequired: Int = DEFAULT_DELIBERATE_BLINKS,
) {
    enum class Phase { VIDEO, READING, DELIBERATE, COMPLETE }

    var phase: Phase = Phase.VIDEO
        private set

    private var phaseStartedMs: Long = 0L
    private var deliberateSeen: Int = 0

    val spontaneous: MutableList<BlinkObservation> = mutableListOf()
    val instructed: MutableList<BlinkObservation> = mutableListOf()

    /** Blinks still needed in Phase C. */
    val deliberateRemaining: Int get() = (deliberateBlinksRequired - deliberateSeen).coerceAtLeast(0)

    /** 0..1 progress through the current phase, for a progress indicator. */
    fun phaseProgress(nowMs: Long): Float {
        val total = phaseDuration(phase)
        if (total <= 0L) return 1f
        val elapsed = (nowMs - phaseStartedMs).coerceIn(0L, total)
        return elapsed.toFloat() / total
    }

    /** Remaining time in the current timed phase, or null for Phase C. */
    fun remainingMs(nowMs: Long): Long? {
        val total = phaseDuration(phase)
        if (total <= 0L) return null
        return (total - (nowMs - phaseStartedMs)).coerceAtLeast(0L)
    }

    fun start(nowMs: Long) {
        phase = Phase.VIDEO
        phaseStartedMs = nowMs
        deliberateSeen = 0
    }

    /**
     * Advances timed phases. Phase C ends on blink count, not a timer.
     * @return true if the phase changed.
     */
    fun onTick(nowMs: Long): Boolean {
        when (phase) {
            Phase.VIDEO -> if (nowMs - phaseStartedMs >= videoMs) {
                phase = Phase.READING
                phaseStartedMs = nowMs
                return true
            }
            Phase.READING -> if (nowMs - phaseStartedMs >= readingMs) {
                phase = Phase.DELIBERATE
                phaseStartedMs = nowMs
                return true
            }
            Phase.DELIBERATE, Phase.COMPLETE -> Unit
        }
        return false
    }

    /**
     * Records a blink into the current phase.
     * @return true if the blink was accepted for learning.
     */
    fun onBlink(blink: BlinkObservation): Boolean {
        if (phase == Phase.COMPLETE) return false
        if (phase == Phase.DELIBERATE) {
            if (blink.isLearnable.not() && blink.confidence < EyeOpennessSample.MIN_CONFIDENCE) {
                return false // too unreliable to count as a requested blink
            }
            instructed += blink.copy(instructed = true)
            deliberateSeen++
            if (deliberateSeen >= deliberateBlinksRequired) {
                phase = Phase.COMPLETE
            }
            return true
        }
        if (!blink.isLearnable) return false
        spontaneous += blink
        return true
    }

    /**
     * Builds the baseline from the spontaneous phases only, then reports how
     * much signal was really collected so the UI can be honest about it.
     */
    fun result(): CalibrationResult {
        val video = BlinkStatistics.summarise(spontaneous.filter { it.startMs < phaseStartedMs })
        // Everything collected before the deliberate phase began is spontaneous.
        val spontaneousOnly = BlinkStatistics.summarise(spontaneous)
        return CalibrationResult(
            baseline = spontaneousOnly,
            spontaneousCount = spontaneousOnly.sampleCount,
            instructedCount = instructed.size,
            complete = phase == Phase.COMPLETE,
            completeRatio = video.completeBlinkRatio,
        )
    }

    /** Lets the user skip or reset at any point. */
    fun cancel() {
        phase = Phase.COMPLETE
    }

    private fun phaseDuration(phase: Phase): Long = when (phase) {
        Phase.VIDEO -> videoMs
        Phase.READING -> readingMs
        Phase.DELIBERATE -> 0L
        Phase.COMPLETE -> 0L
    }

    companion object {
        const val DEFAULT_VIDEO_MS = 60_000L
        const val DEFAULT_READING_MS = 60_000L
        const val DEFAULT_DELIBERATE_BLINKS = 5
    }
}

data class CalibrationResult(
    val baseline: BlinkBaseline,
    val spontaneousCount: Int,
    val instructedCount: Int,
    val complete: Boolean,
    val completeRatio: Float,
) {
    /**
     * Phases A and B are what establish a baseline. Without enough of them the
     * result is reported as insufficient rather than silently applied.
     */
    val hasEnoughSpontaneous: Boolean
        get() = baseline.hasSignal
}
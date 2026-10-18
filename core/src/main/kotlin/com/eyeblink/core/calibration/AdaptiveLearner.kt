package com.eyeblink.core.calibration

import kotlin.math.abs

/** A window of reliable camera observation offered to the learner. */
data class LearningWindow(
    val context: ContextProfile,
    val blinks: List<BlinkObservation>,
    val trackedMs: Long,
    val meanConfidence: Float,
    val meanLuma: Float,
)

enum class LearnVerdict { ACCEPTED, TOO_SHORT, TOO_FEW_BLINKS, LOW_CONFIDENCE, TOO_DARK, NO_NATURAL_BLINKS }

data class LearnOutcome(val verdict: LearnVerdict, val profile: PersonalProfile, val weight: Float = 0f)

/**
 * Gradual personalisation: folds reliable new observations into the personal model.
 *
 * Updates are deliberately timid. A window must be long enough, contain enough natural
 * blinks, and have been tracked with good confidence in good light, and even then it only
 * moves the baseline part of the way and never more than [MAX_STEP] of its current value in
 * one go. Instructed calibration blinks never count.
 */
object AdaptiveLearner {

    const val MIN_TRACKED_MS = 20_000L
    const val MIN_BLINKS = 4
    const val MIN_CONFIDENCE = 0.7f
    const val MAX_WEIGHT = 0.15f

    /** A single update may not shift any baseline value by more than this fraction. */
    const val MAX_STEP = 0.20f

    /** Pseudo-count: how many existing samples it takes before new data is at most half-weighted. */
    private const val PRIOR_STRENGTH = 30

    fun learn(profile: PersonalProfile, window: LearningWindow): LearnOutcome {
        val natural = window.blinks.filter { !it.instructed && it.isLearnable }
        val verdict = when {
            window.trackedMs < MIN_TRACKED_MS -> LearnVerdict.TOO_SHORT
            window.meanConfidence < MIN_CONFIDENCE -> LearnVerdict.LOW_CONFIDENCE
            window.meanLuma < EyeOpennessSample.MIN_ILLUMINANCE -> LearnVerdict.TOO_DARK
            window.blinks.isNotEmpty() && natural.isEmpty() -> LearnVerdict.NO_NATURAL_BLINKS
            natural.size < MIN_BLINKS -> LearnVerdict.TOO_FEW_BLINKS
            else -> LearnVerdict.ACCEPTED
        }
        if (verdict != LearnVerdict.ACCEPTED) return LearnOutcome(verdict, profile)

        val fresh = BlinkStatistics.summarise(natural)
        if (!fresh.hasSignal) return LearnOutcome(LearnVerdict.TOO_FEW_BLINKS, profile)

        val currentContext = profile.byContext[window.context] ?: BlinkBaseline()
        val updatedContext = fold(currentContext, fresh)
        val updatedGeneral = fold(profile.general, fresh)

        val next = profile.copy(general = updatedGeneral).withContext(window.context, updatedContext)
        val w = weightFor(currentContext.sampleCount, fresh.sampleCount)
        return LearnOutcome(LearnVerdict.ACCEPTED, next, w)
    }

    internal fun weightFor(existing: Int, fresh: Int): Float =
        (fresh.toFloat() / (existing + fresh + PRIOR_STRENGTH)).coerceIn(0f, MAX_WEIGHT)

    private fun fold(current: BlinkBaseline, fresh: BlinkBaseline): BlinkBaseline {
        if (!current.hasSignal) return fresh
        val w = weightFor(current.sampleCount, fresh.sampleCount)
        val merged = BlinkStatistics.merge(current, fresh, w)
        return merged.copy(
            medianIbiMs = limit(current.medianIbiMs, merged.medianIbiMs),
            blinkDurationMs = limit(current.blinkDurationMs, merged.blinkDurationMs),
        )
    }

    private fun limit(old: Long, new: Long): Long {
        if (old <= 0L) return new
        val maxDelta = (old * MAX_STEP).toLong()
        val delta = new - old
        return if (abs(delta) <= maxDelta) new else old + if (delta > 0) maxDelta else -maxDelta
    }
}

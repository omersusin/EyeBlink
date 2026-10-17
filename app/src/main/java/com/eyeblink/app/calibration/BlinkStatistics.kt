package com.eyeblink.app.calibration

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A personal baseline: what "normal" looks like for this user, measured
 * locally. Never uploaded.
 *
 * All durations in milliseconds. [sampleCount] is how many learnable
 * observations backed this baseline, so the UI can be honest about confidence
 * rather than presenting a thin result as authoritative.
 */
data class BlinkBaseline(
    val medianIbiMs: Long = 0L,
    val blinkDurationMs: Long = 0L,
    val completeBlinkRatio: Float = 0f,
    val sampleCount: Int = 0,
) {
    val hasSignal: Boolean get() = sampleCount >= MIN_SAMPLES

    /**
     * Hint interval for when to suggest a break.
     *
     * Blink rate and break cadence are different things, so the personal median
     * inter-blink interval is *scaled* rather than used directly. Using it raw
     * would be wrong twice over: a 3s blink interval is not a licence to
     * interrupt every 1.5s, and a 60s floor would clamp every user to the same
     * value and make the personal baseline irrelevant.
     *
     * [BREAK_MULTIPLIER] turns blink cadence into break cadence, then the
     * clamp keeps the result in a sane band. These bounds are a product
     * decision, not a medically validated limit.
     */
    val suggestionIntervalMs: Long
        get() = (medianIbiMs * BREAK_MULTIPLIER)
            .coerceIn(MIN_SUGGESTION_INTERVAL_MS, MAX_SUGGESTION_INTERVAL_MS)

    companion object {
        const val MIN_SAMPLES = 5
        const val BREAK_MULTIPLIER = 15L
        const val MIN_SUGGESTION_INTERVAL_MS = 20_000L
        const val MAX_SUGGESTION_INTERVAL_MS = 600_000L
    }
}

/**
 * Builds a [BlinkBaseline] from observed blinks.
 *
 * Median rather than mean: a single long "blink" (yawn, or a tracking dropout)
 * must not drag the interval estimate. Incomplete closures are counted but
 * excluded from the interval estimate, since they skew it.
 */
object BlinkStatistics {

    fun summarise(blinks: List<BlinkObservation>): BlinkBaseline {
        val learnable = blinks.filter { it.isLearnable }
        if (learnable.isEmpty()) return BlinkBaseline()

        val ibis = interBlinkIntervals(learnable)
        val complete = learnable.count { it.complete }

        return BlinkBaseline(
            medianIbiMs = ibis.medianOrZero(),
            blinkDurationMs = learnable.map { it.durationMs }.medianOrZero(),
            completeBlinkRatio = complete.toFloat() / learnable.size,
            sampleCount = learnable.size,
        )
    }

    /**
     * Gaps between consecutive blinks. Gaps longer than [MAX_IBI_MS] are
     * discarded rather than averaged in: that long a gap means tracking was
     * lost, not that the user blinked slowly.
     */
    fun interBlinkIntervals(blinks: List<BlinkObservation>): List<Long> =
        blinks.sortedBy { it.startMs }
            .zipWithNext { a, b -> b.startMs - a.startMs }
            .filter { it in MIN_IBI_MS..MAX_IBI_MS }

    // Named for the erased JVM signature; do not add a second overload.
    private fun List<Long>.medianOrZero(): Long {
        if (isEmpty()) return 0L
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Standard deviation of inter-blink intervals; a rough irregularity signal. */
    fun ibiVariation(blinks: List<BlinkObservation>): Double {
        val ibis = interBlinkIntervals(blinks)
        if (ibis.size < 2) return 0.0
        val mean = ibis.average()
        val variance = ibis.sumOf { ibi ->
            val deviation = ibi - mean
            deviation * deviation
        } / ibis.size
        return sqrt(variance)
    }

    /** Absolute jitter between neighbouring intervals; lower is more rhythmic. */
    fun meanAbsoluteDeviation(blinks: List<BlinkObservation>): Double {
        val ibis = interBlinkIntervals(blinks)
        if (ibis.size < 2) return 0.0
        val mean = ibis.average()
        return ibis.sumOf { abs(it - mean) } / ibis.size
    }

    /**
     * Folds new observations into an existing baseline with a small weight, so
     * the model adapts gradually instead of reacting to a short burst. This is
     * the spec's "avoid aggressive updates based on a small number of samples".
     */
    fun merge(current: BlinkBaseline, fresh: BlinkBaseline, weight: Float = DEFAULT_WEIGHT): BlinkBaseline {
        if (!fresh.hasSignal) return current
        if (!current.hasSignal) return fresh
        val w = weight.coerceIn(0f, 1f)
        val blendedIbi = (current.medianIbiMs * (1 - w) + fresh.medianIbiMs * w).toLong()
        val blendedDuration =
            (current.blinkDurationMs * (1 - w) + fresh.blinkDurationMs * w).toLong()
        val blendedRatio =
            current.completeBlinkRatio * (1 - w) + fresh.completeBlinkRatio * w
        val total = current.sampleCount + fresh.sampleCount
        return BlinkBaseline(
            medianIbiMs = blendedIbi,
            blinkDurationMs = blendedDuration,
            completeBlinkRatio = blendedRatio,
            sampleCount = total,
        )
    }

    const val MIN_IBI_MS = 200L
    const val MAX_IBI_MS = 120_000L
    const val DEFAULT_WEIGHT = 0.2f
}
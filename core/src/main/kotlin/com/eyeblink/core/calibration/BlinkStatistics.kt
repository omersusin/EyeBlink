package com.eyeblink.core.calibration

import kotlinx.serialization.Serializable
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
@Serializable
data class BlinkBaseline(
    val medianIbiMs: Long = 0L,
    val blinkDurationMs: Long = 0L,
    val completeBlinkRatio: Float = 0f,
    val sampleCount: Int = 0,
) {
    val hasSignal: Boolean get() = sampleCount >= MIN_SAMPLES && medianIbiMs > 0

    /** Blinks per minute implied by the median interval, or null without enough signal. */
    val blinksPerMinute: Float?
        get() = if (hasSignal) 60_000f / medianIbiMs else null

    /**
     * How much to stretch (>1) or shrink (<1) the user's chosen break interval, given how
     * often this person blinks compared with a typical on-screen reference.
     *
     * The direction is deliberate. This app exists to counteract *reduced* blinking, so a
     * person who blinks rarely gets breaks sooner and a person who blinks often gets them a
     * little later. Using the raw interval the other way round would reward the very
     * behaviour the app is meant to interrupt.
     *
     * Bounded to [MIN_SCALE]..[MAX_SCALE] so a noisy baseline can never change the cadence
     * drastically. This is a product heuristic, not a medically validated limit.
     */
    val personalIntervalScale: Float
        get() = if (!hasSignal) 1f
        else (REFERENCE_IBI_MS.toFloat() / medianIbiMs).coerceIn(MIN_SCALE, MAX_SCALE)

    companion object {
        const val MIN_SAMPLES = 5

        /** A typical gap between blinks while looking at a screen (~7.5 per minute). */
        const val REFERENCE_IBI_MS = 8_000L
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 1.4f
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
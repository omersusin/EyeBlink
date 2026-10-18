package com.eyeblink.core.engine

import com.eyeblink.core.anim.smoothstep
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.ObservedRate
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.calibration.Provenance
import com.eyeblink.core.model.RhythmSettings

enum class FactorCode {
    BASE, APP_SCALE, PERSONAL_BASELINE, READING_CONTEXT, LOW_LIGHT, LONG_SESSION,
    OBSERVED_LOW_RATE, OBSERVED_HEALTHY, SKIP_BACKOFF, JITTER,
}

/** One reason the interval is what it is. The UI lists these so the schedule is never a black box. */
data class Factor(val code: FactorCode, val multiplier: Float, val observed: Boolean = false)

data class IntervalPlan(val intervalMs: Long, val factors: List<Factor>)

data class IntervalInputs(
    val rhythm: RhythmSettings,
    val appScalePercent: Int = 100,
    val context: ContextProfile = ContextProfile.VIDEO,
    val personal: PersonalProfile = PersonalProfile(),
    val mode: TriggerMode = TriggerMode.CAMERA_FREE,
    val sessionMs: Long = 0L,
    val observed: ObservedRate? = null,
    /** -1..1, supplied by the caller so this stays deterministic. */
    val jitter: Float = 0f,
)

/**
 * Turns the user's chosen interval into the interval to use *now*.
 *
 * Each adjustment is a named multiplier so the app can say why the next break is when it is.
 * Adjustments that rest on what the camera actually saw are flagged `observed`; everything
 * else is an estimate from calibration, context or session length.
 */
object IntervalPlanner {

    const val FATIGUE_START_MS = 10 * 60_000L
    const val FATIGUE_FULL_MS = 45 * 60_000L
    const val FATIGUE_MIN_FACTOR = 0.70f
    const val READING_FACTOR = 0.85f
    const val LOW_LIGHT_FACTOR = 0.90f

    fun micro(i: IntervalInputs): IntervalPlan {
        val r = i.rhythm
        val base = r.microIntervalSec * 1_000f
        val factors = mutableListOf(Factor(FactorCode.BASE, 1f))
        var ms = base

        fun apply(f: Factor) {
            if (kotlin.math.abs(f.multiplier - 1f) < 0.005f) return
            ms *= f.multiplier
            factors += f
        }

        apply(Factor(FactorCode.APP_SCALE, i.appScalePercent / 100f))

        if (r.adaptive) {
            val baseline = i.personal.baselineFor(i.context)
            when (baseline.provenance) {
                Provenance.OBSERVED -> apply(Factor(FactorCode.PERSONAL_BASELINE, baseline.baseline.personalIntervalScale, observed = true))
                // An estimate is trusted half as much as a measurement.
                Provenance.ESTIMATED -> apply(
                    Factor(FactorCode.PERSONAL_BASELINE, 1f + (baseline.baseline.personalIntervalScale - 1f) * 0.5f),
                )
                // Nothing personal yet: fall back to coarse context priors.
                Provenance.NONE -> when (i.context) {
                    ContextProfile.READING -> apply(Factor(FactorCode.READING_CONTEXT, READING_FACTOR))
                    ContextProfile.LOW_LIGHT -> apply(Factor(FactorCode.LOW_LIGHT, LOW_LIGHT_FACTOR))
                    else -> Unit
                }
            }

            val fatigue = 1f - (1f - FATIGUE_MIN_FACTOR) * smoothstep(FATIGUE_START_MS.toFloat(), FATIGUE_FULL_MS.toFloat(), i.sessionMs.toFloat())
            apply(Factor(FactorCode.LONG_SESSION, fatigue))

            observedFactor(i, baseline.baseline.blinksPerMinute)?.let(::apply)
        }

        if (r.jitterPercent > 0 && i.jitter != 0f) {
            apply(Factor(FactorCode.JITTER, 1f + r.jitterPercent / 100f * i.jitter.coerceIn(-1f, 1f)))
        }

        val clamped = ms.toLong().coerceIn(
            RhythmSettings.MIN_MICRO_SEC * 1_000L,
            RhythmSettings.MAX_MICRO_SEC * 1_000L,
        ).coerceAtLeast(r.cooldownSec * 1_000L)
        return IntervalPlan(clamped, factors)
    }

    /**
     * Compares what the camera saw with the personal baseline for this context. Only a
     * reliable observation and a measured baseline may move the schedule.
     */
    private fun observedFactor(i: IntervalInputs, baselineBpm: Float?): Factor? {
        if (i.mode != TriggerMode.CAMERA_ASSISTED) return null
        val o = i.observed ?: return null
        if (!o.reliable || baselineBpm == null || baselineBpm <= 0f) return null
        val ratio = o.blinksPerMinute / baselineBpm
        return when {
            ratio < 0.5f -> Factor(FactorCode.OBSERVED_LOW_RATE, 0.55f, observed = true)
            ratio < 1f -> Factor(FactorCode.OBSERVED_LOW_RATE, 0.7f + 0.3f * ((ratio - 0.5f) / 0.5f), observed = true)
            ratio >= 1.3f -> Factor(FactorCode.OBSERVED_HEALTHY, 1.25f, observed = true)
            else -> Factor(FactorCode.OBSERVED_HEALTHY, 1f + 0.25f * ((ratio - 1f) / 0.3f), observed = true)
        }
    }

    /** Skipping rests tells us they are unwelcome right now: back off, up to doubling. */
    fun skipBackoff(skipStreak: Int): Float = 1f + 0.25f * skipStreak.coerceIn(0, 4)

    /**
     * Camera-assisted stare check: has the user gone this long, in tracked time, without a
     * blink? The limit is the larger of a fixed floor and a multiple of their own median gap.
     */
    fun stareLimitMs(medianIbiMs: Long): Long =
        maxOf(STARE_FLOOR_MS, medianIbiMs * STARE_IBI_MULTIPLE)

    const val STARE_FLOOR_MS = 14_000L
    const val STARE_IBI_MULTIPLE = 3L
}

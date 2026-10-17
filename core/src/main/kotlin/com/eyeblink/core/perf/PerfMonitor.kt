package com.eyeblink.core.perf

import com.eyeblink.core.engine.Thermal
import kotlinx.serialization.Serializable

/** Rolling latency statistics for the per-frame pipeline. */
class LatencyTracker(private val capacity: Int = 120) {
    private val samples = ArrayDeque<Long>()

    fun add(ms: Long) {
        samples.addLast(ms.coerceAtLeast(0))
        while (samples.size > capacity) samples.removeFirst()
    }

    val count: Int get() = samples.size
    val mean: Double get() = if (samples.isEmpty()) 0.0 else samples.average()
    fun percentile(p: Double): Long {
        if (samples.isEmpty()) return 0
        val sorted = samples.sorted()
        return sorted[((sorted.size - 1) * p.coerceIn(0.0, 1.0)).toInt()]
    }

    val p50: Long get() = percentile(0.5)
    val p95: Long get() = percentile(0.95)
    fun clear() = samples.clear()
}

enum class PerfReason { OK, SLOW_FRAMES, HOT, LOW_BATTERY }

/** How hard the camera pipeline should work right now. 1.0 = as configured. */
data class PerfAdvice(val fpsFactor: Float, val burstOnly: Boolean, val cameraOff: Boolean, val reason: PerfReason)

/**
 * Degrades gracefully instead of draining the battery or heating the phone: first a lower
 * frame rate, then short bursts only, and finally no camera at all (the scheduler then runs
 * on the camera-free timer, which is always available).
 */
object PerfPolicy {

    fun advise(
        p95Ms: Long,
        frameBudgetMs: Long,
        thermal: Thermal,
        batteryPercent: Int,
        charging: Boolean,
        saverBelowPercent: Int,
    ): PerfAdvice {
        if (thermal >= Thermal.SEVERE) return PerfAdvice(0f, false, true, PerfReason.HOT)
        if (!charging && batteryPercent <= saverBelowPercent) return PerfAdvice(0f, false, true, PerfReason.LOW_BATTERY)
        if (thermal == Thermal.MODERATE) return PerfAdvice(0.5f, true, false, PerfReason.HOT)
        if (frameBudgetMs > 0 && p95Ms > frameBudgetMs * 2) return PerfAdvice(0.5f, true, false, PerfReason.SLOW_FRAMES)
        if (frameBudgetMs > 0 && p95Ms > frameBudgetMs) return PerfAdvice(0.75f, false, false, PerfReason.SLOW_FRAMES)
        return PerfAdvice(1f, false, false, PerfReason.OK)
    }
}

@Serializable
enum class TrackingTag { CAMERA_ASSISTED, CAMERA_FREE }

@Serializable
data class BatterySample(
    val atMs: Long,
    val percent: Int,
    val charging: Boolean,
    val screenOn: Boolean,
    val tag: TrackingTag,
)

data class DrainEstimate(val percentPerHour: Float, val hoursObserved: Float) {
    val trustworthy: Boolean get() = hoursObserved >= BatteryLedger.MIN_HOURS
}

data class DrainComparison(val cameraAssisted: DrainEstimate?, val cameraFree: DrainEstimate?) {
    /** Extra drain in percentage points per hour attributable to the camera, if both are trustworthy. */
    val extraPerHour: Float?
        get() = if (cameraAssisted?.trustworthy == true && cameraFree?.trustworthy == true) {
            cameraAssisted.percentPerHour - cameraFree.percentPerHour
        } else null
}

/**
 * Measures what camera-assisted mode really costs compared with camera-free mode, from the
 * phone's own battery readings. Only intervals with the screen on, not charging, under one
 * tracking mode throughout count - anything else would blame the camera for the charger or
 * the screen-off period. Needs enough hours before it calls an estimate trustworthy.
 */
@Serializable
data class BatteryLedger(val samples: List<BatterySample> = emptyList()) {

    fun add(s: BatterySample): BatteryLedger {
        val last = samples.lastOrNull()
        // Ignore duplicates and clock jumps backwards.
        if (last != null && s.atMs <= last.atMs) return this
        return copy(samples = (samples + s).takeLast(MAX_SAMPLES))
    }

    fun estimate(tag: TrackingTag): DrainEstimate? {
        var drop = 0f
        var hours = 0f
        for ((a, b) in samples.zipWithNext()) {
            if (a.tag != tag || b.tag != tag) continue
            if (a.charging || b.charging || !a.screenOn || !b.screenOn) continue
            val dtH = (b.atMs - a.atMs) / 3_600_000f
            if (dtH <= 0f || dtH > MAX_GAP_HOURS) continue
            val d = (a.percent - b.percent).toFloat()
            if (d < 0f) continue // the level rose without "charging": a sensor glitch
            drop += d
            hours += dtH
        }
        if (hours <= 0f) return null
        return DrainEstimate(drop / hours, hours)
    }

    fun compare(): DrainComparison = DrainComparison(estimate(TrackingTag.CAMERA_ASSISTED), estimate(TrackingTag.CAMERA_FREE))

    companion object {
        const val MIN_HOURS = 0.5f
        const val MAX_GAP_HOURS = 0.5f
        const val MAX_SAMPLES = 2_000
    }
}

package com.eyeblink.core.night

import com.eyeblink.core.anim.smoothstep
import com.eyeblink.core.model.NightSettings
import kotlin.math.abs

enum class NightReason { DISABLED, MANUAL_OVERRIDE, SCHEDULE, DARK_ROOM, DAYTIME }

/** What the display should look like right now. Null fields mean "leave that alone". */
data class NightTarget(
    val active: Boolean,
    val reason: NightReason,
    val nightLight: Boolean? = null,
    val warmthKelvin: Int? = null,
    val brightnessPercent: Int? = null,
)

data class NightInputs(
    val settings: NightSettings,
    val minuteOfDay: Int,
    val nowMs: Long,
    val lux: Float? = null,
    /** After a manual brightness change, EyeBlink stays hands-off until this time. */
    val manualOverrideUntilMs: Long = 0L,
)

/**
 * Smart night mode: decides the target, never forces it. It does not assume that a warmer
 * colour or a particular brightness suits everyone - everything comes from the user's own
 * settings, there is a hard brightness floor, a manual change by the user pauses automation,
 * and `manageNightLight` / `manageBrightness` are independent opt-ins.
 */
object NightPlanner {

    /** Below this ambient light the room is dark enough to count as night regardless of the clock. */
    const val DARK_ROOM_LUX = 3f

    fun target(i: NightInputs): NightTarget {
        val s = i.settings.validated()
        if (!s.enabled) return NightTarget(false, NightReason.DISABLED)
        if (i.nowMs < i.manualOverrideUntilMs) return NightTarget(false, NightReason.MANUAL_OVERRIDE)

        val inWindow = inWindow(i.minuteOfDay, s.startMin, s.endMin)
        val darkRoom = s.adaptToAmbientLight && i.lux != null && i.lux < DARK_ROOM_LUX
        val reason = when {
            inWindow -> NightReason.SCHEDULE
            darkRoom -> NightReason.DARK_ROOM
            else -> return NightTarget(false, NightReason.DAYTIME)
        }

        val brightness = if (s.manageBrightness) {
            val ambient = if (s.adaptToAmbientLight && i.lux != null) {
                // A darker room asks for a dimmer screen, but never below 60% of the chosen night level.
                0.6f + 0.4f * smoothstep(2f, 50f, i.lux)
            } else 1f
            (s.nightBrightnessPercent * ambient).toInt().coerceIn(s.brightnessFloorPercent, 100)
        } else null

        return NightTarget(
            active = true,
            reason = reason,
            nightLight = if (s.manageNightLight) true else null,
            warmthKelvin = if (s.manageNightLight) s.warmthKelvin else null,
            brightnessPercent = brightness,
        )
    }

    /** True when [minute] falls in the window, which may wrap past midnight. */
    fun inWindow(minute: Int, start: Int, end: Int): Boolean {
        val day = 24 * 60
        val m = minute.mod(day)
        val s = start.mod(day)
        val e = end.mod(day)
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }

    /**
     * One step of a gradual change toward [target], moving at a constant rate so that going
     * across the whole 0..100 range takes [rampMs]. Never overshoots.
     */
    fun ramp(current: Float, target: Float, dtMs: Long, rampMs: Long): Float {
        if (rampMs <= 0) return target
        val maxStep = 100f * dtMs.coerceAtLeast(0) / rampMs
        val delta = target - current
        return if (abs(delta) <= maxStep) target else current + if (delta > 0) maxStep else -maxStep
    }

    /**
     * Distinguishes "the user turned brightness" from "we did": EyeBlink remembers what it
     * last wrote, and any other value means somebody else changed it.
     */
    fun isManualChange(observedPercent: Int, lastWrittenPercent: Int?, tolerance: Int = 3): Boolean =
        lastWrittenPercent != null && abs(observedPercent - lastWrittenPercent) > tolerance
}

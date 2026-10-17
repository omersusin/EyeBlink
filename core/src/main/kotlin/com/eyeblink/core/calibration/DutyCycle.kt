package com.eyeblink.core.calibration

import com.eyeblink.core.engine.Thermal
import com.eyeblink.core.model.CameraSettings
import com.eyeblink.core.model.TrackingMode

enum class CameraOffReason {
    USER_OFF, NOT_ELIGIBLE, NO_PERMISSION, BATTERY, THERMAL, TOO_DARK, CAMERA_BUSY, BETWEEN_BURSTS,
}

/**
 * Whether the camera should be running right now, and at what frame rate.
 * [recheckInMs] is when the caller should ask again.
 */
data class DutyPlan(
    val runCamera: Boolean,
    val fps: Int,
    val recheckInMs: Long,
    val offReason: CameraOffReason? = null,
)

/** Conditions that veto camera work regardless of the user's mode. */
data class TrackingConstraints(
    val eligible: Boolean,
    val permissionGranted: Boolean,
    val batterySaver: Boolean,
    val thermal: Thermal,
    val tooDark: Boolean,
    val cameraBusyElsewhere: Boolean,
)

/**
 * Camera duty cycling: the biggest battery lever in camera-assisted mode.
 *
 * In BURST mode the camera runs for a short window every period instead of continuously
 * (about 18% duty with the defaults) - enough to estimate the blink rate without paying for
 * constant inference. Constraints are checked first and always win.
 */
class DutyCycle(private val settings: () -> CameraSettings) {

    private enum class Phase { STOPPED, BURST, IDLE }

    private var phase = Phase.STOPPED
    private var phaseStartMs = 0L

    fun plan(nowMs: Long, c: TrackingConstraints): DutyPlan {
        val s = settings().validated()
        if (s.tracking == TrackingMode.OFF) return stop(CameraOffReason.USER_OFF)
        if (!c.eligible) return stop(CameraOffReason.NOT_ELIGIBLE)
        if (!c.permissionGranted) return stop(CameraOffReason.NO_PERMISSION)
        if (c.batterySaver) return stop(CameraOffReason.BATTERY)
        if (c.thermal >= Thermal.SEVERE) return stop(CameraOffReason.THERMAL)
        if (c.cameraBusyElsewhere) return stop(CameraOffReason.CAMERA_BUSY)
        if (c.tooDark) return stop(CameraOffReason.TOO_DARK)

        // Heat shaves the frame rate before it takes the camera away.
        val fps = if (c.thermal >= Thermal.MODERATE) (s.targetFps / 2).coerceAtLeast(MIN_FPS) else s.targetFps

        if (s.tracking == TrackingMode.CONTINUOUS) {
            phase = Phase.BURST
            phaseStartMs = nowMs
            return DutyPlan(true, fps, RECHECK_ACTIVE_MS)
        }

        val burstMs = s.burstSec * 1_000L
        val idleMs = (s.periodSec * 1_000L - burstMs).coerceAtLeast(MIN_IDLE_MS)

        return when (phase) {
            Phase.STOPPED -> begin(Phase.BURST, nowMs).let { DutyPlan(true, fps, burstMs) }
            Phase.BURST -> {
                val elapsed = nowMs - phaseStartMs
                if (elapsed >= burstMs) {
                    begin(Phase.IDLE, nowMs)
                    DutyPlan(false, fps, idleMs, CameraOffReason.BETWEEN_BURSTS)
                } else {
                    DutyPlan(true, fps, burstMs - elapsed)
                }
            }
            Phase.IDLE -> {
                val elapsed = nowMs - phaseStartMs
                if (elapsed >= idleMs) {
                    begin(Phase.BURST, nowMs)
                    DutyPlan(true, fps, burstMs)
                } else {
                    DutyPlan(false, fps, idleMs - elapsed, CameraOffReason.BETWEEN_BURSTS)
                }
            }
        }
    }

    fun reset() {
        phase = Phase.STOPPED
        phaseStartMs = 0L
    }

    private fun begin(next: Phase, nowMs: Long) {
        phase = next
        phaseStartMs = nowMs
    }

    private fun stop(reason: CameraOffReason): DutyPlan {
        reset()
        return DutyPlan(false, 0, RECHECK_IDLE_MS, reason)
    }

    companion object {
        const val RECHECK_IDLE_MS = 2_000L
        const val RECHECK_ACTIVE_MS = 1_000L
        const val MIN_IDLE_MS = 5_000L
        const val MIN_FPS = 8
    }
}

package com.eyeblink.core.anim

import kotlinx.serialization.Serializable

/**
 * Everything that describes one blink: timing, easing and look.
 *
 * Defaults are the spec's initial timing - 140 ms closing, 150 ms fully black,
 * 165 ms opening, 455 ms in total. All of it is user-configurable; [validated]
 * keeps hand-edited or stale stored values inside a range the animation can still
 * render sensibly.
 */
@Serializable
data class BlinkSpec(
    val closeMs: Int = 140,
    val holdMs: Int = 150,
    val openMs: Int = 165,
    val closeEase: EaseKind = EaseKind.NATURAL_CLOSE,
    val openEase: EaseKind = EaseKind.NATURAL_OPEN,
    /** How strongly the lid margins bow into an almond shape while closing, 0..1. */
    val curvature: Float = 0.35f,
    /** Softness of the lid margin, 0 (hard edge) .. 1 (wide feather). */
    val feather: Float = 0.5f,
    /** Strength of the whole-screen darkening that builds while the lids close, 0..1. */
    val darken: Float = 0.6f,
    /** Faint warm rim light along the lid margin. Off by default: lit pixels defeat OLED black. */
    val rimLight: Float = 0f,
    /** Replace the sweeping lids with a plain fade (also forced by the system "remove animations"). */
    val reducedMotion: Boolean = false,
) {
    val totalMs: Int get() = closeMs + holdMs + openMs

    fun validated(): BlinkSpec = copy(
        closeMs = closeMs.coerceIn(MIN_PHASE_MS, MAX_CLOSE_MS),
        holdMs = holdMs.coerceIn(0, MAX_HOLD_MS),
        openMs = openMs.coerceIn(MIN_PHASE_MS, MAX_OPEN_MS),
        curvature = curvature.coerceIn(0f, 1f),
        feather = feather.coerceIn(0f, 1f),
        darken = darken.coerceIn(0f, 1f),
        rimLight = rimLight.coerceIn(0f, 1f),
    )

    companion object {
        const val MIN_PHASE_MS = 40
        const val MAX_CLOSE_MS = 800
        const val MAX_HOLD_MS = 3_000
        const val MAX_OPEN_MS = 1_200
    }
}

enum class BlinkPhase { IDLE, CLOSING, HOLD, OPENING }

/**
 * One sampled instant of the animation, already reduced to the numbers a renderer
 * needs. The renderer decides nothing about timing or easing.
 */
data class BlinkFrame(
    val phase: BlinkPhase,
    /** 0 = fully open, 1 = lids met. */
    val closure: Float,
    /** Alpha of the whole-screen darkening layer. */
    val dim: Float,
    /** Alpha of the pure `#000000` cover. 1 only while the lids are fully closed. */
    val solid: Float,
    /** True when eyelids should be drawn; false is the reduced-motion plain fade. */
    val sweep: Boolean,
) {
    val isIdle: Boolean get() = phase == BlinkPhase.IDLE

    companion object {
        val Idle = BlinkFrame(BlinkPhase.IDLE, 0f, 0f, 0f, sweep = true)
    }
}

/**
 * Maps elapsed time to a [BlinkFrame].
 *
 * Closing eases lids shut and builds a subtle full-screen darkening. The hold is a
 * flat, pure-black cover (on OLED, no pixel emits light). Opening eases the lids
 * away and lets the darkening drain. Nothing here reads a clock: the caller feeds
 * in elapsed milliseconds, which keeps the timeline deterministic and testable.
 */
class BlinkTimeline(requested: BlinkSpec) {
    // Named differently from the property on purpose: inside initializers a constructor
    // parameter shadows a same-named property, which would silently skip validation.
    val spec: BlinkSpec = requested.validated()

    val totalMs: Int get() = spec.totalMs

    private val closeEnd = spec.closeMs.toFloat()
    private val holdEnd = closeEnd + spec.holdMs
    private val openEnd = holdEnd + spec.openMs

    fun frameAt(elapsedMs: Float): BlinkFrame {
        if (elapsedMs < 0f || elapsedMs >= openEnd) return BlinkFrame.Idle

        return when {
            elapsedMs < closeEnd -> {
                val p = spec.closeEase.at(elapsedMs / closeEnd).coerceIn(0f, 1f)
                shape(BlinkPhase.CLOSING, closure = p, dimRamp = p)
            }
            elapsedMs < holdEnd -> shape(BlinkPhase.HOLD, closure = 1f, dimRamp = 1f)
            else -> {
                val q = spec.openEase.at((elapsedMs - holdEnd) / (openEnd - holdEnd)).coerceIn(0f, 1f)
                shape(BlinkPhase.OPENING, closure = 1f - q, dimRamp = 1f - q)
            }
        }
    }

    private fun shape(phase: BlinkPhase, closure: Float, dimRamp: Float): BlinkFrame {
        if (spec.reducedMotion) {
            // No sweeping motion: a plain fade to black and back.
            return BlinkFrame(phase, closure = closure, dim = 0f, solid = closure, sweep = false)
        }
        val fullyClosed = closure >= FULLY_CLOSED
        val dim = if (fullyClosed) 1f else (spec.darken * dimRamp.pow15()).coerceIn(0f, 1f)
        val solid = if (fullyClosed) 1f else smoothstep(SOLID_RAMP_START, 1f, closure)
        return BlinkFrame(phase, closure, dim, solid, sweep = true)
    }

    private fun Float.pow15(): Float = this * kotlin.math.sqrt(this)

    companion object {
        /** Closure at or above this is treated as the lids having met. */
        const val FULLY_CLOSED = 0.999f

        /** The pure-black cover starts fading in this late in the closure so the seam never pops. */
        const val SOLID_RAMP_START = 0.94f
    }
}

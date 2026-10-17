package com.eyeblink.core.engine

import com.eyeblink.core.model.NeverInterrupt

/**
 * Why a break is not happening right now. [kind] decides how the scheduler treats it:
 *
 *  - [Kind.INELIGIBLE]: the situation does not call for breaks at all (screen off, an app the
 *    user did not select). The timers do not run and nothing is "due".
 *  - [Kind.HOLD]: a break is due but must wait until the situation clears - a call, an open
 *    keyboard, a payment screen. These never expire into a forced break.
 *  - [Kind.SOFT]: a passing obstacle (finger down, swipe in flight). Bounded by a deferral
 *    budget, after which the break goes ahead at the next quiet frame.
 */
enum class Reason(val kind: Kind) {
    DISABLED(Kind.INELIGIBLE),
    SUSPENDED(Kind.INELIGIBLE),
    SNOOZED(Kind.INELIGIBLE),
    SCREEN_OFF(Kind.INELIGIBLE),
    DEVICE_LOCKED(Kind.INELIGIBLE),
    UNKNOWN_APP(Kind.INELIGIBLE),
    NOT_SELECTED_APP(Kind.INELIGIBLE),
    NEVER_LIST(Kind.INELIGIBLE),
    QUIET_HOURS(Kind.INELIGIBLE),
    BATTERY_SAVER(Kind.INELIGIBLE),
    THERMAL(Kind.INELIGIBLE),

    IN_CALL(Kind.HOLD),
    CAMERA_IN_USE(Kind.HOLD),
    NAVIGATION(Kind.HOLD),
    SENSITIVE_SCREEN(Kind.HOLD),
    TEXT_INPUT(Kind.HOLD),
    COOLDOWN(Kind.HOLD),
    NO_PAUSE_SUPPORT(Kind.INELIGIBLE),

    /** A finger is on the screen. Never overridden: an active gesture is not interrupted. */
    TOUCHING(Kind.HOLD),
    RECENT_TOUCH(Kind.SOFT),
    VIDEO_TRANSITION(Kind.SOFT),
    PREFER_TRANSITION(Kind.SOFT);

    enum class Kind { INELIGIBLE, HOLD, SOFT }
}

/** The outcome of asking "may I interrupt now?". */
data class GateDecision(val reason: Reason?, val retryInMs: Long = 0L) {
    val allowed: Boolean get() = reason == null

    companion object {
        val Allow = GateDecision(null)
    }
}

/** User-controlled inputs to the gate, derived from settings once per evaluation. */
data class GatePolicy(
    val masterEnabled: Boolean = true,
    val suspended: Boolean = false,
    val snoozedUntilMs: Long = 0L,
    /** The foreground app's profile exists and is enabled. */
    val appSelected: Boolean = false,
    val quietHoursActive: Boolean = false,
    val batterySaverActive: Boolean = false,
    /** Treat an app whose identity cannot be determined as ineligible (the safe default). */
    val requireKnownApp: Boolean = true,
)

/**
 * The single place that answers "is it acceptable to interrupt right now?".
 *
 * Rules are evaluated most-fundamental first so the reported reason is the real blocker.
 * Everything is a pure function of its arguments.
 */
object InterruptionGate {

    /** A finger lifted this recently still counts as an active gesture. */
    const val TOUCH_SETTLE_MS = 450L

    /** A swipe this recent means the next video is still loading in. */
    const val TRANSITION_SETTLE_MS = 700L

    /** Re-check cadence the caller should use while held. */
    const val HOLD_RETRY_MS = 1_000L

    const val SOFT_RETRY_MS = 250L

    fun evaluate(ctx: ContextSnapshot, policy: GatePolicy): GateDecision {
        if (!policy.masterEnabled) return GateDecision(Reason.DISABLED)
        if (policy.suspended) return GateDecision(Reason.SUSPENDED)
        if (ctx.nowMs < policy.snoozedUntilMs) return GateDecision(Reason.SNOOZED, policy.snoozedUntilMs - ctx.nowMs)
        if (!ctx.screenOn) return GateDecision(Reason.SCREEN_OFF)
        if (ctx.deviceLocked) return GateDecision(Reason.DEVICE_LOCKED)
        if (policy.quietHoursActive) return GateDecision(Reason.QUIET_HOURS)
        if (policy.batterySaverActive) return GateDecision(Reason.BATTERY_SAVER)
        if (ctx.thermal >= Thermal.SEVERE) return GateDecision(Reason.THERMAL)

        val pkg = ctx.foregroundPackage
        if (pkg == null) {
            if (policy.requireKnownApp) return GateDecision(Reason.UNKNOWN_APP)
        } else {
            if (NeverInterrupt.matches(pkg)) return GateDecision(Reason.NEVER_LIST)
            if (!policy.appSelected) return GateDecision(Reason.NOT_SELECTED_APP)
        }

        if (ctx.audioMode != AudioMode.NORMAL) return GateDecision(Reason.IN_CALL, HOLD_RETRY_MS)
        if (ctx.cameraBusyElsewhere) return GateDecision(Reason.CAMERA_IN_USE, HOLD_RETRY_MS)
        if (ctx.navigationActive) return GateDecision(Reason.NAVIGATION, HOLD_RETRY_MS)
        if (ctx.sensitiveScreen) return GateDecision(Reason.SENSITIVE_SCREEN, HOLD_RETRY_MS)
        if (ctx.keyboardVisible || ctx.textInputFocused) return GateDecision(Reason.TEXT_INPUT, HOLD_RETRY_MS)

        if (ctx.touching) return GateDecision(Reason.TOUCHING, SOFT_RETRY_MS)
        ctx.sinceTouchMs()?.let { since ->
            if (since < TOUCH_SETTLE_MS) return GateDecision(Reason.RECENT_TOUCH, TOUCH_SETTLE_MS - since)
        }
        ctx.sinceScrollMs()?.let { since ->
            if (since < TRANSITION_SETTLE_MS) return GateDecision(Reason.VIDEO_TRANSITION, TRANSITION_SETTLE_MS - since)
        }
        return GateDecision.Allow
    }
}

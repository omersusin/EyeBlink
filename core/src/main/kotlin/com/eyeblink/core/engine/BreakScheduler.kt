package com.eyeblink.core.engine

import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.ObservedRate
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.NoPauseFallback

enum class BreakKind { MICRO, REST }

enum class BreakOutcome { COMPLETED, SKIPPED_BY_USER, ABORTED, FAILED }

/** Everything the scheduler needs besides the device snapshot. */
data class SchedulerInput(
    val settings: EyeBlinkSettings,
    val suspended: Boolean,
    /** Profile of the foreground app, or null when it is not a selected app. */
    val profile: AppProfile?,
    val context: ContextProfile,
    val personal: PersonalProfile,
    val triggerMode: TriggerMode,
    val observed: ObservedRate?,
    /** A pause strategy has been verified for the foreground app. */
    val canPauseVerified: Boolean,
    /** -1..1; renewed by the scheduler after each break. */
    val jitter: Float = 0f,
)

sealed interface SchedulerStep {
    /** Nothing applies right now (screen off, other app...). Timers are not running. */
    data class Idle(val reason: Reason) : SchedulerStep

    data class Waiting(val microInMs: Long, val restInMs: Long?, val plan: IntervalPlan) : SchedulerStep

    /** A break is due but the moment is wrong. */
    data class Deferred(val kind: BreakKind, val reason: Reason, val retryInMs: Long) : SchedulerStep

    data class Start(val kind: BreakKind, val forced: Boolean, val stare: Boolean = false) : SchedulerStep
}

/**
 * Decides when a break is due and whether now is an acceptable moment for it.
 *
 * Time only advances while the user is actually in a selected app with the screen on;
 * leaving, locking or snoozing pauses the clocks, and a long absence starts a fresh session.
 * The class reads no clock - every call carries `ctx.nowMs` - so a whole afternoon of use can
 * be simulated in a unit test.
 */
class BreakScheduler {

    private var lastStepMs: Long? = null
    private var activeSinceMicroMs = 0L
    private var activeSinceRestMs = 0L
    private var sessionActiveMs = 0L
    private var awayMs = 0L

    private var lastBreakAtMs: Long? = null
    private var lastRestEndedAtMs: Long? = null
    private val microHistory = ArrayDeque<Long>()
    private var skipStreak = 0

    private var dueSinceMs: Long? = null
    private var dueKind: BreakKind? = null
    private var retryNotBeforeMs = 0L

    var snoozedUntilMs: Long = 0L
        private set

    val sessionMs: Long get() = sessionActiveMs

    fun snooze(untilMs: Long) {
        snoozedUntilMs = untilMs
    }

    fun clearSnooze() {
        snoozedUntilMs = 0L
    }

    fun resetTimers() {
        activeSinceMicroMs = 0
        activeSinceRestMs = 0
        sessionActiveMs = 0
        awayMs = 0
        dueSinceMs = null
        dueKind = null
        lastStepMs = null
    }

    fun step(ctx: ContextSnapshot, input: SchedulerInput): SchedulerStep {
        val now = ctx.nowMs
        val dt = lastStepMs?.let { (now - it).coerceIn(0L, MAX_STEP_DT_MS) } ?: 0L
        lastStepMs = now

        val rhythm = input.settings.rhythm
        val policy = GatePolicy(
            masterEnabled = true,
            suspended = input.suspended,
            snoozedUntilMs = snoozedUntilMs,
            appSelected = input.profile != null,
            quietHoursActive = rhythm.quietHours.contains(ctx.localMinuteOfDay),
        )
        val gate = InterruptionGate.evaluate(ctx, policy)

        gate.reason?.let { reason ->
            if (reason.kind == Reason.Kind.INELIGIBLE) return ineligible(reason, dt)
        }
        if (input.settings.playback.fallback == NoPauseFallback.SKIP &&
            input.settings.playback.pauseDuringBreaks && !input.canPauseVerified
        ) {
            return ineligible(Reason.NO_PAUSE_SUPPORT, dt)
        }

        // Eligible: the clocks run.
        awayMs = 0
        activeSinceMicroMs += dt
        activeSinceRestMs += dt
        sessionActiveMs += dt

        val restEnabled = rhythm.restEnabled && (input.profile?.restEnabled ?: true)
        val restIntervalMs = (rhythm.restIntervalMin * 60_000L * IntervalPlanner.skipBackoff(skipStreak)).toLong()
        val plan = IntervalPlanner.micro(
            IntervalInputs(
                rhythm = rhythm,
                appScalePercent = input.profile?.intervalScalePercent ?: 100,
                context = input.context,
                personal = input.personal,
                mode = input.triggerMode,
                sessionMs = sessionActiveMs,
                observed = input.observed,
                jitter = input.jitter,
            ),
        )

        val restDue = restEnabled && activeSinceRestMs >= restIntervalMs
        val restInMs = if (restEnabled) (restIntervalMs - activeSinceRestMs).coerceAtLeast(0L) else null

        val inGrace = lastRestEndedAtMs?.let { now - it < rhythm.postRestGraceMin * 60_000L } ?: false
        val overHourlyCap = hourlyCount(now) >= rhythm.maxMicroPerHour
        val stare = isStaring(input, plan)
        val microDue = !inGrace && !overHourlyCap && (activeSinceMicroMs >= plan.intervalMs || stare)
        // A rest is about to start anyway: don't spend a micro blink right before it.
        val restImminent = restInMs != null && restInMs in 1..REST_IMMINENT_MS

        val kind = when {
            restDue -> BreakKind.REST
            microDue && !restImminent -> BreakKind.MICRO
            else -> null
        }

        if (kind == null) {
            dueSinceMs = null
            dueKind = null
            val microIn = if (inGrace || overHourlyCap) plan.intervalMs else (plan.intervalMs - activeSinceMicroMs).coerceAtLeast(0L)
            return SchedulerStep.Waiting(microIn, restInMs, plan)
        }

        if (dueKind != kind) {
            dueKind = kind
            dueSinceMs = now
        }
        val waited = now - (dueSinceMs ?: now)

        if (now < retryNotBeforeMs) {
            return SchedulerStep.Deferred(kind, Reason.COOLDOWN, retryNotBeforeMs - now)
        }
        lastBreakAtMs?.let { last ->
            val remaining = rhythm.cooldownSec * 1_000L - (now - last)
            if (remaining > 0) return SchedulerStep.Deferred(kind, Reason.COOLDOWN, remaining)
        }

        gate.reason?.let { reason ->
            val overridable = reason.kind == Reason.Kind.SOFT &&
                waited >= rhythm.softDeferralBudgetSec * 1_000L
            if (!overridable) return SchedulerStep.Deferred(kind, reason, gate.retryInMs.coerceAtLeast(MIN_RETRY_MS))
            return SchedulerStep.Start(kind, forced = true, stare = stare)
        }

        // The moment is clear. If we cannot pause the video, prefer to blink right after a swipe.
        if (wantsTransition(kind, input, ctx) && waited < preferWindowMs(kind)) {
            return SchedulerStep.Deferred(kind, Reason.PREFER_TRANSITION, PREFER_RETRY_MS)
        }
        return SchedulerStep.Start(kind, forced = false, stare = stare)
    }

    /** Record how a break ended so the next one is timed from the right moment. */
    fun onBreakFinished(kind: BreakKind, outcome: BreakOutcome, nowMs: Long) {
        dueSinceMs = null
        dueKind = null
        lastBreakAtMs = nowMs
        lastStepMs = nowMs

        when (outcome) {
            BreakOutcome.ABORTED, BreakOutcome.FAILED -> {
                // It did not really happen; ask again soon rather than wait a whole interval.
                retryNotBeforeMs = nowMs + ABORT_RETRY_MS
                return
            }
            BreakOutcome.COMPLETED, BreakOutcome.SKIPPED_BY_USER -> Unit
        }

        when (kind) {
            BreakKind.MICRO -> {
                activeSinceMicroMs = 0
                microHistory.addLast(nowMs)
            }
            BreakKind.REST -> {
                activeSinceMicroMs = 0
                activeSinceRestMs = 0
                sessionActiveMs = 0
                lastRestEndedAtMs = nowMs
                skipStreak = if (outcome == BreakOutcome.SKIPPED_BY_USER) skipStreak + 1 else 0
            }
        }
    }

    private fun ineligible(reason: Reason, dt: Long): SchedulerStep {
        awayMs += dt
        dueSinceMs = null
        dueKind = null
        if (awayMs >= SESSION_RESET_AWAY_MS) {
            activeSinceMicroMs = 0
            sessionActiveMs = 0
        }
        return SchedulerStep.Idle(reason)
    }

    private fun hourlyCount(now: Long): Int {
        while (microHistory.isNotEmpty() && now - microHistory.first() > HOUR_MS) microHistory.removeFirst()
        return microHistory.size
    }

    private fun isStaring(input: SchedulerInput, plan: IntervalPlan): Boolean {
        if (input.triggerMode != TriggerMode.CAMERA_ASSISTED || !input.settings.rhythm.adaptive) return false
        val o = input.observed ?: return false
        if (!o.reliable) return false
        val baseline = input.personal.baselineFor(input.context).baseline
        if (!baseline.hasSignal) return false
        // Only worth interrupting early if a real interval has mostly elapsed already.
        if (activeSinceMicroMs < plan.intervalMs / 3) return false
        return o.trackedSinceBlinkMs >= IntervalPlanner.stareLimitMs(baseline.medianIbiMs)
    }

    private fun wantsTransition(kind: BreakKind, input: SchedulerInput, ctx: ContextSnapshot): Boolean {
        if (input.canPauseVerified) return false
        val fallback = input.settings.playback.fallback
        val wants = when (kind) {
            BreakKind.MICRO -> input.settings.rhythm.preferTransitions
            BreakKind.REST -> fallback == NoPauseFallback.WAIT_FOR_TRANSITION
        }
        if (!wants) return false
        if (ctx.media == MediaSignal.NONE) return false
        val sinceScroll = ctx.sinceScrollMs() ?: return true
        // Just after a swipe is the convenient moment; mid-video is not.
        return sinceScroll !in InterruptionGate.TRANSITION_SETTLE_MS..CONVENIENT_WINDOW_MS
    }

    private fun preferWindowMs(kind: BreakKind) = if (kind == BreakKind.REST) REST_PREFER_WINDOW_MS else MICRO_PREFER_WINDOW_MS

    companion object {
        const val MAX_STEP_DT_MS = 3_000L
        const val SESSION_RESET_AWAY_MS = 90_000L
        const val REST_IMMINENT_MS = 8_000L
        const val ABORT_RETRY_MS = 3_000L
        const val MIN_RETRY_MS = 200L
        const val HOUR_MS = 3_600_000L
        const val CONVENIENT_WINDOW_MS = 3_000L
        const val MICRO_PREFER_WINDOW_MS = 8_000L
        const val REST_PREFER_WINDOW_MS = 25_000L
        const val PREFER_RETRY_MS = 300L
    }
}

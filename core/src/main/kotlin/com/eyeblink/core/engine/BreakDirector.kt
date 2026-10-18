package com.eyeblink.core.engine

import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.playback.PauseResult
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackCoordinator
import com.eyeblink.core.playback.PauseToken
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.ResumeEnvironment
import com.eyeblink.core.playback.ResumeResult
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.playback.Verification
import com.eyeblink.core.rest.RestOutcome
import com.eyeblink.core.rest.RestRequest
import com.eyeblink.core.rest.RestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Draws the break. Implemented by the platform; faked in tests. */
interface OverlayPort {
    /** Plays one blink and returns once the screen is open again. */
    suspend fun playBlink(spec: BlinkSpec)

    /** Shows a rest scene driven by [session] and returns when the session has finished. */
    suspend fun runRest(session: RestSession, spec: BlinkSpec)

    /** Removes everything EyeBlink has drawn. Idempotent and safe to call from any state. */
    fun clear()
}

interface DirectorEnvironment {
    fun nowMs(): Long
    fun snapshot(): ContextSnapshot
    fun gatePolicy(target: String?): GatePolicy

    /** The proven, user-permitted playback backend for [target], or null to leave playback alone. */
    suspend fun backendFor(target: String): PlaybackBackend?

    /**
     * Every backend a break may use for [target], best first. A break moves on to the next one when a backend cannot
     * see the player or cannot deliver its command; it never moves on after a command has gone out.
     */
    suspend fun backendsFor(target: String): List<PlaybackBackend> = listOfNotNull(backendFor(target))

    /** The camera is tracking reliably, so a real blink can confirm a rest. */
    fun blinkConfirmAvailable(): Boolean
}

data class BreakRequest(
    val kind: BreakKind,
    /** Package of the foreground app the break is for. */
    val target: String?,
    /** The scheduler overrode a soft obstacle, so the execution-time re-check should too. */
    val forced: Boolean = false,
)

enum class PauseSummary {
    NOT_ATTEMPTED,

    /** The player reported that it was not playing, so nothing was touched. */
    NOT_PLAYING,

    /** The app has no player that can be read (no media session), so nothing was touched. */
    NO_SESSION,

    /** The player could not be read, so nothing was touched. */
    UNREADABLE,
    PAUSED,
    UNVERIFIED,
    FAILED,
}
enum class ResumeSummary { NOT_ATTEMPTED, RESUMED, ALREADY_PLAYING, REFUSED, FAILED }

data class BreakReport(
    val kind: BreakKind,
    val target: String?,
    val startedMs: Long,
    val endedMs: Long,
    val outcome: BreakOutcome,
    val abortedBy: Reason? = null,
    val strategy: Strategy? = null,
    val pause: PauseSummary = PauseSummary.NOT_ATTEMPTED,
    val resume: ResumeSummary = ResumeSummary.NOT_ATTEMPTED,
    val restOutcome: RestOutcome? = null,
    val error: String? = null,
)

/**
 * Runs one break from start to finish with every safety rule in the right order:
 *
 *   re-check -> pause + verify -> draw -> re-check the world -> resume + verify -> clean up
 *
 * Whatever happens in the middle - an exception, the user hitting emergency stop, the job
 * being cancelled - the overlay is removed and a video EyeBlink paused is given back, in a
 * `NonCancellable` finally block.
 */
class BreakDirector(
    private val overlay: OverlayPort,
    private val coordinator: PlaybackCoordinator,
    private val env: DirectorEnvironment,
) {
    @Volatile
    private var activeRest: RestSession? = null

    /** A natural blink from the camera: confirms a blink-confirmed rest. */
    fun onUserBlink(): Boolean = activeRest?.onBlink(env.nowMs()) ?: false

    /** The on-screen confirm affordance was tapped. */
    fun onUserTap(): Boolean = activeRest?.onTap(env.nowMs()) ?: false

    /** The user skipped the rest. */
    fun skipRest() {
        activeRest?.skip(env.nowMs())
    }

    suspend fun run(request: BreakRequest, settings: EyeBlinkSettings): BreakReport {
        val started = env.nowMs()
        val target = request.target

        // 1. The world may have changed since the scheduler decided.
        val decision = InterruptionGate.evaluate(env.snapshot(), env.gatePolicy(target))
        decision.reason?.let { reason ->
            val tolerated = request.forced && reason.kind == Reason.Kind.SOFT
            if (!tolerated) return BreakReport(request.kind, target, started, env.nowMs(), BreakOutcome.ABORTED, abortedBy = reason)
        }

        val spec = settings.blink.validated()
        val profile = settings.profileFor(target)
        var pause = PauseSummary.NOT_ATTEMPTED
        var resume = ResumeSummary.NOT_ATTEMPTED
        var strategy: Strategy? = null
        var restOutcome: RestOutcome? = null
        var backend: PlaybackBackend? = null
        var token: PauseToken? = null
        var failure: String? = null
        var resumeHandled = false

        val restSeconds = settings.rest.effectiveDurationSec

        suspend fun resumeNow(): ResumeResult? {
            val b = backend ?: return null
            val t = token ?: return null
            resumeHandled = true
            if (!settings.playback.resumeAfterBreak) return null
            val maxHold = maxOf(settings.playback.maxHoldSec * 1_000L, restSeconds * 1_000L + REST_HOLD_MARGIN_MS)
            return coordinator.resume(b, t) {
                val ctx = env.snapshot()
                ResumeEnvironment(
                    foregroundPackage = ctx.foregroundPackage,
                    nowMs = env.nowMs(),
                    maxHoldMs = maxHold,
                    userTouchedSincePause = (ctx.lastTouchMs ?: Long.MIN_VALUE) > t.pausedAtMs,
                )
            }
        }

        try {
            // 2. Pause and verify - only for a proven backend and only a player that is playing.
            if (target != null && settings.playback.pauseDuringBreaks && (profile?.pauseVideo != false)) {
                var sawNoPlayer = false
                var asked = false
                for (candidate in env.backendsFor(target)) {
                    // Once one backend has been asked, only those that read the player's own state may be asked after it:
                    // a blind one is no reason to reach for a way that cannot tell whose sound it is (a media key would
                    // pause whatever else is playing).
                    if (asked && candidate.strategy.verification != Verification.SESSION_STATE) continue
                    asked = true
                    strategy = candidate.strategy
                    backend = candidate
                    var next = false
                    // The token is held from the moment a command may go out, so that a break cancelled while the pause is
                    // still being confirmed (Stop everything, the tile) can still give the video back.
                    when (val r = coordinator.pause(candidate, target, onSending = { token = it }, nowMs = env::nowMs)) {
                        is PauseResult.Paused -> { token = r.token; pause = PauseSummary.PAUSED; failure = null }
                        is PauseResult.Unverified -> { token = r.token; pause = PauseSummary.UNVERIFIED; failure = null }
                        is PauseResult.NotPlaying -> {
                            // A backend that cannot see this app's player says nothing about the player: ask the next.
                            next = r.saw == PlayState.NONE || r.saw == PlayState.UNKNOWN
                            if (r.saw == PlayState.NONE) sawNoPlayer = true
                            pause = when {
                                r.saw == PlayState.NONE || (r.saw == PlayState.UNKNOWN && sawNoPlayer) -> PauseSummary.NO_SESSION
                                r.saw == PlayState.UNKNOWN -> PauseSummary.UNREADABLE
                                else -> PauseSummary.NOT_PLAYING
                            }
                        }
                        // Nothing was delivered, so nothing has happened to the video: the next backend may try.
                        is PauseResult.Failed -> { token = null; pause = PauseSummary.FAILED; failure = r.error; next = true }
                    }
                    if (!next) break
                }
            }

            // 3. Draw.
            when (request.kind) {
                BreakKind.MICRO -> overlay.playBlink(spec)
                BreakKind.REST -> {
                    val session = RestSession(
                        RestRequest(
                            style = settings.rest.style,
                            durationSec = restSeconds,
                            confirm = settings.rest.confirm,
                            blinkConfirmAvailable = env.blinkConfirmAvailable(),
                            reducedMotion = spec.reducedMotion,
                        ),
                        env.nowMs(),
                    )
                    activeRest = session
                    try {
                        overlay.runRest(session, spec)
                    } finally {
                        activeRest = null
                    }
                    restOutcome = if (session.isFinished) session.view(env.nowMs()).outcome else RestOutcome.CANCELLED
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cleanUp(::resumeNow, resumeHandled) }
            throw e
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
            withContext(NonCancellable) { cleanUp(::resumeNow, resumeHandled) }
            return BreakReport(request.kind, target, started, env.nowMs(), BreakOutcome.FAILED, strategy = strategy, pause = pause, restOutcome = restOutcome, error = failure)
        }

        // 4. Give the video back, but only if every safety rule still holds.
        overlay.clear()
        when (val r = resumeNow()) {
            null -> Unit
            ResumeResult.Resumed -> resume = ResumeSummary.RESUMED
            ResumeResult.AlreadyPlaying -> resume = ResumeSummary.ALREADY_PLAYING
            is ResumeResult.Refused -> resume = ResumeSummary.REFUSED
            is ResumeResult.Failed -> { resume = ResumeSummary.FAILED; failure = r.error }
        }

        val outcome = if (restOutcome == RestOutcome.SKIPPED) BreakOutcome.SKIPPED_BY_USER else BreakOutcome.COMPLETED
        return BreakReport(
            kind = request.kind, target = target, startedMs = started, endedMs = env.nowMs(), outcome = outcome,
            strategy = strategy, pause = pause, resume = resume, restOutcome = restOutcome, error = failure,
        )
    }

    /** Runs on failure or cancellation; must never throw and must be quick. */
    private suspend fun cleanUp(resumeNow: suspend () -> ResumeResult?, alreadyHandled: Boolean) {
        runCatching { overlay.clear() }
        if (!alreadyHandled) {
            withTimeoutOrNull(CLEANUP_TIMEOUT_MS) { runCatching { resumeNow() } }
        }
    }

    private companion object {
        const val REST_HOLD_MARGIN_MS = 15_000L
        const val CLEANUP_TIMEOUT_MS = 4_000L
    }
}

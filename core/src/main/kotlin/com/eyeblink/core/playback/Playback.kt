package com.eyeblink.core.playback

import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * How a pause can be delivered to a player we do not own. Ordered best-first by [rank].
 *
 * [verification] says how a pause can be *confirmed* afterwards, which is what makes a
 * strategy safe to use. A strategy that cannot confirm anything is never selected.
 */
enum class Strategy(
    val rank: Int,
    val verification: Verification,
    val targeted: Boolean,
    val experimental: Boolean,
    /** Depends on the target app's internals, so a new app version invalidates test results. */
    val versionSensitive: Boolean,
) {
    LSPOSED_HOOK(0, Verification.SESSION_STATE, targeted = true, experimental = false, versionSensitive = true),
    MEDIA_SESSION(1, Verification.SESSION_STATE, targeted = true, experimental = false, versionSensitive = false),
    PRIVILEGED_SESSION(2, Verification.SESSION_STATE, targeted = true, experimental = false, versionSensitive = false),
    MEDIA_KEY(3, Verification.AUDIO_ACTIVITY, targeted = false, experimental = true, versionSensitive = false),
    AUDIO_FOCUS(4, Verification.AUDIO_ACTIVITY, targeted = false, experimental = true, versionSensitive = false),
    A11Y_NODE(5, Verification.AUDIO_ACTIVITY, targeted = true, experimental = true, versionSensitive = true),
    A11Y_GESTURE(6, Verification.AUDIO_ACTIVITY, targeted = true, experimental = true, versionSensitive = true),
    ;

    /**
     * A break may try this strategy on an app where it has not been proven yet. Only strategies that read the
     * player's own state qualify: the coordinator pauses nothing that is not positively playing, checks that the
     * pause landed and resumes only what it paused, so a first attempt can do no harm - and its outcome is what
     * proves (or rules out) the strategy for that app. The rest need a deliberate test first.
     */
    val canTryUnproven: Boolean get() = verification == Verification.SESSION_STATE && !experimental
}

enum class Verification {
    /** The player's own state (and content identity) can be read: precise. */
    SESSION_STATE,

    /** Only "is audio playing" can be observed: coarse, and blind to muted video. */
    AUDIO_ACTIVITY,
}

enum class PlayState { PLAYING, PAUSED, BUFFERING, STOPPED, NONE, UNKNOWN }

/** One reading of a player. */
data class PlaybackSnapshot(
    val state: PlayState,
    /** Opaque fingerprint of what is playing (id/title/duration). Null when the strategy cannot tell. */
    val contentKey: String? = null,
    val positionMs: Long? = null,
    /** Package that owns the media session, when the strategy knows. */
    val sourcePackage: String? = null,
) {
    companion object {
        val Unknown = PlaybackSnapshot(PlayState.UNKNOWN)
    }
}

/**
 * A way of reading and driving one player. Implementations are thin; every safety rule lives
 * in [PlaybackCoordinator] so it is written (and tested) once.
 *
 * `pause` and `resume` send an explicit command, never a toggle - or, where the only
 * mechanism is a toggle (a tap), they are called only when the coordinator has just
 * confirmed the state that makes the toggle correct.
 */
interface PlaybackBackend {
    val strategy: Strategy

    /** Why the last command was not delivered, in words for the person; null when there is nothing to add. */
    val lastProblem: String? get() = null

    suspend fun snapshot(target: String): PlaybackSnapshot

    /** @return false when the command could not even be delivered. */
    suspend fun pause(target: String): Boolean
    suspend fun resume(target: String): Boolean
}

/** Proof that EyeBlink paused a specific player, and what it looked like at the time. */
data class PauseToken(
    val target: String,
    val strategy: Strategy,
    val contentKey: String?,
    val positionMs: Long?,
    val pausedAtMs: Long,
    /** False when the pause command went out but the paused state was never observed in time. */
    val verified: Boolean,
)

enum class RefuseReason {
    EXPIRED, LEFT_APP, CONTENT_CHANGED, POSITION_CHANGED, OTHER_SOURCE, ENDED, STATE_UNKNOWN, USER_TOUCHED,
}

sealed interface PauseResult {
    data class Paused(val token: PauseToken) : PauseResult

    /** Command sent, paused state not seen in time. The coordinator may still reconcile later. */
    data class Unverified(val token: PauseToken) : PauseResult

    /**
     * Nothing was playing (or we cannot tell), so nothing was touched. [saw] is what the player reported:
     * [PlayState.NONE] means that it has no player to read (an app without a media session), [PlayState.UNKNOWN]
     * that it could not be read at all, anything else that the player really was not playing.
     */
    data class NotPlaying(val saw: PlayState) : PauseResult

    /** The pause command was never delivered. */
    data class Failed(val error: String) : PauseResult
}

sealed interface ResumeResult {
    data object Resumed : ResumeResult

    /** No command needed: it is already playing again. */
    data object AlreadyPlaying : ResumeResult

    /** A safety rule says leave playback alone. */
    data class Refused(val reason: RefuseReason) : ResumeResult

    /** We tried and playback did not come back. The user has to press play. */
    data class Failed(val error: String) : ResumeResult
}

/** What the world looks like when we consider resuming. */
data class ResumeEnvironment(
    val foregroundPackage: String?,
    val nowMs: Long,
    val maxHoldMs: Long,
    /** The user touched the screen after we paused. Matters only for unverified pauses. */
    val userTouchedSincePause: Boolean = false,
)

/** The pure resume decision, separated from the I/O so every branch can be tested directly. */
sealed interface ResumeDecision {
    data object Resume : ResumeDecision
    data object NotNeeded : ResumeDecision
    data class Refuse(val reason: RefuseReason) : ResumeDecision

    companion object {
        const val POSITION_TOLERANCE_MS = 2_500L

        fun decide(token: PauseToken, now: PlaybackSnapshot, env: ResumeEnvironment): ResumeDecision {
            if (env.nowMs - token.pausedAtMs > env.maxHoldMs) return Refuse(RefuseReason.EXPIRED)
            if (env.foregroundPackage != token.target) return Refuse(RefuseReason.LEFT_APP)

            return when (now.state) {
                // Someone - usually the user - already started it again. Leave it be.
                PlayState.PLAYING, PlayState.BUFFERING -> NotNeeded
                PlayState.STOPPED, PlayState.NONE -> Refuse(RefuseReason.ENDED)
                PlayState.UNKNOWN -> Refuse(RefuseReason.STATE_UNKNOWN)
                PlayState.PAUSED -> {
                    if (now.sourcePackage != null && now.sourcePackage != token.target) {
                        return Refuse(RefuseReason.OTHER_SOURCE)
                    }
                    if (token.contentKey != null && now.contentKey != null && token.contentKey != now.contentKey) {
                        return Refuse(RefuseReason.CONTENT_CHANGED)
                    }
                    val before = token.positionMs
                    val after = now.positionMs
                    if (before != null && after != null && abs(after - before) > POSITION_TOLERANCE_MS) {
                        return Refuse(RefuseReason.POSITION_CHANGED)
                    }
                    // We never saw our own pause land. If the user touched the screen in the
                    // meantime they may be the one who paused it, so don't undo it.
                    if (!token.verified && env.userTouchedSincePause) return Refuse(RefuseReason.USER_TOUCHED)
                    Resume
                }
            }
        }
    }
}

/**
 * The pause -> verify -> (break) -> check -> resume protocol.
 *
 *  1. Only a player positively reported as PLAYING is ever paused.
 *  2. After pausing, the PAUSED state must be observed; otherwise the result says so honestly.
 *  3. Resuming happens only when EyeBlink holds a token for this very player, it is still paused,
 *     it is still the same content, the user is still in the app, and not too much time has passed.
 *  4. A resume is itself verified, and retried once.
 */
class PlaybackCoordinator(
    private val verifyTimeoutMs: Long = 2_500L,
    private val pollMs: Long = 50L,
    private val resumeVerifyTimeoutMs: Long = 2_500L,
    /** How long an unverified pause may still be "in flight" when the break ends. */
    private val unverifiedGraceMs: Long = 1_500L,
    /** Milliseconds from any fixed point; what a look at the player costs counts against the waiting time. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    suspend fun pause(backend: PlaybackBackend, target: String, nowMs: () -> Long): PauseResult {
        val before = try {
            backend.snapshot(target)
        } catch (e: Exception) {
            return PauseResult.NotPlaying(PlayState.UNKNOWN)
        }
        if (before.state != PlayState.PLAYING) return PauseResult.NotPlaying(before.state)
        if (before.sourcePackage != null && before.sourcePackage != target) return PauseResult.NotPlaying(PlayState.NONE)

        val delivered = try {
            backend.pause(target)
        } catch (e: Exception) {
            return PauseResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (!delivered) return PauseResult.Failed(backend.lastProblem ?: "the pause command was not delivered")

        val pausedAt = nowMs()
        val paused = awaitState(backend, target, PlayState.PAUSED, verifyTimeoutMs)
        val token = PauseToken(
            target = target,
            strategy = backend.strategy,
            contentKey = paused?.contentKey ?: before.contentKey,
            positionMs = paused?.positionMs ?: before.positionMs,
            pausedAtMs = pausedAt,
            verified = paused != null,
        )
        return if (paused != null) PauseResult.Paused(token) else PauseResult.Unverified(token)
    }

    /**
     * @param environment read afresh on every attempt, because the foreground app and the
     *        clock can change between the first try and the retry
     */
    suspend fun resume(backend: PlaybackBackend, token: PauseToken, environment: () -> ResumeEnvironment): ResumeResult {
        // A slow player can take longer to act on a pause than we were willing to wait. If we
        // never saw ours land, give it a moment more before judging what state things are in -
        // otherwise a late pause would strand the video with nobody to resume it.
        if (!token.verified) awaitState(backend, token.target, PlayState.PAUSED, unverifiedGraceMs)

        var sent = false
        repeat(2) { attempt ->
            val now = try {
                backend.snapshot(token.target)
            } catch (e: Exception) {
                return ResumeResult.Failed(e.message ?: "snapshot failed")
            }
            // Re-decide on every attempt: the world may have changed since the first one.
            when (val d = ResumeDecision.decide(token, now, environment())) {
                // Playing again after a command of ours that was too slow to be seen in time is our resume taking.
                ResumeDecision.NotNeeded -> return if (sent) ResumeResult.Resumed else ResumeResult.AlreadyPlaying
                is ResumeDecision.Refuse -> return ResumeResult.Refused(d.reason)
                ResumeDecision.Resume -> Unit
            }

            val delivered = try {
                backend.resume(token.target)
            } catch (e: Exception) {
                return ResumeResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (delivered) sent = true
            if (delivered && awaitState(backend, token.target, PlayState.PLAYING, resumeVerifyTimeoutMs) != null) {
                return ResumeResult.Resumed
            }
            if (attempt == 0) delay(RETRY_GAP_MS)
        }
        return ResumeResult.Failed("playback did not resume")
    }

    private suspend fun awaitState(
        backend: PlaybackBackend,
        target: String,
        wanted: PlayState,
        timeoutMs: Long,
    ): PlaybackSnapshot? {
        var waited = 0L
        while (true) {
            val lookedAt = clock()
            val snap = try {
                backend.snapshot(target)
            } catch (e: Exception) {
                null
            }
            if (snap != null && snap.state == wanted) return snap
            // Looking at a player through a shell can take a good part of a second; that time is waiting time too.
            waited += (clock() - lookedAt).coerceAtLeast(0L)
            if (waited >= timeoutMs) return null
            delay(pollMs)
            waited += pollMs
        }
    }

    private companion object {
        const val RETRY_GAP_MS = 150L
    }
}

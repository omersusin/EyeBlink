package com.eyeblink.core.playback

import kotlinx.coroutines.delay

enum class CompatOutcome {
    PASSED,

    /** The player was there and reported that it was not playing. */
    NOT_PLAYING,

    /** The app has no player this strategy can see (no media session), so it cannot tell what plays. */
    NO_SESSION,

    /** The player could not be read at all (the shell or the session list did not answer). */
    UNREADABLE,
    PAUSE_NOT_VERIFIED,
    RESUME_FAILED,
    REFUSED,
    ERROR,
    ;

    /** The strategy never got to pause anything, for a reason that says nothing against it. */
    val sawNothing: Boolean get() = this == NOT_PLAYING || this == NO_SESSION || this == UNREADABLE
}

data class StrategyTestResult(
    val strategy: Strategy,
    val outcome: CompatOutcome,
    val pauseLatencyMs: Long = 0L,
    val note: String? = null,
) {
    val passed: Boolean get() = outcome == CompatOutcome.PASSED
}

data class CompatReport(
    val target: String,
    val results: List<StrategyTestResult>,
    /** True if the player may be left paused and the user should press play. */
    val leftPaused: Boolean,
) {
    val anyPassed: Boolean get() = results.any { it.passed }
    val sawPlayback: Boolean get() = results.any { !it.outcome.sawNothing }
}

/**
 * The on-device compatibility test: for each strategy, pause a playing video, confirm it
 * paused, wait like a real break would, resume it, confirm it resumed.
 *
 * It runs through [PlaybackCoordinator], i.e. with exactly the safety rules real breaks use.
 * That is the point: a strategy is only ever called supported if it passed the same protocol.
 */
class CompatTester(private val coordinator: PlaybackCoordinator = PlaybackCoordinator()) {

    suspend fun run(
        target: String,
        backends: List<PlaybackBackend>,
        nowMs: () -> Long,
        foregroundPackage: () -> String?,
        holdMs: Long = DEFAULT_HOLD_MS,
        onResult: (StrategyTestResult) -> Unit = {},
    ): CompatReport {
        val results = mutableListOf<StrategyTestResult>()
        var leftPaused = false

        for (backend in backends.sortedBy { it.strategy.rank }) {
            if (leftPaused) break
            val started = nowMs()
            val result = testOne(backend, target, nowMs, foregroundPackage, holdMs, started)
            results += result
            onResult(result)
            if (result.outcome == CompatOutcome.RESUME_FAILED || result.outcome == CompatOutcome.ERROR) {
                // Don't pile more commands onto a player that may now be stuck.
                leftPaused = true
            }
            if (!leftPaused) delay(SETTLE_BETWEEN_MS)
        }
        return CompatReport(target, results, leftPaused)
    }

    private suspend fun testOne(
        backend: PlaybackBackend,
        target: String,
        nowMs: () -> Long,
        foregroundPackage: () -> String?,
        holdMs: Long,
        started: Long,
    ): StrategyTestResult {
        val strategy = backend.strategy
        return try {
            when (val paused = coordinator.pause(backend, target, nowMs)) {
                is PauseResult.NotPlaying -> StrategyTestResult(
                    strategy,
                    when (paused.saw) {
                        PlayState.NONE -> CompatOutcome.NO_SESSION
                        PlayState.UNKNOWN -> CompatOutcome.UNREADABLE
                        else -> CompatOutcome.NOT_PLAYING
                    },
                )
                is PauseResult.Failed -> StrategyTestResult(strategy, CompatOutcome.ERROR, note = paused.error)
                is PauseResult.Unverified -> {
                    // Whatever happened, try to leave the player as we found it.
                    coordinator.resume(backend, paused.token) { envFor(nowMs, foregroundPackage) }
                    StrategyTestResult(strategy, CompatOutcome.PAUSE_NOT_VERIFIED, nowMs() - started)
                }
                is PauseResult.Paused -> {
                    val latency = nowMs() - started
                    delay(holdMs)
                    when (val r = coordinator.resume(backend, paused.token) { envFor(nowMs, foregroundPackage) }) {
                        ResumeResult.Resumed, ResumeResult.AlreadyPlaying ->
                            StrategyTestResult(strategy, CompatOutcome.PASSED, latency)
                        is ResumeResult.Refused ->
                            StrategyTestResult(strategy, CompatOutcome.REFUSED, latency, r.reason.name)
                        is ResumeResult.Failed ->
                            StrategyTestResult(strategy, CompatOutcome.RESUME_FAILED, latency, r.error)
                    }
                }
            }
        } catch (e: Exception) {
            StrategyTestResult(strategy, CompatOutcome.ERROR, note = e.message)
        }
    }

    private fun envFor(nowMs: () -> Long, foreground: () -> String?) =
        ResumeEnvironment(foregroundPackage = foreground(), nowMs = nowMs(), maxHoldMs = TEST_MAX_HOLD_MS)

    companion object {
        const val DEFAULT_HOLD_MS = 1_200L
        const val SETTLE_BETWEEN_MS = 800L
        const val TEST_MAX_HOLD_MS = 30_000L
    }
}

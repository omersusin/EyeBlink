package com.eyeblink.app.core

import com.eyeblink.app.animation.BlinkAnimator
import com.eyeblink.app.context.ContextEngine
import com.eyeblink.app.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlinkController(
    private val scope: CoroutineScope,
    private val configProvider: () -> BlinkConfig,
    private val animator: BlinkAnimator,
    private val playback: PlaybackController,
    private val contextEngine: ContextEngine,
    private val intervalMs: () -> Long = { DEFAULT_INTERVAL_MS },
    private val onBlink: suspend () -> Unit = {},
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                delay(nextInterval())
                if (!configProvider().enabled) continue
                if (!contextEngine.isInteractive()) continue

                // Pause only if we can positively confirm something is playing.
                val pausedByUs = playback.pauseIfPlaying()
                if (pausedByUs && !playback.isPlaying()) {
                    // Verified paused: no content is lost during the interruption.
                    animator.blink()
                    onBlink()
                    delay(POST_PAUSE_SETTLE_MS)
                    if (playback.isStillPaused()) playback.resumeIfPausedByUs()
                } else {
                    // Nothing verifiable to pause/resume -> blink without touching playback.
                    animator.blink()
                    onBlink()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun nextInterval(): Long = intervalMs().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    private companion object {
        const val DEFAULT_INTERVAL_MS = 20_000L
        const val MIN_INTERVAL_MS = 5_000L
        const val MAX_INTERVAL_MS = 600_000L
        const val POST_PAUSE_SETTLE_MS = 120L
    }
}
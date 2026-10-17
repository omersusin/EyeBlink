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
    private val intervalMs: () -> Long = { DEFAULT_BLINK_INTERVAL_MS },
    private val onBlink: suspend () -> Unit = {},
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                delay(clampBlinkInterval(intervalMs()))
                if (!configProvider().enabled) continue
                if (!contextEngine.isInteractive()) continue

                // pauseIfPlaying() returns true only when it issued a pause AND
                // verified playback is now paused. Anything else means we do not
                // know what is playing, so we must not toggle it.
                val pausedByUs = playback.pauseIfPlaying()

                animator.blink()
                onBlink()

                if (pausedByUs) {
                    delay(POST_PAUSE_SETTLE_MS)
                    if (shouldResumePlayback(pausedByUs = true, stillPaused = playback.isStillPaused())) {
                        playback.resumeIfPausedByUs()
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
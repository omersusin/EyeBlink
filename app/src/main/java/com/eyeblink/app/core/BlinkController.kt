package com.eyeblink.app.core

import com.eyeblink.app.context.ContextEngine
import com.eyeblink.app.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlinkController(
    private val scope: CoroutineScope,
    private val config: BlinkConfig,
    private val animator: BlinkAnimator,
    private val playback: PlaybackController,
    private val contextEngine: ContextEngine,
    private val onBlink: suspend () -> Unit,
) {
    private var job: Job? = null
    private var pausedByUs: Boolean = false
    private var lastBlinkMs: Long = 0

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                delay(nextInterval())
                if (!config.enabled) continue
                if (!contextEngine.isInteractive()) continue
                // pause media if playing
                pausedByUs = playback.pauseIfPlaying()
                // blink
                animator.blink()
                onBlink()
                lastBlinkMs = System.currentTimeMillis()
                // resume if we paused
                if (pausedByUs && playback.isPlaying().not()) {
                    delay(50)
                    playback.resumeIfPausedByUs()
                }
                pausedByUs = false
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun nextInterval(): Long {
        val base = 8000L
        val since = System.currentTimeMillis() - lastBlinkMs
        val factor = if (since > base * 2) 0.8f else 1f
        return (base * factor).toLong().coerceIn(3000, 60000)
    }
}
EOF
echo done
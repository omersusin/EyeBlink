package com.eyeblink.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlinkScheduler(
    private val scope: CoroutineScope,
    private val config: BlinkConfig,
    private val onBlink: suspend () -> Unit,
) {
    private var job: Job? = null
    private var baseIntervalMs: Long = 8000
    private var lastBlinkMs: Long = 0

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                delay(nextInterval())
                onBlink()
                lastBlinkMs = System.currentTimeMillis()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun nextInterval(): Long {
        // adaptive: shorten after long gaps, clamp to sane bounds
        val since = System.currentTimeMillis() - lastBlinkMs
        val factor = if (since > baseIntervalMs * 2) 0.8f else 1f
        return (baseIntervalMs * factor).toLong().coerceIn(3000, 60000)
    }
}
EOF
echo done
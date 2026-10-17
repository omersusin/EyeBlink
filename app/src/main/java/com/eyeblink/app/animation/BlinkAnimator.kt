package com.eyeblink.app.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.eyeblink.app.core.BlinkConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BlinkPhase { IDLE, CLOSING, HOLD, OPENING }

class BlinkAnimator(private val config: BlinkConfig) {
    val progress = Animatable(0f) // 0 = open, 1 = fully closed
    var phase: BlinkPhase = BlinkPhase.IDLE
        private set

    suspend fun blink(onPhase: (BlinkPhase) -> Unit = {}) = coroutineScope {
        if (!config.enabled) return@coroutineScope
        if (config.reducedMotion) {
            phase = BlinkPhase.HOLD
            onPhase(phase)
            delay(config.holdMs)
            phase = BlinkPhase.IDLE
            onPhase(phase)
            return@coroutineScope
        }
        phase = BlinkPhase.CLOSING
        onPhase(phase)
        progress.animateTo(1f, tween(config.closeMs.toInt(), easing = FastOutSlowInEasing))
        phase = BlinkPhase.HOLD
        onPhase(phase)
        delay(config.holdMs)
        phase = BlinkPhase.OPENING
        onPhase(phase)
        progress.animateTo(0f, tween(config.openMs.toInt(), easing = LinearEasing))
        phase = BlinkPhase.IDLE
        onPhase(phase)
    }
}
EOF
echo done
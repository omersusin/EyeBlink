package com.eyeblink.app.core

data class BlinkConfig(
    val closeMs: Long = 140,
    val holdMs: Long = 150,
    val openMs: Long = 165,
    val curve: Float = 0.35f,        // eyelid curvature
    val darkenStrength: Float = 0.6f,
    val enabled: Boolean = true,
    val reducedMotion: Boolean = false,
) {
    val totalMs: Long get() = closeMs + holdMs + openMs
}

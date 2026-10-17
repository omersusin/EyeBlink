package com.eyeblink.core.engine

enum class AudioMode { NORMAL, RINGTONE, IN_CALL, IN_COMMUNICATION }

/** Coarse "is something playing" signal; the platform fills it from the best source it has. */
enum class MediaSignal { UNKNOWN, NONE, PLAYING }

enum class Thermal { NONE, LIGHT, MODERATE, SEVERE, CRITICAL }

/**
 * Everything the scheduler is allowed to know about the device at one instant.
 *
 * The platform layer assembles this from the accessibility service, audio manager,
 * sensors and power state. It is a plain value so the scheduling rules can be tested by
 * constructing situations directly - a call, an open keyboard, a swipe in flight - without
 * a device.
 */
data class ContextSnapshot(
    val nowMs: Long,
    val localMinuteOfDay: Int = 12 * 60,
    val screenOn: Boolean = true,
    val deviceLocked: Boolean = false,
    val foregroundPackage: String? = null,
    val audioMode: AudioMode = AudioMode.NORMAL,
    /** A finger is on the screen right now. */
    val touching: Boolean = false,
    val lastTouchMs: Long? = null,
    /** Most recent scroll/swipe event in the foreground app. */
    val lastScrollMs: Long? = null,
    val keyboardVisible: Boolean = false,
    val textInputFocused: Boolean = false,
    /** Another app is using a camera (capture or recording). */
    val cameraBusyElsewhere: Boolean = false,
    val navigationActive: Boolean = false,
    /** A password field or a known authentication/payment surface is on screen. */
    val sensitiveScreen: Boolean = false,
    val media: MediaSignal = MediaSignal.UNKNOWN,
    val ambientLux: Float? = null,
    val charging: Boolean = false,
    val batteryPercent: Int = 100,
    val powerSave: Boolean = false,
    val thermal: Thermal = Thermal.NONE,
) {
    fun sinceTouchMs(): Long? = lastTouchMs?.let { (nowMs - it).coerceAtLeast(0) }
    fun sinceScrollMs(): Long? = lastScrollMs?.let { (nowMs - it).coerceAtLeast(0) }
}

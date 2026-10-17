package com.eyeblink.app.core

/**
 * Pure interruption rules, deliberately free of Android imports so they can be
 * unit tested without Robolectric or a device.
 *
 * These encode two safety promises from the spec:
 *  - interruptions stay within sane bounds (no hammering the user);
 *  - playback is only resumed when EyeBlink paused it AND it is still paused,
 *    so a pause the user performed is never undone.
 */

internal const val DEFAULT_BLINK_INTERVAL_MS = 20_000L
internal const val MIN_BLINK_INTERVAL_MS = 5_000L
internal const val MAX_BLINK_INTERVAL_MS = 600_000L
internal const val POST_PAUSE_SETTLE_MS = 120L

/** Coerces a requested interval into the allowed range. */
internal fun clampBlinkInterval(ms: Long): Long = ms.coerceIn(MIN_BLINK_INTERVAL_MS, MAX_BLINK_INTERVAL_MS)

/**
 * Resume only when we were the ones who paused it and it is still paused.
 * Any other combination must leave playback alone.
 */
internal fun shouldResumePlayback(pausedByUs: Boolean, stillPaused: Boolean): Boolean =
    pausedByUs && stillPaused
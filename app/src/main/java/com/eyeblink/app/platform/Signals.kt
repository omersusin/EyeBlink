package com.eyeblink.app.platform

import com.eyeblink.app.access.EyeBlinkAccessibilityService

/**
 * What the system has told EyeBlink lately, written by the accessibility service and the
 * notification listener and read by the engine.
 *
 * Plain volatile fields on purpose: every value is a "latest known" reading, the writers are
 * event callbacks on the main thread and the reader is a polling loop, and a torn read of a
 * single `Long` or reference is not possible on the JVM for volatiles.
 *
 * Nothing here ever holds content. Package names, class names and timestamps are all that is
 * kept: the service reads nothing else from the events it is sent.
 */
object Signals {

    /** The app in front, as far as window events can tell. */
    @Volatile var foregroundPackage: String? = null
        private set
    @Volatile var foregroundSinceMs: Long = 0L
        private set

    /** When a finger last went down anywhere on screen (from the outside-touch sentinel). */
    @Volatile var lastTouchDownMs: Long? = null
    /** When the foreground app last scrolled, or something transient (the shade) appeared. */
    @Volatile var lastScrollMs: Long? = null

    @Volatile var keyboardUntilMs: Long = 0L
    @Volatile var sensitiveUntilMs: Long = 0L

    /** While EyeBlink's own windows are on screen, events from its own package are ignored. */
    @Volatile var ownWindowsUntilMs: Long = 0L

    @Volatile var uiVisible: Boolean = false

    @Volatile var accessibility: EyeBlinkAccessibilityService? = null
    @Volatile var listenerConnected: Boolean = false
    @Volatile var navigationActive: Boolean = false

    /** @param ownPackage this app's package (it differs for the debug build) */
    fun onWindowState(ownPackage: String, pkg: String, cls: String?, now: Long) {
        if (pkg == ownPackage && !uiVisible && now < ownWindowsUntilMs) return // our own overlay, not a switch of app
        when {
            // The keyboard is a window of its own; it is not "the app in front".
            cls == IME_WINDOW || pkg.contains("inputmethod", ignoreCase = true) -> {
                keyboardUntilMs = now + KEYBOARD_TTL_MS
            }
            // The notification shade, volume panel and similar: treat as a transition, not a switch.
            pkg == "com.android.systemui" && cls?.contains("recents", ignoreCase = true) != true -> {
                lastScrollMs = now
            }
            else -> {
                if (pkg != foregroundPackage) {
                    foregroundPackage = pkg
                    foregroundSinceMs = now
                    keyboardUntilMs = 0L
                    sensitiveUntilMs = 0L
                }
            }
        }
    }

    fun onFocus(cls: String?, isPassword: Boolean, now: Long) {
        if (cls != null && cls.contains("EditText")) keyboardUntilMs = now + KEYBOARD_TTL_MS
        if (isPassword) sensitiveUntilMs = now + SENSITIVE_TTL_MS
    }

    fun onScroll(now: Long) {
        lastScrollMs = now
    }

    fun onTouchDown(now: Long) {
        lastTouchDownMs = now
    }

    /** Called when the usage-stats fallback learns the foreground app and accessibility has not. */
    fun setForegroundFallback(pkg: String, now: Long) {
        if (pkg != foregroundPackage) {
            foregroundPackage = pkg
            foregroundSinceMs = now
        }
    }

    fun clearAll() {
        foregroundPackage = null
        lastTouchDownMs = null
        lastScrollMs = null
        keyboardUntilMs = 0L
        sensitiveUntilMs = 0L
    }

    private const val IME_WINDOW = "android.inputmethodservice.SoftInputWindow"

    /** A keyboard that has not been mentioned for this long is assumed gone, so a missed event cannot block breaks forever. */
    const val KEYBOARD_TTL_MS = 20_000L
    const val SENSITIVE_TTL_MS = 30_000L
}

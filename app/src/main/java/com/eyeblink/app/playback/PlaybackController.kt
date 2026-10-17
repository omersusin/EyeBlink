package com.eyeblink.app.playback

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log

/**
 * Best-effort external playback control.
 *
 * `getActiveSessions(null)` returns non-empty only for callers holding
 * MEDIA_CONTENT_CONTROL (or a notification-listener grant). When it returns
 * empty we report "unknown" rather than guessing, and callers must not send
 * blind toggles.
 */
class PlaybackController(private val context: Context) {

    private val sessionManager: MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val controller: MediaController? get() = try {
        sessionManager?.getActiveSessions(null)?.firstOrNull { it.isActive }
    } catch (e: SecurityException) {
        Log.w(TAG, "no MEDIA_CONTENT_CONTROL grant: ${e.message}")
        null
    }

    /** True only when a session positively reports playing. */
    fun isPlaying(): Boolean =
        controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    /** True only when a session positively reports paused. */
    fun isStillPaused(): Boolean =
        controller?.playbackState?.state == PlaybackState.STATE_PAUSED

    /**
     * Pause the active session, but only when state is known to be PLAYING.
     * @return true if we issued a pause and playback is now verified paused.
     */
    fun pauseIfPlaying(): Boolean {
        val c = controller ?: return false
        if (c.playbackState?.state != PlaybackState.STATE_PLAYING) return false
        c.transportControls.pause()
        // Verification is a separate read; allow the player a moment to settle.
        repeat(VERIFY_ATTEMPTS) {
            if (isStillPaused()) return true
            Thread.sleep(VERIFY_INTERVAL_MS)
        }
        return isStillPaused()
    }

    /** Resume, but only when the session is still paused (never stomp a user-initiated stop). */
    fun resumeIfPausedByUs(): Boolean {
        val c = controller ?: return false
        if (c.playbackState?.state != PlaybackState.STATE_PAUSED) return false
        c.transportControls.play()
        return true
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val VERIFY_ATTEMPTS = 5
        const val VERIFY_INTERVAL_MS = 40L
    }
}
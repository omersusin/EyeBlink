package com.eyeblink.app.playback

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

class PlaybackController(private val context: Context) {
    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    fun activeController(): MediaController? = try {
        sessionManager.getActiveSessions(null).firstOrNull()
    } catch (e: SecurityException) { null }

    fun pauseIfPlaying(): Boolean {
        val c = activeController() ?: return false
        val state = c.playbackState?.state
        return if (state == android.media.session.PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
            true
        } else false
    }

    fun resumeIfPausedByUs(): Boolean {
        val c = activeController() ?: return false
        c.transportControls.play()
        return true
    }

    fun isPlaying(): Boolean =
        activeController()?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
}

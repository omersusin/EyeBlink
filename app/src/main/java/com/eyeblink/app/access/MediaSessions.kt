package com.eyeblink.app.access

import com.eyeblink.app.platform.systemService
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.eyeblink.app.platform.Access

/** Read access to other apps' media sessions, available once the user grants notification access. */
class MediaSessions(private val ctx: Context) {

    private val manager = ctx.systemService<MediaSessionManager>()

    /** Every active session. Empty - not an error - without the permission. */
    fun all(): List<MediaController> = try {
        manager.getActiveSessions(Access.listenerComponent(ctx))
    } catch (e: Exception) {
        emptyList()
    }

    /** The session of [pkg] that is playing, or else its first one. */
    fun of(pkg: String): MediaController? {
        val mine = all().filter { it.packageName == pkg }
        return mine.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: mine.firstOrNull()
    }

    /** True/false when the foreground app has a session; null when it has none, so nothing can be said. */
    fun isPlaying(pkg: String): Boolean? {
        val c = of(pkg) ?: return null
        return c.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    companion object {
        /** Where a playing session's position is now, from its last report. */
        fun positionOf(ps: PlaybackState): Long {
            if (ps.state != PlaybackState.STATE_PLAYING) return ps.position
            val since = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
            return ps.position + (since * ps.playbackSpeed).toLong()
        }
    }
}

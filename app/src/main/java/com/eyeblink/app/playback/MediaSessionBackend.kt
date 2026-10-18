package com.eyeblink.app.playback

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.eyeblink.app.access.MediaSessions
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackSnapshot
import com.eyeblink.core.playback.Strategy

/**
 * Explicit pause and play through the target app's own media session, read and verified from the
 * session's reported state. This is the precise strategy: it names the player, it sends a command
 * rather than a toggle, and it can tell what content is playing. It works only for apps that
 * publish a media session at all - many short-video apps do not.
 */
class MediaSessionBackend(private val sessions: MediaSessions) : PlaybackBackend {

    override val strategy: Strategy = Strategy.MEDIA_SESSION

    override suspend fun snapshot(target: String): PlaybackSnapshot {
        val controller = sessions.of(target) ?: return PlaybackSnapshot(PlayState.NONE)
        val ps = controller.playbackState
        return PlaybackSnapshot(
            state = stateOf(ps),
            contentKey = fingerprint(controller.metadata),
            positionMs = ps?.let { MediaSessions.positionOf(it) },
            sourcePackage = controller.packageName,
        )
    }

    override suspend fun pause(target: String): Boolean {
        val controller = sessions.of(target) ?: return false
        controller.transportControls.pause()
        return true
    }

    override suspend fun resume(target: String): Boolean {
        val controller = sessions.of(target) ?: return false
        controller.transportControls.play()
        return true
    }

    internal companion object {
        fun stateOf(ps: PlaybackState?): PlayState = when (ps?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> PlayState.PLAYING
            PlaybackState.STATE_PAUSED -> PlayState.PAUSED
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
            -> PlayState.BUFFERING
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR -> PlayState.STOPPED
            PlaybackState.STATE_NONE, null -> PlayState.NONE
            else -> PlayState.UNKNOWN
        }

        /**
         * An opaque fingerprint of the current item: enough to notice "you changed videos", and
         * not reversible to a title. It is held in memory for the length of one break.
         */
        fun fingerprint(md: MediaMetadata?): String? {
            md ?: return null
            val parts = listOfNotNull(
                md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                md.getString(MediaMetadata.METADATA_KEY_TITLE),
                md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                md.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }?.toString(),
            )
            return if (parts.isEmpty()) null else parts.joinToString("|").hashCode().toUInt().toString(16)
        }
    }
}

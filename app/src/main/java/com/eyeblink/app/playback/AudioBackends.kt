package com.eyeblink.app.playback

import com.eyeblink.app.platform.systemService
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.KeyEvent
import com.eyeblink.app.access.EyeBlinkAccessibilityService
import com.eyeblink.app.platform.Signals
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackSnapshot
import com.eyeblink.core.playback.Strategy

/*
 * Experimental strategies. They all share one weakness: the only thing that can confirm them is
 * "is audio playing", which says nothing about a muted video and cannot tell which app is making
 * the sound. So they are off until the user switches them on, they are tried only on an app whose
 * compatibility test passed with them, and the coordinator still refuses to pause anything that is
 * not positively playing.
 */

private fun AudioManager.audioSnapshot() =
    PlaybackSnapshot(if (isMusicActive) PlayState.PLAYING else PlayState.PAUSED)

/** Explicit PAUSE and PLAY media keys. They go to whichever session currently owns the media buttons. */
class MediaKeyBackend(ctx: Context) : PlaybackBackend {

    private val audio = ctx.systemService<AudioManager>()
    override val strategy: Strategy = Strategy.MEDIA_KEY

    override suspend fun snapshot(target: String): PlaybackSnapshot = audio.audioSnapshot()
    override suspend fun pause(target: String): Boolean = key(KeyEvent.KEYCODE_MEDIA_PAUSE)
    override suspend fun resume(target: String): Boolean = key(KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun key(code: Int): Boolean {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }
}

/**
 * Asks for transient audio focus, which well-behaved players answer by pausing themselves, and
 * gives it back afterwards, which they answer by resuming. Nothing is sent to the player at all.
 */
class AudioFocusBackend(ctx: Context) : PlaybackBackend {

    private val audio = ctx.systemService<AudioManager>()
    private var request: AudioFocusRequest? = null
    override val strategy: Strategy = Strategy.AUDIO_FOCUS

    override suspend fun snapshot(target: String): PlaybackSnapshot = audio.audioSnapshot()

    override suspend fun pause(target: String): Boolean {
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        request = r
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override suspend fun resume(target: String): Boolean {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
        return true
    }
}

/**
 * A tap in the middle of the screen, which most feed players treat as pause and play. It is a
 * toggle, so it is only ever sent right after the coordinator has seen the state that makes it
 * correct, and the overlay is already gone before the resume tap goes out.
 */
class GestureTapBackend(private val ctx: Context) : PlaybackBackend {

    private val audio = ctx.systemService<AudioManager>()
    override val strategy: Strategy = Strategy.A11Y_GESTURE

    override suspend fun snapshot(target: String): PlaybackSnapshot = audio.audioSnapshot()
    override suspend fun pause(target: String): Boolean = tap()
    override suspend fun resume(target: String): Boolean = tap()

    private suspend fun tap(): Boolean {
        val service: EyeBlinkAccessibilityService = Signals.accessibility ?: return false
        val m = ctx.resources.displayMetrics
        return service.tap(m.widthPixels / 2f, m.heightPixels * 0.42f)
    }
}

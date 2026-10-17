package com.eyeblink.core.privileged

import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackSnapshot
import com.eyeblink.core.playback.Strategy

/**
 * Playback control through a privileged shell: read every media session with
 * `dumpsys media_session`, deliver explicit PAUSE/PLAY keys with `cmd media_session dispatch`.
 *
 * Media keys go to whichever session currently holds the media button, not to a named app. So
 * a command is sent only when the dump says the *target's* session is that session; otherwise
 * `pause`/`resume` report "not delivered" and the coordinator treats it as a failure instead
 * of risking another app's playback.
 */
class PrivilegedPlaybackBackend(private val shell: ShellExecutor) : PlaybackBackend {

    override val strategy: Strategy = Strategy.PRIVILEGED_SESSION

    override suspend fun snapshot(target: String): PlaybackSnapshot {
        val dump = readDump() ?: return PlaybackSnapshot.Unknown
        val session = dump.forPackage(target) ?: return PlaybackSnapshot(PlayState.NONE)
        return PlaybackSnapshot(
            state = session.state,
            contentKey = session.contentKey,
            positionMs = session.positionMs,
            sourcePackage = session.packageName,
        )
    }

    override suspend fun pause(target: String): Boolean = dispatch(target, MediaKey.PAUSE)

    override suspend fun resume(target: String): Boolean = dispatch(target, MediaKey.PLAY)

    private suspend fun dispatch(target: String, key: MediaKey): Boolean {
        val dump = readDump() ?: return false
        // Never send keys to a session that is not the target's: they would land on someone else.
        if (dump.mediaButtonPackage != null && dump.mediaButtonPackage != target) return false
        if (dump.forPackage(target) == null) return false
        for (command in PrivilegedCommands.dispatchMediaKey(key)) {
            if (shell.run(command, DISPATCH_TIMEOUT_MS).ok) return true
        }
        return false
    }

    private suspend fun readDump(): MediaDump? {
        val r = shell.run(PrivilegedCommands.DUMP_MEDIA_SESSIONS, DUMP_TIMEOUT_MS)
        if (!r.ok || r.output.isBlank()) return null
        return MediaSessionDump.parse(r.output)
    }

    private companion object {
        const val DUMP_TIMEOUT_MS = 4_000L
        const val DISPATCH_TIMEOUT_MS = 3_000L
    }
}

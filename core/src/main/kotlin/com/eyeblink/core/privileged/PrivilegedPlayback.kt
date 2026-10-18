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
 * a command is sent only when the dump says the *target's* session is that session - or, where
 * the dump does not name the holder (the wording changes between Android versions), when no other
 * app is playing, since the key goes to the session that played last. Otherwise `pause`/`resume`
 * report "not delivered" and the coordinator treats it as a failure instead of risking another
 * app's playback. [lastProblem] says which of those it was.
 */
class PrivilegedPlaybackBackend(
    private val shell: ShellExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaybackBackend {

    override val strategy: Strategy = Strategy.PRIVILEGED_SESSION

    private var problem: String? = null
    override val lastProblem: String? get() = problem

    private var cached: MediaDump? = null
    private var cachedAtMs = 0L

    override suspend fun snapshot(target: String): PlaybackSnapshot {
        val dump = readDump() ?: return PlaybackSnapshot.Unknown
        val session = dump.forPackage(target) ?: return PlaybackSnapshot(PlayState.NONE)
        return PlaybackSnapshot(
            state = session.state,
            contentKey = session.contentKey,
            // The dump's position is where the player was when it last reported, not where it is now; for a playing
            // session it is already seconds old and says nothing a later reading could be compared with.
            positionMs = session.positionMs.takeUnless { session.state == PlayState.PLAYING },
            sourcePackage = session.packageName,
        )
    }

    override suspend fun pause(target: String): Boolean = dispatch(target, MediaKey.PAUSE)

    override suspend fun resume(target: String): Boolean = dispatch(target, MediaKey.PLAY)

    private suspend fun dispatch(target: String, key: MediaKey): Boolean {
        problem = null
        // The dump read a moment ago for the snapshot is as good as a new one; after the key has gone out it is not.
        val dump = readDump(maxAgeMs = REUSE_MS)
        if (dump == null) {
            problem = "the phone's media sessions could not be read through the privileged shell"
            return false
        }
        if (dump.forPackage(target) == null) {
            problem = "this app has no media session"
            return false
        }
        // Never send keys to a session that is not the target's: they would land on someone else.
        if (dump.mediaButtonPackage != null && dump.mediaButtonPackage != target) {
            problem = "media keys currently go to a different app than the one in front, so a key would not reach this one"
            return false
        }
        if (dump.mediaButtonPackage == null && dump.sessions.any { it.packageName != target && it.state == PlayState.PLAYING }) {
            problem = "the phone does not say which app receives media keys and another app is playing, so a key could reach that one instead"
            return false
        }
        var lastError = ""
        for (command in PrivilegedCommands.dispatchMediaKey(key)) {
            val r = shell.run(command, DISPATCH_TIMEOUT_MS)
            if (r.ok) {
                cached = null
                return true
            }
            lastError = if (r.timedOut) "timed out" else r.output.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "exit ${r.exitCode}"
        }
        problem = "the ${key.arg} key could not be sent ($lastError)"
        return false
    }

    /** The current media sessions, or null when the shell could not list them. */
    suspend fun sessions(): MediaDump? = readDump()

    /** A copy of the raw dump that is safe to put in a bug report, or null when it could not be read. */
    suspend fun outline(): String? {
        val r = shell.run(PrivilegedCommands.DUMP_MEDIA_SESSIONS, DUMP_TIMEOUT_MS)
        if (!r.ok || r.output.isBlank()) return null
        return MediaSessionDump.outline(r.output)
    }

    private suspend fun readDump(maxAgeMs: Long = 0L): MediaDump? {
        if (maxAgeMs > 0) cached?.let { if (clock() - cachedAtMs <= maxAgeMs) return it }
        val r = shell.run(PrivilegedCommands.DUMP_MEDIA_SESSIONS, DUMP_TIMEOUT_MS)
        if (!r.ok || r.output.isBlank()) {
            cached = null
            return null
        }
        return MediaSessionDump.parse(r.output).also {
            cached = it
            cachedAtMs = clock()
        }
    }

    private companion object {
        const val DUMP_TIMEOUT_MS = 4_000L
        const val DISPATCH_TIMEOUT_MS = 3_000L
        const val REUSE_MS = 400L
    }
}

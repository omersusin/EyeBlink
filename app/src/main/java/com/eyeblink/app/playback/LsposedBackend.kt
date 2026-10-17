package com.eyeblink.app.playback

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.eyeblink.app.system.LsposedReplyReceiver
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackSnapshot
import com.eyeblink.core.playback.Strategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Talks to the optional EyeBlink LSPosed module, which runs inside the target app and can pause the
 * players it created itself.
 *
 * The conversation is deliberately narrow. EyeBlink broadcasts a command to one package; the module's
 * receiver, which only accepts senders holding a signature-level permission, answers through the
 * explicit [PendingIntent] that came with the command. Nothing else is exposed, and the answer can
 * only ever reach EyeBlink's own non-exported receiver.
 *
 * A pause is still verified from the state the module reports afterwards, exactly like every other
 * strategy; the module is trusted to report, not to be right.
 */
class LsposedBackend(private val app: Application) : PlaybackBackend {

    override val strategy: Strategy = Strategy.LSPOSED_HOOK

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Bundle>>()

    /** Set once any hooked app has answered, which is the only honest sign that the module is active. */
    @Volatile var everReplied: Boolean = false
        private set

    fun installed(): Boolean = try {
        app.packageManager.getPackageInfo(MODULE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Called by [LsposedReplyReceiver] with whatever extras the module sent back. */
    fun onReply(extras: Bundle?) {
        extras ?: return
        everReplied = true
        pending.remove(extras.getString(EXTRA_NONCE))?.complete(extras)
    }

    override suspend fun snapshot(target: String): PlaybackSnapshot {
        val r = ask(target, CMD_QUERY) ?: return PlaybackSnapshot.Unknown
        val state = when (r.getString(EXTRA_STATE)) {
            "PLAYING" -> PlayState.PLAYING
            "PAUSED" -> PlayState.PAUSED
            "NONE" -> PlayState.NONE
            else -> PlayState.UNKNOWN
        }
        return PlaybackSnapshot(
            state = state,
            contentKey = if (r.containsKey(EXTRA_CONTENT)) r.getInt(EXTRA_CONTENT).toUInt().toString(16) else null,
            positionMs = if (r.containsKey(EXTRA_POSITION)) r.getLong(EXTRA_POSITION) else null,
            sourcePackage = target,
        )
    }

    override suspend fun pause(target: String): Boolean = ask(target, CMD_PAUSE) != null
    override suspend fun resume(target: String): Boolean = ask(target, CMD_PLAY) != null

    private suspend fun ask(target: String, command: String): Bundle? {
        val nonce = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Bundle>()
        pending[nonce] = deferred
        try {
            val reply = PendingIntent.getBroadcast(
                app, nonce.hashCode(),
                Intent(ACTION_REPLY).setClass(app, LsposedReplyReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            app.sendBroadcast(
                Intent(ACTION_COMMAND).setPackage(target)
                    .putExtra(EXTRA_COMMAND, command).putExtra(EXTRA_NONCE, nonce).putExtra(EXTRA_REPLY, reply),
            )
            return withTimeoutOrNull(REPLY_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(nonce)
        }
    }

    companion object {
        const val MODULE_PACKAGE = "com.eyeblink.lsposed"

        // These strings are the whole protocol. The module has its own copy; keep them in step.
        const val ACTION_COMMAND = "com.eyeblink.lsposed.COMMAND"
        const val ACTION_REPLY = "com.eyeblink.action.LSPOSED_REPLY"
        const val PERMISSION = "com.eyeblink.lsposed.permission.BRIDGE"
        const val EXTRA_COMMAND = "cmd"
        const val EXTRA_NONCE = "nonce"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_STATE = "state"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_POSITION = "pos"
        const val CMD_QUERY = "query"
        const val CMD_PAUSE = "pause"
        const val CMD_PLAY = "play"

        private const val REPLY_TIMEOUT_MS = 700L
    }
}

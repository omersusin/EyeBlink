package com.eyeblink.core.playback

/**
 * A scriptable player for tests. State changes take effect after [latencyMs] of *virtual*
 * time, which lets tests model slow players and races deterministically.
 */
class FakePlayer(
    private val clock: () -> Long,
    override val strategy: Strategy = Strategy.MEDIA_SESSION,
    var state: PlayState = PlayState.PLAYING,
    var content: String? = "video-1",
    var position: Long = 10_000L,
    var source: String? = null,
    /** Delay before a pause command takes effect. */
    var latencyMs: Long = 0L,
    /** Delay before a play command takes effect. */
    var playLatencyMs: Long = 0L,
) : PlaybackBackend {

    var obeysPause = true
    var obeysPlay = true
    var deliversCommands = true
    var snapshotThrows = false
    var commandThrows = false
    var reportsUnknown = false

    val commands = mutableListOf<String>()

    private var pendingAt: Long? = null
    private var pendingState: PlayState? = null

    private fun settle() {
        val at = pendingAt ?: return
        if (clock() >= at) {
            state = pendingState!!
            pendingAt = null
        }
    }

    override suspend fun snapshot(target: String): PlaybackSnapshot {
        if (snapshotThrows) error("snapshot boom")
        settle()
        if (reportsUnknown) return PlaybackSnapshot.Unknown
        return PlaybackSnapshot(state, content, position, source)
    }

    override suspend fun pause(target: String): Boolean {
        if (commandThrows) error("command boom")
        commands += "pause"
        if (!deliversCommands) return false
        if (obeysPause) schedule(PlayState.PAUSED, latencyMs)
        return true
    }

    override suspend fun resume(target: String): Boolean {
        if (commandThrows) error("command boom")
        commands += "play"
        if (!deliversCommands) return false
        if (obeysPlay) schedule(PlayState.PLAYING, playLatencyMs)
        return true
    }

    private fun schedule(next: PlayState, delayMs: Long) {
        pendingAt = clock() + delayMs
        pendingState = next
        settle()
    }

    /** The user does something while the break is showing. */
    fun userPlays() { pendingAt = null; state = PlayState.PLAYING }
    fun userSwipesToNextVideo() { content = "video-2"; position = 0 }
    fun userScrubs(to: Long) { position = to }
}

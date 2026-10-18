package com.eyeblink.core.playback

/**
 * Remembers, app by app, how many breaks in a row found no player to read at all (the app shows no media session). An app
 * that never shows one - a short-video feed, say - can never be paused through a media session, however often it is tried,
 * and treating it as "will try at the next break" for ever would keep the scheduler from handling it as the app that cannot
 * be paused that it is (waiting for a swipe, or skipping it, as the person chose).
 *
 * The memory is short and forgets on its own: after [memoryMs], and whenever the app is updated or the ways EyeBlink can
 * read players change (a permission was granted, root came up), because either can change the answer. Breaks keep trying
 * regardless - one that does find a player ends the blindness at once.
 */
class BlindTracker(
    private val limit: Int = DEFAULT_LIMIT,
    private val memoryMs: Long = DEFAULT_MEMORY_MS,
) {
    private class Entry(var misses: Int, var lastMs: Long, var key: String)

    private val entries = HashMap<String, Entry>()

    /** A break found no player for [target]. [key] names what could change the answer (the app's version, the ways to read). */
    fun sawNoPlayer(target: String, nowMs: Long, key: String) {
        val e = entries[target]
        if (e == null || e.key != key || nowMs - e.lastMs > memoryMs) {
            entries[target] = Entry(1, nowMs, key)
        } else {
            e.misses++
            e.lastMs = nowMs
        }
    }

    /** A break (or a test) found a player for [target], whatever state it was in: the app is not blind. */
    fun sawPlayer(target: String) {
        entries.remove(target)
    }

    /** The app is known to show no player: seen so [limit] times in a row, recently, under the same [key]. */
    fun isBlind(target: String, nowMs: Long, key: String): Boolean {
        val e = entries[target] ?: return false
        return e.key == key && e.misses >= limit && nowMs - e.lastMs <= memoryMs
    }

    /** A test showed no player in every way that reads players: blind at once, without waiting for [limit] breaks. */
    fun markBlind(target: String, nowMs: Long, key: String) {
        entries[target] = Entry(limit, nowMs, key)
    }

    fun forget(target: String) {
        entries.remove(target)
    }

    companion object {
        const val DEFAULT_LIMIT = 3
        const val DEFAULT_MEMORY_MS = 12 * 3_600_000L
    }
}

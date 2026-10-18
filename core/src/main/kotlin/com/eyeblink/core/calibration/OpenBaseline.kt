package com.eyeblink.core.calibration

import java.util.Arrays

/**
 * What "open" looks like for one eye, right now.
 *
 * A person's open eye is not a constant: it is wider on a bright screen than in a dim room, narrower
 * when they look down at a phone than when they look straight ahead, and different from one face to the
 * next by a factor of two. So the engine never compares an eye with a fixed number. It keeps the last few
 * seconds of readings and takes a high percentile of them: blinks and squints are brief dips below that
 * level, so they barely move it, while a slow change (looking down, tired lids) carries it along within a
 * few seconds.
 *
 * Only readings from open eyes are added; the detector leaves out the ones that belong to a closure.
 *
 * Not thread safe. Holds no Android types.
 */
class OpenBaseline(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val percentile: Float = DEFAULT_PERCENTILE,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val times = LongArray(capacity)
    private val values = FloatArray(capacity)
    private val scratch = FloatArray(capacity)
    private var head = 0
    private var size = 0

    private var cached = 0f
    private var cachedFresh = false

    /** A level known from an earlier session, used until this one has gathered readings of its own. */
    var seed: Float? = null

    /**
     * The last level the readings themselves made. When they run out - the camera slept for longer than the window, as it does
     * between two bursts - this is the best guess there is, better than a [seed] from a calibration made hours ago.
     */
    private var last: Float? = null

    /** How many readings the window holds. */
    val count: Int get() = size

    fun add(timeMs: Long, value: Float) {
        if (!value.isFinite()) return
        times[head] = timeMs
        values[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
        cachedFresh = false
    }

    /**
     * The open level at [nowMs]: the percentile of the readings of the last window; while there are too few of them the last
     * level they made, or the [seed]; or null when none of these exists yet.
     */
    fun level(nowMs: Long): Float? {
        evict(nowMs)
        if (size < MIN_SAMPLES) return last ?: seed
        if (cachedFresh) return cached
        val oldest = (head - size + capacity) % capacity
        for (i in 0 until size) scratch[i] = values[(oldest + i) % capacity]
        Arrays.sort(scratch, 0, size)
        cached = scratch[((size - 1) * percentile).toInt().coerceIn(0, size - 1)]
        cachedFresh = true
        last = cached
        return cached
    }

    /** Forgets every reading. The seed and the last level are dropped as well: whatever they described, they no longer do. */
    fun clear() {
        size = 0
        head = 0
        seed = null
        last = null
        cachedFresh = false
    }

    private fun evict(nowMs: Long) {
        var oldest = (head - size + capacity) % capacity
        var dropped = false
        while (size > 0 && nowMs - times[oldest] > windowMs) {
            oldest = (oldest + 1) % capacity
            size--
            dropped = true
        }
        if (dropped) cachedFresh = false
    }

    companion object {
        /** How far back the open level looks. Long enough to ride out blinks, short enough to follow a change of gaze. */
        const val DEFAULT_WINDOW_MS = 8_000L

        /** Open eyes are the top of what an eye does; blinks and squints are dips below it. */
        const val DEFAULT_PERCENTILE = 0.80f

        /** Room for the whole window at up to 60 frames per second (8 s x 60 = 480); beyond that the oldest reading is overwritten. */
        const val DEFAULT_CAPACITY = 512

        /** Fewer readings than this say nothing about a level. */
        const val MIN_SAMPLES = 8
    }
}

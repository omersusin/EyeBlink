package com.eyeblink.core.perf

/**
 * Optional screen-content analysis (the MediaProjection research feature) reduced to the
 * only thing it is for: noticing that what is on screen changed a lot, e.g. a jump to the
 * next video. It works on tiny luma thumbnails and keeps nothing but a 64-bit fingerprint, so
 * no screen content is ever stored.
 */
object FrameFingerprint {

    const val WIDTH = 9
    const val HEIGHT = 8

    /**
     * Difference hash of a luma image of size [w] x [h]: downsample to 9x8 by box averaging,
     * then one bit per horizontally adjacent pair, set when the left pixel is brighter.
     */
    fun of(luma: ByteArray, w: Int, h: Int): Long {
        require(w > 0 && h > 0 && luma.size >= w * h) { "luma buffer too small" }
        val cells = IntArray(WIDTH * HEIGHT)
        for (cy in 0 until HEIGHT) {
            for (cx in 0 until WIDTH) {
                val x0 = cx * w / WIDTH
                val x1 = maxOf(x0 + 1, (cx + 1) * w / WIDTH)
                val y0 = cy * h / HEIGHT
                val y1 = maxOf(y0 + 1, (cy + 1) * h / HEIGHT)
                var sum = 0L
                var n = 0
                for (y in y0 until minOf(y1, h)) for (x in x0 until minOf(x1, w)) {
                    sum += luma[y * w + x].toInt() and 0xFF
                    n++
                }
                cells[cy * WIDTH + cx] = if (n == 0) 0 else (sum / n).toInt()
            }
        }
        var bits = 0L
        var i = 0
        for (cy in 0 until HEIGHT) for (cx in 0 until WIDTH - 1) {
            if (cells[cy * WIDTH + cx] > cells[cy * WIDTH + cx + 1]) bits = bits or (1L shl i)
            i++
        }
        return bits
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

class ContentChangeDetector(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {
    private var last: Long? = null
    private var lastChangeMs: Long? = null

    /** @return true when this frame differs enough from the previous one to count as a content change. */
    fun onFrame(fingerprint: Long, nowMs: Long): Boolean {
        val prev = last
        last = fingerprint
        if (prev == null) return false
        if (FrameFingerprint.distance(prev, fingerprint) < threshold) return false
        // Nullable rather than a MIN_VALUE sentinel: subtracting from Long.MIN_VALUE overflows.
        lastChangeMs?.let { if (nowMs - it < minGapMs) return false }
        lastChangeMs = nowMs
        return true
    }

    fun reset() {
        last = null
        lastChangeMs = null
    }

    companion object {
        const val DEFAULT_THRESHOLD = 18
        const val DEFAULT_MIN_GAP_MS = 1_500L
    }
}

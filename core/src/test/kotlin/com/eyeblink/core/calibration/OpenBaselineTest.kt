package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenBaselineTest {

    private fun OpenBaseline.feed(fromMs: Long, toMs: Long, stepMs: Long = 33, value: (Long) -> Float) {
        var t = fromMs
        while (t < toMs) { add(t, value(t)); t += stepMs }
    }

    @Test
    fun `there is no level before there are readings or a seed`() {
        val b = OpenBaseline()
        assertNull(b.level(0))
        repeat(OpenBaseline.MIN_SAMPLES - 1) { b.add(it * 33L, 0.6f) }
        assertNull("too few readings say nothing", b.level(300))
    }

    @Test
    fun `a seed stands in until the readings are enough`() {
        val b = OpenBaseline().apply { seed = 0.55f }
        assertEquals(0.55f, b.level(0)!!, 0f)
        b.feed(0, 600) { 0.40f }
        assertEquals("its own readings win", 0.40f, b.level(600)!!, 0.001f)
    }

    @Test
    fun `when the readings run out the last level they made stands in, not the seed`() {
        val b = OpenBaseline().apply { seed = 0.55f }
        b.feed(0, 2_000) { 0.40f }
        assertEquals(0.40f, b.level(2_000)!!, 0.001f)
        // The camera sleeps for a minute: the window is empty, and the best guess is what the eye read last.
        assertEquals(0.40f, b.level(62_000)!!, 0.001f)
        b.clear()
        assertNull("clearing forgets that too", b.level(62_000))
    }

    @Test
    fun `the level is the open eye and blinks hardly move it`() {
        val b = OpenBaseline()
        b.feed(0, 8_000) { t -> if (t % 3_000L < 150) 0.10f else 0.62f }   // a blink every 3 s, 150 ms each
        assertEquals(0.62f, b.level(8_000)!!, 0.001f)
    }

    @Test
    fun `it works the same for a narrow eye`() {
        val narrow = OpenBaseline()
        narrow.feed(0, 8_000) { t -> if (t % 3_000L < 150) 0.18f else 0.38f }
        assertEquals(0.38f, narrow.level(8_000)!!, 0.001f)
    }

    @Test
    fun `it follows a slow change within a few seconds`() {
        val b = OpenBaseline()
        b.feed(0, 8_000) { 0.60f }
        // The person looks down: the lids settle at 0.45 and stay there.
        b.feed(8_000, 16_000) { 0.45f }
        assertEquals(0.45f, b.level(16_000)!!, 0.001f)
        val midway = OpenBaseline()
        midway.feed(0, 8_000) { 0.60f }
        midway.feed(8_000, 9_000) { 0.45f }
        assertEquals("one second in, it still says open is 0.60", 0.60f, midway.level(9_000)!!, 0.001f)
    }

    @Test
    fun `old readings fall out of the window`() {
        val b = OpenBaseline(windowMs = 2_000)
        b.feed(0, 2_000) { 0.9f }
        b.feed(2_000, 4_100) { 0.3f }
        assertEquals(0.3f, b.level(4_100)!!, 0.001f)
    }

    @Test
    fun `a window that has emptied has no level`() {
        val b = OpenBaseline(windowMs = 1_000)
        b.feed(0, 1_000) { 0.5f }
        assertNull(b.level(10_000))
    }

    @Test
    fun `clearing forgets the readings and the seed`() {
        val b = OpenBaseline().apply { seed = 0.7f }
        b.feed(0, 1_000) { 0.5f }
        b.clear()
        assertNull(b.level(1_000))
        assertEquals(0, b.count)
    }

    @Test
    fun `a ring that is full keeps the newest readings`() {
        val b = OpenBaseline(windowMs = 10_000_000, capacity = 16)
        for (i in 0 until 64) b.add(i * 10L, if (i < 48) 0.2f else 0.8f)
        assertEquals(16, b.count)
        assertEquals(0.8f, b.level(640)!!, 0f)
    }

    @Test
    fun `readings that are not numbers are ignored`() {
        val b = OpenBaseline()
        b.add(0, Float.NaN)
        b.add(1, Float.POSITIVE_INFINITY)
        assertEquals(0, b.count)
        b.feed(0, 600) { 0.5f }
        assertNotNull(b.level(600))
        assertTrue(b.level(600)!!.isFinite())
    }
}

package com.eyeblink.core.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the documented timing contract from the spec - close 140 ms + hold 150 ms +
 * open 165 ms = 455 ms - and the shape of the frames in between.
 */
class BlinkTimelineTest {

    private val spec = BlinkSpec()
    private val timeline = BlinkTimeline(spec)

    @Test
    fun `default timing totals 455ms`() {
        assertEquals(140, spec.closeMs)
        assertEquals(150, spec.holdMs)
        assertEquals(165, spec.openMs)
        assertEquals(455, spec.totalMs)
        assertEquals(455, timeline.totalMs)
    }

    @Test
    fun `total is the sum of the three phases`() {
        assertEquals(600, BlinkSpec(closeMs = 200, holdMs = 300, openMs = 100).totalMs)
    }

    @Test
    fun `before the start and after the end the timeline is idle`() {
        assertTrue(timeline.frameAt(-1f).isIdle)
        assertTrue(timeline.frameAt(455f).isIdle)
        assertTrue(timeline.frameAt(10_000f).isIdle)
    }

    @Test
    fun `phases follow the configured boundaries`() {
        assertEquals(BlinkPhase.CLOSING, timeline.frameAt(0f).phase)
        assertEquals(BlinkPhase.CLOSING, timeline.frameAt(139f).phase)
        assertEquals(BlinkPhase.HOLD, timeline.frameAt(140f).phase)
        assertEquals(BlinkPhase.HOLD, timeline.frameAt(289f).phase)
        assertEquals(BlinkPhase.OPENING, timeline.frameAt(290f).phase)
        assertEquals(BlinkPhase.OPENING, timeline.frameAt(454f).phase)
    }

    @Test
    fun `the first frame is fully open and the hold is fully closed`() {
        val first = timeline.frameAt(0f)
        assertEquals(0f, first.closure, 1e-4f)
        assertEquals(0f, first.solid, 1e-4f)

        val hold = timeline.frameAt(200f)
        assertEquals(1f, hold.closure, 0f)
        assertEquals("pure black cover during the hold", 1f, hold.solid, 0f)
        assertEquals(1f, hold.dim, 0f)
    }

    @Test
    fun `closure rises through closing and falls through opening`() {
        var prev = -1f
        for (t in 0 until 140) {
            val c = timeline.frameAt(t.toFloat()).closure
            assertTrue("closing went backwards at $t", c >= prev - 1e-5f)
            prev = c
        }
        prev = 2f
        for (t in 290 until 455) {
            val c = timeline.frameAt(t.toFloat()).closure
            assertTrue("opening went forwards at $t", c <= prev + 1e-5f)
            prev = c
        }
    }

    @Test
    fun `darkening builds during closing but never reaches full black before the lids meet`() {
        val almost = timeline.frameAt(139f)
        assertTrue(almost.dim in 0f..1f)
        val mid = timeline.frameAt(70f)
        assertTrue("some darkening mid-closure", mid.dim > 0f)
        assertTrue("darkening is subtle until closed", mid.dim <= spec.darken)
    }

    @Test
    fun `zero darkening strength never darkens the open screen`() {
        val t = BlinkTimeline(BlinkSpec(darken = 0f))
        assertEquals(0f, t.frameAt(50f).dim, 0f)
    }

    @Test
    fun `reduced motion fades instead of sweeping lids`() {
        val t = BlinkTimeline(BlinkSpec(reducedMotion = true))
        val mid = t.frameAt(70f)
        assertFalse("no lids in reduced motion", mid.sweep)
        assertEquals(0f, mid.dim, 0f)
        assertTrue("fade progresses", mid.solid in 0.01f..0.99f)
        val hold = t.frameAt(200f)
        assertEquals(1f, hold.solid, 0f)
    }

    @Test
    fun `solid cover is an alpha ramp near the end of closing not a pop`() {
        var prev = 0f
        var biggestStep = 0f
        for (i in 0..1400) {
            val s = timeline.frameAt(i / 10f).solid
            if (i in 1..1399) biggestStep = maxOf(biggestStep, kotlin.math.abs(s - prev))
            prev = s
        }
        assertTrue("largest per-0.1ms step was $biggestStep", biggestStep < 0.05f)
    }

    @Test
    fun `validation clamps absurd values`() {
        val v = BlinkSpec(closeMs = 1, holdMs = 99_999, openMs = 99_999, curvature = 5f, feather = -1f, darken = 9f).validated()
        assertEquals(BlinkSpec.MIN_PHASE_MS, v.closeMs)
        assertEquals(BlinkSpec.MAX_HOLD_MS, v.holdMs)
        assertEquals(BlinkSpec.MAX_OPEN_MS, v.openMs)
        assertEquals(1f, v.curvature, 0f)
        assertEquals(0f, v.feather, 0f)
        assertEquals(1f, v.darken, 0f)
    }

    @Test
    fun `a timeline built from a wild spec still plays`() {
        val t = BlinkTimeline(BlinkSpec(closeMs = 1, holdMs = -5, openMs = 0))
        assertTrue(t.totalMs >= BlinkSpec.MIN_PHASE_MS * 2)
        assertTrue(t.frameAt(1f).phase != BlinkPhase.IDLE)
    }

    @Test
    fun `a zero length hold goes straight from closing to opening`() {
        val t = BlinkTimeline(BlinkSpec(holdMs = 0))
        assertEquals(BlinkPhase.CLOSING, t.frameAt(139f).phase)
        assertEquals(BlinkPhase.OPENING, t.frameAt(140f).phase)
    }
}

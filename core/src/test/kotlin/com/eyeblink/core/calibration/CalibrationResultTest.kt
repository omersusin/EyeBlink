package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationResultTest {

    private fun session() = CalibrationSession(videoMs = 60_000, readingMs = 60_000, deliberateBlinksRequired = 3)

    private fun blink(start: Long, instructed: Boolean = false, min: Float = 0.05f, conf: Float = 0.9f, depth: Float = 0f) =
        BlinkObservation(start, 130, true, conf, instructed, minOpenness = min, depth = depth)

    private fun sample(t: Long, open: Float = 0.85f, conf: Float = 0.9f, luma: Float = 120f) =
        EyeOpennessSample(t, open, open, conf, luma)

    /** 60 s of 15 fps samples starting at [from]. */
    private fun CalibrationSession.feed(from: Long, durationMs: Long = 60_000, open: Float = 0.85f) {
        var t = from
        while (t < from + durationMs) {
            onSample(sample(t, open))
            t += 66
        }
    }

    @Test
    fun `video and reading blinks seed separate context baselines`() {
        val s = session()
        s.start(0)
        for (i in 0 until 8) s.onBlink(blink(1_000L + i * 6_000L))           // video: every 6 s
        s.onTick(60_000)
        for (i in 0 until 8) s.onBlink(blink(61_000L + i * 9_000L))          // reading: every 9 s
        val profile = s.result().profile
        assertEquals(6_000L, profile.byContext.getValue(ContextProfile.VIDEO).medianIbiMs)
        assertEquals(9_000L, profile.byContext.getValue(ContextProfile.READING).medianIbiMs)
        assertEquals(Provenance.OBSERVED, profile.baselineFor(ContextProfile.READING).provenance)
        assertEquals("a context we never measured stays an estimate", Provenance.ESTIMATED, profile.baselineFor(ContextProfile.SOCIAL).provenance)
    }

    @Test
    fun `a phase with too few blinks does not get a context baseline`() {
        val s = session()
        s.start(0)
        repeat(8) { s.onBlink(blink(1_000L + it * 5_000L)) }
        s.onTick(60_000)
        s.onBlink(blink(70_000)) // one reading blink
        val ctx = s.result().profile.byContext
        assertTrue(ContextProfile.VIDEO in ctx)
        assertFalse(ContextProfile.READING in ctx)
    }

    @Test
    fun `tracked time confidence and brightness are measured per phase`() {
        val s = session()
        s.start(0)
        s.feed(0, 30_000)
        val r = s.result()
        val video = r.phases.getValue(CalibrationSession.Phase.VIDEO)
        assertTrue("tracked=${video.trackedMs}", video.trackedMs in 29_000L..30_100L)
        assertEquals(0.9f, video.meanConfidence, 0.01f)
        assertEquals(120f, video.meanLuma, 0.5f)
        assertEquals(0L, r.phases.getValue(CalibrationSession.Phase.READING).trackedMs)
    }

    @Test
    fun `untrackable frames are not counted as tracked time`() {
        val s = session()
        s.start(0)
        var t = 0L
        repeat(100) { s.onSample(sample(t, conf = 0.1f)); t += 66 }
        assertEquals(0L, s.result().phases.getValue(CalibrationSession.Phase.VIDEO).trackedMs)
        t = 100_000
        repeat(100) { s.onSample(sample(t, luma = 3f)); t += 66 }
        assertEquals(0L, s.result().phases.getValue(CalibrationSession.Phase.VIDEO).trackedMs)
    }

    @Test
    fun `a gap in tracking is not credited`() {
        val s = session()
        s.start(0)
        s.onSample(sample(0)); s.onSample(sample(66))
        s.onSample(sample(10_000)); s.onSample(sample(10_066))
        assertTrue(s.result().phases.getValue(CalibrationSession.Phase.VIDEO).trackedMs <= 140L)
    }

    @Test
    fun `thresholds are fitted from the open level and how deep the deliberate blinks went`() {
        val s = session()
        s.start(0)
        s.feed(0, 20_000, open = 0.62f)   // naturally narrow eyes
        s.skipPhase(20_000)
        s.skipPhase(21_000)
        // Three deliberate blinks that closed the eyes by about half of their open level.
        repeat(3) { s.onBlink(blink(22_000L + it * 1_500L, instructed = true, min = 0.30f, depth = 0.52f)) }
        val r = s.result()
        assertEquals(0.62f, r.openLevel!!, 0.02f)
        assertEquals(0.30f, r.closedLevel!!, 0.02f)
        assertTrue(r.profile.thresholds.fitted)
        assertTrue("fitted to a half closure: ${r.profile.thresholds}", r.profile.thresholds.start < BlinkDetector.DEFAULT_START)
        assertEquals(0.62f, r.profile.thresholds.openLevel, 0.02f)
    }

    @Test
    fun `blinks that were not measured as a depth are judged by their lowest openness`() {
        val s = session()
        s.start(0)
        s.feed(0, 20_000, open = 0.60f)
        s.skipPhase(20_000)
        s.skipPhase(21_000)
        repeat(3) { s.onBlink(blink(22_000L + it * 1_500L, instructed = true, min = 0.12f)) }   // no depth given
        val t = s.result().profile.thresholds
        assertTrue("depth 0.8 from 0.60 -> 0.12: ${t}", t.fitted)
    }

    @Test
    fun `without deliberate blinks the thresholds stay at the defaults`() {
        val s = session()
        s.start(0)
        s.feed(0, 20_000)
        val r = s.result()
        assertNull(r.closedLevel)
        assertFalse(r.profile.thresholds.fitted)
    }

    @Test
    fun `retrying starts from nothing`() {
        val s = session()
        s.start(0)
        repeat(8) { s.onBlink(blink(1_000L + it * 5_000L)) }
        s.feed(0, 10_000)
        s.skipPhase(11_000); s.skipPhase(12_000)
        s.onBlink(blink(13_000, instructed = true))
        s.start(100_000)
        val r = s.result()
        assertEquals(0, r.spontaneousCount)
        assertEquals(0, r.instructedCount)
        assertEquals(0L, r.trackedMs)
        assertNull(r.openLevel)
        assertEquals(CalibrationSession.Phase.VIDEO, s.phase)
    }

    @Test
    fun `phases can be skipped one by one and the last skip completes the session`() {
        val s = session()
        s.start(0)
        s.skipPhase(1_000); assertEquals(CalibrationSession.Phase.READING, s.phase)
        s.skipPhase(2_000); assertEquals(CalibrationSession.Phase.DELIBERATE, s.phase)
        s.skipPhase(3_000); assertEquals(CalibrationSession.Phase.COMPLETE, s.phase)
        s.skipPhase(4_000); assertEquals(CalibrationSession.Phase.COMPLETE, s.phase)
        assertTrue(s.result().complete)
        assertFalse("skipping everything leaves no usable baseline", s.result().hasEnoughSpontaneous)
    }

    @Test
    fun `the deliberate phase never feeds the open eye range`() {
        val s = session()
        s.start(0)
        s.skipPhase(0); s.skipPhase(0)
        s.feed(0, 20_000, open = 0.9f)
        assertNull(s.result().openLevel)
    }

    @Test
    fun `an unconfident deliberate blink does not count toward the closed level`() {
        val s = session()
        s.start(0)
        s.feed(0, 20_000)
        s.skipPhase(20_000); s.skipPhase(21_000)
        repeat(3) { s.onBlink(blink(22_000L + it * 1_500L, instructed = true, min = 0.04f)) }
        assertNotNull(s.result().closedLevel)
    }
}

package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationSessionTest {

    private val session = CalibrationSession(
        videoMs = 1_000,
        readingMs = 1_000,
        deliberateBlinksRequired = 3,
    )

    private fun blink(start: Long, confidence: Float = 0.9f, instructed: Boolean = false) =
        BlinkObservation(start, 120, true, confidence, instructed)

    // --- phase progression ------------------------------------------------

    @Test
    fun `starts in the video phase`() {
        session.start(0)
        assertEquals(CalibrationSession.Phase.VIDEO, session.phase)
    }

    @Test
    fun `video phase advances to reading after its duration`() {
        session.start(0)
        assertFalse("not yet due", session.onTick(999))
        assertTrue(session.onTick(1_000))
        assertEquals(CalibrationSession.Phase.READING, session.phase)
    }

    @Test
    fun `reading advances to deliberate`() {
        session.start(0)
        session.onTick(1_000)
        assertTrue(session.onTick(2_000))
        assertEquals(CalibrationSession.Phase.DELIBERATE, session.phase)
    }

    @Test
    fun `deliberate phase is not time limited`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        assertFalse(session.onTick(2_000))
        assertFalse(session.onTick(999_999))
        assertEquals(CalibrationSession.Phase.DELIBERATE, session.phase)
    }

    @Test
    fun `ticking a completed session changes nothing`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        repeat(3) { session.onBlink(blink(3_000L + it * 1_000)) }
        assertEquals(CalibrationSession.Phase.COMPLETE, session.phase)
        assertFalse(session.onTick(500_000))
        assertEquals(CalibrationSession.Phase.COMPLETE, session.phase)
    }

    // --- spontaneous vs instructed ---------------------------------------

    @Test
    fun `video and reading blinks are collected as spontaneous`() {
        session.start(0)
        assertTrue(session.onBlink(blink(100)))
        assertTrue(session.onBlink(blink(4_100)))
        assertEquals(2, session.spontaneous.size)
        assertEquals(0, session.instructed.size)
    }

    @Test
    fun `deliberate blinks are tagged instructed and kept apart`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        assertTrue(session.onBlink(blink(2_100)))
        assertEquals(1, session.instructed.size)
        assertTrue("must be tagged so it never reaches learning", session.instructed[0].instructed)
        assertTrue("instructed blinks must be unlearnable", session.instructed[0].isLearnable.not())
    }

    @Test
    fun `unreliable spontaneous blinks are rejected`() {
        session.start(0)
        assertFalse(session.onBlink(blink(100, confidence = 0.1f)))
        assertEquals(0, session.spontaneous.size)
    }

    @Test
    fun `deliberate phase completes once enough blinks are counted`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        assertEquals(3, session.deliberateRemaining)
        session.onBlink(blink(2_100))
        assertEquals(2, session.deliberateRemaining)
        session.onBlink(blink(2_200))
        session.onBlink(blink(2_300))
        assertEquals(0, session.deliberateRemaining)
        assertEquals(CalibrationSession.Phase.COMPLETE, session.phase)
    }

    @Test
    fun `blinks after completion are ignored`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        repeat(3) { session.onBlink(blink(2_100L + it)) }
        assertFalse(session.onBlink(blink(9_000)))
    }

    @Test
    fun `cancel ends the session at any point`() {
        session.start(0)
        session.cancel()
        assertEquals(CalibrationSession.Phase.COMPLETE, session.phase)
        assertFalse(session.onBlink(blink(100)))
    }

    // --- a face that goes away in the middle of a blink -----------------------------------------------------------

    private fun sample(t: Long, open: Float, illuminance: Float = 120f) = EyeOpennessSample(t, open, open, 0.9f, illuminance)

    /** A session in its deliberate phase that has seen 1.5 s of an eye open at 0.60, and then two frames of it closing. */
    private fun midBlink(): CalibrationSession {
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
        s.start(0)
        s.skipPhase(0); s.skipPhase(0)
        assertEquals(CalibrationSession.Phase.DELIBERATE, s.phase)
        var t = 0L
        while (t < 1_500) { s.process(sample(t, 0.60f)); t += 33 }
        s.process(sample(1_500, 0.20f))
        s.process(sample(1_533, 0.18f))
        return s
    }

    @Test
    fun `a blink is counted when the eye opens again`() {
        val s = midBlink()
        assertNotNull(s.process(sample(1_600, 0.60f)))
    }

    @Test
    fun `a gap in the pictures does not turn the time it lasted into a long blink`() {
        val s = midBlink()
        assertNull("1.6 s without a picture, then an open eye", s.process(sample(3_100, 0.60f)))
        assertEquals(0, s.instructed.size)
    }

    @Test
    fun `a run of bad pictures that lasts is a gap too`() {
        val s = midBlink()
        var t = 1_566L
        while (t < 3_100) { s.process(sample(t, 0.18f, illuminance = 1f)); t += 33 }
        assertNull("1.5 s of pictures too dark to trust, then an open eye", s.process(sample(t, 0.60f)))
        assertEquals(0, s.instructed.size)
    }

    @Test
    fun `one picture too dark to trust in the middle of a blink neither ends nor splits it`() {
        val s = midBlink()
        assertNull(s.process(sample(1_566, 0.18f, illuminance = 1f)))
        assertNotNull("the blink is counted when the eye opens", s.process(sample(1_600, 0.60f)))
        assertEquals(1, s.instructed.size)
    }

    // --- baseline ---------------------------------------------------------

    @Test
    fun `the baseline is built from spontaneous blinks only`() {
        session.start(0)
        // six spontaneous blinks during video + reading
        listOf(100L, 1_200L, 2_400L, 3_600L, 4_800L, 6_000L)
            .forEach { session.onBlink(blink(it)) }
        session.onTick(7_000)
        session.onTick(8_000)
        repeat(3) { session.onBlink(blink(8_100L + it)) }

        val result = session.result()
        assertEquals(6, result.spontaneousCount)
        assertEquals(3, result.instructedCount)
        // Deliberate blinks must not inflate the sample count.
        assertEquals(6, result.baseline.sampleCount)
    }

    @Test
    fun `an uncalibrated session reports insufficient signal`() {
        session.start(0)
        val result = session.result()
        assertFalse(result.hasEnoughSpontaneous)
        assertEquals(0, result.spontaneousCount)
    }

    @Test
    fun `instructed blinks cannot alone produce a baseline`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        repeat(3) { session.onBlink(blink(2_100L + it)) }
        val result = session.result()
        assertTrue(result.complete)
        assertFalse(
            "deliberate blinks are not a behavioural baseline",
            result.hasEnoughSpontaneous,
        )
    }

    @Test
    fun `result reports completion`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        assertFalse(session.result().complete)
        repeat(3) { session.onBlink(blink(2_100L + it)) }
        assertTrue(session.result().complete)
    }

    // --- progress ---------------------------------------------------------

    @Test
    fun `progress runs from zero to one across a timed phase`() {
        session.start(0)
        assertEquals(0f, session.phaseProgress(0), 0.001f)
        assertEquals(0.5f, session.phaseProgress(500), 0.001f)
        assertEquals(1f, session.phaseProgress(1_000), 0.001f)
    }

    @Test
    fun `progress is clamped for an overshoot`() {
        session.start(0)
        assertEquals(1f, session.phaseProgress(99_000), 0.001f)
    }

    @Test
    fun `remaining time counts down and never goes negative`() {
        session.start(0)
        assertEquals(1_000L, session.remainingMs(0))
        assertEquals(500L, session.remainingMs(500))
        assertEquals(0L, session.remainingMs(5_000))
    }

    @Test
    fun `the deliberate phase has no remaining time`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        assertNull(session.remainingMs(2_100))
    }

    @Test
    fun `restarting resets progress and counters`() {
        session.start(0)
        session.onTick(1_000)
        session.onTick(2_000)
        session.onBlink(blink(2_100))
        session.start(0)
        assertEquals(CalibrationSession.Phase.VIDEO, session.phase)
        assertEquals(3, session.deliberateRemaining)
        assertEquals(0f, session.phaseProgress(0), 0.001f)
    }
}
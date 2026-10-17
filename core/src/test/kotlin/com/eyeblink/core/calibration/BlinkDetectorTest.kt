package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {

    private val detector = BlinkDetector()

    private fun sample(
        t: Long,
        openness: Float,
        confidence: Float = 0.9f,
        illuminance: Float = 200f,
    ) = EyeOpennessSample(t, openness, openness, confidence, illuminance)

    /** Feeds open -> closed -> open and returns the emitted blink, if any. */
    private fun blinkCycle(closeAt: Long, reopenAt: Long, dipTo: Float = 0.0f): BlinkObservation? {
        detector.onSample(sample(closeAt - 100, 0.9f))
        detector.onSample(sample(closeAt, dipTo))
        detector.onSample(sample((closeAt + reopenAt) / 2, dipTo))
        return detector.onSample(sample(reopenAt, 0.9f))
    }

    // --- happy path -------------------------------------------------------

    @Test
    fun `a full close and reopen emits one complete blink`() {
        val b = blinkCycle(closeAt = 1_000, reopenAt = 1_150, dipTo = 0.0f)
        assertNotNull(b)
        assertEquals(1_000L, b!!.startMs)
        assertEquals(150L, b.durationMs)
        assertTrue(b.complete)
        assertFalse("detector must be idle again after emitting", detector.isClosed)
    }

    @Test
    fun `an eye that stays open emits nothing`() {
        assertNull(detector.onSample(sample(0, 0.9f)))
        assertNull(detector.onSample(sample(100, 0.8f)))
        assertNull(detector.onSample(sample(200, 0.95f)))
    }

    @Test
    fun `two separate blinks are counted separately`() {
        val first = blinkCycle(1_000, 1_150)
        val second = blinkCycle(3_000, 3_180)
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1_000L, first!!.startMs)
        assertEquals(3_000L, second!!.startMs)
    }

    // --- completeness -----------------------------------------------------

    @Test
    fun `a partial closure is reported as incomplete`() {
        val b = blinkCycle(closeAt = 1_000, reopenAt = 1_150, dipTo = 0.25f)
        assertNotNull(b)
        assertFalse("0.25 openness never reached the full-closure threshold", b!!.complete)
    }

    @Test
    fun `reaching near zero counts as complete`() {
        val b = blinkCycle(closeAt = 1_000, reopenAt = 1_150, dipTo = 0.05f)
        assertNotNull(b)
        assertTrue(b!!.complete)
    }

    // --- hysteresis -------------------------------------------------------

    @Test
    fun `jitter between the thresholds does not start a blink`() {
        // 0.40 is below open but above close: the eye is not shut yet.
        assertNull(detector.onSample(sample(0, 0.9f)))
        assertNull(detector.onSample(sample(100, 0.40f)))
        assertNull(detector.onSample(sample(200, 0.45f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `a dip below the close threshold does start a blink`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.10f))
        assertTrue(detector.isClosed)
    }

    @Test
    fun `the minimum openness across the closure is what counts`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.20f))
        detector.onSample(sample(150, 0.03f)) // fully shut
        detector.onSample(sample(200, 0.18f))
        val b = detector.onSample(sample(260, 0.9f))
        assertNotNull(b)
        assertTrue("dropped to 0.03, so complete", b!!.complete)
    }

    // --- outlier rejection ------------------------------------------------

    @Test
    fun `an implausibly short closure is rejected as noise`() {
        assertNull(blinkCycle(closeAt = 1_000, reopenAt = 1_010))
    }

    @Test
    fun `an implausibly long closure is rejected as tracking loss`() {
        assertNull(blinkCycle(closeAt = 1_000, reopenAt = 2_500))
    }

    @Test
    fun `a plausible-duration closure at the boundary is accepted`() {
        assertNotNull(blinkCycle(closeAt = 1_000, reopenAt = 1_000 + BlinkDetector.DEFAULT_MIN_DURATION_MS))
    }

    // --- trust gating -----------------------------------------------------

    @Test
    fun `a dark sample cannot open a blink`() {
        assertNull(detector.onSample(sample(100, 0.0f, illuminance = 1f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `a low-confidence sample cannot open a blink`() {
        assertNull(detector.onSample(sample(100, 0.0f, confidence = 0.1f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `an untrackable mid-blink sample does not end the blink`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.05f))
        assertTrue(detector.isClosed)
        // Lighting dips mid-closure; must be ignored entirely.
        assertNull(detector.onSample(sample(150, 0.95f, illuminance = 1f)))
        assertTrue("still closed, the dark frame was ignored", detector.isClosed)
        val b = detector.onSample(sample(220, 0.9f))
        assertNotNull(b)
    }

    @Test
    fun `a dip in confidence mid-blink does not end the blink`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.05f))
        assertNull(detector.onSample(sample(150, 0.95f, confidence = 0.05f)))
        assertTrue(detector.isClosed)
    }

    // --- reset ------------------------------------------------------------

    @Test
    fun `reset drops an in-progress closure`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.05f))
        assertTrue(detector.isClosed)
        detector.reset()
        assertFalse(detector.isClosed)
        // The stale closure must not resurface as a blink when it reopens.
        assertNull(detector.onSample(sample(200, 0.9f)))
    }

    @Test
    fun `the detector is reusable after reset`() {
        blinkCycle(1_000, 1_150)
        detector.reset()
        assertNotNull(blinkCycle(3_000, 3_150))
    }

    @Test
    fun `confidence is carried from the closing frame`() {
        detector.onSample(sample(0, 0.9f))
        detector.onSample(sample(100, 0.05f, confidence = 0.6f))
        val b = detector.onSample(sample(300, 0.9f, confidence = 0.95f))
        assertNotNull(b)
        assertEquals(0.6f, b!!.confidence, 0.001f)
    }

    @Test
    fun `thresholds are ordered so hysteresis is real`() {
        assertTrue(
            BlinkDetector.DEFAULT_REOPEN_THRESHOLD > BlinkDetector.DEFAULT_CLOSE_THRESHOLD
        )
    }

    @Test
    fun `duration bounds are ordered`() {
        assertTrue(BlinkDetector.DEFAULT_MIN_DURATION_MS < BlinkDetector.DEFAULT_MAX_DURATION_MS)
    }
}
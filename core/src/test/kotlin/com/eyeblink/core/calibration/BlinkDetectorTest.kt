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

    /** Open eyes at [open] from [from] to [to] at about 15 frames per second. */
    private fun warmUp(from: Long = 0, to: Long = 1_200, open: Float = 0.80f, step: Long = 66) {
        var t = from
        while (t < to) { detector.onSample(sample(t, open)); t += step }
    }

    /** Close to [dipTo] at [closeAt], reopen to [open] at [reopenAt]; returns the blink the reopening frame produced. */
    private fun blinkCycle(closeAt: Long, reopenAt: Long, dipTo: Float = 0.05f, open: Float = 0.80f): BlinkObservation? {
        detector.onSample(sample(closeAt - 66, open))
        detector.onSample(sample(closeAt, dipTo))
        detector.onSample(sample((closeAt + reopenAt) / 2, dipTo))
        return detector.onSample(sample(reopenAt, open))
    }

    // --- happy path -------------------------------------------------------

    @Test
    fun `a full close and reopen emits one complete blink`() {
        warmUp()
        val b = blinkCycle(closeAt = 2_000, reopenAt = 2_150)
        assertNotNull(b)
        assertEquals(2_000L, b!!.startMs)
        assertEquals(150L, b.durationMs)
        assertTrue(b.complete)
        assertTrue("it closed by most of its open level", b.depth > 0.85f)
        assertFalse("the detector is idle again after emitting", detector.isClosed)
    }

    @Test
    fun `an eye that stays open emits nothing`() {
        warmUp(0, 5_000)
        assertFalse(detector.isClosed)
    }

    @Test
    fun `two separate blinks are counted separately`() {
        warmUp()
        val first = blinkCycle(2_000, 2_150)
        val second = blinkCycle(4_000, 4_180)
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(2_000L, first!!.startMs)
        assertEquals(4_000L, second!!.startMs)
    }

    // --- every kind of eye -------------------------------------------------

    @Test
    fun `a wide eye and a narrow eye are both seen to blink`() {
        for ((open, shut) in listOf(0.85f to 0.10f, 0.62f to 0.12f, 0.40f to 0.15f, 0.30f to 0.14f)) {
            val d = BlinkDetector()
            var t = 0L
            while (t < 1_200) { d.onSample(sample(t, open)); t += 66 }
            d.onSample(sample(2_000, open)); d.onSample(sample(2_066, shut)); d.onSample(sample(2_132, shut))
            val b = d.onSample(sample(2_198, open))
            assertNotNull("open $open, shut $shut", b)
        }
    }

    @Test
    fun `an open eye that never gets near the old fixed thresholds still blinks`() {
        // The case that used to fail: this eye reads 0.35 open, so it never rose back to the 0.55 a blink needed to end.
        warmUp(open = 0.35f)
        val b = blinkCycle(2_000, 2_200, dipTo = 0.10f, open = 0.35f)
        assertNotNull(b)
    }

    // --- completeness -----------------------------------------------------

    @Test
    fun `a partial closure is reported as incomplete`() {
        warmUp()
        // Down to 0.55 of 0.80: a closure of about 0.31 is below start, 0.45 of 0.8 -> 0.44 is above and below full.
        val b = blinkCycle(2_000, 2_150, dipTo = 0.40f)
        assertNotNull(b)
        assertFalse("half way is not a full blink", b!!.complete)
    }

    @Test
    fun `reaching nearly shut counts as complete`() {
        warmUp()
        assertTrue(blinkCycle(2_000, 2_150, dipTo = 0.05f)!!.complete)
    }

    @Test
    fun `the deepest point of the closure is what counts`() {
        warmUp()
        detector.onSample(sample(2_000, 0.80f))
        detector.onSample(sample(2_066, 0.40f))
        detector.onSample(sample(2_132, 0.05f))
        detector.onSample(sample(2_198, 0.40f))
        val b = detector.onSample(sample(2_264, 0.80f))
        assertNotNull(b)
        assertTrue(b!!.complete)
        assertEquals(0.05f, b.minOpenness, 0.001f)
    }

    // --- hysteresis -------------------------------------------------------

    @Test
    fun `jitter below the start threshold does not start a blink`() {
        warmUp()
        // 0.62 of 0.80 is a closure of about 0.22: under start (0.35), so not a blink.
        for (i in 0 until 10) assertNull(detector.onSample(sample(2_000L + i * 66, if (i % 2 == 0) 0.62f else 0.70f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `a dip past the start threshold does start a blink`() {
        warmUp()
        detector.onSample(sample(2_000, 0.30f))
        assertTrue(detector.isClosed)
    }

    @Test
    fun `an eye that settles between the thresholds does not end the blink early`() {
        warmUp()
        detector.onSample(sample(2_000, 0.10f))
        // Back up to 0.60: closure 0.25, above end (0.17): still counts as shut.
        assertNull(detector.onSample(sample(2_066, 0.60f)))
        assertTrue(detector.isClosed)
        assertNotNull(detector.onSample(sample(2_132, 0.80f)))
    }

    // --- what is not a blink ----------------------------------------------

    @Test
    fun `looking down slowly is not a blink`() {
        warmUp(0, 3_000)
        // The lids settle 22 % lower over two seconds, as the eyes follow a phone held low.
        var t = 3_000L
        while (t < 5_000) {
            val open = 0.80f * (1f - 0.22f * (t - 3_000) / 2_000f)
            assertNull("at $t", detector.onSample(sample(t, open)))
            t += 66
        }
        assertFalse(detector.isClosed)
    }

    @Test
    fun `a closure held far longer than a blink is not one, and the detector recovers`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        var t = 2_066L
        while (t < 4_500) { assertNull(detector.onSample(sample(t, 0.05f))); t += 66 }
        // The eyes open again and stay open: after a moment of learning what open is, blinks are seen again.
        while (t < 7_000) { detector.onSample(sample(t, 0.80f)); t += 66 }
        assertNotNull(blinkCycle(7_100, 7_250))
    }

    @Test
    fun `a one frame glitch is rejected unless it is deep`() {
        warmUp()
        // Shallow and alone: a landmark that jumped.
        detector.onSample(sample(2_000, 0.50f))
        assertNull(detector.onSample(sample(2_066, 0.80f)))
        // Deep and alone at 15 fps is a real fast blink.
        detector.onSample(sample(3_000, 0.04f))
        assertNotNull(detector.onSample(sample(3_066, 0.80f)))
    }

    @Test
    fun `an implausibly short closure is rejected`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        assertNull(detector.onSample(sample(2_010, 0.80f)))
    }

    // --- trust gating -----------------------------------------------------

    @Test
    fun `a dark sample cannot open a blink`() {
        warmUp()
        assertNull(detector.onSample(sample(2_000, 0.0f, illuminance = 1f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `a low-confidence sample cannot open a blink`() {
        warmUp()
        assertNull(detector.onSample(sample(2_000, 0.0f, confidence = 0.1f)))
        assertFalse(detector.isClosed)
    }

    @Test
    fun `an untrackable mid-blink sample does not end the blink`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        assertTrue(detector.isClosed)
        assertNull(detector.onSample(sample(2_050, 0.80f, illuminance = 1f)))
        assertTrue("still closed, the dark frame was ignored", detector.isClosed)
        assertNotNull(detector.onSample(sample(2_120, 0.80f)))
    }

    @Test
    fun `confidence is carried from the closing frame`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f, confidence = 0.6f))
        detector.onSample(sample(2_066, 0.05f, confidence = 0.6f))
        val b = detector.onSample(sample(2_132, 0.80f, confidence = 0.95f))
        assertNotNull(b)
        assertEquals(0.6f, b!!.confidence, 0.001f)
    }

    // --- warm-up, seed, reset ----------------------------------------------

    @Test
    fun `it says nothing while it is still learning what open looks like`() {
        detector.onSample(sample(0, 0.80f))
        detector.onSample(sample(66, 0.05f))
        assertNull(detector.onSample(sample(132, 0.80f)))
        assertEquals(0f, detector.closure, 0f)
    }

    @Test
    fun `a known open level needs no warm-up`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        d.onSample(sample(0, 0.05f))
        d.onSample(sample(66, 0.05f))
        assertNotNull(d.onSample(sample(132, 0.80f)))
    }

    @Test
    fun `reset drops an in-progress closure but keeps what open looks like`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        assertTrue(detector.isClosed)
        detector.reset()
        assertFalse(detector.isClosed)
        assertNull("the stale closure must not surface as a blink", detector.onSample(sample(2_100, 0.80f)))
        assertNotNull("no warm-up needed", blinkCycle(3_000, 3_150))
    }

    @Test
    fun `forgetting starts from nothing again`() {
        warmUp()
        detector.forget()
        detector.onSample(sample(2_000, 0.05f))
        assertFalse("a closure cannot be judged without an open level", detector.isClosed)
    }

    @Test
    fun `the live closure follows the eyes`() {
        warmUp()
        detector.onSample(sample(2_000, 0.80f))
        assertEquals(0f, detector.closure, 0.02f)
        detector.onSample(sample(2_066, 0.40f))
        assertEquals(0.5f, detector.closure, 0.03f)
        detector.onSample(sample(2_132, 0.0f))
        assertEquals(1f, detector.closure, 0.001f)
    }

    // --- settings ---------------------------------------------------------

    @Test
    fun `more sensitive settings see a shallower blink`() {
        // A closure of about 0.25 of the open level.
        fun run(settings: DetectorSettings): BlinkObservation? {
            val d = BlinkDetector(settings)
            var t = 0L
            while (t < 1_200) { d.onSample(sample(t, 0.80f)); t += 66 }
            d.onSample(sample(2_000, 0.60f)); d.onSample(sample(2_066, 0.58f)); d.onSample(sample(2_132, 0.60f))
            return d.onSample(sample(2_198, 0.80f))
        }
        assertNull(run(DetectorSettings()))
        assertNotNull(run(DetectorSettings().scaled(0.65f)))
    }

    @Test
    fun `scaling keeps the thresholds in a sane order`() {
        for (factor in listOf(0.3f, 0.7f, 1f, 1.35f, 3f)) {
            val s = DetectorSettings().scaled(factor)
            assertTrue("factor $factor: end < start", s.end < s.start)
            assertTrue("factor $factor: start in range", s.start in 0.12f..0.60f)
        }
    }

    @Test
    fun `configure changes the rules without forgetting the open level`() {
        warmUp()
        detector.configure(DetectorSettings(maxDurationMs = 3_000))
        detector.onSample(sample(2_000, 0.05f))
        var t = 2_066L
        while (t < 3_800) { detector.onSample(sample(t, 0.05f)); t += 66 }
        assertNotNull("a closure of 1.8 s is a blink under the long setting", detector.onSample(sample(t, 0.80f)))
    }

    @Test
    fun `thresholds are ordered so hysteresis is real`() {
        assertTrue(BlinkDetector.DEFAULT_END < BlinkDetector.DEFAULT_START)
        assertTrue(BlinkDetector.DEFAULT_START < BlinkDetector.DEFAULT_FULL)
        assertTrue(BlinkDetector.DEFAULT_MIN_DURATION_MS < BlinkDetector.DEFAULT_MAX_DURATION_MS)
    }

    @Test
    fun `the face model's blink score can see a blink the eyelid measure barely moves for`() {
        // The eyelid reading dips only 12 %, but the model says the eyes are shut.
        val d = BlinkDetector(DetectorSettings(useBlinkScore = true))
        fun s(t: Long, open: Float, score: Float) = EyeOpennessSample(t, open, open, 0.9f, 200f, blinkScore = score)
        var t = 0L
        while (t < 1_200) { d.onSample(s(t, 0.50f, 0.05f)); t += 66 }
        d.onSample(s(2_000, 0.44f, 0.80f)); d.onSample(s(2_066, 0.44f, 0.85f))
        val b = d.onSample(s(2_132, 0.50f, 0.05f))
        assertNotNull(b)
    }

    @Test
    fun `the blink score is ignored unless asked for`() {
        val d = BlinkDetector()
        fun s(t: Long, open: Float, score: Float) = EyeOpennessSample(t, open, open, 0.9f, 200f, blinkScore = score)
        var t = 0L
        while (t < 1_200) { d.onSample(s(t, 0.50f, 0.05f)); t += 66 }
        d.onSample(s(2_000, 0.44f, 0.80f)); d.onSample(s(2_066, 0.44f, 0.85f))
        assertNull(d.onSample(s(2_132, 0.50f, 0.05f)))
    }

    // --- eyes that stay shut, lids that settle lower, a seed that is wrong -------------------------------------------

    /** [openness] at 15 frames per second from [from] to [to] with a little jitter; returns the blinks reported meanwhile. */
    private fun hold(from: Long, to: Long, openness: Float, jitter: Float = 0.01f, seed: Long = 5): List<BlinkObservation> {
        val random = java.util.Random(seed)
        val found = mutableListOf<BlinkObservation>()
        var t = from
        while (t < to) {
            val v = (openness + (random.nextGaussian() * jitter).toFloat()).coerceIn(0f, 1f)
            detector.onSample(sample(t, v))?.let { found += it }
            t += 66
        }
        return found
    }

    @Test
    fun `eyes shut for a long time make no blinks, neither while shut nor when they open`() {
        warmUp()
        val shut = hold(2_000, 27_000, 0.05f)
        assertTrue("the jitter of a shut eye is not a string of blinks: ${shut.size}", shut.isEmpty())
        assertTrue(detector.isShut)
        assertTrue("opening the eyes after that is not a blink", hold(27_000, 28_000, 0.80f, jitter = 0.005f).isEmpty())
        assertFalse(detector.isShut)
        assertNotNull("blinks are seen again within a second", blinkCycle(28_300, 28_450))
    }

    @Test
    fun `eyes shut for just over a blink are shut, a squint is not`() {
        warmUp()
        hold(2_000, 3_300, 0.05f)
        assertTrue("shut tight for 1.3 s", detector.isShut)
        assertTrue(detector.isClosed)

        // A squint: 0.50 of an 0.80 eye, a closure of 0.375 - a blink's worth, but not nearly shut.
        val squint = BlinkDetector().also { d -> var t = 0L; while (t < 1_200) { d.onSample(sample(t, 0.80f)); t += 66 } }
        var t = 2_000L
        while (t < 3_300) { squint.onSample(sample(t, 0.50f)); t += 66 }
        assertFalse("a squint is not shut", squint.isShut)
        assertFalse("and after a second it is the new open, not a closure that never ends", squint.isClosed)
    }

    @Test
    fun `eyes that stay shut for half a minute are taken to have changed for good`() {
        warmUp()
        hold(2_000, 33_000, 0.05f)
        assertFalse(detector.isShut)
        hold(33_000, 36_000, 0.40f)
        assertNotNull("the open level of the eyes is learned anew", blinkCycle(36_100, 36_250, dipTo = 0.05f, open = 0.40f))
    }

    @Test
    fun `a deep closure that turns into a squint is learned again after a moment`() {
        warmUp()
        hold(2_000, 5_000, 0.05f)
        assertTrue(detector.isShut)
        // They open again only part of the way - a squint against the light - and stay there.
        hold(5_000, 8_000, 0.50f)
        assertFalse(detector.isClosed)
        assertNotNull(blinkCycle(8_100, 8_250, dipTo = 0.05f, open = 0.50f))
    }

    @Test
    fun `lids that settle lower after a blink end it, and that is where open is now`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        detector.onSample(sample(2_066, 0.05f))
        // They come back only to 0.62 of an 0.80 eye: a closure of 0.22, between end (0.17) and start (0.35).
        var found: BlinkObservation? = null
        var t = 2_132L
        while (t < 3_500) { detector.onSample(sample(t, 0.62f))?.let { if (found == null) found = it }; t += 66 }
        assertNotNull("the blink is reported although the lids never came all the way back", found)
        assertEquals(2_000L, found!!.startMs)
        assertEquals("it ended when the lids stopped coming back", 132L, found.durationMs)
        assertTrue(found.complete)
        assertFalse(detector.isClosed)
        assertNotNull("the next blink is judged against the new open level", blinkCycle(4_000, 4_150, dipTo = 0.05f, open = 0.62f))
    }

    @Test
    fun `a reopening that takes its time is still just a blink`() {
        warmUp()
        detector.onSample(sample(2_000, 0.05f))
        detector.onSample(sample(2_066, 0.05f))
        // Up through the band between end and start over 0.2 s: slower than most, quicker than "settled".
        detector.onSample(sample(2_132, 0.55f))
        detector.onSample(sample(2_198, 0.58f))
        detector.onSample(sample(2_264, 0.62f))
        val b = detector.onSample(sample(2_330, 0.80f))
        assertNotNull(b)
        assertEquals(330L, b!!.durationMs)
    }

    @Test
    fun `a seed that is too high does not hide the blinks of this session`() {
        // The eye reads 0.50 now and read 0.80 when the seed was taken: by the seed it looks half shut from the first frame.
        val d = BlinkDetector().apply { seed(0.80f) }
        val reported = mutableListOf<BlinkObservation>()
        var t = 0L
        while (t < 1_300) { d.onSample(sample(t, 0.50f))?.let { reported += it }; t += 66 }
        assertTrue("those frames are not a blink", reported.isEmpty())
        assertFalse("and the detector is not stuck in a closure it can never end", d.isClosed)
        d.onSample(sample(1_300, 0.08f)); d.onSample(sample(1_366, 0.08f))
        assertNotNull("a blink 1.3 s into the session is seen", d.onSample(sample(1_432, 0.50f)))
    }

    @Test
    fun `a blink while the seed still stands in for the level is a blink`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        d.onSample(sample(0, 0.80f)); d.onSample(sample(66, 0.80f))
        d.onSample(sample(132, 0.05f)); d.onSample(sample(198, 0.05f))
        assertNotNull(d.onSample(sample(264, 0.80f)))
    }

    @Test
    fun `an eye that is open less than the seed says is judged by what it does`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        var t = 0L
        while (t < 3_000) { d.onSample(sample(t, 0.50f)); t += 66 }
        assertEquals("open is open, whatever the seed said", 0f, d.closure, 0.05f)
    }

    @Test
    fun `a blink score that is not a number is no opinion at all`() {
        val d = BlinkDetector(DetectorSettings(useBlinkScore = true))
        fun s(t: Long, open: Float) = EyeOpennessSample(t, open, open, 0.9f, 200f, blinkScore = Float.NaN)
        var t = 0L
        while (t < 1_200) { d.onSample(s(t, 0.50f)); t += 66 }
        d.onSample(s(2_000, 0.10f)); d.onSample(s(2_066, 0.10f))
        assertFalse("the closure is a number", d.closure.isNaN())
        assertNotNull("and the eyelids alone still see the blink", d.onSample(s(2_132, 0.50f)))
    }

    @Test
    fun `thresholds fitted with the blink score make the detector use the score`() {
        assertTrue(BlinkThresholds(0.30f, 0.13f, 0.55f, fitted = true).settings().useBlinkScore)
        assertFalse(BlinkThresholds().settings().useBlinkScore)
    }
}

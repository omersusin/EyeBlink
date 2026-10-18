package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cases an independent review found by attacking the engine: a lowered gaze that looks like shut eyes, eyes shut for
 * minutes, one bad picture in the middle of a rest, a dip that settles, a seed that is right. Each is a stream a real
 * phone produces, written out so that it can be replayed.
 */
class EngineEdgeCasesTest {

    private val good = QualityReport(0.9f, emptySet())

    private fun sample(t: Long, open: Float, score: Float? = null, confidence: Float = 0.9f, luma: Float = 120f) =
        EyeOpennessSample(t, open, open, confidence, luma, blinkScore = score)

    private fun frame(t: Long, open: Float, score: Float? = null, confidence: Float = 0.9f, luma: Float = 120f) =
        FaceFrame(t, sample(t, open, score, confidence, luma), good, luma)

    private fun noFace(t: Long) = FaceFrame(t, null, QualityReport(0f, setOf(QualityIssue.NO_FACE)), 0f)

    /** Open eyes at [level] with the little jitter of a tracker. */
    private fun jitter(random: java.util.Random, level: Float, amount: Float = 0.01f) =
        (level + (random.nextGaussian() * amount).toFloat()).coerceIn(0f, 1f)

    // --- 1. a lowered gaze is not shut eyes -----------------------------------------------------------------------------

    /** Lids at 0.40 until 5 s and at [lowered] after, a full blink every two seconds; the blinks reported from 8 s on, and the frames that counted. */
    private fun gazeDown(thresholds: BlinkThresholds, lowered: Float, withScore: Boolean, stepMs: Long = 66): Triple<Int, Int, Double> {
        val random = java.util.Random(11)
        val p = TrackingPipeline(thresholds)
        val found = mutableListOf<BlinkObservation>()
        val blinkTimes = (1..19).map { it * 2_000L }
        var frames = 0
        var counted = 0
        var t = 0L
        while (t < 40_000) {
            val base = if (t < 5_000) 0.40f else lowered
            val blink = blinkTimes.any { t >= it && t < it + 3 * stepMs }
            val v = jitter(random, if (blink) base * 0.1f else base)
            val score = if (withScore) (if (blink) 0.92f else 0.06f) else null
            val ev = p.onFrame(frame(t, v, score))
            ev.blink?.let { found += it }
            frames++
            if (ev.trackable) counted++
            t += stepMs
        }
        val expected = blinkTimes.filter { it >= 8_000 }
        val hit = expected.count { e -> found.any { kotlin.math.abs(it.startMs - e) <= 400 } }
        return Triple(hit, expected.size, counted.toDouble() / frames)
    }

    @Test
    fun `a gaze lowered by a third for half a minute is the new open, and blinks on top of it are still seen`() {
        val shallowFit = BlinkThresholds.fromCalibration(openLevel = 0.40f, fullDepth = 0.30f)
        for (withScore in listOf(false, true)) {
            val (hit, expected, counted) = gazeDown(shallowFit, lowered = 0.28f, withScore = withScore)
            assertTrue("score=$withScore: found $hit of $expected", hit >= expected * 0.8)
            assertTrue("score=$withScore: only $counted of the frames counted", counted > 0.9)
        }
    }

    @Test
    fun `the same for the population defaults and a squint of almost half`() {
        for (withScore in listOf(false, true)) {
            val (hit, expected, counted) = gazeDown(BlinkThresholds(), lowered = 0.23f, withScore = withScore)
            assertTrue("score=$withScore: found $hit of $expected", hit >= expected * 0.8)
            assertTrue("score=$withScore: only $counted of the frames counted", counted > 0.9)
        }
    }

    // --- 2. eyes shut for minutes ---------------------------------------------------------------------------------------

    @Test
    fun `eyes shut for two minutes make no blinks and are not credited to the blink rate`() {
        val random = java.util.Random(3)
        val p = TrackingPipeline()
        var t = 0L
        val found = mutableListOf<BlinkObservation>()
        while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).blink?.let { found += it }; t += 66 }
        while (t < 123_000) { p.onFrame(frame(t, jitter(random, 0.05f), 0.93f)).blink?.let { found += it }; t += 66 }
        assertTrue("blinks while shut: ${found.map { it.startMs }}", found.isEmpty())
        val credited = p.rate.observed(t).trackedMs
        assertTrue("two minutes shut credited $credited ms", credited < 10_000)
        assertTrue(p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).trackable)
    }

    @Test
    fun `eyes shut in the deliberate test never complete it, however long`() {
        for (withScore in listOf(false, true)) {
            val random = java.util.Random(5)
            val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
            s.start(0); s.skipPhase(0); s.skipPhase(0)
            var t = 0L
            while (t < 2_000) { s.process(sample(t, jitter(random, 0.60f), if (withScore) 0.06f else null)); t += 33 }
            while (t < 130_000) { s.process(sample(t, jitter(random, 0.06f), if (withScore) 0.93f else null)); t += 33 }
            assertEquals("score=$withScore: counted ${s.instructed.size}", 0, s.instructed.size)
            assertEquals(CalibrationSession.Phase.DELIBERATE, s.phase)
        }
    }

    // --- 3. one bad picture in the middle of a rest ----------------------------------------------------------------------

    private fun restWithOneBadFrame(bad: (Long) -> FaceFrame): List<BlinkObservation> {
        val p = TrackingPipeline()
        val found = mutableListOf<BlinkObservation>()
        var t = 0L
        fun feed(fr: FaceFrame) { p.onFrame(fr).blink?.let { found += it } }
        while (t < 3_000) { feed(frame(t, 0.80f)); t += 50 }
        while (t < 7_500) { feed(frame(t, 0.05f)); t += 50 }
        feed(bad(t)); t += 50
        while (t < 8_000) { feed(frame(t, 0.05f)); t += 50 }
        while (t < 10_000) { feed(frame(t, 0.80f)); t += 50 }
        return found
    }

    @Test
    fun `one bad picture while the eyes are shut does not turn their opening into a blink`() {
        assertTrue("no face", restWithOneBadFrame { noFace(it) }.isEmpty())
        assertTrue("too dark", restWithOneBadFrame { frame(it, 0.05f, luma = 10f) }.isEmpty())
        assertTrue("not confident", restWithOneBadFrame { frame(it, 0.05f, confidence = 0.3f) }.isEmpty())
    }

    @Test
    fun `a gap in the pictures in the middle of a rest does not turn the end of the rest into a blink`() {
        val p = TrackingPipeline()
        val found = mutableListOf<BlinkObservation>()
        var t = 0L
        fun feed(fr: FaceFrame) { p.onFrame(fr).blink?.let { found += it } }
        while (t < 3_000) { feed(frame(t, 0.80f)); t += 50 }
        while (t < 6_000) { feed(frame(t, 0.05f)); t += 50 }          // eyes shut
        t = 7_000                                                     // the face goes: nothing arrives for a second
        while (t < 7_600) { feed(frame(t, 0.05f)); t += 50 }          // and they are still shut when it comes back
        while (t < 9_000) { feed(frame(t, 0.80f)); t += 50 }
        assertTrue("the end of a rest after a gap is not a blink: $found", found.isEmpty())
    }

    @Test
    fun `a gap in the pictures in the middle of a deliberate hold does not count either`() {
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
        s.start(0); s.skipPhase(0); s.skipPhase(0)
        var t = 0L
        while (t < 2_000) { s.process(sample(t, 0.60f)); t += 33 }
        while (t < 6_000) { s.process(sample(t, 0.05f)); t += 33 }
        t = 7_000                                                     // nothing arrives for a second
        while (t < 7_800) { s.process(sample(t, 0.05f)); t += 33 }    // shut again after the gap, for 0.8 s
        while (t < 9_000) { s.process(sample(t, 0.60f)); t += 33 }
        assertEquals(0, s.instructed.size)
    }

    @Test
    fun `one bad picture in the middle of a blink does not lose it`() {
        val p = TrackingPipeline()
        var t = 0L
        while (t < 1_500) { p.onFrame(frame(t, 0.80f)); t += 33 }
        p.onFrame(frame(t, 0.05f)); t += 33
        p.onFrame(frame(t, 0.05f, luma = 5f)); t += 33
        p.onFrame(frame(t, 0.05f)); t += 33
        assertNotNull(p.onFrame(frame(t, 0.80f)).blink)
    }

    @Test
    fun `a face that is gone for a while takes the blink in progress with it`() {
        val p = TrackingPipeline()
        var t = 0L
        while (t < 1_500) { p.onFrame(frame(t, 0.80f)); t += 33 }
        p.onFrame(frame(t, 0.05f)); t += 33
        p.onFrame(noFace(t))
        t += 1_500
        assertNull("a pause of a second and a half is not part of the blink", p.onFrame(frame(t, 0.80f)).blink)
    }

    @Test
    fun `one dark picture 2 s before the end of a long closure in the deliberate test does not make it a deliberate blink`() {
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
        s.start(0); s.skipPhase(0); s.skipPhase(0)
        var t = 0L
        while (t < 2_000) { s.process(sample(t, 0.60f)); t += 33 }
        while (t < 12_000) { s.process(sample(t, 0.05f)); t += 33 }
        s.process(sample(t, 0.05f, luma = 10f)); t += 33
        while (t < 14_000) { s.process(sample(t, 0.05f)); t += 33 }
        while (t < 15_000) { s.process(sample(t, 0.60f)); t += 33 }
        assertEquals(0, s.instructed.size)
    }

    // --- 4. a dip that settles is not a blink unless it began like one --------------------------------------------------

    @Test
    fun `one frame past the start threshold and then a lower level is not a blink`() {
        for (step in listOf(66L, 50L, 33L)) {
            val d = BlinkDetector()
            var t = 0L
            while (t < 3_000) { d.onSample(sample(t, 0.80f)); t += step }
            val found = mutableListOf<BlinkObservation>()
            d.onSample(sample(t, 0.50f))?.let { found += it }; t += step
            while (t < 6_000) { d.onSample(sample(t, 0.62f))?.let { found += it }; t += step }
            assertTrue("step $step: $found", found.isEmpty())
        }
    }

    @Test
    fun `one deep glitch frame in a lowered gaze is not a blink either`() {
        // A landmark that jumped for one frame (0.20 of an 0.80 eye: closure 0.75), then the lids at 0.62 as they were.
        val d = BlinkDetector()
        var t = 0L
        while (t < 3_000) { d.onSample(sample(t, 0.62f)); t += 66 }
        d.onSample(sample(t, 0.80f)); t += 66          // the eye had just opened to 0.80...
        while (t < 4_500) { d.onSample(sample(t, 0.80f)); t += 66 }
        val found = mutableListOf<BlinkObservation>()
        d.onSample(sample(t, 0.20f))?.let { found += it }; t += 66
        while (t < 7_000) { d.onSample(sample(t, 0.62f))?.let { found += it }; t += 66 }
        assertTrue("a single frame is not enough to settle on: $found", found.isEmpty())
    }

    @Test
    fun `sustained dips of a quarter of the open level are not blinks, with the noise of a real tracker`() {
        for (fps in listOf(15, 20, 30)) for (share in listOf(0.26f, 0.30f)) {
            val eye = EyeProfile.TYPICAL
            val sim = EyeSimulator(eye, fps, seed = 99, glitchRate = 0.004f)
            for (k in 0 until 60) sim.lower(2_000L + k * 6_000L, durationMs = 4_000, share = share, rampMs = 300)
            val p = TrackingPipeline()
            var phantoms = 0
            for (s in sim.samples(0, 2_000L + 60 * 6_000L)) if (p.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink != null) phantoms++
            assertTrue("$fps fps, dips of ${(share * 100).toInt()} %: $phantoms phantom blinks in 60 dips", phantoms <= 12)
        }
    }

    @Test
    fun `real blinks that land in a lowered gaze are still seen to settle`() {
        // The lids stay at 0.62 of an 0.80 eye after each blink for as long as the gaze is down: the blink ends there.
        val d = BlinkDetector()
        var t = 0L
        while (t < 1_500) { d.onSample(sample(t, 0.80f)); t += 66 }
        val found = mutableListOf<BlinkObservation>()
        d.onSample(sample(t, 0.05f)); t += 66
        d.onSample(sample(t, 0.05f)); t += 66
        while (t < 3_000) { d.onSample(sample(t, 0.62f))?.let { found += it }; t += 66 }
        assertEquals(1, found.size)
    }

    // --- 5. a seed that is right ----------------------------------------------------------------------------------------

    @Test
    fun `a blink under way when the readings take over from a correct seed is still a blink`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        val seq = listOf(0.80f, 0.80f, 0.80f, 0.80f, 0.30f, 0.05f, 0.05f, 0.20f, 0.62f, 0.80f, 0.80f, 0.80f)
        val found = mutableListOf<BlinkObservation>()
        seq.forEachIndexed { i, o -> d.onSample(sample(i * 66L, o))?.let { found += it } }
        assertEquals("15 fps: $found", 1, found.size)
        assertEquals(330L, found[0].durationMs)
        assertTrue(found[0].complete)
    }

    @Test
    fun `the same at 30 frames per second`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        val seq = listOf(0.80f, 0.80f, 0.80f, 0.40f, 0.10f, 0.05f, 0.05f, 0.10f, 0.30f, 0.62f, 0.80f, 0.80f, 0.80f)
        val found = mutableListOf<BlinkObservation>()
        seq.forEachIndexed { i, o -> d.onSample(sample(i * 33L, o))?.let { found += it } }
        assertEquals("30 fps: $found", 1, found.size)
        assertTrue("it is the whole blink, not the end of it: ${found[0]}", found[0].durationMs >= 200 && found[0].depth > 0.85f)
    }

    @Test
    fun `a seed that is wrong is still found out`() {
        val d = BlinkDetector().apply { seed(0.80f) }
        val found = mutableListOf<BlinkObservation>()
        var t = 0L
        while (t < 2_000) { d.onSample(sample(t, 0.52f))?.let { found += it }; t += 66 }
        assertTrue("those frames are not a blink: $found", found.isEmpty())
        assertFalse(d.isClosed)
        d.onSample(sample(t, 0.06f)); t += 66
        d.onSample(sample(t, 0.06f)); t += 66
        assertNotNull(d.onSample(sample(t, 0.52f)))
    }
}

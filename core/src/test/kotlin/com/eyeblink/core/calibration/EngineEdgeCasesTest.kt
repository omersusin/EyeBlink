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
    private fun gazeDown(thresholds: BlinkThresholds, lowered: Float, withScore: Boolean, stepMs: Long = 66, loweredScore: Float = 0.06f): Triple<Int, Int, Double> {
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
            val score = if (withScore) (if (blink) 0.92f else if (t >= 5_000) loweredScore else 0.06f) else null
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

    // --- 6. what the face model really says is not known: nothing may hang on an exact score ------------------------------

    @Test
    fun `eyes shut are shut whatever the shut score reads, as long as the lids agree`() {
        for (shutScore in listOf(0.60f, 0.72f, 0.95f)) {
            val random = java.util.Random(13)
            val p = TrackingPipeline(BlinkThresholds.fromCalibration(openLevel = 0.60f, fullDepth = 0.75f))
            val found = mutableListOf<BlinkObservation>()
            var t = 0L
            while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).blink?.let { found += it }; t += 66 }
            while (t < 123_000) { p.onFrame(frame(t, jitter(random, 0.05f), shutScore)).blink?.let { found += it }; t += 66 }
            assertTrue("shut score $shutScore: blinks while shut: ${found.map { it.startMs }.take(10)}", found.isEmpty())
            val credited = p.rate.observed(t).trackedMs
            assertTrue("shut score $shutScore: $credited ms credited", credited < 10_000)
        }
    }

    @Test
    fun `the deliberate test is not completed by shut eyes whatever the shut score reads`() {
        for (shutScore in listOf(0.60f, 0.70f, 0.95f)) {
            val random = java.util.Random(5)
            val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
            s.start(0); s.skipPhase(0); s.skipPhase(0)
            var t = 0L
            while (t < 2_000) { s.process(sample(t, jitter(random, 0.60f), 0.06f)); t += 33 }
            while (t < 60_000) { s.process(sample(t, jitter(random, 0.06f), shutScore)); t += 33 }
            assertEquals("shut score $shutScore: counted ${s.instructed.size}", 0, s.instructed.size)
        }
    }

    @Test
    fun `a rest that begins while the camera is off is a rest when the next burst finds it`() {
        val random = java.util.Random(17)
        val p = TrackingPipeline(BlinkThresholds.fromCalibration(openLevel = 0.60f, fullDepth = 0.75f))
        val found = mutableListOf<BlinkObservation>()
        var t = 0L
        // One burst of open eyes, then the camera sleeps and wakes for eight seconds every 53 s while the eyes are shut.
        while (t < 8_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).blink?.let { found += it }; t += 66 }
        val opened = found.size
        var frames = 0
        var counted = 0
        for (k in 1..3) {
            t = k * 53_000L
            val from = t
            while (t < from + 8_000) {
                val ev = p.onFrame(frame(t, jitter(random, 0.05f), 0.95f))
                ev.blink?.let { found += it }
                frames++
                if (ev.trackable) counted++
                t += 66
            }
        }
        assertEquals("blinks in the bursts of a rest: ${found.drop(opened).map { it.startMs }}", opened, found.size)
        // The first second of each burst is a closure that has not outlasted a blink yet; after that nothing is evidence.
        assertTrue("$counted of $frames frames of a rest counted as evidence", counted < frames * 0.25)
    }

    @Test
    fun `a score that rises while the eyes are open and the gaze is down does not blind the detector for long`() {
        val random = java.util.Random(19)
        val p = TrackingPipeline(BlinkThresholds.fromCalibration(openLevel = 0.60f, fullDepth = 0.75f))
        val found = mutableListOf<BlinkObservation>()
        val blinkTimes = (1..39).map { it * 3_000L }
        var t = 0L
        while (t < 120_000) {
            val base = if (t < 5_000) 0.60f else 0.42f
            val restScore = if (t < 5_000) 0.06f else 0.85f
            val blink = blinkTimes.any { t >= it && t < it + 3 * 66 }
            p.onFrame(frame(t, jitter(random, if (blink) 0.05f else base), if (blink) 0.95f else restScore)).blink?.let { found += it }
            t += 66
        }
        val late = blinkTimes.filter { it >= 90_000 }
        val hit = late.count { e -> found.any { kotlin.math.abs(it.startMs - e) <= 400 } }
        assertTrue("found $hit of ${late.size} blinks in the last half minute", hit >= late.size * 0.8)
    }

    @Test
    fun `squeezed shut and then relaxed shut, with the score saying so, makes no blinks`() {
        // Lids that relax to a quarter of their closure are not nearly enough to be called shut, whatever the score says: the
        // detector learns that as the new open level, and the score with it, and counts no blink for it.
        val random = java.util.Random(23)
        val p = TrackingPipeline()
        val found = mutableListOf<BlinkObservation>()
        var t = 0L
        while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).blink?.let { found += it }; t += 66 }
        while (t < 4_000) { p.onFrame(frame(t, jitter(random, 0.05f), 0.95f)).blink?.let { found += it }; t += 66 }
        while (t < 60_000) { p.onFrame(frame(t, jitter(random, 0.45f), 0.95f)).blink?.let { found += it }; t += 66 }
        assertTrue("blinks while the eyes were shut: ${found.map { it.startMs }.take(10)}", found.size <= 1)
    }

    // --- 9. a lowered gaze with a score in the middle of its range is not shut eyes ---------------------------------------

    private fun gazeLowered(thresholds: BlinkThresholds, loweredLevel: Float, openScore: Float, seconds: Int = 120): Triple<Int, Int, Double> {
        val random = java.util.Random(41)
        val p = TrackingPipeline(thresholds)
        val found = mutableListOf<BlinkObservation>()
        val blinkTimes = (1..(seconds / 3 - 1)).map { it * 3_000L }
        var frames = 0
        var counted = 0
        var t = 0L
        while (t < seconds * 1_000L) {
            val lowered = t >= 5_000
            val base = if (lowered) loweredLevel else 0.60f
            val rest = if (lowered) openScore else 0.06f
            val blink = blinkTimes.any { t >= it && t < it + 3 * 66 }
            val ev = p.onFrame(frame(t, jitter(random, if (blink) 0.05f else base), if (blink) 0.95f else rest + (random.nextGaussian() * 0.03).toFloat().coerceIn(-0.06f, 0.06f)))
            ev.blink?.let { found += it }
            frames++
            if (ev.trackable) counted++
            t += 66
        }
        val late = blinkTimes.filter { it >= 40_000 }
        val hit = late.count { e -> found.any { kotlin.math.abs(it.startMs - e) <= 400 } }
        return Triple(hit, late.size, counted.toDouble() / frames)
    }

    @Test
    fun `a lowered gaze whose open-eye score sits in the middle of its range does not blind the detector`() {
        val defaults = BlinkThresholds()
        val fitted = BlinkThresholds.fromCalibration(openLevel = 0.60f, fullDepth = 0.55f)
        for ((name, thr, level) in listOf(Triple("defaults", defaults, 0.378f), Triple("fitted", fitted, 0.42f))) {
            for (openScore in listOf(0.55f, 0.60f, 0.70f)) {
                val (hit, total, counted) = gazeLowered(thr, level, openScore)
                assertTrue("$name, score $openScore: found $hit of $total blinks after 40 s", hit >= total * 0.8)
                assertTrue("$name, score $openScore: only $counted of the frames counted", counted > 0.85)
            }
        }
    }

    // --- 10. a long rest is a rest for as long as its two ways of telling agree -----------------------------------------

    @Test
    fun `a long rest stays a rest when the score is missing on some frames or reads low`() {
        for ((name, shutScore, missing) in listOf(Triple("score missing on 1 %", 0.95f, 0.01f), Triple("score 0.60", 0.60f, 0f), Triple("score 0.60, missing on 5 %", 0.60f, 0.05f))) {
            val random = java.util.Random(43)
            val p = TrackingPipeline()
            val found = mutableListOf<BlinkObservation>()
            var t = 0L
            while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)).blink?.let { found += it }; t += 33 }
            var credited = 0
            var frames = 0
            while (t < 123_000) {
                val score = if (random.nextFloat() < missing) null else (shutScore + (random.nextGaussian() * 0.05).toFloat()).coerceIn(0f, 1f)
                val ev = p.onFrame(frame(t, jitter(random, 0.05f), score))
                ev.blink?.let { found += it }
                frames++
                if (ev.trackable) credited++
                t += 33
            }
            assertTrue("$name: blinks while shut: ${found.map { it.startMs }.take(8)}", found.isEmpty())
            assertTrue("$name: $credited of $frames frames of the rest counted as evidence", credited < frames * 0.1)
        }
    }

    // --- 11. the deliberate step with a score that sits high and lids that do not move ---------------------------------

    @Test
    fun `a score that sits high and lids that do not move do not lock the deliberate test`() {
        val random = java.util.Random(47)
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 3)
        s.start(0); s.skipPhase(0); s.skipPhase(0)
        var t = 0L
        while (t < 2_000) { s.process(sample(t, jitter(random, 0.60f), 0.06f)); t += 33 }
        // From here the open-eye score reads 0.85 and the lids stay where they were; the person blinks on purpose every 2.5 s.
        while (t < 90_000 && s.instructed.size < 3) {
            val blinkNow = (t - 2_000) % 2_500 < 400
            val v = if (blinkNow) 0.05f else 0.60f
            s.process(sample(t, jitter(random, v), if (blinkNow) 0.95f else 0.85f))
            t += 33
        }
        assertTrue("counted ${s.instructed.size} deliberate blinks by ${t / 1000} s", s.instructed.size >= 3 && t < 30_000)
    }

    // --- 12. "Higher" must find more of what a noisy eye does - and not of what it does not do ---------------------------

    private val noisyHeavy = EyeProfile("noisy heavy lids", openEar = 0.14f, closedEar = 0.075f, noise = 0.105f)
    private val noisyMonolid = EyeProfile("noisy monolid", openEar = 0.17f, closedEar = 0.08f, noise = 0.09f)

    /** What a pipeline finds in four two-minute streams of [eye] with 30 blinks each: the blinks found, and everything it reported. */
    private fun blinkStreams(eye: EyeProfile, thresholds: BlinkThresholds, sensitivity: Float, withScore: Boolean): Pair<Int, Int> {
        var hit = 0
        var reported = 0
        for (seed in 1L..4L) {
            val sim = EyeSimulator(eye, 20, seed, withBlinkScore = withScore)
            val times = (0 until 30).map { 3_000L + it * 3_700L }
            times.forEachIndexed { i, tt -> sim.blink(tt, depth = if (i % 2 == 0) 0.55f else 0.7f) }
            val p = TrackingPipeline(thresholds, sensitivity)
            val found = mutableListOf<Long>()
            for (smp in sim.samples(0, 120_000)) p.onFrame(FaceFrame(smp.timestampMs, smp, good, smp.illuminance)).blink?.let { found += it.startMs }
            hit += times.count { e -> found.any { kotlin.math.abs(it - e) <= 400 } }
            reported += found.size
        }
        return hit to reported
    }

    /** What a pipeline reports in four two-minute streams of [eye] that does not blink at all. */
    private fun phantoms(eye: EyeProfile, thresholds: BlinkThresholds, sensitivity: Float, fps: Int = 20): Int {
        var n = 0
        for (seed in 1L..4L) {
            val p = TrackingPipeline(thresholds, sensitivity)
            for (smp in EyeSimulator(eye, fps, seed, withBlinkScore = true).samples(0, 120_000)) {
                if (p.onFrame(FaceFrame(smp.timestampMs, smp, good, smp.illuminance)).blink != null) n++
            }
        }
        return n
    }

    @Test
    fun `where the eyelids are all there is, a higher sensitivity finds more blinks of a noisy heavy-lidded eye and not a flood of false ones`() {
        // The eyelids of an eye this noisy are a poor witness (Auto finds a third of the blinks; with the face model's score, which
        // is what a calibration adds, nearly all). "Higher" is the person's way of asking for more of them at the price of some false
        // ones: the gate gives way by the square root of the factor - held in place it finds 42 of 120 and the sensitivity does
        // nothing, given way in full it reports 44 false blinks in eight minutes and a flood on a noisier eye.
        val auto = blinkStreams(noisyHeavy, BlinkThresholds(), 1f, withScore = false)
        val higher = blinkStreams(noisyHeavy, BlinkThresholds(), 0.7f, withScore = false)
        assertTrue("Auto finds ${auto.first}, Higher ${higher.first} of 120 blinks", higher.first >= auto.first + 20 && higher.first >= 80)
        assertTrue("Higher reported ${higher.second} for ${higher.first} blinks found", higher.second - higher.first <= 35)
    }

    @Test
    fun `the noise of an eye that does not blink is not counted as blinks, at any sensitivity`() {
        val fitted = BlinkThresholds.fromCalibration(openLevel = 0.31f, fullDepth = 0.50f)
        val auto = phantoms(noisyHeavy, fitted, 1f)
        val higher = phantoms(noisyHeavy, fitted, 0.7f)
        assertTrue("Auto reported $auto blinks in 8 minutes of an eye that does not blink", auto <= 12)
        assertTrue("Higher reported $higher", higher <= 36)
        for (sensitivity in listOf(1f, 0.7f)) {
            val (hit, reported) = blinkStreams(noisyHeavy, fitted, sensitivity, withScore = true)
            assertTrue("sensitivity $sensitivity: found $hit of 120", hit >= 100)
            assertTrue("sensitivity $sensitivity: reported $reported for $hit found", reported - hit <= 36)
        }
    }

    @Test
    fun `open eyes that do not blink never complete the deliberate test, however noisy the tracker`() {
        for (eye in listOf(noisyHeavy, noisyMonolid)) for (sensitivity in listOf(1f, 0.7f)) for (seed in 1L..3L) {
            val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 5, sensitivity = sensitivity)
            s.start(0); s.skipPhase(0); s.skipPhase(0)
            for (smp in EyeSimulator(eye, 30, seed, withBlinkScore = true).samples(0, 120_000)) s.process(smp)
            assertTrue("${eye.name} at $sensitivity, seed $seed: ${s.instructed.size} of 5 deliberate blinks were counted from noise", s.instructed.size < 3)
        }
    }

    @Test
    fun `the ordinary eyes that move least are not walked through the deliberate test by noise either`() {
        for (eye in listOf(EyeProfile.MONOLID, EyeProfile.HEAVY_LIDS)) for (sensitivity in listOf(1f, 0.7f)) for (seed in 1L..6L) {
            val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 5, sensitivity = sensitivity)
            s.start(0); s.skipPhase(0); s.skipPhase(0)
            for (smp in EyeSimulator(eye, 30, seed, withBlinkScore = true).samples(0, 120_000)) s.process(smp)
            assertTrue("${eye.name} at $sensitivity, seed $seed: ${s.instructed.size} of 5 deliberate blinks were counted from noise", s.instructed.size < 5)
        }
    }

    // --- 13. one deliberate hold is one deliberate blink, whatever the score does in the middle of it ---------------------

    @Test
    fun `a deliberate hold is counted once when the score is missing on some of its frames`() {
        val random = java.util.Random(53)
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 4)
        s.start(0); s.skipPhase(0); s.skipPhase(0)
        var t = 0L
        while (t < 3_000) { s.process(sample(t, jitter(random, 0.31f), 0.06f)); t += 33 }
        for (hold in 1..3) {
            val until = t + 2_500
            while (t < until) {
                val score = if (random.nextBoolean()) 0.9f else null
                s.process(sample(t, jitter(random, 0.22f), score)); t += 33
            }
            val open = t + 3_000
            while (t < open) { s.process(sample(t, jitter(random, 0.31f), 0.06f)); t += 33 }
            assertEquals("after hold $hold", hold, s.instructed.size)
        }
    }

    // --- 7. the open level of an eye is remembered between bursts -------------------------------------------------------

    @Test
    fun `a burst starts from the open level the last one ended with, however many times the detector has relearned`() {
        val random = java.util.Random(29)
        val p = TrackingPipeline(BlinkThresholds.fromCalibration(openLevel = 0.60f, fullDepth = 0.75f))
        var t = 0L
        // A change of posture: the lids settle at 0.34 and stay, the detector relearns what open is, the burst ends...
        while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.60f), 0.06f)); t += 66 }
        while (t < 12_000) { p.onFrame(frame(t, jitter(random, 0.34f), 0.06f)); t += 66 }
        // ...and 45 s later the next one opens with a blink straight away: the second frame is the closing one.
        t = 57_000
        p.onFrame(frame(t, 0.34f, 0.06f)); t += 66
        p.onFrame(frame(t, 0.05f, 0.95f)); t += 66
        p.onFrame(frame(t, 0.05f, 0.95f)); t += 66
        assertNotNull("no blind frames at the start of the burst", p.onFrame(frame(t, 0.34f, 0.06f)).blink)
    }

    // --- 8. pictures with no face at all are not tracked time -----------------------------------------------------------

    @Test
    fun `pictures with no face are not credited as tracked time by the calibration`() {
        val s = CalibrationSession(videoMs = 60_000, readingMs = 60_000, deliberateBlinksRequired = 3)
        s.start(0)
        var t = 0L
        var n = 0
        while (t < 10_000) {
            if (n++ % 3 == 2) s.onNoFace() else s.process(sample(t, 0.60f))
            t += 33
        }
        val tracked = s.result().phases.getValue(CalibrationSession.Phase.VIDEO).trackedMs
        assertTrue("$tracked ms tracked of 10 s with every third picture empty", tracked in 3_000L..7_000L)
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

    // --- 14. a rest whose shut score reads low is still a rest -------------------------------------------------------------

    @Test
    fun `a rest whose shut score reads low is held for as long as lids alone are`() {
        for (shutScore in listOf(0.30f, 0.45f, 0.50f)) {
            val random = java.util.Random(61)
            val p = TrackingPipeline()
            var t = 0L
            while (t < 3_000) { p.onFrame(frame(t, jitter(random, 0.667f), 0.05f)); t += 50 }
            val found = mutableListOf<BlinkObservation>()
            var credited = 0
            var frames = 0
            while (t < 63_000) {
                val score = (shutScore + (random.nextGaussian() * 0.03).toFloat()).coerceIn(0f, 1f)
                val ev = p.onFrame(frame(t, jitter(random, 0.156f), score))
                ev.blink?.let { found += it }
                frames++
                if (ev.trackable) credited++
                t += 50
            }
            assertTrue("score $shutScore: blinks while shut: ${found.map { it.startMs }.take(6)}", found.isEmpty())
            assertTrue("score $shutScore: $credited of $frames frames of a minute's rest counted as evidence", credited < frames * 0.6)
            assertTrue("score $shutScore: lids alone hold the eyes for half a minute, not for ever ($credited of $frames)", credited > frames * 0.3)
        }
    }

    // --- 15. the meter shows what the eyelids need ----------------------------------------------------------------------------

    @Test
    fun `the closure the meter says counts is the closure that starts a blink, with a face model or without`() {
        val random = java.util.Random(67)
        // The last ease step of the deliberate test (start 0.19), eyelids that scatter by 0.03 of an 0.31 level, a flat score.
        val d = BlinkDetector(DetectorSettings(start = 0.19f, end = 0.095f, full = 0.30f, useBlinkScore = true))
        fun reading(t: Long, level: Float) = EyeOpennessSample(t, jitter(random, level, 0.03f), jitter(random, level, 0.03f), 0.9f, 120f, blinkScore = 0.06f)
        var t = 0L
        while (t < 8_000) { d.onSample(reading(t, 0.311f)); t += 33 }
        val counts = d.startDepth
        assertTrue("the meter says $counts counts, the setting is 0.19: the jitter has to show in it", counts > 0.3f)

        // Lids that close by less than that do not start a blink, by more do: judged on a steady closure, so the noise cannot decide.
        fun steady(depth: Float): Boolean {
            val probe = BlinkDetector(DetectorSettings(start = 0.19f, end = 0.095f, full = 0.30f, useBlinkScore = true))
            val r = java.util.Random(71)
            fun rd(tt: Long, level: Float) = EyeOpennessSample(tt, jitter(r, level, 0.03f), jitter(r, level, 0.03f), 0.9f, 120f, blinkScore = 0.06f)
            var tt = 0L
            while (tt < 8_000) { probe.onSample(rd(tt, 0.311f)); tt += 33 }
            repeat(6) { probe.onSample(rd(tt, 0.311f * (1f - depth))); tt += 33 }
            return probe.isClosed
        }
        assertFalse("a closure of ${counts - 0.08f} is below what the meter says counts", steady(counts - 0.08f))
        assertTrue("a closure of ${counts + 0.12f} is above it", steady(counts + 0.12f))
    }

    // --- 16. a slow camera still tells a rest ----------------------------------------------------------------------------------

    @Test
    fun `eyes shut are held however the pictures fall at six and a half frames a second`() {
        // 150 ms between pictures, give or take a few: whether the 900 ms after which a closure is judged fall on the sixth
        // picture of it or the seventh decides how many pictures there are to judge it by.
        var held = 0
        for (seed in 1L..30L) {
            val random = java.util.Random(seed)
            val p = TrackingPipeline(BlinkThresholds())
            var t = 0L
            fun next() { t += 150 + random.nextInt(11) - 5 }
            while (t < 4_000) { p.onFrame(frame(t, jitter(random, 0.66f, 0.015f), 0.05f)); next() }
            var credited = 0
            var frames = 0
            while (t < 24_000) {
                val ev = p.onFrame(frame(t, jitter(random, 0.15f, 0.015f), 0.9f))
                frames++
                if (ev.trackable) credited++
                next()
            }
            if (credited < frames * 0.1) held++
        }
        assertTrue("$held of 30 twenty-second rests were held", held >= 28)
    }

    // --- 17. a gaze lowered a long way is not eyes shut, whatever the score does a little ------------------------------

    @Test
    fun `a gaze lowered by half is the new open level, with the score barely moved or missing`() {
        // 0.40 -> 0.20 is a closure of 0.5: far more than a phone takes, still less than shut eyes of nearly any kind.
        for ((withScore, loweredScore) in listOf(false to 0.06f, true to 0.06f, true to 0.25f)) {
            val (hit, expected, counted) = gazeDown(BlinkThresholds(), lowered = 0.20f, withScore = withScore, loweredScore = loweredScore)
            assertTrue("score=$withScore ($loweredScore): found $hit of $expected", hit >= expected * 0.8)
            assertTrue("score=$withScore ($loweredScore): only $counted of the frames counted", counted > 0.9)
        }
    }

    // --- 18. quick deliberate blinks are counted at once -------------------------------------------------------------------

    /** Seconds the deliberate step takes with a blink every 2.5 s that is over in [closeMs] + [openMs], or null when it never finishes. */
    private fun quickDeliberate(eye: EyeProfile, closeMs: Long, openMs: Long, fps: Int, seed: Long): Double? {
        val sim = EyeSimulator(eye, fps, seed, withBlinkScore = true)
        (0 until 40).forEach { sim.blink(2_000L + it * 2_500L, depth = 1f, closeMs = closeMs, holdMs = 0, openMs = openMs) }
        val s = CalibrationSession(videoMs = 1_000, readingMs = 1_000, deliberateBlinksRequired = 5)
        s.start(0); s.skipPhase(0); s.skipPhase(0)
        for (smp in sim.samples(0, 120_000)) {
            s.process(smp)
            if (s.phase == CalibrationSession.Phase.COMPLETE) return smp.timestampMs / 1000.0
        }
        return null
    }

    @Test
    fun `a deliberate blink as quick as one that happens by itself is counted at the speed the step is looked at with`() {
        val barely = EyeProfile("barely moving", openEar = 0.14f, closedEar = 0.10f, noise = 0.04f)
        for (eye in listOf(EyeProfile.TYPICAL, EyeProfile.MONOLID, barely)) for ((close, open) in listOf(40L to 80L, 60L to 110L)) for (seed in 1L..3L) {
            val secs = quickDeliberate(eye, close, open, fps = 30, seed = seed)
            assertTrue("${eye.name}, a blink of ${close + open} ms, seed $seed: finished after $secs s", secs != null && secs < 16.0)
        }
    }

    @Test
    fun `and at half that speed a quick blink that goes deep is counted too`() {
        for (seed in 1L..3L) {
            val secs = quickDeliberate(EyeProfile.TYPICAL, 40, 80, fps = 15, seed = seed)
            assertTrue("seed $seed: finished after $secs s", secs != null && secs < 35.0)
        }
    }
}

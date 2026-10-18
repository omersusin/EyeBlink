package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reason the engine measures every eye against its own open level: people's eyes differ by a factor of two,
 * and a test that only some of them can pass is a broken test. These run the whole path - simulated tracker
 * readings, the calibration session, the fitted thresholds, the tracking pipeline - for wide, typical, narrow,
 * monolid, heavy-lidded and asymmetric eyes at 15 and 30 frames per second.
 */
class EyeShapesTest {

    private val good = QualityReport(0.9f, emptySet())

    // --- natural blinks, with the thresholds nobody has fitted yet ------------------------------------------------

    private class Counted(val blinks: List<BlinkObservation>, val atMs: List<Long>) {
        fun found(expected: List<Long>, toleranceMs: Long = 400): Int =
            expected.count { e -> atMs.any { kotlin.math.abs(it - e) <= toleranceMs } }
    }

    private fun natural(
        eye: EyeProfile,
        fps: Int,
        thresholds: BlinkThresholds = BlinkThresholds(),
        sensitivity: Float = 1f,
        seed: Long = 7,
        blinkScore: Boolean = false,
    ): Pair<Counted, List<Long>> {
        val times = (0 until 15).map { 3_000L + it * 3_700L + (it % 4) * 230L }
        val sim = EyeSimulator(eye, fps, seed, withBlinkScore = blinkScore)
        times.forEachIndexed { i, t -> sim.blink(t, depth = if (i % 5 == 4) 0.55f else 1f) }
        // Looking down at the phone for six seconds, which is not a blink.
        sim.lower(20_000, durationMs = 6_000, share = 0.2f)
        val pipeline = TrackingPipeline(thresholds, sensitivity)
        val found = mutableListOf<BlinkObservation>()
        for (s in sim.samples(0, 62_000)) {
            pipeline.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink?.let { found += it }
        }
        return Counted(found, found.map { it.startMs }) to times
    }

    // A tracker's noise decides whether a single shallow blink is seen; three streams per eye say what is typical.
    private val seeds = listOf(7L, 8L, 9L)

    @Test
    fun `natural blinks are found on every kind of eye at 30 fps`() {
        for (eye in EyeProfile.ALL) {
            var hit = 0
            var total = 0
            for (seed in seeds) {
                val (counted, expected) = natural(eye, fps = 30, seed = seed)
                hit += counted.found(expected)
                total += expected.size
                assertTrue("${eye.name}: ${counted.blinks.size} blinks reported for ${expected.size} real ones", counted.blinks.size <= expected.size + 2)
            }
            assertTrue("${eye.name}: found $hit of $total (full closure ${eye.fullClosure})", hit >= total * 0.8)
        }
    }

    @Test
    fun `natural blinks are found on every kind of eye at 15 fps`() {
        for (eye in EyeProfile.ALL) {
            var hit = 0
            var total = 0
            for (seed in seeds) {
                val (counted, expected) = natural(eye, fps = 15, seed = seed)
                hit += counted.found(expected, toleranceMs = 500)
                total += expected.size
                assertTrue("${eye.name}: ${counted.blinks.size} blinks reported for ${expected.size} real ones", counted.blinks.size <= expected.size + 3)
            }
            assertTrue("${eye.name}: found $hit of $total", hit >= total * 0.75)
        }
    }

    @Test
    fun `a still face reports no blinks however narrow the eyes`() {
        for (eye in EyeProfile.ALL) {
            val sim = EyeSimulator(eye, fps = 30, seed = 99)
            val pipeline = TrackingPipeline()
            var blinks = 0
            for (s in sim.samples(0, 90_000)) if (pipeline.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink != null) blinks++
            assertTrue("${eye.name}: $blinks phantom blinks in 90 s", blinks <= 1)
        }
    }

    // --- the deliberate test ---------------------------------------------------------------------------------------------

    private class Run(val session: CalibrationSession, val completedAtMs: Long?, val deliberateStartMs: Long, val hints: Set<CalibrationSession.Hint>)

    /**
     * Two seconds of video and reading are not enough to learn from, but they are enough to learn what the open eye
     * looks like; then [attempts] deliberate blinks, one every 2.5 s, each [depth] deep and held for [holdMs].
     */
    private fun calibrate(
        eye: EyeProfile,
        fps: Int = 30,
        depth: Float = 1f,
        holdMs: Long = 350,
        attempts: Int = 16,
        blinkScore: Boolean = false,
        seed: Long = 7,
        sensitivity: Float = 1f,
    ): Run {
        val session = CalibrationSession(videoMs = 60_000, readingMs = 60_000, deliberateBlinksRequired = 5, sensitivity = sensitivity)
        val sim = EyeSimulator(eye, fps, seed, withBlinkScore = blinkScore)
        sim.blink(2_500).blink(6_200)
        val deliberateStart = 10_000L
        for (i in 0 until attempts) sim.deliberate(deliberateStart + 1_500 + i * 2_500L, depth, holdMs)
        val end = deliberateStart + 1_500 + attempts * 2_500L + 1_000
        session.start(0)
        var completedAt: Long? = null
        val hints = mutableSetOf<CalibrationSession.Hint>()
        var phase = 0
        for (s in sim.samples(0, end)) {
            if (phase == 0 && s.timestampMs >= 8_000) { session.skipPhase(s.timestampMs); phase = 1 }
            if (phase == 1 && s.timestampMs >= deliberateStart) { session.skipPhase(s.timestampMs); phase = 2 }
            session.process(s)
            if (session.phase == CalibrationSession.Phase.DELIBERATE) hints += session.hint
            if (completedAt == null && session.phase == CalibrationSession.Phase.COMPLETE) completedAt = s.timestampMs
        }
        return Run(session, completedAt, deliberateStart, hints)
    }

    @Test
    fun `every kind of eye can pass the deliberate blink test`() {
        for (eye in EyeProfile.ALL) {
            for (fps in listOf(15, 30)) {
                val run = calibrate(eye, fps)
                assertNotNull("${eye.name} at $fps fps never finished the deliberate test", run.completedAtMs)
                val took = (run.completedAtMs!! - run.deliberateStartMs) / 1000
                assertTrue("${eye.name} at $fps fps needed ${took}s", took <= 30)
            }
        }
    }

    @Test
    fun `a blink held for a long time counts when it is deliberate`() {
        val run = calibrate(EyeProfile.TYPICAL, holdMs = 1_600)
        assertNotNull(run.completedAtMs)
        assertEquals(5, run.session.instructed.size)
    }

    @Test
    fun `a person whose eyes only half close can still finish, because the test eases`() {
        for (eye in listOf(EyeProfile.TYPICAL, EyeProfile.NARROW, EyeProfile.MONOLID)) {
            val run = calibrate(eye, depth = 0.45f)
            assertNotNull("${eye.name} with half-closed blinks never finished", run.completedAtMs)
        }
    }

    @Test
    fun `a person who barely moves their lids is told to close them more`() {
        val run = calibrate(EyeProfile.TYPICAL, depth = 0.12f, attempts = 6)
        assertTrue("hints: ${run.hints}", CalibrationSession.Hint.CLOSE_MORE in run.hints || CalibrationSession.Hint.EASED in run.hints)
        assertNull("nothing so shallow should have counted", run.completedAtMs)
    }

    @Test
    fun `the deliberate blinks are tagged and kept out of the natural statistics`() {
        val run = calibrate(EyeProfile.NARROW)
        assertTrue(run.session.instructed.all { it.instructed })
        assertTrue(run.session.spontaneous.none { it.instructed })
        assertTrue("the two natural blinks of the video phase are kept", run.session.spontaneous.size in 1..4)
    }

    @Test
    fun `the face model's blink score lets a very shallow closure pass at once`() {
        // Open 0.10, shut 0.07: a full blink moves the eyelid reading by only 30 %.
        val eye = EyeProfile("barely moving", openEar = 0.10f, closedEar = 0.07f, noise = 0.03f)
        val withScore = calibrate(eye, blinkScore = true)
        assertNotNull(withScore.completedAtMs)
        val without = calibrate(eye, blinkScore = false)
        if (without.completedAtMs != null) {
            assertTrue("the score is never slower", withScore.completedAtMs!! <= without.completedAtMs!!)
        }
    }

    // --- what the test learns ----------------------------------------------------------------------------------------

    @Test
    fun `the thresholds are fitted to how far this person's eyes close`() {
        val wide = calibrate(EyeProfile.WIDE).session.result().profile.thresholds
        val narrow = calibrate(EyeProfile.MONOLID).session.result().profile.thresholds
        assertTrue(wide.fitted)
        assertTrue(narrow.fitted)
        assertTrue("narrow eyes need a lower start than wide ones (${narrow.start} vs ${wide.start})", narrow.start < wide.start)
        assertTrue(narrow.end < narrow.start && narrow.start < narrow.full)
        assertTrue(narrow.openLevel > 0f)
    }

    @Test
    fun `fitted thresholds find the natural blinks of the same person`() {
        for (eye in listOf(EyeProfile.NARROW, EyeProfile.MONOLID, EyeProfile.HEAVY_LIDS, EyeProfile.ASYMMETRIC)) {
            val fitted = calibrate(eye).session.result().profile.thresholds
            val (counted, expected) = natural(eye, fps = 30, thresholds = fitted)
            val hit = counted.found(expected)
            assertTrue("${eye.name}: found $hit of ${expected.size} with ${fitted}", hit >= expected.size * 0.85)
            assertTrue("${eye.name}: ${counted.blinks.size} reported for ${expected.size}", counted.blinks.size <= expected.size + 2)
        }
    }

    @Test
    fun `a higher sensitivity finds more of the shallow blinks of a heavy lidded eye`() {
        val eye = EyeProfile.HEAVY_LIDS
        val (normal, expected) = natural(eye, fps = 30)
        val (higher, _) = natural(eye, fps = 30, sensitivity = 0.7f)
        assertTrue("normal ${normal.found(expected)} vs higher ${higher.found(expected)}", higher.found(expected) >= normal.found(expected))
    }

    @Test
    fun `the fitted open level lets the next session start without a warm-up`() {
        val eye = EyeProfile.NARROW
        val fitted = calibrate(eye).session.result().profile.thresholds
        assertTrue(fitted.openLevel > 0f)
        val sim = EyeSimulator(eye, fps = 30, seed = 3).blink(200).blink(2_000)
        val pipeline = TrackingPipeline(fitted)
        val found = mutableListOf<BlinkObservation>()
        for (s in sim.samples(0, 3_500)) pipeline.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink?.let { found += it }
        assertTrue("the blink 0.2 s into the session is already seen: ${found.map { it.startMs }}", found.any { it.startMs < 700 })
    }

    @Test
    fun `an eye whose lid barely moves is found by a tracker that listens to the score it was fitted with`() {
        // Open 0.14, shut 0.10: a full blink moves the eyelid reading by 29 % and no more. What tells this eye's blinks
        // apart is the face model's score, and the test was passed on it; the tracker has to look at it as well.
        val eye = EyeProfile("barely moving", openEar = 0.14f, closedEar = 0.10f, noise = 0.04f)
        val fitted = calibrate(eye, blinkScore = true).session.result().profile.thresholds
        assertTrue(fitted.fitted)
        assertTrue("the tracker uses what the fit used", fitted.settings().useBlinkScore)
        val (counted, expected) = natural(eye, fps = 30, thresholds = fitted, blinkScore = true)
        assertTrue("found ${counted.found(expected)} of ${expected.size} with $fitted", counted.found(expected) >= expected.size * 0.8)
        assertTrue("${counted.blinks.size} reported for ${expected.size}", counted.blinks.size <= expected.size + 2)
    }

    @Test
    fun `a fit made with the score does not turn a still face into blinks`() {
        for (eye in EyeProfile.ALL) {
            val fitted = calibrate(eye, blinkScore = true).session.result().profile.thresholds
            val sim = EyeSimulator(eye, fps = 30, seed = 77, withBlinkScore = true)
            val pipeline = TrackingPipeline(fitted)
            var blinks = 0
            for (s in sim.samples(0, 90_000)) if (pipeline.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink != null) blinks++
            assertTrue("${eye.name}: $blinks phantom blinks in 90 s with $fitted", blinks <= 1)
        }
    }

    // --- eyes that are shut, faces that go away ---------------------------------------------------------------------------

    @Test
    fun `eyes shut for a long time cause no blinks on any kind of eye, and the first blink after they open is seen`() {
        for (eye in EyeProfile.ALL) {
            val sim = EyeSimulator(eye, fps = 30, seed = 21)
            sim.blink(3_000)
            sim.blink(8_000, holdMs = 25_000)          // shut from 8 s to 33 s
            sim.blink(36_000).blink(38_500)
            val pipeline = TrackingPipeline()
            val found = mutableListOf<BlinkObservation>()
            for (s in sim.samples(0, 42_000)) pipeline.onFrame(FaceFrame(s.timestampMs, s, good, s.illuminance)).blink?.let { found += it }
            val starts = found.map { it.startMs }
            assertEquals("${eye.name}: blinks while the eyes were shut: $starts", 0, found.count { it.startMs in 9_500L..32_500L })
            assertTrue("${eye.name}: the blink 3 s after they opened is seen: $starts", found.any { it.startMs in 35_500L..36_500L })
            assertTrue("${eye.name}: and the next one: $starts", found.any { it.startMs in 38_000L..39_000L })
        }
    }

    @Test
    fun `shutting the eyes during the deliberate test is not a deliberate blink`() {
        for (eye in EyeProfile.ALL) {
            val session = CalibrationSession(videoMs = 60_000, readingMs = 60_000, deliberateBlinksRequired = 5)
            val sim = EyeSimulator(eye, fps = 30, seed = 31).blink(2_500).blink(6_200).blink(11_000, holdMs = 24_000)
            session.start(0)
            var phase = 0
            for (s in sim.samples(0, 35_000)) {
                if (phase == 0 && s.timestampMs >= 8_000) { session.skipPhase(s.timestampMs); phase = 1 }
                if (phase == 1 && s.timestampMs >= 10_000) { session.skipPhase(s.timestampMs); phase = 2 }
                session.process(s)
            }
            assertEquals("${eye.name}: eyes shut for 24 s counted as ${session.instructed.size} deliberate blinks", 0, session.instructed.size)
            assertEquals(CalibrationSession.Phase.DELIBERATE, session.phase)
        }
    }

    @Test
    fun `an unusable deliberate test leaves the defaults rather than invented thresholds`() {
        val run = calibrate(EyeProfile.TYPICAL, depth = 0.05f, attempts = 4)
        val t = run.session.result().profile.thresholds
        assertFalse(t.fitted)
        assertEquals(BlinkDetector.DEFAULT_START, t.start, 0f)
    }
}

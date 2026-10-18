package com.eyeblink.core.calibration

import com.eyeblink.core.engine.Thermal
import com.eyeblink.core.model.AppCategory
import com.eyeblink.core.model.CameraSettings
import com.eyeblink.core.model.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun blink(start: Long, instructed: Boolean = false, confidence: Float = 0.9f) =
    BlinkObservation(start, 120, true, confidence, instructed)

private fun baseline(ibi: Long, n: Int = 30) = BlinkBaseline(medianIbiMs = ibi, blinkDurationMs = 130, completeBlinkRatio = 0.9f, sampleCount = n)

class ContextProfileTest {

    @Test
    fun `dim light wins over the app category`() {
        assertEquals(ContextProfile.LOW_LIGHT, ContextClassifier.classify(AppCategory.VIDEO, true, lux = 3f))
    }

    @Test
    fun `categories map to their profiles in normal light`() {
        assertEquals(ContextProfile.READING, ContextClassifier.classify(AppCategory.READING, false, 200f))
        assertEquals(ContextProfile.SOCIAL, ContextClassifier.classify(AppCategory.SOCIAL, true, 200f))
        assertEquals(ContextProfile.VIDEO, ContextClassifier.classify(AppCategory.VIDEO, true, null))
    }

    @Test
    fun `an uncategorised app with playing media counts as video`() {
        assertEquals(ContextProfile.VIDEO, ContextClassifier.classify(AppCategory.OTHER, true, null))
        assertEquals(ContextProfile.SOCIAL, ContextClassifier.classify(AppCategory.OTHER, false, null))
    }

    @Test
    fun `an unobserved context is estimated from the general baseline and labelled so`() {
        val p = PersonalProfile(general = baseline(8_000))
        val reading = p.baselineFor(ContextProfile.READING)
        assertEquals(Provenance.ESTIMATED, reading.provenance)
        assertEquals(12_000L, reading.baseline.medianIbiMs)
    }

    @Test
    fun `an observed context is reported as observed and not rescaled`() {
        val p = PersonalProfile(general = baseline(8_000)).withContext(ContextProfile.VIDEO, baseline(10_000))
        val video = p.baselineFor(ContextProfile.VIDEO)
        assertEquals(Provenance.OBSERVED, video.provenance)
        assertEquals(10_000L, video.baseline.medianIbiMs)
    }

    @Test
    fun `no data at all is reported as none`() {
        val r = PersonalProfile().baselineFor(ContextProfile.VIDEO)
        assertEquals(Provenance.NONE, r.provenance)
        assertFalse(r.baseline.hasSignal)
    }

    @Test
    fun `a context with too few samples is still only an estimate`() {
        val p = PersonalProfile(general = baseline(8_000)).withContext(ContextProfile.VIDEO, baseline(10_000, n = 2))
        assertEquals(Provenance.ESTIMATED, p.baselineFor(ContextProfile.VIDEO).provenance)
    }
}

class BlinkThresholdsTest {

    @Test
    fun `defaults are used when the deliberate blinks barely closed the eyes`() {
        val t = BlinkThresholds.fromCalibration(openLevel = 0.5f, fullDepth = 0.10f)
        assertFalse(t.fitted)
        assertEquals(BlinkDetector.DEFAULT_START, t.start, 0f)
        assertEquals("the open level is still worth keeping", 0.5f, t.openLevel, 0f)
    }

    @Test
    fun `defaults are used when nothing was measured`() {
        assertFalse(BlinkThresholds.fromCalibration(null, null).fitted)
        assertEquals(0f, BlinkThresholds.fromCalibration(null, null).openLevel, 0f)
    }

    @Test
    fun `a clear closure fits the thresholds in a sane order`() {
        val t = BlinkThresholds.fromCalibration(openLevel = 0.7f, fullDepth = 0.75f)
        assertTrue(t.fitted)
        assertTrue("end < start", t.end < t.start)
        assertTrue("start < full", t.start < t.full)
        assertTrue(t.full <= BlinkThresholds.MAX_FULL)
        assertEquals(0.7f, t.openLevel, 0f)
    }

    @Test
    fun `eyes that close less get a lower start than eyes that close completely`() {
        val narrow = BlinkThresholds.fromCalibration(openLevel = 0.4f, fullDepth = 0.50f)
        val wide = BlinkThresholds.fromCalibration(openLevel = 0.8f, fullDepth = 0.80f)
        assertTrue("${narrow.start} vs ${wide.start}", narrow.start < wide.start)
        assertTrue(narrow.full < wide.full)
    }

    @Test
    fun `eyes that close completely get the default start, not a higher one`() {
        // A start above the default would ask a typical blink to be deeper than a typical blink is: 40 % blinks would go unseen.
        for (depth in listOf(0.72f, 0.85f, 0.95f, 1f)) {
            assertEquals("depth $depth", BlinkDetector.DEFAULT_START, BlinkThresholds.fromCalibration(0.6f, depth).start, 1e-4f)
        }
    }

    @Test
    fun `the start stays out of the tracker's noise, unless the blinks themselves are that shallow`() {
        assertEquals(0.25f, BlinkThresholds.fromCalibration(0.6f, 0.50f).start, 1e-4f)
        assertEquals(0.24f, BlinkThresholds.fromCalibration(0.6f, 0.40f).start, 1e-4f)
        assertEquals("shallower blinks than that cannot be seen from higher up", 0.21f, BlinkThresholds.fromCalibration(0.6f, 0.35f).start, 1e-4f)
        assertEquals(0.18f, BlinkThresholds.fromCalibration(0.6f, 0.30f).start, 1e-4f)
        assertEquals(0.18f, BlinkThresholds.fromCalibration(0.6f, BlinkThresholds.MIN_USABLE_DEPTH).start, 1e-4f)
    }

    @Test
    fun `whatever the depth, a fit is a set of thresholds the detector accepts as they are`() {
        var depth = BlinkThresholds.MIN_USABLE_DEPTH
        while (depth <= 1f) {
            val t = BlinkThresholds.fromCalibration(0.5f, depth)
            assertEquals("depth $depth", t, t.sanitised())
            assertTrue("depth $depth: end < start < full", t.end < t.start && t.start < t.full)
            depth += 0.01f
        }
    }

    @Test
    fun `nan input is not trusted`() {
        assertFalse(BlinkThresholds.fromCalibration(0.5f, Float.NaN).fitted)
        assertEquals(0f, BlinkThresholds.fromCalibration(Float.NaN, 0.6f).openLevel, 0f)
    }

    @Test
    fun `nonsense thresholds fall back to the defaults`() {
        assertEquals(BlinkThresholds(), BlinkThresholds(start = 0.9f, end = 0.1f, full = 0.5f, fitted = true).sanitised())
        assertEquals(BlinkThresholds(), BlinkThresholds(start = 0.3f, end = 0.5f, full = 0.8f, fitted = true).sanitised())
        val fine = BlinkThresholds(0.3f, 0.12f, 0.55f, true, 0.6f)
        assertEquals(fine, fine.sanitised())
    }

    @Test
    fun `a file written by an older build decodes to the defaults, not to numbers that now mean something else`() {
        val old = """{"close":0.30,"reopen":0.55,"complete":0.12,"personalised":true}"""
        val decoded = com.eyeblink.core.model.CoreJson.instance.decodeFromString<BlinkThresholds>(old)
        assertEquals(BlinkThresholds(), decoded)
        assertFalse(decoded.fitted)
    }

    @Test
    fun `thresholds survive a round trip`() {
        val t = BlinkThresholds(0.27f, 0.12f, 0.50f, true, 0.41f)
        val json = com.eyeblink.core.model.CoreJson.instance.encodeToString(BlinkThresholds.serializer(), t)
        assertEquals(t, com.eyeblink.core.model.CoreJson.instance.decodeFromString<BlinkThresholds>(json))
    }

    @Test
    fun `a fitted detector actually uses its thresholds`() {
        // A person whose eyes close by only a quarter of their open level: the defaults would never see this.
        val t = BlinkThresholds(start = 0.15f, end = 0.07f, full = 0.25f, fitted = true, openLevel = 0.6f)
        val d = t.newDetector()
        fun s(ts: Long, o: Float) = EyeOpennessSample(ts, o, o, 0.9f, 200f)
        d.onSample(s(0, 0.6f))
        d.onSample(s(100, 0.44f))
        d.onSample(s(200, 0.44f))
        val b = d.onSample(s(300, 0.6f))
        assertTrue(b != null && b.complete)
        assertNull(BlinkThresholds().newDetector().also { it.seed(0.6f) }.let { dd ->
            dd.onSample(s(0, 0.6f)); dd.onSample(s(100, 0.44f)); dd.onSample(s(200, 0.44f)); dd.onSample(s(300, 0.6f))
        })
    }

    @Test
    fun `sensitivity makes the same thresholds quicker or slower to see a blink`() {
        val base = BlinkThresholds().settings()
        assertTrue(BlinkThresholds().settings(0.7f).start < base.start)
        assertTrue(BlinkThresholds().settings(1.35f).start > base.start)
    }
}

class TrackingQualityTest {

    @Test
    fun `a good frontal well lit frame is fully usable`() {
        val r = TrackingQuality.score(true, 0.5f, 0f, 0f, 120f)
        assertEquals(1f, r.score, 1e-4f)
        assertTrue(r.usable)
        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun `no face scores zero`() {
        val r = TrackingQuality.score(false, 0f, 0f, 0f, 120f)
        assertEquals(0f, r.score, 0f)
        assertEquals(setOf(QualityIssue.NO_FACE), r.issues)
    }

    @Test
    fun `darkness is reported and drags the score down`() {
        val r = TrackingQuality.score(true, 0.5f, 0f, 0f, 15f)
        assertFalse(r.usable)
        assertTrue(QualityIssue.TOO_DARK in r.issues)
    }

    @Test
    fun `a tiny face is not usable`() {
        val r = TrackingQuality.score(true, 0.1f, 0f, 0f, 120f)
        assertFalse(r.usable)
        assertTrue(QualityIssue.FACE_TOO_SMALL in r.issues)
    }

    @Test
    fun `a face pressed against the lens is flagged`() {
        val r = TrackingQuality.score(true, 0.99f, 0f, 0f, 120f)
        assertFalse(r.usable)
        assertTrue(QualityIssue.FACE_TOO_CLOSE in r.issues)
    }

    @Test
    fun `a head turned away is flagged in either direction`() {
        for (yaw in listOf(-0.6f, 0.6f)) {
            val r = TrackingQuality.score(true, 0.5f, yaw, 0f, 120f)
            assertFalse(r.usable)
            assertTrue(QualityIssue.TURNED_AWAY in r.issues)
        }
    }

    @Test
    fun `the weakest factor sets the score`() {
        val r = TrackingQuality.score(true, 0.5f, 0.38f, 0f, 200f)
        assertTrue(r.score in 0.3f..0.7f)
    }

    @Test
    fun `a frontal head has zero yaw and unequal distances shift it`() {
        assertEquals(0f, HeadPose.yaw(100f, 100f), 0f)
        assertTrue(HeadPose.yaw(150f, 50f) > 0f)
        assertTrue(HeadPose.yaw(50f, 150f) < 0f)
        assertEquals(1f, HeadPose.yaw(0f, 0f), 0f)
    }

    @Test
    fun `pitch is measured against the frontal nose ratio`() {
        assertEquals(0f, HeadPose.pitch(45f, 100f), 1e-5f)
        assertTrue(HeadPose.pitch(70f, 100f) > 0f)
        assertTrue(HeadPose.pitch(20f, 100f) < 0f)
    }
}

class BlinkRateTrackerTest {

    private fun feed(t: BlinkRateTracker, fromMs: Long, toMs: Long, stepMs: Long = 66, conf: Float = 0.9f) {
        var ts = fromMs
        while (ts <= toMs) {
            t.onTrackedFrame(ts, conf)
            ts += stepMs
        }
    }

    @Test
    fun `rate is blinks over tracked time`() {
        val t = BlinkRateTracker()
        feed(t, 0, 30_000)
        for (i in 0 until 5) t.onBlink(blink(i * 5_000L))
        val r = t.observed(30_000)
        assertEquals(10f, r.blinksPerMinute, 0.6f)
        assertEquals(5, r.blinks)
        assertTrue(r.reliable)
    }

    @Test
    fun `instructed blinks never count toward the observed rate`() {
        val t = BlinkRateTracker()
        feed(t, 0, 30_000)
        repeat(10) { t.onBlink(blink(it * 1_000L, instructed = true)) }
        assertEquals(0, t.observed(30_000).blinks)
    }

    @Test
    fun `gaps in tracking are not credited as observed time`() {
        val t = BlinkRateTracker()
        feed(t, 0, 5_000)
        t.onTrackingLost()
        feed(t, 60_000, 65_000)
        val r = t.observed(65_000)
        assertTrue("tracked=${r.trackedMs}", r.trackedMs in 9_000L..10_500L)
    }

    @Test
    fun `too little tracked time is not reliable`() {
        val t = BlinkRateTracker()
        feed(t, 0, 4_000)
        t.onBlink(blink(1_000))
        assertFalse(t.observed(4_000).reliable)
    }

    @Test
    fun `low confidence is not reliable even with plenty of time`() {
        val t = BlinkRateTracker()
        feed(t, 0, 40_000, conf = 0.3f)
        assertFalse(t.observed(40_000).reliable)
    }

    @Test
    fun `tracked time since the last blink resets on a blink and ignores lost tracking`() {
        val t = BlinkRateTracker()
        feed(t, 0, 10_000)
        t.onBlink(blink(10_000))
        feed(t, 10_066, 15_000)
        val r = t.observed(15_000)
        assertTrue("since=${r.trackedSinceBlinkMs}", r.trackedSinceBlinkMs in 4_500L..5_100L)
    }

    @Test
    fun `old observations fall out of the window`() {
        val t = BlinkRateTracker(windowMs = 30_000)
        feed(t, 0, 20_000)
        t.onBlink(blink(1_000))
        feed(t, 20_066, 80_000)
        assertEquals(0, t.observed(80_000).blinks)
    }

    @Test
    fun `reset clears everything`() {
        val t = BlinkRateTracker()
        feed(t, 0, 20_000)
        t.onBlink(blink(100))
        t.reset()
        assertEquals(0L, t.observed(20_000).trackedMs)
    }
}

class AdaptiveLearnerTest {

    private fun window(
        blinks: Int = 8,
        tracked: Long = 40_000,
        conf: Float = 0.85f,
        luma: Float = 120f,
        gap: Long = 5_000,
        ctx: ContextProfile = ContextProfile.VIDEO,
        instructed: Boolean = false,
    ) = LearningWindow(ctx, (0 until blinks).map { blink(it * gap, instructed) }, tracked, conf, luma)

    @Test
    fun `a good window is accepted and moves the context baseline`() {
        val p = PersonalProfile(general = baseline(8_000, 40)).withContext(ContextProfile.VIDEO, baseline(8_000, 40))
        val out = AdaptiveLearner.learn(p, window(gap = 6_000))
        assertEquals(LearnVerdict.ACCEPTED, out.verdict)
        val ibi = out.profile.byContext.getValue(ContextProfile.VIDEO).medianIbiMs
        assertTrue("moved toward fresh but not all the way: $ibi", ibi < 8_000 && ibi > 6_000)
    }

    @Test
    fun `short windows are refused`() {
        assertEquals(LearnVerdict.TOO_SHORT, AdaptiveLearner.learn(PersonalProfile(), window(tracked = 5_000)).verdict)
    }

    @Test
    fun `low confidence is refused`() {
        assertEquals(LearnVerdict.LOW_CONFIDENCE, AdaptiveLearner.learn(PersonalProfile(), window(conf = 0.4f)).verdict)
    }

    @Test
    fun `darkness is refused`() {
        assertEquals(LearnVerdict.TOO_DARK, AdaptiveLearner.learn(PersonalProfile(), window(luma = 5f)).verdict)
    }

    @Test
    fun `too few blinks are refused`() {
        assertEquals(LearnVerdict.TOO_FEW_BLINKS, AdaptiveLearner.learn(PersonalProfile(), window(blinks = 2)).verdict)
    }

    @Test
    fun `instructed calibration blinks are never learned from`() {
        val out = AdaptiveLearner.learn(PersonalProfile(), window(instructed = true))
        assertEquals(LearnVerdict.NO_NATURAL_BLINKS, out.verdict)
        assertEquals(PersonalProfile(), out.profile)
    }

    @Test
    fun `a refused window leaves the profile untouched`() {
        val p = PersonalProfile(general = baseline(8_000))
        assertEquals(p, AdaptiveLearner.learn(p, window(tracked = 1_000)).profile)
    }

    @Test
    fun `one update never shifts a baseline by more than the step limit`() {
        val p = PersonalProfile(general = baseline(8_000, 6)).withContext(ContextProfile.VIDEO, baseline(8_000, 6))
        // wildly different fresh data
        val out = AdaptiveLearner.learn(p, window(gap = 40_000))
        val ibi = out.profile.byContext.getValue(ContextProfile.VIDEO).medianIbiMs
        assertTrue("ibi=$ibi", ibi <= (8_000 * (1 + AdaptiveLearner.MAX_STEP)).toLong())
    }

    @Test
    fun `the first accepted window seeds an empty context`() {
        val out = AdaptiveLearner.learn(PersonalProfile(), window(ctx = ContextProfile.READING, gap = 7_000))
        assertEquals(LearnVerdict.ACCEPTED, out.verdict)
        assertEquals(Provenance.OBSERVED, out.profile.baselineFor(ContextProfile.READING).provenance)
        assertTrue(out.profile.general.hasSignal)
    }

    @Test
    fun `weight shrinks as the model gets more data`() {
        assertTrue(AdaptiveLearner.weightFor(5, 8) > AdaptiveLearner.weightFor(500, 8))
        assertTrue(AdaptiveLearner.weightFor(0, 1000) <= AdaptiveLearner.MAX_WEIGHT)
    }
}

class DutyCycleTest {

    private val ok = TrackingConstraints(
        eligible = true, permissionGranted = true, batterySaver = false,
        thermal = Thermal.NONE, tooDark = false, cameraBusyElsewhere = false,
    )

    private fun cycle(mode: TrackingMode = TrackingMode.BURST, burst: Int = 8, period: Int = 45, fps: Int = 15) =
        DutyCycle { CameraSettings(mode, burst, period, fps) }

    @Test
    fun `off means no camera`() {
        val p = cycle(TrackingMode.OFF).plan(0, ok)
        assertFalse(p.runCamera)
        assertEquals(CameraOffReason.USER_OFF, p.offReason)
    }

    @Test
    fun `burst mode runs for the burst then idles then runs again`() {
        val c = cycle()
        assertTrue(c.plan(0, ok).runCamera)
        assertTrue(c.plan(7_000, ok).runCamera)
        val idle = c.plan(8_000, ok)
        assertFalse(idle.runCamera)
        assertEquals(CameraOffReason.BETWEEN_BURSTS, idle.offReason)
        assertFalse(c.plan(30_000, ok).runCamera)
        assertTrue(c.plan(8_000 + 37_000, ok).runCamera)
    }

    @Test
    fun `burst duty is a small fraction of the time`() {
        val c = cycle()
        var on = 0
        var t = 0L
        while (t < 600_000) {
            if (c.plan(t, ok).runCamera) on++
            t += 1_000
        }
        val duty = on / 600f
        assertTrue("duty=$duty", duty in 0.12f..0.25f)
    }

    @Test
    fun `continuous mode keeps the camera on`() {
        val c = cycle(TrackingMode.CONTINUOUS)
        for (t in 0L..120_000L step 10_000L) assertTrue(c.plan(t, ok).runCamera)
    }

    @Test
    fun `constraints always win over the user mode`() {
        val c = cycle(TrackingMode.CONTINUOUS)
        assertEquals(CameraOffReason.NOT_ELIGIBLE, c.plan(0, ok.copy(eligible = false)).offReason)
        assertEquals(CameraOffReason.NO_PERMISSION, c.plan(0, ok.copy(permissionGranted = false)).offReason)
        assertEquals(CameraOffReason.BATTERY, c.plan(0, ok.copy(batterySaver = true)).offReason)
        assertEquals(CameraOffReason.THERMAL, c.plan(0, ok.copy(thermal = Thermal.SEVERE)).offReason)
        assertEquals(CameraOffReason.CAMERA_BUSY, c.plan(0, ok.copy(cameraBusyElsewhere = true)).offReason)
        assertEquals(CameraOffReason.TOO_DARK, c.plan(0, ok.copy(tooDark = true)).offReason)
    }

    @Test
    fun `moderate heat lowers the frame rate before it takes the camera`() {
        val p = cycle(fps = 20).plan(0, ok.copy(thermal = Thermal.MODERATE))
        assertTrue(p.runCamera)
        assertEquals(10, p.fps)
    }

    @Test
    fun `a veto restarts the cycle with a fresh burst`() {
        val c = cycle()
        c.plan(0, ok)
        c.plan(9_000, ok)
        c.plan(10_000, ok.copy(eligible = false))
        assertTrue(c.plan(11_000, ok).runCamera)
    }
}

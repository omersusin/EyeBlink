package com.eyeblink.core.engine

import com.eyeblink.core.calibration.BlinkBaseline
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.ObservedRate
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.model.RhythmSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalPlannerTest {

    private val rhythm = RhythmSettings(microIntervalSec = 60, jitterPercent = 10)

    private fun plan(
        context: ContextProfile = ContextProfile.VIDEO,
        personal: PersonalProfile = PersonalProfile(),
        mode: TriggerMode = TriggerMode.CAMERA_FREE,
        sessionMs: Long = 0,
        observed: ObservedRate? = null,
        scale: Int = 100,
        jitter: Float = 0f,
        r: RhythmSettings = rhythm,
    ) = IntervalPlanner.micro(IntervalInputs(r, scale, context, personal, mode, sessionMs, observed, jitter))

    private fun codes(p: IntervalPlan) = p.factors.map { it.code }

    private fun baseline(ibi: Long) = BlinkBaseline(ibi, 130, 0.9f, 40)
    private fun observed(bpm: Float, trackedSince: Long = 3_000) =
        ObservedRate(bpm, 60_000, (bpm).toInt(), trackedSince, 0.9f)

    @Test
    fun `an uncalibrated fresh user gets exactly the chosen interval`() {
        val p = plan()
        assertEquals(60_000L, p.intervalMs)
        assertEquals(listOf(FactorCode.BASE), codes(p))
    }

    @Test
    fun `the per app scale stretches or shrinks the interval`() {
        assertEquals(30_000L, plan(scale = 50).intervalMs)
        assertEquals(120_000L, plan(scale = 200).intervalMs)
        assertTrue(FactorCode.APP_SCALE in codes(plan(scale = 50)))
    }

    @Test
    fun `a measured rare blinker is scheduled sooner than the unscaled interval`() {
        val personal = PersonalProfile(general = baseline(16_000), byContext = mapOf(ContextProfile.VIDEO to baseline(16_000)))
        val p = plan(personal = personal)
        assertTrue("interval=${p.intervalMs}", p.intervalMs < 60_000)
        val f = p.factors.single { it.code == FactorCode.PERSONAL_BASELINE }
        assertTrue("measured baseline is flagged observed", f.observed)
    }

    @Test
    fun `an estimated baseline is trusted half as much as a measured one`() {
        val general = baseline(8_000)
        val estimatedProfile = PersonalProfile(general = general) // context unobserved -> estimate
        val measuredProfile = PersonalProfile(general = general, byContext = mapOf(ContextProfile.READING to baseline(12_000)))
        // Same effective IBI (8s * 1.5 prior == 12s) but one is estimated and one measured.
        val est = plan(context = ContextProfile.READING, personal = estimatedProfile)
        val meas = plan(context = ContextProfile.READING, personal = measuredProfile)
        assertTrue(est.intervalMs > meas.intervalMs)
        assertFalse(est.factors.first { it.code == FactorCode.PERSONAL_BASELINE }.observed)
    }

    @Test
    fun `without any personal data coarse context priors apply`() {
        assertEquals(51_000L, plan(context = ContextProfile.READING).intervalMs)
        assertEquals(54_000L, plan(context = ContextProfile.LOW_LIGHT).intervalMs)
        assertEquals(60_000L, plan(context = ContextProfile.VIDEO).intervalMs)
    }

    @Test
    fun `long sessions shorten the interval gradually`() {
        val short = plan(sessionMs = 5 * 60_000L).intervalMs
        val mid = plan(sessionMs = 25 * 60_000L).intervalMs
        val long = plan(sessionMs = 60 * 60_000L).intervalMs
        assertEquals(60_000L, short)
        assertTrue(mid < short && long < mid)
        assertEquals((60_000 * IntervalPlanner.FATIGUE_MIN_FACTOR).toLong(), long)
    }

    @Test
    fun `non adaptive scheduling ignores everything except app scale and jitter`() {
        val personal = PersonalProfile(general = baseline(16_000))
        val p = plan(personal = personal, sessionMs = 60 * 60_000L, context = ContextProfile.READING, r = rhythm.copy(adaptive = false))
        assertEquals(60_000L, p.intervalMs)
    }

    @Test
    fun `observed staring pulls the break earlier`() {
        val personal = PersonalProfile(general = baseline(8_000), byContext = mapOf(ContextProfile.VIDEO to baseline(8_000)))
        val p = plan(personal = personal, mode = TriggerMode.CAMERA_ASSISTED, observed = observed(bpm = 2f))
        val f = p.factors.single { it.code == FactorCode.OBSERVED_LOW_RATE }
        assertTrue(f.observed)
        assertTrue(f.multiplier < 0.7f)
        assertTrue(p.intervalMs < 60_000)
    }

    @Test
    fun `observed healthy blinking relaxes the interval but only so far`() {
        val personal = PersonalProfile(general = baseline(8_000), byContext = mapOf(ContextProfile.VIDEO to baseline(8_000)))
        val p = plan(personal = personal, mode = TriggerMode.CAMERA_ASSISTED, observed = observed(bpm = 30f))
        val f = p.factors.single { it.code == FactorCode.OBSERVED_HEALTHY }
        assertEquals(1.25f, f.multiplier, 1e-4f)
    }

    @Test
    fun `the camera claims nothing in camera free mode`() {
        val personal = PersonalProfile(general = baseline(8_000), byContext = mapOf(ContextProfile.VIDEO to baseline(8_000)))
        val p = plan(personal = personal, mode = TriggerMode.CAMERA_FREE, observed = observed(bpm = 1f))
        assertFalse(p.factors.any { it.observed && (it.code == FactorCode.OBSERVED_LOW_RATE || it.code == FactorCode.OBSERVED_HEALTHY) })
    }

    @Test
    fun `an unreliable observation is ignored`() {
        val personal = PersonalProfile(general = baseline(8_000), byContext = mapOf(ContextProfile.VIDEO to baseline(8_000)))
        val shaky = ObservedRate(1f, trackedMs = 2_000, blinks = 0, trackedSinceBlinkMs = 2_000, meanConfidence = 0.9f)
        val p = plan(personal = personal, mode = TriggerMode.CAMERA_ASSISTED, observed = shaky)
        assertFalse(p.factors.any { it.code == FactorCode.OBSERVED_LOW_RATE })
    }

    @Test
    fun `observation without a measured baseline cannot move the schedule`() {
        val p = plan(mode = TriggerMode.CAMERA_ASSISTED, observed = observed(bpm = 1f))
        assertFalse(p.factors.any { it.code == FactorCode.OBSERVED_LOW_RATE })
    }

    @Test
    fun `jitter spreads the interval symmetrically within the configured percent`() {
        assertEquals(66_000L, plan(jitter = 1f).intervalMs)
        assertEquals(54_000L, plan(jitter = -1f).intervalMs)
        assertEquals(66_000L, plan(jitter = 9f).intervalMs)
    }

    @Test
    fun `the result is always inside the allowed range and never under the cooldown`() {
        assertEquals(5_000L, plan(r = rhythm.copy(microIntervalSec = 5, cooldownSec = 3), scale = 50).intervalMs.coerceAtLeast(5_000))
        val p = plan(r = RhythmSettings(microIntervalSec = 5, cooldownSec = 20, jitterPercent = 0))
        assertEquals(20_000L, p.intervalMs)
        assertEquals(600_000L, plan(r = rhythm.copy(microIntervalSec = 600), scale = 200).intervalMs)
    }

    @Test
    fun `skip backoff grows then saturates at double`() {
        assertEquals(1f, IntervalPlanner.skipBackoff(0), 0f)
        assertEquals(1.5f, IntervalPlanner.skipBackoff(2), 0f)
        assertEquals(2f, IntervalPlanner.skipBackoff(4), 0f)
        assertEquals(2f, IntervalPlanner.skipBackoff(40), 0f)
        assertEquals(1f, IntervalPlanner.skipBackoff(-3), 0f)
    }

    @Test
    fun `stare limit is the larger of the floor and a multiple of the personal gap`() {
        assertEquals(IntervalPlanner.STARE_FLOOR_MS, IntervalPlanner.stareLimitMs(2_000))
        assertEquals(30_000L, IntervalPlanner.stareLimitMs(10_000))
    }
}

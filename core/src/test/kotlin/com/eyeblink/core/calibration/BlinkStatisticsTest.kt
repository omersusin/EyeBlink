package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkStatisticsTest {

    private fun blink(
        start: Long,
        duration: Long = 120,
        complete: Boolean = true,
        confidence: Float = 0.9f,
        instructed: Boolean = false,
    ) = BlinkObservation(start, duration, complete, confidence, instructed)

    // --- summary ----------------------------------------------------------

    @Test
    fun `empty input yields an empty baseline`() {
        val b = BlinkStatistics.summarise(emptyList())
        assertEquals(0, b.sampleCount)
        assertFalse(b.hasSignal)
        assertEquals(0L, b.medianIbiMs)
    }

    @Test
    fun `instructed blinks are excluded from learning`() {
        val b = BlinkStatistics.summarise(
            listOf(blink(0), blink(3_000, instructed = true), blink(6_000))
        )
        assertEquals("only the two spontaneous blinks count", 2, b.sampleCount)
    }

    @Test
    fun `low-confidence blinks are excluded`() {
        val b = BlinkStatistics.summarise(listOf(blink(0, confidence = 0.2f), blink(3_000)))
        assertEquals(1, b.sampleCount)
    }

    @Test
    fun `zero-duration blinks are excluded`() {
        val b = BlinkStatistics.summarise(listOf(blink(0, duration = 0), blink(3_000)))
        assertEquals(1, b.sampleCount)
    }

    @Test
    fun `median ibi is the median gap, not the mean`() {
        // gaps: 1000, 2000, 5000  -> median 2000
        val b = BlinkStatistics.summarise(listOf(blink(0), blink(1_000), blink(3_000), blink(8_000)))
        assertEquals(2_000L, b.medianIbiMs)
    }

    @Test
    fun `median of an even number of gaps averages the two middle ones`() {
        val b = BlinkStatistics.summarise(listOf(blink(0), blink(1_000), blink(3_000), blink(6_000)))
        // gaps 1000, 2000, 3000 -> median 2000
        assertEquals(2_000L, b.medianIbiMs)
    }

    @Test
    fun `unsorted input still produces the same median`() {
        val ordered = listOf(blink(0), blink(1_000), blink(3_000), blink(8_000))
        val shuffled = listOf(blink(8_000), blink(1_000), blink(0), blink(3_000))
        assertEquals(
            BlinkStatistics.summarise(ordered).medianIbiMs,
            BlinkStatistics.summarise(shuffled).medianIbiMs,
        )
    }

    @Test
    fun `tracking-loss gaps are discarded`() {
        // 10 minutes between blinks is lost tracking, not a real interval.
        val b = BlinkStatistics.summarise(listOf(blink(0), blink(600_000), blink(603_000)))
        // only the 3000ms gap survives
        assertEquals(3_000L, b.medianIbiMs)
    }

    @Test
    fun `too-fast gaps are discarded as artefacts`() {
        val b = BlinkStatistics.summarise(listOf(blink(0), blink(50), blink(5_000)))
        assertEquals(4_950L, b.medianIbiMs)
    }

    @Test
    fun `single blink yields no interval but still counts`() {
        val b = BlinkStatistics.summarise(listOf(blink(0)))
        assertEquals(1, b.sampleCount)
        assertEquals(0L, b.medianIbiMs)
    }

    @Test
    fun `complete blink ratio is computed`() {
        val b = BlinkStatistics.summarise(
            listOf(blink(0, complete = true), blink(3_000), blink(6_000), blink(9_000, complete = false))
        )
        assertEquals(0.75f, b.completeBlinkRatio, 0.001f)
    }

    @Test
    fun `median blink duration is reported`() {
        val b = BlinkStatistics.summarise(
            listOf(blink(0, duration = 100), blink(3_000, duration = 120), blink(6_000, duration = 140))
        )
        assertEquals(120L, b.blinkDurationMs)
    }

    // --- personal interval scale ------------------------------------------

    private fun baselineWithIbi(gap: Long) =
        BlinkStatistics.summarise((0 until 6).map { blink(it * gap) })

    @Test
    fun `a typical on-screen blinker gets the unscaled interval`() {
        val b = baselineWithIbi(BlinkBaseline.REFERENCE_IBI_MS)
        assertEquals(1f, b.personalIntervalScale, 1e-4f)
    }

    @Test
    fun `someone who blinks rarely gets breaks sooner than someone who blinks often`() {
        // The app exists to counteract reduced blinking, so a long gap between blinks must
        // shorten the interval. The reverse direction would reward the very behaviour the
        // app is meant to interrupt.
        val rare = baselineWithIbi(16_000).personalIntervalScale
        val frequent = baselineWithIbi(4_000).personalIntervalScale
        assertTrue("rare=$rare frequent=$frequent", rare < 1f)
        assertTrue("rare=$rare frequent=$frequent", frequent > 1f)
        assertTrue(rare < frequent)
    }

    @Test
    fun `the scale is bounded so a noisy baseline cannot swing the cadence`() {
        assertEquals(BlinkBaseline.MIN_SCALE, baselineWithIbi(120_000).personalIntervalScale, 0f)
        assertEquals(BlinkBaseline.MAX_SCALE, baselineWithIbi(500).personalIntervalScale, 0f)
    }

    @Test
    fun `different users really do get different scales`() {
        val a = baselineWithIbi(5_000).personalIntervalScale
        val b = baselineWithIbi(12_000).personalIntervalScale
        assertTrue("baseline must still influence the interval", a != b)
    }

    @Test
    fun `without signal the scale is neutral`() {
        assertEquals(1f, BlinkBaseline().personalIntervalScale, 0f)
        assertEquals(null, BlinkBaseline().blinksPerMinute)
    }

    @Test
    fun `blinks per minute follows the median interval`() {
        assertEquals(15f, baselineWithIbi(4_000).blinksPerMinute!!, 1e-3f)
    }

    // --- hasSignal --------------------------------------------------------

    @Test
    fun `hasSignal needs enough samples`() {
        val few = listOf(blink(0), blink(3_000))
        val enough = (0 until BlinkBaseline.MIN_SAMPLES).map { blink(it * 3_000L) }
        assertFalse(BlinkStatistics.summarise(few).hasSignal)
        assertTrue(BlinkStatistics.summarise(enough).hasSignal)
    }

    // --- variation --------------------------------------------------------

    @Test
    fun `variation is zero for perfectly regular blinking`() {
        val regular = (0..5).map { blink(it * 3_000L) }
        assertEquals(0.0, BlinkStatistics.ibiVariation(regular), 1e-6)
        assertEquals(0.0, BlinkStatistics.meanAbsoluteDeviation(regular), 1e-6)
    }

    @Test
    fun `variation is positive for irregular blinking`() {
        val irregular = listOf(blink(0), blink(1_000), blink(4_000), blink(20_000))
        assertTrue(BlinkStatistics.ibiVariation(irregular) > 0.0)
    }

    @Test
    fun `variation needs at least two intervals`() {
        assertEquals(0.0, BlinkStatistics.ibiVariation(listOf(blink(0))), 1e-6)
    }

    // --- gradual merge ----------------------------------------------------

    @Test
    fun `merge keeps the existing baseline when the fresh one has no signal`() {
        val current = BlinkBaseline(4_000, 120, 0.8f, 20)
        val fresh = BlinkStatistics.summarise(listOf(blink(0, instructed = true)))
        assertEquals(current, BlinkStatistics.merge(current, fresh))
    }

    @Test
    fun `merge adopts the fresh baseline when the current one is empty`() {
        val fresh = BlinkStatistics.summarise((0 until 6).map { blink(it * 3_000L) })
        assertEquals(fresh, BlinkStatistics.merge(BlinkBaseline(), fresh))
    }

    @Test
    fun `merge moves the baseline only partway, not all the way`() {
        val current = BlinkBaseline(4_000, 100, 1.0f, 100)
        val fresh = BlinkBaseline(8_000, 200, 0.0f, 100)
        val merged = BlinkStatistics.merge(current, fresh, weight = 0.25f)
        // halfway+ a quarter of the way: 4000 -> 5000
        assertEquals(5_000L, merged.medianIbiMs)
        assertEquals(125L, merged.blinkDurationMs)
        assertEquals(200, merged.sampleCount)
    }

    @Test
    fun `merge weight is clamped to the 0 to 1 range`() {
        val current = BlinkBaseline(4_000, 100, 1.0f, 50)
        val fresh = BlinkBaseline(8_000, 200, 0.0f, 50)
        assertEquals(4_000L, BlinkStatistics.merge(current, fresh, weight = -1f).medianIbiMs)
        assertEquals(8_000L, BlinkStatistics.merge(current, fresh, weight = 5f).medianIbiMs)
    }

    // --- sample trust -----------------------------------------------------

    @Test
    fun `poor confidence or low light makes a sample untrackable`() {
        val good = EyeOpennessSample(0, 0.9f, 0.8f, confidence = 0.9f, illuminance = 200f)
        val dark = good.copy(illuminance = 5f)
        val unsure = good.copy(confidence = 0.2f)
        val bad = good.copy(leftEyeOpenness = 1.4f)
        assertTrue(good.isTrackable)
        assertFalse(dark.isTrackable)
        assertFalse(unsure.isTrackable)
        assertFalse(bad.isTrackable)
    }

    @Test
    fun `openness uses the less open eye`() {
        val s = EyeOpennessSample(0, leftEyeOpenness = 0.9f, rightEyeOpenness = 0.2f, 0.9f, 200f)
        assertEquals(0.2f, s.openness, 0.001f)
    }
}
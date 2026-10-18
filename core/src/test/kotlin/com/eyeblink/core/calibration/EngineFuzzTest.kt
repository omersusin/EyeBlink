package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Whatever a camera, a face tracker or a person does, the engine must stay inside its rules and find its way back:
 * no exceptions, no number that is not a number, no blink longer or shorter than the settings allow, and no state
 * that never ends. Random streams (steady eyes, shut eyes, squints, glitch frames, gaps, dark frames, scores that
 * make no sense) from fixed seeds, so a failure can be replayed.
 */
class EngineFuzzTest {

    private val good = QualityReport(0.9f, emptySet())

    private fun randomSettings(random: Random): DetectorSettings {
        val start = 0.15f + random.nextFloat() * 0.30f
        return DetectorSettings(
            start = start,
            end = start * (0.3f + random.nextFloat() * 0.5f),
            full = start + 0.1f + random.nextFloat() * 0.4f,
            minDurationMs = 20L + random.nextInt(60),
            maxDurationMs = 500L + random.nextInt(3_000),
            singleFrameDepth = start + random.nextFloat() * 0.4f,
            warmupMs = random.nextInt(1_500).toLong(),
            useBlinkScore = random.nextBoolean(),
        ).scaled(0.6f + random.nextFloat() * 1.2f)
    }

    /** One stretch of a stream: what the eyes read, the score, and how long it lasts. */
    private fun feed(
        d: BlinkDetector,
        random: Random,
        fromMs: Long,
        durationMs: Long,
        level: Float,
        jitter: Float,
        stepMs: Long,
        onBlink: (BlinkObservation) -> Unit,
    ): Long {
        var t = fromMs
        while (t < fromMs + durationMs) {
            val glitch = random.nextFloat() < 0.01f
            var v = level + (random.nextGaussian() * jitter).toFloat()
            if (glitch) v *= random.nextFloat()
            v = v.coerceIn(0f, 1f)
            val score = when (random.nextInt(4)) {
                0 -> null
                1 -> Float.NaN
                2 -> random.nextFloat()
                else -> if (level < 0.15f) 0.9f else 0.05f
            }
            val dark = random.nextFloat() < 0.01f
            val s = EyeOpennessSample(t, v, v, 0.9f, if (dark) 5f else 120f, blinkScore = score)
            d.onSample(s)?.let(onBlink)
            assertFalse("closure must be a number", d.closure.isNaN())
            assertTrue("closure within 0..1: ${d.closure}", d.closure in 0f..1f)
            assertTrue("shut implies closed", !d.isShut || d.isClosed)
            t += stepMs
        }
        return t
    }

    @Test
    fun `whatever it is fed, the detector stays within its rules and always finds its way back`() {
        val master = Random(20_241_018)
        repeat(250) { round ->
            val random = Random(master.nextLong())
            val settings = randomSettings(random)
            val d = BlinkDetector(settings)
            if (random.nextBoolean()) d.seed(0.1f + random.nextFloat() * 0.8f)
            val stepMs = listOf(16L, 33L, 66L, 100L, 200L)[random.nextInt(5)]
            val open = 0.15f + random.nextFloat() * 0.75f
            var t = random.nextInt(10_000).toLong()
            val blinks = mutableListOf<BlinkObservation>()
            val record: (BlinkObservation) -> Unit = { blinks += it }

            repeat(1 + random.nextInt(12)) {
                val level = when (random.nextInt(5)) {
                    0 -> open                                    // steady open eyes
                    1 -> open * 0.08f                            // shut
                    2 -> open * (0.45f + random.nextFloat() * 0.4f)  // a squint, a lowered gaze
                    3 -> random.nextFloat()                       // anything at all
                    else -> open * (1f + random.nextFloat() * 0.3f)  // wider than before
                }.coerceIn(0f, 1f)
                t = feed(d, random, t, 100L + random.nextInt(40_000), level, 0.005f + random.nextFloat() * 0.06f, stepMs, record)
            }

            for (b in blinks) {
                assertTrue("round $round: duration ${b.durationMs} within ${settings.minDurationMs}..${settings.maxDurationMs}",
                    b.durationMs in settings.minDurationMs..settings.maxDurationMs)
                assertTrue("round $round: depth ${b.depth}", b.depth in 0f..1f)
                assertTrue("round $round: minOpenness ${b.minOpenness}", b.minOpenness in 0f..1f)
            }

            // Recovery: steady open eyes at some level for forty seconds leave the detector idle and able to see a blink.
            val level = 0.2f + random.nextFloat() * 0.7f
            val clean = EyeOpennessSample(0, level, level, 0.9f, 120f)
            var u = t
            while (u < t + 40_000) {
                d.onSample(clean.copy(timestampMs = u, leftEyeOpenness = level, rightEyeOpenness = level))
                u += stepMs
            }
            assertFalse("round $round: still closed after forty quiet seconds (settings $settings)", d.isClosed)
            assertTrue("round $round: closure ${d.closure} while the eyes are steady", d.closure < 0.1f)
            val found = mutableListOf<BlinkObservation>()
            var k = 0
            while (k < 6) {
                // a clean, decisive blink: shut for 3 frames or 150 ms, whichever is longer
                val shutMs = maxOf(150L, 3 * stepMs)
                var w = u
                while (w < u + shutMs) { d.onSample(clean.copy(timestampMs = w, leftEyeOpenness = level * 0.05f, rightEyeOpenness = level * 0.05f))?.let { found += it }; w += stepMs }
                d.onSample(clean.copy(timestampMs = w, leftEyeOpenness = level, rightEyeOpenness = level))?.let { found += it }
                u = w + 1_000
                var q = w + stepMs
                while (q < u) { d.onSample(clean.copy(timestampMs = q, leftEyeOpenness = level, rightEyeOpenness = level)); q += stepMs }
                k++
            }
            val blinkable = shutLong(settings, stepMs)
            if (!blinkable) assertTrue("round $round: none of six clean blinks was seen, settings $settings, step $stepMs", found.isNotEmpty())
        }
    }

    /** Blinks of this length are longer than the settings allow, or shorter than they ask for: nothing to find. */
    private fun shutLong(settings: DetectorSettings, stepMs: Long): Boolean {
        val shutMs = maxOf(150L, 3 * stepMs)
        return shutMs + stepMs > settings.maxDurationMs || shutMs < settings.minDurationMs
    }

    @Test
    fun `the pipeline stays sane on a stream of faces that come and go, eyes that shut and frames that lie`() {
        val master = Random(777)
        repeat(60) { round ->
            val random = Random(master.nextLong())
            val p = TrackingPipeline(
                if (random.nextBoolean()) BlinkThresholds() else BlinkThresholds(0.27f, 0.12f, 0.5f, true, 0.2f + random.nextFloat() * 0.6f),
                0.6f + random.nextFloat() * 1.2f,
            )
            var t = 0L
            var open = 0.2f + random.nextFloat() * 0.7f
            repeat(2_000) {
                t += 16L + random.nextInt(120)
                if (random.nextFloat() < 0.002f) t += 500L + random.nextInt(5_000)          // the camera stalls
                if (random.nextFloat() < 0.003f) open = (0.2f + random.nextFloat() * 0.7f)  // the eyes read differently
                val face = random.nextFloat() > 0.03f
                val shut = (t / 1_000) % 40 in 30..33                                       // four seconds shut in every forty
                val v = (if (shut) open * 0.06f else open) + (random.nextGaussian() * 0.01).toFloat()
                val sample = if (face) EyeOpennessSample(t, v.coerceIn(0f, 1f), v.coerceIn(0f, 1f), random.nextFloat(), 40f + random.nextFloat() * 100f,
                    blinkScore = if (random.nextBoolean()) random.nextFloat() else null) else null
                val ev = p.onFrame(FaceFrame(t, sample, good, 100f))
                ev.blink?.let { b ->
                    assertTrue("round $round: ${b.durationMs}", b.durationMs > 0)
                    assertTrue(ev.trackable)
                }
                assertFalse(p.closure.isNaN())
                if (random.nextFloat() < 0.002f) p.setThresholds(BlinkThresholds(0.3f, 0.13f, 0.55f, random.nextBoolean(), random.nextFloat()), 0.6f + random.nextFloat())
            }
            val r = p.rate.observed(t)
            assertTrue("round $round: rate ${r.blinksPerMinute}", r.blinksPerMinute.isFinite() && r.blinksPerMinute >= 0f)
            assertTrue(r.trackedMs >= 0)
        }
    }

    @Test
    fun `a calibration that is fed rubbish still ends, still counts only blinks, and fits sane thresholds`() {
        val master = Random(31_337)
        repeat(30) { round ->
            val random = Random(master.nextLong())
            val s = CalibrationSession(videoMs = 5_000, readingMs = 5_000, deliberateBlinksRequired = 3)
            s.start(0)
            var t = 0L
            var open = 0.2f + random.nextFloat() * 0.7f
            while (t < 120_000 && s.phase != CalibrationSession.Phase.COMPLETE) {
                t += 16L + random.nextInt(80)
                s.onTick(t)
                if (random.nextFloat() < 0.002f) t += 500L + random.nextInt(3_000)
                if (random.nextFloat() < 0.02f) { s.onTrackingLost(); continue }
                val blinking = (t / 300) % 11 == 0L
                val shut = (t / 1_000) % 25 in 20..22
                val v = ((if (blinking || shut) open * 0.1f else open) + (random.nextGaussian() * 0.02).toFloat()).coerceIn(0f, 1f)
                val score = if (random.nextBoolean()) (if (blinking || shut) 0.9f else 0.05f) + (random.nextGaussian() * 0.05).toFloat() else null
                s.process(EyeOpennessSample(t, v, v, 0.5f + random.nextFloat() * 0.5f, if (random.nextFloat() < 0.02f) 3f else 100f, score?.coerceIn(0f, 1f)))
                assertFalse(s.closure.isNaN())
            }
            val r = s.result()
            val th = r.profile.thresholds
            assertEquals("round $round: ${th}", th, th.sanitised())
            assertTrue("round $round", r.instructedCount <= 3)
            if (th.fitted) assertNotNull(th.openLevel)
        }
    }
}

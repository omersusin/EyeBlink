package com.eyeblink.core.perf

import com.eyeblink.core.engine.Thermal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyTrackerTest {
    @Test
    fun `percentiles over a known set`() {
        val t = LatencyTracker()
        (1..100).forEach { t.add(it.toLong()) }
        assertEquals(50, t.p50)
        assertEquals(95, t.p95)
        assertEquals(50.5, t.mean, 1e-9)
    }

    @Test
    fun `old samples roll off`() {
        val t = LatencyTracker(capacity = 10)
        repeat(100) { t.add(1_000) }
        repeat(10) { t.add(5) }
        assertEquals(5, t.p95)
        assertEquals(10, t.count)
    }

    @Test
    fun `empty tracker reports zero`() {
        assertEquals(0, LatencyTracker().p95)
    }
}

class PerfPolicyTest {
    private fun advise(p95: Long = 20, thermal: Thermal = Thermal.NONE, battery: Int = 80, charging: Boolean = false) =
        PerfPolicy.advise(p95, frameBudgetMs = 66, thermal = thermal, batteryPercent = battery, charging = charging, saverBelowPercent = 20)

    @Test
    fun `healthy conditions change nothing`() {
        val a = advise()
        assertEquals(1f, a.fpsFactor, 0f)
        assertFalse(a.cameraOff)
        assertEquals(PerfReason.OK, a.reason)
    }

    @Test
    fun `slow frames first lower the rate then go to bursts only`() {
        assertEquals(0.75f, advise(p95 = 90).fpsFactor, 0f)
        val worse = advise(p95 = 200)
        assertEquals(0.5f, worse.fpsFactor, 0f)
        assertTrue(worse.burstOnly)
        assertFalse(worse.cameraOff)
    }

    @Test
    fun `heat is graded`() {
        assertTrue(advise(thermal = Thermal.MODERATE).burstOnly)
        assertTrue(advise(thermal = Thermal.SEVERE).cameraOff)
        assertTrue(advise(thermal = Thermal.CRITICAL).cameraOff)
    }

    @Test
    fun `low battery turns the camera off unless charging`() {
        assertTrue(advise(battery = 15).cameraOff)
        assertFalse(advise(battery = 15, charging = true).cameraOff)
    }
}

class BatteryLedgerTest {
    private fun s(hour: Float, pct: Int, tag: TrackingTag, charging: Boolean = false, screen: Boolean = true) =
        BatterySample((hour * 3_600_000L).toLong(), pct, charging, screen, tag)

    private fun ledger(vararg samples: BatterySample) = samples.fold(BatteryLedger()) { l, x -> l.add(x) }

    @Test
    fun `drain per hour is the drop over the observed time`() {
        val l = ledger(
            s(0f, 100, TrackingTag.CAMERA_FREE), s(0.25f, 98, TrackingTag.CAMERA_FREE), s(0.5f, 96, TrackingTag.CAMERA_FREE),
        )
        val e = l.estimate(TrackingTag.CAMERA_FREE)!!
        assertEquals(8f, e.percentPerHour, 1e-3f)
        assertEquals(0.5f, e.hoursObserved, 1e-3f)
        assertTrue(e.trustworthy)
    }

    @Test
    fun `the camera's extra cost is the difference between the two modes`() {
        val l = ledger(
            s(0f, 100, TrackingTag.CAMERA_FREE), s(0.5f, 96, TrackingTag.CAMERA_FREE),
            s(0.6f, 96, TrackingTag.CAMERA_ASSISTED), s(1.1f, 90, TrackingTag.CAMERA_ASSISTED),
        )
        // free: 8 %/h; assisted: 12 %/h
        assertEquals(4f, l.compare().extraPerHour!!, 1e-3f)
    }

    @Test
    fun `charging and screen off intervals are never blamed on a mode`() {
        val l = ledger(
            s(0f, 50, TrackingTag.CAMERA_ASSISTED, charging = true), s(0.5f, 90, TrackingTag.CAMERA_ASSISTED, charging = true),
            s(1f, 90, TrackingTag.CAMERA_ASSISTED, screen = false), s(1.5f, 89, TrackingTag.CAMERA_ASSISTED, screen = false),
        )
        assertNull(l.estimate(TrackingTag.CAMERA_ASSISTED))
    }

    @Test
    fun `an interval that spans a mode change is not counted for either mode`() {
        val l = ledger(s(0f, 100, TrackingTag.CAMERA_FREE), s(0.5f, 95, TrackingTag.CAMERA_ASSISTED))
        assertNull(l.estimate(TrackingTag.CAMERA_FREE))
        assertNull(l.estimate(TrackingTag.CAMERA_ASSISTED))
    }

    @Test
    fun `too little data is not trustworthy and gives no comparison`() {
        val l = ledger(
            s(0f, 100, TrackingTag.CAMERA_FREE), s(0.1f, 99, TrackingTag.CAMERA_FREE),
            s(0.1f, 99, TrackingTag.CAMERA_ASSISTED), s(0.2f, 97, TrackingTag.CAMERA_ASSISTED),
        )
        assertNotNull(l.estimate(TrackingTag.CAMERA_FREE))
        assertFalse(l.estimate(TrackingTag.CAMERA_FREE)!!.trustworthy)
        assertNull(l.compare().extraPerHour)
    }

    @Test
    fun `a rising level without charging is treated as a glitch`() {
        val l = ledger(s(0f, 50, TrackingTag.CAMERA_FREE), s(0.4f, 60, TrackingTag.CAMERA_FREE), s(0.5f, 58, TrackingTag.CAMERA_FREE))
        val e = l.estimate(TrackingTag.CAMERA_FREE)!!
        assertEquals(20f, e.percentPerHour, 1e-3f)
    }

    @Test
    fun `out of order samples are ignored and the list is bounded`() {
        var l = ledger(s(1f, 90, TrackingTag.CAMERA_FREE), s(0.5f, 95, TrackingTag.CAMERA_FREE))
        assertEquals(1, l.samples.size)
        repeat(BatteryLedger.MAX_SAMPLES + 50) { l = l.add(s(2f + it, 80, TrackingTag.CAMERA_FREE)) }
        assertEquals(BatteryLedger.MAX_SAMPLES, l.samples.size)
    }
}

class ContentChangeDetectorTest {

    private fun gradient(w: Int = 90, h: Int = 80, leftBright: Boolean = true) =
        ByteArray(w * h) { i -> val x = i % w; ((if (leftBright) 255 - x * 255 / w else x * 255 / w)).toByte() }

    private fun noise(seed: Int, w: Int = 90, h: Int = 80): ByteArray {
        val r = java.util.Random(seed.toLong())
        return ByteArray(w * h) { r.nextInt(256).toByte() }
    }

    @Test
    fun `identical frames have identical fingerprints`() {
        assertEquals(FrameFingerprint.of(gradient(), 90, 80), FrameFingerprint.of(gradient(), 90, 80))
    }

    @Test
    fun `the fingerprint does not depend on the thumbnail size for the same picture`() {
        val small = FrameFingerprint.of(gradient(45, 40), 45, 40)
        val big = FrameFingerprint.of(gradient(180, 160), 180, 160)
        assertTrue(FrameFingerprint.distance(small, big) <= 4)
    }

    @Test
    fun `opposite gradients are far apart`() {
        val a = FrameFingerprint.of(gradient(leftBright = true), 90, 80)
        val b = FrameFingerprint.of(gradient(leftBright = false), 90, 80)
        assertTrue(FrameFingerprint.distance(a, b) > 40)
    }

    @Test
    fun `a slight brightness drift is not a content change`() {
        val d = ContentChangeDetector()
        val base = gradient()
        val brighter = ByteArray(base.size) { i -> ((base[i].toInt() and 0xFF) * 0.97f).toInt().toByte() }
        d.onFrame(FrameFingerprint.of(base, 90, 80), 0)
        assertFalse(d.onFrame(FrameFingerprint.of(brighter, 90, 80), 2_000))
    }

    @Test
    fun `a scene cut is detected once and then rate limited`() {
        val d = ContentChangeDetector()
        assertFalse(d.onFrame(FrameFingerprint.of(noise(1), 90, 80), 0))
        assertTrue(d.onFrame(FrameFingerprint.of(noise(2), 90, 80), 2_000))
        assertFalse("too soon after the last change", d.onFrame(FrameFingerprint.of(noise(3), 90, 80), 2_500))
        assertTrue(d.onFrame(FrameFingerprint.of(noise(4), 90, 80), 4_000))
    }

    @Test
    fun `reset forgets the previous frame`() {
        val d = ContentChangeDetector()
        d.onFrame(FrameFingerprint.of(noise(1), 90, 80), 0)
        d.reset()
        assertFalse(d.onFrame(FrameFingerprint.of(noise(2), 90, 80), 5_000))
    }

    @Test
    fun `a buffer that is too small is rejected`() {
        try {
            FrameFingerprint.of(ByteArray(10), 90, 80)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}

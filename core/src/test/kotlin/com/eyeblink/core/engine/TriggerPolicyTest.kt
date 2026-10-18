package com.eyeblink.core.engine

import com.eyeblink.core.calibration.EyeOpennessSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPolicyTest {

    private fun select(
        enabled: Boolean = true,
        ready: Boolean = true,
        screenOn: Boolean = true,
        confidence: Float? = 0.9f,
    ) = TriggerPolicy.select(enabled, ready, screenOn, confidence)

    // --- the honesty rule -------------------------------------------------

    @Test
    fun `camera off means camera-free`() {
        assertEquals(TriggerMode.CAMERA_FREE, select(enabled = false))
    }

    @Test
    fun `an unready camera means camera-free`() {
        assertEquals(TriggerMode.CAMERA_FREE, select(ready = false))
    }

    @Test
    fun `screen off means camera-free`() {
        assertEquals(TriggerMode.CAMERA_FREE, select(screenOn = false))
    }

    @Test
    fun `no sample yet means camera-free`() {
        assertEquals(TriggerMode.CAMERA_FREE, select(confidence = null))
    }

    @Test
    fun `low confidence means camera-free`() {
        assertEquals(TriggerMode.CAMERA_FREE, select(confidence = 0.2f))
    }

    @Test
    fun `everything healthy means camera-assisted`() {
        assertEquals(TriggerMode.CAMERA_ASSISTED, select())
    }

    @Test
    fun `confidence exactly at the threshold still counts`() {
        assertEquals(
            TriggerMode.CAMERA_ASSISTED,
            select(confidence = TriggerPolicy.MIN_USABLE_CONFIDENCE),
        )
    }

    @Test
    fun `we never claim real blink detection in camera-free mode`() {
        assertFalse(TriggerPolicy.claimsRealBlinkDetection(TriggerMode.CAMERA_FREE))
        assertTrue(TriggerPolicy.claimsRealBlinkDetection(TriggerMode.CAMERA_ASSISTED))
    }

    // --- learning gate ----------------------------------------------------

    @Test
    fun `samples only become training data in camera-assisted mode`() {
        val good = EyeOpennessSample(0, 0.9f, 0.9f, confidence = 0.9f, illuminance = 200f)
        assertTrue(TriggerPolicy.isLearnable(TriggerMode.CAMERA_ASSISTED, good))
        assertFalse(
            "camera-off samples must never be learned from",
            TriggerPolicy.isLearnable(TriggerMode.CAMERA_FREE, good),
        )
    }

    @Test
    fun `a dark sample is not learnable even in camera-assisted mode`() {
        val dark = EyeOpennessSample(0, 0.9f, 0.9f, confidence = 0.9f, illuminance = 1f)
        assertFalse(TriggerPolicy.isLearnable(TriggerMode.CAMERA_ASSISTED, dark))
    }

    @Test
    fun `a low-confidence sample is not learnable`() {
        val unsure = EyeOpennessSample(0, 0.9f, 0.9f, confidence = 0.1f, illuminance = 200f)
        assertFalse(TriggerPolicy.isLearnable(TriggerMode.CAMERA_ASSISTED, unsure))
    }
}

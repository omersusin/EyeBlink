package com.eyeblink.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the interruption safety rules. These encode promises the spec makes to
 * the user, so they get real tests rather than comments:
 *  - never interrupt more often than the floor allows;
 *  - never resume playback the user paused themselves.
 */
class InterruptionPolicyTest {

    // --- interval clamping -------------------------------------------------

    @Test
    fun `interval below the floor is raised to it`() {
        assertEquals(MIN_BLINK_INTERVAL_MS, clampBlinkInterval(0))
        assertEquals(MIN_BLINK_INTERVAL_MS, clampBlinkInterval(-5_000))
        assertEquals(MIN_BLINK_INTERVAL_MS, clampBlinkInterval(4_999))
    }

    @Test
    fun `interval above the ceiling is lowered to it`() {
        assertEquals(MAX_BLINK_INTERVAL_MS, clampBlinkInterval(MAX_BLINK_INTERVAL_MS + 1))
        assertEquals(MAX_BLINK_INTERVAL_MS, clampBlinkInterval(Long.MAX_VALUE))
    }

    @Test
    fun `interval inside the range is untouched`() {
        assertEquals(DEFAULT_BLINK_INTERVAL_MS, clampBlinkInterval(DEFAULT_BLINK_INTERVAL_MS))
        assertEquals(9_000L, clampBlinkInterval(9_000))
        assertEquals(MIN_BLINK_INTERVAL_MS, clampBlinkInterval(MIN_BLINK_INTERVAL_MS))
        assertEquals(MAX_BLINK_INTERVAL_MS, clampBlinkInterval(MAX_BLINK_INTERVAL_MS))
    }

    @Test
    fun `default interval is inside the allowed range`() {
        assertTrue(DEFAULT_BLINK_INTERVAL_MS in MIN_BLINK_INTERVAL_MS..MAX_BLINK_INTERVAL_MS)
    }

    @Test
    fun `floor is below the ceiling`() {
        assertTrue(MIN_BLINK_INTERVAL_MS < MAX_BLINK_INTERVAL_MS)
    }

    // --- playback resume safety -------------------------------------------

    @Test
    fun `resume when we paused it and it is still paused`() {
        assertTrue(shouldResumePlayback(pausedByUs = true, stillPaused = true))
    }

    @Test
    fun `never resume when the user paused it, not us`() {
        assertFalse(shouldResumePlayback(pausedByUs = false, stillPaused = true))
    }

    @Test
    fun `never resume when it is playing again`() {
        assertFalse(shouldResumePlayback(pausedByUs = true, stillPaused = false))
    }

    @Test
    fun `never resume when neither condition holds`() {
        assertFalse(shouldResumePlayback(pausedByUs = false, stillPaused = false))
    }

    @Test
    fun `settle delay is short and positive`() {
        assertTrue(POST_PAUSE_SETTLE_MS in 1..1_000)
    }
}
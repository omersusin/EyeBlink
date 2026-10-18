package com.eyeblink.core.rest

import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.RestStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestSessionTest {

    private val t0 = 1_000L

    private fun session(
        confirm: ConfirmMode = ConfirmMode.AUTO,
        seconds: Int = 20,
        blinkOk: Boolean = true,
        style: RestStyle = RestStyle.DISTANCE_FOCUS,
    ) = RestSession(RestRequest(style, seconds, confirm, blinkOk), t0)

    private val restStart = t0 + RestSession.SETTLE_MS

    @Test
    fun `it settles then rests then finishes by itself in auto mode`() {
        val s = session()
        assertEquals(RestPhase.SETTLING, s.view(t0).phase)
        assertEquals(RestPhase.RESTING, s.view(restStart).phase)
        assertEquals(RestPhase.RESTING, s.view(restStart + 19_999).phase)
        val done = s.view(restStart + 20_000)
        assertEquals(RestPhase.DONE, done.phase)
        assertEquals(RestOutcome.COMPLETED, done.outcome)
        assertEquals(ConfirmedBy.AUTO, done.confirmedBy)
    }

    @Test
    fun `progress runs from zero to one over the resting period`() {
        val s = session(seconds = 10)
        assertEquals(0f, s.view(restStart).progress, 1e-4f)
        assertEquals(0.5f, s.view(restStart + 5_000).progress, 1e-4f)
        assertEquals(1f, s.view(restStart + 10_000).progress, 1e-4f)
    }

    @Test
    fun `remaining time counts down and never goes negative`() {
        val s = session(seconds = 10)
        assertEquals(10_000L, s.view(restStart).remainingMs)
        assertEquals(4_000L, s.view(restStart + 6_000).remainingMs)
        assertEquals(0L, s.view(restStart + 50_000).remainingMs)
    }

    @Test
    fun `skipping ends the rest immediately from any phase`() {
        for (at in listOf(t0, restStart + 3_000)) {
            val s = session()
            s.skip(at)
            val v = s.view(at)
            assertEquals(RestPhase.DONE, v.phase)
            assertEquals(RestOutcome.SKIPPED, v.outcome)
        }
    }

    @Test
    fun `cancelling reports cancelled not skipped`() {
        val s = session()
        s.cancel(restStart + 1_000)
        assertEquals(RestOutcome.CANCELLED, s.view(restStart + 1_000).outcome)
    }

    @Test
    fun `a finished session cannot be changed afterwards`() {
        val s = session()
        s.skip(restStart)
        s.cancel(restStart + 100)
        s.onTap(restStart + 200)
        assertEquals(RestOutcome.SKIPPED, s.view(restStart + 300).outcome)
    }

    // --- tap confirmation --------------------------------------------------

    @Test
    fun `tap mode waits for a tap after the rest`() {
        val s = session(ConfirmMode.TAP)
        val end = restStart + 20_000
        val v = s.view(end + 1_000)
        assertEquals(RestPhase.AWAITING_CONFIRM, v.phase)
        assertEquals(ConfirmMode.TAP, v.awaiting)
        assertTrue(s.onTap(end + 2_000))
        val done = s.view(end + 2_000)
        assertEquals(RestOutcome.COMPLETED, done.outcome)
        assertEquals(ConfirmedBy.TAP, done.confirmedBy)
    }

    @Test
    fun `a tap during the rest itself does nothing`() {
        val s = session(ConfirmMode.TAP)
        assertFalse(s.onTap(restStart + 5_000))
        assertEquals(RestPhase.RESTING, s.view(restStart + 5_000).phase)
    }

    @Test
    fun `tap mode gives up waiting after the timeout and completes`() {
        val s = session(ConfirmMode.TAP)
        val end = restStart + 20_000
        val v = s.view(end + RestSession.TAP_TIMEOUT_MS)
        assertEquals(RestPhase.DONE, v.phase)
        assertEquals(ConfirmedBy.TIMEOUT, v.confirmedBy)
        assertEquals(RestOutcome.COMPLETED, v.outcome)
    }

    @Test
    fun `auto mode ignores taps and blinks`() {
        val s = session(ConfirmMode.AUTO)
        val end = restStart + 20_000
        // before the end nothing counts; after it the session is simply over
        assertFalse(s.onBlink(end - 1))
        assertFalse(s.onTap(end - 1))
    }

    // --- blink confirmation ------------------------------------------------

    @Test
    fun `one real blink confirms a blink mode rest`() {
        val s = session(ConfirmMode.BLINK)
        val end = restStart + 20_000
        assertEquals(ConfirmMode.BLINK, s.view(end + 500).awaiting)
        assertTrue(s.onBlink(end + 800))
        assertEquals(ConfirmedBy.BLINK, s.view(end + 800).confirmedBy)
    }

    @Test
    fun `a blink during the rest itself is not a confirmation`() {
        val s = session(ConfirmMode.BLINK)
        assertFalse(s.onBlink(restStart + 5_000))
        assertNull(s.view(restStart + 5_000).outcome)
    }

    @Test
    fun `a tap is always an acceptable alternative to a blink`() {
        val s = session(ConfirmMode.BLINK)
        val end = restStart + 20_000
        assertTrue(s.onTap(end + 100))
        assertEquals(ConfirmedBy.TAP, s.view(end + 100).confirmedBy)
    }

    @Test
    fun `blink mode never traps the user - it times out`() {
        val s = session(ConfirmMode.BLINK)
        val end = restStart + 20_000
        val v = s.view(end + RestSession.BLINK_TIMEOUT_MS)
        assertEquals(RestPhase.DONE, v.phase)
        assertEquals(ConfirmedBy.TIMEOUT, v.confirmedBy)
    }

    @Test
    fun `without a working camera blink mode quietly becomes tap mode`() {
        val s = session(ConfirmMode.BLINK, blinkOk = false)
        val end = restStart + 20_000
        assertEquals(ConfirmMode.TAP, s.view(end + 100).awaiting)
        assertFalse("a blink must not confirm when the camera is not trusted", s.onBlink(end + 200))
        assertTrue(s.onTap(end + 300))
    }

    @Test
    fun `a blink never confirms a tap only rest`() {
        val s = session(ConfirmMode.TAP)
        assertFalse(s.onBlink(restStart + 20_000 + 100))
    }

    @Test
    fun `blackout rests behave the same way`() {
        val s = session(style = RestStyle.BLACKOUT, seconds = 5)
        assertEquals(RestPhase.DONE, s.view(restStart + 5_000).phase)
    }
}

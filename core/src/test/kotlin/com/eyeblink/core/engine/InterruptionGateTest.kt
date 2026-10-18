package com.eyeblink.core.engine

import com.eyeblink.core.model.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptionGateTest {

    private val now = 1_000_000L
    private val ok = ContextSnapshot(nowMs = now, foregroundPackage = "com.instagram.android")
    private val policy = GatePolicy(appSelected = true)

    private fun eval(ctx: ContextSnapshot = ok, p: GatePolicy = policy) = InterruptionGate.evaluate(ctx, p)

    @Test
    fun `a quiet selected app is allowed`() {
        assertTrue(eval().allowed)
    }

    // --- eligibility -------------------------------------------------------

    @Test
    fun `master switch suspend and snooze make the situation ineligible`() {
        assertEquals(Reason.DISABLED, eval(p = policy.copy(masterEnabled = false)).reason)
        assertEquals(Reason.SUSPENDED, eval(p = policy.copy(suspended = true)).reason)
        val snoozed = eval(p = policy.copy(snoozedUntilMs = now + 5_000))
        assertEquals(Reason.SNOOZED, snoozed.reason)
        assertEquals(5_000L, snoozed.retryInMs)
    }

    @Test
    fun `screen off and locked device are ineligible`() {
        assertEquals(Reason.SCREEN_OFF, eval(ok.copy(screenOn = false)).reason)
        assertEquals(Reason.DEVICE_LOCKED, eval(ok.copy(deviceLocked = true)).reason)
    }

    @Test
    fun `an app the user did not select is never interrupted`() {
        assertEquals(Reason.NOT_SELECTED_APP, eval(p = policy.copy(appSelected = false)).reason)
    }

    @Test
    fun `the never list wins even when the user selected the app`() {
        for (pkg in listOf(
            "com.android.systemui", "com.android.settings", "com.google.android.dialer",
            "com.samsung.android.incallui", "com.sec.android.app.camera", "com.google.android.gms",
        )) {
            assertEquals(pkg, Reason.NEVER_LIST, eval(ok.copy(foregroundPackage = pkg)).reason)
        }
    }

    @Test
    fun `an unknown foreground app is treated as ineligible by default`() {
        assertEquals(Reason.UNKNOWN_APP, eval(ok.copy(foregroundPackage = null)).reason)
        assertTrue(eval(ok.copy(foregroundPackage = null), policy.copy(requireKnownApp = false)).allowed)
    }

    @Test
    fun `quiet hours and thermal limits are ineligible`() {
        assertEquals(Reason.QUIET_HOURS, eval(p = policy.copy(quietHoursActive = true)).reason)
        assertEquals(Reason.THERMAL, eval(ok.copy(thermal = Thermal.SEVERE)).reason)
        assertTrue(eval(ok.copy(thermal = Thermal.MODERATE)).allowed)
    }

    // --- hard holds --------------------------------------------------------

    @Test
    fun `calls ringing or in progress hold every break`() {
        for (mode in listOf(AudioMode.RINGTONE, AudioMode.IN_CALL, AudioMode.IN_COMMUNICATION)) {
            val d = eval(ok.copy(audioMode = mode))
            assertEquals(Reason.IN_CALL, d.reason)
            assertEquals(Reason.Kind.HOLD, d.reason!!.kind)
        }
    }

    @Test
    fun `another app using the camera holds breaks`() {
        assertEquals(Reason.CAMERA_IN_USE, eval(ok.copy(cameraBusyElsewhere = true)).reason)
    }

    @Test
    fun `navigation and sensitive screens hold breaks`() {
        assertEquals(Reason.NAVIGATION, eval(ok.copy(navigationActive = true)).reason)
        assertEquals(Reason.SENSITIVE_SCREEN, eval(ok.copy(sensitiveScreen = true)).reason)
    }

    @Test
    fun `typing holds breaks whether the keyboard or just a focused field is the signal`() {
        assertEquals(Reason.TEXT_INPUT, eval(ok.copy(keyboardVisible = true)).reason)
        assertEquals(Reason.TEXT_INPUT, eval(ok.copy(textInputFocused = true)).reason)
    }

    // --- gestures ----------------------------------------------------------

    @Test
    fun `a finger on the screen holds the break`() {
        val d = eval(ok.copy(touching = true))
        assertEquals(Reason.TOUCHING, d.reason)
        assertEquals(Reason.Kind.HOLD, d.reason!!.kind)
    }

    @Test
    fun `a finger that just lifted still counts until the gesture settles`() {
        val d = eval(ok.copy(lastTouchMs = now - 100))
        assertEquals(Reason.RECENT_TOUCH, d.reason)
        assertEquals(InterruptionGate.TOUCH_SETTLE_MS - 100, d.retryInMs)
        assertTrue(eval(ok.copy(lastTouchMs = now - InterruptionGate.TOUCH_SETTLE_MS)).allowed)
    }

    @Test
    fun `a swipe in flight is a video transition`() {
        val d = eval(ok.copy(lastScrollMs = now - 200))
        assertEquals(Reason.VIDEO_TRANSITION, d.reason)
        assertEquals(Reason.Kind.SOFT, d.reason!!.kind)
        assertTrue(eval(ok.copy(lastScrollMs = now - InterruptionGate.TRANSITION_SETTLE_MS)).allowed)
    }

    // --- precedence --------------------------------------------------------

    @Test
    fun `the most fundamental blocker is the one reported`() {
        val everything = ok.copy(
            screenOn = false, audioMode = AudioMode.IN_CALL, touching = true, keyboardVisible = true,
        )
        assertEquals(Reason.SCREEN_OFF, eval(everything).reason)
        val callAndTouch = ok.copy(audioMode = AudioMode.IN_CALL, touching = true)
        assertEquals(Reason.IN_CALL, eval(callAndTouch).reason)
        val keyboardAndTouch = ok.copy(keyboardVisible = true, touching = true)
        assertEquals(Reason.TEXT_INPUT, eval(keyboardAndTouch).reason)
    }

    @Test
    fun `reason kinds classify as documented`() {
        assertEquals(Reason.Kind.INELIGIBLE, Reason.NOT_SELECTED_APP.kind)
        assertEquals(Reason.Kind.HOLD, Reason.COOLDOWN.kind)
        assertEquals(Reason.Kind.SOFT, Reason.PREFER_TRANSITION.kind)
        assertFalse(GateDecision.Allow.reason != null)
    }

    // --- quiet hours -------------------------------------------------------

    @Test
    fun `quiet hours that wrap past midnight cover both sides`() {
        val q = QuietHours(enabled = true, startMin = 23 * 60, endMin = 7 * 60)
        assertTrue(q.contains(23 * 60 + 30))
        assertTrue(q.contains(0))
        assertTrue(q.contains(6 * 60 + 59))
        assertFalse(q.contains(7 * 60))
        assertFalse(q.contains(12 * 60))
    }

    @Test
    fun `same day quiet hours and the disabled state`() {
        val q = QuietHours(enabled = true, startMin = 13 * 60, endMin = 14 * 60)
        assertTrue(q.contains(13 * 60 + 30))
        assertFalse(q.contains(14 * 60))
        assertFalse(QuietHours(enabled = false).contains(3 * 60))
        assertFalse(QuietHours(enabled = true, startMin = 600, endMin = 600).contains(600))
    }
}

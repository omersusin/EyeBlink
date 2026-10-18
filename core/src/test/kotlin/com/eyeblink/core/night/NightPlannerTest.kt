package com.eyeblink.core.night

import com.eyeblink.core.model.NightSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightPlannerTest {

    private val on = NightSettings(enabled = true, manageNightLight = true, manageBrightness = true, startMin = 21 * 60, endMin = 6 * 60)

    private fun target(s: NightSettings = on, minute: Int = 23 * 60, lux: Float? = null, now: Long = 1_000, overrideUntil: Long = 0) =
        NightPlanner.target(NightInputs(s, minute, now, lux, overrideUntil))

    @Test
    fun `disabled does nothing at all`() {
        val t = target(on.copy(enabled = false))
        assertFalse(t.active)
        assertEquals(NightReason.DISABLED, t.reason)
        assertNull(t.nightLight)
        assertNull(t.brightnessPercent)
    }

    @Test
    fun `inside the window night mode is active with the configured values`() {
        val t = target()
        assertTrue(t.active)
        assertEquals(NightReason.SCHEDULE, t.reason)
        assertEquals(true, t.nightLight)
        assertEquals(on.warmthKelvin, t.warmthKelvin)
    }

    @Test
    fun `outside the window it is daytime and nothing is touched`() {
        val t = target(minute = 12 * 60)
        assertFalse(t.active)
        assertEquals(NightReason.DAYTIME, t.reason)
        assertNull(t.nightLight)
    }

    @Test
    fun `a window crossing midnight covers both sides`() {
        assertTrue(NightPlanner.inWindow(23 * 60 + 30, 21 * 60, 6 * 60))
        assertTrue(NightPlanner.inWindow(2 * 60, 21 * 60, 6 * 60))
        assertFalse(NightPlanner.inWindow(6 * 60, 21 * 60, 6 * 60))
        assertFalse(NightPlanner.inWindow(15 * 60, 21 * 60, 6 * 60))
    }

    @Test
    fun `a manual brightness change pauses automation`() {
        val t = target(now = 5_000, overrideUntil = 60_000)
        assertFalse(t.active)
        assertEquals(NightReason.MANUAL_OVERRIDE, t.reason)
        assertTrue(target(now = 60_000, overrideUntil = 60_000).active)
    }

    @Test
    fun `a dark room counts as night even at midday when ambient adaptation is on`() {
        val t = target(minute = 12 * 60, lux = 1f)
        assertTrue(t.active)
        assertEquals(NightReason.DARK_ROOM, t.reason)
        assertFalse(target(on.copy(adaptToAmbientLight = false), minute = 12 * 60, lux = 1f).active)
    }

    @Test
    fun `a brighter room gets a brighter night screen than a dark one`() {
        val dark = target(lux = 2f).brightnessPercent!!
        val dim = target(lux = 60f).brightnessPercent!!
        assertTrue("dark=$dark dim=$dim", dark < dim)
    }

    @Test
    fun `brightness never goes below the users floor`() {
        val harsh = on.copy(nightBrightnessPercent = 10, brightnessFloorPercent = 25)
        assertTrue(target(harsh, lux = 0f).brightnessPercent!! >= 25)
        val s = NightSettings(brightnessFloorPercent = 40, nightBrightnessPercent = 5).validated()
        assertTrue(s.nightBrightnessPercent >= s.brightnessFloorPercent)
    }

    @Test
    fun `night light and brightness are independent opt ins`() {
        val onlyLight = target(on.copy(manageBrightness = false))
        assertEquals(true, onlyLight.nightLight)
        assertNull(onlyLight.brightnessPercent)
        val onlyBrightness = target(on.copy(manageNightLight = false))
        assertNull(onlyBrightness.nightLight)
        assertNull(onlyBrightness.warmthKelvin)
        assertTrue(onlyBrightness.brightnessPercent != null)
    }

    @Test
    fun `warmth is kept to a sane range`() {
        assertEquals(2300, NightSettings(warmthKelvin = 100).validated().warmthKelvin)
        assertEquals(6500, NightSettings(warmthKelvin = 99_999).validated().warmthKelvin)
    }

    // --- ramp ---------------------------------------------------------------

    @Test
    fun `ramp moves at a constant rate and never overshoots`() {
        var v = 100f
        val target = 40f
        var elapsed = 0L
        while (v != target) {
            val next = NightPlanner.ramp(v, target, 1_000, 20_000)
            assertTrue(next >= target)
            assertTrue(v - next <= 5.0001f)
            v = next
            elapsed += 1_000
            assertTrue(elapsed < 30_000)
        }
        assertEquals(12_000L, elapsed)
    }

    @Test
    fun `ramp works downward and upward and snaps when no ramp is wanted`() {
        assertEquals(60f, NightPlanner.ramp(50f, 60f, 10_000, 20_000), 0f)
        assertEquals(55f, NightPlanner.ramp(50f, 90f, 1_000, 20_000), 1e-4f)
        assertEquals(90f, NightPlanner.ramp(50f, 90f, 1_000, 0), 0f)
        assertEquals(50f, NightPlanner.ramp(50f, 90f, -5, 20_000), 0f)
    }

    @Test
    fun `a value the user set is recognised as manual`() {
        assertTrue(NightPlanner.isManualChange(80, lastWrittenPercent = 35))
        assertFalse(NightPlanner.isManualChange(36, lastWrittenPercent = 35))
        assertFalse("nothing written yet means nothing to compare", NightPlanner.isManualChange(80, null))
    }
}

package com.eyeblink.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the documented timing contract from the spec:
 * close 140ms + hold 150ms + open 165ms = 455ms total.
 * A change to any phase that silently breaks the total would be caught here.
 */
class BlinkConfigTest {

    @Test
    fun `default timing totals 455ms`() {
        assertEquals(455L, BlinkConfig().totalMs)
    }

    @Test
    fun `total is the sum of the three phases`() {
        val c = BlinkConfig(closeMs = 200, holdMs = 300, openMs = 100)
        assertEquals(600L, c.totalMs)
    }

    @Test
    fun `appearance defaults are in range`() {
        val c = BlinkConfig()
        assertTrue("curve in 0..1", c.curve in 0f..1f)
        assertTrue("darkenStrength in 0..1", c.darkenStrength in 0f..1f)
    }

    @Test
    fun `enabled by default so first run animates`() {
        assertTrue(BlinkConfig().enabled)
        assertFalse(BlinkConfig().reducedMotion)
    }

    @Test
    fun `copy preserves unrelated fields`() {
        val c = BlinkConfig()
        val slower = c.copy(holdMs = 250)
        assertEquals(250L, slower.holdMs)
        assertEquals(c.closeMs, slower.closeMs)
        assertEquals(c.openMs, slower.openMs)
        assertEquals(c.curve, slower.curve, 0f)
        assertEquals(c.darkenStrength, slower.darkenStrength, 0f)
    }
}
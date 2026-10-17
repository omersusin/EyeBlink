package com.eyeblink.core.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {

    @Test
    fun `every curve starts at zero and ends at one`() {
        for (kind in EaseKind.entries) {
            assertEquals("$kind at 0", 0f, kind.at(0f), 1e-4f)
            assertEquals("$kind at 1", 1f, kind.at(1f), 1e-4f)
        }
    }

    @Test
    fun `input outside 0 to 1 is clamped`() {
        for (kind in EaseKind.entries) {
            assertEquals(kind.at(0f), kind.at(-3f), 0f)
            assertEquals(kind.at(1f), kind.at(9f), 0f)
        }
    }

    @Test
    fun `monotonic curves never go backwards`() {
        val monotonic = EaseKind.entries.filter { it != EaseKind.BACK_OUT }
        for (kind in monotonic) {
            var prev = -1f
            for (i in 0..200) {
                val v = kind.at(i / 200f)
                assertTrue("$kind decreased at step $i: $prev -> $v", v >= prev - 1e-5f)
                prev = v
            }
        }
    }

    @Test
    fun `back-out overshoots one before settling`() {
        val peak = (0..100).maxOf { EaseKind.BACK_OUT.at(it / 100f) }
        assertTrue("peak=$peak", peak > 1.0f)
    }

    @Test
    fun `linear is the identity`() {
        assertEquals(0.37f, EaseKind.LINEAR.at(0.37f), 1e-6f)
    }

    @Test
    fun `ease-in is slower than linear early and ease-out is faster`() {
        assertTrue(EaseKind.CUBIC_IN.at(0.3f) < 0.3f)
        assertTrue(EaseKind.CUBIC_OUT.at(0.3f) > 0.3f)
        assertTrue(EaseKind.QUAD_OUT.at(0.3f) > 0.3f)
    }

    @Test
    fun `symmetric curves cross the midpoint at one half`() {
        assertEquals(0.5f, EaseKind.SINE_IN_OUT.at(0.5f), 1e-5f)
        assertEquals(0.5f, EaseKind.CUBIC_IN_OUT.at(0.5f), 1e-5f)
    }

    @Test
    fun `natural opening settles later than natural closing`() {
        // The lid releases quickly then takes its time; closing is more even.
        assertTrue(EaseKind.NATURAL_OPEN.at(0.3f) > EaseKind.NATURAL_CLOSE.at(0.3f))
    }

    @Test
    fun `cubic bezier reproduces linear when control points lie on the diagonal`() {
        val c = CubicBezier(0.25f, 0.25f, 0.75f, 0.75f)
        for (i in 0..20) assertEquals(i / 20f, c.at(i / 20f), 1e-3f)
    }

    @Test
    fun `cubic bezier matches the well known ease curve at a known point`() {
        // CSS "ease" = cubic-bezier(0.25, 0.1, 0.25, 1). At x = 0.5 y is about 0.8024.
        val ease = CubicBezier(0.25f, 0.1f, 0.25f, 1.0f)
        assertEquals(0.8024f, ease.at(0.5f), 5e-3f)
    }

    @Test
    fun `ids round trip`() {
        for (kind in EaseKind.entries) assertEquals(kind, EaseKind.fromId(kind.id))
        assertEquals(null, EaseKind.fromId("nope"))
        assertNotNull(EaseKind.fromId("cubic"))
    }

    @Test
    fun `smoothstep clamps and eases`() {
        assertEquals(0f, smoothstep(0.2f, 0.8f, 0.1f), 0f)
        assertEquals(1f, smoothstep(0.2f, 0.8f, 0.9f), 0f)
        assertEquals(0.5f, smoothstep(0.2f, 0.8f, 0.5f), 1e-6f)
        // degenerate edges do not divide by zero
        assertEquals(1f, smoothstep(0.5f, 0.5f, 0.7f), 0f)
        assertEquals(0f, smoothstep(0.5f, 0.5f, 0.3f), 0f)
    }
}

package com.eyeblink.app.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeGeometryTest {

    // --- normalised aperture ---------------------------------------------

    @Test
    fun `aperture is the lid gap over face height`() {
        // lid gap 0.05, face height 0.50 -> 0.10
        val a = EyeGeometry.normalisedAperture(listOf(0.40f), listOf(0.45f), faceHeight = 0.50f)
        assertEquals(0.10f, a, 0.0001f)
    }

    @Test
    fun `aperture is scale invariant across face sizes`() {
        val small = EyeGeometry.normalisedAperture(listOf(0.20f), listOf(0.22f), faceHeight = 0.25f)
        val large = EyeGeometry.normalisedAperture(listOf(0.80f), listOf(0.88f), faceHeight = 1.00f)
        assertEquals(0.08f, small, 0.0001f)
        assertEquals(0.08f, large, 0.0001f)
    }

    @Test
    fun `averaging lid points damps ordinary per-point jitter`() {
        val clean = EyeGeometry.normalisedAperture(listOf(0.40f), listOf(0.45f), 0.50f)
        val jittered = EyeGeometry.normalisedAperture(listOf(0.41f, 0.39f), listOf(0.44f, 0.46f), 0.50f)
        assertTrue(
            "small landmark jitter should barely move the reading",
            kotlin.math.abs(clean - jittered) < 0.01f,
        )
    }

    @Test
    fun `a gross outlier is not rejected here, so the source must filter by visibility`() {
        // This function averages, and a mean does not reject outliers. Rejecting
        // low-visibility landmarks is the caller's job (MediaPipe exposes
        // NormalizedLandmark.visibility()); this test pins that division of
        // responsibility so nobody assumes robustness that is not there.
        val clean = EyeGeometry.normalisedAperture(listOf(0.40f), listOf(0.45f), 0.50f)
        val outlier = EyeGeometry.normalisedAperture(listOf(0.40f, 0.90f), listOf(0.45f), 0.50f)
        assertTrue("a mean is expected to be pulled by a gross outlier", outlier > clean)
    }

    @Test
    fun `closed eyes give a near-zero aperture`() {
        val a = EyeGeometry.normalisedAperture(listOf(0.45f), listOf(0.452f), faceHeight = 0.50f)
        assertTrue(a < EyeGeometry.CLOSED_APERTURE_RATIO)
    }

    @Test
    fun `empty lid lists give zero rather than throwing`() {
        assertEquals(0f, EyeGeometry.normalisedAperture(emptyList(), listOf(0.4f), 0.5f), 0f)
        assertEquals(0f, EyeGeometry.normalisedAperture(listOf(0.4f), emptyList(), 0.5f), 0f)
    }

    @Test
    fun `a zero face height gives zero rather than dividing by zero`() {
        val a = EyeGeometry.normalisedAperture(listOf(0.40f), listOf(0.45f), faceHeight = 0f)
        assertEquals(0f, a, 0f)
    }

    @Test
    fun `lid gap sign does not matter`() {
        // landmarks can arrive in either vertical order
        val a = EyeGeometry.normalisedAperture(listOf(0.45f), listOf(0.40f), faceHeight = 0.50f)
        assertEquals(0.10f, a, 0.0001f)
    }

    // --- openness mapping -------------------------------------------------

    @Test
    fun `the open reference maps to fully open`() {
        assertEquals(
            1f,
            EyeGeometry.openness(EyeGeometry.OPEN_APERTURE_RATIO),
            0.0001f,
        )
    }

    @Test
    fun `the closed reference maps to fully closed`() {
        assertEquals(
            0f,
            EyeGeometry.openness(EyeGeometry.CLOSED_APERTURE_RATIO),
            0.0001f,
        )
    }

    @Test
    fun `openness is clamped below zero`() {
        assertEquals(0f, EyeGeometry.openness(0f), 0f)
    }

    @Test
    fun `openness is clamped above one`() {
        assertEquals(1f, EyeGeometry.openness(0.5f), 0f)
    }

    @Test
    fun `a wider gap than the open reference still reads as fully open`() {
        assertEquals(1f, EyeGeometry.openness(EyeGeometry.OPEN_APERTURE_RATIO * 3), 0f)
    }

    @Test
    fun `mid-aperture lands between closed and open`() {
        val mid = (EyeGeometry.OPEN_APERTURE_RATIO + EyeGeometry.CLOSED_APERTURE_RATIO) / 2f
        val o = EyeGeometry.openness(mid)
        assertTrue(o > 0f && o < 1f)
    }

    @Test
    fun `openness increases monotonically with aperture`() {
        var previous = -1f
        for (i in 0..10) {
            val aperture = EyeGeometry.CLOSED_APERTURE_RATIO +
                (EyeGeometry.OPEN_APERTURE_RATIO - EyeGeometry.CLOSED_APERTURE_RATIO) * i / 10f
            val o = EyeGeometry.openness(aperture)
            assertTrue("openness must not decrease at step $i", o > previous)
            previous = o
        }
    }

    @Test
    fun `inverted references are handled without throwing`() {
        assertEquals(0f, EyeGeometry.openness(0.05f, openReference = 0.01f, closedReference = 0.10f), 0f)
    }

    @Test
    fun `per-user references override the defaults`() {
        // someone with smaller eyes reaches "open" at a smaller aperture
        assertEquals(1f, EyeGeometry.openness(0.06f, openReference = 0.06f), 0.0001f)
        assertTrue(EyeGeometry.openness(0.06f) < 1f)
    }

    // --- face height ------------------------------------------------------

    @Test
    fun `face height is the forehead to chin distance`() {
        assertEquals(0.4f, EyeGeometry.faceHeight(0.15f, 0.55f), 0.0001f)
        assertEquals(0.4f, EyeGeometry.faceHeight(0.55f, 0.15f), 0.0001f)
    }

    @Test
    fun `references are ordered so the mapping is well-formed`() {
        assertTrue(EyeGeometry.CLOSED_APERTURE_RATIO < EyeGeometry.OPEN_APERTURE_RATIO)
        assertTrue(EyeGeometry.CLOSED_APERTURE_RATIO >= 0f)
        assertTrue(EyeGeometry.OPEN_APERTURE_RATIO < 1f)
    }
}
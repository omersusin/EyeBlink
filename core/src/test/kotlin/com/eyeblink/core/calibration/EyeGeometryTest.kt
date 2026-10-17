package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class EyeGeometryTest {

    /** An eye of width [w] whose lids are [gap] apart, centred at ([cx], [cy]) and turned by [roll] radians. */
    private class Eye(w: Float, gap: Float, cx: Float = 0.5f, cy: Float = 0.5f, roll: Float = 0f, scale: Float = 1f) {
        val corners: FloatArray
        val upper: FloatArray
        val lower: FloatArray

        init {
            fun rot(x: Float, y: Float): Pair<Float, Float> =
                (cx + scale * (x * cos(roll) - y * sin(roll))) to (cy + scale * (x * sin(roll) + y * cos(roll)))
            val a = rot(-w / 2, 0f)
            val b = rot(w / 2, 0f)
            corners = floatArrayOf(a.first, a.second, b.first, b.second)
            val xs = floatArrayOf(-w / 4, 0f, w / 4)
            upper = FloatArray(6)
            lower = FloatArray(6)
            for (i in 0..2) {
                val u = rot(xs[i], -gap / 2)
                val l = rot(xs[i], gap / 2)
                upper[2 * i] = u.first; upper[2 * i + 1] = u.second
                lower[2 * i] = l.first; lower[2 * i + 1] = l.second
            }
        }

        fun ear(xScale: Float = 1f) = EyeGeometry.aspectRatio(corners, upper, lower, xScale)
    }

    @Test
    fun `the ratio is the lid gap over the eye width`() {
        assertEquals(0.30f, Eye(w = 0.10f, gap = 0.03f).ear(), 1e-4f)
        assertEquals(0.15f, Eye(w = 0.10f, gap = 0.015f).ear(), 1e-4f)
    }

    @Test
    fun `it does not depend on how big the eye is in the picture`() {
        val small = Eye(w = 0.05f, gap = 0.015f, scale = 1f).ear()
        val large = Eye(w = 0.05f, gap = 0.015f, scale = 4f).ear()
        assertEquals(small, large, 1e-4f)
    }

    @Test
    fun `it does not depend on where the eye is`() {
        assertEquals(Eye(0.1f, 0.03f, 0.2f, 0.3f).ear(), Eye(0.1f, 0.03f, 0.8f, 0.6f).ear(), 1e-5f)
    }

    @Test
    fun `it does not change when the head is rolled`() {
        val flat = Eye(0.1f, 0.03f).ear()
        for (roll in listOf(0.3f, -0.5f, 1.2f)) {
            assertEquals("roll $roll", flat, Eye(0.1f, 0.03f, roll = roll).ear(), 1e-4f)
        }
    }

    @Test
    fun `a closed eye reads close to zero`() {
        assertTrue(Eye(0.1f, 0.001f).ear() < 0.02f)
    }

    @Test
    fun `an image that is wider than it is tall is measured in the same unit on both axes`() {
        // 640 x 480: one step in x is 1.33 steps in y. A true gap of 0.03 over a width of 0.10 (in y units).
        val xScale = 640f / 480f
        val w = 0.10f / xScale   // the same width, expressed in the normalised x of a wide picture
        assertEquals(0.30f, Eye(w = w, gap = 0.03f).ear(xScale), 1e-4f)
    }

    @Test
    fun `input that cannot describe an eye reads zero`() {
        val eye = Eye(0.1f, 0.03f)
        assertEquals(0f, EyeGeometry.aspectRatio(floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f), eye.upper, eye.lower), 0f)
        assertEquals(0f, EyeGeometry.aspectRatio(floatArrayOf(0f, 0f), eye.upper, eye.lower), 0f)
        assertEquals(0f, EyeGeometry.aspectRatio(eye.corners, FloatArray(0), FloatArray(0)), 0f)
        assertEquals(0f, EyeGeometry.aspectRatio(eye.corners, eye.upper, FloatArray(4)), 0f)
        assertEquals(0f, EyeGeometry.aspectRatio(eye.corners, floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f, 3f)), 0f)
        assertEquals(0f, EyeGeometry.aspectRatio(eye.corners, eye.upper, eye.lower, xScale = 0f), 0f)
    }

    @Test
    fun `more lid points average out the jitter of one`() {
        val clean = Eye(0.1f, 0.03f)
        val jittered = Eye(0.1f, 0.03f)
        jittered.upper[1] -= 0.006f   // one lid point jumps by a fifth of the gap
        val cleanEar = clean.ear()
        val jitterEar = jittered.ear()
        assertTrue("moved by ${jitterEar - cleanEar}", kotlin.math.abs(jitterEar - cleanEar) < 0.025f)
    }

    @Test
    fun `openness maps onto 0 to 1 and never clips a real eye`() {
        assertEquals(0f, EyeGeometry.openness(0f), 0f)
        assertEquals(1f, EyeGeometry.openness(5f), 0f)
        assertEquals(0f, EyeGeometry.openness(-1f), 0f)
        assertEquals(0f, EyeGeometry.openness(Float.NaN), 0f)
        assertTrue("a very wide eye is still below the top of the scale", EyeGeometry.openness(0.40f) < 1f)
        assertTrue("the order of eyes is kept", EyeGeometry.openness(0.15f) < EyeGeometry.openness(0.30f))
    }

    @Test
    fun `face height is the distance from forehead to chin either way round`() {
        assertEquals(0.4f, EyeGeometry.faceHeight(0.15f, 0.55f), 0.0001f)
        assertEquals(0.4f, EyeGeometry.faceHeight(0.55f, 0.15f), 0.0001f)
    }
}

package com.eyeblink.core.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyelidGeometryTest {

    private val h = 2400f

    @Test
    fun `open lids draw nothing`() {
        for (curvature in listOf(0f, 0.35f, 1f)) {
            for (feather in listOf(0f, 0.5f, 1f)) {
                val layers = EyelidGeometry.topLayers(0f, h, curvature, feather)
                for (layer in layers) {
                    assertTrue(
                        "layer reaches ${layer.margin.edge}/${layer.margin.mid} on an open screen",
                        layer.margin.edge <= 0.001f && layer.margin.mid <= 0.001f,
                    )
                }
            }
        }
    }

    @Test
    fun `closed lids meet exactly on the centre line with no feather`() {
        val layers = EyelidGeometry.topLayers(1f, h, curvature = 1f, feather = 1f)
        assertEquals(1, layers.size)
        val m = layers.single().margin
        assertEquals(h / 2f, m.edge, 1e-3f)
        assertEquals(h / 2f, m.mid, 1e-3f)
        assertEquals(1f, layers.single().alpha, 0f)
    }

    @Test
    fun `the edge only ever moves toward the centre as closure grows`() {
        var prev = -1f
        for (i in 0..100) {
            val e = EyelidGeometry.margin(i / 100f, h, 0.5f).edge
            assertTrue(e >= prev)
            prev = e
        }
    }

    @Test
    fun `curvature bows the middle ahead of the corners`() {
        val m = EyelidGeometry.margin(0.5f, h, 0.6f)
        assertTrue("mid ${m.mid} should lead edge ${m.edge}", m.mid < m.edge)
    }

    @Test
    fun `zero curvature gives a straight margin`() {
        for (i in 0..10) {
            val m = EyelidGeometry.margin(i / 10f, h, 0f)
            assertEquals(m.edge, m.mid, 1e-3f)
        }
    }

    @Test
    fun `the opening is widest in the middle while the lids are mid way`() {
        val frac = EyelidGeometry.openingFraction(0.5f, h, 0.5f)
        val straight = EyelidGeometry.openingFraction(0.5f, h, 0f)
        assertTrue(frac > straight)
    }

    @Test
    fun `quadratic control passes through the requested midpoint`() {
        val m = LidMargin(edge = 300f, mid = 200f)
        // Quadratic bezier mid value = (e + 2c + e) / 4
        val mid = (m.edge + 2f * m.control + m.edge) / 4f
        assertEquals(200f, mid, 1e-3f)
    }

    @Test
    fun `feather layers are ordered faint first and end with a solid core`() {
        val layers = EyelidGeometry.topLayers(0.4f, h, 0.35f, 1f)
        assertTrue(layers.size > 2)
        assertEquals(1f, layers.last().alpha, 0f)
        for (l in layers.dropLast(1)) assertTrue(l.alpha < 1f)
        // each successive layer reaches less far into the opening
        for (i in 1 until layers.size) {
            assertTrue(layers[i].margin.edge <= layers[i - 1].margin.edge + 1e-3f)
        }
    }

    @Test
    fun `feathering never pushes the lid past the unfeathered margin`() {
        val closure = 0.45f
        val solid = EyelidGeometry.margin(closure, h, 0.35f)
        for (l in EyelidGeometry.topLayers(closure, h, 0.35f, 1f)) {
            assertTrue(l.margin.edge <= solid.edge + 1e-3f)
            assertTrue(l.margin.mid <= solid.mid + 1e-3f)
        }
    }

    @Test
    fun `zero feather is a single hard layer`() {
        assertEquals(1, EyelidGeometry.topLayers(0.4f, h, 0.35f, 0f).size)
    }
}

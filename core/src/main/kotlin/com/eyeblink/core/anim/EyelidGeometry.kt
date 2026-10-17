package com.eyeblink.core.anim

import kotlin.math.max

/**
 * One eyelid margin, measured as the distance from the screen edge the lid hangs from.
 *
 * The margin is a quadratic curve through `(0, edge)`, `(w/2, mid)` and `(w, edge)`. For a
 * lid that bows into an almond shape the middle of the margin leads the corners, so
 * `mid < edge`: the opening is widest in the centre and the lids meet there last.
 */
data class LidMargin(val edge: Float, val mid: Float) {
    /** Control-point distance that makes the quadratic pass through [mid] at x = w/2. */
    val control: Float get() = 2f * mid - edge
}

/** One translucent pass of a lid. Painted outermost first, so alphas stack into a soft edge. */
data class LidLayer(val margin: LidMargin, val alpha: Float)

/**
 * Pure eyelid geometry shared by the system overlay, the settings preview and the
 * hero animation. Platforms only turn these numbers into paths.
 *
 * Invariants (pinned by tests):
 *  - closure 0 puts every layer's margin at or above the screen edge, so nothing is drawn;
 *  - closure 1 puts the solid margin exactly on the centre line with no bulge and no
 *    feather, so the two lids meet with no light between them;
 *  - the margin only moves toward the centre as closure grows.
 */
object EyelidGeometry {

    /** Bulge peaks at this fraction of the viewport height for curvature 1. */
    private const val MAX_BULGE = 0.22f

    /** Feather depth at feather 1, as a fraction of viewport height. */
    private const val MAX_FEATHER = 0.045f

    const val FEATHER_STEPS = 7

    /** Where the solid margin of the *top* lid sits for a viewport of [height], 0 at its top edge. */
    fun margin(closure: Float, height: Float, curvature: Float): LidMargin {
        val c = closure.coerceIn(0f, 1f)
        val edge = c * height / 2f
        // 4c(1-c) peaks mid-closure and vanishes at both ends: open and fully closed are flat.
        val bulge = curvature.coerceIn(0f, 1f) * MAX_BULGE * height * (4f * c * (1f - c))
        return LidMargin(edge = edge, mid = edge - bulge)
    }

    /**
     * Feathered layers for the top lid, outermost (faintest, reaching furthest into the
     * opening) first and the solid core last. The bottom lid is the mirror image.
     *
     * The feather shrinks to nothing as the lids meet so the final seam is hard.
     */
    fun topLayers(closure: Float, height: Float, curvature: Float, feather: Float): List<LidLayer> {
        val c = closure.coerceIn(0f, 1f)
        val solid = margin(c, height, curvature)
        val depth = feather.coerceIn(0f, 1f) * MAX_FEATHER * height * (1f - c) * (1f - c)

        if (depth <= 0.5f) return listOf(LidLayer(solid, 1f))

        val layers = ArrayList<LidLayer>(FEATHER_STEPS + 1)
        // The core is pulled back by the full depth; faint layers fill the gap toward the
        // true margin, so the lid never reaches further than the unfeathered margin.
        for (i in 0 until FEATHER_STEPS) {
            val shift = depth * (FEATHER_STEPS - i) / FEATHER_STEPS
            val m = LidMargin(edge = solid.edge - depth + shift, mid = solid.mid - depth + shift)
            layers += LidLayer(m, FEATHER_ALPHA)
        }
        layers += LidLayer(LidMargin(solid.edge - depth, solid.mid - depth), 1f)
        return layers
    }

    /** Alpha of each faint feather pass. Seven stacked passes ramp coverage from 0 to ~0.9. */
    private const val FEATHER_ALPHA = 0.3f

    /**
     * Fraction of the viewport still open at the screen centre, for tests and for the
     * live "how closed is it" readouts.
     */
    fun openingFraction(closure: Float, height: Float, curvature: Float): Float {
        val m = margin(closure, height, curvature)
        val topMid = m.mid
        val bottomMid = height - m.mid
        return max(0f, (bottomMid - topMid) / height)
    }
}

package com.eyeblink.core.calibration

import kotlin.math.abs

/**
 * Converts eyelid landmark positions into a normalised 0..1 openness value.
 *
 * Deliberately arithmetic-only: no MediaPipe, CameraX or Android types, so the
 * geometry can be unit tested without a device or a model file. Turning
 * MediaPipe landmarks into the numbers this needs happens in
 * [MediaPipeEyeOpennessSource].
 *
 * The reference ratios below are product constants chosen to be
 * scale-invariant (they are relative to face height, not pixels). They are a
 * starting point for a typical adult face, NOT a clinically derived limit, and
 * calibration Phase C refines them per user.
 */
object EyeGeometry {

    /** Lid gap of a comfortably open eye, as a fraction of face height. */
    const val OPEN_APERTURE_RATIO = 0.10f

    /** Lid gap of a fully closed eye, as a fraction of face height. */
    const val CLOSED_APERTURE_RATIO = 0.015f

    /**
     * Vertical lid gap, averaged over several lid points for robustness,
     * normalised by face height so it is independent of how close the user is.
     *
     * @param upperLidY y coordinates of the upper-lid landmarks
     * @param lowerLidY y coordinates of the lower-lid landmarks
     */
    fun normalisedAperture(
        upperLidY: List<Float>,
        lowerLidY: List<Float>,
        faceHeight: Float,
    ): Float {
        if (upperLidY.isEmpty() || lowerLidY.isEmpty()) return 0f
        if (faceHeight <= 0f) return 0f

        // Average each lid's y to damp per-point jitter, then take the gap.
        val upper = upperLidY.average().toFloat()
        val lower = lowerLidY.average().toFloat()

        // In image space y grows downward, so the lower lid is the larger value.
        val gap = abs(lower - upper)
        return gap / faceHeight
    }

    /**
     * Maps a normalised aperture onto 0..1 openness using the reference
     * open/closed ratios. Below [CLOSED_APERTURE_RATIO] is fully shut.
     */
    fun openness(
        aperture: Float,
        openReference: Float = OPEN_APERTURE_RATIO,
        closedReference: Float = CLOSED_APERTURE_RATIO,
    ): Float {
        if (openReference <= closedReference) return 0f
        return ((aperture - closedReference) / (openReference - closedReference)).coerceIn(0f, 1f)
    }

    /** Face height from the forehead-top (10) and chin-bottom (152) landmarks. */
    fun faceHeight(foreheadY: Float, chinY: Float): Float = abs(chinY - foreheadY)
}
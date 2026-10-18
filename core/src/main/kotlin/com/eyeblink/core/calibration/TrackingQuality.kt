package com.eyeblink.core.calibration

import kotlin.math.abs

enum class QualityIssue { NO_FACE, TOO_DARK, FACE_TOO_SMALL, FACE_TOO_CLOSE, TURNED_AWAY, TILTED_VERTICALLY }

data class QualityReport(val score: Float, val issues: Set<QualityIssue>) {
    val usable: Boolean get() = score >= TrackingQuality.USABLE_SCORE
}

/**
 * How trustworthy a frame is as a source of blink data.
 *
 * MediaPipe's face landmarker does not report per-landmark visibility, so confidence has to
 * be derived from what *can* be measured: how big the face is in the frame, how frontal the
 * head is, and how bright the image is. Each factor is explainable on its own, and the score
 * is the weakest of them - one bad factor makes the frame unreliable.
 */
object TrackingQuality {

    const val USABLE_SCORE = 0.6f

    /** Face height as a fraction of frame height. */
    private const val SIZE_MIN = 0.18f
    private const val SIZE_GOOD_MIN = 0.30f
    private const val SIZE_GOOD_MAX = 0.80f
    private const val SIZE_MAX = 0.98f

    /** Yaw/pitch proxies, 0 = frontal. */
    private const val POSE_GOOD = 0.22f
    private const val POSE_BAD = 0.55f

    private const val LUMA_BAD = 25f
    private const val LUMA_GOOD = 70f

    fun score(faceDetected: Boolean, faceHeightFrac: Float, yaw: Float, pitch: Float, luma: Float): QualityReport {
        if (!faceDetected) return QualityReport(0f, setOf(QualityIssue.NO_FACE))

        val issues = mutableSetOf<QualityIssue>()

        val size = when {
            faceHeightFrac < SIZE_MIN -> 0f.also { issues += QualityIssue.FACE_TOO_SMALL }
            faceHeightFrac < SIZE_GOOD_MIN -> ramp(faceHeightFrac, SIZE_MIN, SIZE_GOOD_MIN).also { issues += QualityIssue.FACE_TOO_SMALL }
            faceHeightFrac <= SIZE_GOOD_MAX -> 1f
            faceHeightFrac < SIZE_MAX -> (1f - ramp(faceHeightFrac, SIZE_GOOD_MAX, SIZE_MAX)).also { issues += QualityIssue.FACE_TOO_CLOSE }
            else -> 0f.also { issues += QualityIssue.FACE_TOO_CLOSE }
        }

        val yawScore = 1f - ramp(abs(yaw), POSE_GOOD, POSE_BAD)
        val pitchScore = 1f - ramp(abs(pitch), POSE_GOOD, POSE_BAD)
        if (yawScore < 0.99f) issues += QualityIssue.TURNED_AWAY
        if (pitchScore < 0.99f) issues += QualityIssue.TILTED_VERTICALLY

        val light = ramp(luma, LUMA_BAD, LUMA_GOOD)
        if (light < 0.99f) issues += QualityIssue.TOO_DARK

        // The weakest factor sets the score; the issue list explains which one it was.
        val score = minOf(size, yawScore, pitchScore, light).coerceIn(0f, 1f)
        return QualityReport(score, issues)
    }

    /** 0 at [lo] and below, 1 at [hi] and above. */
    private fun ramp(x: Float, lo: Float, hi: Float): Float =
        if (hi <= lo) (if (x >= hi) 1f else 0f) else ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
}

/**
 * Cheap head-pose proxies from a few landmark distances. Frontal is 0; the sign says which
 * way the head is turned. They are not angles - they are monotone stand-ins that are good
 * enough to decide "is this head frontal enough to trust the eyelids".
 */
object HeadPose {

    /**
     * @param noseToLeftContour horizontal distance from the nose tip to the left face contour
     * @param noseToRightContour horizontal distance from the nose tip to the right face contour
     */
    fun yaw(noseToLeftContour: Float, noseToRightContour: Float): Float {
        val total = noseToLeftContour + noseToRightContour
        if (total <= 0f) return 1f
        return ((noseToLeftContour - noseToRightContour) / total).coerceIn(-1f, 1f)
    }

    /**
     * @param noseBelowEyeLine vertical distance from the eye line down to the nose tip
     * @param eyeLineToChin vertical distance from the eye line down to the chin
     * @param frontalRatio the ratio observed for this user when looking straight at the screen
     */
    fun pitch(noseBelowEyeLine: Float, eyeLineToChin: Float, frontalRatio: Float = FRONTAL_NOSE_RATIO): Float {
        if (eyeLineToChin <= 0f) return 1f
        return ((noseBelowEyeLine / eyeLineToChin) - frontalRatio).coerceIn(-1f, 1f)
    }

    /** The nose tip sits roughly 45% of the way from the eye line to the chin on a frontal face. */
    const val FRONTAL_NOSE_RATIO = 0.45f
}

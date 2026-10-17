package com.eyeblink.app.calibration

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/**
 * Turns camera frames into [EyeOpennessSample]s using MediaPipe face landmarks.
 *
 * Everything runs on device: frames are read, reduced to a handful of numbers,
 * and discarded. No image, landmark list or recording is ever written to disk
 * or sent anywhere. The landmark -> openness arithmetic lives in [EyeGeometry]
 * so it can be unit tested without a model file.
 *
 * Requires the front camera permission to be granted by the user before
 * [start]; this class never requests it itself.
 */
class MediaPipeEyeOpennessSource(
    private val context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
    private val minLandmarkVisibility: Float = MIN_LANDMARK_VISIBILITY,
) : AutoCloseable {

    private var landmarker: FaceLandmarker? = null

    /** True when the face model loaded and frames can be processed. */
    val isReady: Boolean get() = landmarker != null

    /**
     * Loads the face landmark model.
     * @return true on success. Failure is logged, not thrown: the caller should
     *         fall back to camera-free scheduling.
     */
    fun start(): Boolean {
        if (landmarker != null) return true
        return try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(modelAssetPath).build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .build()
            landmarker = FaceLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            // A missing/corrupt model asset must not crash the app; we simply
            // stay in camera-free mode.
            Log.w(TAG, "face landmarker unavailable: ${e.message}")
            landmarker = null
            false
        }
    }

    /**
     * Processes one frame. Call [reset] if the camera stream restarts so the
     * video-mode timestamps stay monotonic.
     *
     * @param timestampMs must increase monotonically across calls.
     * @return the sample, or null when the frame had no usable face.
     */
    fun onFrame(bitmap: Bitmap, timestampMs: Long, illuminance: Float): EyeOpennessSample? {
        val lm = landmarker ?: return null
        return try {
            val image = MPImage.Builder(bitmap)
                .build()
            val result = lm.detectForVideo(image, timestampMs)
            image.close()

            val landmarks = result.faceLandmarks().firstOrNull() ?: return null
            landmarksToSample(landmarks, timestampMs, illuminance)
        } catch (e: Exception) {
            Log.w(TAG, "frame failed: ${e.message}")
            null
        }
    }

    /** Releases the native model. Safe to call more than once. */
    fun reset() = Unit

    override fun close() {
        try {
            landmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed: ${e.message}")
        } finally {
            landmarker = null
        }
    }

    /**
     * Reduces a landmark list to one sample. Visible and package-visible so it
     * can be exercised with synthetic landmarks.
     */
    internal fun landmarksToSample(
        landmarks: List<NormalizedLandmark>,
        timestampMs: Long,
        illuminance: Float,
    ): EyeOpennessSample? {
        if (landmarks.size <= MAX_INDEX) return null

        val faceHeight = EyeGeometry.faceHeight(
            landmarks[FOREHEAD].y(),
            landmarks[CHIN].y(),
        )
        if (faceHeight <= 0f) return null

        fun ys(indices: IntArray): List<Float> =
            indices.mapNotNull { i ->
                val lm = landmarks[i]
                // Reject landmarks the model is not confident about: this is the
                // outlier rejection that EyeGeometry deliberately does not do.
                if (lm.visibility().orElse(1f) < minLandmarkVisibility) null else lm.y()
            }

        val leftUpper = ys(LEFT_UPPER)
        val leftLower = ys(LEFT_LOWER)
        val rightUpper = ys(RIGHT_UPPER)
        val rightLower = ys(RIGHT_LOWER)
        if (leftUpper.isEmpty() || leftLower.isEmpty() ||
            rightUpper.isEmpty() || rightLower.isEmpty()
        ) return null

        val left = EyeGeometry.openness(
            EyeGeometry.normalisedAperture(leftUpper, leftLower, faceHeight)
        )
        val right = EyeGeometry.openness(
            EyeGeometry.normalisedAperture(rightUpper, rightLower, faceHeight)
        )

        // Mean landmark visibility stands in for overall tracking confidence.
        val allVisibility = (LEFT_UPPER + LEFT_LOWER + RIGHT_UPPER + RIGHT_LOWER)
            .mapNotNull { landmarks[it].visibility().orElse(null) }
        val confidence = if (allVisibility.isEmpty()) 0f else allVisibility.average().toFloat()

        return EyeOpennessSample(
            timestampMs = timestampMs,
            leftEyeOpenness = left,
            rightEyeOpenness = right,
            confidence = confidence,
            illuminance = illuminance,
        )
    }

    companion object {
        private const val TAG = "EyeOpennessSource"

        /** Put `face_landmarker.task` in src/main/assets/ to use this. */
        const val DEFAULT_MODEL_ASSET = "face_landmarker.task"
        private const val MIN_LANDMARK_VISIBILITY = 0.5f

        // MediaPipe FaceMesh indices (stable in the 478-point model).
        private const val FOREHEAD = 10
        private const val CHIN = 152
        private const val MAX_INDEX = 386

        private val LEFT_UPPER = intArrayOf(159, 158)
        private val LEFT_LOWER = intArrayOf(145, 144)
        private val RIGHT_UPPER = intArrayOf(386, 385)
        private val RIGHT_LOWER = intArrayOf(374, 373)
    }
}
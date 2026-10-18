package com.eyeblink.app.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.eyeblink.app.engine.FaceSource
import com.eyeblink.core.calibration.EyeGeometry
import com.eyeblink.core.calibration.EyeOpennessSample
import com.eyeblink.core.calibration.FaceFrame
import com.eyeblink.core.calibration.HeadPose
import com.eyeblink.core.calibration.QualityIssue
import com.eyeblink.core.calibration.QualityReport
import com.eyeblink.core.calibration.TrackingQuality
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The front camera and an on-device face model, reduced to [FaceFrame]s.
 *
 * Each frame is read, turned into a few numbers (how open each eye is, how big and how frontal the
 * face is, how bright the image is) and thrown away. No picture is written anywhere, no landmark list
 * leaves this class, and the app does not hold the Internet permission. The camera exists only
 * while [start] has been called and [stop] has not; between bursts it is fully closed.
 */
class CameraFaceSource(private val app: Application) : FaceSource {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "eyeblink-camera").apply { isDaemon = true } }
    private val lock = Any()

    private var landmarker: FaceLandmarker? = null
    private var release: Runnable? = null
    private var owner: CameraOwner? = null
    private var provider: ProcessCameraProvider? = null
    private var lastTimestamp = 0L

    @Volatile private var active = false
    override val running: Boolean get() = active

    @Volatile override var lastError: String? = null
        private set

    private val modelPresent: Boolean by lazy {
        try {
            app.assets.open(MODEL).close()
            true
        } catch (e: Exception) {
            remember(e, "the face model file is missing from the app")
            false
        }
    }
    override val ready: Boolean get() = modelPresent

    override fun start(fps: Int, onFrame: (FaceFrame, Long) -> Unit, onError: (String) -> Unit): Boolean {
        if (active) return true
        if (!modelPresent) return false
        lastError = null
        active = true
        main.post { release?.let { main.removeCallbacks(it) }; release = null }
        executor.execute {
            try {
                if (ensureLandmarker() == null) { fail(onError, lastError ?: "the face model could not be loaded"); return@execute }
                val p = ProcessCameraProvider.getInstance(app).get()
                if (!hasFrontCamera(p)) { fail(onError, lastError); return@execute }
                main.post { bind(p, fps, onFrame, onError) }
            } catch (e: Throwable) {
                fail(onError, remember(e, "the camera could not be started"))
            }
        }
        return true
    }

    /**
     * Logs which cameras CameraX can open and says so when none of them faces the person: a phone without a front camera
     * (a tablet, an emulator, a camera an OEM keeps for itself) would otherwise only ever show a countdown and no pictures.
     */
    private fun hasFrontCamera(p: ProcessCameraProvider): Boolean {
        val front = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val back = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        val total = p.availableCameraInfos.size
        Log.i(TAG, "camera: CameraX can open $total camera(s); front: ${if (front) "yes" else "no"}, back: ${if (back) "yes" else "no"}")
        if (front) return true
        val message = "this phone has no front camera EyeBlink can open (CameraX can open $total camera(s), none of them facing the screen)"
        Log.w(TAG, message)
        lastError = message
        return false
    }

    /**
     * Keeps the reason a camera or model step failed, in the log and for the screen. This code runs on threads of its
     * own that nobody is waiting on, so a swallowed exception here used to mean a button that simply did nothing.
     */
    private fun remember(e: Throwable, what: String): String {
        val message = "$what (${describe(e)})"
        Log.e(TAG, message, e)
        lastError = message
        return message
    }

    private fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        val text = "${e.javaClass.name}: ${e.message}"
        return if (root === e) text else "$text, caused by ${root.javaClass.name}: ${root.message}"
    }

    override fun stop() {
        if (!active) return
        active = false
        main.post { unbind(); keepModelAwhile() }
    }

    override fun releaseModel() {
        main.post { if (!active) closeModel() }
    }

    /** Keeps the model for a short while: bursts come back every minute or so. Must run on the main thread. */
    private fun keepModelAwhile() {
        val r = Runnable { closeModel() }
        release = r
        main.postDelayed(r, MODEL_IDLE_MS)
    }

    private fun bind(p: ProcessCameraProvider, fps: Int, onFrame: (FaceFrame, Long) -> Unit, onError: (String) -> Unit) {
        if (!active) return
        try {
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(WIDTH, HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor, Analyzer(fps, onFrame, onError))
            val o = CameraOwner().also { it.resume() }
            p.unbindAll()
            val camera = p.bindToLifecycle(o, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            owner = o
            provider = p
            Log.i(TAG, "camera: the front camera is bound, sensor rotation ${camera.cameraInfo.sensorRotationDegrees} degrees")
            // CameraX reports a camera that is in use, disabled or broken through this state and not through an exception.
            camera.cameraInfo.cameraState.observe(o) { state -> onCameraState(state, onError) }
        } catch (e: Throwable) {
            fail(onError, remember(e, "the camera could not be started"))
        }
    }

    /** Must run on the main thread. */
    private fun unbind() {
        runCatching { provider?.unbindAll() }
        owner?.destroy()
        owner = null
    }

    private fun onCameraState(state: CameraState, onError: (String) -> Unit) {
        val error = state.error
        val problem = error?.let { stateProblem(it.code) + (it.cause?.let { c -> " (${c.javaClass.simpleName}: ${c.message})" } ?: "") }
        Log.i(TAG, "camera: ${state.type}" + (problem?.let { " - $it" } ?: ""))
        if (error == null || problem == null) {
            if (state.type == CameraState.Type.OPEN) lastError = null
            return
        }
        lastError = "the camera reports: $problem"
        // CameraX keeps retrying a camera that another app holds; the rest it gives up on.
        val retried = error.code == CameraState.ERROR_CAMERA_IN_USE || error.code == CameraState.ERROR_MAX_CAMERAS_IN_USE ||
            error.code == CameraState.ERROR_OTHER_RECOVERABLE_ERROR
        if (!retried && active) fail(onError, lastError)
    }

    private fun stateProblem(code: Int) = when (code) {
        CameraState.ERROR_MAX_CAMERAS_IN_USE -> "too many cameras are open"
        CameraState.ERROR_CAMERA_IN_USE -> "another app is using the camera"
        CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "a camera error it may recover from"
        CameraState.ERROR_STREAM_CONFIG -> "the camera could not be set up"
        CameraState.ERROR_CAMERA_DISABLED -> "the camera is disabled by a privacy switch, a policy or the phone's settings"
        CameraState.ERROR_CAMERA_FATAL_ERROR -> "a fatal camera error"
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Do Not Disturb is blocking the camera"
        else -> "camera error $code"
    }

    private fun fail(onError: (String) -> Unit, message: String?) {
        active = false
        main.post { unbind(); keepModelAwhile() }
        onError(message ?: "camera error")
    }

    private fun ensureLandmarker(): FaceLandmarker? = synchronized(lock) {
        landmarker?.let { return it }
        try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                // The model's own "eyes are shut" score: a second opinion that does not depend on the shape of the eye.
                .setOutputFaceBlendshapes(true)
                .build()
            FaceLandmarker.createFromOptions(app, options).also { landmarker = it }
        } catch (e: Throwable) {
            remember(e, "the face model could not be loaded")
            null
        }
    }

    private fun closeModel() {
        synchronized(lock) {
            runCatching { landmarker?.close() }
            landmarker = null
        }
    }

    private inner class Analyzer(
        fps: Int,
        private val onFrame: (FaceFrame, Long) -> Unit,
        private val onError: (String) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private val minGapMs = 1_000L / fps.coerceAtLeast(1)
        private var lastMs = 0L
        private val matrix = Matrix()
        private var firstSeen = false
        private var firstDone = false

        override fun analyze(image: ImageProxy) {
            try {
                val started = SystemClock.elapsedRealtime()
                if (!firstSeen) {
                    firstSeen = true
                    Log.i(TAG, "camera: the first picture, ${image.width}x${image.height}, turned ${image.imageInfo.rotationDegrees} degrees")
                }
                if (!active || started - lastMs < minGapMs) return
                lastMs = started

                val raw = image.toBitmap()
                matrix.reset()
                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false)
                if (upright !== raw) raw.recycle()

                lastTimestamp = maxOf(lastTimestamp + 1, started)
                val frame = detect(upright, lastTimestamp)
                upright.recycle()
                val tookMs = SystemClock.elapsedRealtime() - started
                if (!firstDone) {
                    firstDone = true
                    Log.i(TAG, "camera: the first picture was analysed in $tookMs ms, face seen: ${frame.sample != null}")
                }
                onFrame(frame, tookMs)
            } catch (e: Throwable) {
                onError(remember(e, "a camera frame could not be analysed"))
            } finally {
                image.close()
            }
        }
    }

    private fun detect(bitmap: Bitmap, ts: Long): FaceFrame {
        val frameLuma = meanLuma(bitmap, 0f, 0f, 1f, 1f)
        val none = FaceFrame(ts, null, QualityReport(0f, setOf(QualityIssue.NO_FACE)), frameLuma)
        val result = synchronized(lock) {
            val lm = landmarker ?: return none
            val image = BitmapImageBuilder(bitmap).build()
            try { lm.detectForVideo(image, ts) } finally { image.close() }
        }
        val p = result.faceLandmarks().firstOrNull() ?: return none
        if (p.size <= MAX_INDEX) return none

        val faceHeight = EyeGeometry.faceHeight(p[FOREHEAD].y(), p[CHIN].y())
        if (faceHeight <= 0f) return none

        // How bright the *face* is, not the room: in a dark room the screen lights the face, and a mean over the whole
        // picture would call that frame too dark to trust.
        val luma = meanLuma(
            bitmap,
            minOf(p[LEFT_CONTOUR].x(), p[RIGHT_CONTOUR].x()), minOf(p[FOREHEAD].y(), p[CHIN].y()),
            maxOf(p[LEFT_CONTOUR].x(), p[RIGHT_CONTOUR].x()), maxOf(p[FOREHEAD].y(), p[CHIN].y()),
        )

        val yaw = HeadPose.yaw(abs(p[NOSE].x() - p[LEFT_CONTOUR].x()), abs(p[RIGHT_CONTOUR].x() - p[NOSE].x()))
        val eyeLine = (p[LEFT_EYE_OUTER].y() + p[RIGHT_EYE_OUTER].y()) / 2f
        val pitch = HeadPose.pitch(p[NOSE].y() - eyeLine, p[CHIN].y() - eyeLine)
        val quality = TrackingQuality.score(true, faceHeight, yaw, pitch, luma)

        // Landmarks are normalised to the picture, so a step in x is not the same length as a step in y unless told so.
        val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val left = eyeOpenness(p, RIGHT_EYE_CORNERS, RIGHT_EYE_UPPER, RIGHT_EYE_LOWER, aspect)
        val right = eyeOpenness(p, LEFT_EYE_CORNERS, LEFT_EYE_UPPER, LEFT_EYE_LOWER, aspect)
        val sample = EyeOpennessSample(ts, left, right, confidence = quality.score, illuminance = luma, blinkScore = blinkScore(result))
        return FaceFrame(ts, sample, quality, luma)
    }

    // One set of buffers: detect() only ever runs on the camera thread.
    private val cornerPoints = FloatArray(4)
    private val upperPoints = FloatArray(6)
    private val lowerPoints = FloatArray(6)

    /** The eye aspect ratio (mean lid gap over eye width) of one eye, on the 0..1 scale the engine works with. */
    private fun eyeOpenness(p: List<NormalizedLandmark>, corners: IntArray, upper: IntArray, lower: IntArray, aspect: Float): Float {
        cornerPoints[0] = p[corners[0]].x(); cornerPoints[1] = p[corners[0]].y()
        cornerPoints[2] = p[corners[1]].x(); cornerPoints[3] = p[corners[1]].y()
        for (i in upper.indices) {
            upperPoints[2 * i] = p[upper[i]].x(); upperPoints[2 * i + 1] = p[upper[i]].y()
            lowerPoints[2 * i] = p[lower[i]].x(); lowerPoints[2 * i + 1] = p[lower[i]].y()
        }
        return EyeGeometry.openness(EyeGeometry.aspectRatio(cornerPoints, upperPoints, lowerPoints, aspect))
    }

    /** The mean of the model's blink scores for the two eyes (0 = open, 1 = shut), or null if it gave none. */
    private fun blinkScore(result: FaceLandmarkerResult): Float? {
        val categories = result.faceBlendshapes().orElse(null)?.firstOrNull() ?: return null
        var left = -1f
        var right = -1f
        for (c in categories) {
            when (c.categoryName()) {
                "eyeBlinkLeft" -> left = c.score()
                "eyeBlinkRight" -> right = c.score()
            }
        }
        return if (left >= 0f && right >= 0f) (left + right) / 2f else null
    }

    /** Mean brightness (0..255) of the part of the picture between the given normalised corners; the whole of it if that is too small to say anything. */
    private fun meanLuma(b: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Float {
        var x0 = (left.coerceIn(0f, 1f) * b.width).toInt()
        var y0 = (top.coerceIn(0f, 1f) * b.height).toInt()
        var x1 = (right.coerceIn(0f, 1f) * b.width).toInt().coerceAtMost(b.width)
        var y1 = (bottom.coerceIn(0f, 1f) * b.height).toInt().coerceAtMost(b.height)
        if (x1 - x0 < MIN_LUMA_REGION || y1 - y0 < MIN_LUMA_REGION) { x0 = 0; y0 = 0; x1 = b.width; y1 = b.height }
        val stepX = ((x1 - x0) / 16).coerceAtLeast(1)
        val stepY = ((y1 - y0) / 16).coerceAtLeast(1)
        var sum = 0L
        var n = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val c = b.getPixel(x, y)
                sum += (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    /** CameraX needs a lifecycle to bind to; the camera lives exactly as long as this one is resumed. */
    private class CameraOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        init { registry.currentState = Lifecycle.State.CREATED }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        const val TAG = "EyeBlink"
        const val MODEL = "face_landmarker.task"
        const val WIDTH = 640
        const val HEIGHT = 480
        const val MODEL_IDLE_MS = 120_000L

        /** A face region narrower than this many pixels is no better a sample of the face than the whole picture. */
        const val MIN_LUMA_REGION = 16

        // Indices into MediaPipe's 478-point face mesh.
        const val FOREHEAD = 10
        const val CHIN = 152
        const val NOSE = 1
        const val LEFT_CONTOUR = 234
        const val RIGHT_CONTOUR = 454
        const val LEFT_EYE_OUTER = 33
        const val RIGHT_EYE_OUTER = 263
        const val MAX_INDEX = 454

        // Each eye: its two corners, and three upper-lid points with the lower-lid point facing each. (MediaPipe calls the
        // eye on the left of the picture the "right" one; which is which does not matter here, both are measured alike.)
        val RIGHT_EYE_CORNERS = intArrayOf(33, 133)
        val RIGHT_EYE_UPPER = intArrayOf(160, 159, 158)
        val RIGHT_EYE_LOWER = intArrayOf(144, 145, 153)
        val LEFT_EYE_CORNERS = intArrayOf(263, 362)
        val LEFT_EYE_UPPER = intArrayOf(387, 386, 385)
        val LEFT_EYE_LOWER = intArrayOf(373, 374, 380)
    }
}

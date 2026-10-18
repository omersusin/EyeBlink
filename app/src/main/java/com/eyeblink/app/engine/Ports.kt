package com.eyeblink.app.engine

import com.eyeblink.app.overlay.OverlayWindows
import com.eyeblink.app.overlay.RestRenderer
import com.eyeblink.app.power.ShizukuPort
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.core.calibration.FaceFrame

/**
 * A front camera plus a face model, reduced to "frames of eye openness". Implemented with CameraX
 * and MediaPipe in the `camera` package; everything above it sees only [FaceFrame]s, never pixels.
 */
interface FaceSource {
    /** The face model loaded, so frames can be analysed. */
    val ready: Boolean

    val running: Boolean

    /** Why the camera or the face model last failed, as a sentence a person can read; null while all is well. */
    val lastError: String?

    /**
     * Opens the front camera. [onFrame] is called on a background thread for each analysed frame
     * with how long the analysis took in milliseconds. Returns false if the camera could not start.
     */
    fun start(fps: Int, onFrame: (FaceFrame, Long) -> Unit, onError: (String) -> Unit): Boolean

    /** Closes the camera. Idempotent. */
    fun stop()

    /** The system is short of memory: let go of the face model if the camera is not using it. Idempotent. */
    fun releaseModel() = Unit
}

/** The pieces that live behind libraries only the real app has (Compose windows, CameraX, Shizuku). */
class EnginePorts(
    /** Builds the rest-scene renderer once the engine can supply its windows, language and motion setting. */
    val rest: (windows: OverlayWindows, strings: () -> Strings, reducedMotion: () -> Boolean) -> RestRenderer,
    val faces: FaceSource?,
    val shizuku: ShizukuPort?,
)

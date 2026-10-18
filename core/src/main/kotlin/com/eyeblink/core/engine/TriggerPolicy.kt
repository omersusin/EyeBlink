package com.eyeblink.core.engine

import com.eyeblink.core.calibration.EyeOpennessSample

/**
 * Chooses between camera-assisted and camera-free triggering.
 *
 * The single most important rule here is honesty: EyeBlink must never behave
 * as though it detected a real blink while the camera is off, unavailable or
 * unreliable. When in doubt it falls back to the timer rather than implying a
 * capability it does not have.
 *
 * Pure logic, no Android types, so it is unit tested directly.
 */
enum class TriggerMode {
    /** Real blink detection from the front camera is trustworthy. */
    CAMERA_ASSISTED,

    /** Timer only. No blink claims are made. */
    CAMERA_FREE,
}

object TriggerPolicy {

    /** Tracking confidence below this is treated as unreliable. */
    const val MIN_USABLE_CONFIDENCE = 0.6f

    /**
     * @param cameraEnabled user has turned camera tracking on
     * @param cameraReady model loaded and frames are arriving
     * @param screenOn false when the display is off; no camera work then
     * @param confidence most recent sample confidence, or null if none yet
     */
    fun select(
        cameraEnabled: Boolean,
        cameraReady: Boolean,
        screenOn: Boolean = true,
        confidence: Float? = null,
    ): TriggerMode {
        if (!cameraEnabled) return TriggerMode.CAMERA_FREE
        if (!cameraReady) return TriggerMode.CAMERA_FREE
        if (!screenOn) return TriggerMode.CAMERA_FREE
        if (confidence == null) return TriggerMode.CAMERA_FREE
        if (confidence < MIN_USABLE_CONFIDENCE) return TriggerMode.CAMERA_FREE
        return TriggerMode.CAMERA_ASSISTED
    }

    /** True when we may describe our behaviour as real blink detection. */
    fun claimsRealBlinkDetection(mode: TriggerMode): Boolean = mode == TriggerMode.CAMERA_ASSISTED

    /**
     * A sample counts toward learning only while the camera is genuinely
     * driving the mode. Samples captured while disabled, or too dark, must not
     * become training data.
     */
    fun isLearnable(mode: TriggerMode, sample: EyeOpennessSample): Boolean =
        claimsRealBlinkDetection(mode) && sample.isTrackable
}

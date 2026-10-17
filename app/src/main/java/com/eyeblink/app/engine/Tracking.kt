package com.eyeblink.app.engine

import android.app.Application
import android.util.Log
import com.eyeblink.app.platform.Access
import com.eyeblink.app.platform.DeviceState
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.ui.state.CalibrationStage
import com.eyeblink.app.ui.state.CalibrationUi
import com.eyeblink.core.calibration.AdaptiveLearner
import com.eyeblink.core.calibration.BlinkObservation
import com.eyeblink.core.calibration.BlinkThresholds
import com.eyeblink.core.calibration.CalibrationResult
import com.eyeblink.core.calibration.CalibrationSession
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.DutyCycle
import com.eyeblink.core.calibration.EyeOpennessSample
import com.eyeblink.core.calibration.FaceFrame
import com.eyeblink.core.calibration.LearnVerdict
import com.eyeblink.core.calibration.ObservedRate
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.calibration.QualityReport
import com.eyeblink.core.calibration.TrackingConstraints
import com.eyeblink.core.calibration.TrackingPipeline
import com.eyeblink.core.engine.Thermal
import com.eyeblink.core.engine.TriggerMode
import com.eyeblink.core.engine.TriggerPolicy
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.TrackingMode
import com.eyeblink.core.perf.LatencyTracker
import com.eyeblink.core.perf.PerfPolicy
import com.eyeblink.core.perf.PerfReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Runs the camera as little as possible and learns from what it sees.
 *
 * The decision of whether the camera should be on right now belongs to [DutyCycle] and
 * [PerfPolicy]; this class only carries it out, feeds frames to a [TrackingPipeline], and hands the
 * results on. Everything here runs on the engine thread; the camera thread only posts frames to it.
 */
class TrackingController(
    private val app: Application,
    private val faces: FaceSource?,
    private val device: DeviceState,
    private val settings: () -> EyeBlinkSettings,
    private val profile: () -> PersonalProfile,
    private val setProfile: (PersonalProfile) -> Unit,
    private val context: () -> ContextProfile,
    private val post: (() -> Unit) -> Unit,
    private val onBlink: (BlinkObservation) -> Unit,
    private val onUsage: (trackedMs: Long, cameraMs: Long, blinks: Int) -> Unit,
) {
    private val pipeline = TrackingPipeline(profile().thresholds)
    private val duty = DutyCycle { settings().camera }
    private val latency = LatencyTracker()

    @Volatile var cameraActive = false
        private set
    @Volatile var lastQuality: QualityReport? = null
        private set
    var lastFrameMs = 0L
        private set

    private var darkFrames = 0
    private var darkUntilMs = 0L
    private var failedUntilMs = 0L
    private var pendingCameraMs = 0L
    private var pendingTrackedMs = 0L
    private var pendingBlinks = 0
    private var lastUsageFlushMs = 0L

    val frameP50: Long get() = latency.p50
    val frameP95: Long get() = latency.p95

    fun applyThresholds(t: BlinkThresholds) = pipeline.setThresholds(t)

    /** Evaluated on the engine tick. [eligible]: the user is in a selected app with the screen on. */
    fun tick(now: Long, eligible: Boolean) {
        val source = faces ?: return
        val s = settings()
        if (s.camera.tracking == TrackingMode.OFF) {
            if (source.running) stopCamera(now)
            return
        }
        val bat = device.battery()
        val thermal = if (s.battery.thermalBackoff) device.thermal() else Thermal.NONE
        val advice = PerfPolicy.advise(
            p95Ms = latency.p95, frameBudgetMs = 1_000L / s.camera.targetFps.coerceAtLeast(1), thermal = thermal,
            batteryPercent = bat.percent, charging = bat.charging, saverBelowPercent = s.battery.saverBelowPercent,
        )
        val lowBattery = advice.reason == PerfReason.LOW_BATTERY && s.battery.cameraOffOnSaver
        val plan = duty.plan(
            now,
            TrackingConstraints(
                eligible = eligible,
                permissionGranted = Access.camera(app) && source.ready && now >= failedUntilMs,
                batterySaver = lowBattery || (s.battery.cameraOffOnSaver && device.powerSave()),
                thermal = thermal,
                tooDark = now < darkUntilMs,
                cameraBusyElsewhere = device.cameraBusyElsewhere(ownOpen = source.running),
            ),
        )
        val want = plan.runCamera && !(advice.cameraOff && advice.reason != PerfReason.LOW_BATTERY)
        if (want && !source.running) startCamera(now, (plan.fps * advice.fpsFactor).roundToInt().coerceAtLeast(DutyCycle.MIN_FPS))
        else if (!want && source.running) stopCamera(now)
        cameraActive = source.running
    }

    private fun startCamera(now: Long, fps: Int) {
        val source = faces ?: return
        lastUsageFlushMs = now
        val ok = source.start(fps, { frame, ms -> post { onFrame(frame, ms) } }, { msg -> post { onCameraError(msg) } })
        if (!ok) failedUntilMs = now + RETRY_AFTER_FAILURE_MS
        cameraActive = source.running
    }

    fun stopCamera(now: Long = nowMs()) {
        val source = faces ?: return
        if (source.running) source.stop()
        cameraActive = false
        flushLearning(force = true)
        flushUsage(now)
        pipeline.rate.onTrackingLost()
    }

    private fun onCameraError(@Suppress("UNUSED_PARAMETER") message: String) {
        // Whatever went wrong, back off for a minute instead of retrying in a loop.
        failedUntilMs = nowMs() + RETRY_AFTER_FAILURE_MS
        stopCamera()
    }

    private fun onFrame(frame: FaceFrame, analysisMs: Long) {
        if (faces?.running != true) return
        latency.add(analysisMs)
        lastQuality = frame.quality
        val now = nowMs()

        if (frame.luma < EyeOpennessSample.MIN_ILLUMINANCE) {
            if (++darkFrames >= DARK_FRAMES_BEFORE_PAUSE) darkUntilMs = now + DARK_PAUSE_MS
        } else {
            darkFrames = 0
        }

        val prev = lastFrameMs
        lastFrameMs = frame.timestampMs
        val dt = if (prev > 0) (frame.timestampMs - prev).coerceIn(0L, 1_000L) else 0L
        pendingCameraMs += dt

        val events = pipeline.onFrame(frame)
        if (events.trackable) pendingTrackedMs += dt
        events.blink?.let { b ->
            pendingBlinks++
            onBlink(b)
        }

        flushLearning(force = false)
        if (now - lastUsageFlushMs >= USAGE_FLUSH_MS) flushUsage(now)
    }

    private fun flushLearning(force: Boolean) {
        val window = pipeline.takeLearningWindow(context(), force) ?: return
        val outcome = AdaptiveLearner.learn(profile(), window)
        if (outcome.verdict == LearnVerdict.ACCEPTED) setProfile(outcome.profile)
    }

    private fun flushUsage(now: Long) {
        lastUsageFlushMs = now
        if (pendingCameraMs > 0 || pendingBlinks > 0) onUsage(pendingTrackedMs, pendingCameraMs, pendingBlinks)
        pendingCameraMs = 0; pendingTrackedMs = 0; pendingBlinks = 0
    }

    fun observed(now: Long): ObservedRate? =
        pipeline.rate.observed(now).takeIf { it.trackedMs > 0 }

    /** Camera-assisted only while real, recent, trustworthy observations exist. */
    fun triggerMode(now: Long, screenOn: Boolean): TriggerMode {
        val s = settings().camera
        val maxAge = if (s.tracking == TrackingMode.BURST) (s.periodSec + s.burstSec) * 1_000L else TrackingPipeline.RECENT_MS
        return TriggerPolicy.select(
            cameraEnabled = s.tracking != TrackingMode.OFF,
            cameraReady = faces?.ready == true && Access.camera(app),
            screenOn = screenOn,
            confidence = pipeline.confidenceAt(now, maxAge),
        )
    }

    /** A real blink can confirm a rest only while the camera is looking right now. */
    fun canConfirmBlink(now: Long): Boolean = cameraActive && pipeline.confidenceAt(now) != null

    fun reset() {
        stopCamera()
        pipeline.reset()
    }

    private companion object {
        const val DARK_FRAMES_BEFORE_PAUSE = 30
        const val DARK_PAUSE_MS = 90_000L
        const val RETRY_AFTER_FAILURE_MS = 60_000L
        const val USAGE_FLUSH_MS = 10_000L
    }
}

/**
 * The guided calibration, driven by the same camera frames. While it runs it owns the camera:
 * the background tracking steps aside, and the camera is closed again the moment the last phase ends.
 */
class Calibrator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val faces: FaceSource?,
    private val settings: () -> EyeBlinkSettings,
    private val post: (() -> Unit) -> Unit,
    private val onSave: (PersonalProfile) -> Unit,
    private val onChange: () -> Unit,
) {
    @Volatile var ui: CalibrationUi = CalibrationUi()
        private set

    private var session: CalibrationSession? = null
    private var detector = BlinkThresholds().newDetector()
    private var result: CalibrationResult? = null
    private var tick: Job? = null
    private var blinks = 0
    private var startedAtMs = 0L
    private var framesSeen = 0

    /** Background tracking must not touch the camera while this is true. */
    val running: Boolean get() = session != null

    fun start() {
        val source = faces
        val permitted = Access.camera(app)
        if (source == null || !source.ready || !permitted) {
            // Without the permission the activity has asked already. Without camera support or the face model there is
            // nothing left to ask for, so say what is wrong instead of showing the same page again.
            val why = when {
                source == null -> "this build has no camera support"
                !source.ready -> source.lastError ?: "the face model file is missing from the app"
                else -> null
            }
            ui = CalibrationUi(hasCameraPermission = permitted, modelReady = source?.ready == true, error = why)
            onChange()
            return
        }
        source.stop()
        val c = settings().calibration.validated()
        val s = CalibrationSession(c.videoSec * 1_000L, c.readingSec * 1_000L, c.deliberateBlinks)
        s.start(nowMs())
        session = s
        result = null
        blinks = 0
        framesSeen = 0
        startedAtMs = nowMs()
        detector = BlinkThresholds().newDetector()
        val started = source.start(CALIBRATION_FPS, { frame, _ -> post { onFrame(frame) } }, { message -> post { abort(message) } })
        if (!started) {
            session = null
            ui = CalibrationUi(hasCameraPermission = true, modelReady = true, error = source.lastError ?: "the camera could not be started")
            onChange()
            return
        }
        ui = CalibrationUi(
            stage = CalibrationStage.RUNNING, phase = s.phase, deliberateTotal = c.deliberateBlinks, deliberateLeft = c.deliberateBlinks,
            hasCameraPermission = true, modelReady = true,
        )
        tick?.cancel()
        tick = scope.launch { while (isActive) { step(); delay(250) } }
        onChange()
    }

    /** The person said no to the camera. The intro page says so, because a Begin button that does nothing looks broken. */
    fun cameraDenied() {
        if (session != null) return
        ui = CalibrationUi(hasCameraPermission = false, modelReady = faces?.ready == true, cameraDenied = true)
        onChange()
    }

    private fun onFrame(frame: FaceFrame) {
        val s = session ?: return
        if (++framesSeen == 1) Log.i(TAG, "calibration: the first camera picture came ${nowMs() - startedAtMs} ms after Begin, face seen: ${frame.sample != null}")
        val sample = frame.sample
        if (sample != null) {
            s.onSample(sample)
            detector.onSample(sample)?.let { if (s.onBlink(it)) blinks++ }
        }
        ui = ui.copy(liveOpenness = sample?.openness, quality = frame.quality, blinksSeen = blinks)
    }

    private fun step() {
        val s = session ?: return
        val now = nowMs()
        // A camera that another app holds opens "successfully" and then never delivers a picture; say so instead of waiting.
        if (framesSeen == 0 && now - startedAtMs > NO_FRAMES_MS) {
            val said = faces?.lastError?.let { " ($it)" }.orEmpty()
            abort("no pictures arrived from the camera in ${NO_FRAMES_MS / 1_000} seconds$said")
            return
        }
        s.onTick(now)
        if (s.phase == CalibrationSession.Phase.COMPLETE) {
            finish(s)
            return
        }
        ui = ui.copy(
            phase = s.phase, phaseProgress = s.phaseProgress(now),
            remainingSec = s.remainingMs(now)?.let { ceil(it / 1000f).toInt() } ?: 0,
            deliberateLeft = s.deliberateRemaining, blinksSeen = blinks,
        )
        onChange()
    }

    private fun finish(s: CalibrationSession) {
        tick?.cancel()
        faces?.stop()
        result = s.result()
        session = null
        ui = ui.copy(stage = CalibrationStage.RESULT, result = result, liveOpenness = null)
        onChange()
    }

    fun skipPhase() {
        session?.skipPhase(nowMs())
    }

    fun cancel() {
        tick?.cancel()
        faces?.stop()
        session?.cancel()
        session = null
        result = null
        ui = CalibrationUi(hasCameraPermission = Access.camera(app), modelReady = faces?.ready == true)
        onChange()
    }

    /** The camera or the model failed under a running session: stop everything and tell the user why. */
    private fun abort(message: String) {
        if (session == null) return
        Log.w(TAG, "calibration stopped: $message")
        tick?.cancel()
        faces?.stop()
        session?.cancel()
        session = null
        result = null
        ui = CalibrationUi(hasCameraPermission = Access.camera(app), modelReady = faces?.ready == true, error = message)
        onChange()
    }

    /** Keeps the result - but only if it held enough natural blinks to say anything. */
    fun save() {
        val r = result ?: return
        if (r.hasEnoughSpontaneous) onSave(r.profile.copy(calibratedAtMs = System.currentTimeMillis()))
        result = null
        ui = CalibrationUi(hasCameraPermission = Access.camera(app), modelReady = faces?.ready == true)
        onChange()
    }

    fun reset() {
        onSave(PersonalProfile())
    }

    private companion object {
        const val TAG = "EyeBlink"
        const val CALIBRATION_FPS = 15
        const val NO_FRAMES_MS = 20_000L
    }
}

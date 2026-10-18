package com.eyeblink.app.engine

import android.app.Application
import android.util.Log
import com.eyeblink.app.platform.Access
import com.eyeblink.app.platform.DeviceState
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.ui.state.CalibrationStage
import com.eyeblink.app.ui.state.CalibrationUi
import com.eyeblink.app.ui.state.EyeTestUi
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
    private val pipeline = TrackingPipeline(profile().thresholds, settings().camera.sensitivity.factor)
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

    /** Fresh thresholds, or the same ones with a new sensitivity: what "open" looks like is learned again within a second. */
    fun applyThresholds(t: BlinkThresholds) = pipeline.setThresholds(t, settings().camera.sensitivity.factor)

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
        // The next burst's first frame is not a second of camera time: nothing was looking for the whole time in between.
        lastFrameMs = 0L
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
    private var result: CalibrationResult? = null
    private var tick: Job? = null
    private var blinks = 0
    private var framesSeen = 0
    private var lastFrameAtMs = 0L

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
        val s = CalibrationSession(c.videoSec * 1_000L, c.readingSec * 1_000L, c.deliberateBlinks, settings().camera.sensitivity.factor)
        s.start(nowMs())
        session = s
        result = null
        blinks = 0
        framesSeen = 0
        lastFrameAtMs = nowMs()
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
        lastFrameAtMs = nowMs()
        if (++framesSeen == 1) Log.i(TAG, "calibration: the first camera picture arrived, face seen: ${frame.sample != null}")
        val sample = frame.sample
        // The session decides what counts as a blink in each phase (see CalibrationSession.process).
        if (sample == null) s.onNoFace() else if (s.process(sample) != null) blinks++
        ui = ui.copy(
            // How open the eyes are *for this person*: 1 is their own open eye, whatever it looks like.
            liveOpenness = if (sample != null && sample.isTrackable) 1f - s.closure else null,
            quality = frame.quality, blinksSeen = blinks, hint = s.hint, countsAt = s.startDepth,
        )
    }

    private fun step() {
        val s = session ?: return
        val now = nowMs()
        // A camera that another app holds, or that was switched off under the session, opens "successfully" and then never
        // delivers a picture; say so instead of counting down.
        if (now - lastFrameAtMs > NO_FRAMES_MS) {
            val said = faces?.lastError?.let { " ($it)" }.orEmpty()
            abort((if (framesSeen == 0) "no picture arrived from the camera in" else "the camera stopped delivering pictures for") + " ${NO_FRAMES_MS / 1_000} seconds$said")
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
        /** A blink is over in a few frames; at 15 fps a quick one is two. Calibration is short, so it can afford to look closely. */
        const val CALIBRATION_FPS = 30
        const val NO_FRAMES_MS = 20_000L
    }
}

/**
 * "Try your blinks": the same pipeline that watches during the day, with the thresholds that are in force, shown live.
 * It keeps nothing - no picture, no number - and exists so that a person can see that the engine sees their own eyes
 * and tune the sensitivity by watching the counter. While it runs it owns the camera, like the calibration does.
 */
class EyeTester(
    private val app: Application,
    private val scope: CoroutineScope,
    private val faces: FaceSource?,
    private val settings: () -> EyeBlinkSettings,
    private val profile: () -> PersonalProfile,
    private val post: (() -> Unit) -> Unit,
    private val onChange: () -> Unit,
) {
    @Volatile var ui: EyeTestUi = EyeTestUi()
        private set

    private var pipeline: TrackingPipeline? = null
    private var tick: Job? = null
    private var framesSeen = 0
    private var lastFrameAtMs = 0L
    private var blinks = 0

    /** Background tracking must not touch the camera while this is true. */
    val running: Boolean get() = pipeline != null

    fun start() {
        val source = faces
        val permitted = Access.camera(app)
        if (source == null || !source.ready || !permitted) {
            val why = when {
                source == null -> "this build has no camera support"
                !source.ready -> source.lastError ?: "the face model file is missing from the app"
                else -> null
            }
            ui = EyeTestUi(hasCameraPermission = permitted, error = why)
            onChange()
            return
        }
        source.stop()
        pipeline = newPipeline()
        blinks = 0
        framesSeen = 0
        lastFrameAtMs = nowMs()
        val started = source.start(TEST_FPS, { frame, _ -> post { onFrame(frame) } }, { message -> post { abort(message) } })
        if (!started) {
            pipeline = null
            ui = EyeTestUi(hasCameraPermission = true, error = source.lastError ?: "the camera could not be started")
            onChange()
            return
        }
        ui = EyeTestUi(running = true, hasCameraPermission = true, fitted = profile().thresholds.fitted)
        tick?.cancel()
        tick = scope.launch {
            while (isActive) {
                delay(250)
                if (nowMs() - lastFrameAtMs > NO_FRAMES_MS) { post { abort("no pictures arrived from the camera for ${NO_FRAMES_MS / 1_000} seconds") }; break }
                onChange()
            }
        }
        onChange()
    }

    fun stop() {
        if (pipeline == null) {
            // Nothing was running; at most there is an old message on the page to clear.
            if (ui.error != null || ui.cameraDenied) { ui = EyeTestUi(hasCameraPermission = Access.camera(app)); onChange() }
            return
        }
        tick?.cancel()
        faces?.stop()
        pipeline = null
        ui = EyeTestUi(hasCameraPermission = Access.camera(app))
        onChange()
    }

    fun cameraDenied() {
        if (pipeline != null) return
        ui = EyeTestUi(hasCameraPermission = false, cameraDenied = true)
        onChange()
    }

    /** The thresholds or the sensitivity changed while the test runs: judge the next frames by the new ones. */
    fun refresh() {
        if (pipeline == null) return
        pipeline = newPipeline()
        ui = ui.copy(fitted = profile().thresholds.fitted)
    }

    private fun newPipeline() = TrackingPipeline(profile().thresholds, settings().camera.sensitivity.factor)

    private fun onFrame(frame: FaceFrame) {
        val p = pipeline ?: return
        lastFrameAtMs = nowMs()
        if (++framesSeen == 1) Log.i(TAG, "eye test: the first camera picture arrived, face seen: ${frame.sample != null}")
        val blink = p.onFrame(frame).blink
        if (blink != null) blinks++
        val sample = frame.sample
        ui = ui.copy(
            openness = if (sample != null && sample.isTrackable) 1f - p.closure else null,
            countsAt = p.startDepth,
            blinks = blinks,
            lastDepth = blink?.depth ?: ui.lastDepth,
            lastDurationMs = blink?.durationMs ?: ui.lastDurationMs,
            lastComplete = blink?.complete ?: ui.lastComplete,
            quality = frame.quality,
        )
    }

    private fun abort(message: String) {
        if (pipeline == null) return
        Log.w(TAG, "eye test stopped: $message")
        tick?.cancel()
        faces?.stop()
        pipeline = null
        ui = EyeTestUi(hasCameraPermission = Access.camera(app), error = message)
        onChange()
    }

    private companion object {
        const val TAG = "EyeBlink"
        const val TEST_FPS = 30
        const val NO_FRAMES_MS = 20_000L
    }
}

package com.eyeblink.app.ui.state

import com.eyeblink.core.calibration.CalibrationResult
import com.eyeblink.core.calibration.CalibrationSession
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.calibration.QualityReport
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.Factor
import com.eyeblink.core.engine.Reason
import com.eyeblink.core.engine.TriggerMode
import com.eyeblink.core.model.AppCategory
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.night.NightTarget
import com.eyeblink.core.perf.DrainComparison
import com.eyeblink.core.playback.CompatStatus
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.playback.StrategyTestResult
import com.eyeblink.core.privileged.PowerStatus
import com.eyeblink.core.privileged.SetupStep
import com.eyeblink.core.privileged.SetupStepId
import com.eyeblink.core.stats.DayStats

/** The five destinations of the dock. */
enum class Tab { TODAY, BLINK, RHYTHM, APPS, MORE }

/** Pages reached from More. */
enum class Page { CALIBRATION, EYE_TEST, NIGHT, POWER, DIAGNOSTICS, STATS, PRIVACY, ABOUT }

enum class PermissionKind {
    OVERLAY, ACCESSIBILITY, NOTIFICATION_LISTENER, USAGE_ACCESS, CAMERA, NOTIFICATIONS, WRITE_SETTINGS, BATTERY_OPTIMIZATION,
}

/** Live facts about the engine, refreshed about twice a second while the app is visible. */
data class RuntimeUi(
    val running: Boolean = false,
    val snoozedUntilMs: Long = 0L,
    val microInMs: Long? = null,
    val restInMs: Long? = null,
    /** The interval currently in force, so the ring can show how much of it is left. */
    val intervalMs: Long = 45_000L,
    /** Why the timers are not running (screen off, not a selected app...). */
    val idle: Reason? = null,
    /** A break is due but waiting for a better moment. */
    val deferred: Reason? = null,
    val triggerMode: TriggerMode = TriggerMode.CAMERA_FREE,
    val foregroundLabel: String? = null,
    val context: ContextProfile? = null,
    val factors: List<Factor> = emptyList(),
    /** Bumped on every real blink so the hero eye can echo it. */
    val echo: Int = 0,
    val cameraActive: Boolean = false,
    val lastBreak: BreakReport? = null,
    val sessionMinutes: Int = 0,
)

data class PermissionUi(val granted: Set<PermissionKind> = emptySet()) {
    fun has(k: PermissionKind) = k in granted
}

/** An installed or known app offered in the picker. */
data class AppChoiceUi(val packageName: String, val label: String, val category: AppCategory, val known: Boolean = false)

data class CompatUi(
    val status: CompatStatus = CompatStatus.UNTESTED,
    /** The strategy that has proved itself on this app, if one has. */
    val strategy: Strategy? = null,
    val lastTestedMs: Long = 0L,
    val available: Set<Strategy> = emptySet(),
    val results: List<StrategyTestResult> = emptyList(),
    val testing: Boolean = false,
    val versionName: String = "",
    /** What the next break will try, while nothing has been proven: a way that can read the player back. */
    val trial: Strategy? = null,
)

enum class CalibrationStage { INTRO, RUNNING, RESULT }

data class CalibrationUi(
    val stage: CalibrationStage = CalibrationStage.INTRO,
    val phase: CalibrationSession.Phase = CalibrationSession.Phase.VIDEO,
    val phaseProgress: Float = 0f,
    val remainingSec: Int = 0,
    val deliberateTotal: Int = 5,
    val deliberateLeft: Int = 5,
    /** How open the eyes are, 0..1 where 1 is this person's own open eye, or null when no face is tracked. */
    val liveOpenness: Float? = null,
    /** What the person could do so that the deliberate blinks count, or NONE. */
    val hint: CalibrationSession.Hint = CalibrationSession.Hint.NONE,
    /** How far the eyes must close (0..1, shut = 1) for a deliberate blink to count right now. */
    val countsAt: Float = 0f,
    val quality: QualityReport? = null,
    val blinksSeen: Int = 0,
    val result: CalibrationResult? = null,
    val hasCameraPermission: Boolean = false,
    val modelReady: Boolean = true,
    /** Why the camera or the face model could not start, when it could not; shown on the intro page. */
    val error: String? = null,
    /** The camera permission was refused when Begin was pressed; the intro page explains what to do. */
    val cameraDenied: Boolean = false,
)

/** The "try your blinks" page: what the camera engine sees of the eyes right now. Nothing here is kept. */
data class EyeTestUi(
    val running: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val cameraDenied: Boolean = false,
    /** Why the camera or the face model could not start, when it could not. */
    val error: String? = null,
    /** How open the eyes are, 0..1 where 1 is this person's own open eye, or null when no face is tracked. */
    val openness: Float? = null,
    /** How far the eyes must close (0..1, shut = 1) to count as a blink with the thresholds in force. */
    val countsAt: Float = 0f,
    val blinks: Int = 0,
    /** The last blink seen: how deep it went (0..1), how long it took, and whether it counted as a full one. */
    val lastDepth: Float? = null,
    val lastDurationMs: Long = 0L,
    val lastComplete: Boolean = false,
    val quality: QualityReport? = null,
    /** Whether the thresholds were fitted to this person by a calibration. */
    val fitted: Boolean = false,
)

data class PowerUi(
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    /** null = not checked yet. */
    val rootAvailable: Boolean? = null,
    val status: PowerStatus = PowerStatus.None,
    val busy: Boolean = false,
    val setup: List<SetupStep> = emptyList(),
    val lsposedModuleActive: Boolean = false,
)

data class StatsUi(
    val today: DayStats = DayStats(),
    val week: DayStats = DayStats(),
    /** Oldest first, seven entries. */
    val series: List<DayStats> = emptyList(),
    val seriesLabels: List<String> = emptyList(),
)

data class NightUi(
    val target: NightTarget? = null,
    val canWriteSecureSettings: Boolean = false,
    val canWriteSettings: Boolean = false,
    val lux: Float? = null,
    val brightnessPercent: Int? = null,
    val manualOverrideUntilMs: Long = 0L,
)

data class DiagnosticsUi(
    val appVersion: String = "",
    val androidSdk: Int = 0,
    val device: String = "",
    val serviceRunning: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val listenerConnected: Boolean = false,
    val overlayKind: String = "",
    val frameP50Ms: Long = 0,
    val frameP95Ms: Long = 0,
    val cameraDutyPercent: Int = 0,
    val drain: DrainComparison = DrainComparison(null, null),
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val thermal: String = "none",
    val powerSave: Boolean = false,
    /** The last reason the camera or the face model failed, or null. */
    val cameraError: String? = null,
)

data class UiState(
    val settings: EyeBlinkSettings = EyeBlinkSettings(),
    val runtime: RuntimeUi = RuntimeUi(),
    val perms: PermissionUi = PermissionUi(),
    val power: PowerUi = PowerUi(),
    val apps: List<AppChoiceUi> = emptyList(),
    val compat: Map<String, CompatUi> = emptyMap(),
    val calibration: CalibrationUi = CalibrationUi(),
    val eyeTest: EyeTestUi = EyeTestUi(),
    val personal: PersonalProfile = PersonalProfile(),
    val stats: StatsUi = StatsUi(),
    val night: NightUi = NightUi(),
    val diag: DiagnosticsUi = DiagnosticsUi(),
    val nowMs: Long = 0L,
    /** Local clock as HH:mm, supplied by the platform so the UI stays free of time zones. */
    val clock: String = "",
)

/** Everything the UI can ask the app to do. The platform implements it; the design lab fakes it. */
interface UiActions {
    fun setRunning(on: Boolean)
    fun blinkNow()
    fun restNow()
    fun snooze(minutes: Int)
    fun cancelSnooze()
    fun emergencyStop()

    fun update(transform: (EyeBlinkSettings) -> EyeBlinkSettings)

    fun request(permission: PermissionKind)

    fun addApp(packageName: String)
    fun removeApp(packageName: String)
    fun testCompat(packageName: String)
    fun openApp(packageName: String)

    fun startEyeTest()
    fun stopEyeTest()

    fun startCalibration()
    fun skipCalibrationPhase()
    fun cancelCalibration()
    fun saveCalibration()
    fun resetCalibration()

    fun requestShizuku()
    fun detectRoot()
    fun runSetup(steps: Set<SetupStepId>)
    fun openLsposed()

    fun clearStats()
    fun copyReport()
    fun finishOnboarding()
}

/** An actions object that does nothing; used for previews and tests. */
object NoActions : UiActions {
    override fun setRunning(on: Boolean) = Unit
    override fun blinkNow() = Unit
    override fun restNow() = Unit
    override fun snooze(minutes: Int) = Unit
    override fun cancelSnooze() = Unit
    override fun emergencyStop() = Unit
    override fun update(transform: (EyeBlinkSettings) -> EyeBlinkSettings) = Unit
    override fun request(permission: PermissionKind) = Unit
    override fun addApp(packageName: String) = Unit
    override fun removeApp(packageName: String) = Unit
    override fun testCompat(packageName: String) = Unit
    override fun openApp(packageName: String) = Unit
    override fun startEyeTest() = Unit
    override fun stopEyeTest() = Unit
    override fun startCalibration() = Unit
    override fun skipCalibrationPhase() = Unit
    override fun cancelCalibration() = Unit
    override fun saveCalibration() = Unit
    override fun resetCalibration() = Unit
    override fun requestShizuku() = Unit
    override fun detectRoot() = Unit
    override fun runSetup(steps: Set<SetupStepId>) = Unit
    override fun openLsposed() = Unit
    override fun clearStats() = Unit
    override fun copyReport() = Unit
    override fun finishOnboarding() = Unit
}

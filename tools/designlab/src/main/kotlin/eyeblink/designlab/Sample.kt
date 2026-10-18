package eyeblink.designlab

import com.eyeblink.app.ui.state.AppChoiceUi
import com.eyeblink.app.ui.state.CalibrationStage
import com.eyeblink.app.ui.state.CalibrationUi
import com.eyeblink.app.ui.state.CompatUi
import com.eyeblink.app.ui.state.DiagnosticsUi
import com.eyeblink.app.ui.state.NightUi
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.PermissionUi
import com.eyeblink.app.ui.state.PowerUi
import com.eyeblink.app.ui.state.RuntimeUi
import com.eyeblink.app.ui.state.StatsUi
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.calibration.BlinkBaseline
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.Factor
import com.eyeblink.core.engine.FactorCode
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary
import com.eyeblink.core.engine.TriggerMode
import com.eyeblink.core.model.AppCategory
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.Language
import com.eyeblink.core.model.OperatingMode
import com.eyeblink.core.playback.CompatOutcome
import com.eyeblink.core.playback.CompatStatus
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.playback.StrategyTestResult
import com.eyeblink.core.privileged.Capability
import com.eyeblink.core.privileged.PowerKind
import com.eyeblink.core.privileged.PowerStatus
import com.eyeblink.core.stats.DayStats

/** Believable data for screenshots. */
object Sample {

    fun settings(lang: Language = Language.EN) = EyeBlinkSettings(
        onboardingDone = true, language = lang, mode = OperatingMode.SHIZUKU,
        apps = listOf(
            AppProfile("com.instagram.android", "Instagram", category = AppCategory.SOCIAL),
            AppProfile("com.zhiliaoapp.musically", "TikTok", category = AppCategory.VIDEO, intervalScalePercent = 80),
            AppProfile("com.google.android.youtube", "YouTube", category = AppCategory.VIDEO),
        ),
    )

    /** One believable day: counters that hang together the way the real ledger's would. */
    private fun day(micro: Int, rests: Int, minutes: Int, skipped: Int = 0): DayStats {
        val trackedMin = minutes * 22 / 100
        return DayStats(
            microShown = micro, restsCompleted = rests, restsSkipped = skipped, activeMs = minutes * 60_000L,
            pausesVerified = micro * 8 / 10, pausesUnverified = micro / 14, pausesFailed = micro / 30,
            resumesOk = micro * 76 / 100, resumesRefused = micro / 12, resumesFailed = micro / 60,
            cameraMs = trackedMin * 60_000L, observedBlinks = trackedMin * 116 / 10, observedTrackedMs = trackedMin * 60_000L,
            deferredByReason = mapOf(
                "TOUCHING" to micro / 5, "RECENT_TOUCH" to micro / 3, "VIDEO_TRANSITION" to micro / 8,
                "TEXT_INPUT" to micro / 14, "IN_CALL" to if (micro > 60) 2 else 0,
            ).filterValues { it > 0 },
        )
    }

    private val week = listOf(day(38, 3, 82), day(52, 4, 104, skipped = 1), day(21, 1, 40), day(66, 5, 131), day(71, 6, 149, skipped = 2), day(44, 3, 96), day(29, 2, 61))

    fun calibration(
        stage: CalibrationStage,
        phase: com.eyeblink.core.calibration.CalibrationSession.Phase = com.eyeblink.core.calibration.CalibrationSession.Phase.VIDEO,
        hint: com.eyeblink.core.calibration.CalibrationSession.Hint = com.eyeblink.core.calibration.CalibrationSession.Hint.NONE,
    ): UiState {
        val base = state()
        val video = BlinkBaseline(9_800, 138, 0.9f, 7)
        val reading = BlinkBaseline(13_400, 145, 0.86f, 6)
        val general = BlinkBaseline(11_300, 141, 0.88f, 13)
        val profile = PersonalProfile(
            general = general, byContext = mapOf(ContextProfile.VIDEO to video, ContextProfile.READING to reading),
            thresholds = com.eyeblink.core.calibration.BlinkThresholds(0.30f, 0.14f, 0.55f, true, 0.60f),
        )
        val result = com.eyeblink.core.calibration.CalibrationResult(
            baseline = general, spontaneousCount = 13, instructedCount = 5, complete = true, completeRatio = 0.88f,
            profile = profile, meanConfidence = 0.87f, meanLuma = 118f, trackedMs = 112_000,
        )
        return base.copy(
            calibration = CalibrationUi(
                stage = stage, phase = phase, phaseProgress = 0.42f, remainingSec = 34, deliberateTotal = 5, deliberateLeft = 2,
                liveOpenness = if (phase == com.eyeblink.core.calibration.CalibrationSession.Phase.DELIBERATE) 0.58f else 0.82f,
                countsAt = 0.38f, hint = hint,
                blinksSeen = 4, result = if (stage == CalibrationStage.RESULT) result else null, hasCameraPermission = true,
                quality = com.eyeblink.core.calibration.QualityReport(0.55f, setOf(com.eyeblink.core.calibration.QualityIssue.TOO_DARK)),
            ),
            personal = if (stage == CalibrationStage.RESULT) profile else base.personal,
        )
    }

    /** The "try your blinks" page: not yet begun, or running with a few blinks counted. */
    fun eyeTest(running: Boolean): UiState {
        val base = state()
        return base.copy(
            eyeTest = com.eyeblink.app.ui.state.EyeTestUi(
                running = running, hasCameraPermission = true, openness = if (running) 0.62f else null, countsAt = 0.31f,
                blinks = if (running) 7 else 0, lastDepth = if (running) 0.58f else null, lastDurationMs = 150, lastComplete = running,
                fitted = true,
            ),
        )
    }

    /** The Diagnostics page after a break in which nothing could be paused: root is selected but its shell is down. */
    fun pausingTrouble(): UiState {
        val base = state()
        val br = BreakReport(
            BreakKind.MICRO, "com.google.android.youtube", 0, 455, BreakOutcome.COMPLETED, strategy = Strategy.PRIVILEGED_SESSION,
            pause = PauseSummary.FAILED, error = "media keys currently go to a different app than the one in front, so a key would not reach this one",
        )
        return base.copy(
            settings = base.settings.copy(mode = com.eyeblink.core.model.OperatingMode.ROOT, apps = base.settings.apps),
            runtime = base.runtime.copy(lastBreak = br),
            power = PowerUi(rootAvailable = true, status = PowerStatus.unavailable(PowerKind.ROOT, "no answer")),
        )
    }

    fun state(lang: Language = Language.EN, running: Boolean = true, micro: Long? = 38_000, notReady: Boolean = false): UiState {
        val perms = if (notReady) emptySet() else setOf(PermissionKind.ACCESSIBILITY, PermissionKind.NOTIFICATION_LISTENER, PermissionKind.NOTIFICATIONS, PermissionKind.OVERLAY)
        val baseline = BlinkBaseline(9_200, 140, 0.88f, 64)
        return UiState(
            settings = settings(lang),
            runtime = RuntimeUi(
                running = running, microInMs = micro, restInMs = 14 * 60_000L, intervalMs = 52_000,
                triggerMode = TriggerMode.CAMERA_ASSISTED, foregroundLabel = "Instagram", context = ContextProfile.VIDEO,
                factors = listOf(
                    Factor(FactorCode.PERSONAL_BASELINE, 0.87f, observed = true),
                    Factor(FactorCode.LONG_SESSION, 0.82f),
                    Factor(FactorCode.OBSERVED_LOW_RATE, 0.78f, observed = true),
                ),
                lastBreak = BreakReport(BreakKind.MICRO, "com.instagram.android", 0, 455, BreakOutcome.COMPLETED, strategy = Strategy.MEDIA_SESSION, pause = PauseSummary.PAUSED, resume = ResumeSummary.RESUMED),
            ),
            perms = PermissionUi(perms),
            power = PowerUi(shizukuInstalled = true, shizukuRunning = true, shizukuGranted = true, status = PowerStatus(PowerKind.SHIZUKU, true, 2000, 34, Capability.entries.toSet(), "shell (adb-level)")),
            apps = listOf(
                AppChoiceUi("com.instagram.android", "Instagram", AppCategory.SOCIAL, true),
                AppChoiceUi("com.zhiliaoapp.musically", "TikTok", AppCategory.VIDEO, true),
                AppChoiceUi("com.google.android.youtube", "YouTube", AppCategory.VIDEO, true),
                AppChoiceUi("com.netflix.mediaclient", "Netflix", AppCategory.VIDEO, true),
                AppChoiceUi("com.reddit.frontpage", "Reddit", AppCategory.SOCIAL, true),
                AppChoiceUi("org.telegram.messenger", "Telegram", AppCategory.OTHER),
            ),
            compat = mapOf(
                "com.google.android.youtube" to CompatUi(CompatStatus.VERIFIED, Strategy.MEDIA_SESSION, System.currentTimeMillis() - 3_600_000, setOf(Strategy.MEDIA_SESSION, Strategy.PRIVILEGED_SESSION), listOf(StrategyTestResult(Strategy.MEDIA_SESSION, CompatOutcome.PASSED, 120)), versionName = "19.38.35"),
                "com.instagram.android" to CompatUi(CompatStatus.UNTESTED, available = setOf(Strategy.MEDIA_SESSION, Strategy.PRIVILEGED_SESSION), trial = Strategy.MEDIA_SESSION),
                "com.zhiliaoapp.musically" to CompatUi(CompatStatus.FAILING, null, System.currentTimeMillis() - 86_400_000, setOf(Strategy.MEDIA_SESSION), listOf(StrategyTestResult(Strategy.MEDIA_SESSION, CompatOutcome.NO_SESSION))),
                "com.netflix.mediaclient" to CompatUi(CompatStatus.UNTESTED),
            ),
            personal = PersonalProfile(general = baseline, byContext = mapOf(ContextProfile.VIDEO to baseline.copy(medianIbiMs = 10_100))),
            stats = StatsUi(today = week.last(), week = week.fold(DayStats(), com.eyeblink.core.stats.UsageLedger.Companion::merge), series = week, seriesLabels = listOf("M", "T", "W", "T", "F", "S", "S")),
            night = NightUi(),
            diag = DiagnosticsUi(appVersion = "0.3.0", androidSdk = 34, device = "Pixel 8", serviceRunning = true, accessibilityConnected = true, listenerConnected = true, overlayKind = "Accessibility overlay", frameP50Ms = 14, frameP95Ms = 31, cameraDutyPercent = 18, batteryPercent = 71),
            nowMs = System.currentTimeMillis(), clock = "21:40",
        )
    }
}

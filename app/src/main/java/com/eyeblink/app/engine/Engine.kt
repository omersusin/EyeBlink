package com.eyeblink.app.engine

import com.eyeblink.app.platform.systemService
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.eyeblink.app.overlay.AndroidOverlay
import com.eyeblink.app.overlay.OverlayWindows
import com.eyeblink.app.overlay.RestRenderer
import com.eyeblink.app.overlay.TouchSentinel
import com.eyeblink.app.access.MediaSessions
import com.eyeblink.app.platform.Access
import com.eyeblink.app.platform.AmbientLight
import com.eyeblink.app.platform.DeviceState
import com.eyeblink.app.platform.Signals
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.playback.LsposedBackend
import com.eyeblink.app.power.PowerController
import com.eyeblink.app.store.Stores
import com.eyeblink.app.system.EyeBlinkService
import com.eyeblink.app.system.Notifier
import com.eyeblink.app.system.RunningNote
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.state.AppChoiceUi
import com.eyeblink.app.ui.state.DiagnosticsUi
import com.eyeblink.app.ui.state.PermissionUi
import com.eyeblink.app.ui.state.RuntimeUi
import com.eyeblink.app.ui.state.StatsUi
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.calibration.BlinkObservation
import com.eyeblink.core.calibration.ContextClassifier
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.engine.BreakDirector
import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.BreakRequest
import com.eyeblink.core.engine.BreakScheduler
import com.eyeblink.core.engine.ContextSnapshot
import com.eyeblink.core.engine.DirectorEnvironment
import com.eyeblink.core.engine.GatePolicy
import com.eyeblink.core.engine.IntervalPlan
import com.eyeblink.core.engine.MediaSignal
import com.eyeblink.core.engine.Reason
import com.eyeblink.core.engine.SchedulerInput
import com.eyeblink.core.engine.SchedulerStep
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.KnownApps
import com.eyeblink.core.model.NeverInterrupt
import com.eyeblink.core.perf.BatterySample
import com.eyeblink.core.perf.TrackingTag
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackCoordinator
import com.eyeblink.core.privileged.SetupStepId
import com.eyeblink.core.rest.RestRequest
import com.eyeblink.core.rest.RestSession
import com.eyeblink.core.stats.DayStats
import com.eyeblink.core.stats.UsageLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The brain of the running app: one object that owns every component, decides when a break is due,
 * runs it, and publishes a [UiState] for the screen.
 *
 * Everything here happens on one dedicated thread. Callbacks from the system (accessibility events,
 * camera frames, the UI) only post work to it, so no lock is needed and no two pieces of logic ever
 * observe the world half-updated. Slow things - the camera, the privileged shell, the overlay -
 * are suspended or live on their own threads and report back by posting.
 */
class Engine(val app: Application, ports: EnginePorts) {

    private val faces: FaceSource? = ports.faces

    // ── threading ────────────────────────────────────────────────

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "eyeblink-engine").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    fun post(block: () -> Unit) {
        executor.execute {
            try { block() } catch (e: Throwable) { Log.e(TAG, "engine task failed", e) }
        }
    }

    // ── components ───────────────────────────────────────────────

    private val stores = Stores(app)

    // What was saved, loaded before any component is built. Properties are initialised in the order they are
    // declared, and some components read these while they are still being constructed (the tracker takes its
    // first thresholds from the profile). Declared any further down they are still null at that moment, which
    // made the engine throw - and the app die - the first time it started.
    @Volatile var settings: EyeBlinkSettings = stores.loadSettings()
        private set
    private var personal: PersonalProfile = stores.loadProfile()
    private var ledger: UsageLedger = stores.loadLedger()
    private var batteryLedger = stores.loadBattery()

    private val device = DeviceState(app)
    private val light = AmbientLight(app)
    private val sessions = MediaSessions(app)
    private val usageForeground = UsageForeground(app)
    private val notifier = Notifier(app)
    val windows = OverlayWindows(app)

    private val rest: RestRenderer = ports.rest(windows, ::strings, ::systemReducedMotion)
    private val overlay = AndroidOverlay(app, windows, rest, ::systemReducedMotion, onBlinkStart = { echo++ })

    val lsposed = LsposedBackend(app)

    private val power = PowerController(
        app, scope, ports.shizuku, app.getSharedPreferences("eyeblink-power", 0),
        lsposedActive = { lsposed.everReplied }, onChange = { post { publish() } },
        // Root or Shizuku was just granted on purpose: start using it, unless a privileged mode already works.
        onGranted = { mode -> post { if (!power.status.available && settings.mode != mode) updateNow { it.copy(mode = mode) } } },
    )

    private val compat = CompatController(
        app, scope, stores, { settings }, sessions, device, power,
        lsposed = lsposed,
        foreground = { Signals.foregroundPackage },
        guide = { pkg, outcome -> onCompatGuide(pkg, outcome) },
        onChange = { post { publish() } },
    )

    private val tracking = TrackingController(
        app, ports.faces, device, { settings }, { personal }, { setPersonal(it) }, { lastContext },
        post = ::post, onBlink = ::onBlink, onUsage = ::onUsage,
    )

    private val calibrator = Calibrator(
        app, scope, ports.faces, { settings }, post = ::post, onSave = { setPersonal(it) }, onChange = { post { publish() } },
    )

    private val eyeTester = EyeTester(
        app, scope, ports.faces, { settings }, { personal }, post = ::post, onChange = { post { publish() } },
    )

    private val night = NightController(app, scope, { settings.night }, light, onChange = { post { publish() } })

    private val scheduler = BreakScheduler()
    private val coordinator = PlaybackCoordinator()

    private val env = object : DirectorEnvironment {
        override fun nowMs(): Long = com.eyeblink.app.platform.nowMs()
        override fun snapshot(): ContextSnapshot = buildContext(nowMs())
        override fun gatePolicy(target: String?) = GatePolicy(
            masterEnabled = true, suspended = suspended, snoozedUntilMs = scheduler.snoozedUntilMs,
            appSelected = settings.profileFor(target) != null,
            quietHoursActive = settings.rhythm.quietHours.contains(minuteOfDay()),
        )
        override suspend fun backendFor(target: String): PlaybackBackend? = compat.backendFor(target)
        override fun blinkConfirmAvailable(): Boolean = tracking.canConfirmBlink(nowMs())
    }
    private val director = BreakDirector(overlay, coordinator, env)

    // ── state (engine thread only unless marked; the saved values are declared with `stores`, above) ──

    @Volatile private var running = false
    @Volatile private var suspended = false
    private var echo = 0
    private var lastBreak: BreakReport? = null
    private var jitter = 0f
    private var lastContext = com.eyeblink.core.calibration.ContextProfile.VIDEO
    private var runtime = RuntimeUi()
    private var loopJob: Job? = null
    private var breakJob: Deferred<BreakReport>? = null
    private var sentinel: TouchSentinel? = null

    private var apps: List<AppChoiceUi> = emptyList()
    private var appsLoadedMs = 0L
    private var perms: Set<com.eyeblink.app.ui.state.PermissionKind> = emptySet()
    private var permsAtMs = 0L
    private var lastUsagePollMs = 0L
    private var lastBatterySampleMs = 0L
    private var lastLedgerSaveMs = 0L
    private var pendingActiveMs = 0L
    private var lastDeferralKey: Pair<BreakKind, Reason>? = null
    private var lastNote: RunningNote? = null
    private var lastNoteLang: Strings.Lang? = null
    private var service: WeakReference<EyeBlinkService>? = null

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> get() = _ui

    // ── lifecycle ────────────────────────────────────────────────

    /** Called once from `Application.onCreate`. */
    fun attach() = post {
        power.onModeChanged(settings.mode)
        power.start()
        compat.refreshAvailability()
        loadApps()
        resumeIfNeeded()
        night.sync()
        publish()
    }

    /** Restores the master switch after a process restart, or after a reboot if the user asked for that. */
    fun resumeIfNeeded() = post {
        if (running || !stores.wasRunning) return@post
        val bootCount = bootCount()
        val sameBoot = stores.runningBootCount == bootCount
        if (sameBoot || settings.startOnBoot) setRunningNow(true)
    }

    fun setRunning(on: Boolean) = post { setRunningNow(on) }

    private fun setRunningNow(on: Boolean) {
        if (on == running) return
        if (on) {
            if (!windows.available()) { publish(); return }
            suspended = false
            scheduler.resetTimers()
            running = true
            stores.wasRunning = true
            stores.runningBootCount = bootCount()
            light.start()
            device.watchCameras()
            syncSentinel()
            loopJob?.cancel()
            loopJob = scope.launch { loop() }
        } else {
            running = false
            stores.wasRunning = false
            loopJob?.cancel()
            loopJob = null
            cancelBreak()
            tracking.stopCamera()
            overlay.clear()
            removeSentinel()
            device.unwatchCameras()
            if (!settings.night.enabled) light.stop()
            runtime = RuntimeUi()
        }
        syncService()
        notifyState()
        publish()
    }

    /** Suspend or resume from the Quick Settings tile or the notification: engine stays on, nothing is drawn. */
    fun setSuspended(on: Boolean) = post {
        suspended = on
        if (on) {
            cancelBreak()
            tracking.stopCamera()
            overlay.clear()
        }
        notifyState()
        publish()
    }

    fun toggleSuspended() = post {
        if (!running) setRunningNow(true) else {
            suspended = !suspended
            if (suspended) { cancelBreak(); tracking.stopCamera(); overlay.clear() }
            notifyState()
            publish()
        }
    }

    /** Everything off at once: breaks, camera, calibration, night automation, and every window. */
    fun emergencyStop() = post {
        cancelBreak()
        calibrator.cancel()
        tracking.stopCamera()
        overlay.clear()
        night.suspended = true
        night.sync()
        setRunningNow(false)
        publish()
    }

    /** Android told the accessibility service to stop: take down what is drawn, but stay as configured. */
    fun emergencyClear() = post {
        cancelBreak()
        overlay.clear()
    }

    fun snooze(minutes: Int) = post {
        scheduler.snooze(nowMs() + minutes * 60_000L)
        notifyState()
        publish()
    }

    fun cancelSnooze() = post {
        scheduler.clearSnooze()
        notifyState()
        publish()
    }

    /** A blink on demand, for trying it out. Skips scheduling and never touches playback. */
    fun blinkNow() = post {
        if (breakJob != null || !windows.available()) return@post
        scope.launch { runCatching { overlay.playBlink(settings.blink) } }
    }

    /** A rest on demand. Runs the same scene as a scheduled one, without the video protocol. */
    fun restNow() = post {
        if (breakJob != null || !windows.available()) return@post
        val s = settings
        val session = RestSession(
            RestRequest(s.rest.style, s.rest.effectiveDurationSec, s.rest.confirm, tracking.canConfirmBlink(nowMs()), s.blink.reducedMotion),
            nowMs(),
        )
        restSessionForDemo = session
        scope.launch {
            try { overlay.runRest(session, s.blink) } finally { restSessionForDemo = null; overlay.clear() }
        }
    }

    @Volatile private var restSessionForDemo: RestSession? = null

    private fun cancelBreak() {
        breakJob?.cancel()
        breakJob = null
        restSessionForDemo?.cancel(nowMs())
    }

    // ── settings & data ──────────────────────────────────────────

    fun update(transform: (EyeBlinkSettings) -> EyeBlinkSettings) = post { updateNow(transform) }

    private fun updateNow(transform: (EyeBlinkSettings) -> EyeBlinkSettings) {
        val old = settings
        val next = transform(old).validated()
        if (next == old) return
        settings = next
        stores.saveSettings(next)
        if (old.mode != next.mode) power.onModeChanged(next.mode)
        if (old.night != next.night) { night.suspended = false; night.sync() }
        if (old.apps != next.apps || old.playback != next.playback) compat.refreshAvailability()
        if (old.camera.tracking != next.camera.tracking) {
            if (next.camera.tracking == com.eyeblink.core.model.TrackingMode.OFF) tracking.stopCamera()
            service?.get()?.refresh()
        }
        if (old.camera.sensitivity != next.camera.sensitivity) {
            tracking.applyThresholds(personal.thresholds)
            eyeTester.refresh()
        }
        syncService()
        notifyState()
        publish()
    }

    fun addApp(pkg: String) = post {
        val label = apps.firstOrNull { it.packageName == pkg }?.label ?: KnownApps.find(pkg)?.label ?: pkg
        updateNow { it.withApp(AppProfile(pkg, label, category = KnownApps.categoryOf(pkg))) }
    }

    fun removeApp(pkg: String) = update { it.withoutApp(pkg) }

    fun testCompat(pkg: String) = post { compat.test(pkg) }

    fun startCalibration() = post { eyeTester.stop(); tracking.stopCamera(); calibrator.start(); syncService(); publish() }
    fun startEyeTest() = post { if (calibrator.running) calibrator.cancel(); tracking.stopCamera(); eyeTester.start(); syncService(); publish() }
    fun stopEyeTest() = post { eyeTester.stop(); syncService(); publish() }
    fun eyeTestCameraDenied() = post { eyeTester.cameraDenied(); publish() }
    fun calibrationCameraDenied() = post { calibrator.cameraDenied(); publish() }
    fun skipCalibrationPhase() = post { calibrator.skipPhase() }
    fun cancelCalibration() = post { calibrator.cancel() }
    fun saveCalibration() = post { calibrator.save(); publish() }
    fun resetCalibration() = post { calibrator.reset(); publish() }

    private fun setPersonal(p: PersonalProfile) {
        personal = p
        stores.saveProfile(p)
        tracking.applyThresholds(p.thresholds)
        eyeTester.refresh()
        publish()
    }

    fun requestShizuku() = power.requestShizuku()
    fun detectRoot() = power.detectRoot()
    fun runSetup(steps: Set<SetupStepId>) = power.runSetup(steps)

    fun clearStats() = post {
        ledger = UsageLedger()
        stores.saveLedger(ledger)
        publish()
    }

    fun finishOnboarding() = update { it.copy(onboardingDone = true) }

    fun copyReport() {
        scope.launch {
            // What the privileged shell makes of the media sessions is the one thing a bug report cannot do without
            // when pausing misbehaves; it is read here, with names and titles stripped, and never kept.
            val outline = withTimeoutOrNull(6_000L) { runCatching { compat.sessionOutline() }.getOrNull() }
            val text = report(outline)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                app.systemService<ClipboardManager>().setPrimaryClip(ClipData.newPlainText("EyeBlink report", text))
            }
        }
    }

    // ── signals from services and screens ────────────────────────

    fun onAccessibilityChanged(connected: Boolean) = post {
        compat.refreshAvailability()
        if (connected) resumeIfNeeded()
        if (running) { if (connected) syncSentinel() else removeSentinel() }
        publish()
    }

    fun onListenerChanged(@Suppress("UNUSED_PARAMETER") connected: Boolean) = post {
        compat.refreshAvailability()
        publish()
    }

    fun onUiVisible(visible: Boolean) = post {
        Signals.uiVisible = visible
        if (visible) {
            if (nowMs() - appsLoadedMs > 60_000L) loadApps()
            permsAtMs = 0L
            service?.get()?.refresh()
        }
        publish()
    }

    fun attachService(s: EyeBlinkService?) {
        service = s?.let { WeakReference(it) }
        if (s != null) post { resumeIfNeeded(); notifyState(force = true) }
    }

    /** Whether the service should be alive: the engine is on, night mode is on, or a calibration is running. */
    fun wantsService(): Boolean = running || settings.night.enabled || calibrator.running || eyeTester.running

    /** Whether the service should carry the camera type: only if camera tracking is both wanted and permitted. */
    fun wantsCamera(): Boolean =
        (settings.camera.tracking != com.eyeblink.core.model.TrackingMode.OFF || calibrator.running || eyeTester.running) && Access.camera(app)

    private fun syncService() {
        if (wantsService()) {
            if (service?.get() == null) {
                try {
                    app.startForegroundService(Intent(app, EyeBlinkService::class.java))
                } catch (e: Exception) {
                    // Started from the background where Android forbids it. The accessibility service keeps the
                    // process alive on its own; only the notification and the camera type have to wait.
                    Log.w(TAG, "foreground service could not start: ${e.message}")
                }
            } else {
                service?.get()?.refresh()
            }
        } else {
            service?.get()?.stopSelfSafely()
        }
    }

    // ── the loop ─────────────────────────────────────────────────

    private suspend fun loop() {
        var last = nowMs()
        var lastPublish = 0L
        while (scope.isActive && running) {
            val now = nowMs()
            val dt = (now - last).coerceIn(0L, 3_000L)
            last = now

            pollForeground(now)
            val ctx = buildContext(now)
            val input = schedulerInput(ctx, now)
            val eligible = ctx.screenOn && !ctx.deviceLocked && input.profile != null && !suspended
            // While the calibration runs it owns the camera. On its next tick the tracker would find a camera that "should
            // not be running" (tracking off, or nobody eligible) and stop it, and the calibration would count down without
            // ever seeing a picture.
            if (!calibrator.running && !eyeTester.running) tracking.tick(now, eligible)
            sampleBattery(now, ctx)

            val step = if (suspended || !windows.available()) {
                SchedulerStep.Idle(if (suspended) Reason.SUSPENDED else Reason.DISABLED)
            } else {
                scheduler.step(ctx, input)
            }

            var sleep = IDLE_MS
            when (step) {
                is SchedulerStep.Idle -> {
                    runtime = idleRuntime(step.reason, input, ctx)
                    lastDeferralKey = null
                }
                is SchedulerStep.Waiting -> {
                    pendingActiveMs += dt
                    runtime = runtime(ctx, input, microInMs = step.microInMs, restInMs = step.restInMs, plan = step.plan)
                    lastDeferralKey = null
                    sleep = minOf(if (Signals.uiVisible) UI_MS else IDLE_MS, step.microInMs.coerceAtLeast(MIN_SLEEP_MS))
                }
                is SchedulerStep.Deferred -> {
                    pendingActiveMs += dt
                    val key = step.kind to step.reason
                    if (key != lastDeferralKey && settings.privacy.localStats) {
                        ledger = ledger.recordDeferral(dayKey(), step.reason.name)
                        lastDeferralKey = key
                    }
                    runtime = runtime(ctx, input, microInMs = 0L, restInMs = null, plan = null, deferred = step.reason)
                    sleep = step.retryInMs.coerceIn(MIN_SLEEP_MS, 1_000L)
                }
                is SchedulerStep.Start -> {
                    pendingActiveMs += dt
                    lastDeferralKey = null
                    runBreak(step, ctx)
                    sleep = MIN_SLEEP_MS
                }
            }

            if (now - lastPublish >= (if (Signals.uiVisible) UI_MS else 1_000L)) {
                lastPublish = now
                publish()
            }
            flushLedger(now)
            delay(sleep)
        }
    }

    private suspend fun runBreak(step: SchedulerStep.Start, ctx: ContextSnapshot) {
        val request = BreakRequest(step.kind, ctx.foregroundPackage, step.forced)
        val job = scope.async { director.run(request, settings) }
        breakJob = job
        val report = try {
            job.await()
        } catch (e: CancellationException) {
            if (!scope.isActive) throw e
            null
        } finally {
            breakJob = null
        }
        val now = nowMs()
        if (report == null) {
            scheduler.onBreakFinished(step.kind, BreakOutcome.ABORTED, now)
            return
        }
        scheduler.onBreakFinished(step.kind, report.outcome, now)
        lastBreak = report
        jitter = Random.nextFloat() * 2f - 1f
        if (settings.privacy.localStats) ledger = ledger.recordBreak(dayKey(), report)
        compat.recordReal(report)
        flushLedger(now, force = true)
        publish()
    }

    private fun pollForeground(now: Long) {
        if (Signals.accessibility != null || now - lastUsagePollMs < 2_000L) return
        lastUsagePollMs = now
        if (Access.usageStats(app)) usageForeground.poll()?.let { Signals.setForegroundFallback(it, now) }
    }

    private fun buildContext(now: Long): ContextSnapshot {
        val fg = Signals.foregroundPackage
        val bat = device.battery()
        return ContextSnapshot(
            nowMs = now,
            localMinuteOfDay = minuteOfDay(),
            screenOn = device.screenOn(),
            deviceLocked = device.locked(),
            foregroundPackage = fg,
            audioMode = device.audioMode(),
            touching = false,
            lastTouchMs = Signals.lastTouchDownMs,
            lastScrollMs = Signals.lastScrollMs,
            keyboardVisible = now < Signals.keyboardUntilMs,
            cameraBusyElsewhere = device.cameraBusyElsewhere(tracking.cameraActive),
            navigationActive = Signals.navigationActive,
            sensitiveScreen = now < Signals.sensitiveUntilMs,
            media = mediaSignal(fg),
            ambientLux = light.lux,
            charging = bat.charging,
            batteryPercent = bat.percent,
            powerSave = device.powerSave(),
            thermal = device.thermal(),
        )
    }

    private fun mediaSignal(fg: String?): MediaSignal {
        if (fg == null || settings.profileFor(fg) == null) return MediaSignal.UNKNOWN
        sessions.isPlaying(fg)?.let { return if (it) MediaSignal.PLAYING else MediaSignal.NONE }
        return if (device.audioActive()) MediaSignal.PLAYING else MediaSignal.UNKNOWN
    }

    private fun schedulerInput(ctx: ContextSnapshot, now: Long): SchedulerInput {
        val fg = ctx.foregroundPackage
        val profile = settings.profileFor(fg)
        val category = profile?.category ?: KnownApps.categoryOf(fg.orEmpty())
        val profileContext = ContextClassifier.classify(category, ctx.media == MediaSignal.PLAYING, ctx.ambientLux)
        if (profileContext != lastContext) {
            lastContext = profileContext
        }
        val mode = tracking.triggerMode(now, ctx.screenOn)
        return SchedulerInput(
            settings = settings,
            suspended = suspended,
            profile = profile,
            context = profileContext,
            personal = personal,
            triggerMode = mode,
            observed = if (mode == com.eyeblink.core.engine.TriggerMode.CAMERA_ASSISTED) tracking.observed(now) else null,
            canPauseVerified = compat.canPause(fg),
            jitter = jitter,
        )
    }

    private fun runtime(
        ctx: ContextSnapshot, input: SchedulerInput, microInMs: Long, restInMs: Long?, plan: IntervalPlan?, deferred: Reason? = null,
    ) = RuntimeUi(
        running = true,
        microInMs = microInMs, restInMs = restInMs,
        intervalMs = plan?.intervalMs ?: runtime.intervalMs,
        deferred = deferred,
        triggerMode = input.triggerMode,
        foregroundLabel = labelOf(ctx.foregroundPackage),
        context = input.context,
        factors = plan?.factors ?: runtime.factors,
        sessionMinutes = (scheduler.sessionMs / 60_000L).toInt(),
    )

    private fun idleRuntime(reason: Reason, input: SchedulerInput, ctx: ContextSnapshot) = RuntimeUi(
        running = true, idle = reason, triggerMode = input.triggerMode,
        foregroundLabel = labelOf(ctx.foregroundPackage), context = input.context,
        intervalMs = runtime.intervalMs, factors = runtime.factors,
    )

    // ── learning, usage, battery ─────────────────────────────────

    private fun onBlink(blink: BlinkObservation) {
        if (director.onUserBlink()) publish()
    }

    private fun onUsage(trackedMs: Long, cameraMs: Long, blinks: Int) {
        if (!settings.privacy.localStats) return
        val day = dayKey()
        ledger = ledger.addCamera(day, cameraMs).addObserved(day, blinks, trackedMs)
    }

    private fun sampleBattery(now: Long, ctx: ContextSnapshot) {
        if (now - lastBatterySampleMs < BATTERY_SAMPLE_MS) return
        lastBatterySampleMs = now
        val tag = if (tracking.cameraActive || runtime.triggerMode == com.eyeblink.core.engine.TriggerMode.CAMERA_ASSISTED) TrackingTag.CAMERA_ASSISTED else TrackingTag.CAMERA_FREE
        batteryLedger = batteryLedger.add(BatterySample(now, ctx.batteryPercent, ctx.charging, ctx.screenOn, tag))
        stores.saveBattery(batteryLedger)
    }

    private fun flushLedger(now: Long, force: Boolean = false) {
        if (!force && now - lastLedgerSaveMs < LEDGER_SAVE_MS) return
        lastLedgerSaveMs = now
        if (pendingActiveMs > 0 && settings.privacy.localStats) {
            ledger = ledger.addActive(dayKey(), pendingActiveMs).pruned()
        }
        pendingActiveMs = 0
        stores.saveLedger(ledger)
    }

    // ── touch sentinel ───────────────────────────────────────────

    private fun syncSentinel() {
        if (sentinel != null || Signals.accessibility == null) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val view = TouchSentinel(app)
            if (windows.attachSentinel(view)) sentinel = view
        }
    }

    private fun removeSentinel() {
        val view = sentinel ?: return
        sentinel = null
        android.os.Handler(android.os.Looper.getMainLooper()).post { windows.detach(view) }
    }

    // ── compat guidance ──────────────────────────────────────────

    private fun onCompatGuide(pkg: String?, outcome: CompatOutcomeSummary?) {
        val s = strings()
        if (pkg != null) {
            notifier.testGuide(s, labelOf(pkg) ?: pkg)
        } else if (outcome != null) {
            notifier.testDone(s, labelOf(compat.testing) ?: s.t("the app", "uygulama"), outcome)
        } else {
            notifier.clearTest()
        }
    }

    // ── notification ─────────────────────────────────────────────

    fun notificationNote(): RunningNote = RunningNote(
        running = running, suspended = suspended, snoozed = scheduler.snoozedUntilMs > nowMs(),
        cameraOn = tracking.cameraActive || calibrator.running || eyeTester.running,
        nightOnly = !running && settings.night.enabled,
    )

    fun strings(): Strings = Strings.resolve(settings.language, Locale.getDefault().toLanguageTag())

    private fun notifyState(force: Boolean = false) {
        val note = notificationNote()
        val lang = strings().lang
        if (!force && note == lastNote && lang == lastNoteLang) return
        lastNote = note
        lastNoteLang = lang
        if (wantsService()) notifier.update(strings(), note)
    }

    fun notification() = notifier.running(strings(), notificationNote())

    // ── publishing ───────────────────────────────────────────────

    private fun publish() {
        val now = nowMs()
        if (now - permsAtMs > PERMS_MS) { perms = Access.granted(app); permsAtMs = now }
        val wall = System.currentTimeMillis()
        val snoozeUntil = scheduler.snoozedUntilMs
        val rt = runtime.copy(
            running = running,
            snoozedUntilMs = if (snoozeUntil > now) wall + (snoozeUntil - now) else 0L,
            echo = echo,
            cameraActive = tracking.cameraActive,
            lastBreak = lastBreak,
        )
        val compatUi = settings.apps.associate { it.packageName to compat.ui(it.packageName) }
        _ui.value = UiState(
            settings = settings,
            runtime = rt,
            perms = PermissionUi(perms),
            power = power.ui,
            apps = apps,
            compat = compatUi,
            calibration = calibrator.ui,
            eyeTest = eyeTester.ui,
            personal = personal,
            stats = statsUi(),
            night = night.ui,
            diag = diagnostics(),
            nowMs = wall,
            clock = LocalTime.now().let { String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute) },
        )
        notifyState()
    }

    private fun statsUi(): StatsUi {
        val lang = strings().lang
        val locale = if (lang == Strings.Lang.TR) Locale("tr") else Locale.ENGLISH
        val today = LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val series = days.map { ledger.day(it.toString()) }
        return StatsUi(
            today = series.last(),
            week = series.fold(DayStats(), UsageLedger.Companion::merge),
            series = series,
            seriesLabels = days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) },
        )
    }

    private fun diagnostics(): DiagnosticsUi {
        val bat = device.battery()
        val today = ledger.day(dayKey())
        val duty = if (today.activeMs > 0) (today.cameraMs * 100 / today.activeMs).toInt().coerceIn(0, 100) else 0
        @Suppress("DEPRECATION")
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
        return DiagnosticsUi(
            appVersion = version,
            androidSdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            serviceRunning = service?.get() != null,
            accessibilityConnected = Signals.accessibility != null,
            listenerConnected = Signals.listenerConnected,
            overlayKind = windows.kind,
            frameP50Ms = tracking.frameP50,
            frameP95Ms = tracking.frameP95,
            cameraDutyPercent = duty,
            drain = batteryLedger.compare(),
            batteryPercent = bat.percent,
            charging = bat.charging,
            thermal = device.thermal().name.lowercase(),
            powerSave = device.powerSave(),
            cameraError = faces?.lastError,
        )
    }

    /** A plain-text report for bug reports: versions, permissions, counters. No app names, no content. */
    private fun report(sessionOutline: String? = null): String {
        val d = diagnostics()
        val week = (0..6).map { ledger.day(LocalDate.now().minusDays(it.toLong()).toString()) }.fold(DayStats(), UsageLedger.Companion::merge)
        val verified = settings.apps.count { compat.ui(it.packageName).status == com.eyeblink.core.playback.CompatStatus.VERIFIED }
        return buildString {
            appendLine("EyeBlink ${d.appVersion}  Android API ${d.androidSdk}  ${d.device}")
            appendLine("mode=${settings.mode}  camera=${settings.camera.tracking}  rest=${settings.rest.style}")
            appendLine("accessibility=${d.accessibilityConnected}  mediaSessions=${d.listenerConnected}  overlay=${d.overlayKind.ifEmpty { "none" }}")
            appendLine("permissions=${perms.joinToString { it.name }}")
            appendLine("power=${power.ui.status.kind}/${power.ui.status.available}  lsposedReplied=${lsposed.everReplied}")
            appendLine("selectedApps=${settings.apps.size}  playbackVerified=$verified")
            appendLine("pauseMethods=${compat.available(fresh = true).joinToString { it.name }}  powerDetail=${power.ui.status.detail ?: "-"}")
            lastBreak?.let { br ->
                appendLine("lastBreak: kind=${br.kind} outcome=${br.outcome} strategy=${br.strategy ?: "-"} pause=${br.pause} resume=${br.resume} error=${br.error ?: "-"}")
            }
            appendLine("week: micro=${week.microShown} rests=${week.restsCompleted} skipped=${week.restsSkipped} aborted=${week.aborted} failed=${week.failed}")
            appendLine("pauses: verified=${week.pausesVerified} unverified=${week.pausesUnverified} failed=${week.pausesFailed}")
            appendLine("resumes: ok=${week.resumesOk} refused=${week.resumesRefused} failed=${week.resumesFailed}")
            appendLine("camera: p50=${d.frameP50Ms}ms p95=${d.frameP95Ms}ms duty=${d.cameraDutyPercent}%  model=${faces?.ready}  lastError=${d.cameraError ?: "none"}")
            appendLine("battery=${d.batteryPercent}% charging=${d.charging} thermal=${d.thermal} powerSave=${d.powerSave}")
            if (sessionOutline != null) {
                appendLine("media sessions as the shell sees them (names and titles removed):")
                appendLine(sessionOutline)
            }
        }
    }

    // ── small helpers ────────────────────────────────────────────

    private fun loadApps() {
        appsLoadedMs = nowMs()
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = try {
            @Suppress("DEPRECATION") pm.queryIntentActivities(launcher, 0)
        } catch (e: Exception) { emptyList() }
        val rank = KnownApps.catalog.map { it.packageName }.withIndex().associate { it.value to it.index }
        apps = installed
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != app.packageName }
            .map { (pkg, label) -> AppChoiceUi(pkg, KnownApps.find(pkg)?.label ?: label, KnownApps.categoryOf(pkg), KnownApps.find(pkg) != null) }
            .sortedWith(compareBy<AppChoiceUi> { !it.known }.thenBy { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
    }

    private fun labelOf(pkg: String?): String? {
        pkg ?: return null
        apps.firstOrNull { it.packageName == pkg }?.let { return it.label }
        return KnownApps.find(pkg)?.label ?: runCatching {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    private fun dayKey(): String = LocalDate.now().toString()

    private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, "boot_count", 0)

    private fun systemReducedMotion(): Boolean =
        Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val TAG = "EyeBlink"
        const val IDLE_MS = 1_000L
        const val UI_MS = 250L
        const val MIN_SLEEP_MS = 50L
        const val PERMS_MS = 1_500L
        const val BATTERY_SAMPLE_MS = 5 * 60_000L
        const val LEDGER_SAVE_MS = 30_000L
    }
}

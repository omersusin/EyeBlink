package com.eyeblink.app.engine

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.eyeblink.app.access.MediaSessions
import com.eyeblink.app.platform.Access
import com.eyeblink.app.platform.DeviceState
import com.eyeblink.app.platform.Signals
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.playback.AudioFocusBackend
import com.eyeblink.app.playback.GestureTapBackend
import com.eyeblink.app.playback.LsposedBackend
import com.eyeblink.app.playback.MediaKeyBackend
import com.eyeblink.app.playback.MediaSessionBackend
import com.eyeblink.app.power.PowerController
import com.eyeblink.app.store.Stores
import com.eyeblink.app.ui.state.CompatUi
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.playbackVerdict
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.playback.CompatOutcome
import com.eyeblink.core.playback.CompatPolicy
import com.eyeblink.core.playback.CompatRecord
import com.eyeblink.core.playback.CompatStatus
import com.eyeblink.core.playback.CompatTester
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.playback.StrategyTestResult
import com.eyeblink.core.privileged.Capability
import com.eyeblink.core.privileged.PrivilegedPlaybackBackend
import com.eyeblink.core.privileged.ShellExecutor
import com.eyeblink.core.privileged.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What EyeBlink knows about pausing each app's video, and the single place that decides whether
 * a pause may be attempted at all.
 *
 * The rule is the spec's: nothing is paused without the pause being *checked*, and a strategy is
 * called supported only after it has proved itself on this phone, for this app, at this app version,
 * through the same pause-verify-resume protocol real breaks use. A strategy that reads the player
 * back (a media session, through notification access, root or Shizuku) may be tried by a real
 * break before that: the break's own check is the proof, so nobody has to press a test button
 * first. Real breaks keep the record up to date, so a strategy that stops working switches itself
 * off after three failures in a row and the app goes back to "blink, don't touch the player".
 */
class CompatController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val stores: Stores,
    private val settings: () -> EyeBlinkSettings,
    private val sessions: MediaSessions,
    private val device: DeviceState,
    private val power: PowerController,
    private val lsposed: LsposedBackend?,
    private val foreground: () -> String?,
    /** Tells the user what to do while a test runs (it needs their app in front and playing). */
    private val guide: (String?, CompatOutcomeSummary?) -> Unit,
    private val onChange: () -> Unit,
) {
    private val records = HashMap(stores.loadCompat())
    private val lastResults = HashMap<String, List<StrategyTestResult>>()

    private val mediaSession = MediaSessionBackend(sessions)
    private val mediaKey = MediaKeyBackend(app)
    private val audioFocus = AudioFocusBackend(app)
    private val gesture = GestureTapBackend(app)
    private val privileged = PrivilegedPlaybackBackend(
        ShellExecutor { command, timeoutMs -> power.executor?.run(command, timeoutMs) ?: ShellResult.failure("no privileged shell") },
    )

    private var availableCache: Set<Strategy> = emptySet()
    private var availableAtMs = Long.MIN_VALUE
    @Volatile var testing: String? = null
        private set

    /**
     * What the phone can offer right now. It is worked out from the live state of the permissions and of the
     * privileged shell, not remembered: root or Shizuku finish connecting a moment after the app starts, and a list
     * taken at start-up would never have heard of them.
     */
    fun available(fresh: Boolean = false): Set<Strategy> {
        val now = SystemClock.elapsedRealtime()
        if (!fresh && now - availableAtMs < AVAILABLE_TTL_MS) return availableCache
        availableAtMs = now
        availableCache = buildSet {
            if (Access.listenerEnabled(app)) add(Strategy.MEDIA_SESSION)
            if (power.status.can(Capability.MEDIA_CONTROL)) add(Strategy.PRIVILEGED_SESSION)
            if (lsposed?.installed() == true) add(Strategy.LSPOSED_HOOK)
            add(Strategy.MEDIA_KEY)
            add(Strategy.AUDIO_FOCUS)
            if (Signals.accessibility != null) add(Strategy.A11Y_GESTURE)
        }
        return availableCache
    }

    /** Forgets the remembered list; kept for the call sites that know something just changed. */
    fun refreshAvailability() {
        availableAtMs = Long.MIN_VALUE
    }

    private fun backend(s: Strategy): PlaybackBackend? = when (s) {
        Strategy.MEDIA_SESSION -> mediaSession
        Strategy.PRIVILEGED_SESSION -> privileged
        Strategy.LSPOSED_HOOK -> lsposed
        Strategy.MEDIA_KEY -> mediaKey
        Strategy.AUDIO_FOCUS -> audioFocus
        Strategy.A11Y_GESTURE -> gesture
        // Reading and tapping a node needs permission to read window content, which EyeBlink never asks for.
        Strategy.A11Y_NODE -> null
    }

    /** The backend a break should use for [target]: a proven one, else one worth trying, else null (blink only). */
    fun backendFor(target: String): PlaybackBackend? = backendsFor(target).firstOrNull()

    /** Every backend a break may use for [target], in the order to try them (see [CompatPolicy.candidates]). */
    fun backendsFor(target: String): List<PlaybackBackend> =
        CompatPolicy.candidates(records[target], available(fresh = true), settings().playback, versionOf(target).first)
            .mapNotNull { backend(it) }

    /** Whether a break for [target] could pause its video; asked every turn of the loop, so it may use a half-second-old list. */
    fun canPause(target: String?): Boolean = target != null && bestFor(target, fresh = false) != null

    private fun bestFor(target: String, fresh: Boolean): Strategy? =
        CompatPolicy.best(records[target], available(fresh), settings().playback, versionOf(target).first)

    fun ui(target: String): CompatUi {
        val rec = records[target]
        val p = settings().playback
        val (code, name) = versionOf(target)
        val avail = available()
        val stat = rec?.stats?.values?.maxByOrNull { maxOf(it.lastTestedMs, it.lastOkMs) }
        return CompatUi(
            status = CompatPolicy.status(rec, avail, p, code),
            strategy = CompatPolicy.proven(rec, avail, p, code),
            trial = if (CompatPolicy.proven(rec, avail, p, code) == null) CompatPolicy.trial(rec, avail, p, code) else null,
            lastTestedMs = stat?.let { maxOf(it.lastTestedMs, it.lastOkMs) } ?: 0L,
            available = avail.filter { CompatPolicy.allowedBySettings(it, p) }.toSet(),
            results = lastResults[target].orEmpty(),
            testing = testing == target,
            versionName = name,
        )
    }

    /** Folds the outcome of a real break into the record, so a strategy is proven by use and a failing one is noticed. */
    fun recordReal(report: BreakReport) {
        val target = report.target ?: return
        val verdict = report.playbackVerdict() ?: return
        fold(target, verdict.strategy, verdict.pauseOk, verdict.resumeOk, report.endedMs - report.startedMs, fromTest = false)
        refreshAvailability()
        onChange()
    }

    /**
     * Runs the on-device test for [target]. The app has to be in front with a video playing, so the
     * test opens it, tells the user what to do, and waits for playback before touching anything.
     */
    fun test(target: String) {
        if (testing != null) return
        testing = target
        lastResults[target] = emptyList()
        onChange()
        scope.launch {
            var summary = CompatOutcomeSummary.NO_PLAYBACK
            try {
                app.packageManager.getLaunchIntentForPackage(target)?.let { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                guide(target, null)

                val playing = withTimeoutOrNull(WAIT_FOR_PLAYBACK_MS) {
                    while (!(foreground() == target && isPlaying(target))) delay(500)
                    true
                }
                if (playing != true) return@launch

                val p = settings().playback
                val backends = available(fresh = true).filter { CompatPolicy.allowedBySettings(it, p) }.mapNotNull { backend(it) }
                if (backends.isEmpty()) { summary = CompatOutcomeSummary.NO_STRATEGY; return@launch }

                val report = CompatTester().run(target, backends, ::nowMs, foreground) { r ->
                    lastResults[target] = lastResults[target].orEmpty() + r
                    onChange()
                }
                for (r in report.results) {
                    when (r.outcome) {
                        CompatOutcome.NOT_PLAYING, CompatOutcome.NO_SESSION, CompatOutcome.UNREADABLE, CompatOutcome.REFUSED -> Unit
                        CompatOutcome.PASSED -> fold(target, r.strategy, true, true, r.pauseLatencyMs, fromTest = true)
                        CompatOutcome.RESUME_FAILED -> fold(target, r.strategy, true, false, r.pauseLatencyMs, fromTest = true)
                        CompatOutcome.PAUSE_NOT_VERIFIED, CompatOutcome.ERROR -> fold(target, r.strategy, false, false, r.pauseLatencyMs, fromTest = true)
                    }
                }
                summary = when {
                    report.anyPassed -> CompatOutcomeSummary.VERIFIED
                    report.leftPaused -> CompatOutcomeSummary.LEFT_PAUSED
                    else -> CompatOutcomeSummary.FAILING
                }
            } finally {
                testing = null
                guide(null, summary)
                onChange()
            }
        }
    }

    /** Whether [pkg] is playing: its media session says so, else the privileged shell's reading, else any sound at all. */
    private suspend fun isPlaying(pkg: String): Boolean {
        sessions.isPlaying(pkg)?.let { return it }
        if (power.status.can(Capability.MEDIA_CONTROL)) {
            val seen = privileged.snapshot(pkg).state
            if (seen != PlayState.NONE && seen != PlayState.UNKNOWN) return seen == PlayState.PLAYING
        }
        return device.audioActive()
    }

    /** What the privileged shell sees of the media sessions, shaped for a bug report and stripped of names and titles. */
    suspend fun sessionOutline(): String? = if (power.status.can(Capability.MEDIA_CONTROL)) privileged.outline() else null

    private fun fold(target: String, strategy: Strategy, pauseOk: Boolean, resumeOk: Boolean, latencyMs: Long, fromTest: Boolean) {
        val (code, name) = versionOf(target)
        val base = records[target] ?: CompatRecord(target, code, name)
        records[target] = CompatPolicy.record(
            base, strategy, pauseOk, resumeOk, latencyMs,
            nowMs = System.currentTimeMillis(), fromTest = fromTest, versionCode = code, versionName = name,
        )
        stores.saveCompat(records)
    }

    @Suppress("DEPRECATION")
    private fun versionOf(pkg: String): Pair<Long, String> = try {
        val info = app.packageManager.getPackageInfo(pkg, 0)
        (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()) to (info.versionName ?: "")
    } catch (e: Exception) {
        0L to ""
    }

    private companion object {
        const val WAIT_FOR_PLAYBACK_MS = 60_000L
        const val AVAILABLE_TTL_MS = 500L
    }
}

/** How a compatibility test ended, for the notification that closes it. */
enum class CompatOutcomeSummary { VERIFIED, FAILING, LEFT_PAUSED, NO_PLAYBACK, NO_STRATEGY }

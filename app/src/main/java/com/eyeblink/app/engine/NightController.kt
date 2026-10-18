package com.eyeblink.app.engine

import android.app.Application
import android.content.ContentResolver
import android.content.res.Resources
import android.provider.Settings
import com.eyeblink.app.platform.Access
import com.eyeblink.app.platform.AmbientLight
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.ui.state.NightUi
import com.eyeblink.core.model.NightSettings
import com.eyeblink.core.night.NightInputs
import com.eyeblink.core.night.NightPlanner
import com.eyeblink.core.night.NightTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Smart night mode: eases Android's own Night Light and (optionally) the screen brightness toward
 * what [NightPlanner] says, and gets out of the way the moment the user takes over.
 *
 * It writes only values the user has opted into and granted access for, never goes below the
 * brightness floor, does not touch brightness at all while adaptive brightness is on, and treats
 * any brightness value it did not write as "the user changed it": automation then stays off for the
 * configured back-off. When night ends it puts back only what it changed.
 *
 * Night Light uses the standard `night_display_*` secure settings, which need the one-time
 * `WRITE_SECURE_SETTINGS` grant. Some manufacturers use other keys; there it reports itself
 * unsupported instead of guessing.
 */
class NightController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settings: () -> NightSettings,
    private val light: AmbientLight,
    private val onChange: () -> Unit,
) {
    @Volatile var ui: NightUi = NightUi()
        private set

    private val resolver: ContentResolver = app.contentResolver
    private var job: Job? = null

    /** The user hit emergency stop: stay hands-off until the night settings are touched again. */
    @Volatile var suspended = false

    private var manualUntilMs = 0L
    private var lastWrittenPercent: Int? = null
    private var ramping: Float? = null            // our brightness in percent while we are driving it
    private var originalPercent: Int? = null      // what the user had before we started
    private var weTurnedNightLightOn = false
    private var lastTickMs = 0L

    // What this controller has changed outlives the process: after a restart the next one can still put it back.
    private val memory = app.getSharedPreferences("eyeblink.night.restore", android.content.Context.MODE_PRIVATE)
    private var persisted = Triple(false, null as Int?, null as Int?)

    init {
        weTurnedNightLightOn = memory.getBoolean(KEPT_NIGHT_LIGHT, false)
        originalPercent = memory.getInt(KEPT_ORIGINAL, -1).takeIf { it >= 0 }
        lastWrittenPercent = memory.getInt(KEPT_WRITTEN, -1).takeIf { it >= 0 }
        persisted = Triple(weTurnedNightLightOn, originalPercent, lastWrittenPercent)
    }

    private fun persist() {
        val now = Triple(weTurnedNightLightOn, originalPercent, lastWrittenPercent)
        if (now == persisted) return
        persisted = now
        memory.edit()
            .putBoolean(KEPT_NIGHT_LIGHT, now.first)
            .putInt(KEPT_ORIGINAL, now.second ?: -1)
            .putInt(KEPT_WRITTEN, now.third ?: -1)
            .apply()
    }

    /** Starts or stops the background loop to match the settings. */
    fun sync() {
        val s = settings()
        if (s.enabled && !suspended) {
            if (job?.isActive != true) job = scope.launch { loop() }
        } else {
            job?.cancel()
            job = null
            light.stop()
            // Switched off, or stopped by the emergency stop: either way nothing this controller changed stays changed.
            restore()
            publish(null, null)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        light.stop()
    }

    private suspend fun loop() {
        lastTickMs = nowMs()
        while (scope.isActive) {
            val moving = tick()
            delay(if (moving) 1_000L else 5_000L)
        }
    }

    /** One step. Returns true while a brightness ramp is still in progress. */
    private fun tick(): Boolean {
        val s = settings().validated()
        val now = nowMs()
        val dt = (now - lastTickMs).coerceIn(0L, 10_000L)
        lastTickMs = now

        if (s.adaptToAmbientLight) light.start() else light.stop()
        val lux = light.lux

        val minute = LocalTime.now().let { it.hour * 60 + it.minute }
        val target = NightPlanner.target(NightInputs(s, minute, now, lux, manualUntilMs))

        var moving = false
        if (Access.writeSettings(app) && autoBrightnessOff()) moving = driveBrightness(s, target, now, dt)
        if (Access.secureSettings(app)) driveNightLight(s, target)
        persist()
        publish(target, lux)
        return moving
    }

    // ── brightness ──────────────────────────────────────────────

    private fun autoBrightnessOff(): Boolean = try {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    } catch (e: Exception) {
        false
    }

    private fun currentPercent(): Int? = try {
        (Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f).roundToInt()
    } catch (e: Exception) {
        null
    }

    private fun driveBrightness(s: NightSettings, target: NightTarget, now: Long, dt: Long): Boolean {
        val current = currentPercent() ?: return false

        // Somebody else moved it since we last wrote: they are in charge for a while.
        if (NightPlanner.isManualChange(current, lastWrittenPercent)) {
            manualUntilMs = now + s.manualOverrideBackoffMin * 60_000L
            lastWrittenPercent = null
            ramping = null
            originalPercent = null
            return false
        }

        val nightPercent = target.brightnessPercent
        val want: Float = when {
            target.active && nightPercent != null -> {
                if (originalPercent == null) originalPercent = current
                nightPercent.toFloat()
            }
            // Night is over: hand back what we took, but only if we are the ones holding it down.
            originalPercent != null && lastWrittenPercent != null -> originalPercent!!.toFloat()
            else -> return false
        }

        val from = ramping ?: current.toFloat()
        val next = NightPlanner.ramp(from, want, dt.coerceAtLeast(1L), s.rampSec * 1_000L)
        ramping = next
        val percent = next.roundToInt().coerceIn(if (target.active) s.brightnessFloorPercent else 1, 100)
        if (percent != current) {
            try {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255f / 100f).roundToInt().coerceIn(1, 255))
                lastWrittenPercent = percent
            } catch (e: Exception) {
                return false
            }
        }
        val arrived = next == want
        if (arrived && !target.active) {
            // Fully handed back.
            originalPercent = null
            lastWrittenPercent = null
            ramping = null
        }
        return !arrived
    }

    // ── night light ─────────────────────────────────────────────

    private fun driveNightLight(s: NightSettings, target: NightTarget) {
        try {
            val on = Settings.Secure.getInt(resolver, NIGHT_ACTIVATED, 0) == 1
            if (target.active && target.nightLight == true) {
                val k = (target.warmthKelvin ?: s.warmthKelvin).coerceIn(kelvinMin, kelvinMax)
                if (!on) {
                    if (weTurnedNightLightOn) {
                        // It was on because of us and now it is off: somebody switched it off. They are in charge for a while,
                        // as with the brightness - not switched back on within five seconds.
                        manualUntilMs = nowMs() + s.manualOverrideBackoffMin * 60_000L
                        weTurnedNightLightOn = false
                        return
                    }
                    Settings.Secure.putInt(resolver, NIGHT_ACTIVATED, 1)
                    weTurnedNightLightOn = true
                }
                if (Settings.Secure.getInt(resolver, NIGHT_TEMPERATURE, -1) != k) Settings.Secure.putInt(resolver, NIGHT_TEMPERATURE, k)
            } else if (weTurnedNightLightOn) {
                if (on) Settings.Secure.putInt(resolver, NIGHT_ACTIVATED, 0)
                weTurnedNightLightOn = false
            }
        } catch (e: Exception) {
            // Not granted, or this manufacturer keeps Night Light elsewhere: leave it alone.
        }
    }

    /** Puts back what this controller changed; called when night mode is switched off. */
    private fun restore() {
        if (weTurnedNightLightOn && Access.secureSettings(app)) {
            runCatching { Settings.Secure.putInt(resolver, NIGHT_ACTIVATED, 0) }
            weTurnedNightLightOn = false
        }
        val original = originalPercent
        if (original != null && lastWrittenPercent != null && Access.writeSettings(app)) {
            runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (original * 255f / 100f).roundToInt().coerceIn(1, 255)) }
        }
        originalPercent = null
        lastWrittenPercent = null
        ramping = null
        persist()
    }

    fun publish(target: NightTarget?, lux: Float?) {
        ui = NightUi(
            target = target,
            canWriteSecureSettings = Access.secureSettings(app),
            canWriteSettings = Access.writeSettings(app),
            lux = lux,
            brightnessPercent = currentPercent(),
            manualOverrideUntilMs = if (manualUntilMs > nowMs()) System.currentTimeMillis() + (manualUntilMs - nowMs()) else 0L,
        )
        onChange()
    }

    private val kelvinMin: Int by lazy { systemInt("config_nightDisplayColorTemperatureMin", 2596) }
    private val kelvinMax: Int by lazy { systemInt("config_nightDisplayColorTemperatureMax", 4082) }

    private fun systemInt(name: String, fallback: Int): Int = try {
        val id = Resources.getSystem().getIdentifier(name, "integer", "android")
        if (id != 0) Resources.getSystem().getInteger(id) else fallback
    } catch (e: Exception) {
        fallback
    }

    private companion object {
        const val NIGHT_ACTIVATED = "night_display_activated"
        const val NIGHT_TEMPERATURE = "night_display_color_temperature"
        const val KEPT_NIGHT_LIGHT = "night_light_ours"
        const val KEPT_ORIGINAL = "brightness_before"
        const val KEPT_WRITTEN = "brightness_written"
    }
}

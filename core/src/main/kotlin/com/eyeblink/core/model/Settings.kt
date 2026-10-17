package com.eyeblink.core.model

import com.eyeblink.core.anim.BlinkSpec
import kotlinx.serialization.Serializable

@Serializable
enum class Language { SYSTEM, EN, TR }

/** Which of the two themes the app wears: bright Daylight, deep-indigo Moonlit, or whichever the phone is in. */
@Serializable
enum class Appearance { SYSTEM, LIGHT, DARK }

/** What the user asked for. What is actually possible is negotiated at runtime (see PowerCapabilities). */
@Serializable
enum class OperatingMode { ROOTLESS, SHIZUKU, ROOT }

@Serializable
enum class AppCategory { VIDEO, SOCIAL, READING, OTHER }

@Serializable
enum class RhythmPreset { GENTLE, BALANCED, ACTIVE, CUSTOM }

@Serializable
enum class RestStyle {
    /** The screen stays pure black for the whole rest. */
    BLACKOUT,

    /** A calm countdown that asks the user to look at something far away (20-20-20). */
    DISTANCE_FOCUS,
}

@Serializable
enum class ConfirmMode {
    /** The rest ends by itself when the time is up. */
    AUTO,

    /** A single tap on the screen confirms the user is back. */
    TAP,

    /** One real blink confirms it when the camera is tracking; falls back to a tap. */
    BLINK,
}

/** What to do when playback cannot be paused and verified for the current app. */
@Serializable
enum class NoPauseFallback {
    /** Blink anyway; the content keeps playing underneath. */
    CONTINUE_UNDER,

    /** Wait (bounded) for a natural gap such as a swipe to the next video. */
    WAIT_FOR_TRANSITION,

    /** Do not interrupt this app at all. */
    SKIP,
}

@Serializable
enum class TrackingMode {
    OFF,

    /** Short camera windows at intervals; the cheap default for camera-assisted mode. */
    BURST,

    /** Camera runs whenever a selected app is in the foreground. */
    CONTINUOUS,
}

@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMin: Int = 23 * 60,
    val endMin: Int = 7 * 60,
) {
    /** Handles windows that wrap past midnight. */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val s = startMin.mod(MINUTES_PER_DAY)
        val e = endMin.mod(MINUTES_PER_DAY)
        val m = minuteOfDay.mod(MINUTES_PER_DAY)
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

@Serializable
data class RhythmSettings(
    val preset: RhythmPreset = RhythmPreset.BALANCED,
    val microIntervalSec: Int = 45,
    val restEnabled: Boolean = true,
    val restIntervalMin: Int = 20,
    /** Let observed behaviour, context and session length stretch or shrink the interval. */
    val adaptive: Boolean = true,
    val jitterPercent: Int = 8,
    /** Minimum gap between any two interruptions. */
    val cooldownSec: Int = 10,
    /** Quiet period after a full rest during which no micro blink is shown. */
    val postRestGraceMin: Int = 3,
    val maxMicroPerHour: Int = 90,
    /** When pausing is impossible, wait briefly for a swipe/transition before blinking. */
    val preferTransitions: Boolean = true,
    /** How long soft obstacles (a finger on the screen, a swipe in flight) may delay a break. */
    val softDeferralBudgetSec: Int = 45,
    val quietHours: QuietHours = QuietHours(),
) {
    fun validated(): RhythmSettings = copy(
        microIntervalSec = microIntervalSec.coerceIn(MIN_MICRO_SEC, MAX_MICRO_SEC),
        restIntervalMin = restIntervalMin.coerceIn(MIN_REST_MIN, MAX_REST_MIN),
        jitterPercent = jitterPercent.coerceIn(0, 30),
        cooldownSec = cooldownSec.coerceIn(3, 600),
        postRestGraceMin = postRestGraceMin.coerceIn(0, 30),
        maxMicroPerHour = maxMicroPerHour.coerceIn(6, 360),
        softDeferralBudgetSec = softDeferralBudgetSec.coerceIn(5, 300),
    )

    companion object {
        const val MIN_MICRO_SEC = 5
        const val MAX_MICRO_SEC = 600
        const val MIN_REST_MIN = 5
        const val MAX_REST_MIN = 120

        fun preset(p: RhythmPreset, base: RhythmSettings = RhythmSettings()): RhythmSettings = when (p) {
            RhythmPreset.GENTLE -> base.copy(preset = p, microIntervalSec = 90, restIntervalMin = 30)
            RhythmPreset.BALANCED -> base.copy(preset = p, microIntervalSec = 45, restIntervalMin = 20)
            RhythmPreset.ACTIVE -> base.copy(preset = p, microIntervalSec = 25, restIntervalMin = 15)
            RhythmPreset.CUSTOM -> base.copy(preset = p)
        }
    }
}

@Serializable
data class RestSettings(
    val style: RestStyle = RestStyle.DISTANCE_FOCUS,
    val durationSec: Int = 20,
    val confirm: ConfirmMode = ConfirmMode.AUTO,
    /** Blackout rests may be longer than a distance-focus countdown. */
    val blackoutDurationSec: Int = 30,
) {
    fun validated(): RestSettings = copy(
        durationSec = durationSec.coerceIn(5, 120),
        blackoutDurationSec = blackoutDurationSec.coerceIn(5, 300),
    )

    val effectiveDurationSec: Int
        get() = if (style == RestStyle.BLACKOUT) blackoutDurationSec else durationSec
}

@Serializable
data class CameraSettings(
    val tracking: TrackingMode = TrackingMode.OFF,
    val burstSec: Int = 8,
    val periodSec: Int = 45,
    val targetFps: Int = 15,
) {
    fun validated(): CameraSettings = copy(
        burstSec = burstSec.coerceIn(4, 60),
        periodSec = periodSec.coerceIn(15, 600),
        targetFps = targetFps.coerceIn(8, 30),
    )
}

@Serializable
data class PlaybackSettings(
    val pauseDuringBreaks: Boolean = true,
    val resumeAfterBreak: Boolean = true,
    /** Resume only if the break ended within this window of the pause. */
    val maxHoldSec: Int = 60,
    val fallback: NoPauseFallback = NoPauseFallback.CONTINUE_UNDER,
    /** Experimental: tap the video through the accessibility service. Needs audio verification. */
    val allowGestureTap: Boolean = false,
    /** Experimental: hardware-style PAUSE/PLAY key events. */
    val allowMediaKeys: Boolean = false,
    /** Experimental: ask for transient audio focus so well-behaved players pause themselves. */
    val allowAudioFocus: Boolean = false,
) {
    fun validated(): PlaybackSettings = copy(maxHoldSec = maxHoldSec.coerceIn(5, 600))
}

@Serializable
data class NightSettings(
    val enabled: Boolean = false,
    val startMin: Int = 21 * 60,
    val endMin: Int = 6 * 60 + 30,
    val adaptToAmbientLight: Boolean = true,
    val manageNightLight: Boolean = true,
    val manageBrightness: Boolean = false,
    val warmthKelvin: Int = 3400,
    val nightBrightnessPercent: Int = 35,
    /** Brightness is never driven below this, whatever else is configured. */
    val brightnessFloorPercent: Int = 12,
    val rampSec: Int = 25,
    /** After the user changes brightness by hand, stay hands-off for this long. */
    val manualOverrideBackoffMin: Int = 30,
) {
    fun validated(): NightSettings {
        val floor = brightnessFloorPercent.coerceIn(1, 50)
        return copy(
            warmthKelvin = warmthKelvin.coerceIn(2300, 6500),
            brightnessFloorPercent = floor,
            nightBrightnessPercent = nightBrightnessPercent.coerceIn(floor, 100),
            rampSec = rampSec.coerceIn(2, 300),
            manualOverrideBackoffMin = manualOverrideBackoffMin.coerceIn(1, 480),
        )
    }
}

@Serializable
data class BatterySettings(
    val saverBelowPercent: Int = 20,
    val cameraOffOnSaver: Boolean = true,
    val thermalBackoff: Boolean = true,
) {
    fun validated(): BatterySettings = copy(saverBelowPercent = saverBelowPercent.coerceIn(5, 60))
}

/** Durations of the guided calibration. All adjustable; the user can also skip any phase. */
@Serializable
data class CalibrationSettings(
    val videoSec: Int = 60,
    val readingSec: Int = 60,
    val deliberateBlinks: Int = 5,
) {
    fun validated(): CalibrationSettings = copy(
        videoSec = videoSec.coerceIn(15, 300),
        readingSec = readingSec.coerceIn(15, 300),
        deliberateBlinks = deliberateBlinks.coerceIn(3, 12),
    )
}

@Serializable
data class PrivacySettings(
    val localStats: Boolean = true,
)

/** Rules for one selected app. EyeBlink only ever interrupts apps that have a profile. */
@Serializable
data class AppProfile(
    val packageName: String,
    val label: String = "",
    val enabled: Boolean = true,
    val category: AppCategory = AppCategory.VIDEO,
    /** 50..200. Below 100 means more frequent breaks in this app. */
    val intervalScalePercent: Int = 100,
    val restEnabled: Boolean = true,
    val pauseVideo: Boolean = true,
) {
    fun validated(): AppProfile = copy(intervalScalePercent = intervalScalePercent.coerceIn(50, 200))
}

@Serializable
data class EyeBlinkSettings(
    val onboardingDone: Boolean = false,
    /** English is the app's main language; following the phone's language is a choice, not the default. */
    val language: Language = Language.EN,
    val appearance: Appearance = Appearance.SYSTEM,
    val mode: OperatingMode = OperatingMode.ROOTLESS,
    /** Simple: sensible automatic behaviour. Advanced exposes every knob. */
    val advanced: Boolean = false,
    val blink: BlinkSpec = BlinkSpec(),
    val rhythm: RhythmSettings = RhythmSettings(),
    val rest: RestSettings = RestSettings(),
    val camera: CameraSettings = CameraSettings(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val night: NightSettings = NightSettings(),
    val battery: BatterySettings = BatterySettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val calibration: CalibrationSettings = CalibrationSettings(),
    val apps: List<AppProfile> = emptyList(),
    val startOnBoot: Boolean = false,
) {
    fun validated(): EyeBlinkSettings = copy(
        blink = blink.validated(),
        rhythm = rhythm.validated(),
        rest = rest.validated(),
        camera = camera.validated(),
        playback = playback.validated(),
        night = night.validated(),
        battery = battery.validated(),
        calibration = calibration.validated(),
        apps = apps.map { it.validated() }.distinctBy { it.packageName },
    )

    fun profileFor(packageName: String?): AppProfile? =
        if (packageName == null) null else apps.firstOrNull { it.packageName == packageName && it.enabled }

    fun withApp(profile: AppProfile): EyeBlinkSettings =
        copy(apps = apps.filterNot { it.packageName == profile.packageName } + profile)

    fun withoutApp(packageName: String): EyeBlinkSettings =
        copy(apps = apps.filterNot { it.packageName == packageName })
}

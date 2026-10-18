package com.eyeblink.core.calibration

import com.eyeblink.core.model.AppCategory
import kotlinx.serialization.Serializable

/**
 * The situations EyeBlink keeps separate blink models for. People blink differently
 * watching a clip, reading text, thumbing through a feed, or in a dim room.
 */
@Serializable
enum class ContextProfile { VIDEO, READING, SOCIAL, LOW_LIGHT }

object ContextClassifier {

    /** Below this ambient light (lux) the situation is treated as low-light. */
    const val LOW_LIGHT_LUX = 15f

    /**
     * Picks the profile to schedule and learn under.
     *
     * Low light wins because dim rooms change blinking and tracking quality regardless of
     * what is on screen. Otherwise the app category decides, and an unknown app with active
     * media counts as video.
     */
    fun classify(category: AppCategory, mediaPlaying: Boolean, lux: Float?): ContextProfile = when {
        lux != null && lux < LOW_LIGHT_LUX -> ContextProfile.LOW_LIGHT
        category == AppCategory.READING -> ContextProfile.READING
        category == AppCategory.SOCIAL -> ContextProfile.SOCIAL
        category == AppCategory.VIDEO -> ContextProfile.VIDEO
        mediaPlaying -> ContextProfile.VIDEO
        else -> ContextProfile.SOCIAL
    }
}

/** Where a number shown to the user came from. The UI must never blur these together. */
enum class Provenance {
    /** Measured on this device from this user's own blinks in this context. */
    OBSERVED,

    /** Derived from the user's general profile with a context prior; not measured here. */
    ESTIMATED,

    /** Nothing personal is known yet; defaults apply. */
    NONE,
}

data class ProvenancedBaseline(val baseline: BlinkBaseline, val provenance: Provenance)

/**
 * The personal model: a general baseline plus one per context.
 *
 * A context that has never been observed is *estimated* from the general baseline using a
 * small prior, and is reported as such. Only observations promote it to OBSERVED.
 */
@Serializable
data class PersonalProfile(
    val general: BlinkBaseline = BlinkBaseline(),
    val byContext: Map<ContextProfile, BlinkBaseline> = emptyMap(),
    val calibratedAtMs: Long = 0L,
    val thresholds: BlinkThresholds = BlinkThresholds(),
) {
    val isCalibrated: Boolean get() = general.hasSignal

    fun baselineFor(context: ContextProfile): ProvenancedBaseline {
        byContext[context]?.takeIf { it.hasSignal }?.let { return ProvenancedBaseline(it, Provenance.OBSERVED) }
        if (!general.hasSignal) return ProvenancedBaseline(BlinkBaseline(), Provenance.NONE)
        val factor = IBI_PRIOR.getValue(context)
        val estimated = general.copy(medianIbiMs = (general.medianIbiMs * factor).toLong())
        return ProvenancedBaseline(estimated, Provenance.ESTIMATED)
    }

    fun withContext(context: ContextProfile, baseline: BlinkBaseline): PersonalProfile =
        copy(byContext = byContext + (context to baseline))

    companion object {
        /**
         * Prior multipliers on the gap between blinks. People tend to blink less while
         * reading and in dim light. These are starting points only and are always labelled
         * as estimates in the UI.
         */
        val IBI_PRIOR: Map<ContextProfile, Float> = mapOf(
            ContextProfile.VIDEO to 1.15f,
            ContextProfile.SOCIAL to 1.0f,
            ContextProfile.READING to 1.5f,
            ContextProfile.LOW_LIGHT to 1.25f,
        )
    }
}

package com.eyeblink.core.calibration

/**
 * Orchestrates the three-phase guided calibration.
 *
 * Phase A (video) and Phase B (reading) collect *spontaneous* blinks. Phase C
 * collects *instructed* blinks, which the user performs on request and which
 * must never pollute normal-behaviour learning. Keeping those separate is a
 * spec requirement, and it is enforced here by tagging rather than by
 * convention.
 *
 * Beyond the blinks themselves the session keeps the numbers the spec asks for -
 * tracked time, tracking confidence, image brightness, and the eye-openness range
 * seen open versus closed - so the result can personalise the detector thresholds and
 * seed separate video and reading baselines.
 *
 * The session also owns blink detection ([process]), so that what counts as a blink in each phase is
 * decided here, in one place that can be tested end to end. In the two timed phases that is the ordinary
 * detector. In the deliberate phase it is a more forgiving one - closures of up to three seconds, shallower
 * ones allowed - and it keeps easing while the person is trying and nothing counts: a person whose lids
 * close only a little (narrow eyes, heavy lids) must still be able to finish the test, and the depth they
 * reach is exactly what their own thresholds are then fitted to.
 *
 * Contains no Android types, so the phase logic is unit testable.
 */
class CalibrationSession(
    private val videoMs: Long = DEFAULT_VIDEO_MS,
    private val readingMs: Long = DEFAULT_READING_MS,
    private val deliberateBlinksRequired: Int = DEFAULT_DELIBERATE_BLINKS,
    /** How willing the timed phases are to call a closure a blink; the person's own setting. */
    private val sensitivity: Float = 1f,
) {
    enum class Phase { VIDEO, READING, DELIBERATE, COMPLETE }

    /** What the person could do to make the deliberate test count, when it has not been counting. */
    enum class Hint { NONE, CLOSE_MORE, EASED }

    var phase: Phase = Phase.VIDEO
        private set

    private var phaseStartedMs: Long = 0L
    private var deliberateSeen: Int = 0

    /** Natural blinks from Phases A and B. */
    val spontaneous: MutableList<BlinkObservation> = mutableListOf()

    /** Blinks the user performed on request in Phase C. */
    val instructed: MutableList<BlinkObservation> = mutableListOf()

    private val videoBlinks = mutableListOf<BlinkObservation>()
    private val readingBlinks = mutableListOf<BlinkObservation>()

    private class Tally {
        var trackedMs = 0L
        var confidenceWeighted = 0.0
        var lumaWeighted = 0.0
        var lastSampleMs: Long? = null
    }

    private val tallies = Phase.entries.associateWith { Tally() }
    private val openLevels = ArrayList<Float>()

    private val detector = BlinkDetector(naturalSettings())
    private var easeStep = 0
    private var trackedSinceCountMs = 0L
    private var peakSinceCount = 0f
    private var easedAtMs: Long? = null
    private var lastSampleAtMs = 0L

    /** How closed the eyes are right now as a share of their own open level (0 = open, 1 = shut); for a live display. */
    val closure: Float get() = detector.closure

    /** The depth at which the detector currently starts counting a closure, in the same terms as [closure]. */
    val startDepth: Float get() = detector.settings.start

    /** A suggestion for the person, or [Hint.NONE]. Only the deliberate phase has any. */
    val hint: Hint
        get() {
            if (phase != Phase.DELIBERATE) return Hint.NONE
            val eased = easedAtMs
            if (eased != null && lastSampleAtMs - eased < HINT_HOLD_MS) return Hint.EASED
            if (trackedSinceCountMs >= CLOSE_MORE_AFTER_MS && peakSinceCount < detector.settings.start) return Hint.CLOSE_MORE
            return Hint.NONE
        }

    /** Blinks still needed in Phase C. */
    val deliberateRemaining: Int get() = (deliberateBlinksRequired - deliberateSeen).coerceAtLeast(0)

    /** 0..1 progress through the current phase, for a progress indicator. */
    fun phaseProgress(nowMs: Long): Float {
        val total = phaseDuration(phase)
        if (total <= 0L) return 1f
        val elapsed = (nowMs - phaseStartedMs).coerceIn(0L, total)
        return elapsed.toFloat() / total
    }

    /** Remaining time in the current timed phase, or null for Phase C. */
    fun remainingMs(nowMs: Long): Long? {
        val total = phaseDuration(phase)
        if (total <= 0L) return null
        return (total - (nowMs - phaseStartedMs)).coerceAtLeast(0L)
    }

    /** Starts (or restarts) from Phase A. A retry begins from nothing: no earlier data survives. */
    fun start(nowMs: Long) {
        phase = Phase.VIDEO
        phaseStartedMs = nowMs
        deliberateSeen = 0
        spontaneous.clear()
        instructed.clear()
        videoBlinks.clear()
        readingBlinks.clear()
        openLevels.clear()
        tallies.values.forEach {
            it.trackedMs = 0; it.confidenceWeighted = 0.0; it.lumaWeighted = 0.0; it.lastSampleMs = null
        }
        detector.forget()
        detector.configure(naturalSettings())
        easeStep = 0
        trackedSinceCountMs = 0L
        peakSinceCount = 0f
        easedAtMs = null
        lastSampleAtMs = nowMs
    }

    /**
     * Advances timed phases. Phase C ends on blink count, not a timer.
     * @return true if the phase changed.
     */
    fun onTick(nowMs: Long): Boolean {
        when (phase) {
            Phase.VIDEO -> if (nowMs - phaseStartedMs >= videoMs) return advance(Phase.READING, nowMs)
            Phase.READING -> if (nowMs - phaseStartedMs >= readingMs) return advance(Phase.DELIBERATE, nowMs)
            Phase.DELIBERATE, Phase.COMPLETE -> Unit
        }
        return false
    }

    /** The user chose to skip the current phase. Skipping the last one finishes the session. */
    fun skipPhase(nowMs: Long) {
        when (phase) {
            Phase.VIDEO -> advance(Phase.READING, nowMs)
            Phase.READING -> advance(Phase.DELIBERATE, nowMs)
            Phase.DELIBERATE -> advance(Phase.COMPLETE, nowMs)
            Phase.COMPLETE -> Unit
        }
    }

    /**
     * Feeds one camera sample. Only trackable samples count toward tracked time, quality and
     * the open-eye range; a dark or low-confidence frame is simply not evidence.
     */
    fun onSample(sample: EyeOpennessSample) {
        val tally = tallies.getValue(phase)
        if (!sample.isTrackable) {
            tally.lastSampleMs = null
            return
        }
        val prev = tally.lastSampleMs
        tally.lastSampleMs = sample.timestampMs
        if (prev != null) {
            val dt = sample.timestampMs - prev
            if (dt in 1..MAX_SAMPLE_GAP_MS) {
                tally.trackedMs += dt
                tally.confidenceWeighted += sample.confidence.toDouble() * dt
                tally.lumaWeighted += sample.illuminance.toDouble() * dt
            }
        }
        if (phase == Phase.VIDEO || phase == Phase.READING) {
            openLevels += sample.openness
            if (openLevels.size > MAX_OPEN_SAMPLES) {
                // Keep every other sample: bounded memory, same distribution.
                val kept = openLevels.filterIndexed { i, _ -> i % 2 == 0 }
                openLevels.clear()
                openLevels.addAll(kept)
            }
        }
    }

    /**
     * The whole path from a camera sample to a counted blink: tallies the sample ([onSample]), runs it through
     * the detector for the current phase, and records what that finds ([onBlink]).
     * @return the blink that was counted on this sample, or null
     */
    fun process(sample: EyeOpennessSample): BlinkObservation? {
        onSample(sample)
        if (!sample.isTrackable) return null
        val blink = detector.onSample(sample)
        if (phase == Phase.DELIBERATE) trackDeliberate(sample, blink)
        lastSampleAtMs = sample.timestampMs
        return if (blink != null && onBlink(blink)) blink else null
    }

    /**
     * Records a blink into the current phase.
     * @return true if the blink was accepted for learning.
     */
    fun onBlink(blink: BlinkObservation): Boolean {
        if (phase == Phase.COMPLETE) return false
        if (phase == Phase.DELIBERATE) {
            if (blink.isLearnable.not() && blink.confidence < EyeOpennessSample.MIN_CONFIDENCE) {
                return false // too unreliable to count as a requested blink
            }
            instructed += blink.copy(instructed = true)
            deliberateSeen++
            trackedSinceCountMs = 0L
            peakSinceCount = 0f
            if (deliberateSeen >= deliberateBlinksRequired) {
                phase = Phase.COMPLETE
            }
            return true
        }
        if (!blink.isLearnable) return false
        spontaneous += blink
        if (phase == Phase.VIDEO) videoBlinks += blink else readingBlinks += blink
        return true
    }

    /**
     * Builds the personal profile from the spontaneous phases only, then reports how much
     * signal was really collected so the UI can be honest about it.
     */
    fun result(): CalibrationResult {
        val general = BlinkStatistics.summarise(spontaneous)
        val video = BlinkStatistics.summarise(videoBlinks)
        val reading = BlinkStatistics.summarise(readingBlinks)

        val byContext = buildMap {
            if (video.hasSignal) put(ContextProfile.VIDEO, video)
            if (reading.hasSignal) put(ContextProfile.READING, reading)
        }

        val open = openLevel()
        val closed = closedLevel()
        val depth = fullDepth(open)
        val thresholds = BlinkThresholds.fromCalibration(open, depth)

        val phaseStats = Phase.entries.filter { it == Phase.VIDEO || it == Phase.READING }.associateWith { p ->
            val list = if (p == Phase.VIDEO) videoBlinks else readingBlinks
            val t = tallies.getValue(p)
            PhaseStats(
                blinks = list.size,
                trackedMs = t.trackedMs,
                meanConfidence = if (t.trackedMs > 0) (t.confidenceWeighted / t.trackedMs).toFloat() else 0f,
                meanLuma = if (t.trackedMs > 0) (t.lumaWeighted / t.trackedMs).toFloat() else 0f,
                blinksPerMinute = if (t.trackedMs > 0) list.size * 60_000f / t.trackedMs else 0f,
            )
        }

        val trackedTotal = phaseStats.values.sumOf { it.trackedMs }
        return CalibrationResult(
            baseline = general,
            spontaneousCount = general.sampleCount,
            instructedCount = instructed.size,
            complete = phase == Phase.COMPLETE,
            completeRatio = general.completeBlinkRatio,
            profile = PersonalProfile(general = general, byContext = byContext, thresholds = thresholds),
            phases = phaseStats,
            meanConfidence = weightedMean(phaseStats.values) { it.meanConfidence },
            meanLuma = weightedMean(phaseStats.values) { it.meanLuma },
            trackedMs = trackedTotal,
            openLevel = open,
            closedLevel = closed,
            fullDepth = depth,
        )
    }

    /** Lets the user skip or reset at any point. */
    fun cancel() {
        phase = Phase.COMPLETE
    }

    private fun advance(next: Phase, nowMs: Long): Boolean {
        phase = next
        phaseStartedMs = nowMs
        if (next == Phase.DELIBERATE) {
            easeStep = 0
            trackedSinceCountMs = 0L
            peakSinceCount = 0f
            easedAtMs = null
            detector.reset()
            detector.configure(deliberateSettings(0))
        }
        return true
    }

    /** Typical openness with the eyes open: a high percentile, so blinks don't drag it down. */
    private fun openLevel(): Float? {
        if (openLevels.size < MIN_OPEN_SAMPLES) return null
        val sorted = openLevels.sorted()
        return sorted[((sorted.size - 1) * OPEN_PERCENTILE).toInt()]
    }

    /** Average lowest openness reached by the deliberate, confident blinks. */
    private fun closedLevel(): Float? {
        val good = instructed.filter { it.confidence >= EyeOpennessSample.MIN_CONFIDENCE }
        if (good.size < MIN_CLOSED_BLINKS) return null
        return good.map { it.minOpenness }.average().toFloat()
    }

    /**
     * How far this person's deliberate blinks closed their eyes, as a share of their open level: the middle one
     * of them, so a single odd blink (a flinch, a half-hearted one) does not decide. Blinks that were not measured
     * as a depth fall back to what their lowest openness says against the open level.
     */
    private fun fullDepth(open: Float?): Float? {
        val good = instructed.filter { it.confidence >= EyeOpennessSample.MIN_CONFIDENCE }
        if (good.size < MIN_CLOSED_BLINKS) return null
        val depths = good.mapNotNull { b ->
            when {
                b.depth > 0f -> b.depth
                open != null && open > 0f -> (1f - b.minOpenness / open).coerceIn(0f, 1f)
                else -> null
            }
        }.sorted()
        if (depths.size < MIN_CLOSED_BLINKS) return null
        return depths[depths.size / 2]
    }

    /** Eases the deliberate detector, one step at a time, while the person is trying and nothing is counting. */
    private fun trackDeliberate(sample: EyeOpennessSample, blink: BlinkObservation?) {
        val gap = sample.timestampMs - lastSampleAtMs
        if (gap in 1..MAX_SAMPLE_GAP_MS) trackedSinceCountMs += gap
        peakSinceCount = maxOf(peakSinceCount, detector.closure)
        if (blink != null) return
        if (trackedSinceCountMs >= EASE_AFTER_MS && easeStep < MAX_EASE_STEPS) {
            easeStep++
            trackedSinceCountMs = 0L
            peakSinceCount = 0f
            easedAtMs = sample.timestampMs
            detector.configure(deliberateSettings(easeStep))
        }
    }

    private fun naturalSettings(): DetectorSettings = DetectorSettings().scaled(sensitivity)

    /**
     * What counts as a deliberate blink at ease step [step]: the closure may be shallower each time the person has
     * been trying without result, down to about a fifth of their open level, and may be held for up to three seconds.
     */
    private fun deliberateSettings(step: Int): DetectorSettings {
        val start = DELIBERATE_START[step.coerceIn(0, DELIBERATE_START.lastIndex)]
        return DetectorSettings(
            start = start,
            end = start * 0.5f,
            full = BlinkDetector.DEFAULT_FULL,
            maxDurationMs = DELIBERATE_MAX_DURATION_MS,
            singleFrameDepth = start + 0.12f,
            useBlinkScore = true,
        ).scaled(sensitivity)
    }

    private fun weightedMean(stats: Collection<PhaseStats>, pick: (PhaseStats) -> Float): Float {
        val total = stats.sumOf { it.trackedMs }
        if (total <= 0) return 0f
        return (stats.sumOf { pick(it).toDouble() * it.trackedMs } / total).toFloat()
    }

    private fun phaseDuration(phase: Phase): Long = when (phase) {
        Phase.VIDEO -> videoMs
        Phase.READING -> readingMs
        Phase.DELIBERATE -> 0L
        Phase.COMPLETE -> 0L
    }

    companion object {
        const val DEFAULT_VIDEO_MS = 60_000L
        const val DEFAULT_READING_MS = 60_000L
        const val DEFAULT_DELIBERATE_BLINKS = 5

        const val MAX_SAMPLE_GAP_MS = 400L
        const val MAX_OPEN_SAMPLES = 4_000
        const val MIN_OPEN_SAMPLES = 30
        const val MIN_CLOSED_BLINKS = 3
        const val OPEN_PERCENTILE = 0.8f

        /** The depths (shares of the open level) at which a deliberate blink starts to count, easing one step at a time. */
        private val DELIBERATE_START = floatArrayOf(0.38f, 0.30f, 0.24f, 0.19f)
        const val MAX_EASE_STEPS = 3

        /** Closing the eyes on purpose can be slow and held; a closure is a deliberate blink for up to this long. */
        const val DELIBERATE_MAX_DURATION_MS = 3_000L

        /** Tracked time without a counted deliberate blink after which the test eases by one step. */
        const val EASE_AFTER_MS = 8_000L

        /** After this long without a counted blink and without ever reaching the threshold, suggest closing more firmly. */
        const val CLOSE_MORE_AFTER_MS = 4_000L

        /** How long "the test was eased" stays on the screen. */
        const val HINT_HOLD_MS = 6_000L
    }
}

/** What one timed phase actually measured. */
data class PhaseStats(
    val blinks: Int,
    val trackedMs: Long,
    val meanConfidence: Float,
    val meanLuma: Float,
    val blinksPerMinute: Float,
)

data class CalibrationResult(
    val baseline: BlinkBaseline,
    val spontaneousCount: Int,
    val instructedCount: Int,
    val complete: Boolean,
    val completeRatio: Float,
    val profile: PersonalProfile = PersonalProfile(general = baseline),
    val phases: Map<CalibrationSession.Phase, PhaseStats> = emptyMap(),
    val meanConfidence: Float = 0f,
    val meanLuma: Float = 0f,
    val trackedMs: Long = 0L,
    val openLevel: Float? = null,
    val closedLevel: Float? = null,
    /** How far the deliberate blinks closed the eyes, as a share of their own open level (the middle of them), if measured. */
    val fullDepth: Float? = null,
) {
    /**
     * Phases A and B are what establish a baseline. Without enough of them the
     * result is reported as insufficient rather than silently applied.
     */
    val hasEnoughSpontaneous: Boolean
        get() = baseline.hasSignal
}

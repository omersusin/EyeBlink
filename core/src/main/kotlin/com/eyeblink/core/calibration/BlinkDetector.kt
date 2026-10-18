package com.eyeblink.core.calibration

import kotlin.math.max
import kotlin.math.min

/**
 * How a [BlinkDetector] judges a closure. Every depth is a *share of the person's own open level*, not a
 * number of the camera's: 0 is an eye as open as it usually is, 1 is an eye shut tight.
 */
data class DetectorSettings(
    /** The closure at which an eye counts as closing. */
    val start: Float = BlinkDetector.DEFAULT_START,
    /** The closure below which it counts as open again. Lower than [start]: that gap is the hysteresis. */
    val end: Float = BlinkDetector.DEFAULT_END,
    /** The depth that makes a blink complete rather than partial. */
    val full: Float = BlinkDetector.DEFAULT_FULL,
    /** A closure shorter than this is a glitch of the tracker. */
    val minDurationMs: Long = BlinkDetector.DEFAULT_MIN_DURATION_MS,
    /** A closure longer than this is not a blink: the eyes were shut, or the tracker lost them. */
    val maxDurationMs: Long = BlinkDetector.DEFAULT_MAX_DURATION_MS,
    /** A closure seen in only one frame must be at least this deep, or it is taken for a landmark glitch. */
    val singleFrameDepth: Float = BlinkDetector.DEFAULT_SINGLE_FRAME_DEPTH,
    /** Without a known open level, how long the detector looks before it judges anything. */
    val warmupMs: Long = BlinkDetector.DEFAULT_WARMUP_MS,
    /**
     * Also listen to the face model's own blink score. It is the second opinion that does not depend on the shape of the eye,
     * so it is what lets a lid that barely moves be seen at all. Thresholds that were fitted with it must be used with it.
     */
    val useBlinkScore: Boolean = false,
) {
    /** The same detector, more ([factor] < 1) or less ([factor] > 1) willing to call a closure a blink. */
    fun scaled(factor: Float): DetectorSettings {
        val s = (start * factor).coerceIn(MIN_START, MAX_START)
        return copy(start = s, end = min(end * factor, s * 0.6f).coerceAtLeast(MIN_END), singleFrameDepth = max(singleFrameDepth * factor, s))
    }

    private companion object {
        const val MIN_START = 0.12f
        const val MAX_START = 0.60f
        const val MIN_END = 0.04f
    }
}

/**
 * Turns a stream of [EyeOpennessSample]s into [BlinkObservation]s.
 *
 * It does not ask whether an eye is open, it asks whether an eye is *more closed than it was a moment
 * ago*. For each eye it keeps the open level of the last few seconds ([OpenBaseline]); a sample's
 * closure is how far below that level it is, and the two eyes' closures are averaged. A blink starts when
 * that closure reaches [DetectorSettings.start] and ends when it falls back below [DetectorSettings.end];
 * the gap between the two is the hysteresis that stops tracker jitter from becoming a burst of phantom
 * blinks.
 *
 * That is what makes it work for every kind of eye. A wide eye that drops from 0.85 to 0.1 and a narrow
 * one that drops from 0.40 to 0.18 both close by more than half of their own level, and both are blinks;
 * a threshold on the raw number would have to choose between them.
 *
 * Three things keep "the person's own open level" honest:
 *
 *  - A closure is judged against the level of the moment it began and against nothing that is learned while the eyes
 *    are shut, so a long closure cannot redefine shut as open and then fill the minute with phantom blinks.
 *  - Eyes that are shut longer than any blink lasts stay shut: no blink is reported when they open, and the level of the
 *    open eye is still the one from before. Only a closure that is not deep (a squint, a lowered gaze) is taken to be a
 *    new open level and learned again.
 *  - A level that is only a [seed] from an earlier session is a guess; every reading is learned until the readings can
 *    speak for themselves, and a closure that began under the guess is forgotten when they do.
 *
 * Holds no Android or MediaPipe types on purpose - the detection rules are testable without a camera.
 */
class BlinkDetector(settings: DetectorSettings = DetectorSettings()) {

    var settings: DetectorSettings = settings
        private set

    private val left = OpenBaseline()
    private val right = OpenBaseline()
    private val score = OpenBaseline(percentile = SCORE_PERCENTILE)

    private var firstMs: Long? = null
    private var seeded = false

    // The closure in progress. It is measured from the open level of the moment it began.
    private var closedAtMs: Long? = null
    private var holdL = 0f
    private var holdR = 0f
    private var holdCalm = DEFAULT_CALM_SCORE
    private var underSeed = false
    private var closedFrames = 0
    private var peak = 0f
    private var deepAtMs = 0L
    private var settledSinceMs: Long? = null
    private var shut = false
    private var lowestOpenness = 1f
    private var confidenceAtClose = 0f

    /** How closed the eyes are right now, as a share of their own open level (0 = open, 1 = shut). */
    var closure: Float = 0f
        private set

    /** True while a closure is in progress. */
    val isClosed: Boolean get() = closedAtMs != null

    /**
     * True while the eyes have been shut for longer than a blink lasts (and are still shut tight). That is rest, not
     * staring, and it says nothing about how often this person blinks.
     */
    val isShut: Boolean get() = shut

    /** Changes how closures are judged without forgetting what open looks like. */
    fun configure(next: DetectorSettings) {
        settings = next
    }

    /** Starts from an open level known from before (openness on the 0..1 scale), so no warm-up is needed. */
    fun seed(openLevel: Float) {
        if (openLevel !in MIN_SEED..1f) return
        left.seed = openLevel
        right.seed = openLevel
        seeded = true
    }

    /**
     * Feed one sample. Returns a [BlinkObservation] on the frame where the eyes reopen, or null otherwise.
     *
     * Untrackable samples are ignored: they neither open nor close a blink, so a lighting dip cannot
     * fabricate a closure or split a real one in half.
     */
    fun onSample(sample: EyeOpennessSample): BlinkObservation? {
        if (!sample.isTrackable) return null

        val t = sample.timestampMs
        val first = firstMs ?: t.also { firstMs = it }
        val l = sample.leftEyeOpenness
        val r = sample.rightEyeOpenness

        // The readings speak for themselves once they are enough to make a level; before that the seed stands in for one.
        val ownLevel = left.hasLevel(t) && right.hasLevel(t)
        if (underSeed && ownLevel) clearClosure()

        val since = closedAtMs
        val baseL = if (since != null) holdL else left.level(t)
        val baseR = if (since != null) holdR else right.level(t)

        if (baseL == null || baseR == null || (!seeded && t - first < settings.warmupMs)) {
            // Still finding out what open looks like for this person.
            learnOpen(t, l, r, sample.blinkScore)
            closure = 0f
            return null
        }

        val calm = if (since != null) holdCalm else (score.level(t) ?: DEFAULT_CALM_SCORE)
        val share = scoreShare(sample.blinkScore, calm)
        val c = closureOf(l, baseL, r, baseR, share)
        closure = c

        if (since == null) {
            if (c >= settings.start) {
                holdL = baseL
                holdR = baseR
                holdCalm = calm
                underSeed = !ownLevel
                closedAtMs = t
                closedFrames = 1
                peak = c
                deepAtMs = t
                settledSinceMs = null
                lowestOpenness = sample.openness
                confidenceAtClose = sample.confidence
                // The seed may be wrong, and a closure that nothing can end would hide every blink after it.
                if (underSeed) learnOpen(t, l, r, sample.blinkScore)
            } else {
                learnOpen(t, l, r, sample.blinkScore)
            }
            return null
        }

        if (c > settings.end) {
            // Still shut.
            closedFrames++
            peak = max(peak, c)
            lowestOpenness = min(lowestOpenness, sample.openness)
            if (underSeed) learnOpen(t, l, r, sample.blinkScore)
            if (isDeep(c, share)) deepAtMs = t

            if (c >= settings.start) {
                settledSinceMs = null
            } else {
                // Between the two thresholds: not shut any more, not open either. If the lids stay here, this person does
                // not open as far as before (heavy lids, a lowered gaze, glare): the blink is over and this is the new open.
                val settledAt = settledSinceMs ?: t.also { settledSinceMs = it }
                if (t - settledAt >= SETTLE_MS) {
                    val blink = finish(settledAt, since)
                    relearn(t, l, r)
                    return blink
                }
            }

            val shutFor = t - since
            if (shutFor > settings.maxDurationMs) {
                // Longer than a blink can be. Eyes that are shut tight stay as they are - the level of open is still the one
                // from before they closed, so that opening them again is not mistaken for anything. Anything shallower is a
                // squint or a lowered gaze that has lasted: forget what open was, and learn it again from the next readings.
                val stillShut = t - deepAtMs <= settings.maxDurationMs && shutFor <= SHUT_FORGET_MS
                if (stillShut) shut = true else dropOut(t, l, r, sample.blinkScore)
            }
            return null
        }

        learnOpen(t, l, r, sample.blinkScore)
        return finish(t, since)
    }

    /** Discards any in-progress closure, e.g. when tracking is lost or a phase ends. What open looks like is kept. */
    fun reset() {
        clearClosure()
        closure = 0f
    }

    /** Forgets everything, the open level included. */
    fun forget() {
        reset()
        left.clear()
        right.clear()
        score.clear()
        firstMs = null
        seeded = false
    }

    private fun finish(endMs: Long, since: Long): BlinkObservation? {
        val frames = closedFrames
        val reached = peak
        val lowest = lowestOpenness
        val confidence = confidenceAtClose
        clearClosure()

        val duration = endMs - since
        if (duration < settings.minDurationMs || duration > settings.maxDurationMs) return null
        if (frames <= 1 && reached < settings.singleFrameDepth) return null

        return BlinkObservation(
            startMs = since,
            durationMs = duration,
            complete = reached >= settings.full,
            confidence = confidence,
            minOpenness = lowest,
            depth = reached,
        )
    }

    /** The model's own opinion that the eyes are shut (0..1), measured against how this face looks when it is not blinking. */
    private fun scoreShare(blinkScore: Float?, calm: Float): Float? =
        blinkScore?.takeIf { it.isFinite() }?.let { ((it - calm) / (1f - calm).coerceAtLeast(MIN_SCORE_RANGE)).coerceIn(0f, 1f) }

    private fun closureOf(l: Float, baseL: Float, r: Float, baseR: Float, share: Float?): Float {
        val c = (relative(l, baseL) + relative(r, baseR)) / 2f
        return if (settings.useBlinkScore && share != null) max(c, share * BLINK_SCORE_WEIGHT) else c
    }

    /**
     * Whether a closure of depth [c] is as good as shut: the lids are [DEEP_MARGIN] past the start of a blink and at least
     * [DEEP_FLOOR] down (but never asked to go further than this person's full blinks do), or the face model is sure of it,
     * which is what tells shut eyes from narrow ones when the lid hardly moves.
     */
    private fun isDeep(c: Float, share: Float?): Boolean {
        val lids = min(settings.full, max(settings.start + DEEP_MARGIN, DEEP_FLOOR))
        return c >= lids || (share != null && share >= SHUT_SHARE)
    }

    private fun relative(value: Float, base: Float): Float =
        if (base < MIN_BASE) 0f else (1f - value / base).coerceIn(0f, 1f)

    private fun learnOpen(t: Long, l: Float, r: Float, blinkScore: Float?) {
        left.add(t, l)
        right.add(t, r)
        if (blinkScore != null) score.add(t, blinkScore)
    }

    /** The eyes settled lower than they were: that is where open is now. Nobody needs to wait for the warm-up to find out. */
    private fun relearn(t: Long, l: Float, r: Float) {
        left.clear()
        right.clear()
        left.add(t, l)
        right.add(t, r)
        seeded = false
        firstMs = t - settings.warmupMs
        closure = 0f
    }

    private fun dropOut(t: Long, l: Float, r: Float, blinkScore: Float?) {
        clearClosure()
        left.clear()
        right.clear()
        score.clear()
        seeded = false
        firstMs = t
        learnOpen(t, l, r, blinkScore)
        closure = 0f
    }

    private fun clearClosure() {
        closedAtMs = null
        underSeed = false
        closedFrames = 0
        peak = 0f
        deepAtMs = 0L
        settledSinceMs = null
        shut = false
        lowestOpenness = 1f
        confidenceAtClose = 0f
    }

    companion object {
        /** A closure of a third of the open level is a blink; most natural ones go well past it. */
        const val DEFAULT_START = 0.35f
        const val DEFAULT_END = 0.17f

        /** Most of the way to this person's shut eye: below it a blink is reported as partial. */
        const val DEFAULT_FULL = 0.60f
        const val DEFAULT_MIN_DURATION_MS = 40L

        /** Natural blinks are over in a few hundred milliseconds; a closure of nearly a second is something else. */
        const val DEFAULT_MAX_DURATION_MS = 900L
        const val DEFAULT_SINGLE_FRAME_DEPTH = 0.60f
        const val DEFAULT_WARMUP_MS = 700L

        /**
         * Lids that are back under [DetectorSettings.start] but have not got under [DetectorSettings.end] after this long are
         * not on their way up: the reopening of a blink passes through that band in well under a tenth of a second.
         */
        const val SETTLE_MS = 250L

        /** Eyes shut for this long are taken to have changed for good (or the tracker has), and open is learned anew. */
        const val SHUT_FORGET_MS = 30_000L

        /** How far past the start of a blink, and how deep at least, a closure is to count as shut (see [isDeep]). */
        private const val DEEP_MARGIN = 0.10f
        private const val DEEP_FLOOR = 0.40f

        /** How sure the face model's score has to be that the eyes are shut. */
        private const val SHUT_SHARE = 0.75f

        /** The model's blink score is high only while the eyes are shut; its calm level is the low end of what it says. */
        private const val SCORE_PERCENTILE = 0.20f
        private const val DEFAULT_CALM_SCORE = 0.05f
        private const val MIN_SCORE_RANGE = 0.4f

        /** The score runs 0..1 where a closure measured on the eye runs about 0..0.75, so it is brought to the same size. */
        private const val BLINK_SCORE_WEIGHT = 0.7f

        /** Below this an "open level" is no level at all (eyes shut for the whole window). */
        private const val MIN_BASE = 0.03f
        private const val MIN_SEED = 0.05f
    }
}

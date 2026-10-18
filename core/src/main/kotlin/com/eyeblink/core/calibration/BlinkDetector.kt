package com.eyeblink.core.calibration

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    /**
     * Eyes whose lids are plainly down are held shut for this long unless the face model puts them at their calm level (which
     * it does for a gaze lowered a long way); after it open is learned anew. Where the score agrees with the lids they stay shut
     * for [BlinkDetector.SHUT_FORGET_STRONG_MS].
     */
    val shutForgetMs: Long = BlinkDetector.SHUT_FORGET_MS,
    /**
     * How far the noise gate gives way (see [BlinkDetector.startDepth]) where the eyelids are all there is to go by: 1 as it is,
     * less for a detector that is to be more willing to see a blink. Where the face model takes part the gate stands whatever
     * this says: the model's score already carries the shallow blinks, and the gate is what keeps the eyelids from adding noise to them.
     */
    val gateScale: Float = 1f,
) {
    /** The same detector, more ([factor] < 1) or less ([factor] > 1) willing to call a closure a blink. */
    fun scaled(factor: Float): DetectorSettings {
        val s = (start * factor).coerceIn(MIN_START, MAX_START)
        return copy(
            start = s,
            end = min(end * factor, s * 0.6f).coerceAtLeast(MIN_END),
            singleFrameDepth = max(singleFrameDepth * factor, s),
            // The false blinks of a noisy tracker grow much faster than the true ones when the bar is lowered, so the gate
            // gives way half as far (in the log) as the thresholds do.
            gateScale = gateScale * sqrt(factor),
        )
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
 * What keeps "the person's own open level" honest:
 *
 *  - A closure is judged against the level of the moment it began and against nothing that is learned while the eyes
 *    are shut, so a long closure cannot redefine shut as open and then fill the minute with phantom blinks.
 *  - Eyes that are shut longer than any blink lasts stay shut: no blink is reported when they open, and the level of the
 *    open eye is still the one from before. What says they are shut is lids that are plainly down (half of the open level) with
 *    a face-model score that does not put them at their calm level - nothing here knows what a shut eye's score reads, so it is
 *    never asked to reach a number, and it holds them for longer the more it agrees. A closure that is not that deep (a squint,
 *    a lowered gaze, eyes whose lids hardly move) is taken to be a new open level and learned again, and what the score says is
 *    learned with it.
 *  - How far the readings of an eye scatter when it is open is learned along the way, and a blink has to stand out of it: so
 *    a level that was learned from readings that are all scatter (shut eyes taken for open ones, a very noisy tracker) cannot
 *    turn its own jitter into blinks. (This holds the eyelids to it; a face model that is sure of a blink is not, it has no jitter.)
 *  - When the readings run out - the camera slept - the last level they made stands in for them, and a closure that began
 *    under it is judged like any other.
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

    // How much an open eye's readings jump from one frame to the next, in the units of the readings (not of the eye): the
    // tracker's noise does not depend on whether the eye is open or shut, so it is still known when the level changes.
    private var jitterL = 0f
    private var jitterR = 0f
    private var prevL = 0f
    private var prevR = 0f
    private var prevAtMs = 0L
    private var prevRest = false
    private var gate = 0f

    // The closure in progress. It is measured from the open level of the moment it began.
    private var closedAtMs: Long? = null
    private var holdL = 0f
    private var holdR = 0f
    private var holdCalm = DEFAULT_CALM_SCORE
    private var holdStart = 0f
    private var closedFrames = 0
    private var armedFrames = 0
    private var peak = 0f
    private var deepAtMs = 0L
    private var agreedAtMs = 0L

    // The last second or so of the closure, for judging whether the eyes are shut: the median of it does not move for a blink
    // inside a lowered gaze (not even a deliberate one), and does not flap for the jitter of lids that are only just down by enough.
    private val recentAt = LongArray(EVIDENCE_CAPACITY)
    private val recentLids = FloatArray(EVIDENCE_CAPACITY)
    private val recentShare = FloatArray(EVIDENCE_CAPACITY)
    private val sorted = FloatArray(EVIDENCE_CAPACITY)
    private var recentCount = 0
    private var recentHead = 0
    private var resumed = false
    private var settledSinceMs: Long? = null
    private var shut = false
    private var lowestOpenness = 1f
    private var confidenceAtClose = 0f

    /** How closed the eyes are right now, as a share of their own open level (0 = open, 1 = shut). */
    var closure: Float = 0f
        private set

    /**
     * The closure the eyelids need to start a blink right now: the setting, or higher if their readings are too scattered for
     * that to mean anything. (A blink the face model is sure of starts below it - the score has no jitter to stand out of - but
     * what the eyelids need is what a person can see and act on.)
     */
    val startDepth: Float get() = max(settings.start, gate)

    /** When a closure last went on for longer than a blink can, or 0: for telling a person who holds their eyes shut that it does not count. */
    var longClosureAtMs: Long = 0L
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

        val since = closedAtMs
        val baseL = if (since != null) holdL else left.level(t)
        val baseR = if (since != null) holdR else right.level(t)

        if (baseL == null || baseR == null || (!seeded && t - first < settings.warmupMs)) {
            // Still finding out what open looks like for this person.
            learnOpen(t, l, r, sample.blinkScore)
            watchJitter(t, l, r)
            closure = 0f
            return null
        }

        val calm = if (since != null) holdCalm else (score.level(t) ?: DEFAULT_CALM_SCORE)
        val share = scoreShare(sample.blinkScore, calm)
        val lids = (relative(l, baseL) + relative(r, baseR)) / 2f
        val c = if (settings.useBlinkScore && share != null) max(lids, share * BLINK_SCORE_WEIGHT) else lids
        closure = c

        if (since == null) {
            gate = noiseGate(baseL, baseR)
            // The eyelids have to stand out of their own jitter, the face model's score has no jitter to stand out of.
            val byLids = lids >= max(settings.start, gate)
            val byScore = settings.useBlinkScore && share != null && share * BLINK_SCORE_WEIGHT >= settings.start
            if (byLids || byScore) {
                holdL = baseL
                holdR = baseR
                holdCalm = calm
                holdStart = settings.start
                closedAtMs = t
                closedFrames = 1
                armedFrames = 1
                peak = c
                deepAtMs = t
                agreedAtMs = 0L
                recentCount = 0
                recentHead = 0
                settledSinceMs = null
                lowestOpenness = sample.openness
                confidenceAtClose = sample.confidence
                prevRest = false
            } else {
                learnOpen(t, l, r, sample.blinkScore)
                watchJitter(t, l, r)
            }
            return null
        }

        if (c > settings.end) {
            // Still shut.
            if (resumed) {
                // The first picture after a gap in which the eyes were shut tight. What the last second before the gap showed is the
                // evidence there is, so it is taken to run on into this picture; if they are open the closure ends on its own.
                resumed = false
                carryOver(t)
            }
            closedFrames++
            if (c >= holdStart) armedFrames++
            peak = max(peak, c)
            lowestOpenness = min(lowestOpenness, sample.openness)
            remember(t, lids, share)
            when (shutEvidence()) {
                EVIDENCE_AGREED -> { deepAtMs = t; agreedAtMs = t }
                EVIDENCE_LIDS -> deepAtMs = t
                else -> Unit
            }

            if (c >= holdStart) {
                settledSinceMs = null
            } else {
                // Between the two thresholds: not shut any more, not open either. If the lids stay here after a closure that
                // began like a blink, this person does not open as far as before (heavy lids, a lowered gaze, glare): the blink
                // is over and this is the new open. A dip that only brushed the start threshold is not a blink to settle.
                val settledAt = settledSinceMs ?: t.also { settledSinceMs = it }
                if (t - settledAt >= SETTLE_MS && blinkShaped()) {
                    val blink = finish(settledAt, since)
                    relearn(t, l, r)
                    return blink
                }
            }

            val shutFor = t - since
            if (shutFor > settings.maxDurationMs) {
                // A slow camera has not delivered enough pictures of the closure to judge it by yet: wait for them, but not for ever.
                if (recentCount < EVIDENCE_MIN_FRAMES && shutFor <= settings.maxDurationMs + EVIDENCE_WINDOW_MS) return null
                if (lidsAreDeep()) longClosureAtMs = t
                // Longer than a blink can be. Eyes that are shut tight stay as they are - the level of open is still the one
                // from before they closed, so that opening them again is not mistaken for anything. Anything shallower is a
                // squint or a lowered gaze that has lasted: forget what open was, and learn it again from the next readings.
                val forgetAfter = if (t - agreedAtMs <= AGREEMENT_MEMORY_MS) max(settings.shutForgetMs, SHUT_FORGET_STRONG_MS) else settings.shutForgetMs
                val stillShut = t - deepAtMs <= settings.maxDurationMs && shutFor <= forgetAfter
                if (stillShut) shut = true else dropOut(t, l, r, sample.blinkScore)
            }
            return null
        }

        learnOpen(t, l, r, sample.blinkScore)
        return finish(t, since)
    }

    /** Discards any in-progress closure, e.g. when a phase ends. What open looks like is kept. */
    fun reset() {
        clearClosure()
        closure = 0f
        longClosureAtMs = 0L
    }

    /**
     * Tracking was away for a while. A blink in progress cannot be known - the gap would be counted as part of it - and is
     * dropped. Eyes that were shut tight are most likely still shut when the face comes back, so that closure goes on: if
     * they are open, the first picture says so, and one bad picture in the middle of a rest does not make a blink of its end.
     */
    fun onGap() {
        if (shut) resumed = true else clearClosure()
        prevRest = false
    }

    /** Forgets everything, the open level included. */
    fun forget() {
        reset()
        left.clear()
        right.clear()
        score.clear()
        firstMs = null
        seeded = false
        jitterL = 0f
        jitterR = 0f
        prevRest = false
        gate = 0f
        longClosureAtMs = 0L
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

    private fun relative(value: Float, base: Float): Float =
        if (base < MIN_BASE) 0f else (1f - value / base).coerceIn(0f, 1f)

    private fun remember(t: Long, lids: Float, share: Float?) {
        recentAt[recentHead] = t
        recentLids[recentHead] = lids
        recentShare[recentHead] = share ?: Float.NaN
        recentHead = (recentHead + 1) % EVIDENCE_CAPACITY
        if (recentCount < EVIDENCE_CAPACITY) recentCount++
        // Whatever is older than the window is not evidence of what the eyes are doing now.
        while (recentCount > 0 && t - recentAt[(recentHead - recentCount + EVIDENCE_CAPACITY) % EVIDENCE_CAPACITY] > EVIDENCE_WINDOW_MS) recentCount--
    }

    /** Moves the frames the evidence is made of up in time, so that the newest of them is at [t]: the gap in between did not happen to them. */
    private fun carryOver(t: Long) {
        if (recentCount == 0) return
        val shift = t - recentAt[(recentHead - 1 + EVIDENCE_CAPACITY) % EVIDENCE_CAPACITY]
        val oldest = (recentHead - recentCount + EVIDENCE_CAPACITY) % EVIDENCE_CAPACITY
        for (i in 0 until recentCount) recentAt[(oldest + i) % EVIDENCE_CAPACITY] += shift
    }

    /** The middle of the first [count] values of [sorted] (which is sorted by this). */
    private fun middle(count: Int): Float {
        java.util.Arrays.sort(sorted, 0, count)
        return sorted[count / 2]
    }

    /** Whether the lids, by their median over the last frames, are down by about half of the open level (and by more than a blink's worth). */
    private fun lidsAreDeep(): Boolean {
        if (recentCount < EVIDENCE_MIN_FRAMES) return false
        val oldest = (recentHead - recentCount + EVIDENCE_CAPACITY) % EVIDENCE_CAPACITY
        for (i in 0 until recentCount) sorted[i] = recentLids[(oldest + i) % EVIDENCE_CAPACITY]
        return middle(recentCount) >= max(holdStart + DEEP_MARGIN, DEEP_FLOOR)
    }

    /**
     * Whether the eyes look shut right now, judged by the last frames of the closure: [EVIDENCE_LIDS] when the lids are down by
     * half of the open level (and by more than a blink's worth) - which a gaze lowered at a phone or a squint does not reach - and
     * the face model, if it says anything, does not put the eyes at their calm level; [EVIDENCE_AGREED] when it also puts them
     * half way up its range. How high the score of this person's shut eyes goes does not matter, and neither does a score alone:
     * it is not known what that is for eyes whose lids hardly move, or for a gaze lowered at a phone.
     */
    private fun shutEvidence(): Int {
        if (!lidsAreDeep()) return EVIDENCE_NONE
        val oldest = (recentHead - recentCount + EVIDENCE_CAPACITY) % EVIDENCE_CAPACITY
        var scored = 0
        for (i in 0 until recentCount) {
            val v = recentShare[(oldest + i) % EVIDENCE_CAPACITY]
            if (!v.isNaN()) sorted[scored++] = v
        }
        if (scored * 2 < recentCount) return EVIDENCE_LIDS
        val share = middle(scored)
        return when {
            share >= AGREE_SHARE -> EVIDENCE_AGREED
            share >= CONTRADICT_SHARE -> EVIDENCE_LIDS
            else -> EVIDENCE_NONE
        }
    }

    /**
     * A closure that began like a blink: well past the start threshold, and for more than one frame. The settling path is for
     * blinks whose lids did not come all the way back; a single deep frame in a lowered gaze is a glitch of the tracker.
     */
    private fun blinkShaped(): Boolean = peak >= holdStart + SETTLE_MARGIN && armedFrames >= 2

    private fun learnOpen(t: Long, l: Float, r: Float, blinkScore: Float?) {
        left.add(t, l)
        right.add(t, r)
        if (blinkScore != null) score.add(t, blinkScore)
    }

    /**
     * Watches how much the readings of the open eye jump from one frame to the next. Only frames outside a closure count, one
     * after the other - the edges of a blink are not noise - and the estimate is an average of the jumps that gives little weight
     * to a single large one (a landmark that moved, the first frame of a blink).
     */
    private fun watchJitter(t: Long, l: Float, r: Float) {
        if (prevRest && t - prevAtMs in 1..MAX_JITTER_GAP_MS) {
            jitterL = smooth(jitterL, abs(l - prevL))
            jitterR = smooth(jitterR, abs(r - prevR))
        }
        prevL = l
        prevR = r
        prevAtMs = t
        prevRest = true
    }

    /** The average of the jumps, which starts from next to nothing and grows no faster than [JITTER_WINSOR] times a step: one large jump cannot make an estimate. */
    private fun smooth(estimate: Float, jump: Float): Float {
        val from = max(estimate, JITTER_FLOOR)
        val capped = min(jump, JITTER_WINSOR * from + JITTER_FLOOR)
        return from + JITTER_ALPHA * (capped - from)
    }

    /**
     * How far the closure of an open eye wanders by itself, from the jitter of its readings and the level it is measured
     * against, times a factor that makes a wander that size a rarity. A blink has to stand out of that: for an eye whose level
     * is the readings of shut eyes the jitter is large against it, and the gate rises with it. The two eyes' jitter is
     * averaged, not combined as if they shared none of it - how much of it they share depends on the tracker, and a gate that
     * takes them for independent lets a noisy tracker report a blink every few seconds for eyes that do not blink at all.
     * Where the eyelids are all there is, a detector that is set to be more willing to see a blink is held to less of it.
     */
    private fun noiseGate(baseL: Float, baseR: Float): Float {
        if (jitterL <= 0f || jitterR <= 0f || baseL < MIN_BASE || baseR < MIN_BASE) return 0f
        val sl = jitterL / JITTER_TO_SIGMA / baseL
        val sr = jitterR / JITTER_TO_SIGMA / baseR
        val giveWay = if (settings.useBlinkScore) 1f else settings.gateScale
        return min(NOISE_GATE_K * giveWay * (sl + sr) / 2f, MAX_NOISE_GATE)
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

    /** The eyes stayed where they were for longer than a blink: forget what open was and learn it again, starting with this reading. */
    private fun dropOut(t: Long, l: Float, r: Float, blinkScore: Float?) {
        clearClosure()
        left.clear()
        right.clear()
        score.clear()
        seeded = false
        firstMs = t - settings.warmupMs
        learnOpen(t, l, r, blinkScore)
        closure = 0f
    }

    private fun clearClosure() {
        closedAtMs = null
        closedFrames = 0
        armedFrames = 0
        peak = 0f
        deepAtMs = 0L
        agreedAtMs = 0L
        recentCount = 0
        recentHead = 0
        resumed = false
        settledSinceMs = null
        shut = false
        lowestOpenness = 1f
        confidenceAtClose = 0f
    }

    companion object {
        /** A closure of a third of the open level is a blink; most natural ones go well past it. */
        const val DEFAULT_START = 0.35f

        /**
         * Low enough that the jitter of an open eye, and of a lowered gaze that a blink was mistaken for, does not end a
         * closure by itself: with 0.17 a dip of a quarter of the open level ended in noise about half of the time.
         */
        const val DEFAULT_END = 0.11f

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

        /**
         * A closure counts as a blink that settled only if it went this far past the start threshold: a dip that brushed it with
         * a frame of tracker noise (a lowered gaze, a squint) is not one.
         */
        const val SETTLE_MARGIN = 0.12f

        /** Eyes whose lids alone say they are shut stay shut this long before open is learned anew (see [DetectorSettings.shutForgetMs]). */
        const val SHUT_FORGET_MS = 30_000L

        /**
         * Eyes that the lids and the face model both say are shut stay shut this long: a rest, a short nap. Not longer, because
         * whatever tells shut eyes can be wrong about a gaze lowered at a phone, and a detector that is wrongly sure is blind for
         * as long as it holds.
         */
        const val SHUT_FORGET_STRONG_MS = 180_000L

        /**
         * How much of the end of a closure the evidence of shut eyes is judged by (room for 60 frames a second), and how few frames
         * of it will do. A second and a bit is longer than a deliberate blink and shorter than anything that is not one.
         */
        private const val EVIDENCE_WINDOW_MS = 1_200L
        private const val EVIDENCE_CAPACITY = 96
        private const val EVIDENCE_MIN_FRAMES = 7

        /** What the evidence is: nothing, the lids alone, or the lids with a score that does not contradict them. */
        private const val EVIDENCE_NONE = 0
        private const val EVIDENCE_LIDS = 1
        private const val EVIDENCE_AGREED = 2

        /** A score that agreed a moment ago still agrees when a frame or two of it are missing. */
        private const val AGREEMENT_MEMORY_MS = 5_000L

        /**
         * How far past the start of a blink, and how deep at least, the lids must be (by their median over the last frames) to count
         * as shut: more than a gaze lowered by a third or a squint reach, less than the heavy lids of the eyes that move least.
         */
        private const val DEEP_MARGIN = 0.10f
        private const val DEEP_FLOOR = 0.46f

        /**
         * How far up the face model's score (as a share of this face's own range) has to be to agree with lids that look shut, and
         * how close to its calm level it has to be to contradict them. Between the two it is the score of eyes that may be shut
         * whose face model reads low - nobody knows how low it goes for narrow, heavy-lidded eyes - and the lids are believed
         * for as long as lids alone are.
         */
        private const val AGREE_SHARE = 0.50f
        private const val CONTRADICT_SHARE = 0.15f

        /** The model's blink score is high only while the eyes are shut; its calm level is the low end of what it says. */
        private const val SCORE_PERCENTILE = 0.20f
        private const val DEFAULT_CALM_SCORE = 0.05f
        private const val MIN_SCORE_RANGE = 0.4f

        /** The score runs 0..1 where a closure measured on the eye runs about 0..0.75, so it is brought to the same size. */
        private const val BLINK_SCORE_WEIGHT = 0.7f

        /** Below this an "open level" is no level at all (eyes shut for the whole window). */
        private const val MIN_BASE = 0.03f
        private const val MIN_SEED = 0.05f

        // The noise gate: the average jump between two frames (about 1.13 sigma for noise that is Gaussian), a weight for the
        // newest jump, how much larger than the estimate one jump may count as, and how many sigmas of the wander of a closure a
        // blink has to stand out of - at four, an eye whose jitter is 7 % of its level (a heavy-lidded one) is not held back.
        private const val JITTER_TO_SIGMA = 1.128f
        private const val JITTER_ALPHA = 0.03f
        private const val JITTER_WINSOR = 4f
        private const val JITTER_FLOOR = 0.002f
        private const val MAX_JITTER_GAP_MS = 400L
        private const val NOISE_GATE_K = 4f

        /** The gate never goes so high that a blink the face model is sure of (0.7 of its size) cannot pass it. */
        private const val MAX_NOISE_GATE = 0.50f
    }
}

package com.eyeblink.core.stats

import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary
import kotlinx.serialization.Serializable

/** Counters for one local calendar day. Everything is a number; nothing identifies content. */
@Serializable
data class DayStats(
    val microShown: Int = 0,
    val restsCompleted: Int = 0,
    val restsSkipped: Int = 0,
    val aborted: Int = 0,
    val failed: Int = 0,
    val pausesVerified: Int = 0,
    val pausesUnverified: Int = 0,
    val pausesFailed: Int = 0,
    val resumesOk: Int = 0,
    val resumesRefused: Int = 0,
    val resumesFailed: Int = 0,
    val activeMs: Long = 0L,
    val cameraMs: Long = 0L,
    val observedBlinks: Int = 0,
    val observedTrackedMs: Long = 0L,
    val deferredByReason: Map<String, Int> = emptyMap(),
) {
    val observedBlinksPerMinute: Float?
        get() = if (observedTrackedMs >= MIN_TRACKED_FOR_RATE_MS) observedBlinks * 60_000f / observedTrackedMs else null

    companion object {
        const val MIN_TRACKED_FOR_RATE_MS = 60_000L
    }
}

/** A rolling per-day record kept on the device and shown in the stats screen. Never uploaded. */
@Serializable
data class UsageLedger(val days: Map<String, DayStats> = emptyMap()) {

    fun day(key: String): DayStats = days[key] ?: DayStats()

    private fun update(key: String, f: (DayStats) -> DayStats) = copy(days = days + (key to f(day(key))))

    fun recordBreak(dayKey: String, r: BreakReport): UsageLedger = update(dayKey) { d ->
        var s = d
        when (r.outcome) {
            BreakOutcome.COMPLETED -> s = when (r.kind) {
                BreakKind.MICRO -> s.copy(microShown = s.microShown + 1)
                BreakKind.REST -> s.copy(restsCompleted = s.restsCompleted + 1)
            }
            BreakOutcome.SKIPPED_BY_USER -> s = s.copy(restsSkipped = s.restsSkipped + 1)
            BreakOutcome.ABORTED -> s = s.copy(aborted = s.aborted + 1)
            BreakOutcome.FAILED -> s = s.copy(failed = s.failed + 1)
        }
        s = when (r.pause) {
            PauseSummary.PAUSED -> s.copy(pausesVerified = s.pausesVerified + 1)
            PauseSummary.UNVERIFIED -> s.copy(pausesUnverified = s.pausesUnverified + 1)
            PauseSummary.FAILED -> s.copy(pausesFailed = s.pausesFailed + 1)
            else -> s
        }
        when (r.resume) {
            ResumeSummary.RESUMED, ResumeSummary.ALREADY_PLAYING -> s.copy(resumesOk = s.resumesOk + 1)
            ResumeSummary.REFUSED -> s.copy(resumesRefused = s.resumesRefused + 1)
            ResumeSummary.FAILED -> s.copy(resumesFailed = s.resumesFailed + 1)
            else -> s
        }
    }

    fun recordDeferral(dayKey: String, reason: String): UsageLedger = update(dayKey) {
        it.copy(deferredByReason = it.deferredByReason + (reason to (it.deferredByReason[reason] ?: 0) + 1))
    }

    fun addActive(dayKey: String, ms: Long): UsageLedger = update(dayKey) { it.copy(activeMs = it.activeMs + ms.coerceAtLeast(0)) }

    fun addCamera(dayKey: String, ms: Long): UsageLedger = update(dayKey) { it.copy(cameraMs = it.cameraMs + ms.coerceAtLeast(0)) }

    fun addObserved(dayKey: String, blinks: Int, trackedMs: Long): UsageLedger = update(dayKey) {
        it.copy(observedBlinks = it.observedBlinks + blinks.coerceAtLeast(0), observedTrackedMs = it.observedTrackedMs + trackedMs.coerceAtLeast(0))
    }

    /** Keeps only the newest [keepDays] days. Day keys sort chronologically (ISO dates). */
    fun pruned(keepDays: Int = KEEP_DAYS): UsageLedger {
        if (days.size <= keepDays) return this
        val keep = days.keys.sortedDescending().take(keepDays).toSet()
        return copy(days = days.filterKeys { it in keep })
    }

    /** Sum of the newest [n] days that have data. */
    fun lastDays(n: Int): DayStats = days.keys.sortedDescending().take(n).map { days.getValue(it) }.fold(DayStats(), ::merge)

    companion object {
        const val KEEP_DAYS = 60

        fun merge(a: DayStats, b: DayStats) = DayStats(
            microShown = a.microShown + b.microShown,
            restsCompleted = a.restsCompleted + b.restsCompleted,
            restsSkipped = a.restsSkipped + b.restsSkipped,
            aborted = a.aborted + b.aborted,
            failed = a.failed + b.failed,
            pausesVerified = a.pausesVerified + b.pausesVerified,
            pausesUnverified = a.pausesUnverified + b.pausesUnverified,
            pausesFailed = a.pausesFailed + b.pausesFailed,
            resumesOk = a.resumesOk + b.resumesOk,
            resumesRefused = a.resumesRefused + b.resumesRefused,
            resumesFailed = a.resumesFailed + b.resumesFailed,
            activeMs = a.activeMs + b.activeMs,
            cameraMs = a.cameraMs + b.cameraMs,
            observedBlinks = a.observedBlinks + b.observedBlinks,
            observedTrackedMs = a.observedTrackedMs + b.observedTrackedMs,
            deferredByReason = (a.deferredByReason.keys + b.deferredByReason.keys).associateWith {
                (a.deferredByReason[it] ?: 0) + (b.deferredByReason[it] ?: 0)
            },
        )
    }
}

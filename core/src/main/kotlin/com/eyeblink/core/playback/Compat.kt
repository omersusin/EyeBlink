package com.eyeblink.core.playback

import com.eyeblink.core.model.PlaybackSettings
import kotlinx.serialization.Serializable

@Serializable
data class StrategyStat(
    val pauseOk: Int = 0,
    val pauseFail: Int = 0,
    val resumeOk: Int = 0,
    val resumeFail: Int = 0,
    val consecutiveFails: Int = 0,
    val lastTestedMs: Long = 0L,
    /** When a pause and a resume last both worked, in a test or in real use. */
    val lastOkMs: Long = 0L,
    val lastLatencyMs: Long = 0L,
    /** A deliberate compatibility test has passed (as opposed to incidental real-world successes). */
    val testPassed: Boolean = false,
) {
    val everWorked: Boolean get() = pauseOk > 0 && resumeOk > 0
}

/** What EyeBlink has learned about one app, per app version. */
@Serializable
data class CompatRecord(
    val packageName: String,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val stats: Map<Strategy, StrategyStat> = emptyMap(),
)

enum class CompatStatus {
    /** At least one strategy has been tested and works. Only this may be advertised as supported. */
    VERIFIED,

    /** Never tested. EyeBlink still blinks, but does not touch playback. */
    UNTESTED,

    /** Tested, and nothing worked (or it stopped working). Blinks without pausing. */
    FAILING,

    /** The app updated since the last test; version-sensitive results no longer apply. */
    RETEST_ADVISED,
}

object CompatPolicy {

    /** After this many failures in a row a strategy is switched off until it is re-tested. */
    const val MAX_CONSECUTIVE_FAILS = 3

    fun isUsable(stat: StrategyStat?): Boolean =
        stat != null && stat.everWorked && stat.consecutiveFails < MAX_CONSECUTIVE_FAILS

    /**
     * True when a break may try [s] on an app although it has not proven itself there: it can read the player back
     * ([Strategy.canTryUnproven]), it has not failed a deliberate test, and it has not failed [MAX_CONSECUTIVE_FAILS]
     * attempts in a row. What the attempt shows is folded into the record, so the app is verified by its first
     * successful break or the strategy is dropped by its failures - nobody has to press a test button first.
     */
    fun isTrialable(s: Strategy, stat: StrategyStat?): Boolean =
        s.canTryUnproven &&
            (stat == null || (!stat.everWorked && stat.lastTestedMs == 0L && stat.consecutiveFails < MAX_CONSECUTIVE_FAILS))

    /** Experimental strategies stay off unless the user switched them on. */
    fun allowedBySettings(s: Strategy, p: PlaybackSettings): Boolean = when (s) {
        Strategy.MEDIA_KEY -> p.allowMediaKeys
        Strategy.AUDIO_FOCUS -> p.allowAudioFocus
        Strategy.A11Y_GESTURE, Strategy.A11Y_NODE -> p.allowGestureTap
        else -> true
    }

    /** True when a strategy's earlier results still apply to the installed version of the app. */
    private fun appliesTo(record: CompatRecord, s: Strategy, installedVersionCode: Long): Boolean =
        !s.versionSensitive || record.versionCode == 0L || installedVersionCode == 0L || record.versionCode == installedVersionCode

    /** The best strategy that has *proved* itself on this app, or null. */
    fun proven(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): Strategy? = provenAll(record, available, settings, installedVersionCode).firstOrNull()

    private fun provenAll(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long,
    ): List<Strategy> {
        if (record == null) return emptyList()
        return available
            .filter { allowedBySettings(it, settings) }
            .filter { appliesTo(record, it, installedVersionCode) }
            .filter { isUsable(record.stats[it]) }
            .sortedBy { it.rank }
    }

    private fun trialAll(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long,
    ): List<Strategy> = available
        .filter { allowedBySettings(it, settings) }
        .filter { record == null || appliesTo(record, it, installedVersionCode) }
        .filter { isTrialable(it, record?.stats?.get(it)) }
        .sortedWith(compareBy<Strategy>({ record?.stats?.get(it)?.consecutiveFails ?: 0 }, { it.rank }))

    /**
     * The strategy a break should try because nothing is proven yet, or null. The one that has failed least goes
     * first (then the better ranked), so a strategy that does not work here is passed over after a single failure
     * instead of three.
     */
    fun trial(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): Strategy? = trialAll(record, available, settings, installedVersionCode).firstOrNull()

    /**
     * Every strategy a break may use on this app, in the order to try them: the proven ones by rank, then the ones worth
     * trying. A break moves on to the next when one cannot see the player at all or cannot deliver its command, which
     * costs the video nothing, so a way that is blind here (no media session through notification access, say) cannot
     * stand in the way of one that sees (the same sessions through root).
     */
    fun candidates(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): List<Strategy> = provenAll(record, available, settings, installedVersionCode) +
        trialAll(record, available, settings, installedVersionCode)

    /**
     * The strategy to use for real, or null to blink without touching playback: a proven one, else one worth trying.
     */
    fun best(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): Strategy? = candidates(record, available, settings, installedVersionCode).firstOrNull()

    /** What the person is told about an app; only a proven strategy makes it [CompatStatus.VERIFIED]. */
    fun status(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): CompatStatus {
        if (record == null || record.stats.isEmpty()) return CompatStatus.UNTESTED
        if (proven(record, available, settings, installedVersionCode) != null) return CompatStatus.VERIFIED
        if (trial(record, available, settings, installedVersionCode) != null) return CompatStatus.UNTESTED
        val outdated = record.stats.keys.any { it.versionSensitive && !appliesTo(record, it, installedVersionCode) }
        return if (outdated) CompatStatus.RETEST_ADVISED else CompatStatus.FAILING
    }

    /**
     * What a new build of EyeBlink keeps of what the old one learned about an app. Successes stay. Failures are forgotten: a
     * way that was switched off by a failed test or by a run of failed breaks - perhaps by a mistake the new build no longer
     * makes - gets another chance, and what really happens fills the record again. Null when nothing is left, which makes the
     * app untested again.
     */
    fun forgiven(record: CompatRecord): CompatRecord? {
        val kept = record.stats.filterValues { it.everWorked }.mapValues { it.value.copy(consecutiveFails = 0) }
        return if (kept.isEmpty()) null else record.copy(stats = kept)
    }

    /** What is remembered of an app, in one line for a bug report: counts and verdicts only, never a name. */
    fun describe(record: CompatRecord?): String {
        if (record == null || record.stats.isEmpty()) return "nothing remembered"
        return record.stats.entries.sortedBy { it.key.rank }.joinToString("; ") { (strategy, stat) ->
            buildString {
                append(strategy.name).append(": pause ").append(stat.pauseOk).append('/').append(stat.pauseOk + stat.pauseFail)
                append(", resume ").append(stat.resumeOk).append('/').append(stat.resumeOk + stat.resumeFail)
                if (stat.consecutiveFails > 0) append(", ").append(stat.consecutiveFails).append(" failed in a row")
                if (stat.lastTestedMs != 0L) append(if (stat.testPassed) ", test passed" else ", test failed")
            }
        }
    }

    /** Fold one pause/resume attempt into the record. [fromTest] marks a deliberate test run. */
    fun record(
        record: CompatRecord,
        strategy: Strategy,
        pauseVerified: Boolean,
        resumeVerified: Boolean,
        latencyMs: Long,
        nowMs: Long,
        fromTest: Boolean,
        versionCode: Long = record.versionCode,
        versionName: String = record.versionName,
    ): CompatRecord {
        // A version bump drops results that depended on the old build's internals - before this attempt is folded in,
        // so that a failed retest does not inherit the successes of a build that is gone.
        val bumped = versionCode != record.versionCode && versionCode != 0L
        val kept = if (bumped) record.stats.filterKeys { !it.versionSensitive } else record.stats
        val old = kept[strategy] ?: StrategyStat()
        val ok = pauseVerified && resumeVerified
        val next = old.copy(
            pauseOk = old.pauseOk + if (pauseVerified) 1 else 0,
            pauseFail = old.pauseFail + if (pauseVerified) 0 else 1,
            resumeOk = old.resumeOk + if (resumeVerified) 1 else 0,
            resumeFail = old.resumeFail + if (resumeVerified) 0 else 1,
            // A deliberate test starts from a clean slate; real use accumulates streaks.
            consecutiveFails = if (ok) 0 else if (fromTest) 1 else old.consecutiveFails + 1,
            lastTestedMs = if (fromTest) nowMs else old.lastTestedMs,
            lastOkMs = if (ok) nowMs else old.lastOkMs,
            lastLatencyMs = latencyMs,
            testPassed = if (fromTest) ok else old.testPassed,
        )
        return record.copy(versionCode = versionCode, versionName = versionName, stats = kept + (strategy to next))
    }
}

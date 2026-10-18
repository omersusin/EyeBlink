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
    ): Strategy? {
        if (record == null) return null
        return available
            .filter { allowedBySettings(it, settings) }
            .filter { appliesTo(record, it, installedVersionCode) }
            .filter { isUsable(record.stats[it]) }
            .minByOrNull { it.rank }
    }

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
    ): Strategy? = available
        .filter { allowedBySettings(it, settings) }
        .filter { record == null || appliesTo(record, it, installedVersionCode) }
        .filter { isTrialable(it, record?.stats?.get(it)) }
        .minWithOrNull(compareBy<Strategy>({ record?.stats?.get(it)?.consecutiveFails ?: 0 }, { it.rank }))

    /**
     * The strategy to use for real, or null to blink without touching playback: a proven one, else one worth trying.
     */
    fun best(
        record: CompatRecord?,
        available: Set<Strategy>,
        settings: PlaybackSettings,
        installedVersionCode: Long = 0L,
    ): Strategy? = proven(record, available, settings, installedVersionCode)
        ?: trial(record, available, settings, installedVersionCode)

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
        val old = record.stats[strategy] ?: StrategyStat()
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
        // A version bump drops results that depended on the old build's internals.
        val survives = if (versionCode != record.versionCode && versionCode != 0L) {
            record.stats.filterKeys { !it.versionSensitive }
        } else {
            record.stats
        }
        return record.copy(versionCode = versionCode, versionName = versionName, stats = survives + (strategy to next))
    }
}

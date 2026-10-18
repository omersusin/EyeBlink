package com.eyeblink.core.playback

import com.eyeblink.core.model.PlaybackSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatPolicyTest {

    private val settings = PlaybackSettings()
    private val all = Strategy.entries.toSet()

    private fun record(vararg results: Triple<Strategy, Boolean, Boolean>, version: Long = 100): CompatRecord {
        var r = CompatRecord("com.app", versionCode = version)
        for ((s, pause, resume) in results) r = CompatPolicy.record(r, s, pause, resume, 120, 1_000, fromTest = true, versionCode = version)
        return r
    }

    private val sessionBased = setOf(Strategy.MEDIA_SESSION, Strategy.PRIVILEGED_SESSION)
    private val experimental = setOf(Strategy.MEDIA_KEY, Strategy.AUDIO_FOCUS, Strategy.A11Y_GESTURE)
    private val allExperimentalOn = PlaybackSettings(allowMediaKeys = true, allowAudioFocus = true, allowGestureTap = true)

    /** One break that tried [s] on an app and failed: the pause did not land. */
    private fun failedAttempt(r: CompatRecord?, s: Strategy, t: Long = 5_000) =
        CompatPolicy.record(r ?: CompatRecord("com.app", versionCode = 100), s, false, false, 100, t, fromTest = false, versionCode = 100)

    @Test
    fun `an app that was never tested is untested and nothing but a session based strategy is tried on it`() {
        assertEquals(CompatStatus.UNTESTED, CompatPolicy.status(null, all, settings))
        assertNull("no strategy at all is available", CompatPolicy.best(null, emptySet(), settings))
        assertNull("experimental ones are never tried without a test", CompatPolicy.best(null, experimental, allExperimentalOn))
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(null, sessionBased, settings))
        assertEquals(Strategy.PRIVILEGED_SESSION, CompatPolicy.best(null, setOf(Strategy.PRIVILEGED_SESSION, Strategy.MEDIA_KEY), settings))
        assertNull("nothing is proven", CompatPolicy.proven(null, sessionBased, settings))
    }

    @Test
    fun `root alone is enough - the first break tries it without any test`() {
        // The case that did not work: root granted, nothing ever tested.
        assertEquals(Strategy.PRIVILEGED_SESSION, CompatPolicy.best(null, setOf(Strategy.PRIVILEGED_SESSION), settings))
        assertEquals(CompatStatus.UNTESTED, CompatPolicy.status(null, setOf(Strategy.PRIVILEGED_SESSION), settings))
    }

    @Test
    fun `the first successful break proves the strategy and verifies the app`() {
        val r = CompatPolicy.record(CompatRecord("com.app", 100), Strategy.PRIVILEGED_SESSION, true, true, 300, 9_000, fromTest = false, versionCode = 100)
        assertEquals(Strategy.PRIVILEGED_SESSION, CompatPolicy.proven(r, sessionBased, settings))
        assertEquals(CompatStatus.VERIFIED, CompatPolicy.status(r, sessionBased, settings))
        assertEquals("a success in real use is remembered as one", 9_000L, r.stats.getValue(Strategy.PRIVILEGED_SESSION).lastOkMs)
        assertEquals(0L, r.stats.getValue(Strategy.PRIVILEGED_SESSION).lastTestedMs)
    }

    @Test
    fun `a proven strategy is preferred over a better ranked one nobody has tried`() {
        val r = record(Triple(Strategy.PRIVILEGED_SESSION, true, true))
        assertEquals(Strategy.PRIVILEGED_SESSION, CompatPolicy.best(r, sessionBased, settings))
    }

    @Test
    fun `a failed attempt moves on to the next strategy and three failures each end the trying`() {
        var r: CompatRecord? = null
        r = failedAttempt(r, Strategy.MEDIA_SESSION)
        assertEquals("the one that has not failed goes next", Strategy.PRIVILEGED_SESSION, CompatPolicy.best(r, sessionBased, settings))
        assertEquals(CompatStatus.UNTESTED, CompatPolicy.status(r, sessionBased, settings))
        r = failedAttempt(r, Strategy.PRIVILEGED_SESSION)
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, sessionBased, settings))
        repeat(CompatPolicy.MAX_CONSECUTIVE_FAILS - 1) {
            r = failedAttempt(r, Strategy.MEDIA_SESSION)
            r = failedAttempt(r, Strategy.PRIVILEGED_SESSION)
        }
        assertNull("both have failed their three", CompatPolicy.best(r, sessionBased, settings))
        assertEquals(CompatStatus.FAILING, CompatPolicy.status(r, sessionBased, settings))
    }

    @Test
    fun `the candidates are the proven ones by rank, then the ones worth trying, fewest failures first`() {
        // nothing known: every strategy that can be tried, best ranked first
        assertEquals(
            listOf(Strategy.MEDIA_SESSION, Strategy.PRIVILEGED_SESSION),
            CompatPolicy.candidates(null, all, settings),
        )
        // MEDIA_SESSION failed once: PRIVILEGED_SESSION (no failures) goes before it, and a proven MEDIA_KEY comes first of all
        var r = record(Triple(Strategy.MEDIA_KEY, true, true))
        r = failedAttempt(r, Strategy.MEDIA_SESSION)
        assertEquals(
            listOf(Strategy.MEDIA_KEY, Strategy.PRIVILEGED_SESSION, Strategy.MEDIA_SESSION),
            CompatPolicy.candidates(r, all, settings.copy(allowMediaKeys = true)),
        )
        assertEquals(Strategy.MEDIA_KEY, CompatPolicy.best(r, all, settings.copy(allowMediaKeys = true)))
        assertTrue(CompatPolicy.candidates(r, emptySet(), settings).isEmpty())
    }

    @Test
    fun `a deliberate test that failed rules the strategy out for real breaks`() {
        val r = record(Triple(Strategy.MEDIA_SESSION, false, false))
        assertNull(CompatPolicy.best(r, setOf(Strategy.MEDIA_SESSION), settings))
        assertEquals(CompatStatus.FAILING, CompatPolicy.status(r, setOf(Strategy.MEDIA_SESSION), settings))
        assertEquals("another strategy is still worth a try", Strategy.PRIVILEGED_SESSION, CompatPolicy.best(r, sessionBased, settings))
    }

    @Test
    fun `a strategy that once worked and was switched off by failures is not tried again until a test`() {
        var r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        repeat(CompatPolicy.MAX_CONSECUTIVE_FAILS) { r = failedAttempt(r, Strategy.MEDIA_SESSION) }
        assertNull(CompatPolicy.best(r, setOf(Strategy.MEDIA_SESSION), settings))
    }

    @Test
    fun `a passed test makes the strategy usable and the app verified`() {
        val r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, all, settings))
        assertEquals(CompatStatus.VERIFIED, CompatPolicy.status(r, all, settings))
    }

    @Test
    fun `a failed test is failing not verified`() {
        val r = record(Triple(Strategy.MEDIA_SESSION, true, false))
        assertNull(CompatPolicy.best(r, setOf(Strategy.MEDIA_SESSION), settings))
        assertEquals(CompatStatus.FAILING, CompatPolicy.status(r, setOf(Strategy.MEDIA_SESSION), settings))
    }

    @Test
    fun `pause alone is not enough - it must resume too`() {
        val r = record(Triple(Strategy.MEDIA_SESSION, true, false))
        assertFalse(CompatPolicy.isUsable(r.stats[Strategy.MEDIA_SESSION]))
    }

    @Test
    fun `the best ranked usable strategy wins`() {
        val r = record(
            Triple(Strategy.PRIVILEGED_SESSION, true, true),
            Triple(Strategy.MEDIA_SESSION, true, true),
        )
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, all, settings))
        assertEquals(Strategy.PRIVILEGED_SESSION, CompatPolicy.best(r, setOf(Strategy.PRIVILEGED_SESSION), settings))
    }

    @Test
    fun `a strategy the device cannot currently provide is not chosen`() {
        val r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        assertNull(CompatPolicy.best(r, setOf(Strategy.MEDIA_KEY), settings))
        assertNull(CompatPolicy.best(r, emptySet(), settings))
    }

    @Test
    fun `experimental strategies stay off until the user enables them`() {
        val r = record(Triple(Strategy.MEDIA_KEY, true, true), Triple(Strategy.A11Y_GESTURE, true, true), Triple(Strategy.AUDIO_FOCUS, true, true))
        assertNull(CompatPolicy.best(r, experimental, settings))
        assertEquals(Strategy.MEDIA_KEY, CompatPolicy.best(r, experimental, settings.copy(allowMediaKeys = true)))
        assertEquals(Strategy.AUDIO_FOCUS, CompatPolicy.best(r, experimental, settings.copy(allowAudioFocus = true)))
        assertEquals(Strategy.A11Y_GESTURE, CompatPolicy.best(r, experimental, settings.copy(allowGestureTap = true)))
    }

    @Test
    fun `only strategies that read the player back may be tried unproven`() {
        assertEquals(
            setOf(Strategy.MEDIA_SESSION, Strategy.PRIVILEGED_SESSION),
            Strategy.entries.filter { it.canTryUnproven }.toSet(),
        )
        assertTrue(Strategy.entries.filter { it.canTryUnproven }.all { it.verification == Verification.SESSION_STATE && !it.experimental })
        assertTrue("a hook inside the app's own player is never tried without a test", !Strategy.LSPOSED_HOOK.canTryUnproven)
    }

    @Test
    fun `three real failures in a row switch a strategy off until it is retested`() {
        val only = setOf(Strategy.MEDIA_SESSION)
        var r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        repeat(CompatPolicy.MAX_CONSECUTIVE_FAILS - 1) {
            r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, false, 100, 2_000, fromTest = false)
            assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, only, settings))
        }
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, false, 100, 3_000, fromTest = false)
        assertNull(CompatPolicy.best(r, only, settings))
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, true, 100, 4_000, fromTest = true)
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, only, settings))
    }

    @Test
    fun `a success resets the failure streak`() {
        var r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, false, 100, 2_000, fromTest = false)
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, true, 100, 3_000, fromTest = false)
        assertEquals(0, r.stats.getValue(Strategy.MEDIA_SESSION).consecutiveFails)
    }

    @Test
    fun `an app update invalidates version sensitive strategies but not framework level ones`() {
        val r = record(
            Triple(Strategy.LSPOSED_HOOK, true, true),
            Triple(Strategy.MEDIA_SESSION, true, true),
            version = 100,
        )
        assertEquals(Strategy.LSPOSED_HOOK, CompatPolicy.best(r, all, settings, installedVersionCode = 100))
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, all, settings, installedVersionCode = 101))
    }

    @Test
    fun `retest is advised when only version sensitive results are outdated`() {
        val r = record(Triple(Strategy.LSPOSED_HOOK, true, true), version = 100)
        assertEquals(CompatStatus.RETEST_ADVISED, CompatPolicy.status(r, setOf(Strategy.LSPOSED_HOOK), settings, installedVersionCode = 101))
    }

    @Test
    fun `recording under a new version drops stale version sensitive stats`() {
        var r = record(Triple(Strategy.LSPOSED_HOOK, true, true), Triple(Strategy.MEDIA_SESSION, true, true), version = 100)
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, true, 90, 5_000, fromTest = true, versionCode = 101)
        assertFalse(Strategy.LSPOSED_HOOK in r.stats)
        assertTrue(Strategy.MEDIA_SESSION in r.stats)
        assertEquals(101L, r.versionCode)
    }

    @Test
    fun `strategies that cannot verify anything are all marked`() {
        // Every strategy declares how it verifies; none is allowed to be verification free.
        assertTrue(Strategy.entries.all { it.verification in Verification.entries })
        assertTrue(Strategy.entries.filter { it.experimental }.all { it.verification == Verification.AUDIO_ACTIVITY })
        assertEquals(Strategy.entries.sortedBy { it.rank }, Strategy.entries)
    }
}

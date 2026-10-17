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

    @Test
    fun `an app that was never tested is untested and uses no strategy`() {
        assertEquals(CompatStatus.UNTESTED, CompatPolicy.status(null, all, settings))
        assertNull(CompatPolicy.best(null, all, settings))
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
        assertNull(CompatPolicy.best(r, all, settings))
        assertEquals(CompatStatus.FAILING, CompatPolicy.status(r, all, settings))
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
    }

    @Test
    fun `experimental strategies stay off until the user enables them`() {
        val r = record(Triple(Strategy.MEDIA_KEY, true, true), Triple(Strategy.A11Y_GESTURE, true, true), Triple(Strategy.AUDIO_FOCUS, true, true))
        assertNull(CompatPolicy.best(r, all, settings))
        assertEquals(Strategy.MEDIA_KEY, CompatPolicy.best(r, all, settings.copy(allowMediaKeys = true)))
        assertEquals(Strategy.AUDIO_FOCUS, CompatPolicy.best(r, all, settings.copy(allowAudioFocus = true)))
        assertEquals(Strategy.A11Y_GESTURE, CompatPolicy.best(r, all, settings.copy(allowGestureTap = true)))
    }

    @Test
    fun `three real failures in a row switch a strategy off until it is retested`() {
        var r = record(Triple(Strategy.MEDIA_SESSION, true, true))
        repeat(CompatPolicy.MAX_CONSECUTIVE_FAILS - 1) {
            r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, false, 100, 2_000, fromTest = false)
            assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, all, settings))
        }
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, false, 100, 3_000, fromTest = false)
        assertNull(CompatPolicy.best(r, all, settings))
        r = CompatPolicy.record(r, Strategy.MEDIA_SESSION, true, true, 100, 4_000, fromTest = true)
        assertEquals(Strategy.MEDIA_SESSION, CompatPolicy.best(r, all, settings))
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
        assertEquals(CompatStatus.RETEST_ADVISED, CompatPolicy.status(r, all, settings, installedVersionCode = 101))
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

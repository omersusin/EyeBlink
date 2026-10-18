package com.eyeblink.core.privileged

import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.engine.BreakDirector
import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakRequest
import com.eyeblink.core.engine.ContextSnapshot
import com.eyeblink.core.engine.DirectorEnvironment
import com.eyeblink.core.engine.GatePolicy
import com.eyeblink.core.engine.OverlayPort
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary
import com.eyeblink.core.engine.playbackVerdict
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.PlaybackSettings
import com.eyeblink.core.playback.CompatPolicy
import com.eyeblink.core.playback.CompatRecord
import com.eyeblink.core.playback.CompatStatus
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackCoordinator
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.rest.RestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val YOUTUBE = "com.google.android.youtube"
private const val SPOTIFY = "com.spotify.music"

/**
 * The whole path of a break on a phone with root, against a model of the phone's media session service that takes
 * its time like a real one: nothing has ever been tested, the first break has to pause the video, check the pause,
 * and give the video back - and what it learns has to be right.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootPauseEndToEndTest {

    /** `dumpsys` and the key commands cost time, and the player follows a key a while after it was sent. */
    private class FakePhone(private val scope: TestScope) {
        var state = 3 // PlaybackState.STATE_PLAYING
        /** Where the player really is; the dump of a playing session still says where it was when it last reported. */
        var truePositionMs = 45_210L
        private var lastReportedMs = 45_210L
        var buttonHolder: String? = YOUTUBE
        var dumpMs = 300L
        var keyMs = 100L
        var followsKeyAfterMs = 600L
        var shellAlive = true
        val keys = mutableListOf<String>()
        private var pending: Pair<Long, Int>? = null

        private fun now() = scope.testScheduler.currentTime

        private fun settle() {
            pending?.let { (at, s) ->
                if (now() >= at) {
                    // a player reports its real position when its state changes
                    if (s != 3) lastReportedMs = 45_210L + (at - startedAt)
                    state = s
                    pending = null
                }
            }
        }

        private val startedAt = scope.testScheduler.currentTime

        private fun stateName() = if (state == 3) "PLAYING" else "PAUSED"

        fun dump(): String {
            settle()
            return """
MEDIA SESSION SERVICE (dumpsys media_session)

Media button session is ${buttonHolder?.let { "$it/ParentHolder" } ?: "null"} (userId=0)
Sessions Stack - have 2 sessions:
  ParentHolder $YOUTUBE/ParentHolder (userId=0)
    ownerPid=12345, ownerUid=10234, userId=0
    package=$YOUTUBE
    active=true
    state=PlaybackState {state=${stateName()}($state), position=${if (state == 3) 45210L else lastReportedMs}, buffered position=60000, speed=1.0, updated=123456789, actions=823, custom actions=[], active item id=-1, error=null}
    metadata: size=5, description=Funny cat compilation, SomeChannel, null
  spotify $SPOTIFY/spotify (userId=0)
    package=$SPOTIFY
    active=true
    state=PlaybackState {state=2, position=1000, buffered position=0, speed=0.0, updated=1, actions=1, custom actions=[], active item id=-1, error=null}
            """.trimIndent()
        }

        val shell = ShellExecutor { command, _ ->
            when {
                !shellAlive -> ShellResult.failure("su: denied")
                command.startsWith("dumpsys media_session") -> { delay(dumpMs); ShellResult(0, dump()) }
                command == "cmd media_session dispatch pause" -> key("pause", 2)
                command == "cmd media_session dispatch play" -> key("play", 3)
                else -> ShellResult(127, "not scripted: $command")
            }
        }

        private suspend fun key(name: String, becomes: Int): ShellResult {
            delay(keyMs)
            keys += name
            pending = now() + followsKeyAfterMs to becomes
            return ShellResult(0, "")
        }
    }

    private class Overlay(private val scope: TestScope) : OverlayPort {
        val log = mutableListOf<String>()
        override suspend fun playBlink(spec: BlinkSpec) { log += "blink@${scope.testScheduler.currentTime}"; delay(900) }
        override suspend fun runRest(session: RestSession, spec: BlinkSpec) = error("not used")
        override fun clear() { log += "clear" }
    }

    private class Env(private val scope: TestScope, private val backend: PlaybackBackend?) : DirectorEnvironment {
        override fun nowMs() = scope.testScheduler.currentTime
        override fun snapshot() = ContextSnapshot(nowMs = nowMs(), foregroundPackage = YOUTUBE)
        override fun gatePolicy(target: String?) = GatePolicy(appSelected = true)
        override suspend fun backendFor(target: String): PlaybackBackend? = backend
        override fun blinkConfirmAvailable() = false
    }

    private val settings = EyeBlinkSettings(apps = listOf(AppProfile(YOUTUBE)), playback = PlaybackSettings())

    /** The coordinator on the test's own clock, so that the time a slow shell takes counts as it does on a phone. */
    private fun TestScope.coordinator() = PlaybackCoordinator(clock = { testScheduler.currentTime })

    @Test
    fun `with root and nothing ever tested the first break pauses the video and gives it back, and that proves the app`() = runTest {
        val phone = FakePhone(this)
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val record: CompatRecord? = null
        val available = setOf(Strategy.PRIVILEGED_SESSION)

        // what the engine asks before a break
        val chosen = CompatPolicy.best(record, available, settings.playback)
        assertEquals(Strategy.PRIVILEGED_SESSION, chosen)

        val overlay = Overlay(this)
        val report = BreakDirector(overlay, coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)

        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(ResumeSummary.RESUMED, report.resume)
        assertEquals(listOf("pause", "play"), phone.keys)
        assertEquals("the video is playing again", 3, phone.state)
        assertTrue("the blink was drawn after the pause had landed", overlay.log.first().removePrefix("blink@").toLong() >= 600)

        val verdict = report.playbackVerdict()
        assertNotNull(verdict)
        val learned = CompatPolicy.record(CompatRecord(YOUTUBE), verdict!!.strategy, verdict.pauseOk, verdict.resumeOk, 700, 1_000, fromTest = false)
        assertEquals(CompatStatus.VERIFIED, CompatPolicy.status(learned, available, settings.playback))
    }

    @Test
    fun `a slow phone is not mistaken for a broken one`() = runTest {
        val phone = FakePhone(this).apply { dumpMs = 700; followsKeyAfterMs = 3_200 } // lands after the verify window
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val report = BreakDirector(Overlay(this), coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)

        assertEquals("the pause was not seen in time", PauseSummary.UNVERIFIED, report.pause)
        assertEquals("but the video was given back", ResumeSummary.RESUMED, report.resume)
        assertEquals(3, phone.state)
        val verdict = report.playbackVerdict()!!
        assertTrue("a late pause is a success, not a failure to learn from", verdict.pauseOk && verdict.resumeOk)
    }

    @Test
    fun `a late pause is still given back when the player has moved on by seconds meanwhile`() = runTest {
        // The dump of a playing session says where the player was when it last reported; when the late pause lands the
        // player reports where it really is, several seconds on. That is time passing, not the person scrubbing.
        val phone = FakePhone(this).apply { followsKeyAfterMs = 3_200 }
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val report = BreakDirector(Overlay(this), coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)
        assertEquals(PauseSummary.UNVERIFIED, report.pause)
        assertEquals(ResumeSummary.RESUMED, report.resume)
        assertEquals("the video is playing again", 3, phone.state)
    }

    @Test
    fun `a pause that is a little slow is verified at once`() = runTest {
        val phone = FakePhone(this).apply { dumpMs = 400; followsKeyAfterMs = 1_500 }
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val report = BreakDirector(Overlay(this), coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)
        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(ResumeSummary.RESUMED, report.resume)
    }

    @Test
    fun `when another app holds the media keys nothing is sent and the break says why`() = runTest {
        val phone = FakePhone(this).apply { buttonHolder = SPOTIFY }
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val overlay = Overlay(this)
        val report = BreakDirector(overlay, coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)

        assertEquals(PauseSummary.FAILED, report.pause)
        assertTrue(report.error!!.contains("different app"))
        assertTrue("no key may reach the other app", phone.keys.isEmpty())
        assertEquals("the blink still happens", 2, overlay.log.size)
        assertEquals("and the video is left as it was", 3, phone.state)
        val verdict = report.playbackVerdict()!!
        assertTrue(!verdict.pauseOk)
    }

    @Test
    fun `when the shell cannot be used nothing is touched and it is not held against the strategy`() = runTest {
        val phone = FakePhone(this).apply { shellAlive = false }
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val report = BreakDirector(Overlay(this), coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)
        assertEquals(PauseSummary.UNREADABLE, report.pause)
        assertNull(report.playbackVerdict())
        assertTrue(phone.keys.isEmpty())
    }

    @Test
    fun `a video that is already paused is not touched`() = runTest {
        val phone = FakePhone(this).apply { state = 2 }
        val backend = PrivilegedPlaybackBackend(phone.shell) { testScheduler.currentTime }
        val report = BreakDirector(Overlay(this), coordinator(), Env(this, backend)).run(BreakRequest(BreakKind.MICRO, YOUTUBE), settings)
        assertEquals(PauseSummary.NOT_PLAYING, report.pause)
        assertTrue(phone.keys.isEmpty())
    }
}

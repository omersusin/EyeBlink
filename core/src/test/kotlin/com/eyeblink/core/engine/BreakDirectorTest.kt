package com.eyeblink.core.engine

import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.PlaybackSettings
import com.eyeblink.core.model.RestSettings
import com.eyeblink.core.model.RestStyle
import com.eyeblink.core.playback.FakePlayer
import com.eyeblink.core.playback.PlayState
import com.eyeblink.core.playback.PlaybackBackend
import com.eyeblink.core.playback.PlaybackCoordinator
import com.eyeblink.core.playback.Strategy
import com.eyeblink.core.rest.RestOutcome
import com.eyeblink.core.rest.RestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP = "com.google.android.youtube"

/** Records what the director did, in order, against a shared timeline with the player. */
private class FakeOverlay(private val scope: TestScope, private val log: MutableList<String>) : OverlayPort {
    var blinkThrows = false
    var onBlinkMidway: (() -> Unit)? = null
    var restScript: (suspend (RestSession) -> Unit)? = null
    var clears = 0

    override suspend fun playBlink(spec: BlinkSpec) {
        log += "blink-start"
        if (blinkThrows) error("render failed")
        delay(spec.totalMs / 2L)
        onBlinkMidway?.invoke()
        delay(spec.totalMs - spec.totalMs / 2L)
        log += "blink-end"
    }

    override suspend fun runRest(session: RestSession, spec: BlinkSpec) {
        log += "rest-start"
        restScript?.invoke(session)
        while (!session.view(scope.testScheduler.currentTime).let { it.outcome != null }) delay(100)
        log += "rest-end"
    }

    override fun clear() {
        clears++
        log += "clear"
    }
}

private class FakeEnv(
    private val scope: TestScope,
    val player: FakePlayer?,
    private val log: MutableList<String>,
    /** Backends after the first one, for the break that moves on. */
    private val later: List<PlaybackBackend> = emptyList(),
) : DirectorEnvironment {
    var ctx = ContextSnapshot(nowMs = 0, foregroundPackage = APP)
    var policy = GatePolicy(appSelected = true)
    var blinkConfirm = false

    override fun nowMs() = scope.testScheduler.currentTime
    override fun snapshot() = ctx.copy(nowMs = nowMs())
    override fun gatePolicy(target: String?) = policy
    override suspend fun backendFor(target: String): PlaybackBackend? = player
    override suspend fun backendsFor(target: String): List<PlaybackBackend> = listOfNotNull(player) + later
    override fun blinkConfirmAvailable() = blinkConfirm
}

@OptIn(ExperimentalCoroutinesApi::class)
class BreakDirectorTest {

    private val settings = EyeBlinkSettings(apps = listOf(AppProfile(APP)))

    private class Rig(scope: TestScope, withPlayer: Boolean = true, later: List<PlaybackBackend> = emptyList()) {
        val log = mutableListOf<String>()
        val player = if (withPlayer) FakePlayer(clock = { scope.testScheduler.currentTime }) else null
        val env = FakeEnv(scope, player, log, later)
        val overlay = FakeOverlay(scope, log)
        val director = BreakDirector(overlay, PlaybackCoordinator(), env)

        init {
            player?.let { p ->
                // mirror player commands into the shared log so ordering is observable
                // (the fake records its own list; we merge by checking it at the end)
            }
        }

        /** Player commands interleaved by phase, reconstructed from the log markers. */
        fun commands() = player?.commands.orEmpty()
    }

    private fun micro(forced: Boolean = false) = BreakRequest(BreakKind.MICRO, APP, forced)

    // --- micro blink -------------------------------------------------------

    @Test
    fun `a micro blink pauses the video draws and resumes it`() = runTest {
        val rig = Rig(this)
        val report = rig.director.run(micro(), settings)
        assertEquals(BreakOutcome.COMPLETED, report.outcome)
        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(ResumeSummary.RESUMED, report.resume)
        assertEquals(Strategy.MEDIA_SESSION, report.strategy)
        assertEquals(listOf("pause", "play"), rig.commands())
        assertEquals(listOf("blink-start", "blink-end", "clear"), rig.log)
        assertEquals(PlayState.PLAYING, rig.player!!.state)
    }

    @Test
    fun `the pause lands before the animation starts`() = runTest {
        val rig = Rig(this)
        var stateWhenBlinkStarted: PlayState? = null
        rig.overlay.onBlinkMidway = { stateWhenBlinkStarted = rig.player!!.state }
        rig.director.run(micro(), settings)
        assertEquals(PlayState.PAUSED, stateWhenBlinkStarted)
    }

    @Test
    fun `with no proven backend the blink happens and playback is untouched`() = runTest {
        val rig = Rig(this, withPlayer = false)
        val report = rig.director.run(micro(), settings)
        assertEquals(BreakOutcome.COMPLETED, report.outcome)
        assertEquals(PauseSummary.NOT_ATTEMPTED, report.pause)
        assertEquals(ResumeSummary.NOT_ATTEMPTED, report.resume)
        assertEquals(null, report.strategy)
    }

    @Test
    fun `an app with no player to read and a player that cannot be read are reported as such`() = runTest {
        val none = Rig(this)
        none.player!!.state = PlayState.NONE
        assertEquals(PauseSummary.NO_SESSION, none.director.run(micro(), settings).pause)
        val unreadable = Rig(this)
        unreadable.player!!.reportsUnknown = true
        assertEquals(PauseSummary.UNREADABLE, unreadable.director.run(micro(), settings).pause)
        assertTrue(none.commands().isEmpty() && unreadable.commands().isEmpty())
    }

    @Test
    fun `a video the user already paused is left paused`() = runTest {
        val rig = Rig(this)
        rig.player!!.state = PlayState.PAUSED
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.NOT_PLAYING, report.pause)
        assertEquals(ResumeSummary.NOT_ATTEMPTED, report.resume)
        assertTrue("no playback command may be sent", rig.commands().isEmpty())
        assertEquals(PlayState.PAUSED, rig.player.state)
    }

    @Test
    fun `playback the user restarted during the blink is not touched again`() = runTest {
        val rig = Rig(this)
        rig.overlay.onBlinkMidway = { rig.player!!.userPlays() }
        val report = rig.director.run(micro(), settings)
        assertEquals(ResumeSummary.ALREADY_PLAYING, report.resume)
        assertEquals(listOf("pause"), rig.commands())
    }

    @Test
    fun `swiping to the next video during the blink prevents the resume`() = runTest {
        val rig = Rig(this)
        rig.overlay.onBlinkMidway = { rig.player!!.userSwipesToNextVideo() }
        val report = rig.director.run(micro(), settings)
        assertEquals(ResumeSummary.REFUSED, report.resume)
        assertEquals(listOf("pause"), rig.commands())
    }

    @Test
    fun `switching apps during the blink prevents the resume`() = runTest {
        val rig = Rig(this)
        rig.overlay.onBlinkMidway = { rig.env.ctx = rig.env.ctx.copy(foregroundPackage = "com.android.launcher") }
        val report = rig.director.run(micro(), settings)
        assertEquals(ResumeSummary.REFUSED, report.resume)
    }

    @Test
    fun `pausing can be turned off globally or per app`() = runTest {
        val off = settings.copy(playback = PlaybackSettings(pauseDuringBreaks = false))
        val rig = Rig(this)
        assertEquals(PauseSummary.NOT_ATTEMPTED, rig.director.run(micro(), off).pause)

        val perApp = settings.copy(apps = listOf(AppProfile(APP, pauseVideo = false)))
        val rig2 = Rig(this)
        assertEquals(PauseSummary.NOT_ATTEMPTED, rig2.director.run(micro(), perApp).pause)
        assertTrue(rig2.commands().isEmpty())
    }

    @Test
    fun `resuming can be turned off and then the video stays paused`() = runTest {
        val rig = Rig(this)
        val report = rig.director.run(micro(), settings.copy(playback = PlaybackSettings(resumeAfterBreak = false)))
        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(ResumeSummary.NOT_ATTEMPTED, report.resume)
    }

    // --- moving on to the next backend ---------------------------------------

    private fun TestScope.spare(strategy: Strategy = Strategy.PRIVILEGED_SESSION) =
        FakePlayer(clock = { testScheduler.currentTime }, strategy = strategy)

    @Test
    fun `a backend that cannot see the player is followed by one that can`() = runTest {
        val second = spare()
        val rig = Rig(this, later = listOf(second))
        rig.player!!.state = PlayState.NONE
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(ResumeSummary.RESUMED, report.resume)
        assertEquals("the one that paused is the one named", Strategy.PRIVILEGED_SESSION, report.strategy)
        assertTrue("the blind one sent nothing", rig.commands().isEmpty())
        assertEquals(listOf("pause", "play"), second.commands)
    }

    @Test
    fun `a backend that cannot deliver its command is followed by one that can, without a stale error`() = runTest {
        val second = spare()
        val rig = Rig(this, later = listOf(second))
        rig.player!!.deliversCommands = false
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.PAUSED, report.pause)
        assertEquals(null, report.error)
        assertEquals(listOf("pause", "play"), second.commands)
    }

    @Test
    fun `once a command has gone out no other backend is asked`() = runTest {
        val second = spare()
        val rig = Rig(this, later = listOf(second))
        rig.player!!.obeysPause = false
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.UNVERIFIED, report.pause)
        assertTrue("a second pause could stop something the first one reached late", second.commands.isEmpty())
    }

    @Test
    fun `a backend that sees a video that is not playing ends the search`() = runTest {
        val second = spare()
        val rig = Rig(this, later = listOf(second))
        rig.player!!.state = PlayState.PAUSED
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.NOT_PLAYING, report.pause)
        assertTrue(second.commands.isEmpty())
        assertEquals("the second was never asked, so it is not the one named", Strategy.MEDIA_SESSION, report.strategy)
    }

    @Test
    fun `when no backend can see a player the break says so`() = runTest {
        val blind = spare().apply { state = PlayState.NONE }
        val rig = Rig(this, later = listOf(blind))
        rig.player!!.state = PlayState.NONE
        assertEquals(PauseSummary.NO_SESSION, rig.director.run(micro(), settings).pause)

        val unreadable = spare().apply { reportsUnknown = true }
        val rig2 = Rig(this, later = listOf(unreadable))
        rig2.player!!.reportsUnknown = true
        assertEquals(PauseSummary.UNREADABLE, rig2.director.run(micro(), settings).pause)

        val mixed = spare().apply { reportsUnknown = true }
        val rig3 = Rig(this, later = listOf(mixed))
        rig3.player!!.state = PlayState.NONE
        assertEquals("one that could look and found no session outweighs one that could not look", PauseSummary.NO_SESSION, rig3.director.run(micro(), settings).pause)
    }

    // --- re-check ----------------------------------------------------------

    @Test
    fun `a call that starts before the break begins aborts it before anything happens`() = runTest {
        val rig = Rig(this)
        rig.env.ctx = rig.env.ctx.copy(audioMode = AudioMode.IN_CALL)
        val report = rig.director.run(micro(), settings)
        assertEquals(BreakOutcome.ABORTED, report.outcome)
        assertEquals(Reason.IN_CALL, report.abortedBy)
        assertTrue(rig.log.isEmpty())
        assertTrue(rig.commands().isEmpty())
    }

    @Test
    fun `a soft obstacle aborts an ordinary break but not a forced one`() = runTest {
        val rig = Rig(this)
        rig.env.ctx = rig.env.ctx.copy(lastScrollMs = 0)
        assertEquals(BreakOutcome.ABORTED, rig.director.run(micro(forced = false), settings).outcome)
        val rig2 = Rig(this)
        rig2.env.ctx = rig2.env.ctx.copy(lastScrollMs = 0)
        assertEquals(BreakOutcome.COMPLETED, rig2.director.run(micro(forced = true), settings).outcome)
    }

    @Test
    fun `even a forced break respects hard holds`() = runTest {
        val rig = Rig(this)
        rig.env.ctx = rig.env.ctx.copy(keyboardVisible = true)
        assertEquals(BreakOutcome.ABORTED, rig.director.run(micro(forced = true), settings).outcome)
    }

    // --- failure and cancellation -----------------------------------------

    @Test
    fun `a rendering failure still clears the overlay and gives the video back`() = runTest {
        val rig = Rig(this)
        rig.overlay.blinkThrows = true
        val report = rig.director.run(micro(), settings)
        assertEquals(BreakOutcome.FAILED, report.outcome)
        assertNotNull(report.error)
        assertTrue(rig.overlay.clears >= 1)
        assertEquals("video must not be left paused", PlayState.PLAYING, rig.player!!.state)
    }

    @Test
    fun `cancelling mid blink clears the overlay and resumes the video`() = runTest {
        val rig = Rig(this)
        val job = launch { rig.director.run(micro(), settings) }
        advanceTimeBy(200)
        runCurrent()
        assertEquals(PlayState.PAUSED, rig.player!!.state)
        job.cancel(CancellationException("emergency stop"))
        job.join()
        assertTrue(rig.overlay.clears >= 1)
        assertEquals("emergency stop must not strand a paused video", PlayState.PLAYING, rig.player.state)
    }

    @Test
    fun `cancelling before any pause leaves playback alone`() = runTest {
        val rig = Rig(this)
        rig.player!!.state = PlayState.PAUSED
        val job = launch { rig.director.run(micro(), settings) }
        advanceTimeBy(100)
        job.cancel()
        job.join()
        assertTrue(rig.commands().isEmpty())
    }

    // --- rest --------------------------------------------------------------

    private val restSettings = settings.copy(
        rest = RestSettings(style = RestStyle.DISTANCE_FOCUS, durationSec = 20, confirm = ConfirmMode.AUTO),
    )

    @Test
    fun `a rest pauses the video runs the full countdown and resumes`() = runTest {
        val rig = Rig(this)
        val report = rig.director.run(BreakRequest(BreakKind.REST, APP), restSettings)
        assertEquals(BreakOutcome.COMPLETED, report.outcome)
        assertEquals(RestOutcome.COMPLETED, report.restOutcome)
        assertEquals(ResumeSummary.RESUMED, report.resume)
        assertTrue("rest should last about 20s plus settle: ${testScheduler.currentTime}", testScheduler.currentTime >= 20_000)
        assertEquals(listOf("pause", "play"), rig.commands())
    }

    @Test
    fun `skipping a rest ends it at once and still resumes the video`() = runTest {
        val rig = Rig(this)
        val job = launch { rig.director.run(BreakRequest(BreakKind.REST, APP), restSettings) }
        advanceTimeBy(3_000)
        runCurrent()
        rig.director.skipRest()
        job.join()
        assertTrue("skipped well before 20s: ${testScheduler.currentTime}", testScheduler.currentTime < 5_000)
        assertEquals(PlayState.PLAYING, rig.player!!.state)
    }

    @Test
    fun `the skip is reported to the scheduler as skipped by the user`() = runTest {
        val rig = Rig(this)
        var report: BreakReport? = null
        val job = launch { report = rig.director.run(BreakRequest(BreakKind.REST, APP), restSettings) }
        advanceTimeBy(2_000); runCurrent()
        rig.director.skipRest()
        job.join()
        assertEquals(BreakOutcome.SKIPPED_BY_USER, report!!.outcome)
        assertEquals(RestOutcome.SKIPPED, report!!.restOutcome)
    }

    @Test
    fun `a blink confirmed rest waits for the blink then resumes`() = runTest {
        val rig = Rig(this)
        rig.env.blinkConfirm = true
        val s = restSettings.copy(rest = restSettings.rest.copy(confirm = ConfirmMode.BLINK, durationSec = 5))
        var done = false
        val job = launch { rig.director.run(BreakRequest(BreakKind.REST, APP), s); done = true }
        advanceTimeBy(5_000 + RestSession.SETTLE_MS + 2_000)
        runCurrent()
        assertFalse("still waiting for a blink", done)
        assertTrue(rig.director.onUserBlink())
        job.join()
        assertTrue(done)
        assertEquals(PlayState.PLAYING, rig.player!!.state)
    }

    @Test
    fun `a long rest extends the resume window so the video is not stranded`() = runTest {
        val rig = Rig(this)
        val s = restSettings.copy(
            playback = PlaybackSettings(maxHoldSec = 5),
            rest = restSettings.rest.copy(durationSec = 30),
        )
        val report = rig.director.run(BreakRequest(BreakKind.REST, APP), s)
        assertEquals(ResumeSummary.RESUMED, report.resume)
    }

    @Test
    fun `a pause that lands after the break ends is still given back`() = runTest {
        val rig = Rig(this)
        rig.player!!.latencyMs = 3_200 // slower than the verify window, landing while the blink is on screen
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.UNVERIFIED, report.pause)
        assertEquals("a late pause must not strand the video", PlayState.PLAYING, rig.player.state)
        assertEquals(ResumeSummary.RESUMED, report.resume)
    }

    @Test
    fun `an unverified pause is not undone when the user touched the screen`() = runTest {
        val rig = Rig(this)
        rig.player!!.latencyMs = 2_800 // pause lands after the verify window but during the break
        rig.overlay.onBlinkMidway = {
            rig.env.ctx = rig.env.ctx.copy(lastTouchMs = testScheduler.currentTime)
        }
        val report = rig.director.run(micro(), settings)
        assertEquals(PauseSummary.UNVERIFIED, report.pause)
        assertEquals(ResumeSummary.REFUSED, report.resume)
    }
}

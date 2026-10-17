package com.eyeblink.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP = "com.google.android.youtube"

/**
 * These tests are the executable form of the spec's playback promises:
 *  - never pause what is not playing;
 *  - never claim a pause that was not observed;
 *  - never resume what the user paused, moved on from, or left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val coordinator = PlaybackCoordinator()

    private fun TestScope.player(
        state: PlayState = PlayState.PLAYING,
        latency: Long = 0,
        strategy: Strategy = Strategy.MEDIA_SESSION,
    ) = FakePlayer(clock = { testScheduler.currentTime }, strategy = strategy, state = state, latencyMs = latency)

    private fun TestScope.env(
        fg: String? = APP,
        maxHold: Long = 60_000,
        touched: Boolean = false,
    ) = { ResumeEnvironment(fg, testScheduler.currentTime, maxHold, touched) }

    private suspend fun TestScope.pause(p: FakePlayer) = coordinator.pause(p, APP) { testScheduler.currentTime }

    // --- pausing -----------------------------------------------------------

    @Test
    fun `a playing video is paused and the pause is verified`() = runTest {
        val p = player()
        val r = pause(p)
        assertTrue(r is PauseResult.Paused)
        assertEquals(PlayState.PAUSED, p.state)
        assertTrue((r as PauseResult.Paused).token.verified)
        assertEquals("video-1", r.token.contentKey)
        assertEquals(listOf("pause"), p.commands)
    }

    @Test
    fun `something that is not playing is never touched`() = runTest {
        for (state in listOf(PlayState.PAUSED, PlayState.STOPPED, PlayState.NONE, PlayState.BUFFERING)) {
            val p = player(state)
            assertEquals(PauseResult.NotPlaying, pause(p))
            assertTrue("no command may be sent for $state", p.commands.isEmpty())
        }
    }

    @Test
    fun `an unreadable player is never touched`() = runTest {
        val p = player().apply { reportsUnknown = true }
        assertEquals(PauseResult.NotPlaying, pause(p))
        assertTrue(p.commands.isEmpty())
    }

    @Test
    fun `a session owned by another package is not paused`() = runTest {
        val p = player().apply { source = "com.spotify.music" }
        assertEquals(PauseResult.NotPlaying, pause(p))
        assertTrue(p.commands.isEmpty())
    }

    @Test
    fun `a slow player is waited for within the verify timeout`() = runTest {
        val p = player(latency = 400)
        assertTrue(pause(p) is PauseResult.Paused)
    }

    @Test
    fun `a pause that never lands is reported honestly as unverified`() = runTest {
        val p = player().apply { obeysPause = false }
        val r = pause(p)
        assertTrue("claimed success without seeing a pause: $r", r is PauseResult.Unverified)
        assertFalse((r as PauseResult.Unverified).token.verified)
    }

    @Test
    fun `a pause command that is not delivered is a failure`() = runTest {
        val p = player().apply { deliversCommands = false }
        assertTrue(pause(p) is PauseResult.Failed)
    }

    @Test
    fun `exceptions from the backend never escape`() = runTest {
        assertEquals(PauseResult.NotPlaying, pause(player().apply { snapshotThrows = true }))
        assertTrue(pause(player().apply { commandThrows = true }) is PauseResult.Failed)
    }

    // --- resuming ----------------------------------------------------------

    @Test
    fun `a verified pause is resumed and the resume is verified`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        assertEquals(ResumeResult.Resumed, coordinator.resume(p, token, env()))
        assertEquals(PlayState.PLAYING, p.state)
        assertEquals(listOf("pause", "play"), p.commands)
    }

    @Test
    fun `playback the user restarted is left alone`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.userPlays()
        assertEquals(ResumeResult.AlreadyPlaying, coordinator.resume(p, token, env()))
        assertEquals("no extra play command", listOf("pause"), p.commands)
    }

    @Test
    fun `the user swiping to another video cancels the resume`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.userSwipesToNextVideo()
        assertEquals(ResumeResult.Refused(RefuseReason.CONTENT_CHANGED), coordinator.resume(p, token, env()))
        assertEquals(listOf("pause"), p.commands)
    }

    @Test
    fun `scrubbing during the break cancels the resume`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.userScrubs(120_000)
        assertEquals(ResumeResult.Refused(RefuseReason.POSITION_CHANGED), coordinator.resume(p, token, env()))
    }

    @Test
    fun `leaving the app cancels the resume`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        assertEquals(ResumeResult.Refused(RefuseReason.LEFT_APP), coordinator.resume(p, token, env(fg = "com.android.launcher")))
        assertEquals(ResumeResult.Refused(RefuseReason.LEFT_APP), coordinator.resume(p, token, env(fg = null)))
        assertEquals(listOf("pause"), p.commands)
    }

    @Test
    fun `an old pause is not resumed`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        testScheduler.advanceTimeBy(61_000)
        assertEquals(ResumeResult.Refused(RefuseReason.EXPIRED), coordinator.resume(p, token, env(maxHold = 60_000)))
    }

    @Test
    fun `a session that ended or became unreadable is not resumed`() = runTest {
        val ended = player()
        val t1 = (pause(ended) as PauseResult.Paused).token
        ended.state = PlayState.STOPPED
        assertEquals(ResumeResult.Refused(RefuseReason.ENDED), coordinator.resume(ended, t1, env()))

        val unknown = player()
        val t2 = (pause(unknown) as PauseResult.Paused).token
        unknown.reportsUnknown = true
        assertEquals(ResumeResult.Refused(RefuseReason.STATE_UNKNOWN), coordinator.resume(unknown, t2, env()))
    }

    @Test
    fun `another app taking over the session cancels the resume`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.source = "com.spotify.music"
        assertEquals(ResumeResult.Refused(RefuseReason.OTHER_SOURCE), coordinator.resume(p, token, env()))
    }

    @Test
    fun `a resume that does not take is retried once then reported as failed`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.obeysPlay = false
        val r = coordinator.resume(p, token, env())
        assertTrue(r is ResumeResult.Failed)
        assertEquals("exactly one retry", listOf("pause", "play", "play"), p.commands)
    }

    @Test
    fun `a flaky first resume succeeds on the retry`() = runTest {
        val p = player(latency = 0)
        val token = (pause(p) as PauseResult.Paused).token
        p.obeysPlay = false
        // the first attempt is ignored; the player starts obeying before the retry
        val job = coordinator.resume(p, token) {
            p.obeysPlay = p.commands.count { it == "play" } >= 1
            ResumeEnvironment(APP, testScheduler.currentTime, 60_000)
        }
        assertEquals(ResumeResult.Resumed, job)
    }

    @Test
    fun `the retry re-checks the world and backs off if the user moved on`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        p.obeysPlay = false
        var calls = 0
        val r = coordinator.resume(p, token) {
            calls++
            ResumeEnvironment(if (calls == 1) APP else "com.android.launcher", testScheduler.currentTime, 60_000)
        }
        assertEquals(ResumeResult.Refused(RefuseReason.LEFT_APP), r)
        assertEquals("only the first play was sent", 1, p.commands.count { it == "play" })
    }

    // --- unverified pauses -------------------------------------------------

    @Test
    fun `an unverified pause that did land is reconciled if the user did not touch the screen`() = runTest {
        val p = player(latency = 2_000) // slower than the verify timeout
        val r = pause(p) as PauseResult.Unverified
        testScheduler.advanceTimeBy(3_000) // pause lands during the break
        assertEquals(ResumeResult.Resumed, coordinator.resume(p, r.token, env(touched = false)))
    }

    @Test
    fun `an unverified pause is not undone if the user touched the screen meanwhile`() = runTest {
        val p = player(latency = 2_000)
        val r = pause(p) as PauseResult.Unverified
        testScheduler.advanceTimeBy(3_000)
        assertEquals(ResumeResult.Refused(RefuseReason.USER_TOUCHED), coordinator.resume(p, r.token, env(touched = true)))
    }

    @Test
    fun `touching the screen does not block resuming a verified pause`() = runTest {
        val p = player()
        val token = (pause(p) as PauseResult.Paused).token
        assertEquals(ResumeResult.Resumed, coordinator.resume(p, token, env(touched = true)))
    }

    // --- pure decision table ----------------------------------------------

    private fun token(verified: Boolean = true, content: String? = "a", pos: Long? = 1_000) =
        PauseToken(APP, Strategy.MEDIA_SESSION, content, pos, pausedAtMs = 1_000, verified = verified)

    private fun decide(
        snap: PlaybackSnapshot,
        t: PauseToken = token(),
        env: ResumeEnvironment = ResumeEnvironment(APP, 2_000, 60_000),
    ) = ResumeDecision.decide(t, snap, env)

    @Test
    fun `decision table - paused same content same app resumes`() {
        assertEquals(ResumeDecision.Resume, decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_200)))
    }

    @Test
    fun `decision table - missing content or position cannot veto`() {
        assertEquals(ResumeDecision.Resume, decide(PlaybackSnapshot(PlayState.PAUSED, null, null)))
        assertEquals(ResumeDecision.Resume, decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_000), token(content = null, pos = null)))
    }

    @Test
    fun `decision table - small position jitter is tolerated large drift is not`() {
        assertEquals(ResumeDecision.Resume, decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_000 + ResumeDecision.POSITION_TOLERANCE_MS)))
        assertEquals(
            ResumeDecision.Refuse(RefuseReason.POSITION_CHANGED),
            decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_000 + ResumeDecision.POSITION_TOLERANCE_MS + 1)),
        )
    }

    @Test
    fun `decision table - buffering counts as active so nothing is sent`() {
        assertEquals(ResumeDecision.NotNeeded, decide(PlaybackSnapshot(PlayState.BUFFERING)))
    }

    @Test
    fun `decision table - the foreground check outranks the state check`() {
        val env = ResumeEnvironment("other.app", 2_000, 60_000)
        assertEquals(ResumeDecision.Refuse(RefuseReason.LEFT_APP), decide(PlaybackSnapshot(PlayState.PLAYING), env = env))
    }

    @Test
    fun `decision table - the hold limit is inclusive`() {
        val atLimit = ResumeEnvironment(APP, 1_000 + 60_000, 60_000)
        assertEquals(ResumeDecision.Resume, decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_000), env = atLimit))
        val over = ResumeEnvironment(APP, 1_000 + 60_001, 60_000)
        assertEquals(ResumeDecision.Refuse(RefuseReason.EXPIRED), decide(PlaybackSnapshot(PlayState.PAUSED, "a", 1_000), env = over))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CompatTesterTest {

    private val tester = CompatTester()

    private fun TestScope.player(strategy: Strategy) =
        FakePlayer(clock = { testScheduler.currentTime }, strategy = strategy)

    private suspend fun TestScope.run(vararg players: FakePlayer) =
        tester.run(APP, players.toList(), { testScheduler.currentTime }, { APP })

    @Test
    fun `a working strategy passes and playback ends up playing`() = runTest {
        val p = player(Strategy.MEDIA_SESSION)
        val report = run(p)
        assertTrue(report.anyPassed)
        assertFalse(report.leftPaused)
        assertEquals(PlayState.PLAYING, p.state)
    }

    @Test
    fun `strategies are tried best first`() = runTest {
        val key = player(Strategy.MEDIA_KEY)
        val session = player(Strategy.MEDIA_SESSION)
        val report = run(key, session)
        assertEquals(listOf(Strategy.MEDIA_SESSION, Strategy.MEDIA_KEY), report.results.map { it.strategy })
    }

    @Test
    fun `nothing playing is reported as such not as a failure`() = runTest {
        val p = player(Strategy.MEDIA_SESSION).apply { state = PlayState.PAUSED }
        val report = run(p)
        assertFalse(report.sawPlayback)
        assertEquals(CompatOutcome.NOT_PLAYING, report.results.single().outcome)
        assertTrue(p.commands.isEmpty())
    }

    @Test
    fun `a pause that never lands fails that strategy but the next one still runs`() = runTest {
        val stubborn = player(Strategy.MEDIA_SESSION).apply { obeysPause = false }
        val key = player(Strategy.MEDIA_KEY)
        val report = run(stubborn, key)
        assertEquals(CompatOutcome.PAUSE_NOT_VERIFIED, report.results[0].outcome)
        assertEquals(CompatOutcome.PASSED, report.results[1].outcome)
    }

    @Test
    fun `a player that will not resume stops the test and flags that it may be left paused`() = runTest {
        val stuck = player(Strategy.MEDIA_SESSION).apply { obeysPlay = false }
        val next = player(Strategy.MEDIA_KEY)
        val report = run(stuck, next)
        assertEquals(CompatOutcome.RESUME_FAILED, report.results.single().outcome)
        assertTrue(report.leftPaused)
        assertTrue("no further commands once a player is stuck", next.commands.isEmpty())
    }

    @Test
    fun `the user leaving the app mid test is refused not forced`() = runTest {
        val p = player(Strategy.MEDIA_SESSION)
        val report = tester.run(APP, listOf(p), { testScheduler.currentTime }, { "com.android.launcher" })
        assertEquals(CompatOutcome.REFUSED, report.results.single().outcome)
        assertNotNull(report.results.single().note)
    }
}

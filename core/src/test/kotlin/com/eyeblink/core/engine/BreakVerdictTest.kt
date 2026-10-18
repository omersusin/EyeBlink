package com.eyeblink.core.engine

import com.eyeblink.core.playback.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakVerdictTest {

    private fun report(pause: PauseSummary, resume: ResumeSummary, strategy: Strategy? = Strategy.PRIVILEGED_SESSION) =
        BreakReport(BreakKind.MICRO, "com.app", 0, 1_000, BreakOutcome.COMPLETED, strategy = strategy, pause = pause, resume = resume)

    private fun ok(s: Strategy = Strategy.PRIVILEGED_SESSION) = PlaybackVerdict(s, pauseOk = true, resumeOk = true)

    @Test
    fun `a break that paused and resumed is a success for its strategy`() {
        assertEquals(ok(), report(PauseSummary.PAUSED, ResumeSummary.RESUMED).playbackVerdict())
        assertEquals(ok(), report(PauseSummary.PAUSED, ResumeSummary.ALREADY_PLAYING).playbackVerdict())
    }

    @Test
    fun `a pause that landed late is a success when the resume worked`() {
        // The resume is only sent to a player found paused, so it proves the pause landed.
        assertEquals(ok(), report(PauseSummary.UNVERIFIED, ResumeSummary.RESUMED).playbackVerdict())
    }

    @Test
    fun `a pause that never showed up is a failure`() {
        val failed = PlaybackVerdict(Strategy.PRIVILEGED_SESSION, pauseOk = false, resumeOk = false)
        assertEquals(failed, report(PauseSummary.UNVERIFIED, ResumeSummary.ALREADY_PLAYING).playbackVerdict())
        assertEquals(failed, report(PauseSummary.UNVERIFIED, ResumeSummary.FAILED).playbackVerdict())
        assertEquals(failed, report(PauseSummary.FAILED, ResumeSummary.NOT_ATTEMPTED).playbackVerdict())
    }

    @Test
    fun `a video that would not start again is a half failure`() {
        assertEquals(PlaybackVerdict(Strategy.PRIVILEGED_SESSION, true, false), report(PauseSummary.PAUSED, ResumeSummary.FAILED).playbackVerdict())
    }

    @Test
    fun `nothing that is not the strategys doing counts for or against it`() {
        for (pause in listOf(PauseSummary.NOT_ATTEMPTED, PauseSummary.NOT_PLAYING, PauseSummary.NO_SESSION, PauseSummary.UNREADABLE)) {
            assertNull("$pause", report(pause, ResumeSummary.NOT_ATTEMPTED).playbackVerdict())
        }
        // the person took over during the break
        assertNull(report(PauseSummary.PAUSED, ResumeSummary.REFUSED).playbackVerdict())
        assertNull(report(PauseSummary.UNVERIFIED, ResumeSummary.REFUSED).playbackVerdict())
        // the person switched resuming off, so there is no resume to judge
        assertNull(report(PauseSummary.PAUSED, ResumeSummary.NOT_ATTEMPTED).playbackVerdict())
        // no strategy was used
        assertNull(report(PauseSummary.PAUSED, ResumeSummary.RESUMED, strategy = null).playbackVerdict())
    }
}

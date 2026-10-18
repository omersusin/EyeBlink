package com.eyeblink.core.engine

import com.eyeblink.core.playback.Strategy

/** What one finished break says about the strategy that paused for it: did the pause land, did the resume. */
data class PlaybackVerdict(val strategy: Strategy, val pauseOk: Boolean, val resumeOk: Boolean)

/**
 * Reads a finished break as evidence about the pause strategy it used, or as no evidence at all (null).
 *
 * Evidence is only what the strategy can be blamed or credited for. A video that was not playing, an app without
 * a media session, a shell that could not be read, a person who took over during the break, and a resume the
 * person switched off say nothing about whether the strategy works.
 */
fun BreakReport.playbackVerdict(): PlaybackVerdict? {
    val s = strategy ?: return null
    return when (pause) {
        PauseSummary.NOT_ATTEMPTED, PauseSummary.NOT_PLAYING, PauseSummary.NO_SESSION, PauseSummary.UNREADABLE -> null
        PauseSummary.FAILED -> PlaybackVerdict(s, pauseOk = false, resumeOk = false)
        PauseSummary.PAUSED -> when (resume) {
            ResumeSummary.RESUMED, ResumeSummary.ALREADY_PLAYING -> PlaybackVerdict(s, pauseOk = true, resumeOk = true)
            ResumeSummary.FAILED -> PlaybackVerdict(s, pauseOk = true, resumeOk = false)
            ResumeSummary.REFUSED, ResumeSummary.NOT_ATTEMPTED -> null
        }
        // The pause was not seen to land in time. A resume is only ever sent to a player found paused, so a resume
        // that worked proves that the pause did land - late. That is a success, however slow; it is not a failure.
        PauseSummary.UNVERIFIED -> when (resume) {
            ResumeSummary.RESUMED -> PlaybackVerdict(s, pauseOk = true, resumeOk = true)
            ResumeSummary.ALREADY_PLAYING, ResumeSummary.FAILED -> PlaybackVerdict(s, pauseOk = false, resumeOk = false)
            ResumeSummary.REFUSED, ResumeSummary.NOT_ATTEMPTED -> null
        }
    }
}

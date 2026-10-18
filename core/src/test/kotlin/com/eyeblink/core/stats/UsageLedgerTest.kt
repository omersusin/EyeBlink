package com.eyeblink.core.stats

import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary
import com.eyeblink.core.model.CoreJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLedgerTest {

    private fun report(
        kind: BreakKind = BreakKind.MICRO,
        outcome: BreakOutcome = BreakOutcome.COMPLETED,
        pause: PauseSummary = PauseSummary.NOT_ATTEMPTED,
        resume: ResumeSummary = ResumeSummary.NOT_ATTEMPTED,
    ) = BreakReport(kind, "com.x.y", 0, 0, outcome, pause = pause, resume = resume)

    @Test
    fun `breaks are counted by kind and outcome`() {
        var l = UsageLedger()
        l = l.recordBreak("2026-10-17", report())
        l = l.recordBreak("2026-10-17", report())
        l = l.recordBreak("2026-10-17", report(BreakKind.REST))
        l = l.recordBreak("2026-10-17", report(BreakKind.REST, BreakOutcome.SKIPPED_BY_USER))
        l = l.recordBreak("2026-10-17", report(outcome = BreakOutcome.ABORTED))
        val d = l.day("2026-10-17")
        assertEquals(2, d.microShown)
        assertEquals(1, d.restsCompleted)
        assertEquals(1, d.restsSkipped)
        assertEquals(1, d.aborted)
    }

    @Test
    fun `pause and resume outcomes are tallied honestly`() {
        var l = UsageLedger()
        l = l.recordBreak("d", report(pause = PauseSummary.PAUSED, resume = ResumeSummary.RESUMED))
        l = l.recordBreak("d", report(pause = PauseSummary.UNVERIFIED, resume = ResumeSummary.REFUSED))
        l = l.recordBreak("d", report(pause = PauseSummary.FAILED))
        l = l.recordBreak("d", report(pause = PauseSummary.NOT_PLAYING, resume = ResumeSummary.FAILED))
        val d = l.day("d")
        assertEquals(1, d.pausesVerified)
        assertEquals(1, d.pausesUnverified)
        assertEquals(1, d.pausesFailed)
        assertEquals(1, d.resumesOk)
        assertEquals(1, d.resumesRefused)
        assertEquals(1, d.resumesFailed)
    }

    @Test
    fun `why a video was not paused is counted by outcome - and a break the gate stopped is not counted`() {
        var l = UsageLedger()
        l = l.recordBreak("d", report(pause = PauseSummary.NO_SESSION))
        l = l.recordBreak("d", report(pause = PauseSummary.NO_SESSION))
        l = l.recordBreak("d", report(pause = PauseSummary.NOT_PLAYING))
        l = l.recordBreak("d", report(pause = PauseSummary.NOT_ATTEMPTED))
        l = l.recordBreak("d", report(pause = PauseSummary.PAUSED, resume = ResumeSummary.RESUMED))
        l = l.recordBreak("d", report(outcome = BreakOutcome.ABORTED))
        val d = l.day("d")
        assertEquals(mapOf("NO_SESSION" to 2, "NOT_PLAYING" to 1, "NOT_ATTEMPTED" to 1, "PAUSED" to 1), d.pauseOutcomes)
        assertEquals("the old counters are unchanged", 1, d.pausesVerified)
        assertEquals(4, UsageLedger.merge(d, d).pauseOutcomes.getValue("NO_SESSION"))
        assertEquals(mapOf("PAUSED" to 1), UsageLedger.merge(d.copy(pauseOutcomes = emptyMap()), DayStats(pauseOutcomes = mapOf("PAUSED" to 1))).pauseOutcomes)
    }

    @Test
    fun `a ledger saved before outcomes were counted still loads`() {
        val old = CoreJson.instance.decodeFromString<UsageLedger>("""{"days":{"d":{"microShown":39,"pausesVerified":0}}}""")
        assertEquals(39, old.day("d").microShown)
        assertTrue(old.day("d").pauseOutcomes.isEmpty())
    }

    @Test
    fun `deferrals are grouped by reason`() {
        var l = UsageLedger()
        l = l.recordDeferral("d", "TEXT_INPUT").recordDeferral("d", "TEXT_INPUT").recordDeferral("d", "IN_CALL")
        assertEquals(mapOf("TEXT_INPUT" to 2, "IN_CALL" to 1), l.day("d").deferredByReason)
    }

    @Test
    fun `observed blink rate needs enough tracked time to be reported`() {
        var l = UsageLedger().addObserved("d", 3, 30_000)
        assertNull(l.day("d").observedBlinksPerMinute)
        l = l.addObserved("d", 9, 90_000)
        assertEquals(6f, l.day("d").observedBlinksPerMinute!!, 1e-3f)
    }

    @Test
    fun `negative additions are ignored`() {
        val l = UsageLedger().addActive("d", -5).addCamera("d", -5).addObserved("d", -1, -1)
        assertEquals(0L, l.day("d").activeMs)
        assertEquals(0, l.day("d").observedBlinks)
    }

    @Test
    fun `old days are pruned and the newest are kept`() {
        var l = UsageLedger()
        for (i in 1..70) l = l.addActive("2026-%02d-%02d".format(1 + i / 30, 1 + i % 28), 1)
        val pruned = l.pruned(10)
        assertEquals(10, pruned.days.size)
        assertTrue(pruned.days.keys.all { k -> l.days.keys.sortedDescending().take(10).contains(k) })
    }

    @Test
    fun `last days sums across days`() {
        var l = UsageLedger()
        l = l.recordBreak("2026-10-15", report()).recordBreak("2026-10-16", report()).recordBreak("2026-10-17", report())
        assertEquals(2, l.lastDays(2).microShown)
        assertEquals(3, l.lastDays(30).microShown)
    }

    @Test
    fun `the ledger survives a json round trip`() {
        val l = UsageLedger().recordBreak("d", report(pause = PauseSummary.PAUSED, resume = ResumeSummary.RESUMED)).recordDeferral("d", "IN_CALL")
        val back = CoreJson.instance.decodeFromString<UsageLedger>(CoreJson.instance.encodeToString(l))
        assertEquals(l, back)
    }
}

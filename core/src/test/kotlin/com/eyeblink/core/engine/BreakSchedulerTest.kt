package com.eyeblink.core.engine

import com.eyeblink.core.calibration.BlinkBaseline
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.ObservedRate
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.NoPauseFallback
import com.eyeblink.core.model.PlaybackSettings
import com.eyeblink.core.model.QuietHours
import com.eyeblink.core.model.RhythmSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the scheduler through simulated use, tick by tick, with no real clock. */
private class Sim(
    rhythm: RhythmSettings = RhythmSettings(
        microIntervalSec = 45, restIntervalMin = 20, adaptive = false, jitterPercent = 0,
        cooldownSec = 10, postRestGraceMin = 3, preferTransitions = false, softDeferralBudgetSec = 45,
    ),
    playback: PlaybackSettings = PlaybackSettings(),
) {
    val scheduler = BreakScheduler()
    var now = 10_000_000L
    var settings = EyeBlinkSettings(rhythm = rhythm, playback = playback)
    var ctx = ContextSnapshot(nowMs = now, foregroundPackage = APP)
    var profile: AppProfile? = AppProfile(APP)
    var canPause = true
    var mode = TriggerMode.CAMERA_FREE
    var personal = PersonalProfile()
    var observed: ObservedRate? = null
    var suspended = false

    val starts = mutableListOf<Pair<Long, SchedulerStep.Start>>()
    var lastStep: SchedulerStep? = null

    fun tick(ms: Long = 500, edit: (ContextSnapshot) -> ContextSnapshot = { it }): SchedulerStep {
        now += ms
        ctx = edit(ctx.copy(nowMs = now))
        val step = scheduler.step(
            ctx,
            SchedulerInput(settings, suspended, profile, ContextProfile.VIDEO, personal, mode, observed, canPause),
        )
        lastStep = step
        if (step is SchedulerStep.Start) starts += now to step
        return step
    }

    /** Runs for [totalMs]; on a Start the break completes instantly unless [finish] is false. */
    fun run(totalMs: Long, stepMs: Long = 500, finish: Boolean = true, edit: (ContextSnapshot) -> ContextSnapshot = { it }) {
        var elapsed = 0L
        while (elapsed < totalMs) {
            val s = tick(stepMs, edit)
            elapsed += stepMs
            if (finish && s is SchedulerStep.Start) scheduler.onBreakFinished(s.kind, BreakOutcome.COMPLETED, now)
        }
    }

    fun firstStart(): Pair<Long, SchedulerStep.Start>? = starts.firstOrNull()

    companion object {
        const val APP = "com.instagram.android"
    }
}

class BreakSchedulerTest {

    @Test
    fun `the first micro blink arrives after the interval and not before`() {
        val sim = Sim()
        val t0 = sim.now
        sim.run(60_000, finish = false)
        val (at, start) = sim.firstStart()!!
        assertEquals(BreakKind.MICRO, start.kind)
        assertFalse(start.forced)
        assertTrue("fired after ${(at - t0)} ms", at - t0 in 45_000L..46_000L)
    }

    @Test
    fun `breaks repeat on the interval once each one is finished`() {
        val sim = Sim()
        sim.run(5 * 60_000)
        // 45 s interval over 5 minutes: 6 breaks (the first at 45 s, then every 45 s).
        assertEquals(6, sim.starts.count { it.second.kind == BreakKind.MICRO })
    }

    @Test
    fun `time spent in a non selected app does not count`() {
        val sim = Sim()
        sim.profile = null
        sim.run(10 * 60_000)
        assertTrue(sim.starts.isEmpty())
        assertEquals(Reason.NOT_SELECTED_APP, (sim.lastStep as SchedulerStep.Idle).reason)

        sim.profile = AppProfile(Sim.APP)
        val back = sim.now
        sim.run(60_000, finish = false)
        val at = sim.firstStart()!!.first
        assertTrue("needs a full interval in the selected app, got ${at - back}", at - back >= 44_000L)
    }

    @Test
    fun `a short absence pauses the clock and a long one starts over`() {
        val short = Sim()
        short.run(30_000, finish = false)
        short.profile = null; short.run(30_000)
        short.profile = AppProfile(Sim.APP)
        val resume = short.now
        short.run(30_000, finish = false)
        assertTrue("clock should continue from ~30s", short.firstStart()!!.first - resume < 20_000)

        val long = Sim()
        long.run(30_000, finish = false)
        long.profile = null; long.run(2 * 60_000)
        long.profile = AppProfile(Sim.APP)
        val back = long.now
        long.run(60_000, finish = false)
        assertTrue("fresh session needs the whole interval", long.firstStart()!!.first - back >= 44_000L)
    }

    @Test
    fun `a rest comes due after twenty minutes and takes priority over a micro blink`() {
        val sim = Sim()
        sim.run(21 * 60_000)
        val rests = sim.starts.filter { it.second.kind == BreakKind.REST }
        assertEquals(1, rests.size)
        val restAt = rests.single().first
        assertTrue(restAt - 10_000_000L in 20 * 60_000L..(20 * 60_000L + 2_000))
    }

    @Test
    fun `after a rest no micro blink is shown during the grace period`() {
        val sim = Sim()
        sim.run(20 * 60_000 + 5_000)
        val restAt = sim.starts.first { it.second.kind == BreakKind.REST }.first
        sim.run(2 * 60_000)
        assertTrue(sim.starts.none { it.second.kind == BreakKind.MICRO && it.first in restAt..(restAt + 3 * 60_000) })
    }

    @Test
    fun `a micro blink is not wasted right before a rest`() {
        // Micro every 59 s would land at 295 s; the rest is due at 300 s, 5 s later.
        val sim = Sim(RhythmSettings(microIntervalSec = 59, restIntervalMin = 5, adaptive = false, jitterPercent = 0, postRestGraceMin = 0, preferTransitions = false, cooldownSec = 3))
        sim.run(5 * 60_000 + 2_000)
        val rest = sim.starts.single { it.second.kind == BreakKind.REST }
        val microsBeforeRest = sim.starts.filter { it.second.kind == BreakKind.MICRO && it.first < rest.first }
        assertEquals("59,118,177,236 only; 295 is skipped for the imminent rest", 4, microsBeforeRest.size)
    }

    // --- holds and deferral -----------------------------------------------

    @Test
    fun `a hard hold such as the keyboard defers the break and it fires as soon as the hold clears`() {
        val sim = Sim()
        sim.run(44_000, finish = false)
        // keyboard opens right as the break becomes due
        var step: SchedulerStep? = null
        repeat(30) { step = sim.tick(1_000) { it.copy(keyboardVisible = true) } }
        val deferred = step as SchedulerStep.Deferred
        assertEquals(Reason.TEXT_INPUT, deferred.reason)
        assertTrue(sim.starts.isEmpty())

        val after = sim.tick(500) { it.copy(keyboardVisible = false) }
        assertTrue("the due break must not be lost: $after", after is SchedulerStep.Start)
    }

    @Test
    fun `a soft obstacle is waited out and then overridden after the budget`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, softDeferralBudgetSec = 30, preferTransitions = false, cooldownSec = 3))
        // A swipe every 500 ms keeps the transition reason alive forever.
        sim.run(60_000, finish = false) { it.copy(lastScrollMs = it.nowMs - 100) }
        val first = sim.firstStart()!!
        assertTrue("forced after the budget", first.second.forced)
        val waited = first.first - 10_000_000L
        assertTrue("fired at $waited ms (20s due + 30s budget)", waited in 49_000L..52_000L)
    }

    @Test
    fun `a finger on the screen is never overridden however long it stays`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, softDeferralBudgetSec = 5, preferTransitions = false))
        sim.run(10 * 60_000, finish = false) { it.copy(touching = true) }
        assertTrue(sim.starts.isEmpty())
        assertEquals(Reason.TOUCHING, (sim.lastStep as SchedulerStep.Deferred).reason)
    }

    @Test
    fun `calls and sensitive screens are never overridden`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, softDeferralBudgetSec = 5, preferTransitions = false))
        sim.run(5 * 60_000, finish = false) { it.copy(audioMode = AudioMode.IN_CALL) }
        assertTrue(sim.starts.isEmpty())
        sim.run(5 * 60_000, finish = false) { it.copy(audioMode = AudioMode.NORMAL, sensitiveScreen = true) }
        assertTrue(sim.starts.isEmpty())
    }

    @Test
    fun `cooldown keeps two breaks from landing back to back`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 5, cooldownSec = 10, adaptive = false, jitterPercent = 0, preferTransitions = false))
        sim.run(120_000)
        val gaps = sim.starts.map { it.first }.zipWithNext { a, b -> b - a }
        assertTrue("min gap ${gaps.minOrNull()}", gaps.all { it >= 10_000L })
    }

    @Test
    fun `the hourly cap limits micro blinks`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 5, cooldownSec = 3, maxMicroPerHour = 20, restEnabled = false, adaptive = false, jitterPercent = 0, preferTransitions = false))
        sim.run(59 * 60_000)
        assertEquals(20, sim.starts.count { it.second.kind == BreakKind.MICRO })
    }

    // --- state outside the app --------------------------------------------

    @Test
    fun `suspended snoozed and quiet hours never start a break`() {
        val sim = Sim()
        sim.suspended = true
        sim.run(5 * 60_000)
        assertTrue(sim.starts.isEmpty())
        sim.suspended = false

        sim.scheduler.snooze(sim.now + 3 * 60_000)
        sim.run(2 * 60_000)
        assertTrue(sim.starts.isEmpty())
        assertEquals(Reason.SNOOZED, (sim.lastStep as SchedulerStep.Idle).reason)

        val q = Sim(RhythmSettings(quietHours = QuietHours(true, 0, 24 * 60 - 1), adaptive = false))
        q.run(5 * 60_000)
        assertTrue(q.starts.isEmpty())
    }

    @Test
    fun `after a long snooze the user gets a fresh interval`() {
        val sim = Sim()
        sim.run(40_000, finish = false)
        sim.scheduler.snooze(sim.now + 10 * 60_000)
        sim.run(10 * 60_000)
        val back = sim.now
        sim.run(60_000, finish = false)
        assertTrue(sim.firstStart()!!.first - back >= 44_000L)
    }

    // --- skip feedback ----------------------------------------------------

    @Test
    fun `skipped rests make the next rest come later`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 600, restIntervalMin = 5, adaptive = false, jitterPercent = 0, preferTransitions = false))
        fun runToRest(skip: Boolean): Long {
            val before = sim.now
            while (true) {
                val s = sim.tick(500)
                if (s is SchedulerStep.Start && s.kind == BreakKind.REST) {
                    sim.scheduler.onBreakFinished(BreakKind.REST, if (skip) BreakOutcome.SKIPPED_BY_USER else BreakOutcome.COMPLETED, sim.now)
                    return sim.now - before
                }
                if (sim.now - before > 60 * 60_000L) error("rest never came")
            }
        }
        val first = runToRest(skip = true)
        val second = runToRest(skip = true)
        val third = runToRest(skip = false)
        assertTrue("first=$first", first in 300_000L..302_000L)
        assertTrue("second=$second should be 1.25x", second in 374_000L..377_000L)
        assertTrue("third=$third should be 1.5x", third in 449_000L..452_000L)
        val fourth = runToRest(skip = false)
        assertTrue("completing a rest resets the backoff: $fourth", fourth in 300_000L..302_000L)
    }

    @Test
    fun `an aborted break is retried soon without losing the due state`() {
        val sim = Sim()
        sim.run(46_000, finish = false)
        val s = sim.starts.first().second
        sim.scheduler.onBreakFinished(s.kind, BreakOutcome.ABORTED, sim.now)
        val immediately = sim.tick(500)
        assertTrue("backing off: $immediately", immediately is SchedulerStep.Deferred)
        sim.run(5_000, finish = false)
        assertTrue(sim.starts.size >= 2)
    }

    // --- camera assisted -------------------------------------------------

    @Test
    fun `a measured stare triggers an early micro blink in camera assisted mode`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 60, adaptive = true, jitterPercent = 0, preferTransitions = false))
        val baseline = BlinkBaseline(8_000, 130, 0.9f, 40)
        sim.personal = PersonalProfile(general = baseline, byContext = mapOf(ContextProfile.VIDEO to baseline))
        sim.mode = TriggerMode.CAMERA_ASSISTED
        sim.observed = ObservedRate(blinksPerMinute = 7.5f, trackedMs = 60_000, blinks = 8, trackedSinceBlinkMs = 40_000, meanConfidence = 0.9f)
        sim.run(30_000, finish = false)
        val s = sim.firstStart()!!.second
        assertTrue("stare should trigger before the 60s interval", s.stare)
        assertTrue(sim.firstStart()!!.first - 10_000_000L < 45_000L)
    }

    @Test
    fun `camera free mode never claims a stare`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 60, adaptive = true, jitterPercent = 0, preferTransitions = false))
        val baseline = BlinkBaseline(8_000, 130, 0.9f, 40)
        sim.personal = PersonalProfile(general = baseline, byContext = mapOf(ContextProfile.VIDEO to baseline))
        sim.mode = TriggerMode.CAMERA_FREE
        sim.observed = ObservedRate(7.5f, 60_000, 8, 400_000, 0.9f)
        sim.run(30_000, finish = false)
        assertTrue(sim.starts.isEmpty())
    }

    // --- no pause support --------------------------------------------------

    @Test
    fun `when playback cannot be paused a micro blink waits for the next swipe`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, preferTransitions = true, cooldownSec = 3))
        sim.canPause = false
        sim.ctx = sim.ctx.copy(media = MediaSignal.PLAYING)
        // due at 20 s; no swipe happens, so it waits the full prefer window then goes anyway
        sim.run(40_000, finish = false)
        val at = sim.firstStart()!!.first - 10_000_000L
        assertTrue("fired at $at", at in 27_000L..29_500L)
    }

    @Test
    fun `a swipe that just ended is the convenient moment`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, preferTransitions = true, cooldownSec = 3))
        sim.canPause = false
        sim.ctx = sim.ctx.copy(media = MediaSignal.PLAYING)
        sim.run(19_000, finish = false)
        // swipe at ~19.5s, then 1.2s of quiet
        sim.tick(500) { it.copy(lastScrollMs = it.nowMs - 50) }
        val s = sim.tick(1_200) { it.copy(lastScrollMs = it.nowMs - 1_250) }
        assertTrue("should blink right after the swipe: $s", s is SchedulerStep.Start)
    }

    @Test
    fun `being able to pause removes the need to wait for a transition`() {
        val sim = Sim(RhythmSettings(microIntervalSec = 20, adaptive = false, jitterPercent = 0, preferTransitions = true))
        sim.canPause = true
        sim.ctx = sim.ctx.copy(media = MediaSignal.PLAYING)
        sim.run(25_000, finish = false)
        assertTrue(sim.firstStart()!!.first - 10_000_000L in 19_500L..21_000L)
    }

    @Test
    fun `an app the user configured to skip when pausing is impossible stays quiet`() {
        val sim = Sim(playback = PlaybackSettings(fallback = NoPauseFallback.SKIP))
        sim.canPause = false
        sim.run(5 * 60_000)
        assertTrue(sim.starts.isEmpty())
        assertEquals(Reason.NO_PAUSE_SUPPORT, (sim.lastStep as SchedulerStep.Idle).reason)
    }

    @Test
    fun `the waiting step reports time remaining`() {
        val sim = Sim()
        sim.run(10_000, finish = false)
        val w = sim.lastStep as SchedulerStep.Waiting
        assertTrue(w.microInMs in 34_000L..36_000L)
        assertTrue(w.restInMs!! > 19 * 60_000L)
    }
}

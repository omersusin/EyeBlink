package com.eyeblink.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPipelineTest {

    private val good = QualityReport(0.9f, emptySet())

    private fun frame(t: Long, open: Float, confidence: Float = 0.9f, luma: Float = 120f) =
        FaceFrame(t, EyeOpennessSample(t, open, open, confidence, luma), good, luma)

    private fun noFace(t: Long) = FaceFrame(t, null, QualityReport(0f, setOf(QualityIssue.NO_FACE)), 0f)

    /** 15 fps of open eyes between [from] and [to]. */
    private fun TrackingPipeline.openFrames(from: Long, to: Long) {
        var t = from
        while (t < to) { onFrame(frame(t, 0.9f)); t += 66 }
    }

    private fun TrackingPipeline.blinkAt(t: Long): BlinkObservation? {
        onFrame(frame(t, 0.0f))
        onFrame(frame(t + 66, 0.0f))
        onFrame(frame(t + 132, 0.0f))
        return onFrame(frame(t + 198, 0.9f)).blink
    }

    @Test
    fun `a closure and reopening is reported as a blink on the frame that reopens`() {
        val p = TrackingPipeline()
        p.openFrames(0, 1_000)
        val b = p.blinkAt(1_000)
        assertNotNull(b)
        assertEquals(1_000L, b!!.startMs)
    }

    @Test
    fun `frames without a face are not evidence and never become a blink`() {
        val p = TrackingPipeline()
        p.openFrames(0, 1_000)
        val ev = p.onFrame(noFace(1_066))
        assertFalse(ev.trackable)
        assertNull(ev.blink)
        assertEquals("a lost face leaves no confidence behind", null, p.confidenceAt(1_100))
    }

    @Test
    fun `a dropout in the middle of a closure does not fabricate a blink`() {
        val p = TrackingPipeline()
        p.openFrames(0, 1_000)
        p.onFrame(frame(1_000, 0.0f))
        // The face disappears for two seconds, then the eye is simply open again.
        p.onFrame(noFace(1_100))
        val after = p.onFrame(frame(3_100, 0.9f))
        assertNull(after.blink)
    }

    @Test
    fun `observed rate counts only tracked time`() {
        val p = TrackingPipeline()
        p.openFrames(0, 20_000)
        repeat(4) { p.blinkAt(2_000L + it * 4_000L) }
        val r = p.rate.observed(20_000)
        assertEquals(4, r.blinks)
        assertTrue("20 s tracked is enough to report", r.trackedMs > 15_000)
        assertEquals(12f, r.blinksPerMinute, 3f)
    }

    @Test
    fun `confidence is reported only while it is recent`() {
        val p = TrackingPipeline()
        p.onFrame(frame(1_000, 0.9f, confidence = 0.8f))
        assertEquals(0.8f, p.confidenceAt(2_000)!!, 0.001f)
        assertNull(p.confidenceAt(1_000 + TrackingPipeline.RECENT_MS + 1))
    }

    @Test
    fun `a learning window is handed over once enough tracked time has gathered`() {
        val p = TrackingPipeline()
        p.openFrames(0, 30_000)
        assertNull("30 s is below the window target", p.takeLearningWindow(ContextProfile.VIDEO))
        repeat(5) { p.blinkAt(3_000L + it * 5_000L) }
        p.openFrames(30_000, 62_000)
        val w = p.takeLearningWindow(ContextProfile.VIDEO)
        assertNotNull(w)
        assertEquals(ContextProfile.VIDEO, w!!.context)
        assertTrue(w.trackedMs >= TrackingPipeline.WINDOW_TARGET_MS)
        assertEquals(5, w.blinks.size)
        assertEquals(0.9f, w.meanConfidence, 0.01f)
        assertEquals(120f, w.meanLuma, 1f)
        assertNull("the window starts over after being taken", p.takeLearningWindow(ContextProfile.VIDEO, force = true))
    }

    @Test
    fun `forcing a short window keeps it only if the learner could use it`() {
        val short = TrackingPipeline().apply { openFrames(0, 10_000) }
        assertNull(short.takeLearningWindow(ContextProfile.VIDEO, force = true))

        val usable = TrackingPipeline().apply { openFrames(0, 26_000) }
        assertNotNull(usable.takeLearningWindow(ContextProfile.READING, force = true))
    }

    @Test
    fun `new thresholds apply to the next blink`() {
        val p = TrackingPipeline()
        // A shallow dip, to 0.75 of an open 0.9 (a closure of about 0.17), is not a blink with the defaults...
        p.openFrames(0, 1_500)
        p.onFrame(frame(1_500, 0.75f)); p.onFrame(frame(1_566, 0.75f)); p.onFrame(frame(1_632, 0.75f))
        assertNull(p.onFrame(frame(1_698, 0.9f)).blink)
        // ...but is one for a person whose eyes simply do not close as far.
        p.setThresholds(BlinkThresholds(start = 0.12f, end = 0.05f, full = 0.30f, fitted = true, openLevel = 0.9f))
        p.onFrame(frame(2_500, 0.75f)); p.onFrame(frame(2_566, 0.75f)); p.onFrame(frame(2_632, 0.75f))
        assertNotNull(p.onFrame(frame(2_698, 0.9f)).blink)
    }

    @Test
    fun `a narrow open eye is as good as a wide one`() {
        val p = TrackingPipeline()
        var t = 0L
        while (t < 1_500) { p.onFrame(frame(t, 0.32f)); t += 66 }
        p.onFrame(frame(1_500, 0.10f)); p.onFrame(frame(1_566, 0.10f)); p.onFrame(frame(1_632, 0.10f))
        assertNotNull(p.onFrame(frame(1_698, 0.32f)).blink)
    }

    @Test
    fun `sensitivity changes what the pipeline calls a blink`() {
        val dip = { pipeline: TrackingPipeline ->
            var t = 0L
            while (t < 1_500) { pipeline.onFrame(frame(t, 0.9f)); t += 66 }
            pipeline.onFrame(frame(1_500, 0.70f)); pipeline.onFrame(frame(1_566, 0.70f)); pipeline.onFrame(frame(1_632, 0.70f))
            pipeline.onFrame(frame(1_698, 0.9f)).blink
        }
        assertNull(dip(TrackingPipeline()))
        assertNotNull(dip(TrackingPipeline(sensitivity = 0.6f)))
    }

    @Test
    fun `the live closure follows the eyes`() {
        val p = TrackingPipeline()
        p.openFrames(0, 1_500)
        assertEquals(0f, p.closure, 0.03f)
        p.onFrame(frame(1_500, 0.45f))
        assertEquals(0.5f, p.closure, 0.05f)
    }

    @Test
    fun `reset forgets everything`() {
        val p = TrackingPipeline()
        p.openFrames(0, 30_000)
        p.reset()
        assertNull(p.confidenceAt(30_100))
        assertEquals(0, p.rate.observed(30_100).blinks)
        assertTrue(p.takeLearningWindow(ContextProfile.VIDEO, force = true) == null)
    }
}

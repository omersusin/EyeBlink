package com.eyeblink.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindTrackerTest {

    private val app = "com.short.video"
    private val key = "v1|MEDIA_SESSION"

    @Test
    fun `an app is blind only after several breaks in a row found no player`() {
        val t = BlindTracker()
        t.sawNoPlayer(app, 1_000, key)
        t.sawNoPlayer(app, 2_000, key)
        assertFalse(t.isBlind(app, 2_000, key))
        t.sawNoPlayer(app, 3_000, key)
        assertTrue(t.isBlind(app, 3_000, key))
    }

    @Test
    fun `finding a player ends it at once`() {
        val t = BlindTracker()
        repeat(3) { t.sawNoPlayer(app, 1_000L * it, key) }
        assertTrue(t.isBlind(app, 3_000, key))
        t.sawPlayer(app)
        assertFalse(t.isBlind(app, 3_000, key))
        // and the count starts again from nothing
        t.sawNoPlayer(app, 4_000, key)
        assertFalse(t.isBlind(app, 4_000, key))
    }

    @Test
    fun `an update, a new permission or the passing of time makes it forget`() {
        val t = BlindTracker(memoryMs = 10_000)
        repeat(3) { t.sawNoPlayer(app, 1_000L * it, key) }
        assertTrue(t.isBlind(app, 3_000, key))
        assertFalse("the app was updated", t.isBlind(app, 3_000, "v2|MEDIA_SESSION"))
        assertFalse("root came up", t.isBlind(app, 3_000, "v1|MEDIA_SESSION,PRIVILEGED_SESSION"))
        assertFalse("long ago", t.isBlind(app, 20_000, key))
        // a miss under a new key starts a new count; an old count is not carried over
        t.sawNoPlayer(app, 21_000, key)
        assertFalse(t.isBlind(app, 21_000, key))
    }

    @Test
    fun `apps are counted apart and a test can mark one at once`() {
        val t = BlindTracker()
        repeat(3) { t.sawNoPlayer(app, 1_000L * it, key) }
        assertFalse(t.isBlind("com.other", 3_000, key))
        t.markBlind("com.other", 3_000, key)
        assertTrue(t.isBlind("com.other", 3_000, key))
        t.forget("com.other")
        assertFalse(t.isBlind("com.other", 3_000, key))
    }
}

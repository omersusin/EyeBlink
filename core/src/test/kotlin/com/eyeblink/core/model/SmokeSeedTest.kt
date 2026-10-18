package com.eyeblink.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The emulator test in `.github/smoke` starts the app from a hand-written settings file. If a field is renamed here
 * and that file is not, the app would quietly start from defaults and the test would no longer test what it says.
 */
class SmokeSeedTest {

    @Test
    fun `the settings the emulator test starts with decode to what they promise`() {
        val file = File("../.github/smoke/seed-settings.json")
        assertTrue("expected to run from the core module, looked for ${file.absoluteFile}", file.exists())

        val s = SettingsCodec.decodeOrNull(file.readText().replace("PACKAGE", "com.example.video"))
        assertNotNull("the seed settings are not valid JSON for EyeBlinkSettings", s)
        s!!

        assertTrue(s.onboardingDone)
        assertTrue(s.startOnBoot)
        assertEquals(listOf("com.example.video"), s.apps.map { it.packageName })
        assertEquals(RhythmSettings.MIN_MICRO_SEC, s.rhythm.microIntervalSec)
        assertFalse(s.rhythm.adaptive)
        assertFalse(s.rhythm.restEnabled)
        assertEquals(0, s.rhythm.jitterPercent)
    }
}

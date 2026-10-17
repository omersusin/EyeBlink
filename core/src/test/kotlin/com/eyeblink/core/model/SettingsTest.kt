package com.eyeblink.core.model

import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.EaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun `settings round trip through json unchanged`() {
        val s = EyeBlinkSettings(
            onboardingDone = true,
            language = Language.TR,
            mode = OperatingMode.SHIZUKU,
            blink = BlinkSpec(closeMs = 200, closeEase = EaseKind.CUBIC_IN_OUT, curvature = 0.8f),
            apps = listOf(AppProfile("com.instagram.android", "Instagram", intervalScalePercent = 70)),
            rhythm = RhythmSettings.preset(RhythmPreset.ACTIVE),
        )
        assertEquals(s.validated(), SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `missing blank and corrupt input yield defaults and never throw`() {
        assertEquals(EyeBlinkSettings(), SettingsCodec.decode(null))
        assertEquals(EyeBlinkSettings(), SettingsCodec.decode(""))
        assertEquals(EyeBlinkSettings(), SettingsCodec.decode("{not json"))
        assertNull(SettingsCodec.decodeOrNull("[]"))
    }

    @Test
    fun `unknown keys and unknown enum values are tolerated`() {
        val text = """{"future":"thing","mode":"WARP_DRIVE","rhythm":{"microIntervalSec":30,"extra":1}}"""
        val s = SettingsCodec.decode(text)
        assertEquals(OperatingMode.ROOTLESS, s.mode)
        assertEquals(30, s.rhythm.microIntervalSec)
    }

    @Test
    fun `out of range values are pulled back in on load`() {
        val text = """{"rhythm":{"microIntervalSec":1,"restIntervalMin":9999,"maxMicroPerHour":0},"blink":{"closeMs":1,"holdMs":999999}}"""
        val s = SettingsCodec.decode(text)
        assertEquals(RhythmSettings.MIN_MICRO_SEC, s.rhythm.microIntervalSec)
        assertEquals(RhythmSettings.MAX_REST_MIN, s.rhythm.restIntervalMin)
        assertEquals(6, s.rhythm.maxMicroPerHour)
        assertEquals(BlinkSpec.MIN_PHASE_MS, s.blink.closeMs)
        assertEquals(BlinkSpec.MAX_HOLD_MS, s.blink.holdMs)
    }

    @Test
    fun `fresh settings are safe by default`() {
        val s = EyeBlinkSettings()
        assertTrue("no app is interrupted until the user picks one", s.apps.isEmpty())
        assertEquals(TrackingMode.OFF, s.camera.tracking)
        assertEquals(OperatingMode.ROOTLESS, s.mode)
        assertFalse(s.playback.allowGestureTap || s.playback.allowMediaKeys || s.playback.allowAudioFocus)
        assertFalse(s.night.enabled)
        assertEquals(455, s.blink.totalMs)
    }

    @Test
    fun `profiles are matched by package and only when enabled`() {
        val s = EyeBlinkSettings(apps = listOf(AppProfile("a.b.c"), AppProfile("d.e.f", enabled = false)))
        assertNotNull(s.profileFor("a.b.c"))
        assertNull(s.profileFor("d.e.f"))
        assertNull(s.profileFor("x.y.z"))
        assertNull(s.profileFor(null))
    }

    @Test
    fun `adding an app replaces its earlier profile and duplicates are dropped`() {
        val s = EyeBlinkSettings().withApp(AppProfile("a.b.c", intervalScalePercent = 80)).withApp(AppProfile("a.b.c", intervalScalePercent = 120))
        assertEquals(1, s.apps.size)
        assertEquals(120, s.apps.single().intervalScalePercent)
        assertEquals(0, s.withoutApp("a.b.c").apps.size)
        val dupes = EyeBlinkSettings(apps = listOf(AppProfile("a.b.c"), AppProfile("a.b.c"))).validated()
        assertEquals(1, dupes.apps.size)
    }

    @Test
    fun `app scale is bounded`() {
        assertEquals(50, AppProfile("a.b.c", intervalScalePercent = 1).validated().intervalScalePercent)
        assertEquals(200, AppProfile("a.b.c", intervalScalePercent = 9999).validated().intervalScalePercent)
    }

    @Test
    fun `presets set both intervals and keep other choices`() {
        val base = RhythmSettings(cooldownSec = 33)
        val gentle = RhythmSettings.preset(RhythmPreset.GENTLE, base)
        val active = RhythmSettings.preset(RhythmPreset.ACTIVE, base)
        assertTrue(gentle.microIntervalSec > active.microIntervalSec)
        assertTrue(gentle.restIntervalMin > active.restIntervalMin)
        assertEquals(33, gentle.cooldownSec)
    }

    @Test
    fun `rest duration follows the chosen style`() {
        val r = RestSettings(style = RestStyle.BLACKOUT, blackoutDurationSec = 45, durationSec = 20)
        assertEquals(45, r.effectiveDurationSec)
        assertEquals(20, r.copy(style = RestStyle.DISTANCE_FOCUS).effectiveDurationSec)
    }

    @Test
    fun `the never list covers calls system ui camera and wallet but not ordinary apps`() {
        for (p in listOf("com.android.systemui", "com.google.android.dialer", "com.samsung.android.incallui", "com.sec.android.app.camera", "com.google.android.apps.walletnfcrel")) {
            assertTrue(p, NeverInterrupt.matches(p))
        }
        for (p in listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube", "com.netflix.mediaclient")) {
            assertFalse(p, NeverInterrupt.matches(p))
        }
        assertFalse(NeverInterrupt.matches(null))
    }

    @Test
    fun `known apps are categorised and unknown ones are other`() {
        assertEquals(AppCategory.VIDEO, KnownApps.categoryOf("com.google.android.youtube"))
        assertEquals(AppCategory.SOCIAL, KnownApps.categoryOf("com.instagram.android"))
        assertEquals(AppCategory.OTHER, KnownApps.categoryOf("some.random.app"))
        assertTrue("both TikTok package names are listed", KnownApps.catalog.count { it.label == "TikTok" } == 2)
    }
}

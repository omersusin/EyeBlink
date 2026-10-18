package com.eyeblink.core.privileged

import com.eyeblink.core.playback.PlayState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answers commands from a script and records everything it was asked. */
class ScriptedShell(private val script: (String) -> ShellResult?) : ShellExecutor {
    val commands = mutableListOf<String>()
    override suspend fun run(command: String, timeoutMs: Long): ShellResult {
        commands += command
        return script(command) ?: ShellResult(127, "not scripted: $command")
    }
}

private fun ok(out: String = "") = ShellResult(0, out)
private fun fail(code: Int = 1, out: String = "") = ShellResult(code, out)

/** Modelled on AOSP's MediaSessionService / MediaSessionRecord dump code; not captured from a device. */
object Fixtures {
    val YOUTUBE_PLAYING_SPOTIFY_PAUSED = """
MEDIA SESSIONS (dumpsys media_session)

Media button session is MediaSessionRecord 3f2a1b com.google.android.youtube/youtube-session (userId=0)
Sessions Stack - have 2 sessions:
  youtube-session MediaSessionRecord 3f2a1b com.google.android.youtube/youtube-session (userId=0)
    ownerPid=12345, ownerUid=10234, userId=0
    package=com.google.android.youtube
    launchIntent=null
    mediaButtonReceiver=null
    active=true
    flags=7
    rating type=0
    controllers: 1
    state=PlaybackState {state=3, position=45210, buffered position=60000, speed=1.0, updated=123456789, actions=823, custom actions=[], active item id=-1, error=null}
    audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0x800 tags= bformat=ENCODING_PCM_16BIT
    volumeType=1, controlType=2, max=0, current=0
    metadata: size=5, description=Funny cat compilation, SomeChannel, null
    queueTitle=null, size=0
  spotify MediaSessionRecord 9c8d7e com.spotify.music/spotify (userId=0)
    ownerPid=2222, ownerUid=10120, userId=0
    package=com.spotify.music
    active=true
    state=PlaybackState {state=2, position=1000, buffered position=0, speed=0.0, updated=1, actions=1, custom actions=[], active item id=-1, error=null}
    metadata: size=3, description=Song, Artist, Album
""".trimIndent()

    /** A newer style where the state is printed with its name. */
    val NAMED_STATE = """
Media button session is MediaSessionRecord 11 com.zhiliaoapp.musically/tt (userId=0)
Sessions Stack - have 1 sessions:
  tt MediaSessionRecord 11 com.zhiliaoapp.musically/tt (userId=0)
    package=com.zhiliaoapp.musically
    active=true
    state=PlaybackState {state=PLAYING(3), position=2000, buffered position=0, speed=1.0, updated=5, actions=1, custom actions=[], active item id=-1, error code=0, error message=null}
    metadata: size=1, description=null
""".trimIndent()

    /**
     * The whole of `dumpsys media_session` as the code of Android 14 prints it (MediaSessionService, MediaSessionStack,
     * MediaSessionRecord and PlaybackState.toString, read from the AOSP sources): the service header, the user record
     * with its listeners, the stack, and the audio player monitor's list at the end. Titles and packages are invented.
     */
    val ANDROID_14_FULL_DUMP = """
MEDIA SESSION SERVICE (dumpsys media_session)

2 sessions listeners.
Global priority session is null
User Records:
Record for full_user=0
  Volume key long-press listener: null
  Volume key long-press listener package: 
  Media key listener: null
  Media key listener package: 
  OnMediaKeyEventDispatchedListener: added 1 listener(s)
    from com.android.systemui
  OnMediaKeyEventSessionChangedListener: added 1 listener(s)
    from com.android.systemui
  Last MediaButtonReceiver: null
  Media button session is com.google.android.youtube/ParentHolder (userId=0)
  Sessions Stack - have 2 sessions:
    ParentHolder com.google.android.youtube/ParentHolder (userId=0)
      ownerPid=12345, ownerUid=10234, userId=0
      package=com.google.android.youtube
      launchIntent=PendingIntent{1a2b3c4: PendingIntentRecord{5d6e7f8 com.google.android.youtube startActivity}}
      mediaButtonReceiver=null
      active=true
      flags=3
      rating type=0
      controllers: 2
      state=PlaybackState {state=PLAYING(3), position=45210, buffered position=60000, speed=1.0, updated=123456789, actions=823, custom actions=[], active item id=-1, error=null}
      audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0x800 tags= bformat=ENCODING_PCM_16BIT
      volumeType=LOCAL, controlType=ABSOLUTE, max=0, current=0, volumeControlId=null
      metadata: size=5, description=Funny cat compilation, SomeChannel, null
      queueTitle=null, size=0
    spotify com.spotify.music/spotify (userId=0)
      ownerPid=2222, ownerUid=10120, userId=0
      package=com.spotify.music
      launchIntent=null
      mediaButtonReceiver=MediaButtonReceiverHolder{com.spotify.music/com.spotify.music.MediaButtonReceiver}
      active=true
      flags=2
      rating type=0
      controllers: 1
      state=PlaybackState {state=PAUSED(2), position=1000, buffered position=0, speed=0.0, updated=1, actions=1, custom actions=[], active item id=-1, error=null}
      audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bformat=ENCODING_PCM_16BIT
      volumeType=LOCAL, controlType=ABSOLUTE, max=0, current=0, volumeControlId=null
      metadata: size=3, description=Song, Artist, Album
      queueTitle=null, size=0

Audio playback (lastly played comes first)
  uid=10234 packages=com.google.android.youtube 
  uid=10120 packages=com.spotify.music 
MediaSessionDeviceConfig:
  media_session_notification_listener_enabled=true
""".trimIndent()

    /** Android 15 adds the session's unique id to its name: "package/tag/id". */
    val ANDROID_15_FULL_DUMP = ANDROID_14_FULL_DUMP
        .replace("com.google.android.youtube/ParentHolder (userId=0)", "com.google.android.youtube/ParentHolder/4f1c2a (userId=0)")
        .replace("com.spotify.music/spotify (userId=0)", "com.spotify.music/spotify/9b8d7e (userId=0)")

    val SPOTIFY_HAS_BUTTON = YOUTUBE_PLAYING_SPOTIFY_PAUSED.replace(
        "Media button session is MediaSessionRecord 3f2a1b com.google.android.youtube/youtube-session (userId=0)",
        "Media button session is MediaSessionRecord 9c8d7e com.spotify.music/spotify (userId=0)",
    )

    val FOCUS = """
  mCurrentFocus=Window{a1b2c3d u0 com.instagram.android/com.instagram.mainactivity.MainActivity}
  mFocusedApp=ActivityRecord{e4f5a6 u0 com.instagram.android/.mainactivity.MainActivity t42}
""".trimIndent()
}

class MediaSessionDumpTest {

    @Test
    fun `sessions are listed in order with state position and description`() {
        val dump = MediaSessionDump.parse(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertEquals(2, dump.sessions.size)
        val yt = dump.sessions[0]
        assertEquals("com.google.android.youtube", yt.packageName)
        assertEquals(PlayState.PLAYING, yt.state)
        assertEquals(45_210L, yt.positionMs)
        assertEquals("Funny cat compilation, SomeChannel, null", yt.description)
        assertTrue(yt.active)
        val sp = dump.sessions[1]
        assertEquals("com.spotify.music", sp.packageName)
        assertEquals(PlayState.PAUSED, sp.state)
    }

    @Test
    fun `the whole dump of android 14 and 15 is read - sessions, states, button holder, nothing from the other sections`() {
        for ((name, text) in listOf("14" to Fixtures.ANDROID_14_FULL_DUMP, "15" to Fixtures.ANDROID_15_FULL_DUMP)) {
            val dump = MediaSessionDump.parse(text)
            assertEquals("android $name: sessions", listOf("com.google.android.youtube", "com.spotify.music"), dump.sessions.map { it.packageName })
            assertEquals("android $name: button holder", "com.google.android.youtube", dump.mediaButtonPackage)
            val yt = dump.forPackage("com.google.android.youtube")!!
            assertEquals("android $name: state", PlayState.PLAYING, yt.state)
            assertEquals(45_210L, yt.positionMs)
            assertTrue(yt.active)
            assertEquals(PlayState.PAUSED, dump.forPackage("com.spotify.music")!!.state)
            assertNull("android $name: no session for an app that only appears in the audio list", dump.forPackage("com.android.systemui"))
        }
    }

    @Test
    fun `an outline of a real shaped dump still parses to the same sessions and shows no names`() {
        val outline = MediaSessionDump.outline(Fixtures.ANDROID_15_FULL_DUMP)
        for (secret in listOf("youtube", "spotify", "systemui", "Funny cat", "SomeChannel", "Artist", "4f1c2a", "9b8d7e")) {
            assertFalse("outline must not contain '$secret':\n$outline", outline.contains(secret, ignoreCase = true))
        }
        val reparsed = MediaSessionDump.parse(outline)
        assertEquals(listOf(PlayState.PLAYING, PlayState.PAUSED), reparsed.sessions.map { it.state })
        assertEquals(2, reparsed.sessions.size)
        assertEquals(reparsed.sessions[0].packageName, reparsed.mediaButtonPackage)
    }

    @Test
    fun `the media button session is identified`() {
        assertEquals("com.google.android.youtube", MediaSessionDump.parse(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED).mediaButtonPackage)
        assertEquals("com.spotify.music", MediaSessionDump.parse(Fixtures.SPOTIFY_HAS_BUTTON).mediaButtonPackage)
    }

    @Test
    fun `a state printed with its name is read the same way`() {
        val dump = MediaSessionDump.parse(Fixtures.NAMED_STATE)
        assertEquals(PlayState.PLAYING, dump.sessions.single().state)
        assertEquals(2_000L, dump.sessions.single().positionMs)
        assertNull("the literal text 'null' is not a description", dump.sessions.single().description)
    }

    @Test
    fun `garbage and empty dumps yield no sessions rather than errors`() {
        assertTrue(MediaSessionDump.parse("").sessions.isEmpty())
        assertTrue(MediaSessionDump.parse("Permission Denial: can't dump").sessions.isEmpty())
        assertTrue(MediaSessionDump.parse("\u0000\u0001 random { } = bytes").sessions.isEmpty())
    }

    @Test
    fun `a session with no state line is unknown and never playing`() {
        val dump = MediaSessionDump.parse(
            """
            tag MediaSessionRecord 1 com.x.y/tag (userId=0)
              package=com.x.y
              active=true
            """.trimIndent(),
        )
        assertEquals(PlayState.UNKNOWN, dump.sessions.single().state)
    }

    @Test
    fun `lookup prefers the playing session of a package`() {
        val text = """
            a MediaSessionRecord 1 com.x.y/a (userId=0)
              package=com.x.y
              state=PlaybackState {state=2, position=1}
            b MediaSessionRecord 2 com.x.y/b (userId=0)
              package=com.x.y
              state=PlaybackState {state=3, position=9}
        """.trimIndent()
        assertEquals(9L, MediaSessionDump.parse(text).forPackage("com.x.y")!!.positionMs)
        assertNull(MediaSessionDump.parse(text).forPackage("com.other"))
    }

    @Test
    fun `playback state codes map to play states`() {
        assertEquals(PlayState.PLAYING, PlaybackStateCodes.toPlayState(3))
        assertEquals(PlayState.PAUSED, PlaybackStateCodes.toPlayState(2))
        assertEquals(PlayState.BUFFERING, PlaybackStateCodes.toPlayState(6))
        assertEquals(PlayState.STOPPED, PlaybackStateCodes.toPlayState(7))
        assertEquals(PlayState.NONE, PlaybackStateCodes.toPlayState(0))
        assertEquals(PlayState.UNKNOWN, PlaybackStateCodes.toPlayState(null))
        assertEquals(PlayState.UNKNOWN, PlaybackStateCodes.toPlayState(99))
    }

    @Test
    fun `an outline keeps the shape and the states and drops names and content`() {
        val outline = MediaSessionDump.outline(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        for (secret in listOf("youtube", "spotify", "Funny cat", "SomeChannel", "Song", "Artist", "com.")) {
            assertFalse("outline must not contain '$secret':\n$outline", outline.contains(secret, ignoreCase = true))
        }
        assertTrue(outline.contains("state=PlaybackState {state=3"))
        assertTrue(outline.contains("state=PlaybackState {state=2"))
        assertTrue(outline.contains("active=true"))
        assertTrue(outline.contains("Media button session is"))
        assertTrue("the same app keeps the same placeholder", outline.lines().first { it.startsWith("Media button") }.contains("app1.pkg/"))
        // and what it keeps still parses to the same sessions
        assertEquals(2, outline.lines().count { it.contains("(userId=0)") && !it.startsWith("Media button") })
    }

    @Test
    fun `an outline hides a tag with spaces, custom actions, error texts and metadata of any shape`() {
        val text = """
Media button session is com.x.player/YouTube Music Session (userId=0)
Sessions Stack - have 1 sessions:
  YouTube Music Session com.x.player/YouTube Music Session (userId=0)
    ownerPid=1, ownerUid=10001, userId=0
    package=com.x.player
    active=true
    state=PlaybackState {state=PLAYING(3), position=777, buffered position=0, speed=1.0, updated=5, actions=823, custom actions=[Action:mName='Secret Playlist', mIcon=1], active item id=-1, error=Cannot play My Secret Song}
    metadata: Funny cat video by SomeChannel
    queueTitle=Secret Queue, size=4
""".trimIndent()
        val outline = MediaSessionDump.outline(text)
        for (secret in listOf("YouTube", "Music", "Session ", "Secret", "Funny", "SomeChannel", "player", "Cannot play")) {
            assertFalse("outline must not contain '$secret':\n$outline", outline.contains(secret))
        }
        val again = MediaSessionDump.parse(outline).sessions.single()
        assertEquals(PlayState.PLAYING, again.state)
        assertEquals(777L, again.positionMs)
        assertTrue(outline.contains("actions=823"))
    }

    @Test
    fun `an outline of garbage is empty and a long dump is cut`() {
        assertEquals("", MediaSessionDump.outline("Permission Denial: can't dump"))
        val many = (1..200).joinToString("\n") { "  s$it MediaSessionRecord $it com.x.p$it/s$it (userId=0)\n    package=com.x.p$it\n    active=true" }
        assertEquals(60, MediaSessionDump.outline(many).lines().size)
    }

    @Test
    fun `focus dumps give the foreground package`() {
        assertEquals("com.instagram.android", FocusDump.foregroundPackage(Fixtures.FOCUS))
        assertEquals(
            "com.google.android.youtube",
            FocusDump.foregroundPackage("  topResumedActivity=ActivityRecord{12ab34 u0 com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity t77}"),
        )
        assertEquals(
            "com.reddit.frontpage",
            FocusDump.foregroundPackage("    mResumedActivity: ActivityRecord{9f u0 com.reddit.frontpage/.MainActivity t3}"),
        )
        assertNull(FocusDump.foregroundPackage("mCurrentFocus=null"))
    }
}

class PrivilegedCommandsTest {

    @Test
    fun `valid identifiers are accepted`() {
        assertTrue(ShellQuote.isPackage("com.eyeblink.app"))
        assertTrue(ShellQuote.isPackage("com.eyeblink.app.debug"))
        assertTrue(ShellQuote.isComponent("com.eyeblink.app/.service.Listener"))
        assertTrue(ShellQuote.isComponent("com.eyeblink.app/com.eyeblink.app.A\$B"))
        assertTrue(ShellQuote.isPermission("android.permission.WRITE_SECURE_SETTINGS"))
    }

    @Test
    fun `anything that could inject a command is rejected`() {
        for (bad in listOf("com.x; rm -rf /", "com.x y", "com.x\$(id)", "com.x`id`", "", "nodots", ".leading.dot", "a.b|c", "a.b&c", "a.b\nc")) {
            assertFalse("package [$bad]", ShellQuote.isPackage(bad))
            assertNull(PrivilegedCommands.grantPermission(bad, "android.permission.X"))
        }
        assertNull(PrivilegedCommands.grantPermission("com.x.y", "android.permission.X; id"))
        assertFalse(ShellQuote.isComponent("com.x.y/Cls; id"))
        assertNull(PrivilegedCommands.putSecure("key; id", "1"))
        assertNull(PrivilegedCommands.appOpsAllow("com.x.y", "SYSTEM_ALERT_WINDOW; id"))
        assertNull(PrivilegedCommands.deviceIdleWhitelist("com.x y"))
    }

    @Test
    fun `built commands are exactly what is documented`() {
        assertEquals(
            "pm grant 'com.eyeblink.app' 'android.permission.WRITE_SECURE_SETTINGS'",
            PrivilegedCommands.grantPermission("com.eyeblink.app", "android.permission.WRITE_SECURE_SETTINGS"),
        )
        assertEquals("appops set 'com.eyeblink.app' SYSTEM_ALERT_WINDOW allow", PrivilegedCommands.appOpsAllow("com.eyeblink.app", "SYSTEM_ALERT_WINDOW"))
        assertEquals("settings put secure night_display_activated '1'", PrivilegedCommands.putSecure("night_display_activated", "1"))
    }

    @Test
    fun `media keys are explicit never a toggle`() {
        val all = MediaKey.entries.flatMap { PrivilegedCommands.dispatchMediaKey(it) }
        assertTrue(all.none { it.contains("play-pause") || it.contains("85") })
        assertEquals(listOf("pause", "play"), MediaKey.entries.map { it.arg }.sorted())
    }

    @Test
    fun `accessibility merging keeps the users other services and never duplicates`() {
        val me = "com.eyeblink.app/.accessibility.EyeBlinkAccessibilityService"
        assertEquals(me, AccessibilityList.withService(null, me))
        assertEquals(me, AccessibilityList.withService("null", me))
        assertEquals(me, AccessibilityList.withService("  ", me))
        assertEquals("a.b/.C:$me", AccessibilityList.withService("a.b/.C", me))
        assertEquals("a.b/.C:d.e/.F:$me", AccessibilityList.withService("a.b/.C:d.e/.F", me))
        assertEquals("a.b/.C:$me", AccessibilityList.withService("a.b/.C:$me", me))
        assertTrue(AccessibilityList.contains("a.b/.C:$me", me))
        assertFalse(AccessibilityList.contains("a.b/.C", me))
    }
}

class PowerProbeTest {

    private fun shell(id: ShellResult, media: ShellResult = ok("MEDIA SESSIONS"), focus: ShellResult = ok("mCurrentFocus=Window{1 u0 a.b/c}")) =
        ScriptedShell { cmd ->
            when {
                cmd == "id" -> id
                cmd.startsWith("getprop") -> ok("34")
                cmd.startsWith("dumpsys media_session") -> media
                cmd.startsWith("dumpsys window") -> focus
                else -> null
            }
        }

    @Test
    fun `root is recognised and given every capability`() = runTest {
        val s = PowerProbe.probe(PowerKind.ROOT, shell(ok("uid=0(root) gid=0(root)")))
        assertTrue(s.available)
        assertEquals(0, s.uid)
        assertEquals(34, s.sdk)
        assertTrue(Capability.entries.all { s.can(it) })
    }

    @Test
    fun `a shizuku shell uid is accepted and labelled as adb level`() = runTest {
        val s = PowerProbe.probe(PowerKind.SHIZUKU, shell(ok("uid=2000(shell) gid=2000(shell)")))
        assertTrue(s.available)
        assertEquals(2000, s.uid)
        assertTrue(s.detail!!.contains("shell"))
    }

    @Test
    fun `any other uid is refused`() = runTest {
        val s = PowerProbe.probe(PowerKind.ROOT, shell(ok("uid=10234(u0_a234)")))
        assertFalse(s.available)
        assertFalse(s.can(Capability.SHELL))
    }

    @Test
    fun `a shell that cannot run id is unavailable with a reason`() = runTest {
        val s = PowerProbe.probe(PowerKind.ROOT, shell(fail(127, "su: not found")))
        assertFalse(s.available)
        assertTrue(s.detail!!.contains("not found"))
        val timedOut = PowerProbe.probe(PowerKind.ROOT, ScriptedShell { ShellResult(-1, "", timedOut = true) })
        assertFalse(timedOut.available)
        assertTrue(timedOut.detail!!.contains("grant"))
    }

    @Test
    fun `media control is only claimed when the dump actually works`() = runTest {
        val s = PowerProbe.probe(PowerKind.SHIZUKU, shell(ok("uid=2000(shell)"), media = fail(1, "Permission Denial")))
        assertTrue(s.available)
        assertFalse(s.can(Capability.MEDIA_CONTROL))
        assertTrue(s.can(Capability.SELF_GRANT))
    }

    @Test
    fun `kinds map back to operating modes`() {
        assertEquals(com.eyeblink.core.model.OperatingMode.ROOT, PowerProbe.modeFor(PowerKind.ROOT))
        assertEquals(com.eyeblink.core.model.OperatingMode.ROOTLESS, PowerProbe.modeFor(PowerKind.NONE))
    }
}

class PrivilegedPlaybackBackendTest {

    private fun backend(dump: String, dispatch: (String) -> ShellResult = { ok() }): Pair<PrivilegedPlaybackBackend, ScriptedShell> {
        val shell = ScriptedShell { cmd ->
            when {
                cmd.startsWith("dumpsys media_session") -> ok(dump)
                else -> dispatch(cmd)
            }
        }
        return PrivilegedPlaybackBackend(shell) to shell
    }

    @Test
    fun `snapshot reads the targets session`() = runTest {
        val (b, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        val s = b.snapshot("com.google.android.youtube")
        assertEquals(PlayState.PLAYING, s.state)
        assertNull("a playing session's dumped position is where it was when it last reported, not now", s.positionMs)
        assertEquals("com.google.android.youtube", s.sourcePackage)
        assertNotNull(s.contentKey)
        // a paused one stands still, so its position is the position
        assertEquals(1_000L, b.snapshot("com.spotify.music").positionMs)
    }

    @Test
    fun `an app without a session reads as none not unknown`() = runTest {
        val (b, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertEquals(PlayState.NONE, b.snapshot("com.instagram.android").state)
    }

    @Test
    fun `an unreadable dump reads as unknown`() = runTest {
        val shell = ScriptedShell { fail(1, "denied") }
        assertEquals(PlayState.UNKNOWN, PrivilegedPlaybackBackend(shell).snapshot("com.x.y").state)
    }

    @Test
    fun `pause sends an explicit pause key when the target holds the media button`() = runTest {
        val (b, shell) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertTrue(b.pause("com.google.android.youtube"))
        assertEquals("cmd media_session dispatch pause", shell.commands.last())
        assertTrue(shell.commands.none { it.contains("play-pause") })
    }

    @Test
    fun `a real shaped android 14 and 15 dump is enough to pause and resume`() = runTest {
        for (text in listOf(Fixtures.ANDROID_14_FULL_DUMP, Fixtures.ANDROID_15_FULL_DUMP)) {
            val (b, shell) = backend(text)
            assertEquals(PlayState.PLAYING, b.snapshot("com.google.android.youtube").state)
            assertTrue(b.pause("com.google.android.youtube"))
            assertEquals("cmd media_session dispatch pause", shell.commands.last())
            assertTrue(b.resume("com.google.android.youtube"))
            assertEquals("cmd media_session dispatch play", shell.commands.last())
            assertFalse("keys must not be sent for an app that has no session", b.pause("com.android.systemui"))
        }
    }

    @Test
    fun `keys are never sent when another app holds the media button`() = runTest {
        val (b, shell) = backend(Fixtures.SPOTIFY_HAS_BUTTON)
        assertFalse("would land on Spotify", b.pause("com.google.android.youtube"))
        assertTrue(shell.commands.none { it.startsWith("cmd media_session dispatch") || it.startsWith("input") })
    }

    private fun withoutButtonLine(dump: String) = dump.lines().filterNot { it.startsWith("Media button session") }.joinToString("\n")

    @Test
    fun `a dump that does not name the media button holder is enough when nothing else is playing`() = runTest {
        val (b, shell) = backend(withoutButtonLine(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED))
        assertTrue(b.pause("com.google.android.youtube"))
        assertEquals("cmd media_session dispatch pause", shell.commands.last())
    }

    @Test
    fun `and it is not when another app is playing, because the key could land there`() = runTest {
        val bothPlaying = withoutButtonLine(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED).replace("state=PlaybackState {state=2,", "state=PlaybackState {state=3,")
        val (b, shell) = backend(bothPlaying)
        assertFalse(b.pause("com.google.android.youtube"))
        assertTrue(shell.commands.none { it.startsWith("cmd media_session dispatch") || it.startsWith("input") })
        assertTrue(b.lastProblem.orEmpty(), b.lastProblem.orEmpty().contains("another app is playing"))
    }

    @Test
    fun `keys are never sent for an app that has no session`() = runTest {
        val (b, shell) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertFalse(b.pause("com.instagram.android"))
        assertTrue(shell.commands.none { it.contains("dispatch") || it.startsWith("input") })
    }

    @Test
    fun `dispatch falls back through the chain when the first tool fails`() = runTest {
        val (b, shell) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED) { cmd ->
            if (cmd.startsWith("cmd media_session")) fail(255, "Can't find service") else ok()
        }
        assertTrue(b.resume("com.google.android.youtube"))
        val sent = shell.commands.filter { !it.startsWith("dumpsys") }
        assertEquals(listOf("cmd media_session dispatch play", "media dispatch play"), sent)
    }

    @Test
    fun `if every delivery path fails the command is reported as not delivered`() = runTest {
        val (b, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED) { fail(1) }
        assertFalse(b.pause("com.google.android.youtube"))
        assertTrue(b.lastProblem!!.contains("pause key could not be sent"))
    }

    @Test
    fun `a refused command says why`() = runTest {
        val (spotifyHasKeys, _) = backend(Fixtures.SPOTIFY_HAS_BUTTON)
        assertFalse(spotifyHasKeys.pause("com.google.android.youtube"))
        assertTrue(spotifyHasKeys.lastProblem!!.contains("different app"))

        val (noSession, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertFalse(noSession.resume("com.instagram.android"))
        assertTrue(noSession.lastProblem!!.contains("no media session"))

        val unreadable = PrivilegedPlaybackBackend(ScriptedShell { fail(1, "denied") })
        assertFalse(unreadable.pause("com.x.y"))
        assertTrue(unreadable.lastProblem!!.contains("could not be read"))
    }

    @Test
    fun `a delivered command leaves no problem behind`() = runTest {
        val (b, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED) { fail(1) }
        assertFalse(b.pause("com.google.android.youtube"))
        val (good, _) = backend(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED)
        assertTrue(good.pause("com.google.android.youtube"))
        assertNull(good.lastProblem)
    }

    @Test
    fun `the dump read for the snapshot is reused for the command but never for the next snapshot`() = runTest {
        var now = 1_000L
        val shell = ScriptedShell { cmd -> if (cmd.startsWith("dumpsys media_session")) ok(Fixtures.YOUTUBE_PLAYING_SPOTIFY_PAUSED) else ok() }
        val b = PrivilegedPlaybackBackend(shell) { now }
        b.snapshot("com.google.android.youtube")
        now += 100
        assertTrue(b.pause("com.google.android.youtube"))
        assertEquals("one dump for the snapshot and the command", 1, shell.commands.count { it.startsWith("dumpsys") })

        // the key went out: the next look must be a new one
        b.snapshot("com.google.android.youtube")
        assertEquals(2, shell.commands.count { it.startsWith("dumpsys") })
        b.snapshot("com.google.android.youtube")
        assertEquals("a snapshot never reuses an old dump", 3, shell.commands.count { it.startsWith("dumpsys") })

        // an old dump is not good enough for a command either
        now += 5_000
        assertTrue(b.resume("com.google.android.youtube"))
        assertEquals(4, shell.commands.count { it.startsWith("dumpsys") })
    }
}

class PrivilegedSetupTest {

    private val svc = ServiceComponents(
        packageName = "com.eyeblink.app",
        notificationListener = "com.eyeblink.app/.system.MediaListenerService",
        accessibility = "com.eyeblink.app/.accessibility.EyeBlinkAccessibilityService",
    )

    @Test
    fun `only the requested steps are run`() = runTest {
        val shell = ScriptedShell { ok() }
        val out = PrivilegedSetup(shell, svc).run(setOf(SetupStepId.OVERLAY), sdk = 34)
        assertEquals(listOf(SetupStepId.OVERLAY), out.map { it.id })
        assertEquals(listOf("appops set 'com.eyeblink.app' SYSTEM_ALERT_WINDOW allow"), shell.commands)
    }

    @Test
    fun `camera is never part of the setup`() = runTest {
        val shell = ScriptedShell { ok() }
        PrivilegedSetup(shell, svc).run(SetupStepId.entries.toSet(), sdk = 34)
        assertTrue(shell.commands.none { it.contains("CAMERA") })
    }

    @Test
    fun `a failing step does not stop the others`() = runTest {
        val shell = ScriptedShell { cmd -> if (cmd.contains("SYSTEM_ALERT_WINDOW")) fail(1, "denied") else ok() }
        val out = PrivilegedSetup(shell, svc).run(setOf(SetupStepId.OVERLAY, SetupStepId.USAGE_ACCESS), 34)
        assertFalse(out.first { it.id == SetupStepId.OVERLAY }.ok)
        assertTrue(out.first { it.id == SetupStepId.USAGE_ACCESS }.ok)
    }

    @Test
    fun `accessibility is merged with what the user already enabled`() = runTest {
        val shell = ScriptedShell { cmd ->
            if (cmd == "settings get secure enabled_accessibility_services") ok("com.other/.Svc") else ok()
        }
        val out = PrivilegedSetup(shell, svc).run(setOf(SetupStepId.ACCESSIBILITY), 34)
        assertTrue(out.single().ok)
        assertTrue(shell.commands.contains("settings put secure enabled_accessibility_services 'com.other/.Svc:com.eyeblink.app/.accessibility.EyeBlinkAccessibilityService'"))
        assertTrue(shell.commands.contains("settings put secure accessibility_enabled '1'"))
    }

    @Test
    fun `accessibility is not touched if the current value cannot be read`() = runTest {
        val shell = ScriptedShell { cmd -> if (cmd.startsWith("settings get")) fail(1, "boom") else ok() }
        val out = PrivilegedSetup(shell, svc).run(setOf(SetupStepId.ACCESSIBILITY), 34)
        assertFalse(out.single().ok)
        assertTrue("must not write blind", shell.commands.none { it.startsWith("settings put") })
    }

    @Test
    fun `notification permission is skipped before android 13`() = runTest {
        val shell = ScriptedShell { ok() }
        val out = PrivilegedSetup(shell, svc).run(setOf(SetupStepId.NOTIFICATIONS), sdk = 31)
        assertTrue(out.single().ok)
        assertTrue(shell.commands.isEmpty())
        PrivilegedSetup(shell, svc).run(setOf(SetupStepId.NOTIFICATIONS), sdk = 34)
        assertEquals("pm grant 'com.eyeblink.app' 'android.permission.POST_NOTIFICATIONS'", shell.commands.single())
    }

    @Test
    fun `a malformed component is refused rather than run`() = runTest {
        val bad = svc.copy(notificationListener = "com.eyeblink.app/.X; reboot")
        val shell = ScriptedShell { ok() }
        val out = PrivilegedSetup(shell, bad).run(setOf(SetupStepId.NOTIFICATION_LISTENER), 34)
        assertFalse(out.single().ok)
        assertTrue(shell.commands.isEmpty())
    }
}

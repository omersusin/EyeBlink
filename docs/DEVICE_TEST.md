# First test on a real phone

Everything below the unit tests has been built and linted by CI but never run on a device. This is the order to try
things in, with what you should see. Each step is small; stop at the first thing that surprises you and send the
**Copy report** text from *More → Diagnostics* (versions, permissions and counters only - nothing personal) with a note of which step.

Get the build: GitHub → **Actions** → the latest green **Android CI** run → artifact `eyeblink-release-arm64` (the ordinary
install) or `eyeblink-debug-arm64` (package `com.eyeblink.app.debug`; install it as well while testing, because its crash logs are readable).

## If it crashes

On a rooted phone, from Termux:

```
su -c "logcat -b all -c"                                       # clear the old logs
# open EyeBlink and let it crash, then:
su -c "logcat -b crash -d -v threadtime" > ~/eyeblink-crash.txt
cat ~/eyeblink-crash.txt
```

Without root, `adb logcat -b crash -d` from a computer shows the same thing. What helps most is the block that starts at
`FATAL EXCEPTION`, with every `Caused by:` line, and whether it happened at launch or after a particular tap.

If nothing crashes but the camera does not do what it should (calibration stops, no pictures), the app's own log, under the
tag `EyeBlink`, says how far it got: the front camera binding, the state CameraX reports (a camera that is in use or switched
off shows up here), the first picture and its analysis.

```
su -c "logcat -c"
# press Begin in Calibration, wait half a minute, then:
su -c "logcat -d -s EyeBlink:V" > ~/eyeblink-camera.txt
cat ~/eyeblink-camera.txt
```

## Rootless basics

1. **Onboarding.** Open the app. The welcome page offers a language chip and an Auto / Light / Dark switch; both should change the app at once.
   Choose a few apps (Instagram, YouTube...) and allow *Accessibility* when asked.
2. **Ready.** *Today* should offer **Wake EyeBlink**. If it still says "Finish setup" or "One quick step first", the accessibility service is not connected - check *More → Diagnostics → Health*.
3. **A blink.** Tap **Wake EyeBlink**, then **Blink**. You should see two lids close from the top and bottom - covering the status bar too - hold black for a beat and open again. Touch something while it plays: your touch should still reach the app underneath.
4. **A rest.** Tap **Rest**. A *Distance focus* countdown appears on black; tapping *Skip* ends it at once. Switch the style to *Blackout* in *Rhythm* and repeat.
5. **It leaves you alone.** Open YouTube (selected). Wait for the interval: a blink should arrive in about a minute (not within about half a second of a touch, not while the keyboard is up; a finger held still on the screen is not seen after that). Open the phone app or a banking app: nothing should be drawn there.
6. **The tile.** Add the *EyeBlink* Quick Settings tile. Tap it: everything stops and the overlay is gone. Tap again to resume.

## Video

7. **Media sessions.** Allow *Media session access* (the "Almost there" card on *Today*, or *More → Diagnostics → Permissions*). Open YouTube, play a long video, then in EyeBlink open YouTube's card and tap **Test compatibility**. Expect a notification that says EyeBlink can pause and resume it, and the card to read *pause verified*.
8. **Break with a video.** Leave a video playing and wait for a blink: it should pause under the blink and resume where it was. This works without pressing **Test compatibility** first: with *Media session access* (or Shizuku/Root) the first break tries it, checks it, and the app's card changes from *will try at the next break* to *pause verified*. Then pause the video yourself before a blink: it must **not** be resumed. If nothing is paused, *Today → Last break* and *More → Diagnostics → Video pausing* say why (no media session, player unreadable, media keys going to another app...), and *Copy report* includes a name-free outline of the phone's media sessions (the sessions of the apps you chose are marked `[selected]`), a line for each chosen app (`app1: UNTESTED, would try MEDIA_SESSION; nothing remembered`) and *how breaks met the player (week)* - `NO_SESSION=30` means the app shows no player to read, `NOT_PLAYING` that the video was not playing, `NOT_ATTEMPTED` that no way was left to try. Copy it right after a break in the app that does not pause.
9. **A feed app.** Repeat the test in Instagram or TikTok. It is likely to report "can't pause" or "not tested" - that is the honest answer for apps without a media session; check that blinks still happen over the video (or follow the fallback you chose in *Rhythm → Your video*; switch on *Fine-tune everything* to see that choice).

## Camera

10. **Bursts.** *Rhythm → Camera → Bursts*, allow the camera. *Today* should say the trigger is *Camera-assisted*, and Android's camera indicator and EyeBlink's "Camera on" notification should appear in short bursts. No picture is ever shown.
11. **Calibration.** *More → Calibration → Begin*. Watch, read, blink on purpose. Note whether it says it cannot see you (light, distance) and what the result page shows. If **Begin** seems to do nothing, the page should now show a red card: *Camera access was not allowed* when Android refused the permission, or *The camera could not start* with the reason (the face model, the camera, a missing library) - that sentence is the bug report. Then look at *Diagnostics → Performance*: frame analysis p50/p95, the camera's share of active time and the last camera problem, if any. In the *Blink* step the bar under the eye shows how far your eyelids moved, and turns yellow once that counts as a blink; if you have narrow or heavy-lidded eyes and nothing counts for a few seconds, the page says so and the test eases by itself. If blinks are still missed afterwards, try *Rhythm → Camera → Eye sensitivity → Higher*. Two checks worth a minute: close your eyes and hold them shut for five seconds in the *Blink* step - nothing may count, however long they stay shut - and after the result page open *More → Calibration → Try your blinks* and blink naturally for half a minute: the counter should follow your real blinks, and a glance down at the desk should not count (note how often it does; that is the number to report if the engine is too eager or too shy for your eyes).
12. **Android 14+.** Put the app in the background with Bursts on and wait a few minutes. The camera may stop; EyeBlink should fall back to its timer without crashing.

## Elevated access (optional)

13. **Shizuku.** Install and start Shizuku, then *More → Power modes → Shizuku → Grant access*. Tick the steps under *Set it up for me* and **Apply**: each should report *done*. Re-run *Test compatibility* for YouTube: a *Media session via Shizuku/Root* result should appear.
14. **Root.** Magisk, KernelSU or APatch: *Check for root*, grant it - the *Root* card is then selected for you (if it says *Ready, but this mode is not selected yet*, tap the card) - then the same *Set it up for me*. *More → Diagnostics → Video pausing* should list *Root / Shizuku: can read and pause media*; play a video in a selected app and wait for a break.
15. **Night.** *More → Night* → on, then turn on *Night Light*. Without access it shows the one-time `adb shell pm grant ... WRITE_SECURE_SETTINGS` command; with Shizuku or root, tick *Change Night Light* under *Power modes → Set it up for me* and **Apply**. Warmth should ramp in over the chosen time, and stop the moment you change brightness yourself.

## Over time

16. **Battery.** Use Camera-assisted for a few hours unplugged, then Camera-free for a few hours. *Diagnostics → Battery cost of the camera* should show a comparison only once both have enough hours; before that it says "not measured" on purpose.
17. **Reboot.** Switch on *More → Start with the phone*, leave EyeBlink running, restart the phone: the notification should come back without opening the app.

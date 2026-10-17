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
5. **It leaves you alone.** Open YouTube (selected). Wait for the interval: a blink should arrive in about a minute (not while a finger is on the screen, not while typing). Open the phone app or a banking app: nothing should be drawn there.
6. **The tile.** Add the *EyeBlink* Quick Settings tile. Tap it: everything stops and the overlay is gone. Tap again to resume.

## Video

7. **Media sessions.** Allow *Media session access* (the "Almost there" card on *Today*, or *More → Diagnostics → Permissions*). Open YouTube, play a long video, then in EyeBlink open YouTube's card and tap **Test compatibility**. Expect a notification that says EyeBlink can pause and resume it, and the card to read *pause verified*.
8. **Break with a video.** Leave a video playing and wait for a blink: it should pause under the blink and resume where it was. Then pause the video yourself before a blink: it must **not** be resumed.
9. **A feed app.** Repeat the test in Instagram or TikTok. It is likely to report "can't pause" or "not tested" - that is the honest answer for apps without a media session; check that blinks still happen over the video (or follow the fallback you chose in *Rhythm → Your video*; switch on *Fine-tune everything* to see that choice).

## Camera

10. **Bursts.** *Rhythm → Camera → Bursts*, allow the camera. *Today* should say the trigger is *Camera-assisted*, and Android's camera indicator and EyeBlink's "Camera on" notification should appear in short bursts. No picture is ever shown.
11. **Calibration.** *More → Calibration → Begin*. Watch, read, blink on purpose. Note whether it says it cannot see you (light, distance) and what the result page shows. If **Begin** seems to do nothing, the page should now show a red card: *Camera access was not allowed* when Android refused the permission, or *The camera could not start* with the reason (the face model, the camera, a missing library) - that sentence is the bug report. Then look at *Diagnostics → Performance*: frame analysis p50/p95, the camera's share of active time and the last camera problem, if any.
12. **Android 14+.** Put the app in the background with Bursts on and wait a few minutes. The camera may stop; EyeBlink should fall back to its timer without crashing.

## Elevated access (optional)

13. **Shizuku.** Install and start Shizuku, then *More → Power modes → Shizuku → Grant access*. Tick the steps under *Set it up for me* and **Apply**: each should report *done*. Re-run *Test compatibility* for YouTube: a *Media session via Shizuku/Root* result should appear.
14. **Root.** Magisk, KernelSU or APatch: *Check for root*, grant it, then the same *Set it up for me*.
15. **Night.** *More → Night* → on, then turn on *Night Light*. Without access it shows the one-time `adb shell pm grant ... WRITE_SECURE_SETTINGS` command; with Shizuku or root, tick *Change Night Light* under *Power modes → Set it up for me* and **Apply**. Warmth should ramp in over the chosen time, and stop the moment you change brightness yourself.

## Over time

16. **Battery.** Use Camera-assisted for a few hours unplugged, then Camera-free for a few hours. *Diagnostics → Battery cost of the camera* should show a comparison only once both have enough hours; before that it says "not measured" on purpose.
17. **Reboot.** Switch on *More → Start with the phone*, leave EyeBlink running, restart the phone: the notification should come back without opening the app.

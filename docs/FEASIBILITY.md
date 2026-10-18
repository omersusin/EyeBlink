# Feasibility: what Android lets EyeBlink do

EyeBlink was specified as a long list of ambitions. Before any of it was built, each one was checked
against what Android actually allows an ordinary app to do. This page is that check, kept honest:
**Yes** means a supported API does the job, **With limits** means it works under conditions that the
app has to handle, and **Best effort** means it cannot be made reliable and EyeBlink says so instead of pretending.

"Verified" below means covered by the JVM tests in `:core` or by CI. Nothing in this repository has yet been
run on a physical phone; see [Status](../README.md#status) for what that means for each part.

| Ambition | Verdict | How, and what limits it |
|---|---|---|
| Draw an eyelid-shaped blink over any app | **Yes** | An accessibility overlay (`TYPE_ACCESSIBILITY_OVERLAY`) needs no extra permission and can cover the status bar; `TYPE_APPLICATION_OVERLAY` ("Display over other apps") is the fallback. The blink is a plain `View` that draws the same eyelid geometry the live preview uses. On OLED the `#000000` hold emits no light; on LCD the backlight stays on, and the app says so. |
| Never block touches during a blink | **Yes** | The overlay is `FLAG_NOT_TOUCHABLE`; Android 12's untrusted-touch rules do not apply to a window that takes no touches. |
| Hybrid trigger: camera-assisted or camera-free | **With limits** | The camera path uses the front camera and MediaPipe Face Landmarker. A blink lasts 100-150 ms, so useful detection needs roughly 15+ fps and good light. Since Android 11 the camera can only be used while the app is visible or from a foreground service of type `camera`, and since Android 14 that service cannot be *started* from the background. EyeBlink therefore runs the camera in short duty-cycled bursts and falls back to its timer whenever the camera is unavailable. |
| Never claim a blink was detected without the camera | **Yes** | A design rule enforced in code: the trigger mode is part of the UI state, and every observed number carries its provenance (`observed` vs `estimated`). |
| Guided calibration, spontaneous vs instructed blinks separate | **Yes** | Needs the camera permission; frames are reduced to numbers in memory and discarded. The statistics keep the two kinds of blink in separate buckets. Eyes are measured against their own open level, not a fixed number, so narrow or heavy-lidded eyes can pass the deliberate-blink step; that is tested on simulated eyes, not yet on real ones. |
| Continuous learning without drifting | **With limits** | Small, gated updates (only from reliable frames: good light, face found, plausible rate) with a slow learning rate. It cannot be validated without real faces, so the thresholds are conservative. |
| Pause a video and resume it where it was | **With limits** | See the next section. This is the hardest requirement and the one that most depends on the app being paused. |
| Never resume something the user paused | **Yes** | Resume is refused if playback changed state in a way EyeBlink did not cause, if the media item changed, or if the app left the foreground. Covered by tests. |
| Micro Blink and Smart Rest (20-20-20) | **Yes** | Blackout and Distance Focus scenes are a Compose view hosted in a touchable overlay for the length of the rest. Confirmation is a tap or, with the camera, one real blink; the rest always ends by itself. |
| Don't interrupt touches, typing, calls, navigation, transitions | **With limits** | Android does not let an app watch another app's touches. EyeBlink uses a 1 px "outside touch" window (it receives the *down* of a touch, with no coordinates and no *up*), accessibility event *types* (a window changed, something scrolled, a text field took focus - never their content), the telephony state, the camera-in-use callback and the navigation notification category. Because the finger lifting is invisible, it waits out a debounce window instead. |
| Smart night mode (warmth, brightness) | **With limits** | Brightness needs `WRITE_SETTINGS`, which the user can grant. Night Light needs `WRITE_SECURE_SETTINGS`, which only adb, Shizuku or root can grant, and its implementation differs between manufacturers. Changes are gradual and EyeBlink steps back when the user changes brightness. |
| Rootless mode | **Yes** | Accessibility + notification listener + overlay + usage-access fallback. Everything except the optional items below works without any elevated access. |
| Shizuku and Root modes | **With limits** | Both run a short, fixed list of shell commands (`cmd media_session`, `dumpsys media_session`, `pm grant`, `appops`, `settings put secure ...`). Shizuku is reached by reflection on its private `newProcess`; root through `su`. They make setup one tap and add a more reliable view of media sessions. Not run on a device yet. |
| LSPosed module | **Best effort** | A separate, optional APK that can pause a player from inside the target app. It talks to EyeBlink through a signature-permission broadcast bridge. Player internals change with every app release, so it is only used for an app after its own test passes. |
| Optional screen analysis (MediaProjection) | **With limits** | Needs a consent dialog each session and shows a persistent capture indicator. The change detector (a 64-bit difference hash that stores no content) is implemented and tested in `:core`; the capture itself is not wired into the app. |
| Per-app, per-version compatibility testing | **Yes** | Results are keyed by package and version code; an app update marks them "retest advised". A strategy is only called *verified* after pause, confirmation, resume and confirmation all succeed - in a real test or in a real break. A strategy that reads the player back may be tried by the first real break, so nothing has to be tested by hand first. |
| Privacy and safety | **Yes** | No `INTERNET` permission (removed in the manifest and checked in CI), no window content read, camera frames never stored, overlays disappear if the service dies, and a denylist keeps EyeBlink off calls, payments, sign-in and system screens. |
| Battery and performance measurement | **With limits** | Frame time (p50/p95), camera duty cycle and battery drain are measured on the phone. A drain *comparison* needs hours of each mode, so until then the UI says "not measured" rather than guessing. |

## Pausing video, in detail

There is no single Android API for "pause whatever video is playing". EyeBlink tries strategies in this
order and only uses one that has proved itself for that app and version - by a test, or by a real break that paused, checked and resumed
(only the strategies that can read the player's own state back without touching the app's internals - media session, and the same through Shizuku or root - may be tried this way; the others, the LSPosed hook included, need a passed test first):

1. **LSPosed hook** (only if the module is installed and the app's test passed) - in-process, most precise, most fragile.
2. **Media session** - through the notification listener's access to `MediaSessionManager`; `transportControls.pause()`
   and `play()`, with the result *verified* by reading `PlaybackState`. This is the dependable one, and it works for
   every app that publishes a real session (most long-form video and audio apps).
3. **Privileged media session** - the same, but listing and controlling sessions through Shizuku or root.
4. **Media key** - a key event to whichever session is current. It toggles, so it can start playback instead of stopping it; used only if verified.
5. **Audio focus** - asks other apps to yield audio. Many pause, many only duck; used only if verified.
6. **Accessibility tap gesture** - last resort; needs a known tap position.

An **accessibility node click** on the player's own pause button would be the most direct route, but it needs
`canRetrieveWindowContent`, which would let the service read what is on screen. EyeBlink does not request it,
so that route is permanently unavailable by design.

Many short-video feeds (Reels, TikTok-style apps) publish no usable media session at all. For them no
rootless strategy can be verified. EyeBlink then follows the user's chosen fallback: blink anyway over the playing
video, wait for the next swipe, or leave that app alone.

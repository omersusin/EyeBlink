# EyeBlink — Smart Eye Comfort & Adaptive Blink System for Android

An intelligent Android eye-comfort app that introduces brief, natural visual breaks during prolonged smartphone use (Reels, Shorts, scrolling). Combines a realistic screen-closing "blink" animation with personalized blink observation (camera-assisted) and adaptive timer scheduling (camera-free), plus safe pause/resume of media playback.

> **Not medical.** Does not physically replace blinking or treat dry-eye disease. Provides brief visual breaks, interrupts continuous stimulation, encourages natural blinking, improves subjective comfort.

## Core features

- **Hybrid blink animation** — curved eyelid-shaped gradients close from top/bottom, subtle darkening, fully black `#000000` hold, smooth reopen. Default 140/150/165 ms (≈455 ms). All timings/curves/easing customizable. Live preview in settings; respects reduced-motion.
- **Hybrid trigger system** — camera-assisted blink detection with personal baseline, or adaptive timer from calibration data + usage + preferences. Never claims real-blink detection while camera inactive.
- **Guided calibration** — Phase A video viewing, Phase B reading, Phase C deliberate blinks. Spontaneous vs instructed blinks kept separate. Local only, no raw video.
- **Continuous personalization** — gradual model updates from reliable observations; context profiles (video/reading/social/low-light); observed vs estimated clearly distinguished.
- **Smart pause/resume** — layered playback control: MediaSession/MediaController → AccessibilityService → optional LSPosed hooks. Never toggles blindly; verifies pause and resumes only when safe. Fallback when unsupported.
- **Adaptive rest** — Micro Blink (auto-resume) and Smart Rest (longer, configurable; camera-confirmed blink or tap). Optional Blackout Rest / Distance Focus Rest (20-20-20). Skippable/cancellable.
- **Interruption scheduling** — avoids triggers during touch/swipe, text entry, calls, notifications, camera capture, navigation, video transitions, recent rests; cooldowns.
- **Smart Night Mode** — optional Night Light/brightness integration, gradual, user-controlled.
- **Rootless + LSPosed modes** — shared core; rootless uses standard Android APIs; LSPosed adapter is modular/optional.
- **Optional MediaProjection** — research feature, explicit consent, not default.
- **Per-app compatibility calibration** — detect player, pause/resume test, record working strategy; per-app/version settings.

## Privacy & safety

- Camera frames processed locally; no facial/eye data uploaded; no raw camera video saved in normal tracking.
- All sensitive permissions optional and explained; visible camera-active indicator; instant suspend via app or Quick Settings.
- Overlays disappear on service failure; avoids critical screens (emergency/payment/auth).
- Handles rotation, app changes, service restarts, low memory.
- Never resumes media the user intentionally paused.

## Architecture

Kotlin + Jetpack Compose settings. Components: Core, Blink Animation Engine, Eye Tracking Engine (MediaPipe on-device), Calibration Engine, Context Engine, Playback Controller, Accessibility Adapter, LSPosed Adapter, Night Display Controller, Privacy & Safety Manager.

## Milestones

1. Functional prototype — animation, black overlay, scheduling, app detection, safe activation.
2. Playback integration — MediaSession, accessibility control, verification, compatibility tests.
3. Personal calibration — guided video/text calibration, blink detection, personalized thresholds, local profiles.
4. Adaptive intelligence — hybrid triggering, context-aware scheduling, safe long rest, gradual personalization.
5. Advanced integration — optional LSPosed modules, night mode, performance optimization, optional screen analysis, wider compatibility.

## Status

**Implemented and compiling-ready (not yet built on a device):**

| Component | State |
|---|---|
| Hybrid blink animation (gradient eyelids, darken, black hold, reopen) | done |
| Reduced-motion path (snap closed, no sweep) | done |
| Adaptive timer scheduling with clamped intervals | done |
| Full-screen overlay via foreground service (`TYPE_APPLICATION_OVERLAY`) | done |
| Configurable timing + appearance persisted to DataStore | done |
| Settings UI with live preview, start/stop, master toggle | done |
| Quick Settings tile for one-tap suspend/resume | done |
| Context engine: screen-on, unlocked, foreground-app blocking | done |
| Accessibility adapter (bounded play/pause node search) | done |
| R8 + resource shrinking on release | done |
| Per-ABI release splits + App Bundle (50.8 MB → 14.4 MB) | done |
| Signed release APK (v2/v3, plus detached v4 `.idsig`) | done |
| Blink detection from MediaPipe face landmarks (on device) | code complete, unrun |
| Eyelid geometry → normalised openness | done |
| Guided calibration phase machine (A video / B reading / C deliberate) | done |
| Personal baseline statistics + gradual merge | done |
| Hybrid trigger policy (camera-assisted vs camera-free) | done |
| Local-only baseline storage | done |

**Playback pause/resume is coded but inert.** Cross-app `MediaSession`
control needs a permission a normal app cannot hold (see *Known platform
limits*), so `PlaybackController` always reports *unknown* and blinks proceed
without touching media. The logic is in place and its safety rules are unit
tested for when that path becomes reachable.

**Camera mode has never run.** MediaPipe, the bundled
`face_landmarker.task` model and CameraX all compile and package, and every
MediaPipe API used was verified with `javap` against the resolved AAR — but
no frame has ever been processed on a device. Landmark indices, openness
thresholds and the phase timings are all *unvalidated against real faces*.

**Not implemented yet:** the calibration screens (UI; the phase logic behind
them is done and tested), the camera tracking service that feeds the
detector, Smart Rest / Blackout Rest / Distance Focus Rest, per-app
compatibility testing, LSPosed adapter, MediaProjection.

The feature list above is the *design target*, not the current state.

### Tests

116 JVM unit tests, run in CI on every push. They cover the parts that can be
verified without a device:

- `BlinkConfigTest` — pins the documented 140 + 150 + 165 = **455 ms** contract.
- `InterruptionPolicyTest` — interruptions stay within 5 s – 10 min, and
  playback resumes **only** when EyeBlink paused it *and* it is still paused.
- `TriggerPolicyTest` — camera-off/unready/low-confidence/screen-off all fall
  back to camera-free and never claim real blink detection.
- `BlinkDetectorTest` — hysteresis, completeness, outlier rejection,
  dark-frame immunity.
- `EyeGeometryTest` — scale-invariant aperture mapping, clamping, monotonicity.
- `BlinkStatisticsTest` — median intervals, tracking-loss rejection, gradual
  merge, and the blink-rate → break-cadence mapping.
- `CalibrationSessionTest` — A/B spontaneous vs C instructed separation, phase
  progression, cancel/restart.

All calibration and policy logic is deliberately free of Android types so it
runs as plain JVM tests. What remains untested is precisely the untestable
part: CameraX plumbing, MediaPipe runtime behaviour, and anything the user
sees.

### Known gaps

- Blink interval is a fixed constant (`BlinkController.DEFAULT_INTERVAL_MS = 20s`);
  the adaptive interval formula was removed as dead logic.
- `ContextEngine.foregroundPackage()` needs the user to grant Usage Access;
  without it, blinks are suppressed entirely (fails safe).
- Camera-based triggering from the design is absent — timing is timer-only.
- Nothing here has been run on a device; behaviour is verified by inspection only.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35). No SDK is required to
clone.

```bash
./gradlew :app:testDebugUnitTest   # 116 unit tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease    # per-ABI signed slices
./gradlew :app:bundleRelease      # .aab for Play
```

### Release sizes

MediaPipe's `libmediapipe_tasks_jni.so` is ~11 MB *per ABI*, which made the
universal APK 50.8 MB. It is now split per ABI:

| Artifact | Size |
|---|---|
| `app-arm64-v8a-release.apk` | 14.4 MB |
| `app-armeabi-v7a-release.apk` | 11.4 MB |
| `app-x86_64-release.apk` | 16.8 MB |
| `app-release.aab` (Play) | 24.9 MB |

x86_64 is kept so emulators still work.

### CI

`.github/workflows/android.yml` runs on every push and PR to `main`, and on
manual dispatch. It lints, runs the unit tests, assembles a debug APK and an
R8-optimized release APK, verifies the release signature with `apksigner`, and
uploads everything as the `eyeblink-apk` artifact (30-day retention).

Release signing is enabled only when these secrets exist, otherwise Gradle
emits an unsigned APK instead of failing (so forks and secretless runs still
work):

| Secret | Contents |
|---|---|
| `KEYSTORE_BASE64` | base64 of the release keystore |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | signing key alias |
| `KEY_PASSWORD` | key password |

The keystore is decoded into `RUNNER_TEMP` and deleted at the end of the job.
Nothing is committed: `.gitignore` blocks `*.jks`, `*.p12`, `*.b64` and
`keystore.properties`.

The release APK is signed with **v2 + v3**, plus a detached **v4** `.idsig`.
It is a Play-ready signing identity but the APK itself is not Play-ready until
the listing exists. To rebuild locally:

```bash
# create keystore.properties (git-ignored) or export the same env vars
./gradlew assembleRelease
```

Superseded runs on the same ref are cancelled automatically.

### Permissions

| Permission | Why | Required? |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | draw the blink overlay | yes |
| `FOREGROUND_SERVICE` (+ `..._SPECIAL_USE`) | keep the overlay alive | yes |
| `POST_NOTIFICATIONS` | show the ongoing-service notification | Android 13+ |
| `PACKAGE_USAGE_STATS` | read foreground app to avoid interrupting calls | optional (grant in Settings) |
| `CAMERA` | front-camera blink detection during calibration and camera-assisted mode | optional |

### Known platform limits

**Cross-app playback control is not available to a normal app.**
`MEDIA_CONTENT_CONTROL` is `signature|privileged`, so `MediaSessionManager
.getActiveSessions()` throws `SecurityException` and lint rejects the
`uses-permission`. `PlaybackController` therefore reports playback state as
*unknown* and blinks **without** touching media. The reachable alternatives are
the notification-listener role, or the optional LSPosed hook. Do not remove the
`SecurityException` catch — it is what keeps the app honest about this.

### Camera and privacy

Camera permission is optional and never mandatory. Without it EyeBlink blinks
on a timer and stays quiet during calls; it never claims to detect real blinks.

When granted, frames are processed **on device** by MediaPipe and discarded.
EyeBlink stores no images, no landmark lists and no recordings, and the
baseline it derives is kept in local DataStore with no upload path. The only
persisted artefact is numeric: median blink interval, blink duration,
complete-blink ratio and sample count.

A visible foreground-service notification is shown while camera tracking runs.
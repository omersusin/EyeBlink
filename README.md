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
| Adaptive timer scheduling with clamps + cooldown bounds | done |
| Full-screen overlay via foreground service (`TYPE_APPLICATION_OVERLAY`) | done |
| Configurable timing + appearance persisted to DataStore | done |
| Settings UI with live preview, start/stop, master toggle | done |
| MediaSession pause/resume with state verification | done |
| Context engine: screen-on, unlocked, foreground-app blocking | done |
| Accessibility adapter (bounded play/pause node search) | done |

**Not implemented yet:** camera tracking and MediaPipe blink detection, guided
calibration (Milestone 3), adaptive learning (Milestone 4), LSPosed adapter,
MediaProjection, Smart Rest / Blackout Rest / Distance Focus Rest, per-app
compatibility testing, Quick Settings tile.

The feature list above is the *design target*, not the current state.

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
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:lintDebug
```

### CI

`.github/workflows/android.yml` runs on every push and PR to `main`, and on
manual dispatch. It lints, runs the unit tests, assembles both a debug and an
unsigned release APK, and uploads them as the `eyeblink-apk` artifact
(14-day retention).

The release APK is **unsigned** — no keystore lives in the repo. Sign it
locally before distributing:

```bash
zipalign -p 4 in.apk out.apk
apksigner sign --ks release.jks out.apk
```

Superseded runs on the same ref are cancelled automatically.

### Permissions

| Permission | Why | Required? |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | draw the blink overlay | yes |
| `FOREGROUND_SERVICE` (+ `..._SPECIAL_USE`) | keep the overlay alive | yes |
| `POST_NOTIFICATIONS` | show the ongoing-service notification | Android 13+ |
| `PACKAGE_USAGE_STATS` | read foreground app to avoid interrupting calls | optional (grant in Settings) |

### Known platform limits

**Cross-app playback control is not available to a normal app.**
`MEDIA_CONTENT_CONTROL` is `signature|privileged`, so `MediaSessionManager
.getActiveSessions()` throws `SecurityException` and lint rejects the
`uses-permission`. `PlaybackController` therefore reports playback state as
*unknown* and blinks **without** touching media. The reachable alternatives are
the notification-listener role, or the optional LSPosed hook. Do not remove the
`SecurityException` catch — it is what keeps the app honest about this.

Camera and Usage Access are requested in-app and are never mandatory; without
them EyeBlink still blinks on a timer and simply stays quiet during calls.
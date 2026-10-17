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

Technical feasibility evaluation and architecture design in progress. Core animation + scheduling prototype next.
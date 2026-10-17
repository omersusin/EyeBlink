#!/usr/bin/env bash
# Starts the debug build on an emulator and checks that it comes up, stays up and does its job.
#
# Run by .github/workflows/android.yml inside reactivecircus/android-emulator-runner, adb already connected:
#     bash .github/smoke/smoke.sh
# Everything it learns is printed, and screenshots, UI dumps and logs are left in $OUT for the workflow to upload.
#
# A crash, a dead process or an engine task that threw fails the run. What depends on the emulator image or on how the
# screen happens to look (a button that could not be found, a missing system app) is only a warning, so that a flaky
# emulator never hides a real result.

set -u

PKG=com.eyeblink.app.debug
ACTIVITY=com.eyeblink.app.host.MainActivity
A11Y=com.eyeblink.app.access.EyeBlinkAccessibilityService
OUT=${OUT:-build/smoke}
HERE=$(cd "$(dirname "$0")" && pwd)
FAILED=0
mkdir -p "$OUT"

step() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "::error::$*"; FAILED=1; }
warn() { echo "::warning::$*"; }
tools() { python3 "$HERE/smoke_tools.py" "$@"; }
ashell() { adb shell "$@" | tr -d '\r'; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || warn "no screenshot for $1"; }

# A screenshot, plus what the log needs to say about it: which colours it is made of and which words are on screen.
snap() {
  shot "$1"
  echo "[$1] $(tools palette "$OUT/$1.png")"
  if tools dump "$OUT/$1.xml"; then
    tools texts "$OUT/$1.xml" | head -30
  else
    echo "[$1] (the UI tree could not be read)"
  fi
}
alive() { ashell pidof "$PKG" | grep -Eq '^[0-9]+'; }
crash_log() { adb logcat -d -b crash -v threadtime 2>/dev/null | tr -d '\r'; }
engine_errors() { adb logcat -d -s EyeBlink:E 2>/dev/null | tr -d '\r'; }

# Fails when the app is gone, has recorded a crash, or its engine thread threw.
health() {
  local label=$1
  if ! alive; then fail "$label: the app is not running"; fi
  if crash_log | grep -q "FATAL EXCEPTION"; then
    fail "$label: the app crashed"
    crash_log | head -60
  fi
  if engine_errors | grep -q "engine task failed"; then
    fail "$label: an engine task threw"
    engine_errors | head -60
  fi
}

# Taps a label and waits for the page to settle; a warning (not a failure) when the label is not there.
press() {
  local shot_name=$1 label=$2
  shift 2
  if tools tap "$label" "$@"; then
    sleep 2
    snap "$shot_name"
  else
    warn "could not tap '$label' (step $shot_name)"
    return 1
  fi
}

collect() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1
  crash_log > "$OUT/crash.txt"
  ashell dumpsys activity services "$PKG" > "$OUT/dumpsys-services.txt" 2>&1
  ashell dumpsys accessibility > "$OUT/dumpsys-accessibility.txt" 2>&1
  ashell dumpsys window windows > "$OUT/dumpsys-windows.txt" 2>&1
  ashell "dumpsys notification --noredact" > "$OUT/dumpsys-notifications.txt" 2>&1
  adb shell "run-as $PKG cat shared_prefs/eyeblink.xml" > "$OUT/final-prefs.xml" 2>&1
}

# ── the device ──────────────────────────────────────────────────────────────────────────────────────────────────────

step "Device"
echo "Android $(ashell getprop ro.build.version.release) (API $(ashell getprop ro.build.version.sdk)), $(ashell getprop ro.product.cpu.abi)"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

step "Install"
apk=""
for candidate in app/build/outputs/apk/debug/*x86_64*.apk; do
  if [ -e "$candidate" ]; then apk=$candidate; break; fi
done
if [ -z "$apk" ]; then fail "no x86_64 debug APK was built"; exit 1; fi
echo "$apk"
if ! adb install -r -t "$apk"; then fail "the APK would not install"; exit 1; fi
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell appops set "$PKG" GET_USAGE_STATS allow

# ── 1. first start, then a walk through the app ─────────────────────────────────────────────────────────────────────

step "First start, as a new user"
adb logcat -c
timeout 120 adb shell am start -W -n "$PKG/$ACTIVITY" | tail -6
sleep 8
health "first start"
snap 01-welcome
if [ -f "$OUT/01-welcome.xml" ]; then
  tools has "$OUT/01-welcome.xml" Welcome Begin || warn "the welcome page is not what was expected"
else
  warn "could not read the UI tree"
fi

step "Onboarding"
press 02-how-it-works Begin &&
  press 03-pick-apps Next &&
  press 04-allow Next &&
  press 05-ready Next &&
  press 06-home "Start EyeBlink"
sleep 4
health "after onboarding"
ashell dumpsys activity services "$PKG" > "$OUT/services-after-onboarding.txt"
if grep -q "isForeground=true" "$OUT/services-after-onboarding.txt"; then
  echo "the foreground service is running"
else
  warn "no foreground service after 'Start EyeBlink'"
fi

step "A rest on demand"
if tools tap Rest --topmost --timeout 15; then
  sleep 4
  shot 07-rest
  echo "[07-rest] $(tools palette "$OUT/07-rest.png")"
  ashell dumpsys window windows | grep -E "EyeBlink" | head -5
  black=$(tools black "$OUT/07-rest.png")
  echo "black share of the screen during the rest: $black"
  if [ "$black" != "n/a" ] && awk -v b="$black" 'BEGIN { exit !(b < 0.5) }'; then
    warn "the rest scene covers only $black of the screen in pure black"
  fi
  tools tap Skip --timeout 6 || { echo "no Skip button visible; waiting the rest out"; sleep 25; }
  sleep 2
else
  warn "could not start a rest from the home screen"
fi
health "after a rest"

step "A blink on demand"
tools tap Blink --topmost --timeout 15 && sleep 3
health "after a blink"

step "Tabs, language and theme"
for tab in Rhythm Apps More; do
  press "tab-$(echo "$tab" | tr '[:upper:]' '[:lower:]')" "$tab" --bottommost --timeout 10
done
if tools tap English --timeout 10; then
  sleep 2
  snap 10-language-list
  if tools tap "Türkçe" --timeout 8; then
    sleep 3
    snap 11-turkish
    tools has "$OUT/11-turkish.xml" "Bugün" || warn "the app did not switch to Turkish"
    tools tap "Türkçe" --timeout 8 && sleep 2
    tools tap English --timeout 8 && sleep 2
    snap 12-english-again
  fi
else
  warn "could not open the language list"
fi
if tools tap Dark --timeout 8; then
  sleep 2
  snap 13-dark
fi
health "after tabs, language and theme"

adb logcat -d -v threadtime > "$OUT/logcat-1-walkthrough.txt" 2>&1

# ── 2. the automatic path: accessibility service, a selected app, a short interval ─────────────────────────────────

step "Automatic breaks"
adb shell am force-stop "$PKG"

fg=""
for candidate in com.android.documentsui com.google.android.documentsui com.android.contacts com.google.android.contacts \
  com.android.calendar com.google.android.calendar com.android.deskclock com.google.android.deskclock \
  com.android.chrome com.google.android.apps.maps; do
  if ashell pm list packages "$candidate" | grep -qx "package:$candidate"; then fg=$candidate; break; fi
done
echo "the app to keep in front: ${fg:-none found}"

component=""
if [ -n "$fg" ]; then
  component=$(ashell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$fg" | tail -1)
  case "$component" in */*) ;; *) component="" ;; esac
  echo "its launcher activity: ${component:-unknown}"
fi

# Preferences first, then the accessibility service, so the engine finds its settings when the service wakes it up.
tools seed "${fg:-com.android.documentsui}" > "$OUT/seed-eyeblink.xml"
adb shell "run-as $PKG sh -c 'rm -f shared_prefs/eyeblink.xml.bak; mkdir -p shared_prefs; cat > shared_prefs/eyeblink.xml'" < "$OUT/seed-eyeblink.xml"
adb shell settings put secure enabled_accessibility_services "$PKG/$A11Y"
adb shell settings put secure accessibility_enabled 1
adb logcat -c
timeout 120 adb shell am start -W -n "$PKG/$ACTIVITY" | tail -3
sleep 8
snap 20-running
health "start with saved settings"
ashell dumpsys accessibility | grep -i "eyeblink" | head -5 || true

if [ -n "$component" ]; then
  adb shell am start -n "$component"
  sleep 4
  snap 21-selected-app
fi

echo "waiting for an automatic blink..."
shown=no
deadline=$((SECONDS + 100))
while [ "$SECONDS" -lt "$deadline" ]; do
  sleep 5
  alive || break
  if adb shell "run-as $PKG cat shared_prefs/eyeblink.xml" 2>/dev/null | tr -d '\r' | tools ledger --quiet; then shown=yes; break; fi
done
adb shell "run-as $PKG cat shared_prefs/eyeblink.xml" 2>/dev/null | tr -d '\r' | tools ledger || true
if [ "$shown" = yes ]; then
  echo "an automatic blink was shown"
else
  warn "no automatic blink was recorded in 100 s (selected app: ${fg:-none})"
fi
snap 22-after-blinks
health "after automatic breaks"

# ── report ──────────────────────────────────────────────────────────────────────────────────────────────────────────

collect
step "What the app logged"
grep -E "EyeBlink|AndroidRuntime|FATAL|ANR in" "$OUT/logcat.txt" | tail -40 || true

step "Result"
if [ "$FAILED" -eq 0 ]; then echo "the app started, stayed up and drew its blinks"; fi
exit "$FAILED"

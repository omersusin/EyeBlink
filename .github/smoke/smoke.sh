#!/usr/bin/env bash
# Starts a build of the app on an emulator and checks that it comes up, stays up and does its job.
#
# Run by .github/workflows/android.yml inside reactivecircus/android-emulator-runner, adb already connected:
#     SMOKE_VARIANT=debug|minified bash .github/smoke/smoke.sh
# "minified" is the release build as R8 leaves it (see app/build.gradle.kts): it is where reflection and native
# callbacks break, so it is tried as well as the debug build.
# Everything it learns is printed, and screenshots, UI dumps and logs are left in $OUT for the workflow to upload.
#
# A crash, a dead process or an engine task that threw fails the run. What depends on the emulator image or on how the
# screen happens to look (a button that could not be found, a missing system app) is only a warning, so that a flaky
# emulator never hides a real result.

set -u

VARIANT=${SMOKE_VARIANT:-debug}
case "$VARIANT" in
  debug) PKG=com.eyeblink.app.debug ;;
  minified) PKG=com.eyeblink.app.minified ;;
  *) echo "::error::unknown SMOKE_VARIANT '$VARIANT'"; exit 2 ;;
esac
ACTIVITY=com.eyeblink.app.host.MainActivity
A11Y=com.eyeblink.app.access.EyeBlinkAccessibilityService
OUT=${OUT:-build/smoke/$VARIANT}
MAPPING=app/build/outputs/mapping/$VARIANT/mapping.txt
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
# EyeBlink's overlay window (the rest scene, a blink) exists only while something is being drawn over the screen.
overlay_up() { ashell dumpsys window windows | grep -Eq 'Window\{[0-9a-f]+ u0 EyeBlink\}'; }
crash_log() { adb logcat -d -b crash -v threadtime 2>/dev/null | tr -d '\r'; }

# Stack traces from the minified build use R8's short names; give them back their own when the SDK can.
retrace() {
  local tool=""
  for candidate in "${ANDROID_HOME:-/nonexistent}"/cmdline-tools/*/bin/retrace; do
    if [ -x "$candidate" ]; then tool=$candidate; break; fi
  done
  if [ -n "$tool" ] && [ -f "$MAPPING" ]; then "$tool" "$MAPPING" 2>/dev/null || cat; else cat; fi
}
engine_errors() { adb logcat -d -s EyeBlink:E 2>/dev/null | tr -d '\r'; }

# Fails when the app is gone, has recorded a crash, or its engine thread threw.
health() {
  local label=$1
  if ! alive; then fail "$label: the app is not running"; fi
  if crash_log | grep -q "FATAL EXCEPTION"; then
    fail "$label: the app crashed"
    crash_log | retrace | head -80
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

# EyeBlink's saved settings live in its private folder. run-as reaches that folder only for a debuggable build, and the
# minified build is not one (a debuggable build is optimised less, which once hid a failure that only the real release
# had), so there the emulator's root shell is used - the image allows it - and the file is given the folder's owner and
# SELinux label so the app can read and rewrite it.
ROOT=0
use_root_when_needed() {
  if adb shell "run-as $PKG true" >/dev/null 2>&1; then
    echo "run-as works: the build is debuggable"
    return
  fi
  adb root > /dev/null 2>&1
  for _ in 1 2 3 4 5; do
    adb wait-for-device
    if [ "$(ashell id -u)" = 0 ]; then
      ROOT=1
      echo "the build is not debuggable: using the emulator's root shell for its saved settings"
      return
    fi
    sleep 2
  done
  warn "no run-as and no root: the saved settings cannot be reached"
}
prefs_read() {
  if [ "$ROOT" = 1 ]; then
    adb shell "cat /data/data/$PKG/shared_prefs/eyeblink.xml" 2>/dev/null | tr -d '\r'
  else
    adb shell "run-as $PKG cat shared_prefs/eyeblink.xml" 2>/dev/null | tr -d '\r'
  fi
}
prefs_write() { # $1 = the file to install as the app's settings
  if [ "$ROOT" = 1 ]; then
    local dir=/data/data/$PKG uid label
    uid=$(ashell stat -c %u "$dir")
    label=$(ashell ls -dZ "$dir" | awk '{print $1}')
    adb push "$1" /data/local/tmp/eyeblink-seed.xml > /dev/null
    adb shell "mkdir -p $dir/shared_prefs && rm -f $dir/shared_prefs/eyeblink.xml.bak && cp /data/local/tmp/eyeblink-seed.xml $dir/shared_prefs/eyeblink.xml && chown -R $uid:$uid $dir/shared_prefs && (chcon -R $label $dir/shared_prefs || restorecon -R $dir/shared_prefs)"
    ashell ls -lZ "$dir/shared_prefs"
  else
    adb shell "run-as $PKG sh -c 'rm -f shared_prefs/eyeblink.xml.bak; mkdir -p shared_prefs; cat > shared_prefs/eyeblink.xml'" < "$1"
  fi
}

# What the app, CameraX and the camera service said, for when the camera does not deliver what it should.
camera_report() {
  echo "--- the app's own log ---"
  adb logcat -d -s EyeBlink:V 2>/dev/null | tr -d '\r' | tail -25
  echo "--- CameraX and the camera service ---"
  adb logcat -d -v threadtime 2>/dev/null | tr -d '\r' | grep -iE "camera" | grep -v "EyeBlink" | tail -50
  echo "--- the emulator's camera settings ---"
  grep -h "hw.camera" "$HOME"/.android/avd/*.avd/config.ini 2>/dev/null
  echo "--- the camera service: which cameras, which way they face, who has them open ---"
  adb shell dumpsys media.camera 2>/dev/null | tr -d '\r' | grep -iE "eyeblink|number of camera devices|static information|lens.facing|evict|error" | head -30
}

collect() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1
  crash_log | retrace > "$OUT/crash.txt"
  if [ -f "$MAPPING" ]; then cp "$MAPPING" "$OUT/mapping.txt"; fi
  ashell dumpsys activity services "$PKG" > "$OUT/dumpsys-services.txt" 2>&1
  ashell dumpsys accessibility > "$OUT/dumpsys-accessibility.txt" 2>&1
  ashell dumpsys window windows > "$OUT/dumpsys-windows.txt" 2>&1
  ashell "dumpsys notification --noredact" > "$OUT/dumpsys-notifications.txt" 2>&1
  prefs_read > "$OUT/final-prefs.xml" 2>&1
}

# ── the device ──────────────────────────────────────────────────────────────────────────────────────────────────────

step "Device"
echo "Android $(ashell getprop ro.build.version.release) (API $(ashell getprop ro.build.version.sdk)), $(ashell getprop ro.product.cpu.abi)"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# A slow emulator makes the launcher miss its deadline, and an "isn't responding" box then covers the app for the rest of
# the run. Hide such boxes, give the launcher a moment to settle, and let the UI helper press Wait on any that slip through.
adb shell settings put global hide_error_dialogs 1
adb shell input keyevent KEYCODE_HOME
sleep 15

step "Install"
apk=""
for candidate in app/build/outputs/apk/"$VARIANT"/*x86_64*.apk; do
  if [ -e "$candidate" ]; then apk=$candidate; break; fi
done
if [ -z "$apk" ]; then fail "no x86_64 $VARIANT APK was built"; exit 1; fi
echo "$apk"
if ! adb install -r -t "$apk"; then fail "the APK would not install"; exit 1; fi
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
adb shell pm grant "$PKG" android.permission.CAMERA
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell appops set "$PKG" GET_USAGE_STATS allow
use_root_when_needed

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
  # The rest scene is an overlay window, which uiautomator does not list, so Skip is pressed where it sits: bottom centre.
  # "Skipping is always one tap away" is a promise of the app; a rest that cannot be left would be its worst bug.
  if overlay_up; then echo "the rest scene's window is up"; else warn "no EyeBlink window while resting"; fi
  tools tapat 0.5 0.93
  ended=0
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.5
    if ! overlay_up; then ended=1; break; fi
  done
  if [ "$ended" = 1 ]; then
    echo "Skip ended the rest"
  else
    warn "Skip did not close the rest scene; waiting it out"
    sleep 25
  fi
  sleep 2
else
  warn "could not start a rest from the home screen"
fi
health "after a rest"

step "A blink on demand"
tools tap Blink --topmost --timeout 15 && sleep 3
health "after a blink"

step "Tabs"
for tab in Rhythm Apps More; do
  press "tab-$(echo "$tab" | tr '[:upper:]' '[:lower:]')" "$tab" --bottommost --timeout 10
done
health "after the tabs"

# The camera and the face model start here. A phone once swallowed their failure and showed the same page again, so
# the page now says what went wrong; the test reads it out.
step "Calibration"
if tools tap Calibration --timeout 15; then
  sleep 2
  snap 14-calibration-intro
  if tools tap Begin --timeout 10; then
    # Long enough for the page to show a verdict: the session gives up after 20 s without a single picture from the camera.
    sleep 26
    snap 15-calibration
    shown_texts=$(tools texts "$OUT/15-calibration.xml")
    if echo "$shown_texts" | grep -q "face model could not be loaded"; then
      # The face model is what R8 once broke in the release build only; this must never come back.
      fail "the face model could not be loaded"
      echo "$shown_texts" | grep -iE "face model|caused by|Error|Exception" | head -8
      camera_report
    elif adb logcat -d -s EyeBlink:I 2>/dev/null | tr -d '\r' | grep "calibration: the first camera picture"; then
      echo "pictures are arriving from the camera"
    elif echo "$shown_texts" | grep -q "no front camera"; then
      warn "this emulator has no front camera, so the camera pipeline was not exercised"
      camera_report
    else
      warn "no picture came from the camera during calibration"
      camera_report
    fi
    if tools has "$OUT/15-calibration.xml" "Camera on"; then
      echo "calibration is running: the camera and the face model came up"
      # Skip the three steps, so that the page a person reaches after three minutes is drawn at least once on a real runtime.
      for _ in 1 2 3; do
        tools tap "Skip this step" --timeout 8 && sleep 2
      done
      sleep 3
      snap 16-calibration-result
      if tools has "$OUT/16-calibration-result.xml" "Not enough to learn from yet" ||
        tools has "$OUT/16-calibration-result.xml" "Here is what I learned"; then
        echo "the calibration result page is drawn"
      else
        warn "no result page after skipping through the calibration"
      fi
    else
      warn "calibration did not stay running"
      echo "$shown_texts" | head -20
    fi
  else
    warn "could not press Begin on the calibration page"
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 2
else
  warn "could not open the calibration page"
fi
health "after calibration"

# The page that shows what the camera engine sees of the eyes. It starts the same camera and face model as the
# calibration, through a session of its own, and has to let go of them again.
step "Blink test"
adb logcat -c
if tools tap More --bottommost --timeout 15 && tools tap Calibration --scroll --timeout 15; then
  sleep 2
  if tools tap "Try your blinks" --scroll --timeout 10; then
    sleep 2
    snap 17-blink-test-intro
    if tools tap Begin --timeout 10; then
      sleep 14
      snap 18-blink-test
      if adb logcat -d -s EyeBlink:I 2>/dev/null | tr -d '\r' | grep "eye test: the first camera picture"; then
        echo "the blink test is receiving pictures"
      else
        warn "no picture came from the camera during the blink test"
        camera_report
      fi
      if tools has "$OUT/18-blink-test.xml" "Blinks seen"; then
        echo "the blink test page is running"
        tools tap Stop --timeout 8 && sleep 2
        snap 19-blink-test-stopped
        tools has "$OUT/19-blink-test-stopped.xml" "Begin" || warn "the blink test did not go back to its start after Stop"
      else
        warn "the blink test page did not stay running"
        tools texts "$OUT/18-blink-test.xml" | head -20
      fi
    else
      warn "could not press Begin on the blink test page"
    fi
    adb shell input keyevent KEYCODE_BACK
    sleep 2
  else
    warn "could not open the blink test page"
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 2
else
  warn "could not reach the calibration page for the blink test"
fi
health "after the blink test"

step "Language and theme"
if tools tap English --scroll --timeout 20; then
  sleep 2
  snap 10-language-list
  if tools tap "Türkçe" --timeout 8; then
    sleep 3
    snap 11-turkish
    tools has "$OUT/11-turkish.xml" "Bugün" || warn "the app did not switch to Turkish"
    tools tap "Türkçe" --scroll --timeout 15 && sleep 2
    tools tap English --timeout 8 && sleep 2
    snap 12-english-again
  fi
else
  warn "could not open the language list"
fi
if tools tap Dark --scroll --timeout 15; then
  sleep 2
  snap 13-dark
fi
health "after language and theme"

# Every page the app has, so a page that only breaks on a real Android runtime is found here and not on somebody's phone.
# The theme is Moonlit by now, which also puts the dark palette through every one of them.
step "Every page"
page_number=40
for page in Night "Power modes" Diagnostics Statistics Privacy; do
  page_number=$((page_number + 1))
  if tools tap "$page" --scroll --timeout 15; then
    sleep 3
    snap "$page_number-page-$(echo "$page" | tr '[:upper:] ' '[:lower:]-')"
    if [ "$page" = Diagnostics ]; then
      tools tap "Copy report" --scroll --timeout 10 && sleep 1
    fi
    adb shell input keyevent KEYCODE_BACK
    sleep 2
  else
    warn "could not open the '$page' page"
  fi
done
health "after every page"

step "Blink Studio and the app picker"
if tools tap Blink --bottommost --timeout 10; then
  sleep 3
  snap 50-studio
  tools tap "Live preview of the blink" --timeout 8 && sleep 2
fi
if tools tap Apps --bottommost --timeout 10; then
  sleep 2
  if tools tap "Add an app" --timeout 10; then
    sleep 3
    snap 51-app-picker
    adb shell input keyevent KEYCODE_BACK
    sleep 2
  fi
fi
health "after the studio and the app picker"

# Somebody who says no to the camera has to be told so, not shown the same page again. Revoking the permission ends the
# app's process; it is started again, asked to calibrate, and Android's question is answered with "Don't allow".
step "Calibration without the camera"
adb shell pm revoke "$PKG" android.permission.CAMERA
adb logcat -c
timeout 120 adb shell am start -W -n "$PKG/$ACTIVITY" | tail -3
sleep 6
if tools tap More --bottommost --timeout 15 && tools tap Calibration --scroll --timeout 15; then
  sleep 2
  if tools tap Begin --timeout 10; then
    sleep 3
    snap 60-camera-question
    tools tap "t allow" --timeout 10 && sleep 3
    explained=no
    for attempt in 1 2; do
      snap "6$attempt-camera-refused"
      if tools has "$OUT/6$attempt-camera-refused.xml" "Camera access was not allowed"; then explained=yes; break; fi
      adb shell input keyevent KEYCODE_BACK # Android may have opened the app's own settings page in front of it
      sleep 2
    done
    if [ "$explained" = yes ]; then
      echo "the page explains that the camera was refused"
    else
      warn "the calibration page did not explain the refused camera"
    fi
  else
    warn "could not press Begin without the camera permission"
  fi
else
  warn "could not reach the calibration page without the camera permission"
fi
adb shell pm grant "$PKG" android.permission.CAMERA
health "after the camera was refused"

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
prefs_write "$OUT/seed-eyeblink.xml"
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
  if prefs_read | tools ledger --quiet; then shown=yes; break; fi
done
prefs_read | tools ledger || true
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

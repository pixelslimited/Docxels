#!/usr/bin/env bash
# Runs inside the emulator: installs the build, walks through the app, and saves screenshots + UI dumps to qa-out/.
set -u
APK="$1"; OUT="${2:-qa-out}"
mkdir -p "$OUT"
exec > >(tee -a "$OUT/run-log.txt") 2>&1
echo "start $(date)"; adb devices; adb shell getprop ro.product.cpu.abi
UI="python3 $(dirname "$0")/ui.py"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; $UI texts > "$OUT/$1.txt" 2>/dev/null || true; }
step() { echo "== $*" | tee -a "$OUT/log.txt"; }
tap() { $UI tap "$1" | tee -a "$OUT/log.txt"; sleep "${2:-2}"; }

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$APK" | tee -a "$OUT/log.txt"
adb shell pm grant ng.pixelslimited.docxels android.permission.CAMERA 2>/dev/null || true
adb shell pm grant ng.pixelslimited.docxels android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
adb shell monkey -p ng.pixelslimited.docxels -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
step home; shot 01_home

for label in Library Write Tools Settings Home; do
  step "tab $label"
  if tap "^$label\$" 3; then shot "02_tab_$label"; fi
done

adb logcat -d -s AndroidRuntime:E DocScan:E > "$OUT/crashes.txt" 2>&1 || true
adb logcat -d > "$OUT/logcat-full.txt" 2>&1 || true

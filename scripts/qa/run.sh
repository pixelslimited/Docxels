#!/usr/bin/env bash
# Runs inside the emulator: installs the build, walks through the app, and saves screenshots + UI dumps to qa-out/.
set -u
APK="$1"; OUT="${2:-qa-out}"
mkdir -p "$OUT"
exec > >(tee -a "$OUT/run-log.txt") 2>&1
echo "start $(date)"; adb devices; adb shell getprop ro.product.cpu.abi
UI="python3 $(dirname "$0")/ui.py"
shot() { dismiss 2>/dev/null; adb exec-out screencap -p > "$OUT/$1.png"; $UI texts > "$OUT/$1.txt" 2>/dev/null || true; }
step() { echo "== $*" | tee -a "$OUT/log.txt"; }
tap() { $UI tap "$1" | tee -a "$OUT/log.txt"; local rc=${PIPESTATUS[0]}; sleep "${2:-2}"; return $rc; }

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$APK" | tee -a "$OUT/log.txt"
adb shell pm grant ng.pixelslimited.docxels android.permission.CAMERA 2>/dev/null || true
adb shell pm grant ng.pixelslimited.docxels android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
adb shell monkey -p ng.pixelslimited.docxels -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
# Let the emulator settle, and dismiss "System UI isn't responding" if the slow image shows it.
for i in $(seq 1 40); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done
sleep 30
dismiss() { for i in 1 2 3; do $UI tap "^Wait$" >/dev/null 2>&1 && sleep 2 || break; done; }
dismiss
step home; shot 01_home

for label in Documents Tools Settings Home; do
  step "tab $label"
  if tap "^$label\$" 3; then shot "02_tab_$label"; fi
done

adb logcat -d -s AndroidRuntime:E DocScan:E > "$OUT/crashes.txt" 2>&1 || true
adb logcat -d > "$OUT/logcat-full.txt" 2>&1 || true

# Typing test: does a long paragraph wrap inside the page?
step "new document"
for p in "^New document$" "^Write$" "^New$" "Blank"; do tap "$p" 4 && break; done
dismiss
shot 03_new_document
adb shell input tap 540 900; sleep 2
LONG="The%squick%sbrown%sfox%sjumps%sover%sthe%slazy%sdog%sand%skeeps%srunning%sacross%sthe%sfield%suntil%sthe%ssun%sgoes%sdown%sbehind%sthe%shills%sand%sthe%swhole%sparagraph%smust%swrap%sinside%sthe%spage."
adb shell input text "$LONG$LONG$LONG"; sleep 3
dismiss
shot 04_typed_paragraph
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes2.txt" 2>&1 || true

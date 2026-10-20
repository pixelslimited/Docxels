#!/usr/bin/env bash
# Runs inside the emulator: installs the build, walks through the app, and saves screenshots + UI dumps to qa-out/.
set -u
APK="$1"; OUT="${2:-qa-out}"
mkdir -p "$OUT"
exec > >(tee -a "$OUT/run-log.txt") 2>&1
echo "start $(date)"; adb devices; adb shell getprop ro.product.cpu.abi
UI="python3 $(dirname "$0")/ui.py"
shot() { dismiss 2>/dev/null; adb exec-out screencap -p > "$OUT/$1.png"; $UI texts > "$OUT/$1.txt" 2>/dev/null || true; adb shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1 && adb pull /sdcard/d.xml "$OUT/$1.xml" >/dev/null 2>&1 || true; }
step() { echo "== $*" | tee -a "$OUT/log.txt"; }
tap() { $UI tap "$1" | tee -a "$OUT/log.txt"; local rc=${PIPESTATUS[0]}; sleep "${2:-2}"; return $rc; }

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure immersive_mode_confirmations confirmed
adb install -r "$APK" | tee -a "$OUT/log.txt"
adb shell pm grant ng.pixelslimited.docxels android.permission.CAMERA 2>/dev/null || true
adb shell pm grant ng.pixelslimited.docxels android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
adb shell monkey -p ng.pixelslimited.docxels -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
# Let the emulator settle, and dismiss "System UI isn't responding" if the slow image shows it.
for i in $(seq 1 40); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done
sleep 30
dismiss() { for i in 1 2 3 4 5 6; do { $UI tap "^Wait$" || $UI tap "^Got it$" || $UI tap "^Skip$" || $UI tap "^Done$" || $UI tap "^Start$"; } >/dev/null 2>&1 && sleep 2 || break; done; }
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
for p in "^New doc$" "^New document$"; do tap "$p" 4 && break; done
dismiss
shot 03_new_document
adb shell input tap 540 520; sleep 2
LONG="The%squick%sbrown%sfox%sjumps%sover%sthe%slazy%sdog%sand%skeeps%srunning%sacross%sthe%sfield%suntil%sthe%ssun%sgoes%sdown%sbehind%sthe%shills%sand%sthe%swhole%sparagraph%smust%swrap%sinside%sthe%spage."
adb shell input text "$LONG$LONG$LONG"; sleep 3
dismiss
shot 04_typed_paragraph
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes2.txt" 2>&1 || true

# PDF to Word on a sample letter: letterhead on odd pages, artwork on every page, a rule and spaced paragraphs.
step "pdf to word"
adb shell am force-stop ng.pixelslimited.docxels; sleep 2
adb push "$(dirname "$0")/sample-letter.pdf" /sdcard/Download/sample-letter.pdf >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sample-letter.pdf >/dev/null 2>&1
adb shell monkey -p ng.pixelslimited.docxels -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 8
dismiss
tap "^Home$" 2
if tap "PDF to Word" 4; then
  shot 05_picker
  tap "sample-letter" 3 || { tap "Show roots" 2; tap "^Downloads$" 3; shot 05b_downloads; tap "sample-letter" 3; }
  for i in 1 2 3 4 5 6; do shot "06_progress_$i"; sleep 2; done
  sleep 15
  shot 07_converted
  adb shell input swipe 540 1800 540 600 300; sleep 2; shot 08_converted_scrolled
else
  echo "PDF to Word tile not found"
fi
# Justify the first long paragraph of the converted letter through the ribbon, then look at it.
step justify
adb shell input swipe 540 600 540 1800 300; sleep 2
adb shell input tap 540 700; sleep 2
adb shell input keycombination 113 29; sleep 2   # Ctrl+A: justify the whole letter
adb shell input keyevent 4; sleep 2
adb shell input tap 980 2170; sleep 2; shot 09a_ribbon
tap "^Paragraph$" 2
tap "^Justify$" 2; shot 09b_justify_ribbon
tap "^Close" 2 || adb shell input tap 1010 1450
sleep 2; shot 09c_justified
# Export to PDF so the exported pages can be compared with the editor (the file is read back from the app's cache).
step "export pdf"
adb shell input tap 1006 212; sleep 2; shot 10a_menu
tap "Share as PDF" 6 || true
shot 10b_share
adb shell input keyevent 4; sleep 1
adb root >/dev/null 2>&1; sleep 3
adb pull /data/data/ng.pixelslimited.docxels/cache/exports/sample-letter.pdf "$OUT/export.pdf" 2>&1 | tee -a "$OUT/log.txt" || true
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes3.txt" 2>&1 || true
adb logcat -d -s System.out:I > "$OUT/measure-log.txt" 2>&1 || true

# ---- New features: invoice maker, text box, tracked changes ----
step "restart for new features"
adb shell am force-stop ng.pixelslimited.docxels; sleep 2
adb push "$(dirname "$0")/tracked-sample.docx" /sdcard/Download/tracked-sample.docx >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/tracked-sample.docx >/dev/null 2>&1
adb logcat -c
adb shell monkey -p ng.pixelslimited.docxels -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 8
dismiss
tap "^Home$" 2
shot 20_home_again

step "invoice maker"
if tap "Invoice" 3; then shot 21_invoice_maker; adb shell input keyevent 4; sleep 2; else echo "Invoice tile not found"; fi
dismiss

step "tracked changes"
if tap "^Import$" 3; then
  shot 30_import_sheet
  if tap "Word file" 4; then
    shot 31_word_picker
    tap "tracked-sample" 4 || { tap "Show roots" 2; tap "^Downloads$" 3; tap "tracked-sample" 4; }
    shot 32_tracked_prompt
    tap "Show changes" 5; shot 33_tracked_markup
    adb shell input keyevent 4; sleep 2
  fi
fi
dismiss

step "text box"
tap "^Home$" 2
for p in "^New doc$" "^New document$"; do tap "$p" 4 && break; done
dismiss
adb shell input tap 540 520; sleep 1
tap "Show tools" 2; shot 40_logo_menu
tap "^Insert$" 3; shot 41_insert_groups
tap "Illustrations" 2; shot 42_illustrations
tap "Shape" 2; shot 43_shapes_menu
tap "Text box" 3; shot 44_textbox_inserted
adb shell input tap 540 520; sleep 2; shot 45_textbox_dialog
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes4.txt" 2>&1 || true
adb logcat -d > "$OUT/logcat-full2.txt" 2>&1 || true

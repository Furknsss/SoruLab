#!/usr/bin/env bash
set -euo pipefail

mkdir -p out/twin-visual
capture_failure() {
  adb logcat -d -t 300 > out/twin-visual/failure-logcat.txt 2>&1 || true
  adb shell uiautomator dump /sdcard/failure-view.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/failure-view.xml > out/twin-visual/failure-view.xml 2>/dev/null || true
  adb exec-out screencap -p > out/twin-visual/failure-screen.png 2>/dev/null || true
}
trap capture_failure ERR

adb shell wm size 720x1280
adb shell wm density 240
adb install -r app/build/outputs/apk/debug/app-debug.apk
mkdir -p out/twin-visual
# A screenshot that contains a black launch screen or System UI ANR
# is NOT proof that Compose drew the checked twin. Assert UI semantics.
READY=0
for attempt in 1 2 3; do
  adb shell am start -W -n com.aistudio.sorulab.ykslab/com.example.ui.screens.TwinDiagramPreviewActivity || true
  sleep 16
  adb shell uiautomator dump /sdcard/twin-view.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/twin-view.xml > out/twin-visual/screen.xml 2>/dev/null || true
  if grep -q "SoruLab" out/twin-visual/screen.xml && grep -q "YENİ İKİZ" out/twin-visual/screen.xml; then
    READY=1
    break
  fi
  adb shell input keyevent 4 || true
done
test "$READY" -eq 1
adb exec-out screencap -p > out/twin-visual/native-triangle-twin.png
pip install -q pillow
python - <<'PY'
from PIL import Image
path = "out/twin-visual/native-triangle-twin.png"
with Image.open(path) as original:
    image = original.convert("RGB")
    image.thumbnail((160, 240))
    bright = sum(max(p) >= 215 and min(p) >= 185 for p in image.getdata())
    coverage = bright / (image.width * image.height)
    print("Bright canvas fraction", coverage)
    assert coverage > 0.40, "Black screen / system ANR is NOT visual QA"
PY
# Verify generated scientific illustration uses its own image surface
# and leaves the native question/options visible.
adb shell am start -W -n com.aistudio.sorulab.ykslab/com.example.ui.screens.TwinIllustrationPreviewActivity
sleep 14
adb shell uiautomator dump /sdcard/twin-science.xml >/dev/null 2>&1
adb shell cat /sdcard/twin-science.xml > out/twin-visual/science-view.xml
grep -q "Biyoloji İkiz Görsel Testi" out/twin-visual/science-view.xml
grep -q "Stoma" out/twin-visual/science-view.xml
adb exec-out screencap -p > out/twin-visual/native-science-twin.png
python - <<'PY'
from PIL import Image
with Image.open("out/twin-visual/native-science-twin.png") as p:
    image = p.convert("RGB")
    image.thumbnail((160, 240))
    bright = sum(min(rgb) > 185 for rgb in image.getdata())
    coverage = bright / (image.width * image.height)
    assert coverage > 0.40, "Science preview missing or system ANR"
PY


#!/usr/bin/env bash
# Collect optional ORIGINAL synthetic Android UI snapshots.
# Android UI instrumentation result is determined by connectedDebugAndroidTest;
# missing screenshots must be reported separately, not disguised as a test failure.
set -euo pipefail
OUT="app/build/qa-device-screenshots"
mkdir -p "$OUT"
echo "Trying to locate synthetic device UI screenshots..."
adb shell 'find /sdcard/Android/data -type f -name denemeler-phone.png 2>/dev/null' \
  | tr -d '\r' > "$OUT/paths.txt" || true
for pkg in com.aistudio.sorulab.ykslab com.aistudio.sorulab.ykslab.test; do
  path="/sdcard/Android/data/$pkg/files/qa-screens"
  if adb shell "test -f '$path/denemeler-phone.png'" >/dev/null 2>&1; then
    printf '%s\n' "$path/denemeler-phone.png" >> "$OUT/paths.txt"
  fi
done
screen_path="$(grep -m1 -v '^[[:space:]]*$' "$OUT/paths.txt" || true)"
if [[ -z "$screen_path" ]]; then
  echo "::warning::Android instrumentation passed but optional screenshot files could not be located."
  adb shell 'ls -la /sdcard/Android/data/com.aistudio.sorulab.ykslab/files/qa-screens 2>/dev/null' || true
  exit 0
fi
screen_dir="${screen_path%/*}"
if ! adb pull "$screen_dir/." "$OUT/"; then
  echo "::warning::Android instrumentation passed; screenshot pull failed (Android storage policy)."
  exit 0
fi
if [[ -s "$OUT/denemeler-phone.png" && -s "$OUT/solving-board-phone.png" ]]; then
  echo "Both synthetic Compose screenshots archived."
else
  echo "::warning::Only some synthetic UI screenshots were collected."
fi

#!/usr/bin/env bash
set -euo pipefail
mkdir -p out/install-smoke
BASELINE="$(find baseline -type f -name '*.apk' -print -quit)"
NEW="$(find update -type f -name '*.apk' -print -quit)"
test -s "$BASELINE" && test -s "$NEW"
echo "baseline=$BASELINE update=$NEW"
echo "baseline sha256=$(sha256sum "$BASELINE" | cut -d' ' -f1)"
echo "update sha256=$(sha256sum "$NEW" | cut -d' ' -f1)"
adb wait-for-device
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
echo "[1] INSTALL v1.4.7"
if ! adb install -r "$BASELINE" > out/install-smoke/install-v147.txt 2>&1; then
  cat out/install-smoke/install-v147.txt
  adb logcat -d -v threadtime -t 600 > out/install-smoke/error-log.txt || true
  exit 1
fi
cat out/install-smoke/install-v147.txt
adb shell dumpsys package com.aistudio.sorulab.ykslab | grep -E 'versionCode|versionName' | head -4 | tee out/install-smoke/v147.txt
echo "[2] INSTALL v1.4.8 OVER INSTALLED v1.4.7 (NO UNINSTALL)"
if ! adb install -r "$NEW" > out/install-smoke/install-v148.txt 2>&1; then
  cat out/install-smoke/install-v148.txt
  adb logcat -d -v threadtime -t 1200 > out/install-smoke/error-log.txt || true
  exit 1
fi
cat out/install-smoke/install-v148.txt
adb shell dumpsys package com.aistudio.sorulab.ykslab | grep -E 'versionCode|versionName' | head -4 | tee out/install-smoke/v148.txt
adb shell pm path com.aistudio.sorulab.ykslab | tee out/install-smoke/installed-package.txt
echo "[3] PASSED: in-place update installed"

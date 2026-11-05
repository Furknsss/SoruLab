#!/usr/bin/env bash
# Install and launch the actual signed distributable, not a test ComponentActivity.
set -euo pipefail
apk_path="${1:?Pass the signed APK path}"
report_dir="${2:-app/build/signed-apk-install}"
package_name="com.aistudio.sorulab.ykslab"
component_name="$package_name/com.example.MainActivity"
mkdir -p "$report_dir"
python3 scripts/check_apk_integrity.py "$apk_path"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk_path" \
  > "$report_dir/signature.txt"
adb install --no-streaming -r "$apk_path" | tee "$report_dir/install.txt"
adb logcat -c
adb shell am start -W -n "$component_name" | tee "$report_dir/launch.txt"
if ! rg -q 'Status: ok' "$report_dir/launch.txt"; then
  echo 'Signed APK did not launch successfully' >&2
  exit 1
fi
# Let asynchronous startup initialize; each sample also proves the process survived.
for sample in {1..10}; do
  sleep 1
  if ! adb shell pidof "$package_name" > "$report_dir/pid.txt"; then
    adb logcat -b crash -d > "$report_dir/crash.txt"
    echo 'Signed APK process exited during startup' >&2
    exit 1
  fi
done
adb logcat -b crash -d > "$report_dir/crash.txt"
if rg -q "Process: $package_name" "$report_dir/crash.txt"; then
  echo 'Signed APK crashed during startup' >&2
  exit 1
fi
adb exec-out screencap -p > "$report_dir/release-launch.png"
adb shell uiautomator dump /sdcard/sorulab-launch.xml
adb pull /sdcard/sorulab-launch.xml "$report_dir/release-launch.xml"
if ! rg -q "package=\"$package_name\"" "$report_dir/release-launch.xml"; then
  echo 'App UI is not visible after launch' >&2
  exit 1
fi
# Exercise the update path without uninstalling or clearing application data.
adb install --no-streaming -r "$apk_path" | tee "$report_dir/reinstall.txt"
echo 'SIGNED APK: signature, installation, launch, visible UI and reinstall passed'

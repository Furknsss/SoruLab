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
if ! grep -Fq 'Status: ok' "$report_dir/launch.txt"; then
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
if grep -Fq "Process: $package_name" "$report_dir/crash.txt"; then
  echo 'Signed APK crashed during startup' >&2
  exit 1
fi
# The emulator's Pixel Launcher can show a SYSTEM "isn't responding"
# dialog over an otherwise healthy SoruLab activity. Recover that OS-owned
# dialog, then recheck actual SoruLab UI instead of failing a good APK.
app_ui_visible=false
for attempt in 1 2 3; do
  adb exec-out screencap -p > "$report_dir/release-launch.png"
  adb shell uiautomator dump /sdcard/sorulab-launch.xml
  adb pull /sdcard/sorulab-launch.xml "$report_dir/release-launch.xml"
  if grep -Fq "package=\"$package_name\"" "$report_dir/release-launch.xml"; then
    app_ui_visible=true
    break
  fi
  if grep -Eq "Launcher isn.t responding|System UI isn.t responding" "$report_dir/release-launch.xml"; then
    # Tap the system-owned ANR dialog's "Wait" button at its actual
    # accessibility bounds; never tap an arbitrary position in SoruLab.
    wait_xy="$(python3 - "$report_dir/release-launch.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    for node in root.iter('node'):
        if node.attrib.get('resource-id') == 'android:id/aerr_wait':
            coords = [int(x) for x in re.findall(r'\d+', node.attrib.get('bounds', ''))]
            if len(coords) == 4:
                print((coords[0] + coords[2]) // 2, (coords[1] + coords[3]) // 2)
                break
except (ValueError, ET.ParseError, OSError):
    pass
PY
)"
    if [[ -n "$wait_xy" ]]; then
      adb shell input tap $wait_xy
      echo 'Dismissed unrelated emulator launcher ANR using Wait'
    fi
  fi
  adb shell am start -W -n "$component_name" >/dev/null
  sleep 3
done
if [[ "$app_ui_visible" != true ]]; then
  echo 'SoruLab UI is not visible after emulator recovery' >&2
  exit 1
fi
# Exercise the update path without uninstalling or clearing application data.
adb install --no-streaming -r "$apk_path" | tee "$report_dir/reinstall.txt"
echo 'SIGNED APK: signature, installation, launch, visible UI and reinstall passed'

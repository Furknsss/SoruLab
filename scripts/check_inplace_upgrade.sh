#!/usr/bin/env bash
# Verify that an APK can replace the previously distributed signed SoruLab APK
# without uninstalling it. Optional --install runs the actual adb upgrade check.
set -euo pipefail

old_apk="${1:?Usage: check_inplace_upgrade.sh OLD_APK NEW_APK [--install]}"
new_apk="${2:?Usage: check_inplace_upgrade.sh OLD_APK NEW_APK [--install]}"
test -s "$old_apk" && test -s "$new_apk"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk_root" || { echo "Missing ANDROID_HOME/ANDROID_SDK_ROOT" >&2; exit 1; }
build_tools="$sdk_root/build-tools/36.0.0"
aapt="$build_tools/aapt"
apksigner="$build_tools/apksigner"
test -x "$aapt" && test -x "$apksigner"

metadata() { "$aapt" dump badging "$1" | sed -n '1p'; }
package_name() { sed -nE "s/^package: name='([^']+)'.*/\\1/p" <<<"$1"; }
version_code() { sed -nE "s/.*versionCode='([0-9]+)'.*/\\1/p" <<<"$1"; }
cert_digest() {
  "$apksigner" verify --print-certs "$1" \
    | sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+).*/\\1/p'
}

old_info="$(metadata "$old_apk")"
new_info="$(metadata "$new_apk")"
old_pkg="$(package_name "$old_info")"
new_pkg="$(package_name "$new_info")"
old_code="$(version_code "$old_info")"
new_code="$(version_code "$new_info")"
old_cert="$(cert_digest "$old_apk")"
new_cert="$(cert_digest "$new_apk")"

test "$old_pkg" = "com.aistudio.sorulab.ykslab" && test "$old_pkg" = "$new_pkg" \
  || { echo "Package ID changed: $old_pkg -> $new_pkg" >&2; exit 1; }
[[ "$old_code" =~ ^[0-9]+$ && "$new_code" =~ ^[0-9]+$ ]] \
  || { echo "Could not read versionCode" >&2; exit 1; }
(( new_code > old_code )) \
  || { echo "Version downgrade or same code: $old_code -> $new_code" >&2; exit 1; }
test -n "$old_cert" && test "$old_cert" = "$new_cert" \
  || { echo "Signing certificate changed: app data-preserving update is impossible" >&2; exit 1; }

echo "Upgrade metadata OK: $old_pkg, versionCode $old_code -> $new_code; certificate unchanged."

if [[ "${3:-}" == "--install" ]]; then
  adb install -r "$old_apk"
  adb shell pm path "$old_pkg" | grep -q 'package:'
  adb install -r "$new_apk"
  adb shell pm path "$new_pkg" | grep -q 'package:'
  installed="$(adb shell dumpsys package "$new_pkg" | sed -nE 's/.*versionCode=([0-9]+).*/\\1/p' | sed -n '1p' | tr -d '\r')"
  test "$installed" = "$new_code" \
    || { echo "Expected versionCode $new_code on device, got $installed" >&2; exit 1; }
  echo "Real adb in-place upgrade passed: $old_code -> $new_code (no uninstall)."
fi

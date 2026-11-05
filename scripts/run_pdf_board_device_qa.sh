#!/usr/bin/env bash
# Original synthetic fixtures only; no user books or account data.
set -euo pipefail

archive_case() {
  local case_label="$1"
  local case_dir="app/build/qa-viewport-reports/$case_label"
  mkdir -p "$case_dir"
  for source in app/build/outputs/androidTest-results/connected app/build/reports/androidTests/connected; do
    if [[ -d "$source" ]]; then
      cp -a "$source" "$case_dir/$(basename "$(dirname "$source")")"
    fi
  done
  bash scripts/collect_pdf_qa_screenshots.sh
}

run_case() {
  local case_label="$1"
  local args=( :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache --max-workers=2
    "-Pandroid.testInstrumentationRunnerArguments.sorulab.viewportCase=$case_label" )
  if [[ "$case_label" != phone ]]; then
    args+=( '-Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.SoruLabStylusViewportDeviceTest,com.example.ui.SoruLabDeviceUiSmokeTest' )
  fi
  if bash ./gradlew "${args[@]}"; then
    archive_case "$case_label"
  else
    archive_case "$case_label"
    return 1
  fi
}

trap 'adb shell wm size reset >/dev/null 2>&1 || true; adb shell wm density reset >/dev/null 2>&1 || true; adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true' EXIT
run_case phone
adb shell wm size 1600x2560
adb shell wm density 240
run_case tablet-portrait
adb shell wm size 2560x1600
run_case tablet-landscape
adb shell wm size reset
adb shell wm density reset
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
bash ./gradlew :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache --max-workers=2 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.util.PdfOfflineImportDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.sorulab.requireOffline=true \
  -Pandroid.testInstrumentationRunnerArguments.sorulab.viewportCase=offline
archive_case offline

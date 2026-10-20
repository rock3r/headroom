#!/usr/bin/env bash
# Compares two APKs as a user's first session after installing them, once ART has compiled what
# the APK's baseline profiles list (Play does that at install; for a sideload, ProfileInstaller
# queues it for the phone's next background compilation). For each APK: install it, clear every
# profile and compiled code, launch once so ProfileInstaller writes the APK's profile, compile
# with speed-profile, then measure cold starts and the given scenarios.
#
# Usage: scripts/perf/profile-ab.sh <serial> <runs> <label-a> <apk-a> <label-b> <apk-b> -- <scenario>...
set -euo pipefail
serial="$1"; runs="$2"; shift 2
pkg="${PKG:-dev.sebastiano.headroom.benchmark}"
here="$(dirname "$0")"
adb=(adb -s "$serial")
pairs=()
while [ "$1" != "--" ]; do pairs+=("$1" "$2"); shift 2; done
shift
for ((i = 0; i < ${#pairs[@]}; i += 2)); do
  label="${pairs[i]}"; apk="${pairs[i + 1]}"
  echo "### $label ($apk)"
  "${adb[@]}" install -r "$apk" | tail -1
  "${adb[@]}" shell cmd package compile --reset "$pkg" | tail -1
  "${adb[@]}" logcat -c
  "${adb[@]}" shell am start -W -n "$pkg/dev.sebastiano.headroom.MainActivity" >/dev/null
  sleep 12
  "${adb[@]}" logcat -d -s ProfileInstaller | tail -2
  "${adb[@]}" shell cmd package compile -m speed-profile -f "$pkg" | tail -1
  for _ in 1 2 3 4 5; do
    "${adb[@]}" shell am force-stop "$pkg"
    sleep 1
    "${adb[@]}" shell am start -W -n "$pkg/dev.sebastiano.headroom.MainActivity" | grep TotalTime
    sleep 2
  done
  sleep 2
  "$here/suite.sh" "$serial" "$label" "$runs" "$@" > "build/perf/$label.log" 2>&1
done

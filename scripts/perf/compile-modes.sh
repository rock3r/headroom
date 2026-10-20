#!/usr/bin/env bash
# Measures how screen openings depend on ART compilation: runs the given scenarios with the app
# compiled as verify (interpreted and JIT, like a fresh sideload), speed-profile (AOT for what the
# profile lists: the libraries' baseline profiles plus what JIT recorded), and speed (everything
# AOT, the upper bound of what a baseline profile can give). Leaves the app at speed-profile.
#
# Usage: scripts/perf/compile-modes.sh <serial> <label> <runs> <scenario>...
set -euo pipefail
serial="$1"; label="$2"; runs="$3"; shift 3
pkg="${PKG:-dev.sebastiano.headroom.benchmark}"
here="$(dirname "$0")"
for mode in verify speed-profile speed; do
  echo "### $mode"
  adb -s "$serial" shell cmd package compile -m "$mode" -f "$pkg" | tail -1
  # Cold starts: the first `am start -W` after a force-stop, five times.
  for _ in 1 2 3 4 5; do
    adb -s "$serial" shell am force-stop "$pkg"
    sleep 1
    adb -s "$serial" shell am start -W -n "$pkg/dev.sebastiano.headroom.MainActivity" | grep -E "TotalTime"
    sleep 2
  done
  sleep 3
  "$here/suite.sh" "$serial" "$label-$mode" "$runs" "$@" > "build/perf/$label-$mode.log" 2>&1
done
adb -s "$serial" shell cmd package compile -m speed-profile -f "$pkg" | tail -1

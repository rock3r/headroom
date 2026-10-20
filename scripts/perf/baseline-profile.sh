#!/usr/bin/env bash
# Regenerates app/src/main/baseline-prof.txt, the app's baseline profile: the classes and methods
# that ART compiles ahead of time at install, so screens open fast from the first launch instead
# of after the phone's background compilation has run (see docs/perf/WORK-LEDGER.md, F3).
#
# 1. Build the benchmark build without R8, so the profile has the source names:
#      ./gradlew :app:assembleBenchmark -Pheadroom.benchmark.minify=false
# 2. Install it on a phone (it has its own application ID and starts with demo data), open it on the
#    overview, and run this script. It clears the app's profile, drives every scenario of
#    scripts/perf/suite.sh plus cold starts, and writes the profile ART recorded.
#
# Usage: scripts/perf/baseline-profile.sh <adb serial>
set -euo pipefail
serial="${1:?Pass the adb serial}"
pkg="dev.sebastiano.headroom.benchmark"
here="$(dirname "$0")"
adb=(adb -s "$serial")
out="app/src/main/baseline-prof.txt"

"${adb[@]}" shell cmd package compile --reset "$pkg" >/dev/null
for _ in 1 2 3; do
  "${adb[@]}" shell am force-stop "$pkg"
  "${adb[@]}" shell am start -W -n "$pkg/dev.sebastiano.headroom.MainActivity" >/dev/null
  sleep 4
done
"$here/suite.sh" "$serial" baseline-profile-run 2 >/dev/null 2>&1
# dump-profiles makes the app flush what ART recorded, then writes it as text.
"${adb[@]}" shell cmd package dump-profiles --dump-classes-and-methods "$pkg"
dump="/data/misc/profman/$pkg-primary.prof.txt"
"${adb[@]}" pull "$dump" build/perf/primary.prof.txt >/dev/null
# Keep Headroom's own code and the libraries it calls; drop the platform's.
grep -vE '^(H|S|P)*L(android|java|javax|kotlin/jvm/internal|sun|dalvik|libcore|com/android/internal)/' \
  build/perf/primary.prof.txt | sort -u > "$out"
echo "Wrote $(wc -l < "$out") rules to $out"

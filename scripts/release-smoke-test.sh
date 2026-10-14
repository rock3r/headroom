#!/usr/bin/env bash
# Smoke-tests a release APK on a connected device or emulator, before the release is published.
#
#   scripts/release-smoke-test.sh path/to/headroom.apk
#
# It installs the APK exactly as it will ship (minified with R8, signed), so no test APK and no
# extra R8 keep rules are needed. A fresh install has no accounts and shows demo data, so no real
# account or token is involved. The script drives the app through adb and UI Automator: it opens
# the overview, syncs, opens an account's detail, then the Resets, Stats and Settings screens.
# It fails if a screen does not show, if a sync does not change the numbers, or if the app crashes.
#
# Set ANDROID_SERIAL to pick a device when more than one is connected. The app is uninstalled
# first, so do not point this at a phone with real accounts in Headroom.
set -euo pipefail

apk="${1:?Usage: $0 path/to/app.apk}"
package="dev.sebastiano.headroom"
timeout_seconds=20
artifacts="${SMOKE_TEST_ARTIFACTS:-build/release-smoke-test}"
mkdir -p "$artifacts"

dump="$artifacts/window.xml"

log() { echo "smoke-test: $*"; }

fail() {
  # stderr: fail also runs inside $(...), where stdout is captured.
  echo "::error::Release smoke test failed: $*" >&2
  adb exec-out screencap -p > "$artifacts/failure.png" || true
  adb logcat -d > "$artifacts/logcat.txt" || true
  cp "$dump" "$artifacts/failure-window.xml" 2>/dev/null || true
  exit 1
}

# Fails when the app process died or the crash buffer names the app.
check_alive() {
  if adb logcat -d -b crash | grep -q "Process: $package"; then
    fail "the app crashed (see logcat.txt)"
  fi
  if ! adb shell pidof "$package" > /dev/null; then
    fail "the app is not running"
  fi
}

# Writes the current window hierarchy to $dump, one node per line.
dump_window() {
  adb exec-out uiautomator dump /dev/tty 2> /dev/null \
    | sed 's/UI hierchary dumped to.*//' \
    | grep -o '<node [^>]*>' > "$dump" || true
}

# Prints the first node whose text or content description is exactly $1.
find_node() {
  local label="$1"
  grep -F -e "text=\"$label\"" -e "content-desc=\"$label\"" "$dump" | head -n 1 || true
}

# Waits until a node with the label $1 shows, and prints it.
wait_for() {
  local label="$1" node=""
  local deadline=$((SECONDS + timeout_seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    dump_window
    node=$(find_node "$label")
    if [ -n "$node" ]; then
      echo "$node"
      return 0
    fi
    sleep 1
  done
  check_alive
  fail "\"$label\" did not show within ${timeout_seconds}s"
}

# Waits until no node with the label $1 shows.
wait_gone() {
  local label="$1"
  local deadline=$((SECONDS + timeout_seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    dump_window
    [ -z "$(find_node "$label")" ] && return 0
    sleep 1
  done
  check_alive
  fail "\"$label\" still shows after ${timeout_seconds}s"
}

# Taps the centre of the node with the label $1.
tap() {
  local label="$1" node bounds
  node=$(wait_for "$label")
  bounds=$(echo "$node" | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p')
  read -r left top right bottom <<< "$bounds"
  adb shell input tap $(((left + right) / 2)) $(((top + bottom) / 2))
}

# Prints the text of the node right after the node with the label $1: on the overview, the
# percentage that follows an account's plan.
text_after() {
  dump_window
  grep -F -A 1 "text=\"$1\"" "$dump" | tail -n 1 | sed -nE 's/.* text="([^"]*)".*/\1/p' || true
}

step() {
  log "$1"
  adb shell screencap -p "/sdcard/smoke.png"
  adb pull -q "/sdcard/smoke.png" "$artifacts/$2.png" > /dev/null 2>&1 || true
}

log "installing $apk"
adb uninstall "$package" > /dev/null 2>&1 || true
adb install "$apk"
adb logcat -c
adb logcat -b crash -c

log "starting the app"
adb shell am start -W -n "$package/.MainActivity" > /dev/null

# The overview, in demo mode.
wait_for "Demo data" > /dev/null
wait_for "Claude" > /dev/null
check_alive
step "the overview shows demo data" 1-overview

# Sync: the demo numbers move on with each refresh.
before=$(text_after "Max 20x")
[ -n "$before" ] || fail "the overview does not show Claude's usage"
tap "Refresh"
deadline=$((SECONDS + timeout_seconds))
after=$(text_after "Max 20x")
# A blank read means the dump missed the node, not that the number changed.
while [ -z "$after" ] || [ "$after" = "$before" ]; do
  [ "$SECONDS" -lt "$deadline" ] || fail "a sync did not change Claude's usage from $before"
  sleep 1
  after=$(text_after "Max 20x")
done
check_alive
step "a sync moved Claude from $before to $after" 2-synced

# An account's detail, and back.
tap "Claude"
wait_for "Reset alerts" > /dev/null
wait_gone "Demo data"
check_alive
step "the detail for Claude shows" 3-detail
adb shell input keyevent KEYCODE_BACK
wait_for "Demo data" > /dev/null

# The other tabs.
tap "Resets"
wait_for "Upcoming" > /dev/null
check_alive
step "the Resets tab shows" 4-resets

tap "Stats"
wait_for "Example stats from demo data" > /dev/null
check_alive
step "the Stats tab shows" 5-stats

# Settings opens from the overview's top bar.
tap "Overview"
wait_for "Demo data" > /dev/null
tap "Settings"
wait_for "Close settings" > /dev/null
check_alive
step "Settings shows" 6-settings

adb logcat -d > "$artifacts/logcat.txt"
log "passed"

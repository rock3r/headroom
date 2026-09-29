#!/usr/bin/env bash
# Records a Perfetto system trace while scrolling Headroom's Settings screen, then merges in the
# app's own AndroidX Tracing 2 sections. Needs a debug build installed and Settings open.
# Usage: scripts/trace-settings-scroll.sh <adb serial> [output directory]
# See docs/TRACING.md.
set -euo pipefail

serial="${1:?Pass the adb serial of the device}"
out="${2:-build/traces}"
package="dev.sebastiano.headroom"
adb=(adb -s "$serial")
mkdir -p "$out"

"${adb[@]}" push "$(dirname "$0")/perfetto/scroll.pbtxt" /data/local/tmp/headroom-scroll.pbtxt >/dev/null
"${adb[@]}" shell 'cat /data/local/tmp/headroom-scroll.pbtxt | perfetto --txt -c - -o /data/misc/perfetto-traces/headroom-scroll.pftrace --background' >/dev/null
sleep 2

# Fling up and down a few times, fast and slow, in the middle of the screen.
read -r width height < <("${adb[@]}" shell wm size | sed -nE 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/p' | tail -1)
x=$((width / 2)); low=$((height * 4 / 5)); high=$((height / 4))
for _ in 1 2 3; do
  "${adb[@]}" shell input swipe "$x" "$low" "$x" "$high" 150; sleep 1.2
  "${adb[@]}" shell input swipe "$x" "$low" "$x" "$high" 300; sleep 1.2
  "${adb[@]}" shell input swipe "$x" "$high" "$x" "$low" 150; sleep 1.2
  "${adb[@]}" shell input swipe "$x" "$high" "$x" "$low" 300; sleep 1.2
done

# Going home makes the app flush its trace buffer. Then wait for Perfetto to stop.
"${adb[@]}" shell input keyevent KEYCODE_HOME
sleep 8

"${adb[@]}" pull /data/misc/perfetto-traces/headroom-scroll.pftrace "$out/system.pftrace" >/dev/null
latest=$("${adb[@]}" shell run-as "$package" ls no_backup/perfetto_traces | tr -d '\r' | sort | tail -1)
"${adb[@]}" exec-out run-as "$package" cat "no_backup/perfetto_traces/$latest" > "$out/app.pftrace"
cat "$out/system.pftrace" "$out/app.pftrace" > "$out/merged.pftrace"
echo "Merged trace: $out/merged.pftrace"

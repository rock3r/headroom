#!/usr/bin/env bash
# Records a Perfetto system trace while scrolling Headroom's Settings screen, together with the
# app's in-process AndroidX Tracing 2 sections: the app's own and Compose's composable names.
# Needs a debug build installed and Settings open.
# Usage: scripts/trace-settings-scroll.sh <adb serial> [output directory]
# See docs/TRACING.md.
set -euo pipefail

serial="${1:?Pass the adb serial of the device}"
out="${2:-build/traces}"
package="dev.sebastiano.headroom"
receiver="$package/androidx.tracing.profiler.ConnectedProfilerTracingReceiver"
adb=(adb -s "$serial")
mkdir -p "$out"
rm -rf "$out/app"

# The same broadcasts an IDE profiler sends to androidx.tracing's receiver. START clears old
# in-process traces and turns recording on; it stays on, even across restarts, until STOP.
broadcast() { "${adb[@]}" shell am broadcast -a "androidx.tracing.profiler.action.$1" -n "$receiver" | tail -1; }
broadcast START >/dev/null

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

# FLUSH_TRACES_GET_PATH writes the in-process trace out, copies it where the shell can read it,
# and answers with that folder. Then wait for the 20 second system trace to end.
path=$(broadcast FLUSH_TRACES_GET_PATH | sed -nE 's/.*data="([^"]+)".*/\1/p')
if [ -z "$path" ]; then echo "The app did not flush its trace. Is a debug build running?" >&2; exit 1; fi
broadcast STOP >/dev/null
sleep 6

"${adb[@]}" pull /data/misc/perfetto-traces/headroom-scroll.pftrace "$out/system.pftrace" >/dev/null
"${adb[@]}" pull "$path" "$out/app" >/dev/null
cat "$out/system.pftrace" "$out"/app/*.perfetto-trace > "$out/merged.pftrace"
echo "Merged trace: $out/merged.pftrace"

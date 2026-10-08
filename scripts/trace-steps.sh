#!/usr/bin/env bash
# Records a Perfetto system trace while a list of steps drives Headroom, together with the app's
# in-process AndroidX Tracing 2 sections: the app's own and Compose's composable names. Use it to
# trace a screen as it opens. Needs a debug build installed and running.
#
# Usage: scripts/trace-steps.sh <adb serial> <output directory> <step>...
# Steps:
#   tap:X,Y      taps the screen at X,Y pixels (find them with scripts/find-on-screen.sh)
#   wait:S       waits S seconds (decimals are fine)
#   back         presses back
#   fling-up     flings the content up, towards its end; fling-down goes back
#   mark:NAME    writes NAME and the device's boot time to <output directory>/marks.txt
#
# With APP_TRACE=0 the app records nothing in process: Compose's composable sections slow
# composition down, so use it to measure frame times, and the default to find their causes.
#
# Example, opening Settings and closing it twice:
#   scripts/trace-steps.sh 127.0.0.1:15555 build/traces/settings \
#     tap:980,190 wait:2 back wait:2 tap:980,190 wait:2 back wait:2
# See docs/TRACING.md.
set -euo pipefail

serial="${1:?Pass the adb serial of the device}"
out="${2:?Pass the output directory}"
shift 2
package="dev.sebastiano.headroom"
receiver="$package/androidx.tracing.profiler.ConnectedProfilerTracingReceiver"
adb=(adb -s "$serial")
device_trace=/data/misc/perfetto-traces/headroom-steps.pftrace
mkdir -p "$out"
rm -rf "$out/app" "$out/marks.txt"

app_trace="${APP_TRACE:-1}"
broadcast() { "${adb[@]}" shell am broadcast -a "androidx.tracing.profiler.action.$1" -n "$receiver" | tail -1; }
# If a step fails, stop both traces: the system trace has no set length, and app recording would
# stay on across restarts. A run that ends normally stops them below.
pid=""
finished=0
cleanup() {
  [ "$finished" = 1 ] && return
  if [ -n "$pid" ]; then "${adb[@]}" shell "kill -TERM $pid" >/dev/null 2>&1 || true; fi
  if [ "$app_trace" = 1 ]; then broadcast STOP >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
if [ "$app_trace" = 1 ]; then broadcast START >/dev/null; else broadcast STOP >/dev/null; fi

# The scroll configuration without its fixed length: the trace runs until the steps end. Without
# the app's sections it also leaves out every process's track events, which can fill the buffer in
# a long run and push its start out.
config="$out/steps.pbtxt"
if [ "$app_trace" = 1 ]; then
  sed '/^duration_ms:/d' "$(dirname "$0")/perfetto/scroll.pbtxt" > "$config"
else
  sed -e '/^duration_ms:/d' -e '/name: "track_event"/d' "$(dirname "$0")/perfetto/scroll.pbtxt" > "$config"
fi
"${adb[@]}" push "$config" /data/local/tmp/headroom-steps.pbtxt >/dev/null
"${adb[@]}" shell "rm -f $device_trace"
# --background-wait returns once every data source has started, so the first step is recorded.
pid=$("${adb[@]}" shell "cat /data/local/tmp/headroom-steps.pbtxt | perfetto --txt -c - -o $device_trace --background-wait" | tr -d '\r' | tail -1)

read -r width height < <("${adb[@]}" shell wm size | sed -nE 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/p' | tail -1)
x=$((width / 2)); low=$((height * 4 / 5)); high=$((height / 4))
for step in "$@"; do
  case "$step" in
    tap:*) IFS=, read -r tx ty <<< "${step#tap:}"; "${adb[@]}" shell input tap "$tx" "$ty" ;;
    wait:*) sleep "${step#wait:}" ;;
    back) "${adb[@]}" shell input keyevent KEYCODE_BACK ;;
    fling-up) "${adb[@]}" shell input swipe "$x" "$low" "$x" "$high" 150 ;;
    fling-down) "${adb[@]}" shell input swipe "$x" "$high" "$x" "$low" 150 ;;
    mark:*) echo "${step#mark:} $("${adb[@]}" shell cat /proc/uptime | cut -d' ' -f1)" >> "$out/marks.txt" ;;
    *) echo "Unknown step: $step" >&2; exit 2 ;;
  esac
done
sleep 1

# FLUSH_TRACES_GET_PATH writes the in-process trace out, copies it where the shell can read it,
# and answers with that folder. Stopping perfetto with SIGTERM ends the trace and writes it.
if [ "$app_trace" = 1 ]; then
  path=$(broadcast FLUSH_TRACES_GET_PATH | sed -nE 's/.*data="([^"]+)".*/\1/p')
  if [ -z "$path" ]; then echo "The app did not flush its trace. Is a debug build running?" >&2; exit 1; fi
  broadcast STOP >/dev/null
fi
"${adb[@]}" shell "kill -TERM $pid; while kill -0 $pid 2>/dev/null; do sleep 0.2; done"
finished=1

"${adb[@]}" pull "$device_trace" "$out/system.pftrace" >/dev/null
if [ "$app_trace" = 1 ]; then
  "${adb[@]}" pull "$path" "$out/app" >/dev/null
  cat "$out/system.pftrace" "$out"/app/*.perfetto-trace > "$out/merged.pftrace"
else
  cp "$out/system.pftrace" "$out/merged.pftrace"
fi
echo "Merged trace: $out/merged.pftrace"

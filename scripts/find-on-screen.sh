#!/usr/bin/env bash
# Prints the centre, in pixels, of the first element on screen whose content description or text
# is LABEL, as X,Y for a tap step of scripts/trace-steps.sh. Run it before tracing, not during:
# the accessibility dump makes the app do extra work.
# Usage: scripts/find-on-screen.sh <adb serial> <label>
set -euo pipefail

serial="${1:?Pass the adb serial of the device}"
label="${2:?Pass the content description or text to find}"
adb=(adb -s "$serial")
"${adb[@]}" shell uiautomator dump /data/local/tmp/headroom-ui.xml >/dev/null
"${adb[@]}" shell cat /data/local/tmp/headroom-ui.xml |
  tr '>' '\n' |
  grep -F -e "content-desc=\"$label\"" -e "text=\"$label\"" |
  head -1 |
  sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' |
  { read -r l t r b || { echo "Nothing on screen is labelled \"$label\"" >&2; exit 1; }
    echo "$(((l + r) / 2)),$(((t + b) / 2))"; }

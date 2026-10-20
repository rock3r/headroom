#!/usr/bin/env bash
# Lists the labelled nodes on screen with their centres, to find tap coordinates for the perf
# scripts. The accessibility dump adds app work, so never run it while measuring.
# Usage: scripts/perf/nodes.sh <adb serial>
set -euo pipefail
adb -s "${1:?Pass the adb serial}" exec-out uiautomator dump /dev/tty 2>/dev/null \
  | sed 's/UI hierchary dumped to.*//' | grep -o '<node [^>]*>' \
  | perl -ne 'my ($t)=/ text="([^"]*)"/; my ($d)=/content-desc="([^"]*)"/; my ($r)=/resource-id="([^"]*)"/;
      my ($a,$b,$c,$e)=/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/; my $l=$t||$d||$r; next unless $l;
      printf "%5d,%-5d %s\n", ($a+$c)/2, ($b+$e)/2, $l;'

#!/usr/bin/env bash
# Runs every scenario of the performance audit with scripts/perf/ftl.py and writes one JSON line
# per scenario to <out>/summary.jsonl. Start with the benchmark build open on the overview,
# scrolled to the top, demo data, Pixel 7 Pro coordinates (1080x2232 window).
#
# Usage: scripts/perf/suite.sh <serial> <label> [runs] [scenario...]
set -euo pipefail
serial="${1:?serial}"; label="${2:?label}"; runs="${3:-5}"; shift $(( $# >= 3 ? 3 : $# ))
pkg="${PKG:-dev.sebastiano.headroom.benchmark}"
out="build/perf/$label"; mkdir -p "$out"
here="$(dirname "$0")"

# Coordinates on the Pixel 7 Pro.
OVERVIEW=tap:309,2023; RESETS=tap:456,2023; STATS=tap:603,2023; REFRESH=tap:792,2021
SETTINGS=tap:975,200; CARD=tap:283,1322; ALL_RESETS=tap:603,961; ALERT=tap:262,961
SORT=tap:858,1155; ADD_ACCOUNT=tap:875,417

# Every scenario ends where it started. Back on a top-level tab leaves the app, so tabs are left
# with a tap on Overview, never with back.
# bash 3.2 (macOS) has no associative arrays.
scenario() {
  case "$1" in
    overview_fling) echo "wait:1 -- fling-up wait:1.2 fling-down wait:1.2" ;;
    tabs) echo "wait:1 -- $RESETS wait:1.5 $STATS wait:1.5 $OVERVIEW wait:1.5" ;;
    resets_scroll) echo "$RESETS wait:1.5 -- fling-up wait:1.2 fling-down wait:1.2 -- $OVERVIEW wait:1" ;;
    stats_scroll) echo "$STATS wait:1.5 -- fling-up wait:1.2 fling-down wait:1.2 -- $OVERVIEW wait:1" ;;
    detail_open) echo "wait:1 -- $CARD wait:1.5 back wait:1.5" ;;
    detail_scroll) echo "$CARD wait:1.5 -- fling-up wait:1.2 fling-down wait:1.2 -- back wait:1.5" ;;
    detail_period) echo "$CARD wait:1.5 -- tap:502,1649 wait:1 tap:701,1649 wait:1 tap:900,1649 wait:1 tap:241,1649 wait:1 -- back wait:1.5" ;;
    settings_open) echo "wait:1 -- $SETTINGS wait:2.5 back wait:2.5" ;;
    settings_scroll) echo "$SETTINGS wait:2.5 -- fling-up wait:1 fling-up wait:1 fling-down wait:1 fling-down wait:1 -- back wait:2.5" ;;
    theme) echo "$SETTINGS wait:2.5 -- tap:843,1516 wait:2 tap:540,1516 wait:2 -- tap:271,1516 wait:2 back wait:2.5" ;;
    palette) echo "$SETTINGS wait:2.5 -- tap:383,1903 wait:1.5 tap:699,1903 wait:1.5 tap:462,2061 wait:1.5 -- tap:224,1902 wait:1.5 back wait:2.5" ;;
    refresh) echo "wait:1 -- $REFRESH wait:3.5" ;;
    pull_refresh) echo "wait:1 -- swipe:540,500,540,1500,500 wait:3.5" ;;
    sort_menu) echo "wait:1 -- $SORT wait:1.2 back wait:1.2" ;;
    all_resets) echo "wait:1 -- $ALL_RESETS wait:2 $OVERVIEW wait:2" ;;
    alert_toggle) echo "wait:1 -- $ALERT wait:1.2 $ALERT wait:1.2" ;;
    add_account) echo "wait:1 -- $ADD_ACCOUNT wait:2 back wait:2" ;;
    providers) echo "$ADD_ACCOUNT wait:2 -- tap:574,1220 wait:2 back wait:2 -- back wait:2" ;;
    accounts_drag) echo "$ADD_ACCOUNT wait:2 -- swipe:980,521,980,1022,1500 wait:1.5 swipe:980,1022,980,521,1500 wait:1.5 -- back wait:2" ;;
    idle_overview) echo "wait:2 -- wait:5" ;;
    idle_overview_scrolled) echo "swipe:540,1500,540,700,300 wait:2 -- wait:5 -- fling-down wait:1.5" ;;
    idle_resets) echo "$RESETS wait:2 -- wait:5 -- $OVERVIEW wait:1" ;;
    idle_stats) echo "$STATS wait:2 -- wait:5 -- $OVERVIEW wait:1" ;;
    idle_detail) echo "$CARD wait:2 -- wait:5 -- back wait:1.5" ;;
    idle_settings) echo "$SETTINGS wait:3 -- wait:5 -- back wait:2.5" ;;
    *) echo "Unknown scenario: $1" >&2; exit 2 ;;
  esac
}

order=(idle_overview idle_resets idle_stats idle_detail idle_settings overview_fling tabs resets_scroll stats_scroll detail_open detail_scroll detail_period
  settings_open settings_scroll theme palette refresh pull_refresh sort_menu all_resets
  alert_toggle add_account providers)
[ $# -gt 0 ] && order=("$@")

for name in "${order[@]}"; do
  echo "== $name"
  # shellcheck disable=SC2086
  line=$(python3 "$here/ftl.py" "$serial" "$pkg" "$runs" "$out/$name" $(scenario "$name") | tee /dev/stderr | tail -1)
  echo "{\"scenario\": \"$name\", \"result\": $line}" >> "$out/summary.jsonl"
done

#!/usr/bin/env bash
# Renders the Play Store graphics from frame.html and the README screenshots in docs/screenshots.
# Output goes to app/src/main/play/listings/en-US/graphics, the layout that Gradle Play Publisher
# reads. Needs Node and Google Chrome, which Playwright drives. Re-record the screenshots first if
# the UI changed:
#   ./gradlew :app:recordRoborazziDebug :widget:recordWidgetGallery
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
frame="file://$root/scripts/store-graphics/frame.html"
out="$root/app/src/main/play/listings/en-US/graphics"
playwright=1.58.2

render() { # <output png> <width> <height> <query>
  mkdir -p "$(dirname "$1")"
  npx -y "playwright@$playwright" screenshot --channel chrome --viewport-size="$2,$3" \
    --wait-for-timeout=800 "$frame?$4" "$1" >/dev/null
  echo "${1#"$root"/}"
}

enc() { python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1"; }

phone() { # <n> <screenshot> <title> <subtitle> [scroll offset]
  render "$out/phone-screenshots/$1.png" 1080 1920 \
    "kind=phone&shot=$2&title=$(enc "$3")&sub=$(enc "$4")&offset=${5:-0}"
}

render "$out/icon/icon.png" 512 512 "kind=icon"
render "$out/feature-graphic/feature-graphic.png" 1024 500 "kind=feature"

phone 1 overview.png "All your AI limits" "Weekly and session usage for all your subscriptions."
phone 2 detail.png "Pace, not just percent" "See if you're ahead of an even pace, and when you'd hit 100%."
phone 3 resets.png "Know when limits reset" "An alert when a weekly limit really resets."
render "$out/phone-screenshots/4.png" 1080 1920 \
  "kind=widgets&title=$(enc "Widgets everywhere")&sub=$(enc "Rings, bars and shapes for the home and lock screen.")"
phone 5 stats.png "See how you use it" "Clean resets, busiest hours and who works hardest."
phone 6 overview-grape-dark.png "Your colours, day or night" "Wallpaper colours or eight palettes, light or dark."
phone 7 accounts.png "All your AI plans" "Add every account you pay for, from any supported service."
phone 8 reset-confetti.png "A little delight" "Confetti when a limit resets while you watch."

tablet() { # <n> <screenshot> <title> <subtitle>
  local q
  q="kind=tablet&shot=$2&title=$(enc "$3")&sub=$(enc "$4")"
  render "$out/tablet-screenshots/$1.png" 1920 1080 "$q"
  render "$out/large-tablet-screenshots/$1.png" 2560 1440 "$q"
}

tablet 1 expanded.png "List and detail, side by side" "Built for tablets and foldables."
tablet 2 expanded-dark.png "Every detail at a glance" "Pace, charts and alerts next to your accounts."
tablet 3 resets-expanded-dark.png "Know when limits reset" "Every upcoming reset in one place."
tablet 4 stats-expanded.png "See how you use it" "Your own usage history, on the big screen."

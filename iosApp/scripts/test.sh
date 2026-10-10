#!/usr/bin/env bash
# Generates the Xcode project and runs the app's unit and UI tests on an iPhone simulator.
# Usage, from the repository root: iosApp/scripts/test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

xcodegen generate --quiet

# The newest iPhone simulator this Xcode has, so the script does not depend on a model name.
device=$(xcrun simctl list devices available --json | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
phones = [
    (runtime, d["udid"])
    for runtime, ds in devices.items() if "iOS" in runtime
    for d in ds if d["name"].startswith("iPhone")
]
print(sorted(phones)[-1][1])
')

xcodebuild \
    -project Headroom.xcodeproj \
    -scheme Headroom \
    -destination "platform=iOS Simulator,id=$device" \
    -derivedDataPath build \
    test

#!/bin/bash
# Signs the linked headroomd binary with a stable code-signing identity.
#
# The linker emits an ad-hoc signature whose cdhash changes on every rebuild, so a keychain
# "Always Allow" granted once would stop matching the next build and macOS would prompt again.
# Signing with a real identity keeps the designated requirement stable across rebuilds, which is
# what makes the one-time approval in `headroomd connect claude` durable.
#
# Override the identity with HEADROOM_CODESIGN_IDENTITY; by default the first Apple Development
# identity in the login keychain is used.
set -euo pipefail

BIN="$(cd "$(dirname "$0")" && pwd)/build/bin/macosArm64/debugExecutable/headroomd.kexe"
if [[ ! -f "$BIN" ]]; then
  echo "no binary at $BIN; run :desktop:daemon:linkDebugExecutableMacosArm64 first" >&2
  exit 1
fi

if [[ -z "${HEADROOM_CODESIGN_IDENTITY:-}" ]]; then
  HEADROOM_CODESIGN_IDENTITY="Headroom Quota Daemon Pilot"
fi
if ! security find-certificate -c "$HEADROOM_CODESIGN_IDENTITY" >/dev/null 2>&1; then
  HEADROOM_CODESIGN_IDENTITY="$(
    security find-identity -v -p codesigning | awk -F'"' '/Apple Development/ {print $2; exit}'
  )"
fi
if [[ -z "${HEADROOM_CODESIGN_IDENTITY:-}" ]]; then
  echo "no codesigning identity found; pass HEADROOM_CODESIGN_IDENTITY" >&2
  exit 1
fi

codesign --force --timestamp=none \
  --sign "$HEADROOM_CODESIGN_IDENTITY" \
  --identifier dev.sebastiano.headroom.daemon.pilot \
  "$BIN"
codesign --verify --verbose=1 "$BIN" 2>&1 | head -2
codesign -dvv "$BIN" 2>&1 | grep -E "Identifier=|Authority=" | head -3

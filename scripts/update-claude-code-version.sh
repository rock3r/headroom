#!/usr/bin/env bash
# Sets the Claude Code version that Headroom's Claude requests send in their User-Agent to the
# latest published Claude Code, and prints the change. Claude lists usage-limit resets only for
# recent Claude Code clients, so docs/RELEASING.md runs this before every release.
# Usage: scripts/update-claude-code-version.sh [version, default: the latest on npm]
set -euo pipefail

file="$(cd "$(dirname "$0")/.." && pwd)/core/quota/src/main/kotlin/dev/sebastiano/headroom/quota/ClaudeCodeIdentity.kt"
pattern='^    const val VERSION = "([0-9]+\.[0-9]+\.[0-9]+)"$'

latest="${1:-$(npm view @anthropic-ai/claude-code version)}"
if ! [[ "$latest" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Not a Claude Code release version: '$latest'" >&2
    exit 1
fi

current=$(sed -nE "s/$pattern/\1/p" "$file")
if [[ -z "$current" ]]; then
    echo "Could not find the VERSION line in $file" >&2
    exit 1
fi

if [[ "$current" == "$latest" ]]; then
    echo "Claude Code version is already $current."
    exit 0
fi

sed -E -i.bak "s/$pattern/    const val VERSION = \"$latest\"/" "$file"
rm "$file.bak"
echo "Claude Code version: $current -> $latest"

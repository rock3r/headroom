#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPO_DIR="${1:-/Users/sebp/src/compose-charts}"
REF="${2:-headroom-integration}"

if [[ ! -d "$REPO_DIR/.git" && ! -f "$REPO_DIR/.git" ]]; then
    echo "Error: compose-charts git repository not found at $REPO_DIR" >&2
    exit 1
fi

COMMIT_SHA="$(git -C "$REPO_DIR" rev-parse "${REF}^{commit}")"
SHORT_SHA="$(git -C "$REPO_DIR" rev-parse --short=7 "$COMMIT_SHA")"
COMMIT_DATE="$(git -C "$REPO_DIR" show -s --format=%cs "$COMMIT_SHA")"

rm -rf "$SCRIPT_DIR/src/main/kotlin"
mkdir -p "$SCRIPT_DIR/src/main/kotlin"

git -C "$REPO_DIR" archive "$COMMIT_SHA" charts-core/src/main/kotlin \
    | tar -x -C "$SCRIPT_DIR/src/main/kotlin" --strip-components=4
git -C "$REPO_DIR" archive "$COMMIT_SHA" charts-compose/src/main/kotlin \
    | tar -x -C "$SCRIPT_DIR/src/main/kotlin" --strip-components=4
git -C "$REPO_DIR" show "$COMMIT_SHA:LICENSE" > "$SCRIPT_DIR/LICENSE"
git -C "$REPO_DIR" show "$COMMIT_SHA:NOTICE" > "$SCRIPT_DIR/NOTICE"

perl -pi -e "s/^- Upstream commit: \`[0-9a-f]+\` \([0-9-]+\)\./- Upstream commit: \`$COMMIT_SHA\` ($COMMIT_DATE)./" \
    "$SCRIPT_DIR/README.md"

ABOUT_JSON="$ROOT_DIR/app/config/aboutlibraries/libraries/compose-charts.json"
if [[ -f "$ABOUT_JSON" ]]; then
    perl -pi -e "s/\"artifactVersion\": \"[^\"]+\"/\"artifactVersion\": \"$SHORT_SHA\"/" "$ABOUT_JSON"
fi

echo "Updated third-party/compose-charts to $COMMIT_SHA ($COMMIT_DATE)"

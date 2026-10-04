#!/usr/bin/env bash
# Prints the machine's memory, swap and free disk, and its largest processes, every few seconds.
# CI runs it next to ./gradlew check, so the job log shows what a runner was short of even when
# the runner dies mid-job.
# Usage: scripts/ci-resources.sh [seconds between lines, default 30]
set -uo pipefail

interval="${1:-30}"

# Names a process by its JVM role where it has one, so the Gradle daemon, the Kotlin daemon and
# each test JVM are told apart.
role() {
    grep -oE 'GradleDaemon|KotlinCompileDaemon|Gradle Test Executor [0-9]+|GradleWorkerMain' <<<"$1" |
        head -1 || true
}

while true; do
    memory=$(free -m | awk '
        /^Mem:/ { printf "memory used %d MB, available %d MB", $3, $7 }
        /^Swap:/ { printf ", swap used %d of %d MB", $3, $2 }')
    disk=$(df -m --output=avail . | tail -1 | tr -d ' ')
    largest=$(ps -eo rss=,args= --sort=-rss | head -4 | while read -r rss args; do
        name=$(role "$args")
        printf '%s %d MB; ' "${name:-${args%% *}}" $((rss / 1024))
    done)
    echo "[resources] $memory; disk free $disk MB; largest: ${largest%; }"
    sleep "$interval"
done

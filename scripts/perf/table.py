#!/usr/bin/env python3
"""Prints a table of a suite run, or compares two: scripts/perf/table.py <label> [<label-after>]."""
import json
import sys

COLS = ["frames", "app_jank_total", "any_jank", "late", "ui_p50", "ui_p90", "ui_max", "ui_over8", "rt_p90", "rt_max"]
HEAD = ["frames", "appJ", "anyJ", "late", "uiP50", "uiP90", "uiMax", "ui>8", "rtP90", "rtMax"]


def load(label):
    out = {}
    for line in open(f"build/perf/{label}/summary.jsonl"):
        d = json.loads(line)
        out[d["scenario"]] = d["result"]["median"]  # a later run of a scenario wins
    return out


def fmt(v):
    return "-" if v is None else (f"{v:g}" if isinstance(v, (int, float)) else str(v))


def main():
    a = load(sys.argv[1])
    b = load(sys.argv[2]) if len(sys.argv) > 2 else None
    print("| scenario | " + " | ".join(HEAD) + " |")
    print("|---" * (len(HEAD) + 1) + "|")
    for name, m in a.items():
        if b is None:
            cells = [fmt(m.get(c)) for c in COLS]
        elif name in b:
            cells = [f"{fmt(m.get(c))} → {fmt(b[name].get(c))}" for c in COLS]
        else:
            continue
        print(f"| {name} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    main()

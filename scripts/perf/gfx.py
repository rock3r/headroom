#!/usr/bin/env python3
"""Measures frame times for one scripted interaction, several times, with dumpsys gfxinfo.

Usage: scripts/perf/gfx.py <serial> <package> <runs> <setup-steps> -- <measured-steps>

Steps are the same as scripts/trace-steps.sh (tap:X,Y wait:S back fling-up fling-down) plus
swipe:X1,Y1,X2,Y2,MS, key:NAME and start (a warm start of the main activity). Setup steps run
before each measured run and are not counted. Each run resets gfxinfo, runs the measured steps,
then reads the frame summary. Prints one line per run and the median of each figure, as JSON on
the last line.
"""
import json
import re
import statistics
import subprocess
import sys
import time


def adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True).stdout


def screen(serial):
    out = adb(serial, "shell", "wm", "size")
    sizes = re.findall(r"(\d+)x(\d+)", out)
    w, h = sizes[-1]
    return int(w), int(h)


def run_step(serial, package, step, size):
    w, h = size
    x, low, high = w // 2, h * 4 // 5, h // 4
    if step.startswith("tap:"):
        tx, ty = step[4:].split(",")
        adb(serial, "shell", "input", "tap", tx, ty)
    elif step.startswith("wait:"):
        time.sleep(float(step[5:]))
    elif step == "back":
        adb(serial, "shell", "input", "keyevent", "KEYCODE_BACK")
    elif step.startswith("key:"):
        adb(serial, "shell", "input", "keyevent", step[4:])
    elif step == "fling-up":
        adb(serial, "shell", "input", "swipe", str(x), str(low), str(x), str(high), "150")
    elif step == "fling-down":
        adb(serial, "shell", "input", "swipe", str(x), str(high), str(x), str(low), "150")
    elif step.startswith("swipe:"):
        adb(serial, "shell", "input", "swipe", *step[6:].split(","))
    elif step == "start":
        adb(serial, "shell", "am", "start", "-W", "-n", f"{package}/dev.sebastiano.headroom.MainActivity")
    else:
        raise SystemExit(f"Unknown step: {step}")


FIELDS = {
    "frames": r"Total frames rendered: (\d+)",
    "janky": r"Janky frames: (\d+)",
    "p50": r"^50th percentile: (\d+)ms",
    "p90": r"^90th percentile: (\d+)ms",
    "p95": r"^95th percentile: (\d+)ms",
    "p99": r"^99th percentile: (\d+)ms",
    "missed_vsync": r"Number Missed Vsync: (\d+)",
    "slow_ui": r"Number Slow UI thread: (\d+)",
    "slow_draw": r"Number Slow issue draw commands: (\d+)",
    "gpu_p50": r"^50th gpu percentile: (\d+)ms",
    "gpu_p99": r"^99th gpu percentile: (\d+)ms",
}


def parse(text):
    # The first block is the app's own (the main window); later ones are other surfaces.
    out = {}
    for key, pattern in FIELDS.items():
        m = re.search(pattern, text, re.MULTILINE)
        out[key] = int(m.group(1)) if m else None
    out["janky_pct"] = round(100 * out["janky"] / out["frames"], 1) if out["frames"] else None
    return out


def main():
    serial, package, runs = sys.argv[1], sys.argv[2], int(sys.argv[3])
    rest = sys.argv[4:]
    split = rest.index("--")
    setup, measured = rest[:split], rest[split + 1 :]
    size = screen(serial)
    results = []
    for i in range(runs):
        for step in setup:
            run_step(serial, package, step, size)
        adb(serial, "shell", "dumpsys", "gfxinfo", package, "reset")
        for step in measured:
            run_step(serial, package, step, size)
        r = parse(adb(serial, "shell", "dumpsys", "gfxinfo", package))
        results.append(r)
        print(f"run {i + 1}: {r}", flush=True)
    med = {}
    for key in results[0]:
        values = [r[key] for r in results if r[key] is not None]
        med[key] = statistics.median(values) if values else None
    print(json.dumps({"median": med, "runs": results}))


if __name__ == "__main__":
    main()

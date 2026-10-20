#!/usr/bin/env python3
"""Measures frames for one scripted interaction with Perfetto's FrameTimeline, several times.

Usage: scripts/perf/ftl.py <serial> <package> <runs> <out-dir> <setup> -- <measured> [-- <teardown>]

The steps are the same as scripts/perf/gfx.py. Each run records a light system trace (frame
timeline and scheduling, no app sections) around the measured steps, then asks trace_processor for
the app's frames: how many, how many SurfaceFlinger marked as the app's fault ("App Deadline
Missed"), and the frame durations. Prints one line per run and the medians as JSON on the last line.
Needs trace_processor v58 or later (TP env var, or ~/.local/bin/trace_processor from
https://get.perfetto.dev/trace_processor): older ones cannot read Android 17 traces.
"""
import json
import os
import statistics
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gfx import adb, run_step, screen  # noqa: E402

CONFIG = """
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  atrace_categories: "gfx" atrace_categories: "view"
  atrace_apps: "%s" } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
"""

QUERY = """
with f as (
  select a.dur, a.jank_type, e.dur as expected
  from actual_frame_timeline_slice a
  join process p on p.upid = a.upid
  left join expected_frame_timeline_slice e on e.upid = a.upid and e.name = a.name
  where p.name = '%(pkg)s'
),
work as (
  select s.dur, t.name as thread
  from slice s join thread_track tt on s.track_id = tt.id join thread t using (utid)
    join process p using (upid)
  where p.name = '%(pkg)s'
    and ((t.is_main_thread and s.name like 'Choreographer#doFrame%%')
      or (t.name = 'RenderThread' and s.name like 'DrawFrame%%'))
)
select count(*) as frames,
  sum(jank_type like '%%App Deadline Missed%%') as app_jank,
  sum(jank_type != 'None' and jank_type != 'Buffer Stuffing') as any_jank,
  sum(dur > expected) as late,
  group_concat(dur, ',') as durs,
  (select group_concat(dur, ',') from work where thread != 'RenderThread') as ui,
  (select group_concat(dur, ',') from work where thread = 'RenderThread') as rt
from f;
"""

TP = os.environ.get("TP", os.path.expanduser("~/.local/bin/trace_processor"))
DEVICE_TRACE = "/data/misc/perfetto-traces/headroom-ftl.pftrace"


def pct(values, p):
    if not values:
        return None
    values = sorted(values)
    k = min(len(values) - 1, max(0, round(p / 100 * (len(values) - 1))))
    return values[k]


def record(serial, package, steps, size, out):
    adb(serial, "shell", f"rm -f {DEVICE_TRACE}")
    cfg = CONFIG % package
    proc = subprocess.run(
        ["adb", "-s", serial, "shell", f"perfetto --txt -c - -o {DEVICE_TRACE} --background-wait"],
        input=cfg, capture_output=True, text=True,
    )
    pid = proc.stdout.strip().splitlines()[-1].strip()
    for step in steps:
        run_step(serial, package, step, size)
    adb(serial, "shell", f"kill -TERM {pid}; while kill -0 {pid} 2>/dev/null; do sleep 0.1; done; sleep 0.5")
    subprocess.run(["adb", "-s", serial, "pull", DEVICE_TRACE, out], capture_output=True)


def analyse(trace, package):
    res = subprocess.run([TP, "-Q", QUERY % {'pkg': package}, trace], capture_output=True, text=True)
    lines = [l for l in res.stdout.strip().splitlines() if l.strip()]
    # Output: header line, then the values line, CSV-ish with quotes.
    row = next(csv_rows(lines[1:]))
    row = [None if v == "[NULL]" else v for v in row]
    frames, app_jank, any_jank, late, durs, ui, rt = row

    def ms(text):
        return [int(d) / 1e6 for d in text.split(",")] if text else []

    def r2(v):
        return round(v, 2) if v is not None else None

    durs, ui, rt = ms(durs), ms(ui), ms(rt)
    return {
        "frames": int(frames),
        "app_jank": int(app_jank or 0),
        "any_jank": int(any_jank or 0),
        "late": int(late or 0),
        "p50": r2(pct(durs, 50)),
        "p90": r2(pct(durs, 90)),
        "max": r2(max(durs)) if durs else None,
        "ui_p50": r2(pct(ui, 50)),
        "ui_p90": r2(pct(ui, 90)),
        "ui_max": r2(max(ui)) if ui else None,
        "ui_over8": sum(d > 8.33 for d in ui),
        "rt_p90": r2(pct(rt, 90)),
        "rt_max": r2(max(rt)) if rt else None,
    }


def csv_rows(lines):
    import csv

    yield from csv.reader(lines)


def main():
    serial, package, runs, out_dir = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    rest = sys.argv[5:]
    split = rest.index("--")
    setup, measured = rest[:split], rest[split + 1 :]
    teardown = []
    if "--" in measured:
        split = measured.index("--")
        measured, teardown = measured[:split], measured[split + 1 :]
    os.makedirs(out_dir, exist_ok=True)
    size = screen(serial)
    scale = adb(serial, "shell", "settings", "get", "global", "animator_duration_scale").strip()
    if scale not in ("1", "1.0"):
        raise SystemExit(f"animator_duration_scale is {scale}: animations would not run. Set it to 1.")
    results = []
    for i in range(runs):
        for step in setup:
            run_step(serial, package, step, size)
        focus = adb(serial, "shell", "dumpsys", "window")
        focus = next((l for l in focus.splitlines() if "mCurrentFocus" in l), "")
        if package + "/" not in focus:
            raise SystemExit(f"{package} is not in front: {focus.strip()}")
        trace = os.path.join(out_dir, f"run{i + 1}.pftrace")
        record(serial, package, measured, size, trace)
        for step in teardown:
            run_step(serial, package, step, size)
        r = analyse(trace, package)
        results.append(r)
        print(f"run {i + 1}: {r}", flush=True)
    med = {}
    for key in results[0]:
        values = [r[key] for r in results if r[key] is not None]
        med[key] = statistics.median(values) if values else None
    med["app_jank_total"] = sum(r["app_jank"] for r in results)
    med["frames_total"] = sum(r["frames"] for r in results)
    print(json.dumps({"median": med, "runs": results}))


if __name__ == "__main__":
    main()

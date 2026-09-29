# Tracing

Headroom records its own trace sections with [AndroidX Tracing 2](https://developer.android.com/jetpack/androidx/releases/tracing).
Use them with a Perfetto system trace to find out what makes a frame late.

## How it works

- `AppTracing` (in `app/src/main/.../tracing`) holds the app's `Tracer`. `traced(name) { … }`
  records one section, and `tracedItem(name) { … }` records one lazy list item's composition.
  The Settings screen wraps every row this way, as `Settings: <row>`.
- Debug builds install a real tracer at start-up (`app/src/debug/.../InstallAppTracing.kt`).
  Release builds keep the library's stub, which records nothing.
- The tracer only records while a system trace is running with the app in `atrace_apps`. It writes
  to `no_backup/perfetto_traces/` in the app's data folder, and flushes when an activity stops.
- Tracing 2 records in the app's own buffer, which system traces do not capture yet. Its timestamps
  use the same clock as Perfetto (`CLOCK_BOOTTIME`), so concatenating the app's file onto a system
  trace puts both on one timeline.

## Recording a scroll of Settings

1. Install a debug build, open Headroom, and open Settings.
2. Run `scripts/trace-settings-scroll.sh <adb serial>`. It records 20 seconds of system trace while
   flinging the list, presses Home so the app flushes, pulls both traces and merges them into
   `build/traces/merged.pftrace`.
3. Open the merged trace in [ui.perfetto.dev](https://ui.perfetto.dev), or query it with
   `trace_processor`.

## Useful queries

Frames that missed their deadline, with the Settings rows composed around them:

```sql
select f.name as frame, round(f.dur / 1e6, 1) as dur_ms, f.jank_type,
  (select group_concat(s.name || ' ' || round(s.dur / 1e6, 1) || 'ms', ' | ')
   from slice s
   where s.name like 'Settings:%' and s.ts < f.ts + f.dur and s.ts + s.dur > f.ts - 33000000)
    as settings_rows
from actual_frame_timeline_slice f join process p using (upid)
where p.name = 'dev.sebastiano.headroom' and f.jank_type != 'None'
order by f.dur desc;
```

Where the main thread and the render thread spend their time:

```sql
select t.name as thread, s.name, count(*) as n, round(sum(s.dur) / 1e6) as total_ms
from slice s join thread_track tt on s.track_id = tt.id join thread t using (utid)
  join process p using (upid)
where p.name = 'dev.sebastiano.headroom' and s.depth <= 2
  and (t.is_main_thread or t.name = 'RenderThread')
group by t.name, s.name order by total_ms desc limit 25;
```

A debug build runs without R8 and with Compose's debug checks, so its absolute timings are slower
than a release build's. Compare rows against each other, not against the 16 ms budget. An emulator
renders in software, which makes render thread timings much worse than on a phone.

# Tracing

Headroom uses [AndroidX Tracing 2](https://developer.android.com/jetpack/androidx/releases/tracing) to record app trace sections.
Combine them with a Perfetto system trace to investigate late frames.

## How it works

- `AppTracing` in `app/src/main/.../tracing` writes to AndroidX Tracing's global tracer.
  `traced(name) { … }` records a section. `tracedItem(name) { … }` records a lazy-list item's composition.
  Settings wraps each row with the name `Settings: <row>`.
- Debug builds include `androidx.tracing:tracing-wire`.
  At startup, `ConnectedProfilerTracingInitializer` installs its recording tracer globally.
  Release builds retain the library's non-recording stub because they exclude tracing-wire.
- Debug builds include `androidx.compose.runtime:runtime-tracing`.
  Since Compose 1.13, this library records each composable's name, file and line through the global tracer.
  It needs no native library.
- Broadcasts to `androidx.tracing.profiler.ConnectedProfilerTracingReceiver` control recording.
  An IDE profiler sends the same broadcasts:

  | Action (`androidx.tracing.profiler.action.…`) | What it does |
  |---|---|
  | `START` | Deletes old in-process traces and turns recording on. It stays on, even across app restarts, until `STOP`. |
  | `FLUSH_TRACES_GET_PATH` | Writes the buffer out, copies the traces to `/sdcard/Android/media/dev.sebastiano.headroom/perfetto_traces`, and returns that folder as the result data. |
  | `STOP` | Turns recording off. |

  ```bash
  adb shell am broadcast -a androidx.tracing.profiler.action.START \
    -n dev.sebastiano.headroom/androidx.tracing.profiler.ConnectedProfilerTracingReceiver
  ```

- App and system traces use `CLOCK_BOOTTIME`.
  Concatenating the app file onto the system trace aligns their events on one timeline.

## Recording a scroll of Settings

1. Install a debug build, open Headroom, and open Settings.
2. Run `scripts/trace-settings-scroll.sh <adb serial>`.
   The script starts app recording and captures 20 seconds of system trace while scrolling the list.
   It flushes and stops app recording, pulls the files, and combines them in `build/traces/merged.pftrace`.
3. Open the merged trace in [ui.perfetto.dev](https://ui.perfetto.dev), or query it with
   `trace_processor`.

## Recording a screen as it opens

`scripts/trace-steps.sh <adb serial> <output directory> <step>...` captures app and system traces while running the requested steps.
Supported steps are `tap:X,Y`, `wait:S`, `back`, `fling-up`, `fling-down` and `mark:NAME`.
`mark:NAME` writes the device's boot time to `marks.txt` to locate a step in the trace.

Before recording, find tap coordinates with `scripts/find-on-screen.sh <adb serial> <label>`.
The script matches content descriptions or text. Its accessibility dump adds app work, so do not run it during recording.

Choose the recording mode according to the measurement:

- The default mode includes app sections to identify slow composables.
  Compose records about 20,000 sections for one screen opening. That recording slows composition.
- `APP_TRACE=0` disables app recording and excludes every process's track events from the system trace.
  Use this mode to measure frame times.

Phone system traces grow by about 3 MB per second. Keep runs under about 40 seconds to avoid overflowing the 128 MB buffer.
Overflow discards the start of the trace. After recording, check that `system.pftrace` contains frames and has a plausible size.

Debug-build traces on a Pixel 11 Pro found repeated shape-morph creation in two composables.
Settings palette swatches created one `Morph` each on every composition.
The overview's Material pull-to-refresh `LoadingIndicator` built morphs even while hidden.
Each cost about 10 ms or more per composition. The current implementation caches or avoids that work.

## Useful queries

This query lists late frames and Settings rows composed near them:

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

This query ranks Settings composables by time spent outside child slices:

```sql
select s.name, count(*) as n, round(sum(s.dur - coalesce(
    (select sum(c.dur) from slice c where c.parent_id = s.id), 0)) / 1e6, 1) as self_ms
from slice s
where s.category = 'androidx.compose'
  and exists (select 1 from ancestor_slice(s.id) a where a.name like 'Settings:%')
group by s.name order by self_ms desc limit 20;
```

This query groups main-thread and render-thread time by slice name:

```sql
select t.name as thread, s.name, count(*) as n, round(sum(s.dur) / 1e6) as total_ms
from slice s join thread_track tt on s.track_id = tt.id join thread t using (utid)
  join process p using (upid)
where p.name = 'dev.sebastiano.headroom' and s.depth <= 2
  and (t.is_main_thread or t.name = 'RenderThread')
group by t.name, s.name order by total_ms desc limit 25;
```

Debug builds omit R8 and enable Compose debug checks. Their absolute timings are slower than release timings.
Use debug traces to compare rows; use release measurements to assess the 16 ms frame budget.
Software rendering makes emulator render-thread timings much slower than phone timings.

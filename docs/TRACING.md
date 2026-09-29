# Tracing

Headroom records its own trace sections with [AndroidX Tracing 2](https://developer.android.com/jetpack/androidx/releases/tracing).
Use them with a Perfetto system trace to find out what makes a frame late.

## How it works

- `AppTracing` (in `app/src/main/.../tracing`) records into AndroidX Tracing's global tracer.
  `traced(name) { … }` records one section, and `tracedItem(name) { … }` records one lazy list
  item's composition. The Settings screen wraps every row this way, as `Settings: <row>`.
- Debug builds include `androidx.tracing:tracing-wire`. At start-up its
  `ConnectedProfilerTracingInitializer` registers a recording tracer as the global one. Release
  builds do not include it, so they keep the library's stub, which records nothing.
- Debug builds also include `androidx.compose.runtime:runtime-tracing`. From Compose 1.13 it
  records every composable, by name, file and line, through the same global tracer. No native
  library is involved.
- Recording is controlled with broadcasts to `androidx.tracing.profiler.ConnectedProfilerTracingReceiver`,
  the same ones an IDE's profiler sends:

  | Action (`androidx.tracing.profiler.action.…`) | What it does |
  |---|---|
  | `START` | Deletes old in-process traces and turns recording on. It stays on, even across app restarts, until `STOP`. |
  | `FLUSH_TRACES_GET_PATH` | Writes the buffer out, copies the traces to `/sdcard/Android/media/dev.sebastiano.headroom/perfetto_traces`, and returns that folder as the result data. |
  | `STOP` | Turns recording off. |

  ```bash
  adb shell am broadcast -a androidx.tracing.profiler.action.START \
    -n dev.sebastiano.headroom/androidx.tracing.profiler.ConnectedProfilerTracingReceiver
  ```

- The in-process trace is separate from a Perfetto system trace. Both use `CLOCK_BOOTTIME`, so
  concatenating the app's file onto a system trace puts both on one timeline.

## Recording a scroll of Settings

1. Install a debug build, open Headroom, and open Settings.
2. Run `scripts/trace-settings-scroll.sh <adb serial>`. It starts in-process recording, records
   20 seconds of system trace while flinging the list, flushes and stops in-process recording,
   pulls both traces and merges them into `build/traces/merged.pftrace`.
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

The slowest composables inside the Settings rows, by their own time:

```sql
select s.name, count(*) as n, round(sum(s.dur - coalesce(
    (select sum(c.dur) from slice c where c.parent_id = s.id), 0)) / 1e6, 1) as self_ms
from slice s
where s.category = 'androidx.compose'
  and exists (select 1 from ancestor_slice(s.id) a where a.name like 'Settings:%')
group by s.name order by self_ms desc limit 20;
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

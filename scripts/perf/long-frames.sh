#!/usr/bin/env bash
# Lists the long main-thread frames of a trace and, for each, the slices that took the most time
# in it (self time, without their children). Usage: scripts/perf/long-frames.sh <trace> [min ms]
set -euo pipefail
trace="${1:?trace}"; min="${2:-8}"
pkg="${PKG:-dev.sebastiano.headroom.benchmark}"
tp="${TP:-$HOME/.local/bin/trace_processor}"
"$tp" -Q "
create perfetto table frames as
select s.id, s.ts, s.dur, s.track_id from slice s
  join thread_track tt on s.track_id = tt.id join thread t using (utid) join process p using (upid)
where p.name = '$pkg' and t.is_main_thread and s.name like 'Choreographer#doFrame%' and s.dur > $min * 1e6;

select f.ts as frame_ts, round(f.dur / 1e6, 1) as frame_ms, d.name,
  round(sum(d.dur - coalesce((select sum(c.dur) from slice c where c.parent_id = d.id), 0)) / 1e6, 2) as self_ms,
  count(*) as n
from frames f join descendant_slice(f.id) d
group by f.id, d.name
having self_ms > 0.4
order by f.ts, self_ms desc;
" "$trace" 2>/dev/null | awk -F, 'BEGIN{last=""} {if ($1!=last) {print ""; last=$1} print}'

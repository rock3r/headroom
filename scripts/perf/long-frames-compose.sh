#!/usr/bin/env bash
# For each long main-thread frame of a merged trace (system + app sections), prints the frame and
# the composables and app sections that spent the most self time inside it.
# Usage: scripts/perf/long-frames-compose.sh <merged trace> [min ms] [top n]
set -euo pipefail
trace="${1:?trace}"; min="${2:-12}"; top="${3:-12}"
pkg="${PKG:-dev.sebastiano.headroom.benchmark}"
tp="${TP:-$HOME/.local/bin/trace_processor}"
"$tp" -Q "
create perfetto table frames as
select s.id, s.ts, s.dur from slice s
  join thread_track tt on s.track_id = tt.id join thread t using (utid) join process p using (upid)
where p.name = '$pkg' and t.is_main_thread and s.name like 'Choreographer#doFrame%' and s.dur > $min * 1e6;

create perfetto table selfs as
select f.ts as frame_ts, round(f.dur / 1e6, 1) as frame_ms, s.name,
  round(sum(s.dur - coalesce((select sum(c.dur) from slice c where c.parent_id = s.id), 0)) / 1e6, 2) as self_ms,
  count(*) as n
from frames f join slice s on s.ts >= f.ts and s.ts < f.ts + f.dur
  -- Only the app's own slices: the trace records every process's track events.
  left join thread_track tt on s.track_id = tt.id left join thread t using (utid)
  left join process_track pt on s.track_id = pt.id
where coalesce(t.upid, pt.upid) = (select upid from process where name = '$pkg')
  and (s.category is not null or s.name in ('AndroidOwner:measureAndLayout', 'TextStringSimpleNode::measure', 'Constructing StaticLayout', 'Compose:applyChanges', 'AndroidOwner:draw', 'Chart:compile'))
group by f.id, s.name;

select * from (
  select *, row_number() over (partition by frame_ts order by self_ms desc) as r from selfs
) where r <= $top order by frame_ts, self_ms desc;
" "$trace" 2>/dev/null | awk -F, 'BEGIN{last=""} {if ($1!=last) {print ""; last=$1} print}' | cut -c1-200

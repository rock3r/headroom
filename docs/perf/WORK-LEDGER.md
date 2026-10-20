# Performance audit — work ledger

Branch: `perf/frame-drop-audit` (from `main` @ `7b3244d`).
Device: Pixel 7 Pro (`cheetah`), Android 17, 1080×2340 @ 120 Hz (8.33 ms frame budget).
Build under test: `benchmark` build type — release (R8, no Compose debug checks), debug-signed,
`<profileable shell>`, application ID `dev.sebastiano.headroom.benchmark`, so the phone's real
Headroom install (1.3.0, real accounts) is never touched. Fresh install ⇒ demo data.

## Method

- Frame numbers: `scripts/perf/gfx.py` (dumpsys gfxinfo, per-run reset, N runs, medians), and
  Perfetto FrameTimeline (`actual_frame_timeline_slice`) for jank attribution.
- Cause-finding: Perfetto system trace + in-process AndroidX Tracing 2 sections (composable names),
  only switched on for cause-finding runs, never for the numbers.
- Every change: measure before, change, measure after with the same script and run count.

## Log

| Time | What |
|---|---|
| 10-19 22:25 | Pulled `main` (`7b3244d`), made branch `perf/frame-drop-audit`, added the `benchmark` build type. |
| 10-19 22:29 | First measurements looked perfect: 0 idle frames. They were wrong (traps 1, 6). |
| 10-19 22:37 | Another agent was driving the same phone. Paused until the user and atc sorted out the lease. |
| 10-20 00:19 | Lease back with `--ttl 14400`. Animation scales were at 1. `ftl.py` now refuses to run unless the scale is 1 and the benchmark build is in front. |
| 10-20 00:21–00:38 | Baseline: 22 scenarios, 5 runs each, FrameTimeline (`build/perf/baseline`, table under "Baseline"). |
| 10-20 00:39–00:41 | Cause-finding traces with app and Compose sections (`build/perf/cause/*`). Freed the lease for the Clockblock agent. |
| 10-20 00:42 | Analysis: (1) refresh is GPU-bound in the shimmer shader, with jank rising as stars accumulate; (2) the overview and detail redraw the whole window at 120 Hz while at rest; (3) every screen change composes the whole screen in one 30–80 ms frame; (4) the chart recompiles on every frame of a period change. |
| 10-20 00:45 | Fix candidates built: shimmer star culling (F1), 30 fps vote for endless decorative motion (F2). |

## Baseline (main @ 7b3244d, benchmark build, animations on, 5 runs, medians)

`appJ` = frames SurfaceFlinger blamed on the app, summed over the 5 runs. `anyJ` = median per run of
frames with any jank except buffer stuffing. `late` = frames presented after their expected
deadline. `ui*` = `Choreographer#doFrame` durations, ms. `ui>8` = main-thread frames over 8.33 ms.
`rt*` = RenderThread `DrawFrame` durations, ms.

| scenario | frames | appJ | anyJ | late | uiP50 | uiP90 | uiMax | ui>8 | rtP90 | rtMax |
|---|---|---|---|---|---|---|---|---|---|---|
| idle_overview (5 s) | 621 | 0 | 0 | 0 | 1.93 | 2.46 | 4.51 | 0 | 5.5 | 7.66 |
| idle_resets | 0 | 0 | 0 | 0 | - | - | - | 0 | - | - |
| idle_stats | 0 | 0 | 0 | 0 | 0.05 | 7.02 | 7.02 | 0 | - | - |
| idle_detail (5 s) | 631 | 0 | 0 | 0 | 2.89 | 3.84 | 6.52 | 0 | 6.72 | 9.85 |
| idle_settings | 0 | 0 | 0 | 0 | 0.61 | 20 | 20 | 0 | 4.06 | 4.06 |
| overview_fling | 379 | 4 | 2 | 8 | 1.53 | 3.28 | 14.52 | 3 | 5.23 | 7.25 |
| tabs | 422 | 10 | 11 | 4 | 1.28 | 2.65 | 63.29 | 4 | 3.95 | 5.68 |
| resets_scroll | 130 | 2 | 0 | 0 | 1.41 | 3.32 | 8.06 | 0 | 4.66 | 6.35 |
| stats_scroll | 222 | 13 | 4 | 3 | 1.63 | 4.72 | 18.17 | 6 | 4.68 | 6.24 |
| detail_open | 403 | 26 | 9 | 5 | 2.44 | 4.21 | 78.03 | 8 | 4.47 | 7.3 |
| detail_scroll | 384 | 1 | 0 | 3 | 2.48 | 3.8 | 7.84 | 0 | 5.79 | 8.58 |
| detail_period | 566 | 19 | 12 | 21 | 1.76 | 4.18 | 15.12 | 12 | 6.32 | 14.49 |
| settings_open | 404 | 21 | 9 | 5 | 1.19 | 2.75 | 50.16 | 6 | 4.31 | 6.03 |
| settings_scroll | 442 | 2 | 0 | 0 | 1.94 | 3.44 | 10.84 | 2 | 4.97 | 6.83 |
| theme | 205 | 10 | 6 | 2 | 1.59 | 2.77 | 32.13 | 2 | 4.16 | 6.27 |
| palette | 309 | 15 | 9 | 3 | 1.87 | 2.93 | 32.28 | 3 | 3.73 | 5.18 |
| refresh | 408 | 18 | 67 | 153 | 3.77 | 9.36 | 36.82 | 82 | 10.25 | 18.48 |
| pull_refresh | 483 | 15 | 68 | 198 | 6.08 | 9.28 | 31.02 | 89 | 10.35 | 19.34 |
| sort_menu | 372 | 15 | 6 | 3 | 1.85 | 3.93 | 34.23 | 2 | 4.4 | 7.76 |
| all_resets | 308 | 9 | 7 | 2 | 1.22 | 2.44 | 71.63 | 4 | 4.05 | 6.36 |
| alert_toggle | 350 | 6 | 6 | 2 | 1.57 | 2.26 | 21.12 | 2 | 5.41 | 7.03 |
| add_account | 328 | 16 | 7 | 4 | 1.06 | 1.92 | 47.19 | 6 | 3.73 | 5.85 |
| providers | 43 | 0 | 4 | 2 | 1.09 | 2.3 | 25.77 | 3 | 2.73 | 4.02 |

## Findings

### F1. The refresh shimmer is GPU-bound (refresh, pull-to-refresh)

- 67 janky frames per run (of about 410), and 153 late. The main thread spends 2.9 s of 3.5 s in
  `postAndWait`, waiting for the RenderThread. The RenderThread spends 2.2 s in
  `dequeueBuffer → waitForBufferRelease`, waiting for the GPU to give a buffer back. The GPU is
  the bottleneck.
- Jank by time since the tap: almost none in the first 2 s, then 5–11 janky frames in every
  250 ms until the end. The sweep runs in the first half of the shimmer; the second half is
  the trail of stars.
- `RefreshShimmer`'s AGSL runs for every pixel of the 1080×2232 window on every frame. For each
  pixel it visits 9 grid cells and works out each cell's star (hashes with `sin`, `pow` in
  `easeInverse`) before checking whether the cell has a star at all (`h > 0.72`, 28 % of cells).
  For every star that is born it evaluates `star()` (four `exp`, two `length`) at every pixel of
  the 3×3 neighbourhood, even 90 px from a star whose radius is 15 px. More stars are born as the
  front travels, so the cost grows over the shimmer, which matches the jank pattern.

### F2. The overview and account detail redraw at 120 Hz while at rest

- `idle_overview`: 621 frames in 5 s (124 fps) with nothing touched. Each frame is about 2 ms of
  main thread and 4–5.5 ms of RenderThread. HWUI draws the whole window every frame
  ("Drawing 0 0 1080 2232").
- Causes: the next reset card's shape turning (`rememberInfiniteTransition`, a full turn per
  minute, 6°/s), Material's `LinearWavyProgressIndicator` on any account over pace, and
  `WavyRing` in the detail.
- Resets, Stats and Settings are still at rest (0 frames).

### F3. Opening a screen composes it in one long frame

- `detail_open` 78 ms, `all_resets` 72 ms, `tabs` 63 ms, `settings_open` 50 ms, `add_account`
  47 ms (median of each run's worst frame).
- Traces show no single culprit. In the 80 ms detail frame: composition 38 ms (with tracing on),
  measure and layout 13 ms, text measurement 5.6 ms over 66 texts, apply changes 6.5 ms, draw
  6.6 ms. Returning to the overview from another tab or from Settings rebuilds the overview:
  46–59 ms.
- Still to measure: how much is ART running uncompiled code, since an `adb install` has no
  compiled code until background dexopt runs.

### F4. The chart recompiles on every frame of a period change

- `detail_period`: 12 janky frames per run, RenderThread up to 14.5 ms. `Chart:compile` runs in
  place on every frame of the transition, 0.6–7.4 ms each.
| 10-20 00:47–01:05 | Measured F1+F2 (`build/perf/f1f2`). F1 confirmed (W1). The F2 vote works on the detail (W2) but not on the overview. |
| 10-20 01:05–01:38 | Pinned down the overview. With the card's turn off it is still 120 fps; with every wavy bar off it is still 120 fps; with both off it draws 0 frames. Both votes are dropped inside lazy grid items (U1). |
| 10-20 01:40 | TDD: `DecorativePhaseTest` failed against an infinite-transition stub (60 changes a second), then passed against `rememberDecorativePhase`, a 30 fps clock. The card's turn now uses it (W3). |
| 10-20 01:50–02:01 | Compile-mode experiment (`build/perf/cm-*`), see F3 below. |
| 10-20 02:07 | `./gradlew check` green. Commits failed: 1Password signing agent locked (trap 10). Work left staged in three groups. |
| 10-20 02:09 | F5 checked and dropped: Haze builds the status-bar blur's RenderEffect once when the blur appears (0.8 ms plus a 6.5 ms RenderThread wait on that one frame), not on every frame of the fade. The fade's alpha is a layer alpha in Haze 2.0. Not worth changing. |

### F3 measurements: ART compilation (Pixel 7 Pro, benchmark build with W1–W3, 5 runs)

Cold start (`am start -W` TotalTime, 5 runs): **verify 281–291 ms** (fresh sideload, nothing
compiled) · **speed-profile 166–188 ms** (libraries' baseline profiles plus what JIT recorded in
this session) · speed 212–228 ms (everything compiled, which is slower: more code to map).

Worst frame per run (median of 5), ms:

| scenario | verify | speed-profile | speed |
|---|---|---|---|
| detail_open | 96.4 | 62.8 | 67.3 |
| tabs | 84.4 | 57.1 | 55.5 |
| settings_open | 63.9 | 42.9 | 52.8 |
| all_resets | 72.6 | 55.4 | 51.9 |
| add_account | 68.4 | 47.1 | 50.5 |

App-blamed janky frames (sum of 5 runs): verify 33/22/29/14/26 → speed-profile 20/9/20/10/10.

Takeaway: profile-guided compilation removes about a third of the worst frame on every screen
change and 40 % of cold start. Headroom ships only its libraries' baseline profiles
(`assets/dexopt/baseline.prof`, 9 KB), so until the phone's background compilation runs (hours
or days after an install or update), users get the `verify` column. A baseline profile for
Headroom's own code gives the `speed-profile` column from the first launch: Play compiles it at
install, and ProfileInstaller queues it for sideloads. Even compiled, opening a screen takes
43–63 ms, which is composition work (see F3 above).
| 10-20 02:27–02:35 | Recorded the baseline profile on an R8-less benchmark build (`scripts/perf/baseline-profile.sh`). `dump-profiles` flushes the app itself; `kill -SIGUSR1` is not allowed for a non-debuggable app. |
| 10-20 02:41–03:06 | Baseline profile A/B with the same procedure for both APKs (`profile-ab.sh`): W4. |
| 10-20 03:06 | Final full suite with W1–W4 (`build/perf/final`). |

### F4 in detail (not fixed)

The FrameTimeline of `detail_period` shows the jank comes almost entirely from the 3M→1Y and
1Y→Period transitions. For about 190 ms each frame's main thread takes 7.8–14 ms, the chart
compiling the scene for the animated viewport in place on every frame. Most frames are marked
"Buffer Stuffing", which means added latency rather than drops: only 2–3 frames per run miss
their deadline. Fixing it means changing the vendored chart (compile the target scene once and
tween to it, or compile the moving viewport off the main thread), which is bigger than this audit.

### F6. A refresh's two long frames (not fixed)

After W1, a refresh's worst frames (uiMax 36–42 ms in the benchmark runs) are two
composition-bound frames, not the shader. Seen in the traced run: the tap frame (20 ms traced:
Material's pull-to-refresh `LoadingIndicatorImpl` 2.3 ms, six `loadVectorResource` calls
1.3 ms) and the frame where the synced data lands and every card recomposes (25 ms traced:
`CardTop`, `MeterRow`, text measurement). Both happen once per refresh.

## Final run: baseline → W1–W4 (benchmark build installed fresh, compiled state reset as in the baseline, 5 runs, medians)

W4 changes nothing here, because a fresh `adb install` is not compiled. See W4 for the compiled comparison. The worst-frame column (`uiMax`) varies by ±10 ms between identical runs (trap 11).

| scenario | frames | appJ | anyJ | late | uiP50 | uiP90 | uiMax | ui>8 | rtP90 | rtMax |
|---|---|---|---|---|---|---|---|---|---|---|
| idle_overview | 621 → 619 | 0 → 5 | 0 → 2 | 0 → 0 | 1.93 → 1.98 | 2.46 → 2.71 | 4.51 → 6.25 | 0 → 0 | 5.5 → 5.47 | 7.66 → 8.56 |
| idle_resets | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 | - → 0.76 | - → 38.66 | - → 39.1 | 0 → 0 | - → 2.59 | - → 2.59 |
| idle_stats | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 | 0.05 → 0.08 | 7.02 → 15.73 | 7.02 → 15.73 | 0 → 0 | - → - | - → - |
| idle_detail | 631 → 157 | 0 → 0 | 0 → 0 | 0 → 0 | 2.89 → 2.51 | 3.84 → 3.52 | 6.52 → 4.41 | 0 → 0 | 6.72 → 6.36 | 9.85 → 7.8 |
| idle_settings | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 | 0.61 → 0.51 | 20 → 14.07 | 20 → 14.07 | 0 → 0 | 4.06 → 2.91 | 4.06 → 2.91 |
| overview_fling | 379 → 383 | 4 → 7 | 2 → 3 | 8 → 4 | 1.53 → 1.63 | 3.28 → 3.15 | 14.52 → 14.06 | 3 → 2 | 5.23 → 5.5 | 7.25 → 7.4 |
| tabs | 422 → 427 | 10 → 13 | 11 → 13 | 4 → 4 | 1.28 → 1.28 | 2.65 → 2.68 | 63.29 → 75.35 | 4 → 5 | 3.95 → 3.84 | 5.68 → 6.41 |
| resets_scroll | 130 → 131 | 2 → 0 | 0 → 0 | 0 → 0 | 1.41 → 1.47 | 3.32 → 3.23 | 8.06 → 7.73 | 0 → 0 | 4.66 → 4.8 | 6.35 → 6.24 |
| stats_scroll | 222 → 224 | 13 → 9 | 4 → 3 | 3 → 2 | 1.63 → 1.73 | 4.72 → 4.44 | 18.17 → 23.38 | 6 → 6 | 4.68 → 4.87 | 6.24 → 6.41 |
| detail_open | 403 → 281 | 26 → 26 | 9 → 9 | 5 → 7 | 2.44 → 2.87 | 4.21 → 4.91 | 78.03 → 79.33 | 8 → 9 | 4.47 → 4.77 | 7.3 → 9.34 |
| detail_scroll | 384 → 117 | 1 → 4 | 0 → 3 | 3 → 4 | 2.48 → 2.51 | 3.8 → 3.69 | 7.84 → 7.67 | 0 → 0 | 5.79 → 6.84 | 8.58 → 11.43 |
| detail_period | 566 → 308 | 19 → 8 | 12 → 8 | 21 → 15 | 1.76 → 2.01 | 4.18 → 4.66 | 15.12 → 13.35 | 12 → 8 | 6.32 → 7.09 | 14.49 → 14.42 |
| settings_open | 404 → 407 | 21 → 25 | 9 → 10 | 5 → 5 | 1.19 → 1.27 | 2.75 → 2.78 | 50.16 → 53.05 | 6 → 6 | 4.31 → 4.44 | 6.03 → 6.42 |
| settings_scroll | 442 → 452 | 2 → 1 | 0 → 0 | 0 → 1 | 1.94 → 1.69 | 3.44 → 3.09 | 10.84 → 8.21 | 2 → 0 | 4.97 → 4.83 | 6.83 → 6.07 |
| theme | 205 → 205 | 10 → 8 | 6 → 7 | 2 → 2 | 1.59 → 1.49 | 2.77 → 2.56 | 32.13 → 30.14 | 2 → 4 | 4.16 → 3.89 | 6.27 → 5.76 |
| palette | 309 → 310 | 15 → 13 | 9 → 11 | 3 → 4 | 1.87 → 1.62 | 2.93 → 2.58 | 32.28 → 23.07 | 3 → 3 | 3.73 → 3.36 | 5.18 → 4.72 |
| refresh | 408 → 456 | 18 → 10 | 67 → 7 | 153 → 4 | 3.77 → 1.47 | 9.36 → 2.41 | 36.82 → 43.93 | 82 → 3 | 10.25 → 4.49 | 18.48 → 7.33 |
| pull_refresh | 483 → 517 | 15 → 9 | 68 → 4 | 198 → 3 | 6.08 → 1.36 | 9.28 → 2.7 | 31.02 → 21.88 | 89 → 5 | 10.35 → 4.33 | 19.34 → 6.76 |
| sort_menu | 372 → 380 | 15 → 10 | 6 → 8 | 3 → 3 | 1.85 → 1.79 | 3.93 → 4.03 | 34.23 → 36.8 | 2 → 2 | 4.4 → 4.42 | 7.76 → 7.55 |
| all_resets | 308 → 316 | 9 → 15 | 7 → 9 | 2 → 3 | 1.22 → 1.23 | 2.44 → 2.37 | 71.63 → 62.76 | 4 → 3 | 4.05 → 3.9 | 6.36 → 6.26 |
| alert_toggle | 350 → 348 | 6 → 8 | 6 → 6 | 2 → 1 | 1.57 → 1.51 | 2.26 → 2.24 | 21.12 → 19.96 | 2 → 3 | 5.41 → 4.99 | 7.03 → 7.17 |
| add_account | 328 → 323 | 16 → 16 | 7 → 7 | 4 → 4 | 1.06 → 1.03 | 1.92 → 1.71 | 47.19 → 53.58 | 6 → 6 | 3.73 → 3.4 | 5.85 → 5.75 |
| providers | 43 → 43 | 0 → 0 | 4 → 4 | 2 → 2 | 1.09 → 1.03 | 2.3 → 2.39 | 25.77 → 28.27 | 3 → 3 | 2.73 → 2.8 | 4.02 → 3.93 |

## Follow-up after review (10-20 08:00)

- The owner asked for the wavy bar fix at 60 Hz, since 30 fps looks janky. Vendored Material's indicator with a 60 fps wave (W5) and moved all decorative motion to 60 fps. `rememberDecorativePhase` now runs a `FrameGate` on every frame callback instead of `delay`, so its frames come at an even pace (every other vsync at 120 Hz). TDD: `FrameGateTest` failed against an always-pass stub, then passed.
- Measured (`build/perf/wavy60`): idle overview 618 → 312 frames in 5 s; idle detail 156 → 314 (60 fps, as intended). The detail's "late" count (0–15 per run, no jank) comes from the display switching to 60 Hz for the vote; SurfaceFlinger reports no jank.
- Codex review: `long-frames-compose.sh` counted other processes' track-event slices. It now counts only the app's own.

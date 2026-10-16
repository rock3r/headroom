# Compose Charts for Jetpack Compose (vendored)

Declarative charts for Jetpack Compose, by Chris Sinco: <https://github.com/c5inco/compose-charts>.
Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Headroom uses it for the long-term usage history chart on the account detail screen (`:app`).

## Why it's vendored

Compose Charts has no Maven release yet, and CI must not rely on `mavenLocal()` or an included
build. So its source lives here, in its own packages (`dev.c5inco.charts.core` and
`dev.c5inco.charts.compose`), built as one module (`:third-party:compose-charts`).

- Upstream URL: <https://github.com/c5inco/compose-charts>
- Upstream branch: `headroom-integration`
- Upstream commit: `0fc5c82d29b23c38136c44fc6aa7f7d3f908a4a5` (2026-10-17).
- Upstream PRs included: `c5inco/compose-charts` PRs [#14](https://github.com/c5inco/compose-charts/pull/14) (`x-rules`: vertical `XRuleMark` and `XBandMark` annotations), [#15](https://github.com/c5inco/compose-charts/pull/15) (`line-gaps-and-patterns`: `Gaps.Connect`, `Gaps.ConnectWithin` and `LinePattern.Dashed`/`Dotted`), and [#16](https://github.com/c5inco/compose-charts/pull/16) (`viewport`: `ChartViewport`, pan/zoom/fling gestures, M4 decimation, plot clipping and scroll semantics) — all open, merged together on the pushed upstream branch `headroom-integration` (initial merge commit `04e546019aa6c687ebdf27da23577570235553b5`).
- Copied: `charts-core/src/main/kotlin` and `charts-compose/src/main/kotlin` (Kotlin sources only, packages unchanged), plus `LICENSE` and `NOTICE`. Not copied: the sample app, the tests, the benchmarks and the documentation site.
- Local patches: none (pure upstream copy).
- Static analysis: `:third-party:*` modules are excluded from detekt, Compose Rules, ktfmt and Android Lint in `build-logic` (`Quality.kt`, `AndroidLibraryConventionPlugin.kt` and `AndroidComposeConventionPlugin.kt`) so the vendored copy stays byte-for-byte identical to upstream without editing `config/detekt/detekt.yml` or adding baselines.

## Updating

Run `./third-party/compose-charts/update.sh` from the repository root:

```bash
./third-party/compose-charts/update.sh [path-to-compose-charts] [commit-or-ref]
```

By default it reads `/Users/sebp/src/compose-charts` at `headroom-integration`, re-copies
`charts-core/src/main/kotlin`, `charts-compose/src/main/kotlin`, `LICENSE` and `NOTICE`, and
updates the commit line above (and the version in `app/config/aboutlibraries/libraries/compose-charts.json`).

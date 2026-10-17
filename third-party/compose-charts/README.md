# Compose Charts for Jetpack Compose (vendored)

Declarative charts for Jetpack Compose, by Chris Sinco: <https://github.com/c5inco/compose-charts>.
Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Headroom uses it for the long-term usage history chart on the account detail screen (`:app`).

## Why it's vendored

Compose Charts has no Maven release yet, and CI must not rely on `mavenLocal()` or an included
build. So its source lives here, in its own packages (`dev.c5inco.charts.core` and
`dev.c5inco.charts.compose`), built as one module (`:third-party:compose-charts`).

- Upstream URL: <https://github.com/c5inco/compose-charts>
- Upstream branch: `main`
- Upstream commit: `9aacb2bc1ec6d03a7191d126149cd47f8305b814` (2026-10-17).
- Upstream PRs included: pure copy of upstream `main` (PRs [#14](https://github.com/c5inco/compose-charts/pull/14) `x-rules`, [#15](https://github.com/c5inco/compose-charts/pull/15) `line-gaps-and-patterns`, [#16](https://github.com/c5inco/compose-charts/pull/16) `viewport` and [#17](https://github.com/c5inco/compose-charts/pull/17) CI workflow are all merged into `main`; no pending PRs).
- Copied: `charts-core/src/main/kotlin` and `charts-compose/src/main/kotlin` (Kotlin sources only, packages unchanged), plus `LICENSE` and `NOTICE`. Not copied: the sample app, the tests, the benchmarks and the documentation site.
- Local patches: none (pure upstream copy).
- Static analysis: `:third-party:*` modules are excluded from detekt, Compose Rules, ktfmt and Android Lint in `build-logic` (`Quality.kt`, `AndroidLibraryConventionPlugin.kt` and `AndroidComposeConventionPlugin.kt`) so the vendored copy stays byte-for-byte identical to upstream without editing `config/detekt/detekt.yml` or adding baselines.

## Updating

Run `./third-party/compose-charts/update.sh` from the repository root:

```bash
./third-party/compose-charts/update.sh [path-to-compose-charts] [commit-or-ref]
```

By default it reads `/Users/sebp/src/compose-charts` at `main`, re-copies
`charts-core/src/main/kotlin`, `charts-compose/src/main/kotlin`, `LICENSE` and `NOTICE`, and
updates the commit line above (and the version in `app/config/aboutlibraries/libraries/compose-charts.json`).

# Compose Charts for Jetpack Compose (vendored)

Declarative charts for Jetpack Compose, by Chris Sinco: <https://github.com/c5inco/compose-charts>.
Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Headroom uses it for the long-term usage history chart on the account detail screen (`:app`).

## Why it's vendored

Vendoring pins an exact upstream commit, including changes not yet released, without requiring
`mavenLocal()` or an included build in CI. Its source lives here, in its own packages (`dev.c5inco.charts.core` and
`dev.c5inco.charts.compose`), built as one module (`:third-party:compose-charts`).

- Upstream URL: <https://github.com/c5inco/compose-charts>
- Upstream branch: `main`
- Upstream commit: `bc0477af8832d4b9c96aba0489fe928583ffceba` (2026-11-15).
- Copied: `charts-core/src/main/kotlin` and `charts-compose/src/androidMain/kotlin` into `src/main/kotlin`, and `charts-compose/src/commonMain/kotlin` into `src/commonMain/kotlin` (Kotlin sources only, packages unchanged), plus `LICENSE` and `NOTICE`. Not copied: the desktop implementation, sample apps, tests, benchmarks and documentation site.
- Local patch: `trace-lint.patch` adds a declaration-only `UnclosedTrace` suppression to the Android `beginTraceSection` wrapper. Its caller, `ChartScenes.trace`, closes the section in `finally`; Headroom's Lint gate cannot follow that pairing across the wrappers. This changes no runtime behavior and leaves the check enabled elsewhere.
- Build integration: the Android module compiles the renderer and JVM core together, using `-Xmulti-platform` and marking `Platform.kt` with `-Xcommon-sources` to resolve its `expect` hooks against the Android `actual` implementations. This preserves upstream's source layout without adding desktop or iOS targets to the app's chart dependency.
- Static analysis: `:third-party:*` modules are excluded from detekt, Compose Rules and ktfmt in `build-logic` (`Quality.kt` and `AndroidComposeConventionPlugin.kt`) so the vendored copy stays identical to upstream apart from the documented local patch. Android Lint still runs, including when the app checks its dependencies.

## Updating

Run `./third-party/compose-charts/update.sh` from the repository root:

```bash
./third-party/compose-charts/update.sh [path-to-compose-charts] [commit-or-ref]
```

By default it reads `/Users/sebp/src/compose-charts` at `main`, re-copies
the source sets listed above, `LICENSE` and `NOTICE`, applies `trace-lint.patch`, and
updates the commit line above (and the version in `app/config/aboutlibraries/libraries/compose-charts.json`).
If the patch no longer applies, the update fails before updating version metadata; review the
upstream tracing implementation and adjust or remove the patch before rerunning the updater.

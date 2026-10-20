# Material 3 linear wavy progress indicator (vendored, patched)

Material 3's determinate `LinearWavyProgressIndicator`, from AOSP's `androidx.compose.material3:material3`.
Apache License 2.0: see [LICENSE](LICENSE).

Headroom uses it for the quota bar of an account that needs attention (`QuotaBar` in `:core:designsystem`).

## Why it's vendored

Upstream moves the wave on every display frame, forever, and gives no way to lower that rate:
the wave offset is an infinite `Animatable` inside the modifier node. On a 120 Hz phone, one
wavy bar on screen keeps a screen at rest redrawing the whole window 120 times a second. On a
Pixel 7 Pro that is about 2 ms of main thread and 5 ms of RenderThread per frame (see
`docs/perf/`, F2 and U2). A frame-rate vote around it does not help inside the overview's lazy
grid (U1).

- Upstream: `androidx.compose.material3:material3-android:1.5.0-alpha29` sources jar.
- Copied:
  - `androidx/compose/material3/internal/LinearWavyProgressModifiers.kt` as
    `LinearWavyProgressModifiers.kt`, with the indeterminate parts kept so the diff against upstream
    stays small.
  - The determinate `LinearWavyProgressIndicator` from `WavyProgressIndicator.kt`.
  - `IncreaseVerticalSemanticsBounds` from `internal/AccessibilityUtil.kt`.
  - The amplitude animation specs from `WavyProgressIndicator.kt`, with their motion tokens
    inlined.
- Package: `dev.sebastiano.headroom.material3.wavy`. Upstream's package would clash with
  Material's own private classes of the same names.
- Patch (marked `Headroom patch`): the wave offset moves on at most 60 frames a second
  (`WaveFrameRate`). It still follows the frame clock and the animation duration scale, so the
  wave moves at the same speed. Each frame the wave moves on sets the deadline for the next one
  a 60 Hz frame later, and display frames before that deadline are skipped: every other frame at
  120 Hz, two in three at 90 Hz, every frame at 60 Hz. Frames it skips change no state, so
  nothing is drawn for them.
- Static analysis: `:third-party:*` modules are excluded from detekt, Compose Rules, ktfmt and
  Android Lint, so the copy stays close to upstream.

## Updating

Download the sources jar of the Material 3 version the app uses from Google's Maven repository
(`androidx/compose/material3/material3-android/<version>/material3-android-<version>-sources.jar`),
copy the files listed above again, and re-apply the patch and the package change. If Material
ever lets the wave's frame rate be lowered, drop this module and use Material's indicator again.

# Testing

## TDD flow

1. Write the test first.
2. Run just that test and check it fails for the reason you expect.
3. Write the minimum production code to make it pass.
4. Run the test again, then `./gradlew :module:check`.
5. Refactor with the tests green.

## Test layers

| Layer | Where | Runs on | Command |
|---|---|---|---|
| Unit tests | `src/test` in JVM modules | JVM, JUnit 5 | `./gradlew :core:quota:test` |
| Multiplatform tests | `src/commonTest` in multiplatform modules | JVM, JUnit 5 | `./gradlew :core:model:jvmTest` |
| Multiplatform tests | `src/commonTest` in multiplatform modules | iOS simulator (macOS only) | `./gradlew :core:model:iosSimulatorArm64Test` |
| Android unit tests | `src/test` in Android modules | JVM with Robolectric, JUnit 4 | `./gradlew :core:data:testDebugUnitTest` |
| Compose UI tests | `src/test` in `:app` and `:core:designsystem` | Robolectric | `./gradlew :app:testDebugUnitTest` |
| Screenshots | `src/test`, Roborazzi | Robolectric | `./gradlew :app:recordRoborazziDebug` |
| End-to-end tests | `src/androidTest` in `:app` | Emulator, fake data | `./gradlew :app:pixel9api37DebugAndroidTest` |
| Release smoke test | `scripts/release-smoke-test.sh` | Emulator, demo data, minified APK | See [RELEASING.md](RELEASING.md#the-smoke-test) |

### Multiplatform tests

`commonTest` code runs on every target, so it uses `kotlin.test` only: no JUnit imports and no
`java.*` APIs. Kotlin/Native does not allow commas in function names, so backticked test names
leave them out. `./gradlew check` runs the iOS simulator tests on macOS and skips them elsewhere;
CI runs them in a macOS job.

### Fixtures, not live services

Provider parsing tests use recorded JSON fixtures in `src/test/resources` (`src/jvmTest/resources`
in multiplatform modules). HTTP behaviour is tested against OkHttp's `MockWebServer`, which only
runs on the JVM, so those tests live in `jvmTest`. No test talks to a real provider.

### End-to-end tests

The instrumented tests start the app in demo mode with a fixed clock, so the screens show the
same numbers every run. They run on a Gradle Managed Device (`pixel9api37`), which Gradle
creates and starts itself. Locally you need the Android 37 `google_apis` system image. In CI
the same task runs with a software GPU.

To run the instrumented tests on an emulator or phone you already have running, without the
managed device, install both APKs on it and start the runner:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <serial> install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w \
    dev.sebastiano.headroom.test/dev.sebastiano.headroom.HeadroomTestRunner
```

The tests share one app process. A test that changes the demo accounts puts them back after it
runs.

### Screenshots

Roborazzi records screenshots of the main screens from Robolectric with
`./gradlew :app:recordRoborazziDebug`. `./gradlew :widget:recordWidgetGallery` plays each widget
in the Android 16 widget player and saves it. Both are used for the README. They are not compared
in CI, because font rendering differs between machines.

Robolectric draws only when a test asks. A test that draws frames just to move animations along
calls `drawFrame()` on a node rather than dropping the result of `captureToImage()`. Under native
graphics a picture's pixels live outside the Java heap and the garbage collector does not count
them, so dropped pictures pile up: thousands of them ran CI runners out of memory (issue #6).

### Widgets

Widget tests play every captured document in Robolectric's copy of the Android 16 widget player
and fail when only the card background is drawn. That player supports document level 6 only, so
the widgets are captured at that level.

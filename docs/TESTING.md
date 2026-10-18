# Testing

## Test-driven development

1. Write the test first.
2. Run the targeted test. Check that it fails for the expected reason.
3. Write the minimum production code to make it pass.
4. Run the test again, then `./gradlew :module:check`.
5. Refactor while keeping the tests passing.

## Test layers

| Layer | Where | Runs on | Command |
|---|---|---|---|
| Unit tests | `src/jvmTest` in KMP modules, `src/test` in JVM modules | JVM, JUnit 5 | `./gradlew :core:quota:jvmTest` |
| Multiplatform tests | `src/commonTest` in multiplatform modules | JVM, JUnit 5 | `./gradlew :core:model:jvmTest` |
| Multiplatform tests | `src/commonTest` in multiplatform modules | iOS simulator (macOS only) | `./gradlew :core:model:iosSimulatorArm64Test` |
| iOS app tests | `iosApp/HeadroomTests`, `iosApp/HeadroomUITests` | iOS simulator (macOS only) | `iosApp/scripts/test.sh` |
| Android unit tests | `src/test` in Android modules | JVM with Robolectric, JUnit 4 | `./gradlew :core:data:testDebugUnitTest` |
| Compose UI tests | `src/test` in `:app` and `:core:designsystem` | Robolectric | `./gradlew :app:testDebugUnitTest` |
| Screenshots | `src/test`, Roborazzi | Robolectric | `./gradlew :app:recordRoborazziDebug` |
| End-to-end tests | `src/androidTest` in `:app` | Emulator, fake data | `./gradlew :app:pixel9api37DebugAndroidTest` |
| Release smoke test | `scripts/release-smoke-test.sh` | Emulator, demo data, minified APK | See [RELEASING.md](RELEASING.md#the-smoke-test) |

### Multiplatform tests

`commonTest` runs on every target and must use `kotlin.test`. It cannot import JUnit or `java.*` APIs.
Kotlin/Native forbids commas in function names, including backticked test names.
`./gradlew check` runs iOS simulator tests on macOS and skips them on other hosts.
Continuous integration (CI) runs those tests in a macOS job.

### Provider tests use fixtures

Provider parsing tests read recorded JSON fixtures from `src/test/resources`, or `src/jvmTest/resources` in multiplatform modules.
HTTP tests use OkHttp's JVM-only `MockWebServer` and belong in `jvmTest`.
Tests never contact real providers.

### End-to-end tests

Instrumented tests start demo mode with a fixed clock to keep screen values repeatable.
Gradle creates and starts the managed device `pixel9api37`.
Local runs require the Android 37 `google_apis` system image. CI runs the same task with software graphics.

To use an existing emulator or phone, install both APKs and start the runner:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <serial> install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w \
    dev.sebastiano.headroom.test/dev.sebastiano.headroom.HeadroomTestRunner
```

Tests share one app process. Each test must restore any demo accounts it changes.

### Screenshots

`./gradlew :app:recordRoborazziDebug` records main-screen screenshots through Roborazzi and Robolectric.
`./gradlew :widget:recordWidgetGallery` saves each widget from the Android 16 widget player.
The README uses both image sets. CI does not compare screenshots because machines render fonts differently.

Robolectric draws frames only on request. Use a node's `drawFrame()` to advance animations without saving images.
Discarding `captureToImage()` results can exhaust CI memory, as reported in issue #6.
Native graphics stores pixels outside the Java heap, so the garbage collector does not account for that memory.

### Widgets

Widget tests play every captured document in Robolectric's copy of the Android 16 widget player
and fail when only the card background is drawn. That player supports document level 6 only, so
the widgets are captured at that level.

### Older Android versions

The app runs on Android 14 and later. Robolectric runs tests on the target SDK unless a test says
otherwise, so behaviour that differs on older versions gets its own test with
`@Config(sdk = [34])`: for example, no widgets before Android 16.

Keep tests that draw AGSL shaders (the delights, the liquid ripple, the Haze blur) on the default
SDK. Robolectric's native graphics for SDK 34 has an older shader compiler, which rejects shaders
that Android 14 compiles, such as `layout(color)` uniforms and a `const` loop bound. An Android 14
emulator compiles every shader in the app.

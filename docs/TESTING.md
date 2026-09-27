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
| Android unit tests | `src/test` in Android modules | JVM with Robolectric, JUnit 4 | `./gradlew :core:data:testDebugUnitTest` |
| Compose UI tests | `src/test` in `:app` and `:core:designsystem` | Robolectric | `./gradlew :app:testDebugUnitTest` |
| Screenshots | `src/test`, Roborazzi | Robolectric | `./gradlew :app:recordRoborazziDebug` |
| End-to-end tests | `src/androidTest` in `:app` | Emulator, fake data | `./gradlew :app:pixel9api37DebugAndroidTest` |

### Fixtures, not live services

Provider parsing tests use recorded JSON fixtures in `src/test/resources`. HTTP behaviour is
tested against OkHttp's `MockWebServer`. No test talks to a real provider.

### End-to-end tests

The instrumented tests start the app in demo mode with a fixed clock, so the screens show the
same numbers every run. They run on a Gradle Managed Device (`pixel9api37`), which Gradle
creates and starts itself. Locally you need the Android 37 `google_apis` system image. In CI
the same task runs with a software GPU.

### Screenshots

Roborazzi records screenshots of the main screens from Robolectric with
`./gradlew :app:recordRoborazziDebug`. `./gradlew :widget:recordWidgetGallery` plays each widget
in the Android 16 widget player and saves it. Both are used for the README. They are not compared
in CI, because font rendering differs between machines.

### Widgets

Widget tests play every captured document in Robolectric's copy of the Android 16 widget player
and fail when only the card background is drawn. That player supports document level 6 only, so
the widgets are captured at that level.

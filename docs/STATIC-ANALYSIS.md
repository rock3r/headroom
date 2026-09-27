# Static analysis

Three tools run as part of `./gradlew check`, and all of them fail the build on any finding.

| Tool | What it checks | Config |
|---|---|---|
| detekt 2 | Kotlin code smells and complexity | `config/detekt/detekt.yml`, building on the default rules |
| Compose Rules (Nacho Lopez) | Compose API and state rules, loaded into detekt | Defaults |
| ktfmt | Formatting, KotlinLang style | Applied by the convention plugins |
| Android Lint | Android correctness, security, performance, accessibility | Warnings are errors |

The convention plugins in `build-logic` apply all of them to every module, including the pure
Kotlin modules, which get Android Lint through the `com.android.lint` plugin.

## Commands

```bash
./gradlew check        # everything, the CI gate
./gradlew ktfmtFormat  # rewrite formatting
./gradlew ktfmtCheck   # formatting only
./gradlew detekt       # detekt only
./gradlew lint         # Android Lint only
```

## Lint

Every Lint warning is an error. Four checks are off, because they fail the build whenever a
newer library or SDK is published, which says nothing about the code:
`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` and `OldTargetApi`.

## Suppressions

Fix the cause first. When a suppression is truly needed, put it on the smallest scope
(one declaration, at most one file) and write the reason in a comment next to it. Do not add
baselines.

# Static analysis

`./gradlew check` runs the following analysis tools. Any finding fails the build.

| Tool | What it checks | Config |
|---|---|---|
| detekt 2 | Kotlin code smells and complexity | `config/detekt/detekt.yml`, building on the default rules |
| Compose Rules (Nacho Lopez) | Compose API and state rules, loaded into detekt | Defaults |
| ktfmt | Formatting, KotlinLang style | Applied by the convention plugins |
| Android Lint | Android correctness, security, performance, accessibility | Warnings are errors |

Convention plugins in `build-logic` apply these checks to every module.
Pure Kotlin modules use `com.android.lint` to run Android Lint.

## Commands

```bash
./gradlew check        # everything, the CI gate
./gradlew ktfmtFormat  # rewrite formatting
./gradlew ktfmtCheck   # formatting only
./gradlew detekt       # detekt only
./gradlew lint         # Android Lint only
```

## Lint

Lint treats every warning as an error.
The project disables four checks that report new library or SDK releases rather than code defects:
`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` and `OldTargetApi`.

## Suppressions

Fix the underlying problem before considering a suppression.
Limit any necessary suppression to one declaration, or at most one file. Add a nearby comment explaining the reason.
Do not add baselines.

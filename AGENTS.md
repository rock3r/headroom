# AGENTS.md

## Project overview

Headroom is a native Android app that shows how much of your AI subscription quotas you have
left (Claude, ChatGPT Codex, GitHub Copilot, Grok, Z.AI, Kimi Code, OpenCode Go, JetBrains AI).
It has Material 3 Expressive UI, adaptive layouts, Remote Compose widgets for the home and lock
screens, and a notification when a weekly limit resets.

- Android 16 (API 36) and later only. `compileSdk` and `targetSdk` are 37.
- An iOS app (SwiftUI, iOS 18 and later) in `iosApp/` shares the Kotlin business logic through
  Kotlin Multiplatform. See [iosApp/README.md](iosApp/README.md).
- Kotlin, Jetpack Compose, Material 3 Expressive, Remote Compose. No Views, no Glance.
- Plain constructor injection. There is no DI framework.

## Source of truth docs

| Document | Use it for |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module map, dependency direction, data flow |
| [docs/TESTING.md](docs/TESTING.md) | TDD flow, test layers, how to run each one |
| [docs/STATIC-ANALYSIS.md](docs/STATIC-ANALYSIS.md) | detekt, Compose Rules, ktfmt and Android Lint |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | File placement, naming, git workflow |

## Non-negotiables

### TDD first

1. Write the test.
2. Run the targeted test and see it fail for the right reason.
3. Write the minimum production code to make it pass.
4. Re-run the targeted test, then the module's `check`.

Details: [docs/TESTING.md](docs/TESTING.md).

### Zero tolerance for static analysis

`./gradlew check` must be green before every commit. It runs tests, detekt (with Compose
Rules), ktfmt and Android Lint, and Lint treats every warning as an error.

- Do not edit `config/detekt/detekt.yml`, add baselines, or disable Lint checks without the
  owner's approval.
- Fix the cause. When a suppression is truly needed, keep it to one declaration or file and
  write down why next to it.
- Run `./gradlew ktfmtFormat` instead of formatting by hand.

### Scope discipline

Keep changes to what the task asks for. Unrequested behaviour changes are regressions.

### Secrets

Never commit tokens, keys or real account data. Tests use fixtures and fake data only. Sign-in
tokens are stored encrypted on the device and excluded from backup.

## Running the build

Use JDK 21. `./gradlew check` is the local gate and the CI gate. See the docs above for the
individual tasks.

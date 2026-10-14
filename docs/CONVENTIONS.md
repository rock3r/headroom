# Conventions

## Code

- Package root: `dev.sebastiano.headroom`, then the module name.
- JVM modules use explicit API mode: every public declaration states its visibility.
- Keep Android types out of `:core:model`, `:core:quota` and `:core:auth`.
- Use `kotlin.time` (`Instant`, `Duration`, `Clock`) for instants and durations, and
  `kotlinx-datetime` for calendar dates, times of day and time zones, so the shared code can
  move to Kotlin Multiplatform. Android-only code that formats dates for display can convert
  with `toJavaInstant()` at the formatter.
- Constructor injection only. `:app` wires the object graph in `AppGraph`.

## Compose

- Follow the Compose Rules: a `Modifier` parameter on every public composable, state hoisted
  to the caller, no mutable state in parameters.
- Motion follows Material 3 Expressive: expressive springs on containers, no overshoot on
  values that are read as data (bar ends, ring arcs, numbers).
- A progress indicator is wavy only when its account needs attention (over pace, or above
  85%). Everything else is flat.
- Every piece of meaning carried by motion also has a static form, for when animations are off.

## Git

- Plain, descriptive commit messages. No conventional-commit prefixes.
- One topic per branch. Run `./gradlew check` before every commit.

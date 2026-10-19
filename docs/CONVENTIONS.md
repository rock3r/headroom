# Conventions

## Code

- Start package names with `dev.sebastiano.headroom`, followed by the module name.
- JVM modules use explicit API mode. State the visibility of every public declaration.
- Keep Android types out of `:core:model`, `:core:quota` and `:core:auth`.
- Use `kotlin.time` (`Instant`, `Duration`, `Clock`) for instants and durations, and
  `kotlinx-datetime` for calendar dates, times of day and time zones. These APIs work in shared Kotlin Multiplatform code.
  Android display formatters can convert instants with `toJavaInstant()`.
- Use constructor injection. `AppGraph` in `:app` connects the objects without an injection framework.

## Compose

- Follow Compose Rules. Give each public composable a `Modifier` parameter and let callers own state.
  Parameters must not contain mutable state.
- Use Material 3 Expressive springs for containers.
  Data values, including bar ends, ring arcs and numbers, must not overshoot.
- Use wavy progress indicators only when an account exceeds an even usage rate or 85% usage.
  Keep other indicators flat.
- Give every motion-based message a static form for users who disable animation.

## Git

- Write plain, descriptive commit messages without conventional-commit prefixes.
- Keep each branch to one topic. Run `./gradlew check` before every commit.
- Track releases with milestones named for the version, such as `1.4` or `1.5`, and close a milestone
  once its release is tagged. Put an issue in the milestone of the release it ships in: a fix merged
  after a tag belongs to the next one. An issue with no milestone is backlog, so there is no
  `backlog` milestone.

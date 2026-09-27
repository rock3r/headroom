# Architecture

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin/JVM | Quota types, pace maths, reset alert policy, countdown formatting, repository contracts, demo data |
| `:core:quota` | Kotlin/JVM | One fetcher per provider: calls the provider's usage endpoint and parses it into the model |
| `:core:auth` | Kotlin/JVM | OAuth (PKCE, loopback redirect, device code), API keys, token refresh, token store contract |
| `:core:data` | Android library | Room history, DataStore settings, encrypted token store, sync, reset alarms, notifications |
| `:core:designsystem` | Android library, Compose | Theme, quota indicators, provider avatars, shapes and motion specs |
| `:widget` | Android library | Remote Compose widgets for the home screen and the lock screen |
| `:app` | Android application | Screens, navigation, adaptive layouts, dependency wiring |

Dependencies point down: `:app` → `:widget`, `:core:designsystem`, `:core:data` →
`:core:quota`, `:core:auth` → `:core:model`. The JVM modules have no Android dependencies, so
their tests run fast on the JVM.

## Data flow

Every trigger runs the same sync. The sync writes a snapshot to Room. The app, the widgets and
the reset scheduler only read from Room, so they never disagree and never call the network
themselves.

1. A trigger (periodic WorkManager job, app start, pull to refresh, widget tap) asks the sync
   to refresh one or all accounts.
2. The sync takes a valid access token from the token store, refreshing it when needed, and
   calls the provider's fetcher.
3. The result is stored as the latest snapshot plus a history point per window.
4. The UI and the widgets observe the repository as a `Flow`.
5. The reset scheduler sets one alarm per weekly window, one minute after `resets_at`. The
   alarm re-fetches that account and posts a notification only when the reset really happened
   (usage dropped, or `resets_at` moved forward by about a week).

## Demo mode

`FakeQuotaRepository` in `:core:model` serves the demo accounts. The app uses it when no
account is signed in, and the UI tests, end-to-end tests and README screenshots use it too.

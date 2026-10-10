# Architecture

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin Multiplatform (JVM, iOS) | Quota types, pace maths, reset alert policy, countdown formatting, repository contracts, demo data |
| `:core:quota` | Kotlin Multiplatform (JVM, iOS) | One fetcher per provider: calls the provider's usage endpoint and parses it into the model |
| `:core:auth` | Kotlin Multiplatform (JVM, iOS) | OAuth (PKCE, loopback redirect, device code), API keys, token refresh, token store contract |
| `:core:storage` | Kotlin Multiplatform (Android, JVM, iOS) | Room database (accounts, snapshots, history, reset events), DataStore settings, account fetching and sign-in bookkeeping |
| `:core:data` | Android library | Encrypted token store, sync worker, reset alarms, notifications, the Android wiring (`DataGraph`) |
| `:core:designsystem` | Android library, Compose | Theme, quota indicators, provider avatars, shapes and motion specs |
| `:widget` | Android library | Remote Compose widgets for the home screen and the lock screen |
| `:app` | Android application | Screens, navigation, adaptive layouts, dependency wiring |

Dependencies point down: `:app` → `:widget`, `:core:designsystem`, `:core:data` →
`:core:storage` → `:core:quota`, `:core:auth` → `:core:model`. `:widget` also uses `:core:designsystem`, for the
colour palettes it shares with the app. The JVM modules have no Android dependencies, so their
tests run fast on the JVM.

### Kotlin Multiplatform

The business logic is moving into Kotlin Multiplatform modules, so a SwiftUI iOS app can share it.
A multiplatform module (`headroom.kmp.library`) has JVM, `iosArm64` and `iosSimulatorArm64`
targets, its code in `src/commonMain` and its tests in `src/commonTest`. The Android modules use the
JVM target. Common code uses `kotlin.time`, `kotlinx-datetime` and `kotlinx-atomicfu` locks in place
of `java.time` and `synchronized`, and Okio for hashing. HTTP goes through Ktor
(`KtorQuotaHttpClient`), with the OkHttp engine on the JVM and Android and the Darwin engine on
iOS. `:core:model`, `:core:quota`, `:core:auth` and `:core:storage` are multiplatform.

`:core:storage` uses `headroom.kmp.android.library`, which adds an Android target: Room and DataStore
ship separate Android builds, and the Android app keeps Android's own SQLite and the same file names
(`headroom.db`, `files/datastore/settings.preferences_pb`). iOS opens the same database with the
SQLite that ships with Room. The JVM target only runs `commonTest` on the host, with that bundled
SQLite. `HeadroomStorage` is the one public entry point; each platform opens it with its own
`openHeadroomStorage`.

The OAuth loopback listener (`LoopbackServer`) keeps its HTTP handling in common code, over a small
socket interface: `ServerSocket` on the JVM and POSIX sockets on iOS, always bound to the loopback
address. On iOS the sign-in page must open in `ASWebAuthenticationSession`, never in Safari: Safari
puts Headroom in the background, iOS suspends it, and the listener stops answering.

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
5. The reset scheduler sets one alarm per weekly window, 10 seconds after `resets_at`. The
   alarm re-fetches that account and posts a notification only when the reset really happened
   (usage dropped, or `resets_at` moved forward by about a week).

## Quota windows

A `QuotaSnapshot` holds a list of `QuotaWindow`s. Each window has a `WindowKind`. The kind
decides whether the window can alert, whether it has a pace, and which window an account leads
with.

| Kind | Meaning | Resets | Alerts |
|---|---|---|---|
| `Session`, `Daily` | Short windows, such as the 5-hour session | Yes | Never |
| `Weekly` | Weekly limits | Yes | On by default |
| `Monthly` | Monthly limits | Yes | Opt-in |
| `Other` | A window of unknown length | Maybe | Never |
| `Credit` | A one-time amount, such as a promotional credit | No, it expires | Never |

A credit has no `resetsAt`. Its `expiresAt` holds the date it expires, and the detail screen says
"Expires <date>". When the provider reports money, `usedAmount` and `limitAmount` hold it, with
`amountUnit` set to a currency code such as `USD`.

A window whose `isRecognised` is false is one the provider sent but Headroom does not know yet.
The fetcher keeps it, with a label made from its key, so a new quota shows without an app update.
The detail screen puts an info button next to its label, with a tooltip that explains it.

Credits and unrecognised windows are *informational* (`QuotaWindow.isInformational`). They never
alert (`ResetPolicy.canAlert`), never count as the next reset (`NextReset`), have no pace and
never need attention (`Pace`), and are never an account's primary or session window
(`AccountState`). Because the widgets only show the primary window, the session window and the
next reset, the widgets never show them.

### Claude usage

`ClaudeQuotaFetcher` reads `GET /api/oauth/usage`. It maps the keys as follows.

- Known keys such as `five_hour`, `seven_day` and `seven_day_*` are reset windows.
- `iguana_necktie` is the Claude Code cloud session credit, and `cinder_cove` is the Claude Code
  and Cowork credit. Both are credits. Their `resets_at` is the expiry date.
- Any other object is unrecognised. With a non-null `limit_dollars` it is a credit, labelled
  "Credit · <name>". Without one it is a window of kind `Other`.
- Each `weekly_scoped` row of the `limits` array becomes a "Weekly · <name>" window, named after
  `scope.model` or `scope.surface`. Rows are classified by `kind`, never by label. A row is left
  out when a flat `seven_day_<name>` key reports the same model, so nothing shows twice.

## Expired sign-in

An account whose sync failed with `QuotaErrorKind.Auth` has an expired sign-in
(`AccountState.isSignInExpired`). Its snapshot is kept, but it is stale. `RoomQuotaRepository`
keeps the Auth error through later errors, such as a network error. Only a sync that works clears
it.

- The app and the widgets draw its numbers faded and pulled towards grey (`Modifier.stale` and
  `StaleStyle` in `:core:designsystem`). They are never wavy, and pace is worked out at the time
  of the last good sync. "Last updated 2 hours ago" rounds to the nearest unit (`Age`).
- The card and the detail show a "Sign in" action that is never faded. Every widget layout marks
  the account too, never faded: Bars and the single ring and shape show a "Sign in" label, and the
  ring grid, the lock screen and the Shape grid show a red "!" badge where the number would be,
  because only the account name goes under a ring. The content description still says that the
  sign-in expired, with the last numbers. A widget tap on such an account opens its sign-in.
- It never leads the next reset, gets no reset alarms, and its resets are not listed as upcoming.
- `SignInAlertingRepository` in `:core:data` runs `SignInAlertPolicy` after every refresh. The
  first time an account expires, it posts a warning on the "Sign-in problems" channel. Tapping it
  does not remove it: a good sync or removing the account does. A network error changes nothing.
- Every "Sign in" starts the provider's normal sign-in for that account, as a `SignInState.Starting`
  step at once. `SignInManager.reauthenticate` stores the new tokens under the same account id, so
  the history, name and alert switches stay, and updates the label when the provider reports a new
  email. It refuses a different account: by the provider's account id when both sides have one,
  otherwise by the email. Copilot and the API-key providers name no account, so any sign-in is
  accepted for them.
- Debug builds preview it with demo data:
  `adb shell am broadcast -n dev.sebastiano.headroom/.DemoSignInExpiredReceiver`
  (add `--ez clear true` to undo).

## Usage-limit resets

Each sync reads the resets of Codex, Grok, Claude and Z.AI accounts next to the usage, and Room
stores them with the snapshot. Codex, Grok and Z.AI resets can be used. Claude's can be used only
when the user turns on the experimental setting; otherwise they are shown only. Z.AI resets need a
second sign-in, to ZCode,
which `:core:auth` runs and stores next to the account's API key. About a day before a reset the
user can use expires, a notification reminds them. Each sync also records the resets that were
used or expired, for the Stats tab. The reset clients live in `:core:quota`, the sync and storage
in `:core:data`, and the screens in `:app`. See [RESETS.md](RESETS.md), which also describes that
history and says how to read the logs.

## Demo mode

`FakeQuotaRepository` in `:core:model` serves the demo accounts. The app uses it when no
account is signed in, and the UI tests, end-to-end tests and README screenshots use it too.

## Reset island (experimental)

When a quota resets while Headroom is in the background, an opt-in black pill grows out of the
camera cutout, shows the provider logo and one line, and shrinks back. It lives in `:app`, in the
`island` package, because it needs the app's UI and an Android service.

- `ResetIsland` in `:core:data` is the seam. `AndroidResetNotifier` offers each reset to it before
  it posts the notification. When the island shows the reset, the notification goes to a quiet
  channel, so the user gets no pop-up as well. `:core:data` never depends on `:app`.
- `AppResetIsland` in `:app` implements the seam. `IslandConditions.blockedBy()` is the pure
  decision: setting off, no mode available, screen off, landscape, Headroom in the foreground, or
  Do Not Disturb. In `Overlay` mode a locked device also blocks the island.
- `resolveIslandMode()` picks the mode. `Accessibility` when the service is connected. Otherwise
  `Overlay` when the user allowed Display over other apps (`SYSTEM_ALERT_WINDOW`). Otherwise `None`,
  and the reset uses the heads-up notification.
- `IslandHub` is the in-process signal. `ResetIslandService`, an accessibility service with no
  events and no window content, reports that it is connected and collects the hub's requests.
  Only an accessibility overlay draws above the status bar, the shade and the lock screen.
- `islandGeometry()` is the pure mapping from the camera cutout and the screen size to the pill.
- `Overlay` mode is for a device whose admin blocks accessibility services. `AndroidIslandOverlay`
  adds a `TYPE_APPLICATION_OVERLAY` window from a window context. That window draws below the
  status bar, never shows on the lock screen, and is touchable on purpose: Android draws an
  untouchable overlay at 80% alpha, so black would look grey. It is only as big as the pill
  (`overlayIslandGeometry()`). A tap opens Headroom and a swipe up dismisses it.
- The overlay window must not outlive its animation. A watchdog removes it a few seconds after the
  longest island should have ended. Android also removes the windows of a process that dies.
- The reset checker runs in a WorkManager worker, and a background process can be frozen while the
  pill is on screen. So `AndroidResetNotifier` posts the notification first, then awaits
  `ResetIsland.awaitIdle()`, which returns when the overlay window is gone. The worker's coroutine,
  and so the process, stays alive for that time (about five seconds).

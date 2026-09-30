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
`:core:quota`, `:core:auth` → `:core:model`. `:widget` also uses `:core:designsystem`, for the
colour palettes it shares with the app. The JVM modules have no Android dependencies, so their
tests run fast on the JVM.

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
- The card, the detail and every widget layout show a "Sign in" action that is never faded. A
  widget tap on such an account opens its sign-in.
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

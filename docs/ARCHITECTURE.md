# Architecture

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin Multiplatform (JVM, iOS) | Quota types, pace maths, reset alert policy, countdown formatting, repository contracts, demo data |
| `:core:quota` | Kotlin Multiplatform (JVM, iOS) | One fetcher per provider: calls the provider's usage endpoint and parses it into the model |
| `:core:auth` | Kotlin Multiplatform (JVM, iOS) | OAuth (PKCE, loopback redirect, device code), API keys, token refresh, token store contract |
| `:core:storage` | Kotlin Multiplatform (Android, JVM, iOS) | Room database (accounts, snapshots, history, reset events), DataStore settings, account fetching and sign-in bookkeeping |
| `:core:data` | Android library | Encrypted token store, sync worker, reset alarms, notifications, the Android wiring (`DataGraph`) |
| `:shared` | Kotlin Multiplatform (JVM, iOS) | The `HeadroomKit` framework for the iOS app: wiring, Keychain-backed token store, Swift-friendly view models |
| `:core:designsystem` | Android library, Compose | Theme, quota indicators, provider avatars, shapes and motion specs |
| `:third-party:compose-charts` | Android library, Compose (vendored) | Declarative Compose charts (`charts-core` and `charts-compose` from `c5inco/compose-charts`) for the account detail usage history chart |
| `:widget` | Android library | Remote Compose widgets for the home screen and the lock screen, from Android 16 |
| `:app` | Android application | Screens, navigation, adaptive layouts, dependency wiring |

`:app` depends on `:widget`, `:core:designsystem`, `:third-party:compose-charts` and `:core:data`.
`:core:data` depends on `:core:storage`, which depends on `:core:quota` and `:core:auth`.
Those modules depend on `:core:model`.
`:widget` uses `:core:designsystem` to share the app's colour palettes.
The JVM modules have no Android dependencies. Their tests run directly on the JVM.

### Kotlin Multiplatform

Kotlin Multiplatform shares business logic with the SwiftUI iOS app.
The `headroom.kmp.library` convention creates JVM, `iosArm64` and `iosSimulatorArm64` targets.
Shared source belongs in `src/commonMain`; shared tests belong in `src/commonTest`. Android modules use the JVM target.
Common code uses `kotlin.time`, `kotlinx-datetime`, `kotlinx-atomicfu` locks and Okio hashing.
It avoids `java.time` and `synchronized`.
`KtorQuotaHttpClient` uses Ktor's OkHttp engine on JVM and Android, and Darwin engine on iOS.
`:core:model`, `:core:quota`, `:core:auth` and `:core:storage` use Kotlin Multiplatform.

`:core:storage` uses `headroom.kmp.android.library` to add an Android target for Room and DataStore's separate Android builds.
Android keeps its system SQLite and existing paths: `headroom.db` and `files/datastore/settings.preferences_pb`.
iOS uses Room's bundled SQLite. The JVM target uses bundled SQLite only to run `commonTest` on the host.
`HeadroomStorage` is the public entry point. Each platform supplies `openHeadroomStorage`.
The shared sign-in state machine also lives in `:core:storage`, in `RealSignInController` and `AuthSignInSteps`.

`:shared` builds the static `HeadroomKit` framework for iOS.
`openHeadroom(directory:secrets:)` connects storage, sync and sign-in, matching Android's `DataGraph` and `AppGraph`.
The app implements `SecureValueStore` with the Keychain.
`SecureTokenStore` uses the Android token store's compare-and-swap revision rules.
Swift observes `OverviewUi` and `SignInUi` through main-thread callbacks.
They carry plain values and stable identifiers (`"network"`, `"over"`). The app's String Catalog supplies display text.

The SwiftUI app lives in `iosApp/`. Its [README](../iosApp/README.md) describes builds, tests and integration.
XcodeGen creates the Xcode project from `iosApp/project.yml`.

`LoopbackServer` handles OAuth HTTP redirects in common code through a small socket interface.
It uses JVM `ServerSocket` or iOS POSIX sockets, bound only to the loopback address.
iOS sign-in pages must open in `ASWebAuthenticationSession`.
Safari backgrounds Headroom, which lets iOS suspend the app and stop the listener from answering.

## Data flow

All refresh triggers use the same sync path. Sync writes snapshots to Room.
The app, widgets and reset scheduler read those snapshots without making their own network requests.

1. A WorkManager job, app start, pull-to-refresh action or widget tap requests a refresh for one or all accounts.
2. Sync gets an access token from the token store and refreshes it if needed. It then calls the provider's fetcher.
3. Sync saves the latest snapshot and one history point per window, including when the window resets.
   When long-term history is on (`HistoryRetention.LongTerm`, the default), sync keeps a year of history:
   every point for 7 days, then one per 1-hour bucket up to 31 days, per 3-hour bucket up to 92 days, and
   per 12-hour bucket up to 365 days, always keeping each reset period's first and peak points.
   Compaction runs at most once an hour for the synced account and scans only points older than 7 days.
   When long-term history is off (`HistoryRetention.ShortTerm`), sync keeps 60 days at full resolution and
   deletes older points on the next sync.
4. The UI and widgets observe the repository through a `Flow`.
5. The reset scheduler sets an alarm for each weekly window, 10 seconds after `resets_at`.
   The alarm fetches the account again. It posts an alert after usage drops or `resets_at` advances by about a week.

## Quota windows

A `QuotaSnapshot` contains `QuotaWindow` entries. Each entry has a `WindowKind`.
The kind controls alerts, pace calculations and the account's primary window.

| Kind | Meaning | Resets | Alerts |
|---|---|---|---|
| `Session`, `Daily` | Short windows, such as the 5-hour session | Yes | Never |
| `Weekly` | Weekly limits | Yes | On by default |
| `Monthly` | Monthly limits | Yes | Opt-in |
| `Other` | A window of unknown length | Maybe | Never |
| `Credit` | A one-time amount, such as a promotional credit | No, it expires | Never |

A credit uses `expiresAt` and has no `resetsAt`. Account details display "Expires <date>".
For monetary quotas, `usedAmount` and `limitAmount` hold amounts. `amountUnit` holds a currency code such as `USD`.

A provider window unknown to Headroom has `isRecognised = false`.
The fetcher preserves it and derives a label from its key. New quotas can therefore appear without an app update.
Account details add an info button with an explanatory tooltip beside the label.

Credits and unrecognised windows have `QuotaWindow.isInformational` set.
`ResetPolicy.canAlert` excludes them from alerts. `NextReset` excludes them from next-reset selection.
`Pace` gives them no pace or attention state. `AccountState` excludes them from primary and session window selection.
Widgets show only primary windows, session windows and the next reset, so they omit informational windows.

### Claude usage

`ClaudeQuotaFetcher` reads `GET /api/oauth/usage` and maps its keys to windows:

- Known keys, including `five_hour`, `seven_day` and `seven_day_*`, produce reset windows.
- `iguana_necktie` identifies the Claude Code cloud session credit. `cinder_cove` identifies the Claude Code and Cowork credit.
  Headroom treats their `resets_at` values as expiry dates.
- Unknown objects with non-null `limit_dollars` become credits labelled "Credit · <name>".
  Other unknown objects become `Other` windows.
- Each `weekly_scoped` row in `limits` becomes a "Weekly · <name>" window, named from `scope.model` or `scope.surface`.
  The fetcher classifies rows by `kind`. It omits rows whose model already appears in a flat `seven_day_<name>` key.

### Muse usage

`MuseQuotaFetcher` reads limits from `POST /muse-code/key` on `api.meta.ai`, matching the Muse CLI's startup request.
`MuseDeviceAuth` in `:core:auth` implements the CLI's device-code sign-in.
Meta issues a device client access (DCA) token without a refresh token. Headroom stores the DCA token as the credential.
The credential expires only if Meta's response specifies an expiry time.

`subs_usage.window` becomes a session window, usually five hours. `subs_usage.weekly` becomes a weekly window.
An account without an active subscription returns no `subs_usage`. Headroom reports `QuotaErrorKind.Access` for that response.
Meta supplies no stored usage-limit resets for Muse Code, so Muse has no reset client.
Normal reset alerts use the quota windows' reset times.

### Gemini usage

`GeminiQuotaFetcher` reads subscription and rate limits from `/v1/usage` on `cloudaicompanion.googleapis.com`, matching the Gemini Code Assist CLI request.
`GeminiOAuth` in `:core:auth` implements Google OAuth 2.0 PKCE with a loopback redirect to `http://localhost:<port>/oauth2callback`.
Google issues access and refresh tokens. `daily` becomes a daily window and `session` a session window.

## Expired sign-in

A sync failure with `QuotaErrorKind.Auth` sets `AccountState.isSignInExpired`.
`QuotaResult.Failure` and `AccountState` also carry an optional `AuthFailureReason`, such as `KeyExpired` when a provider reports that an API key itself has expired (for example OpenCode Go's `error.code == "api_key_expired"` on HTTP 401).
`RoomQuotaRepository` stores that reason alongside `Auth` in `lastError` (`"Auth:KeyExpired"`) and retains the stale snapshot and Auth error through later failures, including network errors.
Only a successful sync clears the Auth error.

- `Modifier.stale` and `StaleStyle` in `:core:designsystem` fade usage values towards grey.
  Stale indicators stay flat. Pace uses the last successful sync time.
  `Age` rounds labels such as "Last updated 2 hours ago" to the nearest unit.
- Cards and account details keep the action fully visible.
  OAuth and device-code providers show "Sign-in expired" and a "Sign in" action.
  API-key providers (`Provider.usesApiKey`) show "API key expired" (when `authReason` is `KeyExpired`, with instructions to create a new key in the provider's console) or "API key rejected", and a "Replace API key" action.
  Bars, single-ring and single-shape widgets show a fully visible "Sign in" label.
  Ring grids, lock screen widgets and Shape grids replace the number with a red "!" badge.
  Rings retain the account name below them. Content descriptions announce the expiry and the last usage values.
  Tapping a stale widget account opens sign-in.
- Expired accounts cannot supply the next reset, receive reset alarms or appear under upcoming resets.
- After every refresh, `SignInAlertingRepository` in `:core:data` runs `SignInAlertPolicy`.
  The first expiry posts a warning on "Sign-in problems", worded for either an expired sign-in or an expired/rejected API key.
  A successful sync or account removal clears the warning. Tapping the warning or receiving a network error leaves it in place.
- "Sign in" / "Replace API key" starts the provider's normal flow (`SignInState.Starting` for OAuth and device code, `SignInState.ApiKey` for API keys).
  `SignInManager.reauthenticate` saves new tokens under the same account ID, preserving history, name and alert switches.
  It updates the label if the provider returns a new email.
  It rejects a different provider account ID when both credentials have one; otherwise it compares email addresses.
  Copilot and API-key providers supply no account identity, so Headroom accepts any sign-in for them.
- For API-key providers, `SignInManager` validates a submitted key with one quota fetch before saving it.
  When the provider rejects the key with `QuotaErrorKind.Auth`, `SignInManager` throws `InvalidApiKeyException`, leaving any stored credential untouched, and `RealSignInController` returns to `SignInState.ApiKey` with an inline error (`ApiKeyRejection.Expired` or `ApiKeyRejection.Unauthorized`).
  When validation fails with a network error, nothing is saved and the flow moves to `SignInState.Failed(SignInError.Network)` so the user can retry.
- Debug builds preview it with demo data:
  `adb shell am broadcast -n dev.sebastiano.headroom/.DemoSignInExpiredReceiver`
  (add `--ez clear true` to undo).

## Usage-limit resets

Sync reads Codex, Grok, Claude and Z.AI resets alongside usage. Room saves them with the snapshot.
Users can redeem Codex, Grok and Z.AI resets. Claude redemption requires the experimental setting; otherwise Claude resets remain read-only.
Z.AI requires a separate ZCode sign-in. `:core:auth` stores that credential alongside the account's API key.
Headroom reminds users about usable resets about a day before expiry.
Sync records used and expired resets for Stats.
Reset clients live in `:core:quota`, sync and storage integration in `:core:data`, and screens in `:app`.
[RESETS.md](RESETS.md) describes redemption, history and logging.

## Demo mode

`FakeQuotaRepository` in `:core:model` supplies demo accounts when no account has signed in.
`DemoUsage` generates hourly demo history across the retention horizon (a year when long-term history is on,
60 days when off) and compacts it with `HistoryRetention`.
UI tests, end-to-end tests and README screenshots use the same repository.

## Reset island (experimental)

The opt-in reset island expands from the camera cutout after a reset while Headroom runs in the background.
The black pill shows the provider logo and one line of text, then contracts.
The `island` package in `:app` owns its UI and Android service.

- `ResetIsland` in `:core:data` defines the interface. `AndroidResetNotifier` offers resets to it before posting notifications.
  When the island displays a reset, the notifier uses a quiet channel to avoid a second pop-up.
  `:core:data` has no dependency on `:app`.
- `AppResetIsland` implements that interface in `:app`.
  `IslandConditions.blockedBy()` blocks display when disabled, unavailable, screen-off, landscape, foreground or Do Not Disturb conditions apply.
  `Overlay` mode also blocks display on locked devices.
- `resolveIslandMode()` selects `Accessibility` when the service connects, then `Overlay` if `SYSTEM_ALERT_WINDOW` permission allows it.
  Otherwise it selects `None`, and Headroom uses a heads-up notification.
- `IslandHub` carries in-process requests. `ResetIslandService` reports its connection and collects requests.
  This accessibility service requests no events or window content. Its overlay can draw above the status bar, notification shade and lock screen.
- `islandGeometry()` calculates the pill geometry from the camera cutout and screen size without side effects.
- `AndroidIslandOverlay` supports devices whose administrators block accessibility services.
  It creates a `TYPE_APPLICATION_OVERLAY` window from a window context, sized by `overlayIslandGeometry()` to match the pill.
  This window draws below the status bar and cannot appear on the lock screen.
  The window accepts touch because Android limits untouchable overlays to 80% alpha, which makes black look grey.
  Tapping opens Headroom; swiping up dismisses the pill.
- A watchdog removes the overlay a few seconds after the longest animation can end.
  Android also removes windows when their process dies. The overlay must not remain after its animation.
- A WorkManager worker checks resets. Android can freeze background processes while the pill remains visible.
  `AndroidResetNotifier` posts the notification, then waits for `ResetIsland.awaitIdle()` to confirm overlay removal.
  The worker coroutine keeps the process active during that wait, about five seconds.

## Display glasses (experimental)

While display glasses are connected, the overview shows a glasses button next to Settings.
`GlassesLauncher` watches the connection (`ProjectedContext.isProjectedDeviceConnected`) and starts
`GlassesActivity` on the glasses' display (`ProjectedContext.createProjectedActivityOptions`). The
manifest gives the activity `requiredDisplayCategory="xr_projected"`, so it never opens on the
phone's screen. The projected library needs Android 16, so on older versions `glassesLauncher()`
returns `GlassesLauncher.Unavailable` and the button never shows.

The activity reads the same `HomeViewModel` as the overview, so the glasses show the same accounts
in the same order, with the same used or left setting. `glassesPages` turns the state into pages:
the next reset first, then one page per account. The UI is Jetpack Compose Glimmer, not Material:
a `GlimmerHorizontalPager` of one card at a time, a `Card` for the next reset and a
`LeadingImageCard` with the provider's logo for each account. The background is black, which the
glasses' additive display draws as see-through. System back closes the activity.

Each logo has the colour of its provider's avatar in the app's dark theme, with the palette chosen
in the app's settings (`ProviderPalette`, harmonised with that palette's primary colour). The dark
theme's light tones read best on the black background, so the glasses use them whatever the phone's
theme is.

`GlassesPreviews.kt` has Compose previews on Android Studio's "Display Glasses" device
(`id:ai_glasses_device`, 450 x 450 px at mdpi). The Roborazzi screenshots in `docs/screenshots` use
the same size.

Glimmer and the projected library are alpha releases, and the projected launch and connection
calls are marked experimental.

## XR headsets (Full Space)

On an Android XR headset the app opens in Full Space
(`android.window.PROPERTY_XR_ACTIVITY_START_MODE` in the manifest). It uses Jetpack Compose for XR
from the `ui.spatial` package. Everywhere else, including a headset's Home Space,
`isSpatialUiEnabled()` is false and the app is the same adaptive app as on any device.

- `SpatialPanels` in `HeadroomApp` puts the window in a `SpatialMainPanel` and, beside it, a
  `SpatialPanel` for the account detail. The user can move and resize both. It lives above the
  pages, so the main panel stays still while Settings, Accounts and Licences replace the home.
- The main panel takes the window's size, so `layoutWidth()` keeps picking the layout. The
  activity handles its own size changes (`configChanges`), as `SpatialMainPanel` needs.
- The Overview tab hands its detail to a `DetailPanelSlot` (`DetailPanelContent`) while it is
  composed. The list fills the main panel, and opening an account only selects it. The Resets tab
  opens accounts in the Overview, as it does in an expanded window. Resets, Stats and the other
  pages have no detail panel.
- `HomeNavigationOrbiter` replaces the floating toolbar or the navigation rail with an `Orbiter`
  on the same edge of the main panel: the bottom when compact, the start otherwise
  (`navigationEdge()`).

The tests use Robolectric and the fake XR runtime (`FullSpaceTest`). Two quirks of that setup:

- An orbiter or a panel that leaves the composition keeps its semantics under Robolectric, so
  tests check what is inside or outside the window (`APP_TAG`), or the subspace nodes, rather than
  the absence of a node.
- `Espresso.pressBack()` reaches another window. Tests call the activity's
  `onBackPressedDispatcher` instead.

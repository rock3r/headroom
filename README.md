# Headroom

Headroom shows your remaining AI subscription quotas on Android. It supports Claude, ChatGPT Codex,
GitHub Copilot, Grok, Z.AI, Kimi Code, OpenCode Go, JetBrains AI and Muse.
Home screen and lock screen widgets show your usage, and reset alerts notify you when a weekly limit resets.

A SwiftUI app in [`iosApp/`](iosApp/README.md) shares the same Kotlin Multiplatform quota, sign-in and storage logic on iOS 18 and later.
Headroom is free software under the [Apache License 2.0](LICENSE).

<p align="center"><a href="https://play.google.com/store/apps/details?id=dev.sebastiano.headroom"><img src="docs/media/badge_google_play.png" alt="Google Play" height="80"></a></p>

<p align="center">
  Or install the APK from <a href="https://github.com/rock3r/headroom/releases/latest">GitHub releases</a> or with <a href="#with-obtainium">Obtainium</a>.
  The Google Play version has <a href="#google-play-differences">two policy differences</a>.
</p>

<p align="center"><img src="docs/media/demo.gif" alt="Headroom demo" width="300"></p>

The recording is also available as a [video](docs/media/demo.mp4). It shows demo data.

> [!IMPORTANT]
> Headroom reads usage from undocumented endpoints. It signs in with the OAuth clients used by
> each provider's command-line tools or with your API key. Providers can change or block access at any time.
> Their terms may prohibit this access. Headroom targets personal use and has no affiliation with these providers.

## Screenshots

| Overview | Account detail | Resets | Stats |
|---|---|---|---|
| ![Overview](docs/screenshots/overview.png) | ![Detail](docs/screenshots/detail.png) | ![Resets](docs/screenshots/resets.png) | ![Stats](docs/screenshots/stats.png) |

| Dark theme | Accounts | Sign-in with a device code |
|---|---|---|
| ![Overview, dark](docs/screenshots/overview-dark.png) | ![Accounts](docs/screenshots/accounts.png) | ![Device code](docs/screenshots/sign-in-device-code.png) |

| Settings | A colour palette | Refresh shimmer | Reset confetti |
|---|---|---|---|
| ![Settings](docs/screenshots/settings.png) | ![Grape palette, dark](docs/screenshots/overview-grape-dark.png) | ![Refresh shimmer](docs/screenshots/refresh-shimmer-dark.png) | ![Reset confetti](docs/screenshots/reset-confetti.png) |

The experimental reset island expands from the camera cutout when a limit resets while Headroom runs
in the background. It shows the provider logo and a ring that refills.
A letter identifies the account when you have several accounts with that provider:

![Reset island](docs/screenshots/reset-island-badge.png)

Tablets and unfolded foldables show the account list beside the selected account's details:

![Expanded layout](docs/screenshots/expanded.png)

When a sign-in or API key expires, Headroom fades the account's last usage values and shows their age.
The account offers a button to sign in again or replace the key. Headroom posts one warning notification and removes it after a successful sync:

| Overview | Account detail | Widget | Notification |
|---|---|---|---|
| ![Expired sign-in, overview](docs/screenshots/expired-sign-in-overview.png) | ![Expired sign-in, detail](docs/screenshots/expired-sign-in-detail.png) | ![Expired sign-in, widget](docs/screenshots/widgets/widget-expired-bars.png) | ![Expired sign-in, notification](docs/screenshots/expired-sign-in-notification.png) |

### Widgets

The launcher draws each widget from a Remote Compose document.
Bars and Rings widgets also show a counter badge when an account has usable resets.
These images come from the Android 16 widget player:

| Rings, one account | Rings, several accounts | Shape |
|---|---|---|
| ![Rings widget](docs/screenshots/widgets/widget-rings.png) | ![Rings grid](docs/screenshots/widgets/widget-rings-grid.png) | ![Shape widget](docs/screenshots/widgets/widget-shape.png) |

| Bars | Lock screen |
|---|---|
| ![Bars widget](docs/screenshots/widgets/widget-bars.png) | ![Lock screen widget](docs/screenshots/widgets/widget-lock-screen.png) |

## Features

### Quotas, pace and history

- The overview shows each account's weekly or monthly usage, session usage, and difference from an even usage rate.
  Drag accounts to set their order, or sort them by usage or next reset.
- A tick on each bar marks the even usage rate. Bars become wavy when the account exceeds that rate or reaches 85% usage, and stay flat otherwise.
- Account details plot weekly and monthly windows together over time.
  The chart marks past resets and shades the primary window's current period with its even-pace line and projection.
  It supports pan, pinch-zoom, scrubbing tooltips, window filter chips and `Period` / `1M` / `3M` / `1Y` range presets.
- By default, Headroom keeps a year of history.
  It keeps every sync for 7 days, then compacts points into 1-hour, 3-hour and 12-hour buckets across the year.
  Turning off "Keep long-term history" in Settings keeps 60 days at full resolution.
- One-time credits show usage and expiry dates, including Claude's cloud session credit.
  Headroom also displays unfamiliar quotas and labels them as unrecognised.
- With no signed-in account, Headroom displays labelled demo data.

### Resets and alerts

- Provider-issued resets refill limits early. Headroom lists each reset and its expiry date for ChatGPT Codex, Grok, Claude and Z.AI.
- The confirmation sheet refills the affected bars after a successful reset and shows the remaining reset count.
  Claude's confirmation notes that its reset API is unpublished. Z.AI resets use a separate ZCode sign-in and let you ask Z.AI for new reset cards.
  See [Usage-limit resets](docs/RESETS.md).
- Headroom schedules an alarm for each weekly window. It fetches usage when the alarm fires and posts a heads-up notification after confirming the reset.
  Session and daily limits never trigger alerts, and monthly alerts require opt-in. Each alert has a bell switch in the Resets tab.
- Reset reminders arrive about a day before an available reset expires, with at most one reminder daily.
  For example, a reminder says "Your ChatGPT Codex reset expires tomorrow at 06:18".
  Tapping it opens the account's resets. Settings can disable reminders.
- The experimental reset island expands from the camera cutout when a limit resets while Headroom runs in the background.
  It uses an accessibility service that reads no screen content.
  Devices that block that service can use the Display over other apps permission. Settings explains each setup path.

### Expired sign-ins and API keys

- Expired sign-ins and expired or rejected API keys keep their last values, history, names and alerts.
  Headroom shows the values' age and posts one warning notification until the next successful sync.
  Expired accounts cannot supply the next reset or trigger reset alerts.
- OAuth and device-code accounts offer a "Sign in" action.
  API-key accounts state whether the key expired or was rejected and offer "Replace API key".
  Headroom validates a submitted API key with a quota fetch before saving it.

### Widgets, Quick Settings and Stats

- Home screen and lock screen widgets offer Rings, Bars, Shape and Countdown styles.
  Each widget saves its accounts, window and colours, and Bars and Rings widgets show a counter badge for usable resets.
  Settings can add any style to the home screen.
- The Quick Settings tile opens Headroom and shows the next reset or the quota nearest its limit.
  Long-pressing the tile opens its options in Headroom's Settings.
- Stats uses on-device history to show reset cycles completed without hitting a limit, each provider's share of usage, and a 31-day usage-by-hour heatmap.
  It also shows peak single-day usage, unused quota at reset, a seven-day line per account, and used or expired resets from the last year.

### Appearance, motion and layout

- In Settings, you can choose used or remaining percentages, the background sync interval, and a light, dark or system theme.
  You can match your wallpaper colours or pick one of eight fixed palettes.
  Theme changes expand from the tap location, and reduced motion replaces moving transitions with short fades.
- Optional effects include a rainbow shimmer with sparkles after updates and confetti after a reset while the app is open.
  Each effect has a Try button and an off switch.
- Phones use a floating toolbar. Foldables and tablets use a navigation rail, and wide screens show the account list beside the selected account's details.

## Signing in

Headroom follows each provider's own sign-in flow.
On Android, it encrypts tokens with an Android Keystore key and excludes them from backups.

| Provider | How you sign in |
|---|---|
| Claude | Browser sign-in. The page returns to the app; if it cannot, paste the code the page shows. |
| ChatGPT Codex, Grok, JetBrains AI | Browser sign-in, returning to the app. |
| GitHub Copilot, Kimi Code, Muse | Device code: enter a short code on the provider's page. |
| Z.AI, OpenCode Go | API key, checked with a quota fetch before saving. Z.AI resets use a separate ZCode browser sign-in. |

## Installing

Headroom requires Android 16 (API 36) or later. Google Play and GitHub releases use the same signing key, so you
can switch between them. An update from either source installs over the other and keeps your
accounts and history. [docs/RELEASING.md](docs/RELEASING.md) describes the release process.

### From Google Play

Google Play installs Headroom and delivers updates automatically:

[<img src="docs/media/badge_google_play.png" alt="Google Play" height="64">](https://play.google.com/store/apps/details?id=dev.sebastiano.headroom)

<a id="google-play-differences"></a>

> [!NOTE]
> Google Play policy requires two changes in the Play version. Everything else is the same.
> The APK from GitHub releases or Obtainium schedules exact reset alarms and includes the reset island's accessibility service. See [docs/PLAY-STORE.md](docs/PLAY-STORE.md).

| Feature | Google Play version |
|---|---|
| Reset alerts | Play rejects `USE_EXACT_ALARM` for this kind of app, so the Play version uses the "Alarms & reminders" permission instead. Allow that permission in Android's app settings for on-time alerts; without it, alerts use an inexact alarm and can arrive a few minutes late. |
| Reset island | The Play version omits the accessibility service and offers only Display over other apps. In that mode, the island draws below the status bar and cannot appear on the lock screen. |

### From GitHub releases

Download the APK from the [latest release](https://github.com/rock3r/headroom/releases/latest) and open it on your phone.

### With Obtainium

[Obtainium](https://obtainium.imranr.dev/) installs Headroom from GitHub releases and reports available updates.

[<img src="docs/media/badge_obtainium.png" alt="Get it on Obtainium" width="161">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22dev.sebastiano.headroom%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Frock3r%2Fheadroom%22%2C%22author%22%3A%22rock3r%22%2C%22name%22%3A%22Headroom%22%7D)

The badge opens apps.obtainium.imranr.dev. That site offers Obtainium installation when the app is missing.
If you have Obtainium, you can add Headroom with this URL:

```
https://github.com/rock3r/headroom
```

The APK signing certificate has this SHA-256 fingerprint:

```
81:5F:AA:26:1D:76:B2:B3:37:E4:AB:D0:F3:BA:31:84:AD:05:FB:8E:BD:65:01:42:43:47:22:69:BC:DA:5F:C2
```

## Building

Install JDK 21 and Android SDK platform 37 before building the Android app:

```bash
./gradlew :app:installDebug
```

The Android app runs on Android 16 (API 36) or later.
For the SwiftUI iOS app (iOS 18 or later), see [iosApp/README.md](iosApp/README.md).

## Project layout

| Module | What it holds |
|---|---|
| `:core:model` | Quota types, pace maths, reset rules, repository contracts, demo data |
| `:core:quota` | One fetcher and reset client per provider |
| `:core:auth` | Sign-in flows, token refresh, token store contract |
| `:core:storage` | Room database (accounts, snapshots, history, reset events), DataStore settings, shared sign-in flow |
| `:core:data` | Encrypted token store, sync worker, reset alarms, notifications, Android wiring |
| `:shared` | `HeadroomKit` Kotlin Multiplatform framework and view models for the iOS app |
| `:core:designsystem` | Theme, type, quota indicators, provider avatars |
| `:third-party:compose-charts` | Vendored `compose-charts` library for the detail usage history chart |
| `:widget` | Remote Compose widgets for the home screen and lock screen |
| `:app` | Android screens, navigation, adaptive layouts, dependency wiring |
| [`iosApp/`](iosApp/README.md) | SwiftUI iOS app, WidgetKit widgets, Control Center control and Live Activity |

`:core:model`, `:core:quota`, `:core:auth`, `:core:storage` and `:shared` use Kotlin Multiplatform and share business logic between Android and iOS.
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes the modules and their data flow.

## Tests and quality checks

Contributors write tests before production code, and `./gradlew check` must pass with zero findings.
It runs JVM, Android Robolectric, Compose UI, and macOS iOS simulator tests.
It also runs detekt with Compose Rules, ktfmt in KotlinLang style, and Android Lint with warnings as errors.

| Command | What it does |
|---|---|
| `./gradlew check` | Everything above. This is the gate for every commit and for CI. |
| `./gradlew :app:pixel9api37DebugAndroidTest` | End-to-end tests on a Gradle-managed Pixel 9 emulator with API 37, using demo data. |
| `iosApp/scripts/test.sh` | Generates the Xcode project and runs the iOS unit and UI tests on macOS. |
| `./gradlew :app:recordRoborazziDebug` | Records the app screenshots in `docs/screenshots/`. |
| `./gradlew :widget:recordWidgetGallery` | Records the widget pictures in `docs/screenshots/widgets/`. |
| `./gradlew ktfmtFormat` | Formats the Kotlin code. |

GitHub Actions runs `check` for pull requests and `main`.
The path filter skips tests, including end-to-end tests, for documentation-only changes
([.github/workflows/ci.yml](.github/workflows/ci.yml)).

The project guides cover [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md),
[static analysis](docs/STATIC-ANALYSIS.md), [conventions](docs/CONVENTIONS.md),
[tracing](docs/TRACING.md), [releasing](docs/RELEASING.md) and [Google Play](docs/PLAY-STORE.md).

## Known limitations

- Real-account testing covers one Pixel phone. Automated tests use recorded responses and a local test server.
  Provider response changes can break a fetcher without failing those tests.
- Codex, Claude, Z.AI and Grok resets follow the providers' clients. Codex, Claude and Z.AI redemption passed real-account checks; Grok redemption remains untested with a real account.
  [RESETS.md](docs/RESETS.md) records those checks. Use `adb logcat -s HeadroomResets` to inspect requests without tokens or account details.
- Remote Compose remains alpha. Widgets pin one library version and use restricted APIs for live countdowns.
- Widget checks use the Android 16 widget player under Robolectric. Device-launcher checks remain incomplete.
  Lock screen widgets and the tap-to-flip ring have no hardware test results.
- Android can stop Headroom during browser sign-in and cancel the flow. Restart sign-in if that happens.
- Some work profiles and device policies block the reset island's accessibility service.
  The Display over other apps fallback works on those devices.
  Status bar icons draw above that overlay, and the overlay cannot appear on the lock screen.

## Credits

Most provider logos come from [models.dev](https://models.dev)
([sst/models.dev](https://github.com/sst/models.dev), MIT licence).
The JetBrains logo comes from [Simple Icons](https://simpleicons.org) (CC0), with adaptations.
The logos remain their owners' trademarks. Headroom uses them to identify account providers.

## Licence

Copyright 2026 Sebastiano Poggi.

Headroom uses the Apache License, Version 2.0. [LICENSE](LICENSE) contains the full terms.
Provider logos have separate terms, listed in Credits.

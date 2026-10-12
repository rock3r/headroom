# Headroom for iOS

A native SwiftUI app on the same Kotlin code as the Android app: the quota fetchers, sign-in,
storage and sync come from the `HeadroomKit` framework, which the `:shared` module builds. See
[ARCHITECTURE.md](../docs/ARCHITECTURE.md#kotlin-multiplatform).

## Building

You need Xcode 27, JDK 21 and [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`).

```bash
cd iosApp
xcodegen          # writes Headroom.xcodeproj, which is not checked in
open Headroom.xcodeproj
```

The app target's first build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so
Xcode builds the Kotlin framework for the right configuration and simulator or device by itself.

To run on a device, create `iosApp/Local.xcconfig`, which git ignores, with your team and a bundle
id prefix of your own, then run `xcodegen` again:

```
DEVELOPMENT_TEAM = ABCDE12345
BUNDLE_ID_PREFIX = com.example
```

Bundle ids and App Groups belong to the team that registered them first, so `dev.sebastiano`, the
default in `Signing.xcconfig`, only signs with its owner's team. The app becomes `<prefix>.headroom`,
the widgets `<prefix>.headroom.widgets`, and the App Group they share `group.<prefix>.headroom`. Run
it from Xcode the first time: automatic signing registers all three with your account, which
`xcodebuild` on the command line cannot do.

## Testing

```bash
iosApp/scripts/test.sh
```

It generates the project and runs the unit tests (`HeadroomTests`: the SVG path parser and the
Keychain store) and the UI tests (`HeadroomUITests`: the demo overview, the tabs, the detail screen,
Settings, and the sign-in screens, which stop before anything reaches a provider). The shared
Kotlin code has its own tests, which `./gradlew check` runs on the JVM and the iOS simulator.

## How it fits together

- `AppModel` opens Headroom with its files in Application Support and the sign-ins in the Keychain
  (`KeychainStore`, available after first unlock, this device only). It mirrors HeadroomKit's
  overview, settings, stats, Resets tab and the sign-in and redeem steps into observable state.
- The tabs are those of the Android app: Overview, Resets and Stats. On a wide screen they move to
  a sidebar, as the Android navigation rail, and the overview shows the selected account beside the
  list. Settings opens from the overview.
- The screens follow the Android ones: the cards' big number, bars, session bar and pace chip; the
  next reset card with its alert; the detail's ring, windows, amounts, pace chart, reset alerts and
  resets card. The redeem sheet runs HeadroomKit's redeem session, the same as on Android: one key
  per attempt, so no path uses two resets. Its bars show what a reset clears and refill when it
  worked. Z.AI's ZCode sign-in opens in the same browser sheet as the others.
- The delights are the Android ones: the refresh shimmer, the reset confetti and the gloss after a
  redeem. HeadroomKit's `watchDelights` says when, with the Android reset tracker, so each plays
  once; nothing plays while motion is reduced, in Settings or on the device.
- `headroom://` links open an account (`account/<id>`), its resets (`account/<id>/resets`), its
  sign-in (`signin/<id>`) or a tab (`resets`, `stats`). The widgets and notifications use them; debug
  builds also take `-openURL <link>` at launch, for UI tests and screenshots.
- Settings > Open-source licences lists the libraries HeadroomKit is built with. The build runs
  AboutLibraries' `:shared:exportLibraryDefinitions` and copies the result into the app.
- Sign-in runs the same steps as on Android. A browser sign-in opens the provider's page in
  `ASWebAuthenticationSession` (`BrowserSignIn`), never in Safari: Safari would put the app in the
  background, iOS would suspend it, and the loopback listener that receives the redirect would stop
  answering.
- Text lives in the app, worded as on Android; HeadroomKit sends ids such as `"network"`
  (`Texts`).
- Background refresh asks iOS to sync no sooner than the frequency chosen in Settings. iOS decides
  when it actually runs.
- `AppServices` follows the accounts and the settings, as Android's workers and alarms do:
  - Notifications come from HeadroomKit's plan (`NotificationScheduler`): an alert when a weekly or
    monthly limit resets, with "Open" and "Mute <account>" actions; a reminder a day before a reset
    expires, which opens the account at its resets; and a warning when a sign-in expires. iOS cannot wake the app at the reset to check that the
    limit really reset, as Android's exact alarms do, so the alerts fire at the reset time the
    provider announced. Permission is asked once there is a real account.
  - The widgets (`HeadroomWidgets`) read a snapshot the app writes to the App Group after every
    change. They are the Android styles, Rings, Bars, Shape and Countdown, plus a Lock Screen
    widget; each can be set to show chosen accounts. A tap opens the account, or its sign-in when
    that expired. The Control Center control is iOS's take on the Quick Settings tile: the next
    reset or the tightest quota, as chosen in Settings. Widgets cannot sync by themselves, so they
    show the numbers of the app's last sync.
  - The reset Live Activity is iOS's take on the reset island: with "Reset Live Activity" on in
    Settings, a reset less than eight hours away counts down on the Lock Screen and in the Dynamic
    Island, and says so when it happened. A Live Activity can last eight hours, hence the limit.
- Code the widgets need too (the snapshot, the Live Activity's attributes, provider colours and
  logos) is in `Shared`, which both targets compile.
- The app icon is rendered from the Android launcher icon's design by
  `scripts/render-app-icon.swift`.

## Differences from Android

What the platforms do differently, rather than what is missing:

- Reset alerts fire at the reset time the provider announced; iOS cannot wake the app to check that
  the limit really reset, as Android's exact alarms do.
- The reset island is a Live Activity, which iOS ends after eight hours, so it starts once the
  reset is that close.
- iOS lets only the user add widgets and controls: Settings says how, instead of pinning them.
- Background updates run when iOS allows, no sooner than the frequency chosen in Settings.

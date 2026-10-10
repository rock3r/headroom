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

To run on a device, set two values in `project.yml`, then run `xcodegen` again:

- `DEVELOPMENT_TEAM`: your team id.
- `BUNDLE_ID_PREFIX`: a prefix of your own, such as `com.example`. Bundle ids and App Groups belong
  to the team that registered them first, so `dev.sebastiano` only signs with its owner's team. The
  app becomes `<prefix>.headroom`, the widgets `<prefix>.headroom.widgets`, and the App Group they
  share `group.<prefix>.headroom`. Xcode's automatic signing registers all three.

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
- The tabs are those of the Android app: Overview, Resets and Stats. Settings opens from the
  overview. The detail screen has the pace chart, the reset alert switches and the resets card,
  whose redeem sheet runs HeadroomKit's redeem session, the same as on Android: one key per attempt,
  so no path uses two resets. Z.AI's ZCode sign-in opens in the same browser sheet as the others.
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
    monthly limit resets, with a "Mute this limit" action; a reminder a day before a reset expires;
    and a warning when a sign-in expires. iOS cannot wake the app at the reset to check that the
    limit really reset, as Android's exact alarms do, so the alerts fire at the reset time the
    provider announced. Permission is asked once there is a real account.
  - The widgets (`HeadroomWidgets`) read a snapshot the app writes to the App Group after every
    change: usage rings and a countdown for the Home Screen, gauges and text for the Lock Screen,
    and the Next reset control for Control Center, iOS's take on the Quick Settings tile. Widgets
    cannot sync by themselves, so they show the numbers of the app's last sync.
  - The reset Live Activity is iOS's take on the reset island: with "Reset Live Activity" on in
    Settings, a reset less than eight hours away counts down on the Lock Screen and in the Dynamic
    Island, and says so when it happened. A Live Activity can last eight hours, hence the limit.
- Code the widgets need too (the snapshot, the Live Activity's attributes, provider colours and
  logos) is in `Shared`, which both targets compile.
- The app icon is rendered from the Android launcher icon's design by
  `scripts/render-app-icon.swift`.

## Not there yet

Compared with the Android app, the iOS app has no refresh shimmer or reset confetti, so Settings
does not offer them, and no open-source licences screen. Its widgets are not configurable: they show
every account, in the overview's order.

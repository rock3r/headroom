# Headroom for iOS

The iOS app uses SwiftUI and shares Kotlin business logic with Android.
The `:shared` module builds `HeadroomKit`, which supplies quota fetching, sign-in, storage and sync. See
[ARCHITECTURE.md](../docs/ARCHITECTURE.md#kotlin-multiplatform).

## Building

Install Xcode 27, JDK 21 and [XcodeGen](https://github.com/yonaskolb/XcodeGen) before building.
Install XcodeGen with `brew install xcodegen`.

```bash
cd iosApp
xcodegen          # writes Headroom.xcodeproj, which is not checked in
open Headroom.xcodeproj
```

The app target first runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`.
Xcode uses that phase to build the Kotlin framework for the selected configuration and destination.

For a device build, create the git-ignored file `iosApp/Local.xcconfig` with your team and bundle ID prefix.
Then run `xcodegen` again:

```
DEVELOPMENT_TEAM = ABCDE12345
BUNDLE_ID_PREFIX = com.example
```

Apple assigns bundle IDs and App Groups to the team that first registers them.
The default `dev.sebastiano` prefix in `Signing.xcconfig` requires its owner's team.
Your prefix produces `<prefix>.headroom`, `<prefix>.headroom.widgets` and the shared App Group `group.<prefix>.headroom`.
Run the first device build from Xcode. Automatic signing registers those identifiers with your account; command-line `xcodebuild` cannot register them.

## Testing

```bash
iosApp/scripts/test.sh
```

The script generates the project and runs `HeadroomTests` and `HeadroomUITests`.
Unit tests cover the SVG path parser and Keychain store.
UI tests cover the demo overview, tabs, account details, Settings and sign-in screens.
Sign-in tests stop before contacting a provider.
`./gradlew check` separately tests the shared Kotlin code on the JVM and iOS simulator.

## How it fits together

- `AppModel` stores files in Application Support and sign-ins in the Keychain.
  `KeychainStore` limits secrets to this device and makes them available after the first unlock.
  `AppModel` exposes observable state for HeadroomKit's overview, settings, stats, resets, sign-in and redemption steps.
- Overview, Resets and Stats match the Android tabs. Wide screens use a sidebar and show the selected account beside the overview list.
  Settings opens from the overview.
- Account cards show the main number, quota bars, session bar and pace chip. The next-reset card includes its alert control.
  Account details include a ring, windows, amounts, pace chart, reset alerts and available resets.
- The redemption sheet uses HeadroomKit's shared session. Each attempt uses one key to prevent duplicate reset use.
  Bars identify the limits that the reset clears and refill after success.
  ZCode sign-in for Z.AI opens in the browser sheet used by other sign-ins.
- `watchDelights` uses the shared reset tracker to trigger refresh shimmer, reset confetti and post-redemption gloss once each.
  Reduced motion in the app or device settings disables those effects.
- Widgets and notifications use `headroom://` links.
  Routes open an account (`account/<id>`), its resets (`account/<id>/resets`), sign-in (`signin/<id>`) or a tab (`resets`, `stats`).
  Debug builds also accept `-openURL <link>` at launch for UI tests and screenshots.
- Settings > Open-source licences lists HeadroomKit's libraries.
  The build runs AboutLibraries' `:shared:exportLibraryDefinitions` and copies its output into the app.
- Sign-in follows the Android steps. `BrowserSignIn` opens provider pages in `ASWebAuthenticationSession`.
  Opening Safari would background Headroom. iOS would suspend the app and stop its loopback redirect listener from answering.
- The app owns display text and matches Android wording. HeadroomKit's `Texts` sends identifiers such as `"network"`.
- Background refresh requests the frequency selected in Settings. iOS chooses the actual run time and never runs before that interval.
- `AppServices` observes accounts and settings to manage notifications, widgets and Live Activities.
  - `NotificationScheduler` supplies the notification plan. Weekly and monthly alerts offer "Open" and "Mute <account>" actions.
    Expiry reminders arrive a day before a reset expires and open the account's resets. Sign-in expiry triggers a warning.
    iOS cannot wake Headroom at reset time to confirm usage. Alerts use the provider's announced reset time.
    The app asks for permission after the user adds a real account.
  - `HeadroomWidgets` reads snapshots that the app writes to the App Group after every change.
    Rings, Bars, Shape, Countdown and Lock Screen widgets can show selected accounts.
    Tapping opens the account or its expired sign-in. Widgets cannot sync independently and display the app's last synced values.
    The Control Center control shows the next reset or quota nearest its limit, according to Settings.
  - Enabling "Reset Live Activity" starts a countdown when a reset is less than eight hours away.
    The Lock Screen and Dynamic Island show the countdown and reset status. iOS limits Live Activities to eight hours.
- Both targets compile `Shared`, which holds snapshots, Live Activity attributes, provider colours and logos.
- `scripts/render-app-icon.swift` renders the app icon from the Android launcher design.

## Differences from Android

Platform rules affect alerts, widgets and background work:

| Feature | iOS behaviour |
|---|---|
| Reset alerts | Alerts use the announced reset time because iOS cannot wake the app to confirm usage. |
| Reset island | A Live Activity starts within eight hours of reset and ends within iOS's eight-hour limit. |
| Widgets and controls | Users add them through iOS. Settings gives instructions. |
| Background updates | iOS chooses the run time after the interval selected in Settings. |

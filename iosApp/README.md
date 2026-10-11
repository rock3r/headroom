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
To run on a device, set your team in `project.yml` (`DEVELOPMENT_TEAM`) or in Xcode.

## Testing

```bash
iosApp/scripts/test.sh
```

It generates the project and runs the unit tests (`HeadroomTests`: the SVG path parser and the
Keychain store) and the UI tests (`HeadroomUITests`: the demo overview and the sign-in screens, which
stop before anything reaches a provider). The shared Kotlin code has its own tests, which
`./gradlew check` runs on the JVM and the iOS simulator.

## How it fits together

- `AppModel` opens Headroom with its files in Application Support and the sign-ins in the Keychain
  (`KeychainStore`, available after first unlock, this device only). It mirrors HeadroomKit's
  overview and sign-in step into observable state.
- Sign-in runs the same steps as on Android. A browser sign-in opens the provider's page in
  `ASWebAuthenticationSession` (`BrowserSignIn`), never in Safari: Safari would put the app in the
  background, iOS would suspend it, and the loopback listener that receives the redirect would stop
  answering.
- Text lives in the app, worded as on Android; HeadroomKit sends ids such as `"network"`
  (`Texts`).
- Background refresh asks iOS to sync at most every 15 minutes, as the Android default.
- The app icon is rendered from the Android launcher icon's design by
  `scripts/render-app-icon.swift`.

## Not there yet

Compared with the Android app, the iOS app has no widgets, no reset notifications or alerts, no
reset island (a Live Activity would be the iOS take), no stats tab, no settings and no resets
screen. The shared code already has the data and the policies for them.

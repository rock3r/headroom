# Google Play

Headroom ships on Google Play as `dev.sebastiano.headroom`, from Marco Gomiero's developer
account, next to the GitHub APK. This page holds everything the Play Console asks for that is not
in the store listing files.

## Where things live

| What | Where |
|---|---|
| Store listing text, contact details, release notes | `app/src/main/play/` (Gradle Play Publisher layout) |
| Icon, feature graphic, phone and tablet screenshots | `app/src/main/play/listings/en-US/graphics/` |
| The script that draws the graphics | [`scripts/store-graphics/render.sh`](../scripts/store-graphics/render.sh) |
| Privacy policy | `https://www.prof18.com/headroom/pp/privacy-policy-oct26/` (source in the prof18.com site repo) |
| Service account for uploads | `~/.local-config/headroom_play_config.json` locally, the `PLAY_SERVICE_ACCOUNT_JSON` secret in CI; never in this repo |

The graphics are built from the README screenshots, so they always show demo data. After a UI
change, record the screenshots again and redraw the graphics:

```bash
./gradlew :app:recordRoborazziDebug :widget:recordWidgetGallery
scripts/store-graphics/render.sh
```

The listing text and the screenshot captions stay generic about providers on purpose: only the
full description names them, so adding a provider means editing one paragraph.

## Policy notes for the Play build

The Play build is the `play` build type in `app/build.gradle.kts`: the release build, signed the
same way, with its own manifest in `app/src/play/AndroidManifest.xml`. Build the bundle with:

```bash
./gradlew :app:bundlePlay
```

The bundle lands in `app/build/outputs/bundle/play/app-play.aab`. Without the `HEADROOM_KEY*`
variables it is unsigned, and Play refuses it.

Two things in the GitHub build would not pass Play review, so the Play build leaves them out:

- **`USE_EXACT_ALARM`** is only for alarm clock and calendar apps. The Play build declares
  `SCHEDULE_EXACT_ALARM` instead. `AlarmResetScheduler` already falls back to an inexact alarm
  when `canScheduleExactAlarms()` is false, so reset alerts may arrive a few minutes late unless
  the user allows exact alarms.
- **The reset island's accessibility service.** Play only accepts the Accessibility API for
  accessibility tools, or with a declaration, an in-app disclosure and a video, and it refuses
  most other uses. The Play build drops the service and keeps the Display over other apps
  fallback. The set-up sheet in Settings notices that the service is missing and offers only
  Display over other apps.

## Store settings

| Setting | Value |
|---|---|
| App or game | App |
| Free or paid | Free (cannot change to paid later) |
| Category | Productivity |
| Tags | Productivity, Tools, Developer tools (pick the closest available) |
| Contact email | mgp.dev.studio@gmail.com |
| Website | https://github.com/rock3r/headroom |
| Countries | All |
| Devices | Phones, tablets, foldables. Android 16 (API 36) and later. |

## App access (instructions for the reviewer)

Choose **All or some functionality is restricted**, and add one entry with no username or
password:

> **Name:** Demo mode (no sign-in needed)
>
> **Instructions:**
>
> Headroom shows the usage limits of paid AI subscriptions (for example Claude, ChatGPT Codex and
> GitHub Copilot). Signing in needs a paid subscription with one of those third-party services,
> so we cannot share a test account. Instead, every feature can be reviewed in demo mode:
>
> 1. Install and open the app. With no account signed in, it starts in demo mode with clearly
>    labelled example data ("Demo data" banner at the top). No sign-in is needed.
> 2. Overview: tap any account card to open its detail (usage ring, pace chart, reset alerts).
> 3. Use the bottom toolbar (or the navigation rail on tablets) to open Resets and Stats.
> 4. Tap the refresh button to simulate a sync.
> 5. Tap the gear icon for Settings: theme, colours, widgets, alerts and the Quick Settings tile.
> 6. Widgets: long-press the home screen, choose Widgets, and add any Headroom widget. It shows
>    the demo accounts.
> 7. "Add account" in the banner lists the supported services. Each one opens that service's own
>    sign-in page in the browser, which needs a real subscription; you can back out at any point.
>
> The app has no Headroom account, server, ads or in-app purchases. Notifications are only sent
> when a usage limit resets or a sign-in expires.

## Ads

No, the app does not contain ads. Check that the merged release manifest has no
`com.google.android.gms.permission.AD_ID` before the first upload.

## Content rating (IARC questionnaire)

- Category: **All other app types** (utility, productivity).
- Every question about violence, sexuality, language, controlled substances, gambling and
  horror: **No**.
- Users can interact or exchange content: **No**.
- Shares the user's location: **No**.
- Allows digital purchases: **No**.
- Web browser or search engine: **No**.

Expected rating: Everyone / PEGI 3 / USK 0.

## Target audience and content

- Target age groups: **18 and over** only. The app is for people who pay for AI subscriptions.
- Appeals to children: **No**.

## Data safety

Headroom has no server and no SDKs that send data. It sends sign-in tokens and requests only to
the AI service that the user signed in to, which is the service the user is using, not a third
party that Headroom shares data with.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS only), if Play asks |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected. Removing an account or uninstalling deletes everything. |

If Play review pushes back on "No data collected", the fallback is to declare **Personal info →
Email address** and **App activity → Other actions** as *collected*, *not shared*, *processed
ephemerally: no*, *required: no*, purpose **App functionality**, because the account email and
usage history are received from the provider and stored on the device.

## Other declarations

| Declaration | Answer |
|---|---|
| Privacy policy | `https://www.prof18.com/headroom/pp/privacy-policy-oct26/` |
| Government app | No |
| Financial features | None |
| Health | None |
| News app | No |
| Advertising ID | No |
| Exact alarm permission | Not needed: the Play build uses `SCHEDULE_EXACT_ALARM`, which has no declaration |
| Foreground service permissions | Not needed: only WorkManager's plain `FOREGROUND_SERVICE`, with no typed foreground service |
| Accessibility API | Not needed: the Play build has no accessibility service |

## App signing

Play App Signing uses the existing Headroom release key (see [RELEASING.md](RELEASING.md#the-release-key)),
so an install from Play and one from GitHub or Obtainium can update each other. When the Console
asks for the app signing key on the first upload, choose **Use a different key** → **Export and
upload a key from Java keystore**, and run Google's PEPK tool on the keystore from 1Password with
the encryption key the Console shows. The same key is also the upload key.

## Publishing from CI

The [Release workflow](../.github/workflows/release.yml) uploads the signed Play bundle with
[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher), which is enabled only
for the `play` build type. It needs:

| What | Where |
|---|---|
| The service account key (JSON) | GitHub Actions secret `PLAY_SERVICE_ACCOUNT_JSON`. Gradle Play Publisher reads it from `ANDROID_PUBLISHER_CREDENTIALS`. |
| The track | GitHub Actions variable `PLAY_TRACK` (`internal`, `alpha`, `beta` or `production`). Unset means `production`. |
| The release notes | `app/src/main/play/release-notes/en-US/default.txt`, 500 characters at most |

The service account belongs to Headroom alone. In the Play Console it has access to this app only,
with these permissions: View app information, Release apps to testing tracks, Manage testing
tracks and edit tester lists, Manage store presence, and Release to production, exclude devices,
and use Play App Signing. Without the last one a production upload fails with "The caller does not
have permission"; set `PLAY_TRACK` to `internal` and promote in the Console instead.

The workflow uploads only the bundle and its release notes, never the store listing, so edits
made in the Console stay. To push the listing text and graphics in `app/src/main/play` on purpose,
run this from a machine with the key:

```bash
ANDROID_PUBLISHER_CREDENTIALS="$(cat ~/.local-config/headroom_play_config.json)" \
./gradlew :app:publishPlayListing
```

To upload a bundle by hand, for example to retry a failed upload, build and sign it first (see
[RELEASING.md](RELEASING.md#the-release-key)), then:

```bash
ANDROID_PUBLISHER_CREDENTIALS="$(cat ~/.local-config/headroom_play_config.json)" \
./gradlew :app:publishPlayBundle --track internal --artifact-dir app/build/outputs/bundle/play
```

## First release

Headroom 1.2.0 (version code 3) was the first Play release, uploaded by hand from the `play`
build. Releases after it go through the workflow.

# Google Play

Marco Gomiero's developer account publishes Headroom on Google Play as `dev.sebastiano.headroom`.
GitHub also distributes an APK. This guide covers Console settings outside the store listing files.

## Where things live

| What | Where |
|---|---|
| Store listing text, contact details, release notes | `app/src/main/play/` (Gradle Play Publisher layout) |
| Icon, feature graphic, phone and tablet screenshots | `app/src/main/play/listings/en-US/graphics/` |
| The script that draws the graphics | [`scripts/store-graphics/render.sh`](../scripts/store-graphics/render.sh) |
| Privacy policy | `https://www.prof18.com/headroom/pp/privacy-policy-oct26/` (source in the prof18.com site repo) |
| Service account for uploads | `~/.local-config/headroom_play_config.json` locally, the `PLAY_SERVICE_ACCOUNT_JSON` secret in CI; never in this repo |

Store graphics use the README screenshots and contain only demo data.
After UI changes, record screenshots and regenerate the graphics:

```bash
./gradlew :app:recordRoborazziDebug :widget:recordWidgetGallery
scripts/store-graphics/render.sh
```

Only the full description names providers. Listing text and screenshot captions use general terms.
Adding a provider therefore requires updating one paragraph.

## Policy notes for the Play build

The `play` build type in `app/build.gradle.kts` inherits the release build and its signing setup.
It adds `app/src/play/AndroidManifest.xml`. Build its bundle with:

```bash
./gradlew :app:bundlePlay
```

Gradle writes `app/build/outputs/bundle/play/app-play.aab`.
Without the `HEADROOM_KEY*` variables, Gradle leaves the bundle unsigned. Play rejects unsigned bundles.

The Play build changes two features to meet Play policy:

- Play reserves `USE_EXACT_ALARM` for alarm clock and calendar apps.
  The Play build declares `SCHEDULE_EXACT_ALARM`.
  When `canScheduleExactAlarms()` returns false, `AlarmResetScheduler` uses an inexact alarm.
  Reset alerts can arrive several minutes late unless the user allows exact alarms.
- Play accepts Accessibility API use for accessibility tools or requires a declaration, disclosure and video.
  It rejects most other uses. The Play build removes the reset island's accessibility service and keeps the Display over other apps fallback.
  Settings detects the missing service and offers only Display over other apps.

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

Choose **All or some functionality is restricted**. Add this entry without a username or password:

> **Name:** Demo mode (no sign-in needed)
>
> **Instructions:**
>
> Headroom shows usage limits for paid AI subscriptions, including Claude, ChatGPT Codex and GitHub Copilot.
> Sign-in requires a paid subscription with a provider. We cannot share a test account.
> Demo mode provides example data for reviewing the app:
>
> 1. Install and open Headroom without signing in. The app shows example accounts with a "Demo data" banner.
> 2. Tap an Overview account card to see its usage ring, pace chart and reset alerts.
> 3. Use the bottom toolbar (or the navigation rail on tablets) to open Resets and Stats.
> 4. Tap Refresh to simulate a sync.
> 5. Tap the gear icon to review theme, colours, widgets, alerts and Quick Settings tile options.
> 6. Long-press the home screen, choose Widgets, and add a Headroom widget. It displays demo accounts.
> 7. Tap "Add account" in the banner to list supported services.
>    Each service opens its browser sign-in page and requires a real subscription. You can cancel at any time.
>
> Headroom has no account system, server, ads or in-app purchases.
> Notifications cover usage resets, expiring reset credits and expired sign-ins.

## Ads

Declare that the app has no ads.
Before the first upload, check that the merged release manifest excludes `com.google.android.gms.permission.AD_ID`.

## Content rating (IARC questionnaire)

- Category: **All other app types** (utility, productivity).
- Every question about violence, sexuality, language, controlled substances, gambling and
  horror: **No**.
- Users can interact or exchange content: **No**.
- Shares the user's location: **No**.
- Allows digital purchases: **No**.
- Web browser or search engine: **No**.

These answers are expected to produce Everyone / PEGI 3 / USK 0 ratings.

## Target audience and content

- Target age groups: **18 and over** only. The app is for people who pay for AI subscriptions.
- Appeals to children: **No**.

## Data safety

Headroom operates no server and includes no SDKs that send data.
It sends sign-in tokens and requests only to the AI services selected by the user.
The Data safety answers treat those requests as direct use of the selected service.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS only), if Play asks |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected. Removing an account or uninstalling deletes everything. |

If Play rejects "No data collected", declare the locally stored provider email and usage history with these fields:

| Field | Fallback answer |
|---|---|
| Types | **Personal info → Email address**; **App activity → Other actions** |
| Collected | Yes |
| Shared | No |
| Processed ephemerally | No |
| Required | No |
| Purpose | **App functionality** |

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

Play App Signing uses Headroom's existing [release key](RELEASING.md#the-release-key).
Play, GitHub and Obtainium installs can therefore update each other.
On the first upload, choose **Use a different key** → **Export and upload a key from Java keystore**.
Run Google's PEPK tool on the 1Password keystore with the Console's encryption key.
Use the same key as the upload key.

## Publishing from CI

The [Release workflow](../.github/workflows/release.yml) uploads signed bundles through
[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher).
Only the `play` build type enables that plugin. Uploads require these settings:

| What | Where |
|---|---|
| The service account key (JSON) | GitHub Actions secret `PLAY_SERVICE_ACCOUNT_JSON`. Gradle Play Publisher reads it from `ANDROID_PUBLISHER_CREDENTIALS`. |
| The track | GitHub Actions variable `PLAY_TRACK` (`internal`, `alpha`, `beta` or `production`). Unset means `production`. |
| The release notes | `app/src/main/play/release-notes/en-US/default.txt`, 500 characters at most |

The service account must have access only to Headroom. Grant these Console permissions:

- View app information.
- Release apps to testing tracks.
- Manage testing tracks and edit tester lists.
- Manage store presence.
- Release to production, exclude devices, and use Play App Signing.

Without the last permission, production uploads fail with "The caller does not have permission".
Set `PLAY_TRACK` to `internal` and promote the release in the Console if that permission is unavailable.

The workflow uploads the bundle and release notes. It preserves listing edits made in the Console.
To upload listing text and graphics from `app/src/main/play`, run this command on a machine with the key:

```bash
ANDROID_PUBLISHER_CREDENTIALS="$(cat ~/.local-config/headroom_play_config.json)" \
./gradlew :app:publishPlayListing
```

For a manual upload or retry, first [build and sign the bundle](RELEASING.md#the-release-key).
Then run:

```bash
ANDROID_PUBLISHER_CREDENTIALS="$(cat ~/.local-config/headroom_play_config.json)" \
./gradlew :app:publishPlayBundle --track internal --artifact-dir app/build/outputs/bundle/play
```

## First release

Headroom 1.2.0, version code 3, was the first Play release. Its upload used a manually built `play` bundle.
Later releases use the workflow.

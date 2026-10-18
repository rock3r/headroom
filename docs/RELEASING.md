# Releasing

Pushing a version tag, such as `v1.0.0`, starts the [Release workflow](../.github/workflows/release.yml).
The workflow builds a minified APK and a Play bundle. It signs both with the release key and checks their signatures.
An emulator then runs a smoke test on the APK. A failed smoke test stops publication.
After success, the workflow attaches the APK and SHA-256 checksum to the GitHub release, then uploads the bundle to Google Play.

## Steps

1. Set `versionName` in `app/build.gradle.kts` and increase `versionCode` by one.
   The workflow stops if the tag and `versionName` do not match.
2. Write `docs/release-notes/<version>.md`. If the file is missing, GitHub generates notes from commits.
3. Write plain-text Play notes in `app/src/main/play/release-notes/en-US/default.txt`, using at most 500 characters.
   The workflow rejects longer notes before building. Unchanged notes cause Play to display the previous release's text.
4. Update the Claude Code version that Headroom sends to Claude:

   ```bash
   scripts/update-claude-code-version.sh
   ```

   The script reads the latest published Claude Code version with `npm view @anthropic-ai/claude-code version`.
   It updates `ClaudeCodeIdentity.VERSION` in `:core:quota` and prints the change.
   Claude lists usage-limit resets only for recent Claude Code clients. An old client version hides Claude resets from Headroom.
   [RESETS.md](RESETS.md#claude) describes this requirement.
5. Run `./gradlew check`, commit, and push `main`.
6. Tag the commit and push the tag:

   ```bash
   git tag -a v1.0.0 -m "Headroom 1.0.0"
   git push origin v1.0.0
   ```

7. Wait for completion, then check the GitHub release and Play Console.
   For failures, follow [The smoke test](#the-smoke-test).

## Google Play

`./gradlew :app:bundlePlay` builds the separate `play` build type with Play policy changes.
The workflow uploads it through Gradle Play Publisher to the track named by `PLAY_TRACK`.
An unset variable selects production. Play upload runs after GitHub publication, so upload failures leave the GitHub release available.
Fix the cause and rerun the failed job.
[PLAY-STORE.md](PLAY-STORE.md) covers build differences, listing files and Console settings.

## The smoke test

The workflow tests the exact release APK on an API 37 emulator before publication.
R8 minifies release builds, while end-to-end tests use debug builds. Only running the minified APK can expose R8-specific failures.

[`scripts/release-smoke-test.sh`](../scripts/release-smoke-test.sh) installs the APK and controls it through adb and UI Automator.
The script needs no test APK or extra R8 keep rules. A fresh install starts demo mode without real accounts or tokens.
The script checks these steps in order:

1. The overview shows the demo accounts.
2. Refresh changes Claude's weekly usage.
3. Claude's detail opens, and Back returns to the overview.
4. The Resets tab, the Stats tab and Settings open.

The script fails on an app crash or a screen that takes more than 20 seconds to appear.
The release job reuses the emulator cache saved by the end-to-end CI job on `main`.

After a failure, download the workflow's `release-smoke-test` artifact.
It contains step screenshots, the failure screenshot and window hierarchy, logcat output and the emulator log.
Fix the cause on `main`, then move and push the tag:

```bash
git tag -fa v1.0.0 -m "Headroom 1.0.0"
git push --force origin v1.0.0
```

For a local emulator, build a release APK, sign it with any key, and pass it to the script.
The script uninstalls Headroom first. Do not use a phone that holds real Headroom accounts.

```bash
./gradlew :app:assembleRelease
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
    --out app-release-smoke.apk app/build/outputs/apk/release/app-release-unsigned.apk
ANDROID_SERIAL=emulator-5554 scripts/release-smoke-test.sh app-release-smoke.apk
```

The smoke test uses no sign-in and reads no real provider responses.
JVM tests check response parsing with recorded fixtures before R8 runs.

## Obtainium

Users can install and update Headroom through [Obtainium](https://obtainium.imranr.dev/) using GitHub releases.
Preserve these release rules so Obtainium can find updates:

- Attach one `.apk` per release. Multiple APKs require users to choose an asset.
  Per-ABI builds require an APK filter in the README link.
- Match the version tag to the installed `versionName`. The tag can include a leading `v`, as in `v1.2.0`.
  Obtainium compares these version numbers.

## The release key

Sign every release with the same key. Android rejects updates signed with a different key.
Losing the key forces users to uninstall Headroom before installing a new release. Uninstalling deletes their accounts.

| What | Where |
|---|---|
| The keystore (PKCS12) and its password | 1Password, vault **Private**, item **Headroom Android release key** |
| The key alias | `headroom` |
| The certificate's SHA-256 | `81:5F:AA:26:1D:76:B2:B3:37:E4:AB:D0:F3:BA:31:84:AD:05:FB:8E:BD:65:01:42:43:47:22:69:BC:DA:5F:C2` |
| The copy that CI uses | GitHub Actions secrets `HEADROOM_KEYSTORE_BASE64`, `HEADROOM_KEYSTORE_PASSWORD`, `HEADROOM_KEY_ALIAS` and `HEADROOM_KEY_PASSWORD` |
| Play App Signing | The same key, uploaded to the Play Console with PEPK, so Play and GitHub installs update each other |

The keystore and key use the same password.
The workflow rejects APKs whose signing certificate differs from the fingerprint above.

For a local signed build, export the keystore from 1Password and run:

```bash
HEADROOM_KEYSTORE_FILE=/path/to/headroom-release.p12 \
HEADROOM_KEYSTORE_PASSWORD=... HEADROOM_KEY_ALIAS=headroom HEADROOM_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

Without these variables, Gradle leaves the release unsigned.
Debug builds use the debug key. Debug and release installs cannot update each other.

# Releasing

A release is a git tag. Pushing a tag such as `v1.0.0` starts the
[Release workflow](../.github/workflows/release.yml). It builds a minified APK, signs it with the
release key, checks the signature, and smoke-tests the APK on an emulator. Then it attaches the
APK and its SHA-256 checksum to a GitHub release for that tag. If the smoke test fails, the
workflow does not publish the release.

## Steps

1. Set `versionName` in `app/build.gradle.kts` to the new version, and raise `versionCode` by one.
   The workflow stops if the tag and `versionName` do not match.
2. Write the release notes in `docs/release-notes/<version>.md`. Without that file, GitHub writes
   the notes from the commits.
3. Update the Claude Code version that Headroom sends to Claude:

   ```bash
   scripts/update-claude-code-version.sh
   ```

   The script reads the latest published Claude Code (`npm view @anthropic-ai/claude-code
   version`), writes it to `ClaudeCodeIdentity.VERSION` in `:core:quota`, and prints the change.
   Claude lists usage-limit resets only for recent Claude Code clients. With an old version, the
   Claude resets disappear from the app. See [RESETS.md](RESETS.md#claude).
4. Run `./gradlew check`, commit, and push `main`.
5. Tag the commit and push the tag:

   ```bash
   git tag -a v1.0.0 -m "Headroom 1.0.0"
   git push origin v1.0.0
   ```

6. Wait for the workflow to finish, then check the release page. If the smoke test failed, see
   [The smoke test](#the-smoke-test).

## The smoke test

R8 minifies the release build, and the end-to-end tests only run the debug build. A problem that
R8 causes shows only when the minified app runs. So before it publishes, the workflow runs the
exact APK that it is about to ship on an API 37 emulator.

[`scripts/release-smoke-test.sh`](../scripts/release-smoke-test.sh) installs the APK and drives
it with adb and UI Automator. There is no test APK, so the release build needs no extra R8 keep
rules. A fresh install has no accounts, so the app shows demo data. No real account or token is
involved. The script checks these things, in order:

1. The overview shows the demo accounts.
2. A refresh syncs: Claude's weekly usage changes.
3. Claude's detail opens, and Back returns to the overview.
4. The Resets tab, the Stats tab and Settings open.

The script fails if a screen does not show within 20 seconds, or if the app crashes. The emulator
comes from the cache that the CI end-to-end job saves on `main`, so a release does not download
it again.

When the smoke test fails, open the workflow run and download the `release-smoke-test` artifact.
It has a screenshot of each step, a screenshot and the window hierarchy at the failure, the
logcat, and the emulator log. Fix the cause on `main`, then move the tag to the fixed commit and
push it again:

```bash
git tag -fa v1.0.0 -m "Headroom 1.0.0"
git push --force origin v1.0.0
```

To run the smoke test on your own emulator, build the release APK, sign it with any key, and pass
it to the script. The script uninstalls Headroom first, so do not run it on a phone with real
accounts in Headroom.

```bash
./gradlew :app:assembleRelease
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
    --out app-release-smoke.apk app/build/outputs/apk/release/app-release-unsigned.apk
ANDROID_SERIAL=emulator-5554 scripts/release-smoke-test.sh app-release-smoke.apk
```

The smoke test does not sign in, so it does not read real provider responses. The JVM tests cover
that parsing with recorded fixtures, but against the code before R8 runs.

## Obtainium

Users can install and update Headroom with [Obtainium](https://obtainium.imranr.dev/), which
reads the GitHub releases. Keep these things as they are, or it stops finding updates:

- One `.apk` asset per release. Obtainium asks the user to pick when there are several, and a new
  asset such as a per-ABI build needs an APK filter in the README link.
- A tag that is the version, optionally with a leading `v` (`v1.2.0`). Obtainium compares it with
  the installed `versionName`, so the two must stay the same number.

## The release key

Every release must be signed with the same key. Android refuses to install an update signed with
a different key over an existing install, so a lost key means users must uninstall first, and lose
their accounts.

| What | Where |
|---|---|
| The keystore (PKCS12) and its password | 1Password, vault **Private**, item **Headroom Android release key** |
| The key alias | `headroom` |
| The certificate's SHA-256 | `81:5F:AA:26:1D:76:B2:B3:37:E4:AB:D0:F3:BA:31:84:AD:05:FB:8E:BD:65:01:42:43:47:22:69:BC:DA:5F:C2` |
| The copy that CI uses | GitHub Actions secrets `HEADROOM_KEYSTORE_BASE64`, `HEADROOM_KEYSTORE_PASSWORD`, `HEADROOM_KEY_ALIAS` and `HEADROOM_KEY_PASSWORD` |

The keystore and the key share one password. The workflow checks that each APK is signed with the
certificate above, and fails if it is not.

To build a signed release on your own machine, export the keystore from 1Password, then:

```bash
HEADROOM_KEYSTORE_FILE=/path/to/headroom-release.p12 \
HEADROOM_KEYSTORE_PASSWORD=... HEADROOM_KEY_ALIAS=headroom HEADROOM_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

Without these variables the release build is left unsigned. Debug builds always use the debug
key, so a debug install and a release install cannot update each other.

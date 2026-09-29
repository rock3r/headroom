# Releasing

A release is a git tag. Pushing a tag such as `v1.0.0` starts the
[Release workflow](../.github/workflows/release.yml). It builds a minified APK, signs it with the
release key, checks the signature, and attaches the APK and its SHA-256 checksum to a GitHub
release for that tag.

## Steps

1. Set `versionName` in `app/build.gradle.kts` to the new version, and raise `versionCode` by one.
   The workflow stops if the tag and `versionName` do not match.
2. Write the release notes in `docs/release-notes/<version>.md`. Without that file, GitHub writes
   the notes from the commits.
3. Run `./gradlew check`, commit, and push `main`.
4. Tag the commit and push the tag:

   ```bash
   git tag -a v1.0.0 -m "Headroom 1.0.0"
   git push origin v1.0.0
   ```

5. Wait for the workflow to finish, then check the release page.

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

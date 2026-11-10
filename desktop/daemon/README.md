# Headroom quota daemon (pilot)

A small macOS native process that exposes local quota readings to agents. It does not redeem
resets, and it does not publish the port beyond `127.0.0.1`.

The opt-in refresh reuses the same Kotlin fetchers and reset readers as the mobile app and reads
the sign-ins that already exist on this Mac. The API and the hooks serve the persisted cache and
say when a reading is missing or stale instead of inventing one.

## Commands

```sh
./gradlew :desktop:daemon:linkDebugExecutableMacosArm64
./desktop/daemon/codesign.sh   # re-signs the binary with a stable local identity
BIN=desktop/daemon/build/bin/macosArm64/debugExecutable/headroomd.kexe

"$BIN" serve [--port 4217]     # loopback API from the cache; never calls a provider
"$BIN" hook [--agent claude|codex]  # one hook answer from the cache; fail-open, no provider call
"$BIN" refresh                 # opt-in: read real usage with the sign-ins on this Mac
"$BIN" connect claude          # the only command that may show the macOS keychain approval
"$BIN" hooks preview|install|uninstall [--command CMD]  # agent hook registration
```

`codesign.sh` matters: the linker emits an ad-hoc signature whose hash changes on every rebuild,
so a keychain "Always Allow" granted to one build would not cover the next. The script signs the
binary with a code-signing identity whose requirement is stable across rebuilds; override the
identity with `HEADROOM_CODESIGN_IDENTITY`. It never changes trust settings or the keychain. On a
machine without a suitable identity, create a local one first (example, no personal data implied):

```sh
CN="Headroom Quota Daemon Pilot"
PW=$(openssl rand -hex 12)
openssl req -newkey rsa:2048 -nodes -keyout k.pem -x509 -days 3650 -out c.pem \
  -subj "/CN=$CN/O=Headroom Pilot" \
  -addext "keyUsage=critical,digitalSignature" -addext "extendedKeyUsage=codeSigning"
openssl pkcs12 -export -legacy -inkey k.pem -in c.pem -name "$CN" -passout pass:"$PW" -out i.p12
# LibreSSL (macOS /usr/bin/openssl) rejects -legacy: drop it there.
security import i.p12 -k ~/Library/Keychains/login.keychain-db -P "$PW" -T /usr/bin/codesign
rm -f k.pem c.pem i.p12
```

Signing is optional for building and testing; it only matters once you want a keychain approval
for the binary to survive rebuilds.

## Refresh and credentials

`refresh` is the only path that calls providers, and it only reads. Claude comes from the login
keychain (service `Claude Code-credentials`, discovered, never assumed); Codex comes from the
CLI's own `~/.codex/auth.json`. Tokens live in memory for the refresh only: they are not written
to the state file, the API, or a log.

- A quiet `refresh` never shows a keychain prompt (it passes `kSecUseAuthenticationUIFail`) and
  never retries; if macOS withholds the sign-in, the refresh says so and continues without it.
- `connect claude` is the deliberate, interactive step: run it yourself and click Always Allow
  once. Refreshes after that read the sign-in without prompting. A denial is never retried.
- The keychain read has a hard deadline (10 s quiet, 120 s interactive). If macOS holds the item
  (for example while its Access Control window is open in Keychain Access), the refresh reports
  that and gives up instead of hanging.
- If the stored Claude token itself has expired, the reading is reported with the auth error and
  no numbers are invented; run `claude` once to refresh that sign-in.

## What an agent can ask

The daemon listens on `127.0.0.1` only. The token in `~/.headroom/pilot-token` (mode `0600`) is a
local secret, not a provider credential. A request also needs a loopback `Host` and, if it sends
`Origin`, a loopback origin. That stops a browser page on another site from calling the port.

```sh
TOKEN=$(cat ~/.headroom/pilot-token)
curl --fail-with-body \
  -H "Authorization: Bearer $TOKEN" \
  -H "Host: 127.0.0.1:4217" \
  http://127.0.0.1:4217/v1/quotas
```

`4217` is the default port; tests use a scratch port so they never fight a real instance. The
body uses opaque refs (`claude-1`), never an email, a provider account id, or a token. Resets are
listed as counts, and the cache keeps them, so the API serves them between refreshes. There is no
redeem route: `POST /v1/resets/redeem` is `404`.

`GET /health` answers `{"ok":true}` with the same checks.

## Hooks

`headroomd hook` reads the cache file and prints the JSON both installed agents accept. Verified
against Claude Code 2.1.302 and Codex CLI 0.166.0:

```json
{"hookSpecificOutput":{"hookEventName":"UserPromptSubmit","additionalContext":"…"}}
```

A missing cache, a broken cache, or no advisory is `{}`. The hook does not call a provider, so it
stays fast and fail-open. It does not block the prompt. The advisory latch is persisted next to
the numbers, so a later hook process stays quiet until the condition clearly clears (over pace,
or about 10% left, re-armed only past the hysteresis band).

`headroomd hooks` registers the hook by merging one Headroom group into each agent's own config:
Claude Code's `settings.json` (under `CLAUDE_CONFIG_DIR` or `~/.claude`) and Codex's `hooks.json`
(under `CODEX_HOME` or `~/.codex`).

- `preview` prints the merge and writes nothing;
- `install` merges, preserving every other hook and setting (a config that is not valid JSON is
  refused, never rewritten);
- `uninstall` removes exactly the Headroom group and leaves the rest in place.

Codex only runs hooks when `[features] hooks = true` and a new hook is trusted after its trust
review (or a per-run `--dangerously-bypass-hook-trust` for a deliberate test). The installer
never writes a trust hash. Nothing is registered for login or launchd.

## Run

```sh
HEADROOM_STATE=/tmp/synthetic.json "$BIN" serve --port 4217
HEADROOM_STATE=/tmp/synthetic.json "$BIN" hook --agent claude
```

Synthetic cache files are not real quota. A message in an agent transcript shows the hook ran. It
does not show that the model used fewer tokens.

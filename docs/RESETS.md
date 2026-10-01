# Usage-limit resets

Some providers give subscribers resets: a reset refills a usage limit before its normal reset time.
Headroom shows the resets of Codex, Grok and Claude accounts. It can use Codex and Grok resets.
Claude's resets are shown for information only. Z.AI resets are not shown.

| Provider | Shows resets | Can use a reset | Why not |
|---|---|---|---|
| ChatGPT Codex | Yes | Yes | |
| Grok | Yes | Yes | |
| Claude | Yes | No | The owner decided to show Claude's grants only, for now. |
| Z.AI | No | No | Z.AI resets need a separate ZCode sign-in, which Headroom does not have. |

`Provider.canRedeemResets` in `:core:model` is the one place that says which providers can use
resets. The UI and `SyncedResetProvider` both read it.

## How the resets are read

Each sync reads an account's resets together with its usage, with the same credential.
`AccountQuotaFetcher` in `:core:data` starts the reset read in parallel with the usage fetch, and
adds the result to the snapshot (`QuotaSnapshot.resets`).

- A reset read that fails never fails the sync. The snapshot sets `resetsReadFailed`, and Room
  keeps the resets it stored last.
- Room stores the resets as JSON on the account row (`accounts.resetsJson`, database version 6).
- The screens read the resets from the stored snapshot. Opening a screen makes no network call.
- A demo account never reaches a provider. Debug builds give the demo accounts fake resets
  (`FakeResetProvider`); release builds give them none. `RealOrDemoResetProvider` in `:app` sends
  each account to the right side.

## Endpoints

These are the calls the reset clients in `:core:quota` make. None of them was tried against a real
account while building this; the shapes come from the providers' own clients.

| Provider | Call | Request | Headers |
|---|---|---|---|
| Codex | List credits | `GET https://chatgpt.com/backend-api/wham/rate-limit-reset-credits` | `Authorization: Bearer <ChatGPT OAuth token>`, `ChatGPT-Account-Id`, `Accept: application/json` |
| Codex | Use a credit | `POST …/wham/rate-limit-reset-credits/consume` with `{"redeem_request_id": <attempt key>, "credit_id": <soonest-expiring credit>}` | The same, plus `Content-Type: application/json` |
| Grok | List tokens | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/GetRemainingResets`, an empty protobuf message in a gRPC-web frame | `Authorization: Bearer <xAI OAuth token>`, `Content-Type: application/grpc-web+proto`, `Accept: application/grpc-web+proto`, `X-Grpc-Web: 1`, `TE: trailers` |
| Grok | Use a token | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/RedeemReset` with `token_id` (field 10) | The same |
| Claude | List grants | `GET https://api.anthropic.com/api/oauth/usage?cedar_ember=1&skip_spend=1` | `Authorization: Bearer <Claude OAuth token>`, `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-cli/2.1.281 (external, cli)` |

### Codex

- A pool holds the credits whose `status` is `available` and whose `expires_at` has not passed,
  soonest expiry first. A reset refills the 5-hour and the weekly limits.
- The consume call names the soonest-expiring credit. The credit is pinned to the attempt key, so
  "Try again" addresses the same credit. When the list cannot be read, the call goes out without a
  credit id and the server picks one.
- Answers: `reset` is a success, `already_redeemed` is a success of an earlier try with the same
  key, `nothing_to_reset` and `no_credit` end the attempt. An answer Headroom does not know keeps
  the key, so "Try again" can send it again.

### Grok

- gRPC-web framing (`GrpcWeb`): each frame is a flag byte, a four-byte big-endian length and the
  payload. A trailer frame (flag `0x80`) carries `grpc-status`. A trailers-only answer carries it
  in the HTTP headers instead.
- The protobuf messages (`GrokResetProto`) are read and written by hand: tokens are field 10, each
  with `token_id` (10) and `validity_end` (30, a Timestamp).
- The redeem call has no idempotency key of its own. Headroom pins the token to the attempt key.
  When a retry finds its pinned token gone, the earlier try worked.
- gRPC status 16 or 7, or HTTP 401 or 403, asks the user to sign in again. Status 8 or HTTP 429 is
  rate limiting.

### Claude

- The `cedar_ember` block of the usage answer lists the grants. Each grant with resets left and an
  `ends_at` in the future becomes a pool that clears the windows in its `clears` list.
- The grant named by `next_grant_id` is the one the server uses next. The other grants are shown as
  queued, so the count reads "1 (+3)". A paused grant is paused.
- An account outside the program shows no resets. The reasons `tier`, `seat` and `tenure` show a
  short explanation instead.
- The server fills the block only for the Claude Code client, so the call carries its User-Agent.
  If Claude starts answering `surface` or `cli_version`, the User-Agent needs a newer version.

## Using a reset

- The sheet mints one attempt key per confirmation. "Try again" sends the same key.
- `ResetAttemptMemory` keeps the key of an unsettled attempt for 10 minutes, per account and pool.
  `SharedPreferencesResetAttemptStore` writes it to disk before the call goes out, so it survives
  the app being stopped. It stores the account id, the pool, the key and the time. It stores no
  token. Like every app file, it is excluded from backup and device transfer.
- The pinned credit or token lives in memory only. After a restart, a retry of the same Codex key
  is still safe, because the server answers `already_redeemed`.
- After a reset works, `ResetCenter` waits 2 seconds (`ResetRefresh.DELAY`), then refreshes the
  account. The refill animation plays from the new usage.
- An account whose sign-in expired shows its resets faded, with no action.

## Logs

The reset clients write one line per request to logcat, under the tag `HeadroomResets`:

```
adb logcat -s HeadroomResets
```

- Debug lines: the provider, the operation, the endpoint path without its query, the HTTP status,
  the gRPC status, the provider's answer code, the counts, the attempt key and the time the call
  took. A redeem also logs the pool and a short hash of the account id.
- Warning lines: an answer Headroom could not read, with the fields it looked for and the body,
  redacted and cut to 400 characters.
- The lines never carry a token, a cookie, an `Authorization` header, an email or an account id.
  `ResetLogRedaction` removes them from bodies; `ResetLogTest` checks it.

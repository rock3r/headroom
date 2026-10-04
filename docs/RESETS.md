# Usage-limit resets

Some providers give subscribers resets: a reset refills a usage limit before its normal reset time.
Headroom shows the resets of Codex, Grok, Claude and Z.AI accounts. It can use Codex, Grok and Z.AI
resets, and ask Z.AI for more. Claude's resets are shown for information only.

| Provider | Shows resets | Can use a reset | Notes |
|---|---|---|---|
| ChatGPT Codex | Yes | Yes | |
| Grok | Yes | Yes | |
| Claude | Yes | No | The owner decided to show Claude's grants only, for now. |
| Z.AI | After a ZCode sign-in | Yes | Z.AI resets need a separate ZCode sign-in. |

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

These are the calls the reset clients in `:core:quota` make. The shapes come from the providers'
own clients. On 2026-10-04 the owner used a reset with a real account, read from the phone's
`HeadroomResets` log:

| Provider | Checked with a real account |
|---|---|
| ChatGPT Codex | List and consume: the consume answered `reset`, and the list then had one credit fewer (3 to 2). |
| Z.AI | Sign-in, status, ask and use: see [Z.AI](#zai). |
| Grok | Not yet: the account had no reset token. |
| Claude | Reading the grants only. Claude resets cannot be used. |

| Provider | Call | Request | Headers |
|---|---|---|---|
| Codex | List credits | `GET https://chatgpt.com/backend-api/wham/rate-limit-reset-credits` | `Authorization: Bearer <ChatGPT OAuth token>`, `ChatGPT-Account-Id`, `Accept: application/json` |
| Codex | Use a credit | `POST …/wham/rate-limit-reset-credits/consume` with `{"redeem_request_id": <attempt key>, "credit_id": <soonest-expiring credit>}` | The same, plus `Content-Type: application/json` |
| Grok | List tokens | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/GetRemainingResets`, an empty protobuf message in a gRPC-web frame | `Authorization: Bearer <xAI OAuth token>`, `Content-Type: application/grpc-web+proto`, `Accept: application/grpc-web+proto`, `X-Grpc-Web: 1`, `TE: trailers` |
| Grok | Use a token | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/RedeemReset` with `token_id` (field 10) | The same |
| Claude | List grants | `GET https://api.anthropic.com/api/oauth/usage?cedar_ember=1&skip_spend=1` | `Authorization: Bearer <Claude OAuth token>`, `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-cli/2.1.281 (external, cli)` |
| Z.AI | List cards | `GET https://zcode.z.ai/api/v1/coding-plan/reset/status` | `Authorization: Bearer <ZCode JWT>`, `X-Bigmodel-Authorization: <Z.AI business token>`, `Bigmodel-Target-Type: PERSONAL` |
| Z.AI | Use a card | `POST …/coding-plan/reset/use` with `{"idempotency_key": <attempt key>, "reset_type": "FIVE_HOUR" \| "WEEK"}` | The same, plus `Content-Type: application/json` |
| Z.AI | Mark history read | `POST …/coding-plan/reset/history/read` with no body | `Authorization` and `X-Bigmodel-Authorization` only: no target scope |
| Z.AI | Ask for a card | `POST …/coding-plan/reset/opportunity` with `{"idempotency_key": <ask key>}` | As for the list, plus `Content-Type: application/json` |

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

### Z.AI

- Z.AI serves its reset cards from `zcode.z.ai`, which refuses the API key that Headroom reads the
  usage with. So a Z.AI account needs a second sign-in, to ZCode. Until it has one, the account
  detail's Resets card shows "Want to see your resets?" and a "Sign in to ZCode" button. The
  Resets tab leaves the account out.
- Every answer is a `{code, msg, data}` envelope. Only a numeric `code` of 0 is a success. The
  server sends the envelope with its business code on HTTP errors too, so Headroom reads it first.
  HTTP 401, 403 and 429 keep their HTTP meaning, except that code 3301 is always a decline.
- The status lists two pools: `five_hour` (the 5-hour limit) and `week` (the weekly limit). Each
  card has an `expire_at` in epoch milliseconds; Headroom also reads seconds. Expired cards are
  left out. HTTP 401 or 403 means the ZCode sign-in no longer works, and the card asks for it
  again.
- A redeem reads the status first, so an empty pool answers "no credit" and uses nothing. That
  check is skipped for an attempt key that already reached `use`: when the answer to the last
  card's `use` was lost, the pool is empty, and only the server can say whether the key worked.
  Such a key is forgotten only on a definite "no" to its first `use`: a JSON `used: false`, or
  HTTP 401 or 403. These keys live in memory only.
- After a card is used, `history/read` clears ZCode's "unread" mark. It is best effort: a failure
  is logged and the reset still counts as done. The mark is shared by all of the user's plans, so
  the call sends no body and no target scope.
- Asking for a card runs only when the user taps "Ask for a reset card". It is never polled,
  because Z.AI rate limits it per account and ZCode already polls it. Each ask sends its own
  `idempotency_key`; only an ask that failed in a way that may pass (no connection, or business code
  2007) sends the same key again. Business code 3301 is a "not now" with `next_try_at`. HTTP 429
  (or code 429) means the account asked too often. As ZCode does, Headroom then holds asks per
  ZCode sign-in: until `next_try_at` but at least 5 minutes, 10 minutes when no time is named, and
  10 minutes after a 429. An ask before then is answered without a call. A failure holds nothing.
- Team plans are not supported. The scoped calls send `Bigmodel-Target-Type: PERSONAL`; a team
  plan sends `TEAM` with `Bigmodel-Organization` and `Bigmodel-Project`, which Headroom does not
  look up.
- The calls follow ZCode's own source (github.com/zai-org/ZCode,
  `packages/services/src/usage-stats/providers/bigmodelUsageQuotaProvider.ts` and
  `packages/ui/src/lib/codingPlanQuotaResetCoordinator.ts`), read on 2026-10-04. Every call was
  checked with a real personal Coding Plan account on the same day, from the phone's
  `HeadroomResets` log: the sign-in, the status, an ask that was granted (the status then went
  from 0 to 5 cards per limit), an ask declined with 3301, and a `use` of a weekly card (`used:
  true`, one weekly card fewer). `history/read` answers `{code, msg}` with no `data`.
- After a granted ask, `ResetCenter` refreshes the account before the sheet shows the answer,
  because the resets come from the stored snapshot. "Use it now" then finds the new card.

### The ZCode sign-in

`ZCodeAuth` in `:core:auth` is the sign-in of ZCode's own CLI. `AuthMethods.zCodeSignIn` runs it as
a device-code flow with no code to show:

1. `POST https://zcode.z.ai/api/v1/oauth/cli/init` with `Authorization: Bearer <poll token>` (32
   random bytes, in hex) and `{"provider":"zai"}`. The answer gives `flow_id`, the `chat.z.ai`
   page to open (refused unless it is `https`), `expires_at`, `poll_interval_sec`, and maybe its
   own `poll_token`, which then replaces the client's.
2. The app opens that page in a browser tab and polls `GET …/oauth/cli/poll/{flow_id}`. `pending`
   keeps polling, and so do HTTP 408, 429 and 5xx. `failed`, or another 4xx, ends the sign-in. A
   `ready` answer carries the ZCode JWT (`token`) and a Z.AI OAuth token (`zai.access_token`).
3. `POST https://api.z.ai/api/auth/z/login` with `{"token": <Z.AI OAuth token>}` gives the
   short-lived business token and its `expires_in`.

The credential keeps the business token as its access token and both long-lived tokens, as JSON
(`ZCodeTokens`), as its refresh token. `SignInManager.signInToZCode` saves it in the same token store
as the account's API key, under `ZCodeCredential.idFor(accountId)` (the account id plus `#zcode`).
Signing the account out removes both. The Z.AI refresher in `AuthMethods.refreshers` mints a new
business token when it expires; the API key itself never expires, so the refresher never sees it.
When the login call refuses the Z.AI token, the user must sign in to ZCode again.

`AccountQuotaFetcher` adds the ZCode sign-in to a Z.AI account's credentials
(`ProviderCredentials.zCode`): ready, missing, or unavailable (for example offline). A ZCode
sign-in that does not work never fails the usage fetch. In the app, `ZCodeSignIn` runs one sign-in
at a time from the redeem sheet. The sheet says while it waits for the browser, says why it
failed, and closes once the user signed in; the account is refreshed so its resets show.

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

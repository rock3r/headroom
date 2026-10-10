# Usage-limit resets

Some providers give subscribers resets: a reset refills a usage limit before its normal reset time.
Headroom shows the resets of Codex, Grok, Claude and Z.AI accounts. It can use Codex, Grok and Z.AI
resets, and ask Z.AI for more. It can use Claude's resets only when the user turns on "Redeem Claude
resets (experimental)" in Settings. Until then, Claude's resets are shown for information only.

| Provider | Shows resets | Can use a reset | Notes |
|---|---|---|---|
| ChatGPT Codex | Yes | Yes | |
| Grok | Yes | Yes | |
| Claude | Yes | Experimental, off by default | The user turns it on in Settings. Never tried with a real account. |
| Z.AI | After a ZCode sign-in | Yes | Z.AI resets need a separate ZCode sign-in. |

`Provider.canRedeemResets(settings)` in `:core:model` is the one place that says which providers can
use resets. The UI and `SyncedResetProvider` both read it, with the user's current settings.
`Provider.redeemsResetsExperimentally` says which redeems are experimental: Claude's. Its
confirmation step shows an "Experimental" label.

## How the resets are read

Each sync reads an account's resets together with its usage, with the same credential.
`AccountQuotaFetcher` in `:core:data` starts the reset read in parallel with the usage fetch, and
adds the result to the snapshot (`QuotaSnapshot.resets`).

- A reset read that fails never fails the sync. The snapshot sets `resetsReadFailed`, and Room
  keeps the resets it stored last. Room also keeps the flag (`accounts.resetsReadFailed`, database
  version 8), so the stored snapshot shows that its resets are older than its usage. The next sync
  that works sets the flag again. A sync that fails leaves it as it was.
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
| Claude | Reading the grants only. Using a grant was never tried: see [Claude](#claude). |

| Provider | Call | Request | Headers |
|---|---|---|---|
| Codex | List credits | `GET https://chatgpt.com/backend-api/wham/rate-limit-reset-credits` | `Authorization: Bearer <ChatGPT OAuth token>`, `ChatGPT-Account-Id`, `Accept: application/json` |
| Codex | Use a credit | `POST …/wham/rate-limit-reset-credits/consume` with `{"redeem_request_id": <attempt key>, "credit_id": <soonest-expiring credit>}` | The same, plus `Content-Type: application/json` |
| Grok | List tokens | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/GetRemainingResets`, an empty protobuf message in a gRPC-web frame | `Authorization: Bearer <xAI OAuth token>`, `Content-Type: application/grpc-web+proto`, `Accept: application/grpc-web+proto`, `X-Grpc-Web: 1`, `TE: trailers` |
| Grok | Use a token | `POST https://grok.com/prod_mc_billing.ConsumerUiSvc/RedeemReset` with `token_id` (field 10) | The same |
| Claude | List grants | `GET https://api.anthropic.com/api/oauth/usage?cedar_ember=1&skip_spend=1` | `Authorization: Bearer <Claude OAuth token>`, `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-cli/<version> (external, cli)` |
| Claude | Read the organization | `GET https://api.anthropic.com/api/oauth/profile`, for `organization.uuid` | `Authorization: Bearer <Claude OAuth token>` |
| Claude | Use a grant | `POST https://api.anthropic.com/api/organizations/{organization}/reset_rate_limits` with `{"program":"cedar_ember","grant_id":<grant>,"request_id":<attempt key>}` | As for the list, plus `Content-Type: application/json` |
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
- `SharedPreferencesAttemptTargetStore` writes the pin to disk before the redeem goes out, so a
  retry after the app was stopped addresses the same token. It stores the latest 16 attempt keys
  with their token ids, and no access token. When that write fails, the redeem is not sent and
  fails, so it can be retried with the same key.
- gRPC status 16 or 7, or HTTP 401 or 403, asks the user to sign in again. Status 8 or HTTP 429 is
  rate limiting.

### Claude

- The `cedar_ember` block of the usage answer lists the grants. Each grant with resets left and an
  `ends_at` in the future becomes a pool that clears the windows in its `clears` list.
- The grant named by `next_grant_id` is the one the server uses next. The other grants are shown as
  queued, so the count reads "1 (+3)". A paused grant is paused.
- An account outside the program shows no resets. The reasons `tier`, `seat` and `tenure` show a
  short explanation instead.
- The server fills the block only for recent Claude Code clients, so the call carries their
  User-Agent. `ClaudeCodeIdentity.VERSION` in `:core:quota` holds the version for every Claude
  request. `scripts/update-claude-code-version.sh` sets it to the latest published Claude Code,
  and each release runs it (see [RELEASING.md](RELEASING.md)).
- When the account is outside the program, the `HeadroomResets` log line names the server's
  reason, for example `ineligible cli_version`. The reasons `surface` and `cli_version` mean the
  version is too old: run the script. A reason that is not a short code is logged as `other`, so
  the log never carries the server's text.

Using a grant is experimental. The endpoint and its answers come from research; nobody has tried
them with a real account yet, so this section describes what Headroom sends and expects, not what
was measured.

- Only the grant named by `next_grant_id` can be used, while it is `usable_now`, not `paused`, and
  before `ends_at`. The sheet only offers that grant (`ResetPoolStatus.Ready`). A grant with
  `use_requires_limit: true` that is not `usable_now` shows as "At a limit only": the confirm button
  is off and the sheet says why. At a limit, the grant's `clears` must include the limit that was
  hit; Headroom relies on the server's `usable_now` for that. Any other grant that is not
  `usable_now`, or whose `starts_at` is still to come, shows as "Not yet" (`NotUsableYet`): the
  button is off, and the sheet says that Claude does not allow it yet.
- A grant with `use_requires_limit: false` can be used at any time. The confirmation says that
  this cannot be undone.
- A redeem reads the status first and notes the grant's `resets_left`, but only before the first
  send of an attempt key. It then reads the organization from the profile. If either cannot be read,
  nothing is sent and the redeem fails, so it can be tried again with the same key. A grant the
  status no longer lists is ineligible, and nothing is sent.
- `request_id` is the attempt key. Claude only accepts `^[A-Za-z0-9_-]{1,64}$`; the attempt keys are
  UUIDs, which match. A key that does not match is sent as its SHA-256 in hex, so every try of one
  attempt still sends the same id.
- Answers (`result`): `reset` is a success, with `resets_left`. `already_used` is a success of an
  earlier try with the same `request_id`. `not_limited` used nothing, because nothing was used yet.
  `cooldown` and `ineligible` used nothing. `unavailable` is unconfirmed: Headroom keeps the key and
  offers "Check again". A `result` Headroom does not know keeps the key, so "Try again" can send it
  again. HTTP 429 is rate limiting, and HTTP 401 or 403 asks the user to sign in again.
- "Check again" reads the status and never sends the reset again. The reset worked when the grant
  now has fewer `resets_left` than before the first send. Anything else stays unconfirmed. The
  `resets_left` noted before the send lives in memory only, so after a restart a check stays
  unconfirmed; a new confirmation within 10 minutes sends the same `request_id`, and Claude then
  answers `already_used` if the earlier try worked.
- The log names the redeem path as `/api/organizations/{organization}/reset_rate_limits`: the
  organization identifies the account, so it is never logged.
  The answer's `result` and `reason` are logged only when they are short codes; any other text is
  logged as `other`.

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
  HTTP 401 or 403. `SharedPreferencesAttemptTargetStore` writes these keys to disk before `use`
  goes out, so a retry after the app was stopped still reaches `use`. It stores the latest 16 keys
  with their pools, and no token. When that write fails, `use` is not sent and the redeem fails, so
  it can be retried with the same key.
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
  HTTP 401 or 403 means the ZCode sign-in no longer works: the sheet then offers "Sign in again",
  which starts the ZCode sign-in, as it does for a refused `use`.
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
   own `poll_token`, which then replaces the client's. An `expires_at` that has already passed
   ends the sign-in at once, as timed out, without polling.
2. The app opens that page in a browser tab and polls `GET …/oauth/cli/poll/{flow_id}`. `pending`
   keeps polling, and so do HTTP 408, 429 and 5xx. `failed`, or another 4xx, ends the sign-in. A
   `ready` answer carries the ZCode JWT (`token`) and a Z.AI OAuth token (`zai.access_token`).
3. `POST https://api.z.ai/api/auth/z/login` with `{"token": <Z.AI OAuth token>}` gives the
   short-lived business token and its `expires_in`.

The credential keeps the business token as its access token and both long-lived tokens, as JSON
(`ZCodeTokens`), as its refresh token. `SignInManager.signInToZCode` saves it in the same token store
as the account's API key, under `ZCodeCredential.idFor(accountId)` (the account id plus `#zcode`).
Signing the account out removes both. A ZCode sign-in and a sign-out never run at the same time, so
a ZCode sign-in that finishes during a sign-out cannot leave its credential behind. The Z.AI
refresher in `AuthMethods.refreshers` mints a new business token when it expires; the API key
itself never expires, so the refresher never sees it.
When the login call refuses the Z.AI token, the user must sign in to ZCode again.

`AccountQuotaFetcher` adds the ZCode sign-in to the credentials of a Z.AI account's reset read
(`ProviderCredentials.zCode`): ready, missing, or unavailable (for example offline). It resolves
the sign-in inside the reset read, in parallel with the usage fetch, which only needs the API key.
A ZCode sign-in that does not work never fails the usage fetch. A sync waits at most 5 seconds for
it (`ZCODE_SIGN_IN_WAIT`), refresh included; past that, the sync reads no resets and keeps the
stored ones, so a token endpoint that does not answer never holds up the usage.
In the app, `ZCodeSignIn` runs one sign-in at a time from the redeem sheet. The sheet says while it
waits for the browser, says why it failed, and closes once the user signed in; the account is
refreshed so its resets show.

## Using a reset

- The sheet mints one attempt key per confirmation. "Try again" sends the same key.
- `ResetAttemptMemory` keeps the key of an unsettled attempt for 10 minutes, per account and pool.
  `SharedPreferencesResetAttemptStore` writes it to disk before the call goes out, so it survives
  the app being stopped. It stores the account id, the pool, the key and the time. It stores no
  token. Like every app file, it is excluded from backup and device transfer.
- Codex pins its credit in memory only. After a restart, a retry of the same Codex key is still
  safe, because the server answers `already_redeemed`. Grok pins its token on disk (see Grok
  above), because its server has no idempotency key.
- After a reset works, `ResetCenter` waits 2 seconds (`ResetRefresh.DELAY`), then refreshes the
  account. The refill animation plays from the new usage.
- An account whose sign-in expired shows its resets faded, with no action.

## Reminders before a reset expires

About a day before a reset expires unused, Headroom posts a notification on the "Reset reminders"
channel, for example "Your ChatGPT Codex reset expires tomorrow at 06:18". The user can turn it off
in Settings ("Remind me before a reset expires"). It is on by default.

`ResetReminderPolicy` in `:core:model` makes every decision:

- It reminds about the resets the user can act on: the account's sign-in works,
  `Provider.canRedeemResets(settings)` is true, and the pool is `Ready` or `WaitingForLimit`. Each
  expiry in `ResetPool.expiries` is one reset. Pools with no expiry dates never remind.
- A reset is due `LEAD` (one day) before it expires, and is reminded about once.
- The user gets at most one reminder per day. When it goes out, it lists every reset whose reminder
  falls on that day, soonest first. A reset whose reminder falls on a day that already had one
  moves to 09:00 the next day (`DEFERRED_TIME`), and is skipped if it expires before then.

`ResetReminders` in `:core:data` runs it. After every sync and every settings change it sets one
inexact alarm for the next check (`ResetReminderAlarm`); a reboot, an app update or a time zone change sets it again.
The alarm starts `ResetReminderWorker`, which refreshes the accounts with due resets first, so a
reset used on another device is left out.

The worker has no network constraint, because an offline phone must still get the reminder. The
stored resets may be stale after the refresh of an account with a due reset in two cases:

- The refresh fails with `QuotaErrorKind.Network`.
- The refresh works but cannot read the resets (`QuotaSnapshot.resetsReadFailed`). An example is a
  Z.AI account whose usage works while the ZCode sign-in endpoint does not answer.

In both cases the worker posts nothing and runs again later (`ResetReminderRetryPolicy`). It
waits 5, 15, 30 and 60 minutes. When the retries run out, it posts from the stored resets. It also
posts from the stored resets when the next wait would end less than 12 hours (`MIN_NOTICE`) before
the soonest due reset expires. A retry does not count as a reminder, so the rule of one reminder
per day still holds.

`SharedPreferencesResetReminderLedger` stores which
resets were reminded about and the day of the last reminder. It stores keys made of the account
id, the pool id and the expiry time, and no token.

Tapping the notification opens the detail of the soonest reset's account and scrolls its Resets
card into view (`ResetReminderIntents`, `OpenAccountRequest.showResets`).

## Reset history

Headroom keeps a history of the resets that were used or expired, for the "Usage limit resets"
card of the Stats tab.

### Where it is stored

- Room stores it in the `reset_events` table of `headroom.db` (database version 7), next to the
  usage history. Like every app file, it is excluded from backup and device transfer.
- A row holds the account id, the provider, the pool id and label, "used" or "expired", the time
  Headroom saw it, the reset's expiry date, where a used reset was used, what it gave back, and
  the attempt key of a redeem in Headroom. It holds no token.
- Rows are kept for 365 days: each sync deletes older ones. Removing an account deletes its rows,
  as it deletes its usage history.

### How a reset is recorded

`ResetEventDetector` in `:core:model` compares the resets a sync read with the ones stored before,
pool by pool. Every provider lists one expiry date per reset, so a reset is matched by its date.

- A reset that is gone after its expiry date counts as expired.
- A reset that is gone before its expiry date counts as used. When the provider gives no expiry
  date, a lower count counts as a use.
- `ResetCenter` records a redeem that works in Headroom straight away, before it refreshes the
  usage (`RoomResetEventLog.redeemed`). The attempt key is unique, so a retry, an `already_used`
  answer or a "Check again" records it once. A later sync then finds its reset gone and settles the
  redeem instead of adding a second use. The redeem settles the gone reset that expires first after
  the redeem, because the providers use the soonest reset first.
- A sync can store between the provider using a reset and Headroom recording the redeem. It then
  records that reset as used elsewhere. The redeem record runs in one transaction with the syncs,
  and takes over a use elsewhere of the same pool recorded in the last 5 minutes, so the reset
  counts once. It keeps that row's expiry and its estimate of the usage before the reset. The
  takeover matches by pool, not by reset, because the stored resets no longer show which reset the
  redeem used. When a use elsewhere and a redeem in Headroom of the same pool fall within those 5
  minutes, both still count, but their "in Headroom" and "elsewhere" labels can swap.
- A redeem stays pending until a sync settles it, however long that takes. A first sync after days
  offline still counts its reset once.
- Any other use counts as a use "elsewhere", for example in Claude Code or on chatgpt.com.
- Nothing is recorded when the resets could not be read, when Z.AI needs its ZCode sign-in, when
  the account is outside the provider's program, or on the first read of an account.

A use elsewhere in the minutes before its expiry date looks like an expiry: Headroom cannot tell
them apart.

### What a reset gave back

"Gave back" is the used percent of each limit that a reset cleared, added up per kind of limit
(`ResetGivenBack`). When a reset covers several limits of one kind, such as Claude's weekly ones,
the highest counts. A limit that reset on its own since the usage was read counts nothing.

| Reset | Measured from | Marked "about" |
|---|---|---|
| Used in Headroom | The usage stored at the moment of the redeem | When that usage is more than 30 minutes old |
| Used elsewhere | The usage of the last sync before the reset was found gone | Always |

The estimate for a use elsewhere can be wrong in both directions: usage may have grown after the
last sync, and some of the limit may have been used again after the reset. When one sync finds
several uses of one pool, only the first one gets an estimate, because the usage was close to
zero after it. All of this was checked with unit tests and demo data only, not with real
accounts.

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

# Usage-limit resets

Provider-issued resets refill usage limits before their normal reset times.
Headroom lists resets for Codex, Grok, Claude and Z.AI. Users can redeem Codex, Grok, Claude and Z.AI resets and request more Z.AI cards.

| Provider | Shows resets | Can use a reset | Notes |
|---|---|---|---|
| ChatGPT Codex | Yes | Yes | |
| Grok | Yes | Yes | |
| Claude | Yes | Experimental | Claude has not published this API, so it may fail or stop working. |
| Z.AI | After a ZCode sign-in | Yes | Z.AI resets need a separate ZCode sign-in. |
| Muse | No | No | Meta gives Muse subscriptions no banked resets to list or use. Its limits refill at their `resets_at` times, which the reset alerts already cover. |

`Provider.canRedeemResets()` in `:core:model` defines redemption support.
The UI and `SyncedResetProvider` call it.
`Provider.redeemsResetsExperimentally` identifies Claude redemption as experimental. Claude's confirmation step displays an "Experimental" label.

## How the resets are read

Sync reads resets alongside usage with the account's credentials.
`AccountQuotaFetcher` in `:core:data` fetches resets and usage concurrently, then fills `QuotaSnapshot.resets`.

- Failed reset reads do not fail usage sync. The snapshot sets `resetsReadFailed`, and Room retains the previous resets.
  Room saves this flag in `accounts.resetsReadFailed` from database version 8.
  The flag identifies resets older than their usage snapshot. Each successful usage sync updates the flag; failed syncs leave it unchanged.
- Room stores reset JSON in `accounts.resetsJson`, introduced in database version 6.
- Screens read stored snapshots. Opening a screen makes no network request.
- Demo accounts never contact providers. Debug builds use `FakeResetProvider`; release builds give demo accounts no resets.
  `RealOrDemoResetProvider` in `:app` selects the provider for each account.

## Widgets

Home screen and lock screen Bars and Rings widgets show counters for currently usable resets.
The counter displays the number, or "9+" above nine, in a disc matching the ring or bar colour.
Rings place it outside the bottom-right edge to separate it from the arc endpoint.
Bars place it on the avatar's bottom-right corner. Shape widgets omit the counter.

Widgets and the app use `ResetAvailability.availableNow` for the count.
Widgets hide the counter for zero resets or expired sign-ins. Screen readers announce text such as "2 resets available now."
`docs/screenshots/widgets/widget-resets-*.png` contains the widget images.

## Endpoints

Reset clients in `:core:quota` follow the providers' client request formats.
The owner checked the following operations with real accounts using the phone's `HeadroomResets` log:

| Provider | Checked with a real account |
|---|---|
| ChatGPT Codex | List and consume: the consume answered `reset`, and the list then had one credit fewer (3 to 2). |
| Z.AI | Sign-in, status, ask and use: see [Z.AI](#zai). |
| Grok | Not yet: the account had no reset token. |
| Claude | List and use: see [Claude](#claude). |

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

- Pools contain unexpired credits with `status = available`, ordered by earliest expiry.
  A reset refills the 5-hour and weekly limits.
- The consume request names the earliest-expiring credit. Headroom associates that credit with the attempt key, so "Try again" targets it again.
  If listing fails, the request omits the credit ID and lets the server choose.
- `reset` reports success. `already_redeemed` confirms an earlier request with the same key.
  `nothing_to_reset` and `no_credit` end the attempt. Unknown responses retain the key for "Try again".

### Grok

- `GrpcWeb` frames contain a flag byte, a four-byte big-endian length and a payload.
  Trailer frames use flag `0x80` and carry `grpc-status`. Trailers-only responses carry that status in HTTP headers.
- `GrokResetProto` manually reads and writes protobuf messages.
  Field 10 contains tokens. Each token has `token_id` in field 10 and `validity_end`, a Timestamp, in field 30.
- Grok redemption accepts no idempotency key. Headroom associates a token with each attempt key.
  If a retry finds that token missing, Headroom treats the earlier request as successful.
- `SharedPreferencesAttemptTargetStore` saves the association before sending redemption. Restarts therefore preserve the retry target.
  It saves the latest 16 attempt keys and token IDs, without access tokens.
  A failed disk write blocks redemption and allows retry with the same key.
- gRPC status 16 or 7, or HTTP 401 or 403, requires sign-in again.
  gRPC status 8 or HTTP 429 reports rate limiting.

### Claude

- `cedar_ember` in the usage response lists grants.
  Grants with remaining resets and future `ends_at` values become pools. Each pool clears the windows listed in `clears`.
- `next_grant_id` identifies the next grant the server will use. Headroom labels other grants as queued, producing counts such as "1 (+3)".
  Paused grants retain their paused status.
- Accounts outside the program show no resets. Reasons `tier`, `seat` and `tenure` produce short explanations.
- Claude returns grants only for recent Claude Code clients. Requests send the client User-Agent with `ClaudeCodeIdentity.VERSION` from `:core:quota`.
  Run `scripts/update-claude-code-version.sh` for every release to use the latest published version. See [RELEASING.md](RELEASING.md).
- `HeadroomResets` records eligibility reason codes, for example `ineligible cli_version`.
  `surface` and `cli_version` mean the version is too old. Run the update script for those reasons.
  Headroom replaces any reason beyond a short code with `other`, excluding server text from logs.

Claude grant redemption passed a real-account check. The confirmation step still labels it experimental because Claude has not published this API.

- Redemption requires the `next_grant_id` grant, `usable_now`, an unpaused state and a future `ends_at`.
  The sheet offers only that grant as `ResetPoolStatus.Ready`.
  A grant with `use_requires_limit: true` and false `usable_now` displays "At a limit only", disables confirmation and explains why.
  At a limit, `clears` must include the exhausted limit. Headroom relies on server `usable_now` to check that condition.
  Other unusable grants or future `starts_at` values display "Not yet" (`NotUsableYet`). Confirmation stays disabled with an explanation.
- `use_requires_limit: false` allows redemption at any time. Confirmation warns that redemption cannot be undone.
- Before the first send for an attempt key, Headroom reads status and remembers `resets_left`.
  It then reads the profile's organization. Failure to read either blocks redemption and allows retry with the same key.
  A grant missing from status becomes ineligible without sending redemption.
- `request_id` uses the attempt key. Claude accepts `^[A-Za-z0-9_-]{1,64}$`, which includes Headroom's UUID keys.
  Headroom converts other keys to hexadecimal SHA-256, preserving the same ID across retries.
- `reset` reports success and `resets_left`. `already_used` confirms earlier success with the same `request_id`.
  `not_limited`, `cooldown` and `ineligible` consume no reset.
  `unavailable` retains the key and offers "Check again". Unknown results retain the key for "Try again".
  HTTP 429 reports rate limiting. HTTP 401 or 403 requires sign-in again.
- "Check again" reads status without resending redemption. A lower `resets_left` than before the first send confirms success.
  Other results remain unconfirmed. The original count lives only in memory, so a check after restart cannot confirm success.
  A new confirmation within 10 minutes reuses `request_id`. Claude returns `already_used` if the earlier request succeeded.
- Logs replace the organization identifier with `{organization}` in `/api/organizations/{organization}/reset_rate_limits`.
  Logs include `result` and `reason` only as short codes. Other text becomes `other`.

### Z.AI

- `zcode.z.ai` rejects the API key used for usage reads. Reset cards therefore require a separate ZCode sign-in.
  Without it, the detail card shows "Want to see your resets?" and "Sign in to ZCode".
  The Resets tab omits the account.
- Responses use `{code, msg, data}` envelopes. Only numeric `code` 0 reports success.
  Headroom reads the business code before HTTP status because error responses also include envelopes.
  HTTP 401, 403 and 429 retain their meaning, except business code 3301 always reports a decline.
- Status contains `five_hour` and `week` pools for the 5-hour and weekly limits.
  `expire_at` uses epoch milliseconds; Headroom also accepts seconds. Headroom omits expired cards.
  HTTP 401 or 403 requires another ZCode sign-in.
- Before redemption, Headroom checks status. An empty pool returns "no credit" without using a card.
  Retries skip that check if their attempt key already reached `use`.
  A lost response for the last card leaves an empty pool; only the server can confirm whether the attempt succeeded.
  Headroom forgets such keys only after a definite rejection of the first `use`: JSON `used: false`, HTTP 401 or HTTP 403.
  `SharedPreferencesAttemptTargetStore` saves keys before `use`, preserving retries after restart.
  It stores the latest 16 keys and pools, without tokens. Disk-write failure blocks `use` and permits retry with the same key.
- After redemption, `history/read` clears ZCode's shared "unread" flag across the user's plans.
  The request sends no body or target scope. Headroom logs failures without changing the successful redemption result.
- Only tapping "Ask for a reset card" requests new cards. Headroom never polls this operation because Z.AI rate-limits accounts and ZCode already polls.
  Each request uses its own `idempotency_key`. Connection failures and business code 2007 allow retry with that key.
  Business code 3301 declines the request and supplies `next_try_at`. HTTP 429 or business code 429 reports excessive requests.
  Headroom delays further requests per ZCode sign-in, following ZCode's rules:
  - A supplied `next_try_at` delays requests until that time, with a minimum of 5 minutes.
  - A decline without a time delays requests for 10 minutes.
  - A 429 delays requests for 10 minutes.
  Early requests receive a local answer without a server call. Failures create no delay.
  HTTP 401 or 403 makes the sheet offer "Sign in again" for ZCode, matching rejected `use` requests.
- Headroom supports personal plans only and sends `Bigmodel-Target-Type: PERSONAL`.
  Team plans require `TEAM`, `Bigmodel-Organization` and `Bigmodel-Project`. Headroom does not look up those identifiers.
- The implementation follows github.com/zai-org/ZCode, read on 2026-10-04:
  `packages/services/src/usage-stats/providers/bigmodelUsageQuotaProvider.ts` and `packages/ui/src/lib/codingPlanQuotaResetCoordinator.ts`.
  Real-account checks that day used a personal Coding Plan account and the phone's `HeadroomResets` log.
  Checks covered sign-in, status, a granted request increasing each pool from 0 to 5 cards, and a request declined with 3301.
  A weekly redemption returned `used: true` and reduced the weekly card count by one.
  `history/read` returned `{code, msg}` without `data`.
- After a granted request, `ResetCenter` refreshes the stored account snapshot before displaying the result.
  "Use it now" can then find the new card.

### The ZCode sign-in

`ZCodeAuth` in `:core:auth` follows ZCode's CLI sign-in.
`AuthMethods.zCodeSignIn` uses a device-code flow without a displayed code:

1. Send `POST https://zcode.z.ai/api/v1/oauth/cli/init` with `Authorization: Bearer <poll token>` and `{"provider":"zai"}`.
   Generate the poll token from 32 random bytes encoded in hexadecimal.
   The response supplies `flow_id`, a `chat.z.ai` page, `expires_at` and `poll_interval_sec`.
   Reject pages without `https`. Replace the client's poll token if the response includes `poll_token`.
   A past `expires_at` immediately times out sign-in without polling.
2. Open the page in a browser tab and poll `GET …/oauth/cli/poll/{flow_id}`.
   Continue on `pending`, HTTP 408, HTTP 429 or 5xx. Stop on `failed` or other 4xx responses.
   `ready` supplies the ZCode JWT as `token` and a Z.AI OAuth token as `zai.access_token`.
3. Send `POST https://api.z.ai/api/auth/z/login` with `{"token": <Z.AI OAuth token>}`.
   The response supplies a short-lived business token and `expires_in`.

The credential uses the business token as its access token.
Its refresh-token field contains both long-lived tokens as `ZCodeTokens` JSON.
`SignInManager.signInToZCode` saves it alongside the API key under `ZCodeCredential.idFor(accountId)`, the account ID plus `#zcode`.
Both `signInToZCode` and `signInToCursor` reject a secondary sign-in that is already linked to another account or replaces a stored secondary sign-in with a different account.
Sign-out removes both credentials. Sign-in and sign-out cannot run concurrently, preventing late sign-in completion from leaving credentials behind.

The Z.AI refresher in `AuthMethods.refreshers` renews expired business tokens. It never receives the non-expiring API key.
A rejected Z.AI login token requires another ZCode sign-in.

`AccountQuotaFetcher` supplies `ProviderCredentials.zCode` as ready, missing or unavailable, for example while offline.
It resolves credentials during reset reads, concurrently with API-key usage fetching.
ZCode failures never fail usage fetching. `ZCODE_SIGN_IN_WAIT` limits credential resolution, including refresh, to 5 seconds.
After timeout, sync retains stored resets and continues without reading new ones.

`ServiceSignIn` runs this sign-in, and a Grok account's Cursor sign-in with it: one at a time, from the
redemption sheet or the account. The sheet displays browser waiting status or failure reasons.
The page is readied while the user reads, so the tap opens it at once, and a page readied for a
sheet that is gone is forgotten: it expires a few minutes after it is made.
Successful sign-in keeps the sheet open and shows the account's resets.

ZCode finishes on its own server and pins the `redirect_uri` to its CLI callback, so there is no
redirect back to the app and the app polls for the result. Android freezes a background app, which
pauses that poll, so the app waits for the user to come back before it can carry on; the waiting
line says so. The page opens in this app's task, so the browser tab goes away when the user returns.

## Using a reset

- The sheet creates one attempt key per confirmation. "Try again" reuses that key.
- `ResetAttemptMemory` retains unsettled keys for 10 minutes per account and pool.
  `SharedPreferencesResetAttemptStore` saves the account ID, pool, key and time before sending requests, preserving them after restart.
  It stores no tokens.
- Codex keeps credit associations only in memory. Its server's `already_redeemed` response protects retries after restart.
  Grok saves token associations to disk because its server accepts no idempotency key.
- After success, `ResetCenter` waits 2 seconds (`ResetRefresh.DELAY`) and refreshes usage.
  The refill animation uses the new values.
- Accounts with expired sign-ins show faded resets without actions.

## Reminders before a reset expires

Headroom posts on "Reset reminders" about a day before an unused reset expires.
For example, a notification says "Your ChatGPT Codex reset expires tomorrow at 06:18".
"Remind me before a reset expires" in Settings defaults to on and lets users disable reminders.

`ResetReminderPolicy` in `:core:model` applies these rules:

- The sign-in must work, `Provider.canRedeemResets()` must return true, and the pool must be `Ready` or `WaitingForLimit`.
  Each `ResetPool.expiries` entry represents one reset. Pools without expiry dates trigger no reminders.
- Each reset receives one reminder, due `LEAD` (one day) before expiry.
- Headroom sends at most one reminder daily. It lists that day's due resets in expiry order.
  If a reminder already went out that day, new due resets move to 09:00 the next day (`DEFERRED_TIME`).
  Headroom skips resets that expire before that deferred time.

`ResetReminders` in `:core:data` schedules one inexact `ResetReminderAlarm` after each sync or settings change.
Reboots, app updates and time zone changes reschedule it.
The alarm starts `ResetReminderWorker`. The worker first refreshes accounts with due resets to exclude cards used on other devices.

The worker has no network constraint, allowing reminders while offline.
An account's due resets can remain stale after refresh in two cases:

- The refresh fails with `QuotaErrorKind.Network`.
- Usage refresh succeeds but reset reads fail (`QuotaSnapshot.resetsReadFailed`).
  For example, Z.AI usage can succeed while the ZCode credential endpoint times out.

For either case, `ResetReminderRetryPolicy` delays posting and retries after 5, 15, 30 and 60 minutes.
After exhausting retries, the worker uses stored resets.
It also uses stored resets if another wait would leave less than 12 hours (`MIN_NOTICE`) before the earliest stale due reset expires.
Only accounts with stale resets affect that cutoff. Freshly read accounts do not affect it.
Retries do not count as reminders. The daily limit still applies.

`SharedPreferencesResetReminderLedger` records notified resets and the last reminder day.
Its keys combine account ID, pool ID and expiry time. It stores no tokens.

Tapping opens the account with the earliest-expiring reset and scrolls to its Resets card.
`ResetReminderIntents` and `OpenAccountRequest.showResets` handle this navigation.

## Reset history

The Stats tab's "Usage limit resets" card shows used and expired resets from stored history.

### Where it is stored

- Room stores reset events in `headroom.db`'s `reset_events` table, introduced in database version 7.
- Rows contain account ID, provider, pool ID and label, used or expired status, observation time and expiry date.
  Used events also record redemption location, restored quota and the Headroom attempt key where available. Rows contain no tokens.
- Each sync deletes rows older than 365 days. Removing an account deletes its reset events and usage history.

### How a reset is recorded

`ResetEventDetector` in `:core:model` compares each pool's new resets with stored resets.
Providers list one expiry date per reset. The detector matches resets by that date.

- A reset missing after expiry counts as expired.
- A reset missing before expiry counts as used. Without expiry dates, a lower count indicates use.
- `ResetCenter` calls `RoomResetEventLog.redeemed` immediately after successful redemption, before refreshing usage, passing the time the attempt was first sent.
  A unique attempt key prevents duplicate records from retries, `already_used` responses or "Check again", and the first-send time lets a retry confirmed after expiry still match the reset.
  Later syncs match the missing reset to the pending redemption without adding another use.
  Matching selects the missing reset with the earliest expiry after redemption, following provider redemption order.
- A sync can save after provider redemption but before Headroom records success. That sync initially labels the use "elsewhere", or "expired" if confirmation comes after the reset's expiry.
  Redemption recording shares a transaction boundary with sync and replaces an expired reset from the same pool whose expiry followed the first attempt, or a same-pool external use recorded within the last 5 minutes.
  The replacement preserves the expiry date and pre-reset usage estimate.
  Matching uses the pool because stored resets no longer identify the redeemed card.
  Separate external and Headroom redemptions within those 5 minutes still count separately, but their location labels can swap.
- Pending redemptions wait for sync without a timeout. Sync after several offline days still counts each reset once.
- Other uses receive the "elsewhere" label, including Claude Code and chatgpt.com redemptions.
- The detector records no events after failed reads, missing ZCode sign-in, program ineligibility or an account's first reset read.

Headroom cannot distinguish external use shortly before expiry from expiry itself.

### What a reset gave back

`ResetGivenBack` calculates cleared usage percentages by window kind for the "Gave back" value.
For several limits of one kind, such as Claude weekly limits, it uses the highest percentage.
It excludes limits that reset normally after the last usage read, then adds the values across kinds.

| Reset | Measured from | Marked "about" |
|---|---|---|
| Used in Headroom | The usage stored at the moment of the redeem | When that usage is more than 30 minutes old |
| Used elsewhere | The usage of the last sync before the reset was found gone | Always |

External-use estimates can be too high or too low. Usage can increase after the last sync and resume after the reset.
When sync detects several uses in one pool, only the first receives an estimate because the first reset left usage near zero.
Unit tests and demo data cover these calculations. Real-account checks remain incomplete.

## Logs

Reset clients write one logcat line per request with the `HeadroomResets` tag:

```
adb logcat -s HeadroomResets
```

- Debug lines include provider, operation, endpoint path without query, HTTP and gRPC statuses, provider response code, counts, attempt key and request duration.
  Redemption lines also include pool and a short account-ID hash.
- Warning lines identify unreadable responses and expected fields. They include a redacted body limited to 400 characters.
- Logs exclude tokens, cookies, `Authorization` headers, emails and raw account IDs.
  `ResetLogRedaction` removes those values from bodies. `ResetLogTest` checks redaction.

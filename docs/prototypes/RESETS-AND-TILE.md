# Reset and tile prototype

This page records the version 1.1 review prototype. It uses fake data and makes no provider network calls.
`./gradlew :app:recordRoborazziDebug` writes prototype screenshots to `app/build/outputs/roborazzi/prototypes`.
The repository excludes those screenshots and the review recordings and GIFs.

> [RESETS.md](../RESETS.md) describes the shipped feature and its current settings.
> The prototype's combined Claude/Z.AI experimental switch no longer applies.
> Codex, Grok and Z.AI support redemption. Z.AI requires ZCode sign-in.
> Claude redemption uses its own experimental setting in the current app.

## Run the prototype

1. Install a debug build.
2. Open Settings > **Reset prototypes** near the end of Settings. This debug-only page runs each flow.
3. Open a demo Claude, ChatGPT Codex or Grok account, or the Resets tab, to view fake resets.
   Redemption changes demo usage on the next refresh, which also animates the overview card.

## The Quick Settings tile

`HeadroomTileService` displays the "Headroom" tile with the launcher's monochrome ring icon.
A tap opens `MainActivity` through `startActivityAndCollapse(PendingIntent)`.
On a locked device, `unlockAndRun` asks the user to unlock first.

The owner selected a permanent subtitle. Settings stores the choice in `TileSettings`:

| Choice | Example | Default |
|---|---|---|
| Show the next reset | Grok · in 15h 26m | Yes |
| Show the tightest quota | Grok · 88% used | No |
| No data | Open Headroom | Automatic fallback |

The app calls `TileService.requestListeningState` after data, subtitle choice or used/left settings change.
"Add to Quick Settings" calls `StatusBarManager.requestAddTileService` and displays "Added", "Already added", "Not added" or a failure.

Emulator checks covered adding the tile, both subtitles and opening Headroom.
Locked-device tapping remained untested because the emulator had no screen lock.
The empty-data fallback had unit-test coverage only because demo mode always supplied accounts.

## Reset display decisions

The prototype shows each supported provider's reset count, expiry and scope in account details and the Resets tab.
Overview cards omit a reset chip. Their bars still animate after redemption.

Counts show currently available resets followed by queued resets, for example "1 (+3)" or "2" when none are queued.
Screen readers announce "1 reset available now, 3 more queued".
Details retain each grant's "1 of 2" count and label other grants "Queued". The queued-count presentation remains an open decision.

Queued counts show this tooltip: "1 reset available now. 3 more are queued: they should unlock after these are used."
The word "should" preserves uncertainty about when grants unlock.
Material's `TooltipBox` and `PlainTooltip` support hover and long press. Tapping the count button calls `show()`.
The tooltip hides its text from screen readers to avoid reading the count twice.
Emulator checks covered taps. Source inspection confirmed hover through `enableUserInput`; nobody tested it with a mouse.

Codex and Grok redemption defaults to on.
The prototype's "Redeem Claude and Z.AI resets" setting enables their experimental calls, which lacked real-account tests.
Its supporting text discloses that limit.

- When disabled, those providers retain read-only resets.
  A footer below all grants replaces "Use a reset" with "Redeeming is experimental. Turn it on in Settings."
  Its link opens the setting. The Resets tab also omits redemption buttons and shows one linked footer note.
- When enabled, confirmation displays "Experimental".
- Z.AI remains in the prototype's 1.1 scope and requires separate ZCode sign-in even for listing cards.
  Without sign-in, account details show "Want to see your resets?" and a stub "Sign in to ZCode" button.
  The Resets tab omits the account because it lists usable resets.
- After every successful redemption, the app waits two seconds (`ResetRefresh.DELAY`) and refreshes the account.
  Bars shimmer throughout the wait and fetch.

## Provider data

| Provider | Pools | Scope line | Prototype support |
|---|---|---|---|
| ChatGPT Codex | One | "Resets your current 5-hour and weekly limits." Free and Go use monthly limits. | Redemption defaults to on. |
| Grok | One | "Resets your current weekly usage pool." | Redemption defaults to on. |
| Z.AI | Separate 5-hour and weekly pools | Each pool has its own line. | Experimental redemption requires ZCode sign-in. Users can request reset cards. |
| Claude | One per grant | `clears` supplies text such as "Refills your 5-hour session and your weekly limit." | Experimental redemption requires live User-Agent and organization-ID checks. It uses the existing Claude sign-in. |

GitHub Copilot, Kimi Code, OpenCode Go and JetBrains AI supply no resets. Headroom omits reset controls for them.

## Shared model and UI

`Resets.kt` and `RedeemSession.kt` in `:core:model` define shared types.
Providers supply pools and text to the shared UI.

| Type | Role |
|---|---|
| `ResetScope` | It identifies restored limits by window ID for Claude, window kind for Codex/Grok/Z.AI, or unknown scope. Unknown scope displays "Resets your current usage limits". |
| `ResetPool` | It stores ID, label, remaining count, optional total, optional expiry dates, scope, status and timing. |
| `ResetAvailability` | It stores account pools, Z.AI's `requiresSignIn` and `canAskForMore`, and Claude's `ineligibleReason`. Ineligible accounts show one detail-card explanation. |
| `ResetProvider` | It defines `availability(account)`, `redeem(account, poolId, attemptKey)`, `check(account, poolId, attemptKey)` and `askForMore(account)`. |
| `ResetProviders` | It selects the account's provider. Other providers use `NoResets`. |
| `RedeemSession` | It holds one sheet's state in plain Kotlin with unit tests. |

Pool statuses are ready, waiting for a limit, queued and paused. Claude grants other than the next grant are queued.
Timing allows redemption at a limit only or at any time. The latter adds an irreversible-action warning.

All providers use `RedeemOutcome`:

- Success includes remaining resets and whether the key repeated an earlier request.
- Terminal outcomes include nothing to reset, no credit, cooldown and ineligible.
- Other outcomes include unconfirmed, rate limited, sign in again, failed and unsupported.
- Codex `already_redeemed` and Claude `already_used` count as success.

`ResetAttemptMemory` retains unsettled keys for ten minutes per account and pool.
A confirmation within that interval reuses the key. This follows Claude's rule; other provider rules remained unknown during prototyping.

`ResetCenter` reads availability for every account and rereads it after each redemption or card request.
After success, it waits `ResetRefresh.DELAY` and refreshes usage. `refreshing` identifies waiting accounts.
`ResetCopy` supplies provider text.

Debug redemption calls `FakeQuotaRepository.resetOnServer`. The next refresh returns empty cleared windows and triggers normal data-change animations.
Debug builds use `FakeResetProvider` and `app/src/debug` scenarios.
Prototype release builds use `NoResets` until real clients exist.

## Expired sign-ins

Expired accounts retain faded last-known usage and equally old reset data.
The prototype disables reset actions until sign-in succeeds:

- The faded detail card retains counts but omits redemption, card requests, experimental notes and ZCode sign-in.
- The Resets tab excludes the account from usable and upcoming resets.
- Overview bars and the detail ring neither shimmer nor refill.

`ExpiredAccountResetsTest` covers the detail card and Resets tab rules.

## Sheet layout and motion

The redemption UI uses `ModalBottomSheet`. `AnimatedContent` fades and expands incoming steps while the sheet resizes with a spatial spring.
Reduced motion uses crossfades only. Wide screens limit the sheet to 560 dp.
Each step title is a heading and polite live region for screen readers.

All sheet sections start height animation on the same frame and use the same spring.
Each gap belongs to the section below it and animates with that section.
The earlier implementation changed the gap above usage bars in one frame, moving the top edge by 20 dp.
The revised bars expand from their top without clipping. They remain fully visible and overlap incoming text for about 150 ms during expansion.
`RedeemSheetResizeTest` follows the top edge frame by frame through every flow.
It fails if one frame moves more than a quarter of the total distance.

## Redemption steps

1. Choose a pool when several pools have resets, as with Z.AI.
   Two large cards show current usage for the limits each pool clears.
2. Confirm using one bar per account window, in used or remaining mode.
   Affected windows display at full opacity; other windows dim.
   The sheet shows "Use 1 of your 2 resets?", scope and the earliest-expiring reset's date.
   Claude adds "Your weekly limit still resets on its usual day."
   Anytime redemption adds an irreversible-action warning. Limit-only redemption disables confirmation and explains why when no limit is reached.
   Buttons show "Use a reset" and "Not now", or "Back" for pool selection.
3. Wait while affected bars shimmer and the expressive loading indicator displays "Resetting your usage…" or "Checking again…".
   The sheet also blocks dismissal during "Updating your usage…" after success.
   Swipe-down, handle drag, scrim tap, Back key and predictive Back do nothing while busy, following the owner's decision.
   Dismissal resumes after the busy states end.
4. Read the outcome and use its available action, as listed below.
5. For Z.AI card requests, the sheet shows "Asking Z.AI for a reset card…".
   It then shows granted with "Use it now", not yet with the retry time, or throttled.
   Requests run only on user action, without polling.
6. Missing ZCode sign-in shows an explanation and "Sign in to ZCode".

`RedeemSheetDismissTest` checks Back, swipe-down and scrim taps in every busy state, then confirms Back closes the completed sheet.
`recording-dismiss-attempts.gif` manually checks all five dismissal methods with "Claude · slow reset".

| Outcome | Sheet behaviour |
|---|---|
| Success | It immediately displays "Usage reset. 1 reset left." and Done. Bars keep shimmering during "Updating your usage…". |
| Nothing to reset, no credit, cooldown, ineligible | It displays an explanation and Done. Z.AI also offers "Ask for a reset card" when credit is empty. |
| Failed or rate limited | "Try again" reuses the attempt key. |
| Unconfirmed | "Check again" reads status with the same key without resending redemption. |
| Sign in again | It opens the accounts screen. |

## Successful reset effects

After fresh usage arrives, cleared bars animate 160 ms apart to 0% used or 100% remaining.
A critically damped reset spring prevents overshoot. Bars keep their size without a scale effect.
Enabled "Reset confetti" starts confetti from the bars when refill begins.

`BarGloss` starts after each bar settles. A narrow, angled light band crosses left to right, clipped to the rounded bar.
Four-pointed stars appear in and near the bar, rotate slightly and fade.
Each sweep lasts 800 ms. Sweeps follow the bars' staggered settling times.

`DelightLight.kt` supplies the same iridescent colours for gloss and refresh shimmer, following the owner's round-5 request:

- Sweeps use pastel pink, peach, pale yellow, mint, sky blue and lavender around a brighter centre. Hues shift during movement.
- Each star uses a shifting hue from that spectrum and a small bright centre.
- Effect colours stay the same across app palettes.
- Light themes draw full-strength colours with coloured centres. Stars use darker, same-hue halos to remain visible on pale surfaces.
- Dark themes use additive light. Sweeps approach white to avoid low-contrast colour; stars retain full colour.
- The refresh shimmer retains its approved intensity, motion, timing and star lifetime. The revision changes only its colours.

"Reset confetti" also controls gloss. "Refresh shimmer" does not control gloss.
Reduced motion dims busy bars and crossfades new values without springs or stagger. It disables shimmer, confetti and gloss.

Underlying screens retain old values while the sheet stays open.
After closing, the detail ring and overview bars animate to new usage.
Overview cards start from their last displayed values, including cards returning on screen after detail-screen redemption.
A weekly usage drop triggers the overview's reset spring and "Just reset" label.

Only the frontmost surface plays confetti for a reset, following the owner's decision.
The sheet calls `Delights.claimReset` for the account and cleared windows.
The overview suppresses its confetti during and after that sheet. A normal sync without a sheet leaves the overview free to play confetti.
Nested delight hosts share claims. Each claim applies once and expires after five minutes.
`HomeDelightsTest` covers sheet claims and normal-sync effects.

## Open prototype decisions

1. Must details retain "1 of 2" per grant alongside the card's "1 (+3)" count?
2. Who will test Claude and Z.AI redemption with real accounts before removing the prototype's experimental restriction?

## Work required after the prototype

The prototype still requires real clients and persistent state. These items describe the prototype's limits; [RESETS.md](../RESETS.md) records the shipped implementation.

- Add clients in `:core:quota` beside quota fetchers.
  Codex needs `wham/usage`, `rate-limit-reset-credits` and `consume`, using `redeem_request_id` and earliest-expiring `credit_id`.
  Grok needs gRPC-web `GetRemainingResets` and `RedeemReset`, then a refresh after about two seconds.
  Z.AI needs `:core:auth` ZCode sign-in, `reset/status`, `reset/use` and `reset/opportunity`.
  Claude needs `next_grant_id` grants and redemption after live User-Agent and organization-ID checks.
- Persist retry keys beyond the process. Prototype `ResetAttemptMemory` keeps them only in memory.
- Save availability with Room snapshots during sync so widgets and the tile can read it.
  The prototype reads availability through `ResetCenter`.
- Handle failed refreshes and unchanged usage in the sheet. Prototype refresh always succeeds.
- Replace the ZCode stub button with sign-in.
- Add fixture parsing and `MockWebServer` tests for each client.
  Add sheet retry, status-check, busy-dismissal and experimental-setting tests.
  Existing tests cover `RedeemSessionTest`, `FakeQuotaRepositoryTest`, `TileSubtitleTest`, `HomeDelightsTest` and `RedeemSheetDismissTest`.

## Screenshots and recordings

`./gradlew :app:recordRoborazziDebug` writes screenshots to `app/build/outputs/roborazzi/prototypes`.
Review artifacts cover these states:

| Files | Contents |
|---|---|
| `detail-*.png`, `resets-tab*.png`, `card-*.png` | Reset placements include read-only `card-claude.png`, light/dark ZCode sign-in, and `card-overview-refreshing*.png` with reduced motion. |
| `sheet-*.png` | Every scenario step covers light/dark themes, Grape/Lagoon palettes, remaining mode and expanded width. Images include "Updating your usage…", reduced-motion busy states and staggered `sheet-*-refilling*.png`. |
| `settings-tile*.png`, `prototypes-page.png` | Settings tile choices and the debug page appear here. |
| `gloss-strip-{used,left}{,-dark}.png`, `gloss-strip-left-lemon.png` | Claude gloss strips contain 14 frames at 112 ms intervals, down column one then column two. The last uses Lemon. `RedeemSheetMomentsScreenshotTest`'s `gloss*` tests write source frames to `app/build/gloss`; separate processing builds strips. |
| `shimmer-strip-{light,dark}.png` | Overview shimmer strips contain 12 frames at 128 ms intervals, along row one then row two. `ScreenshotTest`'s `shimmerFrames*` tests write frames to `app/build/shimmer`. |
| `delight-palettes.png` | `DelightPalettesScreenshotTest` shows gloss on full/empty bars and shimmer on a card in every palette. Light appears left; dark appears right. |
| `emulator-*.png` | Images cover adding the tile, "Added", subtitle settings, both subtitles and `emulator-count-tooltip.png`. |
| `emulator-gloss-used-frames.png`, `emulator-gloss-left-frames.png` | Cropped bar frames use 250 ms intervals. Round-4 captures retain the earlier theme-primary gloss colour. |

The `recording-*.gif` files record these flows:

- `gloss-claude-used` and `gloss-claude-left` show success, confetti, refill and gloss in used and remaining modes.
  These round-4 recordings use theme-primary gloss. Iridescent gloss has screenshot strips only.
- Recordings made before gloss retain the earlier scale and glow ending.
- `success-claude-used` shows sheet refill followed by detail-ring and overview animations after closing.
- `success-codex-left` shows Codex success in remaining mode.
- `unconfirmed-check-again` shows an unconfirmed attempt and status check.
- `success-claude-reduced` shows success with reduced motion.
- `dismiss-attempts` checks dismissal during redemption and updating, then closes the completed sheet with Back.
- `one-confetti-claude` shows success followed by overview-bar reduction without duplicate confetti.
- Round-6 `resize-zai`, `resize-codex`, `resize-grok`, `resize-claude`, `resize-zai-ask` and `resize-zai-sign-in` show every step's resizing.
  `slow-resize-zai` slows animations fivefold. Emulator software rendering drops frames and can make normal-speed transitions stutter initially.
  The slowed recording shows continuous size changes.
- Round-1 recordings show Grok failure and retry, Z.AI pool selection and card requests before the revised refill, and a home-screen tile tap.

# Prototype: usage limit resets and the Quick Settings tile

This is a prototype for 1.1, for the owner to review. It runs on fake data only. It makes no
network calls to any provider. Its screenshots are not kept in the repository: `./gradlew
:app:recordRoborazziDebug` writes them to `app/build/outputs/roborazzi/prototypes`. The screen
recordings and GIFs it mentions were made for the review and are not in the repository either.

> This page records the prototype. The feature that shipped is described in
> [docs/RESETS.md](../RESETS.md). It differs from the prototype: Codex and Grok resets can be used,
> Claude's are shown for information only, and Z.AI resets are not shown. The experimental setting
> and its notes were removed, so the parts of this page about them no longer apply.

## How to try it

1. Install a debug build.
2. Open Settings. Near the end, **Reset prototypes** opens a page that plays every flow. Only
   debug builds have that page.
3. The demo accounts also have fake resets: Claude, ChatGPT Codex and Grok. They show in the
   account detail and on the Resets tab. A redeem really changes the demo account's usage on the
   next refresh, so the overview card animates too.

## The Quick Settings tile

- `HeadroomTileService` is a tile labelled "Headroom", with the launcher's monochrome ring as its
  icon.
- A tap opens `MainActivity` with `startActivityAndCollapse(PendingIntent)`. When the device is
  locked, the tap first asks the user to unlock (`unlockAndRun`).
- The tile always shows a subtitle (owner decision). Settings offers two choices under the "Add to
  Quick Settings" row: "Show the next reset" ("Grok · in 15h 26m"), which is the default, and "Show
  the tightest quota" ("Grok · 88% used"). The choice is stored in `TileSettings`. With no data
  yet, the tile says "Open Headroom". The app asks the tile to update
  (`TileService.requestListeningState`) when the data, the choice or the used/left setting changes.
- The "Add to Quick Settings" row calls `StatusBarManager.requestAddTileService` and shows the
  answer: "Added", "Already added", "Not added", or a failure.

Checked on the emulator: adding the tile, both subtitle choices, and opening the app from the tile.
Not checked on a device: a tap while the device is locked (the emulator has no screen lock), and
the "Open Headroom" fallback (demo mode always has accounts; a unit test covers the empty case).

## Resets: the owner's decisions

- Headroom always shows the resets of every supported provider: the count, the expiry and the
  scope. They show in the account detail's Resets card and on the Resets tab. The overview card
  has no resets chip; its bars still animate after a reset.
- A count shows the resets available now, with the queued ones in brackets: "1 (+3)". With nothing
  queued it is just "2". Screen readers hear "1 reset available now, 3 more queued". The detail
  card keeps each grant's own "1 of 2", with "Queued" on the others; how to count queued grants
  is still open.
- When something is queued, the count has a tooltip: "1 reset available now. 3 more are queued:
  they should unlock after these are used." We have not confirmed when queued grants unlock, so
  it says "should". It is Material's `TooltipBox` with a `PlainTooltip`. It shows on mouse hover
  and on long press (the `TooltipBox` handles both; its source in this Material version handles
  hover through `enableUserInput`), and on a plain tap, because the count is a button that calls
  `show()`. The tooltip text is hidden from screen readers, so the count is not read twice.
  Checked on the emulator by tapping; hover was read in the source, not tried with a mouse.
- Redeeming ChatGPT Codex and Grok resets is on by default.
- Redeeming Claude and Z.AI resets is experimental, because their redeem calls are not verified.
  The Settings row "Redeem Claude and Z.AI resets" turns it on. Its supporting line says the calls
  have not been tested with real accounts.
  - When it is off, those providers still show their resets. In place of the "Use a reset" button,
    a quiet line says "Redeeming is experimental. Turn it on in Settings.", with a link that opens
    Settings at that row. The line sits in a footer band below all the grants, so it reads as
    applying to the whole card. On the Resets tab those rows have no button, and one note in the
    card's footer links to the setting.
  - When it is on, the confirm step shows a small "Experimental" label.
- Z.AI stays in 1.1. It needs a separate ZCode sign-in even to show its resets. Without it, the
  detail card shows a notice, "Want to see your resets?", with a "Sign in to ZCode" button (a stub
  in the prototype). The Resets tab leaves it out: that section lists only resets that can be used,
  and a sign-in row there would be noise.
- After every successful redeem, for every provider, the app waits two seconds
  (`ResetRefresh.DELAY`, one shared constant), then refreshes the account. The bars shimmer during
  the wait and the fetch, so it reads as part of the moment, not as a slow app.

## What each provider supplies

| Provider | Pools | Scope line | Status |
|---|---|---|---|
| ChatGPT Codex | One | "Resets your current 5-hour and weekly limits." Monthly on Free and Go. | Redeem on by default |
| Grok | One | "Resets your current weekly usage pool." | Redeem on by default |
| Z.AI | Two: 5-hour and weekly | One line per pool | Experimental. Needs a ZCode sign-in. Can ask for a reset card. |
| Claude | One per grant | From the grant's `clears`, for example "Refills your 5-hour session and your weekly limit." | Experimental. Uses the existing Claude sign-in. Needs a live check of the User-Agent and the org id. |

GitHub Copilot, Kimi Code, OpenCode Go and JetBrains AI have no resets, so Headroom shows nothing
for them.

## The framework

The types are in `:core:model` (`Resets.kt` and `RedeemSession.kt`). The UI is shared. Each
provider only supplies its pools and its copy.

- `ResetScope` says which limits a reset restores: by window id (Claude's `clears`), by kind of
  window (Codex, Grok, Z.AI), or unknown. Unknown shows as "Resets your current usage limits".
- `ResetPool` is one kind of reset: an id, a label, how many are left, the optional total, the
  expiry dates (can be empty), the scope, a status and a timing.
  - Status: ready, waiting for a limit, queued (Claude grants that are not next), or paused.
  - Timing: at a limit only, or at any time. "At any time" makes the confirm step say that the
    reset cannot be undone.
- `ResetAvailability` is an account's pools, plus `requiresSignIn` (Z.AI), `canAskForMore` (Z.AI
  reset cards) and `ineligibleReason` (Claude). An ineligible account shows only a quiet line in
  the detail card.
- `ResetProvider` has `availability(account)`, `redeem(account, poolId, attemptKey)`,
  `check(account, poolId, attemptKey)` and `askForMore(account)`. `ResetProviders` sends each
  account to its provider. `NoResets` is every other provider.
- `RedeemOutcome` is shared by every provider: success (with the resets left, and whether the key
  was a replay), nothing to reset, no credit, cooldown, ineligible, unconfirmed, rate limited,
  sign in again, failed, and unsupported. A replayed key (Codex `already_redeemed`, Claude
  `already_used`) counts as success.
- `RedeemSession` is the state of one sheet. It is plain Kotlin with unit tests.
- `ResetAttemptMemory` keeps the key of each unsettled attempt for ten minutes, per account and
  pool. A new confirm within that time sends the same key. Claude's rule is ten minutes; the other
  providers use the same until their rules are known.
- In the app, `ResetCenter` reads the availability of every account and reads it again after each
  redeem and each ask. After a success it waits `ResetRefresh.DELAY` and refreshes the account's
  usage, as any refresh does; `refreshing` says which accounts are waiting. `ResetCopy` holds each
  provider's words.
- In debug builds a redeem calls `FakeQuotaRepository.resetOnServer`, which plays the provider's
  side: the next refresh reads the cleared windows as empty. Nothing animates on a special path;
  the screens animate because the data changed.
- Debug builds get `FakeResetProvider` and the scenarios in `app/src/debug`. Release builds get
  `NoResets`, so they show no resets until the real clients exist.

## Accounts whose sign-in expired

An account whose sign-in expired shows its last good data, faded (from `main`). Its resets are as
old as its usage, so the app offers none of them until the user signs in again:

- The detail's Resets card is faded like the rest of the account's data. It keeps its counts but
  has no actions: no "Use a reset", no "Ask for a reset card", no experimental note and no ZCode
  sign-in button.
- The Resets tab does not list the account under the resets you can use, just as it does not list
  its upcoming resets.
- Its bars neither shimmer nor refill, on the overview card and in the detail ring.

`ExpiredAccountResetsTest` covers the first two.

## The flow, step by step

The redeem sheet is a `ModalBottomSheet`. Its steps change with `AnimatedContent`: the new step
fades and grows in, and the sheet resizes on the spatial spring. With reduced motion the steps
only crossfade.

The sheet's height changes smoothly between steps, with no jumps (round 6 fix). Every part of the
sheet changes its height on the same spring, starting on the same frame. Each gap belongs to the
part below it, so a gap opens and closes with its part. Before this fix, the gap above the usage
bars appeared or vanished in one frame, and the sheet's top edge jumped by 20 dp. The usage bars
grow from their top and are not clipped, so both bars show whole from the first frame. While
their space opens, they briefly overlap the incoming step's text, for about 150 ms.
`RedeemSheetResizeTest` follows the sheet's top edge frame by frame through every flow, and fails
if one frame moves it by more than a quarter of the whole way. On wide screens the sheet is at most 560 dp wide. Every step title is a heading
and a polite live region, so screen readers announce each change.

1. **Choose a pool.** Only when more than one pool has resets (Z.AI). Two large cards, each with
   the current usage of the limits it resets.
2. **Confirm.** One bar per window of the account, in used or left mode. The windows the reset
   restores are drawn in full, the others dimmed. "Use 1 of your 2 resets?", the scope line, and which reset is used (the one that expires first, with
   its date). Claude adds "Your weekly limit still resets on its usual day." A reset that works at
   any time adds a warning that it cannot be undone. A reset that needs a limit, when the user is
   not at one, shows why and disables the button. Buttons: "Use a reset" and "Not now" (or "Back"
   to the pool choice).
3. **Resetting.** The bars being reset shimmer: a band of light sweeps along them. Below them, the
   expressive loading indicator and "Resetting your usage…". "Checking again…" looks the same.
   While either runs, and while "Updating your usage…" shows after a success, nothing closes the
   sheet (owner decision): a swipe down, a drag on the handle, a tap on the scrim, back as a key
   and the predictive back gesture all do nothing. Once the outcome shows, they work again.
   `RedeemSheetDismissTest` tries back, a swipe down and a scrim tap in each busy state, and checks
   that back closes the sheet afterwards. The recording `recording-dismiss-attempts.gif` tries all
   five ways by hand, on the debug scenario "Claude · slow reset".
4. **Outcome.**
   - Success: "Usage reset. 1 reset left." and Done, at once. While the refresh brings in the new
     usage, the bars keep shimmering and a line says "Updating your usage…". Then the cleared bars
     refill one after the other, 160 ms apart: down to 0% in used mode, up to 100% in left mode.
     The value moves on the critically damped reset spring, so the number never passes its new
     value. The bars do not pop or grow.
   - Confetti bursts from the bars as they start to refill, when the "Reset confetti" delight is
     on.
   - The gloss (`BarGloss`): once a bar settles, a narrow band of light sweeps it once from left
     to right. The band leans a little and is clipped to the bar's rounded shape. As it passes, a
     few four-pointed stars light up in and just around the bar, turn a little and fade. One sweep
     takes 800 ms, and each bar starts its own after it settles, so the sweeps follow the refill's
     stagger.
   - The light is iridescent, like light on a holographic sticker (owner request, round 5). The
     gloss and the refresh shimmer share it (`DelightLight.kt`), so they stay one family:
     - A sweep carries a soft pastel spectrum across its width: pink, peach, butter yellow, mint,
       sky blue and lavender, around a brighter core. Its hues drift as it moves.
     - Each star takes its own hue from the same spectrum, which shifts a little as it twinkles,
       and has a small bright core.
     - The light never uses the theme's colours, so it is the same in every palette.
     - Light themes: the colours are drawn normally at full strength, the cores keep some colour,
       and each star has a soft halo of its own hue, deeper, that lifts it off pale surfaces.
     - Dark themes: the light is added, as before. The sweeps are washed toward white, because
       faint colour added to a dark surface reads as murky. The stars keep their full colour.
     - The refresh shimmer keeps its approved strength, motion, timing and star life. Only its
       colours changed.
   - Which setting controls the gloss (my choice): the "Reset confetti" delight. The gloss is part
     of the reset's celebration, so it plays exactly when the confetti would. The "Refresh
     shimmer" delight does not affect it.
   - After the sheet closes: the screens under it kept the old numbers while it was open. Now the
     new usage flows in, so the detail ring and the overview card's bars animate to it where the
     user looks. Overview cards also start from the value they last showed, so a card that comes
     back on screen after a reset from the detail plays the change too. A weekly drop counts as a
     reset for the overview, so the card drains on the reset spring and shows "Just reset".
   - Only one confetti (owner decision): only the front-most surface plays a reset's confetti.
     When the sheet plays it, it claims the reset for that account and each window it cleared
     (`Delights.claimReset`). The overview then finds the claim and stays quiet, while the sheet is
     open and after it closes. A reset that a normal sync brings, with no sheet, is not claimed, so
     the overview plays its confetti as before. Delight hosts inside other hosts share the claims.
     A claim is used once, and lapses after five minutes. `HomeDelightsTest` covers both cases.
   - With reduced motion: the working bars only dim (no shimmer), the new values crossfade in with
     no spring and no stagger, and there is no confetti and no gloss.
   - Nothing to reset, no credit, cooldown, ineligible: a short explanation and Done. With no
     credit, Z.AI also offers "Ask for a reset card".
   - Failed or rate limited: Try again sends the same key.
   - Unconfirmed: "Check again" reads the status with the same key. It never sends the reset again.
   - Sign in again: opens the accounts screen.
5. **Ask for a reset card** (Z.AI): "Asking Z.AI for a reset card…", then the answer: granted
   (with "Use it now"), not yet (with the time to ask again), or throttled. It only runs when the
   user asks. It is never polled.
6. **ZCode sign-in missing** (Z.AI): explains the separate sign-in and offers "Sign in to ZCode".

## Open questions for the owner

1. Queued grants: the detail keeps "1 of 2" per grant next to the card's "1 (+3)". Keep both?
2. Claude and Z.AI redeeming stays behind the experimental setting until someone checks it with
   real accounts. Who checks it, and when does it leave the experimental group?

## What the real implementation still needs

- **Network clients per provider**, in `:core:quota`, next to the fetchers:
  - Codex: `wham/usage` for the count, `rate-limit-reset-credits` for the credits, and `consume`
    with `redeem_request_id` and the soonest-expiring `credit_id`.
  - Grok: gRPC-web `GetRemainingResets` and `RedeemReset`, then a refetch after about two seconds.
  - Z.AI: a ZCode sign-in in `:core:auth`, then `reset/status`, `reset/use` and
    `reset/opportunity`.
  - Claude: the grants with `next_grant_id`, and the redeem call, after a live check of the
    User-Agent and the org id.
- **Retry-key memory that survives the process.** Today `ResetAttemptMemory` is in memory only.
- **Availability in the sync.** Store the resets next to each snapshot in Room, instead of reading
  them from `ResetCenter`, so the widgets and the tile can show them too.
- **A refresh that can fail.** The prototype's refresh always works. A real one can fail or come
  back with unchanged usage; the sheet then needs a line for that.
- **The ZCode sign-in** behind the stub button.
- **Tests:** parsing tests with recorded fixtures and `MockWebServer` for each client; UI tests for
  the sheet (dismissal blocked while busy, Try again, Check again); and tests for the experimental
  setting. Today the model (`RedeemSessionTest`, `FakeQuotaRepositoryTest`), the tile subtitle
  (`TileSubtitleTest`), the confetti claims (`HomeDelightsTest`) and the sheet's dismissal
  (`RedeemSheetDismissTest`) have tests.

## Screenshots and recordings

Roborazzi records them with `./gradlew :app:recordRoborazziDebug` into
`app/build/outputs/roborazzi/prototypes`:

- `detail-*.png`, `resets-tab*.png`, `card-*.png`: where resets appear. This includes the Claude
  card with no action (`card-claude.png`), the Z.AI sign-in notice in light and dark, and an overview card refreshing (`card-overview-refreshing*.png`, also with
  reduced motion).
- `sheet-*.png`: every step of every scenario, in light and dark, plus the Grape and Lagoon
  palettes, left mode, an expanded width, "Updating your usage…", the resetting and updating
  states with reduced motion, and the refill part-way through its stagger
  (`sheet-*-refilling*.png`).
- `settings-tile*.png` and `prototypes-page.png`: the Settings rows (the tile choice) and the
  debug page.
- `gloss-strip-{used,left}{,-dark}.png` and `gloss-strip-left-lemon.png`: the gloss on the Claude
  sheet, 14 frames 112 ms apart, read down the first column and then the second. The last one uses
  the Lemon palette. Roborazzi does not write these: the `gloss*` tests in
  `RedeemSheetMomentsScreenshotTest` write the frames to `app/build/gloss`, and the strips are
  made from them.
- `shimmer-strip-{light,dark}.png`: the refresh shimmer over the overview, 12 frames 128 ms apart,
  read along the first row and then the second. The `shimmerFrames*` tests in `ScreenshotTest`
  write the frames to `app/build/shimmer`.
- `delight-palettes.png`: the gloss on a full and an empty bar, and the shimmer on a card, in
  every palette, light on the left and dark on the right (`DelightPalettesScreenshotTest`).
- `emulator-*.png`: the add-tile prompt, the "Added" row, the tile choice in Settings, the tile
  with each subtitle, and the count's tooltip (`emulator-count-tooltip.png`).
  `emulator-gloss-used-frames.png` and `emulator-gloss-left-frames.png` are frames from the gloss
  recordings, a quarter of a second apart, cropped to the sheet's bars. They are from round 4, so
  they show the gloss in the theme's primary colour, before the iridescent light.
- `recording-*.gif`:
  - `gloss-claude-used` and `gloss-claude-left`: the Claude success in used and left mode, with
    the confetti, the refill and the gloss on each bar. They are from round 4, so the gloss is
    still in the theme's primary colour. The iridescent light has no emulator recording yet: see
    the strips above.
  - The recordings below were made before the gloss, so their refill still ends in the old pop and
    glow.
  - `success-claude-used`: the Claude success in used mode, with the sheet's refill and then the
    detail ring and the overview card animating after the sheet closes.
  - `success-codex-left`: the Codex success in left mode.
  - `unconfirmed-check-again`: an unconfirmed attempt, then "Check again".
  - `success-claude-reduced`: a success with reduced motion on.
  - `dismiss-attempts`: every way to dismiss the sheet, tried while the reset runs and while the
    new usage comes in, then back closing it once the outcome shows.
  - `one-confetti-claude`: a Claude success, then the overview card draining with no second
    confetti.
  - `resize-zai`, `resize-codex`, `resize-grok`, `resize-claude`, `resize-zai-ask` and
    `resize-zai-sign-in` (round 6): the sheet resizing through every step of each flow.
    `slow-resize-zai` is the Z.AI flow with animations five times slower. The emulator draws in
    software and drops frames, so the normal-speed recordings can show a stutter at the start of
    a step. The slowed recording shows that the size itself changes continuously.
  - From round 1: the Grok failure then Try again, the Z.AI pool choice and "Ask for a reset card"
    (before this round's refill), and a tap on the tile from the home screen.

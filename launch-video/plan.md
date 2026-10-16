# Headroom launch video plan

This document records the original storyboard and two timing revisions.
The current build uses `build/timing.json`; the table below retains the original 120 BPM timing.

## Timing revisions

| Plan | Duration | Timing |
|---|---|---|
| Original | 72 s; 4320 frames | 1920 × 1080 at 60 frames per second (fps), 120 beats per minute (BPM), 36 bars |
| Chosen track | 77 s; 4620 frames | Lyria take edm-2, `music/headroom-edm2-edit.wav`, 128 BPM; each bar lasts 1.875 s |
| Revision 2, after c_01–c_08 | 73.25 s | Music edit v2 ends at bar 37 followed by a short closing sound |

The original plan uses 30 frames per beat and 120 frames, or 2 seconds, per bar.
Music and scene scripts read shared bar-to-frame timing from `build/timing.json`.
If the source tempo changes, retain the bar plan and regenerate frame numbers from `cav beats`.

The chosen-track revision derives shot frames from `build/grid.json` into `build/timing.json`.
All 18 cuts occur on downbeats. A rendered bell and sub-bass closing sound starts when the rhythm stops at 71.39 s.

| Chosen-track section | Bars | Start time where recorded |
|---|---|---|
| Intro | 1–4 | |
| Logo | 5 | 7.63 s |
| Overview | 7–12 | |
| Pace, with a musical texture change | 13 | 22.63 s |
| Detail | 17–20 | |
| Dark countdown | 21–24 | |
| Reset island, with rising music | 25 | 45.13 s |
| Confetti and resets | 27–28 | |
| Widgets, with the rhythm returning | 29 | 52.63 s |
| Palettes | 33–34 | |
| Closing section | 35 | 63.88 s |
| Rhythm stops; closing sound begins | | 71.39 s |

Revision 2 shortens the provider, dark countdown and closing sections. Widgets appear within a phone layout.
The reset sequence runs through s5c limit, s6a night, s6r1 "Use a reset", s6r2 refill and s6b reset island.
`renders/previz.mp4` contains the animated preview.
Its build runs `build/10_previz_a.js`, `20_previz_b.js`, `30_previz_c.js` and `90_centre.js`, in that order.

## Concept

The video shows remaining quota and compares usage with an even rate before a user reaches a limit.
Its visual elements match Headroom: flat and wavy bars, pace ticks, rings, cookie-shaped avatars, reset island and confetti.
Wavy bars indicate attention. Flat bars indicate normal usage.
The original concept places app screenshots inside a phone and animates selected elements as editable Cavalry layers.
The [implemented video](README.md) rebuilds every screen as layers.

## Colours and typography

The design follows the app's Material 3 Expressive appearance.

| Role | Hex |
|---|---|
| Light surface | `#f8fafe` |
| Text | `#1a1c22` |
| Cobalt primary / icon background | `#305eb7` / `#3553b0` |
| Primary container | `#c9dfff` |
| Tertiary pink container | `#fdcdef` |
| Dark section | `#0d0f14`, primary-on-dark `#a4c5ff` |
| Reset island ring | `#3ddc84` |
| Provider accents (OKLCH L.56 C.14) | Claude `#b55422`, OpenCode `#887400`, Codex `#008b4e`, Z.AI `#008c8e`, Grok `#007fba`, Kimi `#556dc6`, Copilot `#885cb5`, JetBrains `#af4d80` |
| Palette seeds | Coral `#ff6f61`, Tangerine `#ff8c1a`, Lemon `#ffd60a`, Lime `#8bd41a`, Lagoon `#00bfa5`, Sky `#2e9bff`, Grape `#8a4dff`, Bubblegum `#ff4fa3` |

Use Google Sans Flex, matching the app. Display text uses weight 820–860, width 114–118, ROND 100 and close letter spacing.
Labels use weight 620 and ROND 50. All text must be at least 30 px.

## Motion

Use Material 3 Expressive springs. Entrances use `spring` or `outBack`, exits use `inBack`, and shared-axis transitions use `inOutCubic`.
Focus each shot on one main element. Stagger elements by 3–4 frames and retain slow movement during holds.

## Original storyboard

The timings and numeric examples below belong to the original 120 BPM plan.
Text describes the planned message; it does not change the rendered video's captions.

| Bars | Time | Frames | Shot behaviour | Message |
|---|---|---|---|---|
| 0–3 | 0–8 s | 0–479 | A large quota bar fills on a light surface. Its pace tick passes, the bar becomes wavy and usage reaches 100%. It changes into a shaking "Usage limit reached · resets in 4h 12m" pill. Everything contracts to a dot during the final half beat. | A session can reach its usage limit. Headroom shows the remaining quota. |
| 4–5 | 8–12 s | 480–719 | On bar 4, the dot expands over a cobalt background. The app ring draws to 75%, its dot appears, and the Headroom name animates in. | Headroom shows remaining AI quota. |
| 6–9 | 12–20 s | 720–1199 | The camera moves through the ring into a phone showing Overview. Eight provider avatars appear beside it on eight beats. | Users can view subscriptions together. |
| 10–13 | 20–28 s | 1200–1679 | The camera zooms into Claude's card. Usage reaches 71% and an even-pace tick appears at 60%. On the downbeat, the bar becomes wavy and shows "11 pts over pace". Codex's flat 34% bar appears below it. | Users can compare usage with an even rate and view forecasts. |
| 14–17 | 28–36 s | 1680–2159 | A tap ripple opens account details. Beside the phone, a ring draws to 71% with animated digits. The pace chart draws its dashed even-pace line. | The forecast shows "100 % in 1d 17h at this rate." |
| 18–23 | 36–48 s | 2160–2879 | A dark screen accelerates a "15h 28m" countdown to zero. On bar 20, the reset island expands from the camera cutout. Its ring fills green and shows "reset". Bar 21 returns to a light surface with confetti over the phone. The Resets screen slides in. | Alerts follow confirmed resets. |
| 24–27 | 48–56 s | 2880–3359 | Rings, Shape, Bars and lock screen widgets animate onto a wallpaper gradient on beats. | Widgets work on home and lock screens. |
| 28–29 | 56–60 s | 3360–3599 | Theme changes expand from the tap location. Palettes change each beat: light, Bubblegum, Lagoon, dark Grape and dark Tangerine. | Users can choose colours and account order. |
| 30–35 | 60–72 s | 3600–4319 | Elements contract into the icon dot on cobalt. The icon and name appear on bar 30, followed by the tagline, licence/platform line, repository URL and Obtainium link. The final chord starts on bar 32. The ring continues a slow size animation until the end. | The video identifies Headroom as free and open source for Android 16+. It shows github.com/rock3r/headroom and Obtainium. It discloses no provider affiliation and identifies logos as their owners' trademarks. |

## Music

The original music plan uses an upbeat 120 BPM track, adjusted to the source track's tempo when needed.
Sparse plucked notes accompany the opening. The final half beat of bar 3 pauses before the main rhythm starts on bar 4.
Bar 18 increases intensity for the dark section. A filter sweep leads to the reset accent on bar 20.
Bars 28–29 reduce the arrangement for palette changes. The final accent starts on bar 30, with the chord continuing to 72 seconds.

## Assets

- `../docs/screenshots/*.png` supplies 1233 × 2673 Roborazzi captures with demo data for the rebuilt phone.
- `../docs/screenshots/widgets/*.png` supplies widget-player captures.
- `assets/logos/*.svg` comes from `core/model/.../ProviderLogo.kt`.
  The sources use models.dev's MIT licence and Simple Icons' CC0 terms. Logos remain their owners' trademarks.
- The app icon follows `app/src/main/res/drawable/ic_launcher_foreground.xml`.

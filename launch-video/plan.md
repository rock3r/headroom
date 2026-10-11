# Headroom launch video: plan

72 s, 1920 × 1080, 60 fps, 4320 frames, 120 BPM. One beat = 30 frames, one bar = 120 frames (2 s).
36 bars. Every shared time lives in `build/timing.json` (bars → frames); music and scenes read it.
If the sourced track has another tempo, the bar plan stays and the frame numbers are regenerated
from `cav beats`.

> **Re-timed to the chosen track (`music/headroom-edm2-edit.wav`).** Lyria take edm-2 at 128 BPM
> (bar = 1.875 s), with a rendered bell/sub stinger added where the groove stops at 71.39 s. 77 s,
> 4620 frames. Shot frames live in `build/timing.json` (from `build/grid.json`); all 18 cuts land
> on downbeats. Section map: intro bars 1–4 · drop bar 5 (7.63 s, logo) · overview bars 7–12 ·
> texture change bar 13 (22.63 s, pace) · detail bars 17–20 · dark break bars 21–24 (night
> countdown) · rise bar 25 (45.13 s, reset island) · confetti/resets bars 27–28 · re-entry bar 29
> (52.63 s, widgets) · palettes bars 33–34 · outro bar 35 (63.88 s) · groove stops + stinger 71.39 s.
> The bar/time columns below are the original 120 BPM plan.

> **Revision 2 (after review notes c_01–c_08):** 73.25 s; music edit v2 ends on bar 37 + stinger.
> Shorter providers/night/outro; new reset sequence (s5c limit → s6a night → s6r1 'Use a reset' sheet →
> s6r2 refill hit → s6b reset island); widgets in phone context; moving previz in `renders/previz.mp4`
> built by `build/10_previz_a.js`, `20_previz_b.js`, `30_previz_c.js`, then `90_centre.js`.

## Concept

**"Know your headroom."** Everyone on an AI plan finds their limit by hitting it. Headroom shows
how much is left, and whether you're burning it too fast. The video is made of Headroom's own
vocabulary: quota bars (flat = fine, wavy = attention), pace ticks, rings, cookie-shaped avatars,
the reset island and confetti. Real app screenshots sit in a phone; the hero elements are rebuilt
as live Cavalry layers so they can move.

## Look (app-native, Material 3 Expressive)

| Role | Hex |
|---|---|
| Paper (light surface) | `#f8fafe` |
| Ink | `#1a1c22` |
| Cobalt (primary / icon bg) | `#305eb7` / `#3553b0` |
| Primary container | `#c9dfff` |
| Tertiary (pink) container | `#fdcdef` |
| Night (dark section) | `#0d0f14`, primary-on-dark `#a4c5ff` |
| Reset green (island ring) | `#3ddc84` |
| Provider accents (OKLCH L.56 C.14) | Claude `#b55422`, OpenCode `#887400`, Codex `#008b4e`, Z.AI `#008c8e`, Grok `#007fba`, Kimi `#556dc6`, Copilot `#885cb5`, JetBrains `#af4d80` |
| Palette seeds (montage) | Coral `#ff6f61`, Tangerine `#ff8c1a`, Lemon `#ffd60a`, Lime `#8bd41a`, Lagoon `#00bfa5`, Sky `#2e9bff`, Grape `#8a4dff`, Bubblegum `#ff4fa3` |

Type: Google Sans Flex only (the app's font). Display: weight 820–860, width 114–118, ROND 100,
tight tracking. Labels: weight 620, ROND 50. Nothing under 30 px.

Motion: M3 Expressive springs. Entrances `spring`/`outBack`, exits `inBack`, shared-axis pushes
`inOutCubic`. One hero at a time; staggered 3–4 frame steps; holds keep a slow drift.

## Storyboard

| Bars | Time | Frames | Shot | On screen |
|---|---|---|---|---|
| 0–3 | 0–8 s | 0–479 | **Hook.** Paper. A huge quota bar fills fast, its pace tick slides past, it turns wavy, hits 100 % and snaps into a "Usage limit reached · resets in 4h 12m" pill with a shake. Last half beat: everything sucks into a dot. | "You're deep in a session." → "Then this." → "Know your headroom." |
| 4–5 | 8–12 s | 480–719 | **Logo.** Hit on bar 4: the dot bursts to a cobalt field. The app icon's ring draws on to 75 %, its dot pops, "Headroom" cascades in. | "How much AI you have left. At a glance." |
| 6–9 | 12–20 s | 720–1199 | **Overview.** The ring zooms through into a phone showing the overview. Eight provider avatars pop on eight beats beside it. | "Every subscription. One overview." |
| 10–13 | 20–28 s | 1200–1679 | **Pace.** Crash zoom into the Claude card: the rebuilt bar fills to 71 %, the even-pace tick drops in at 60 %, the bar goes wavy on the downbeat, "11 pts over pace" pops. Codex's flat 34 % bar slides under it. | "Pace, not just percent." / "Wavy means slow down. Flat means fine." |
| 14–17 | 28–36 s | 1680–2159 | **Detail.** Tap ripple on the card → phone shows the account detail. Beside it the rebuilt ring draws to 71 % (digits roll up) and the pace chart draws on with its dashed even-pace line. | "See where your week is heading." / "100 % in 1d 17h at this rate." |
| 18–23 | 36–48 s | 2160–2879 | **Resets.** Night. A giant "15h 28m" countdown fast-forwards to zero. Hit on bar 20: the reset island grows out of a camera cutout, its ring fills green, "reset". Bar 21: back to paper with confetti over the phone. Resets screen slides in. | "Know the moment it resets." / "Alerts only when it really did." |
| 24–27 | 48–56 s | 2880–3359 | **Widgets.** A wallpaper gradient; Rings, Shape, Bars and lock-screen widgets spring in on beats. | "Home screen. Lock screen." |
| 28–29 | 56–60 s | 3360–3599 | **Make it yours.** Theme change spreads from a tap point, one palette per beat (light → bubblegum → lagoon → grape dark → tangerine dark…). | "Your colours. Your order." |
| 30–35 | 60–72 s | 3600–4319 | **Outro.** Everything folds back into the icon dot on cobalt. Icon + wordmark land on bar 30; tagline; then "Free and open source · Android 16+", "github.com/rock3r/headroom", "Get it on Obtainium". Final chord bar 32, ring keeps breathing to the end. | "Know your headroom." + small "Not affiliated with any AI provider. Logos are their owners' trademarks." |

## Music

Upbeat, bright, 120 BPM (or whatever the sourced track is): sparse plucks under the hook,
a pause on the last half beat of bar 3, the drop on bar 4, a lift at bar 18 (night) with a
filter sweep into the reset hit on bar 20, a breakdown-ish bar 28–29 for the palette montage,
final hit bar 30 and chord tail to 72 s.

## Assets

- `../docs/screenshots/*.png` (1233 × 2673 Roborazzi captures, demo data) in a rebuilt phone.
- `../docs/screenshots/widgets/*.png` (widget player captures).
- `assets/logos/*.svg` extracted from `core/model/.../ProviderLogo.kt` (models.dev MIT / Simple Icons CC0; trademarks of their owners).
- App icon rebuilt from `app/src/main/res/drawable/ic_launcher_foreground.xml`.

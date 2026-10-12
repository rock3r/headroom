# Headroom launch video

The 75 second launch video for Headroom, built in [Cavalry](https://cavalry.scenegroup.co) from
scripts. Nothing in the video is a screenshot: every screen, widget and transition is rebuilt as live
layers by the files in `build/`, so the scene can be regenerated and re-rendered at any size.

## What is here

| Path | What it is |
|---|---|
| `build/00_lib.js` | Shared helpers: shots, rings, logos, cookies, timing |
| `build/01_ui.js` | The app's UI kit: cards, bars, hero, toolbar, overview |
| `build/02_os.js` | Launcher, lock screen, widgets and the reset island |
| `build/10_shots_a.js` … `40_shots_d.js` | The shots, in order |
| `build/90_centre.js` | Second pass that aligns the logo lockups and the tagline |
| `build/timing.json` | Shot boundaries and beat frames (128 BPM, 60 fps) |
| `build/themes.json` | Material 3 colour tokens for the palettes |
| `build/thumb.js` | The YouTube thumbnail, built on its own in a cleared scene |
| `build/run.sh` | Runs scripts in Cavalry with the shared library, from any checkout |
| `build/build.sh` | Rebuilds the whole scene and runs the alignment pass |
| `assets/logos/` | Provider logos, the fingerprint glyph and the toolbar icons (SVG) |
| `music/headroom-edm2-v3.flac` | The soundtrack, cut to the video's length |
| `publish/` | The YouTube description and thumbnail |
| `plan.md`, `storyboard.json` | The plan and the shot list |

## Requirements

- Cavalry, with the cav plugin and CLI. The video was built with cav 1.2.0 (helpers 1.2.0+218832b7).
- The Google Sans Flex font installed on the machine that renders.
- ffmpeg and ffprobe, which cav uses to mux the audio and check the render.
- Paths are relative to this folder, so the repo can live anywhere. Cavalry scripts cannot see where
  they are, so `build/run.sh` writes the folder's path to `build/.root.js` (ignored by git) and runs it
  first. Always run the scripts through `run.sh` or `build.sh`, not with `cav run` directly.

## Rebuild and render

From this folder, with Cavalry open:

```sh
sh build/build.sh
cav scene save scenes/launch.cv
cav render -o renders/headroom-launch-1080p60.mp4 --audio music/headroom-edm2-v3.flac --scale 100 --timeout 120m
```

`build.sh` clears the open scene before building. Use `--scale 50` for a quick preview render.

The thumbnail is built separately, because it also clears the scene:

```sh
sh build/run.sh build/01_ui.js build/02_os.js build/thumb.js
cav frame 0 --scale 100 -o out/thumb.png
```

Then scale it to 1280 x 720 for YouTube.

## Music

The soundtrack was generated with Google's Lyria and edited to the cut. Check Lyria's terms before
reusing it outside this video.

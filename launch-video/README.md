# Headroom launch video

The 75-second Headroom launch video uses scripts in [Cavalry](https://cavalry.scenegroup.co).
Scripts in `build/` draw every screen, widget and transition as editable layers.
The video contains no screenshots. Rebuilding the scene allows rendering at any size.

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

- Install Cavalry with the cav plugin and command-line interface (CLI).
  The video build used cav 1.2.0 and helpers 1.2.0+218832b7.
- Install Google Sans Flex on the rendering machine.
- Install ffmpeg and ffprobe. cav uses them to combine audio with video and check the render.

All paths are relative to this folder. Cavalry scripts cannot find their own location.
`build/run.sh` writes the folder path to the git-ignored `build/.root.js` and runs that file first.
Always use `run.sh` or `build.sh` to run scripts. Direct `cav run` calls skip this setup.

## Rebuild and render

Open Cavalry, then run these commands from this folder:

```sh
sh build/build.sh
cav scene save scenes/launch.cv
cav render -o renders/headroom-launch-1080p60.mp4 --audio music/headroom-edm2-v3.flac --scale 100 --timeout 120m
```

`build.sh` clears the current scene before rebuilding it. Use `--scale 50` for a lower-resolution preview.

Build the thumbnail separately. Its script also clears the current scene:

```sh
sh build/run.sh build/01_ui.js build/02_os.js build/thumb.js
cav frame 0 --scale 100 -o out/thumb.png
```

Scale the resulting image to 1280 x 720 for YouTube.

## Music

Google's Lyria generated the soundtrack. The edited track matches the video's timing.
Check Lyria's terms before using the soundtrack outside this video.

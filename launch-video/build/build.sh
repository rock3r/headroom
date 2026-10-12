#!/bin/sh
# Rebuild the whole video in the open Cavalry scene (it is cleared first), then align the lockups.
here=$(dirname "$0")
sh "$here/run.sh" build/01_ui.js build/02_os.js build/10_shots_a.js build/20_shots_b.js build/30_shots_c.js build/40_shots_d.js --timeout 15m && \
sh "$here/run.sh" build/90_centre.js --timeout 15m

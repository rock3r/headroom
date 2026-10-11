#!/bin/sh
# Run scripts in Cavalry with the shared library loaded first, e.g.
#   sh build/run.sh build/01_ui.js build/02_os.js build/thumb.js
# Cavalry scripts cannot see where they live, so this writes build/.root.js with the absolute path
# of launch-video/ and runs it first. Every path in the scripts is relative to that folder.
cd "$(dirname "$0")/.." || exit 1
dir=$(pwd -P | sed "s/'/\\\\'/g")
printf "globalThis.LV_DIR = '%s';\n" "$dir" > build/.root.js
exec cav run build/.root.js build/00_lib.js "$@"

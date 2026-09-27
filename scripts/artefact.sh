#!/usr/bin/env bash
# The artefact command of docs/architecture/00-index.md.
# Produces, from the current state of the tree, everything needed to exercise the product by hand:
#   build/artefact/recipe-fork-debug.apk   install with `adb install -r` or by copying to the phone
#   build/artefact/server/                 run with `docker compose up --build` inside it
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --console=plain :app:assembleDebug :server:installDist
out=build/artefact
rm -rf "$out"
mkdir -p "$out/server/server/build"
cp app/build/outputs/apk/debug/app-debug.apk "$out/recipe-fork-debug.apk"
cp server/Dockerfile "$out/server/server/"
cp -r server/build/install "$out/server/server/build/"
sed 's|context: ../../server|context: ./server|' deploy/local/compose.yml > "$out/server/compose.yml"
echo "$(git rev-parse --short HEAD)" > "$out/SOURCE_COMMIT"
echo "Artefact at $(pwd)/$out (source $(cat "$out/SOURCE_COMMIT"))"

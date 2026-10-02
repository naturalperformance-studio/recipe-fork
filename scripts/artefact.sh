#!/usr/bin/env bash
# The artefact command of docs/architecture/00-index.md.
# Produces, from the current state of the tree, everything needed to exercise the product by hand:
#   build/artefact/recipe-fork-debug.apk             install with `adb install -r` or by copying to the phone
#   build/artefact/recipe-fork-serverless-debug.apk  the same application built in serverless mode
#                                                    (decisions/20260928-serverless-mode-is-a-build-flag.md);
#                                                    one application id, so it installs over the standard one
#   build/artefact/recipe-fork-serverless-release.apk the serverless build the owner installs by hand: shrunk by R8,
#                                                    signed with the debug key, so it installs over either debug APK
#   build/artefact/server/                           run with `docker compose up --build` inside it
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/artefact
rm -rf "$out"
mkdir -p "$out/server/server/build"
# Both APKs come from the same task's output, so each is copied away before the other is built.
./gradlew --console=plain -Precipefork.serverless=true :app:assembleDebug :app:assembleRelease
cp app/build/outputs/apk/debug/app-debug.apk "$out/recipe-fork-serverless-debug.apk"
cp app/build/outputs/apk/release/app-release.apk "$out/recipe-fork-serverless-release.apk"
./gradlew --console=plain :app:assembleDebug :server:installDist
cp app/build/outputs/apk/debug/app-debug.apk "$out/recipe-fork-debug.apk"
cp server/Dockerfile "$out/server/server/"
cp -r server/build/install "$out/server/server/build/"
sed 's|context: ../../server|context: ./server|' deploy/local/compose.yml > "$out/server/compose.yml"
echo "$(git rev-parse --short HEAD)" > "$out/SOURCE_COMMIT"
echo "Artefact at $(pwd)/$out (source $(cat "$out/SOURCE_COMMIT"))"
ls -l "$out"/*.apk

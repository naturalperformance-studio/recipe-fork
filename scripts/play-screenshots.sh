#!/usr/bin/env bash
# The Google Play store's pictures, drawn from the app's own screens under Robolectric (no device needed):
#   build/play/screenshots/en-*.png, ru-*.png   phone screenshots, 1080×1920: the book, a card mid-cook (View), the editor,
#                                                the card's steps (Cooking) and the settings, in English and in Russian
#   build/play/screenshots/feature-graphic.png   1024×500: the icon and the name on the theme's primary container
#   build/play/screenshots/icon-512.png          the 512×512 store icon (LauncherIconTest)
# The screenshot tests run only when this script names their directory; `./gradlew testAll` skips them.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(pwd)/build/play/screenshots"
rm -rf "$out"
mkdir -p "$out"
./gradlew --console=plain :app:testDebugUnitTest --rerun \
    -Precipefork.playScreenshots="$out" \
    --tests 'app.recipefork.app.play.PlayScreenshotsTest' \
    --tests 'app.recipefork.app.LauncherIconTest.rendersThePlayStoreIcon'
cp app/build/play/icon-512.png "$out/icon-512.png"
echo "Store pictures at $out"
ls -l "$out"

#!/usr/bin/env bash
# The device-test command of docs/architecture/00-index.md: instrumented tests (app/src/androidTest)
# on a headless emulator at API 29, the lowest supported version (QUAL-005).
# Only tasks whose criteria need real codecs or a real device run it.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
avd=recipe-fork-api29
image="system-images;android-29;default;x86_64"
if ! "$sdk/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$avd"; then
    echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd -n "$avd" -k "$image" --force >/dev/null
fi
"$sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect >/tmp/recipe-fork-emulator.log 2>&1 &
emu=$!
trap 'kill $emu 2>/dev/null || true' EXIT
"$sdk/platform-tools/adb" wait-for-device
until [ "$("$sdk/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
./gradlew --console=plain :app:connectedDebugAndroidTest
"$sdk/platform-tools/adb" emu kill || true

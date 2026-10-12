#!/usr/bin/env bash
# The device-test command of docs/architecture/00-index.md: instrumented tests (app/src/androidTest)
# on a headless emulator at API 29, the lowest supported version (QUAL-005).
# Only tasks whose criteria need real codecs or a real device run it.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
avd=recipe-fork-api29
image="system-images;android-29;default;x86_64"
# The emulator by its own address: a phone attached over USB is tested too, and must not be taken for it booted.
serial=emulator-5554
if ! "$sdk/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$avd"; then
    echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd -n "$avd" -k "$image" --force >/dev/null
fi
"$sdk/emulator/emulator" -avd "$avd" -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect >/tmp/recipe-fork-emulator.log 2>&1 &
emu=$!
trap 'kill $emu 2>/dev/null || true' EXIT
# Wait for the boot, but stop as soon as the emulator dies (e.g. no access to /dev/kvm) instead of waiting forever.
until [ "$("$sdk/platform-tools/adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if ! kill -0 "$emu" 2>/dev/null; then
        echo "The emulator stopped before it booted; see /tmp/recipe-fork-emulator.log" >&2
        grep -m1 -A1 'ERROR' /tmp/recipe-fork-emulator.log >&2 || true
        exit 1
    fi
    sleep 2
done
./gradlew --console=plain :app:connectedDebugAndroidTest
"$sdk/platform-tools/adb" -s "$serial" emu kill || true

#!/usr/bin/env bash
# The device test of the build the owner installs by hand: the minified, resource-shrunk serverless release
# (app/build.gradle.kts, release) on a headless emulator at API 29 (QUAL-005), as scripts/device-test.sh boots it.
# Runs ReleaseSmokeTest against it — its test APK mapped by R8 onto the shrunk application — so a class R8 removed
# from under Room, kotlinx.serialization, the capture provider, the timers' service and receivers or Compose fails here, not on
# the phone. Any extra arguments go to Gradle.
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
results=app/build/outputs/androidTest-results/connected/releaseSmoke
rm -rf "$results"
./gradlew --console=plain -Precipefork.serverless=true -Precipefork.testBuildType=releaseSmoke \
    -Pandroid.testInstrumentationRunnerArguments.class=app.recipefork.app.ReleaseSmokeTest \
    :app:connectedReleaseSmokeAndroidTest "$@"
# A test process that dies before its first test reports no test and no failure; that is no pass — on any of the
# devices tested, each reported in a file of its own.
reports=("$results"/*.xml)
if [ ! -e "${reports[0]}" ]; then
    echo "No device reported a test; see $results and the device's log." >&2
    exit 1
fi
for report in "${reports[@]}"; do
    for smoke in theBuildStartsKeepsExportsImportsTakesAPictureAndRingsATimer theBuildConvertsAClip theBuildGoesThroughEveryMainScreenWithoutACrash; do
        if ! grep -qs "testcase name=\"$smoke\"" "$report"; then
            echo "The smoke test $smoke did not run in $report; see the device's log." >&2
            exit 1
        fi
    done
done
"$sdk/platform-tools/adb" -s "$serial" emu kill || true

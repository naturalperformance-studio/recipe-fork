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
if ! "$sdk/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$avd"; then
    echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd -n "$avd" -k "$image" --force >/dev/null
fi
"$sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect >/tmp/recipe-fork-emulator.log 2>&1 &
emu=$!
trap 'kill $emu 2>/dev/null || true' EXIT
"$sdk/platform-tools/adb" wait-for-device
until [ "$("$sdk/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
results=app/build/outputs/androidTest-results/connected/releaseSmoke
rm -rf "$results"
./gradlew --console=plain -Precipefork.serverless=true -Precipefork.testBuildType=releaseSmoke \
    -Pandroid.testInstrumentationRunnerArguments.class=app.recipefork.app.ReleaseSmokeTest \
    :app:connectedReleaseSmokeAndroidTest "$@"
# A test process that dies before its first test reports no test and no failure; that is no pass.
for smoke in theBuildStartsKeepsExportsImportsTakesAPictureAndRingsATimer theBuildConvertsAClip theBuildGoesThroughEveryMainScreenWithoutACrash; do
    if ! grep -qs "testcase name=\"$smoke\"" "$results"/*.xml; then
        echo "The smoke test $smoke did not run; see $results and the device's log." >&2
        exit 1
    fi
done
"$sdk/platform-tools/adb" emu kill || true

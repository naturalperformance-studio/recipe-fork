#!/usr/bin/env bash
# The test of scripts/play-bundle.sh. First it checks the merged manifests: only the play build drops INTERNET and
# ACCESS_NETWORK_STATE; debug, release and releaseSmoke keep both. Then, without the upload key's settings, the script
# must name the missing ones and stop before building; with them — a throwaway keystore made here by keytool and
# deleted at the end, never the owner's key — it must build recipe-fork-<version>.aab and pass its own checks of the
# bundle (version, serverless, no network permission, not debuggable, R8-minified, signed with that key) without
# printing a password. The owner's ~/Projects/.env is never read: PLAY_ENV points at a file of the test's own, and
# PLAY_OUT at a folder of its own, so build/play/ never holds a bundle signed with the throwaway key.
set -euo pipefail
cd "$(dirname "$0")/.."
vars=(RECIPEFORK_UPLOAD_STORE_FILE RECIPEFORK_UPLOAD_STORE_PASSWORD RECIPEFORK_UPLOAD_KEY_ALIAS RECIPEFORK_UPLOAD_KEY_PASSWORD)
unset "${vars[@]}"
tmp="$(mktemp -d)"
# Gradle's own copy of the bundle is signed with the throwaway key too, so it goes as well.
trap 'rm -rf -- "${tmp:?}" app/build/outputs/bundle/play' EXIT
export PLAY_ENV="$tmp/play.env" PLAY_OUT="$tmp/out"
version="$(grep -E '^recipefork.version=' gradle.properties | cut -d= -f2 | tr -d ' \r')"
aab="$PLAY_OUT/recipe-fork-$version.aab"
failed() { echo "FAIL: $*" >&2; exit 1; }

echo "== the network permissions of each build's merged manifest"
./gradlew --console=plain -q :app:processDebugMainManifest :app:processReleaseMainManifest \
    :app:processReleaseSmokeMainManifest :app:processPlayMainManifest
for variant in debug release releaseSmoke play; do
    merged="app/build/intermediates/merged_manifest/$variant/process${variant^}MainManifest/AndroidManifest.xml"
    [ -s "$merged" ] || failed "no merged manifest at $merged"
    for permission in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
        if grep -qF "android:name=\"$permission\"" "$merged"; then has=yes; else has=no; fi
        if [ "$variant" = play ]; then want=no; else want=yes; fi
        [ "$has" = "$want" ] || failed "$variant: $permission declared: $has, wanted: $want"
    done
done

echo "== no key settings anywhere"
if out="$(scripts/play-bundle.sh 2>&1)"; then failed "the script succeeded without a key"; fi
for name in "${vars[@]}"; do grep -q "$name" <<<"$out" || failed "the message does not name $name: $out"; done
grep -q 'BUILD' <<<"$out" && failed "Gradle ran without a key: $out"
[ ! -e "$aab" ] || failed "$aab was written without a key"

echo "== half of them in the env file"
printf 'RECIPEFORK_UPLOAD_STORE_FILE=%s\nRECIPEFORK_UPLOAD_KEY_ALIAS="upload"\n' "$tmp/upload.p12" >"$PLAY_ENV"
if out="$(scripts/play-bundle.sh 2>&1)"; then failed "the script succeeded with half a key"; fi
grep -q 'missing: RECIPEFORK_UPLOAD_STORE_PASSWORD RECIPEFORK_UPLOAD_KEY_PASSWORD$' <<<"$(head -1 <<<"$out")" \
    || failed "the message does not name exactly the two passwords: $out"

echo "== a throwaway key: file and alias from the env file, passwords from the environment"
export THROWAWAY_PASSWORD="throwaway-$RANDOM$RANDOM-pass"
password="$THROWAWAY_PASSWORD"
keytool -genkeypair -keystore "$tmp/upload.p12" -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 -validity 2 \
    -dname "CN=recipe-fork play-bundle test" -storepass:env THROWAWAY_PASSWORD -keypass:env THROWAWAY_PASSWORD >/dev/null 2>&1
export RECIPEFORK_UPLOAD_STORE_PASSWORD="$password" RECIPEFORK_UPLOAD_KEY_PASSWORD="$password"
out="$(scripts/play-bundle.sh 2>&1)" || failed "the script failed with a key:
$(tail -30 <<<"$out")"
grep -qF "Play bundle at $(realpath "$aab"): version $version (code " <<<"$out" || failed "no success line: $(tail -5 <<<"$out")"
grep -qF "$password" <<<"$out" && failed "the output shows the password"
[ -s "$aab" ] || failed "no $aab"
echo "$(tail -1 <<<"$out")"

echo "== no SDK build-tools to check the bundle with"
if out="$(ANDROID_HOME="$tmp/no-sdk" scripts/play-bundle.sh 2>&1)"; then failed "the script succeeded without aapt2"; fi
grep -q 'No aapt2 in' <<<"$out" || failed "no message about aapt2: $out"
grep -q 'BUILD' <<<"$out" && failed "Gradle ran without aapt2: $out"

echo "== a key that does not open"
export RECIPEFORK_UPLOAD_STORE_PASSWORD="not-$password"
if out="$(scripts/play-bundle.sh 2>&1)"; then failed "the script succeeded with a wrong password"; fi
grep -q 'does not open' <<<"$out" || failed "no message about the keystore: $out"
grep -qF "$password" <<<"$out" && failed "the output shows the password"

echo "play-bundle test passed"

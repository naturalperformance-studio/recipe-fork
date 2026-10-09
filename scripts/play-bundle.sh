#!/usr/bin/env bash
# The app bundle for Google Play: the serverless build (decisions/20260928-serverless-mode-is-a-build-flag.md), shrunk
# by R8 like the release APK, carrying the version of recipefork.version in gradle.properties, signed with the upload key.
#   build/play/recipe-fork-<version>.aab   upload it to the Play Console (PLAY_OUT names another folder)
#
# The upload key lives outside the repository. Its four settings are read from the environment, or else from a shared
# env file (default ~/Projects/.env, override with PLAY_ENV):
#   RECIPEFORK_UPLOAD_STORE_FILE      the keystore file (an absolute path, or ~/...)
#   RECIPEFORK_UPLOAD_STORE_PASSWORD  its password
#   RECIPEFORK_UPLOAD_KEY_ALIAS       the upload key's alias in it
#   RECIPEFORK_UPLOAD_KEY_PASSWORD    the key's password (for a PKCS12 keystore, the same as the store's)
# Without them the script names what is missing and stops before building. No password is ever printed.
# After the build the bundle is checked: it is the serverless build, its manifest carries the version, asks for no
# network permission and is not debuggable, its code is R8's release output, and it is signed with that key.
# It needs the Android SDK's build-tools (aapt2) under ANDROID_HOME (default ~/Android/Sdk). Any extra arguments go
# to Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."

env_file="${PLAY_ENV:-$HOME/Projects/.env}"
# One NAME=value line of the env file; surrounding quotes and a Windows line end are dropped, inner spaces kept.
read_var() {
    [ -r "$env_file" ] || return 0
    grep -E "^$1=" "$env_file" | head -1 | cut -d= -f2- | tr -d '\r' | sed -E "s/^\"(.*)\"$/\1/; s/^'(.*)'$/\1/"
}
missing=()
for name in RECIPEFORK_UPLOAD_STORE_FILE RECIPEFORK_UPLOAD_STORE_PASSWORD RECIPEFORK_UPLOAD_KEY_ALIAS RECIPEFORK_UPLOAD_KEY_PASSWORD; do
    [ -n "${!name:-}" ] || printf -v "$name" '%s' "$(read_var "$name")"
    if [ -n "${!name}" ]; then export "$name"; else missing+=("$name"); fi
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "The Play upload key is not set; missing: ${missing[*]}" >&2
    echo "Set them in $env_file (or point PLAY_ENV at another file) or in the environment." >&2
    exit 1
fi
store="${RECIPEFORK_UPLOAD_STORE_FILE/#\~/$HOME}"
store="$(realpath -m -- "$store")"
[ -r "$store" ] || { echo "No readable keystore at $store (RECIPEFORK_UPLOAD_STORE_FILE)" >&2; exit 1; }
export RECIPEFORK_UPLOAD_STORE_FILE="$store"
if ! keytool -list -keystore "$store" -storepass:env RECIPEFORK_UPLOAD_STORE_PASSWORD \
        -alias "$RECIPEFORK_UPLOAD_KEY_ALIAS" >/dev/null 2>&1; then
    echo "The keystore $store does not open with RECIPEFORK_UPLOAD_STORE_PASSWORD or holds no key $RECIPEFORK_UPLOAD_KEY_ALIAS" >&2
    exit 1
fi

version="$(grep -E '^recipefork.version=' gradle.properties | cut -d= -f2 | tr -d ' \r')"
[ -n "$version" ] || { echo "No recipefork.version in gradle.properties" >&2; exit 1; }
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
[ -x "$aapt2" ] || { echo "No aapt2 in $sdk/build-tools/*/ to check the bundle with: install the SDK build-tools or set ANDROID_HOME" >&2; exit 1; }

# The play build type is the serverless build by itself (app/build.gradle.kts); no -Precipefork.serverless is needed.
./gradlew --console=plain :app:bundlePlay "$@"
out="${PLAY_OUT:-build/play}"
aab="$out/recipe-fork-$version.aab"
mkdir -p "$out"
cp app/build/outputs/bundle/play/app-play.aab "$aab"

# The checks. A failed one leaves the bundle in place but exits non-zero, so it is not uploaded by mistake.
fail() { echo "$aab: $*" >&2; exit 1; }
# R8 folds BuildConfig.SERVERLESS into the code, so the flag is read where the bundle was built from.
grep -q 'boolean SERVERLESS = true;' app/build/generated/source/buildConfig/play/app/recipefork/app/BuildConfig.java \
    || fail "the play build was not built serverless (BuildConfig.SERVERLESS)"
work="$(mktemp -d)"
trap 'rm -rf -- "${work:?}"' EXIT
# aapt2 reads a bundle's protobuf manifest only from an APK in protobuf form, so one is made of the base module's.
unzip -q -j "$aab" base/manifest/AndroidManifest.xml base/resources.pb -d "$work" || fail "no base/manifest/AndroidManifest.xml"
(cd "$work" && zip -q proto.apk AndroidManifest.xml resources.pb)
manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$work/proto.apk")"
code="$(grep -oE 'android:versionCode\([^)]*\)=[0-9]+' <<<"$manifest" | head -1 | cut -d= -f2)"
name="$(grep -oE 'android:versionName\([^)]*\)="[^"]*"' <<<"$manifest" | head -1 | cut -d'"' -f2)"
[ "$name" = "$version" ] || fail "versionName is '$name', not $version"
# The scheme of gradle.properties, as app/build.gradle.kts computes it (Gradle has already checked the format).
IFS=. read -r major minor patch <<<"$version"
expected_code=$((10#$major * 1000000 + 10#$minor * 1000 + 10#${patch:-0}))
[ "$code" = "$expected_code" ] || fail "versionCode is '$code', not $expected_code"
grep -q 'android:debuggable' <<<"$manifest" && fail "the manifest declares android:debuggable"
for permission in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
    if grep -qF "\"$permission\"" <<<"$manifest"; then fail "the manifest asks for $permission"; fi
done
# (Each tool's output is taken whole before it is searched: a grep that stops early would break the pipe under pipefail.)
unzip -q -j "$aab" base/dex/classes.dex -d "$work" || fail "no base/dex/classes.dex"
grep -aq '~~R8{[^}]*"compilation-mode":"release"' "$work/classes.dex" || fail "base/dex/classes.dex is not R8's release output"
unzip -l "$aab" BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map >/dev/null \
    || fail "no R8 mapping in BUNDLE-METADATA"
verified="$(jarsigner -verify "$aab" 2>&1)" || true
grep -q '^jar verified' <<<"$verified" || fail "the signature does not verify"
fingerprint() { grep -oE 'SHA256: [0-9A-F:]+' | sed -n '1s/^SHA256: //p'; }
signed_with="$(keytool -printcert -jarfile "$aab" | fingerprint)"
upload_key="$(keytool -list -v -keystore "$store" -storepass:env RECIPEFORK_UPLOAD_STORE_PASSWORD \
    -alias "$RECIPEFORK_UPLOAD_KEY_ALIAS" | fingerprint)"
[ -n "$signed_with" ] && [ "$signed_with" = "$upload_key" ] || fail "signed with $signed_with, not the upload key $upload_key"

echo "Play bundle at $(realpath "$aab"): version $name (code $code), serverless, no network permission, R8-minified, signed with the upload key (SHA-256 $upload_key)"

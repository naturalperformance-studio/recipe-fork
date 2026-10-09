#!/usr/bin/env bash
# The test of scripts/play-bundle.sh. Without the upload key's settings it must name the missing ones and stop before
# building; with them — a throwaway keystore made here by keytool and deleted at the end, never the owner's key — it
# must build recipe-fork-<version>.aab and pass its own checks of the bundle (version, not debuggable,
# R8-minified, signed with that key) without printing a password. The owner's ~/Projects/.env is never read: PLAY_ENV
# points at a file of the test's own, and PLAY_OUT at a folder of its own, so build/play/ never holds a bundle signed
# with the throwaway key.
set -euo pipefail
cd "$(dirname "$0")/.."
vars=(RECIPEFORK_UPLOAD_STORE_FILE RECIPEFORK_UPLOAD_STORE_PASSWORD RECIPEFORK_UPLOAD_KEY_ALIAS RECIPEFORK_UPLOAD_KEY_PASSWORD)
unset "${vars[@]}"
tmp="$(mktemp -d)"
trap 'rm -rf -- "${tmp:?}"' EXIT
export PLAY_ENV="$tmp/play.env" PLAY_OUT="$tmp/out"
version="$(grep -E '^recipefork.version=' gradle.properties | cut -d= -f2 | tr -d ' \r')"
aab="$PLAY_OUT/recipe-fork-$version.aab"
failed() { echo "FAIL: $*" >&2; exit 1; }

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
password="throwaway-$RANDOM$RANDOM-pass"
keytool -genkeypair -keystore "$tmp/upload.p12" -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 \
    -validity 2 -dname "CN=recipe-fork play-bundle test" -storepass "$password" -keypass "$password" >/dev/null 2>&1
export RECIPEFORK_UPLOAD_STORE_PASSWORD="$password" RECIPEFORK_UPLOAD_KEY_PASSWORD="$password"
out="$(scripts/play-bundle.sh 2>&1)" || failed "the script failed with a key:
$(tail -30 <<<"$out")"
grep -qF "Play bundle at $(realpath "$aab"): version $version (code " <<<"$out" || failed "no success line: $(tail -5 <<<"$out")"
grep -qF "$password" <<<"$out" && failed "the output shows the password"
[ -s "$aab" ] || failed "no $aab"
echo "$(tail -1 <<<"$out")"

echo "== a key that does not open"
export RECIPEFORK_UPLOAD_STORE_PASSWORD="not-$password"
if out="$(scripts/play-bundle.sh 2>&1)"; then failed "the script succeeded with a wrong password"; fi
grep -q 'does not open' <<<"$out" || failed "no message about the keystore: $out"
grep -qF "$password" <<<"$out" && failed "the output shows the password"

echo "play-bundle test passed"

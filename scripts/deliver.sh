#!/usr/bin/env bash
# Sends the hand-test APK from build/artefact/ to the owner's Telegram chat, so a fresh build
# can be installed straight from the phone. Run after scripts/artefact.sh.
#
#   scripts/deliver.sh [notes-file]
#
# notes-file (optional): plain text sent as a message after the APK — what changed, what to try.
# Credentials live outside the repository, in a shared env file (default ~/Projects/.env,
# override with DELIVERY_ENV): TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID. The token is never printed.
set -euo pipefail
cd "$(dirname "$0")/.."

env_file="${DELIVERY_ENV:-$HOME/Projects/.env}"
[ -r "$env_file" ] || { echo "No credentials file at $env_file (set DELIVERY_ENV)" >&2; exit 1; }
read_var() { grep -E "^$1=" "$env_file" | head -1 | cut -d= -f2- | tr -d "\"' \r"; }
token="$(read_var TELEGRAM_BOT_TOKEN)"
chat="$(read_var TELEGRAM_CHAT_ID)"
[ -n "$token" ] && [ -n "$chat" ] || { echo "TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID must be set in $env_file" >&2; exit 1; }

apk=build/artefact/recipe-fork-debug.apk
[ -f "$apk" ] || { echo "No $apk — run scripts/artefact.sh first" >&2; exit 1; }
# The Bot API refuses uploads over 50 MB.
size=$(stat -c %s "$apk")
[ "$size" -le $((50 * 1024 * 1024)) ] || { echo "$apk is $((size / 1024 / 1024)) MB; Telegram takes at most 50 MB" >&2; exit 1; }

source_commit="$(cat build/artefact/SOURCE_COMMIT 2>/dev/null || git rev-parse --short HEAD)"
increment="$(grep -l '^\*\*Status:\*\* \(active\|demo\)' docs/increments/*.md 2>/dev/null | sort -V | tail -1 | xargs -r basename -s .md)"
caption="recipe-fork ${increment:-build} · $(git rev-parse --abbrev-ref HEAD) ${source_commit} · $(date '+%Y-%m-%d %H:%M')"

api="https://api.telegram.org/bot${token}"
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get("ok") else (print(d.get("description","failed"), file=sys.stderr) or 1))'; }

curl -sS -F chat_id="$chat" -F caption="$caption" \
    -F document=@"$apk;filename=recipe-fork-${source_commit}.apk" \
    "$api/sendDocument" | ok

if [ $# -ge 1 ] && [ -s "$1" ]; then
    # A message holds at most 4096 characters.
    curl -sS --data-urlencode chat_id="$chat" --data-urlencode text="$(head -c 4000 "$1")" \
        "$api/sendMessage" | ok
fi
echo "Delivered $apk ($((size / 1024 / 1024)) MB) to Telegram: $caption"

#!/usr/bin/env bash
# Builds the shipped dictionaries from their sources (dictionary/README.md): validates dictionary/*.tsv against the
# snapshot as committed in git (at the merge base with develop, else at HEAD) and writes
# app/src/main/assets/dictionaries.json with its version digest. On any problem it names each by file and line,
# exits 1 and leaves the asset as it was.
#
#   scripts/dictionary-build.sh [--first-build]
#
# --first-build: only when no dictionary was ever shipped, so there is no committed snapshot to check ids against.
set -euo pipefail
cd "$(dirname "$0")/.."
flags=()
case "${1:-}" in
    "") ;;
    --first-build) flags+=(-PfirstBuild) ;;
    *) echo "usage: $0 [--first-build]" >&2; exit 2 ;;
esac
exec ./gradlew --quiet --console=plain "${flags[@]}" :core:buildDictionary

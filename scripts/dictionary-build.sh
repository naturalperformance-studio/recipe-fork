#!/usr/bin/env bash
# Builds the shipped dictionaries from their sources (dictionary/README.md): validates dictionary/*.tsv and writes
# app/src/main/assets/dictionaries.json with its version digest. On any problem it names each by file and line,
# exits 1 and leaves the asset as it was.
#
#   scripts/dictionary-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew --quiet --console=plain :core:buildDictionary

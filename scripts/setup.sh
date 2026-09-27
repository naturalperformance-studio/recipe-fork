#!/usr/bin/env bash
# Prepares a fresh checkout or worktree to build: untracked files do not travel into a worktree.
# Writes local.properties with the Android SDK location. Dependencies are cached in ~/.gradle
# and shared by every worktree, so nothing else needs installing here.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [ ! -d "$sdk/platforms" ]; then
    echo "Android SDK not found at $sdk (set ANDROID_HOME)" >&2
    exit 1
fi
echo "sdk.dir=$sdk" > local.properties
echo "local.properties -> sdk.dir=$sdk"

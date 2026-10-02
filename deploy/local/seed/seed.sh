#!/usr/bin/env bash
# Loads the PLACEHOLDER nickname word lists into the local deployment's store.
# Run from deploy/local (or anywhere) after `docker compose up` has started the
# server once, so that its migrations have created the tables.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U recipefork -d recipefork < seed/nickname-words.sql

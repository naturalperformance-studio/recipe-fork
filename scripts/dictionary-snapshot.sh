#!/usr/bin/env bash
# The dictionary snapshot command of docs/architecture/00-index.md: a release step.
# Fetches both dictionaries from the production server and writes them, as the server gave them
# (DictionariesDto, with the server's version), to app/src/main/assets/dictionaries.json, which is
# then committed (decisions/20260928-dictionaries-ship-with-the-application.md).
#
#   scripts/dictionary-snapshot.sh <production server URL> [output file]
#
# The URL may instead come from RECIPEFORK_PRODUCTION_URL. Needs curl and jq. The file is replaced
# only when the answer is a whole, well-formed pair of dictionaries: a failed or partial fetch leaves
# the committed snapshot as it is.
set -euo pipefail
cd "$(dirname "$0")/.."

server="${1:-${RECIPEFORK_PRODUCTION_URL:-}}"
out="${2:-app/src/main/assets/dictionaries.json}"
if [ -z "$server" ]; then
    echo "usage: $0 <production server URL> [output file]  (or set RECIPEFORK_PRODUCTION_URL)" >&2
    exit 2
fi

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl --fail --silent --show-error --location --max-time 60 \
    -H 'Accept: application/json' "${server%/}/api/v1/dictionaries" -o "$tmp"

# The store's rules, checked again on what arrived: a version, labels in both languages (L10N-003) on
# every entry, removed or not, unique ids, at most two levels of ingredients (DOM-010), a divisibility
# that is true, false or absent, and factors only for known unit codes, each a number above zero
# (DOM-011, CAT-009).
jq -e '
    (.version | type == "string" and length > 0)
    and (.ingredients | type == "array") and (.tags | type == "array")
    and ([.ingredients[], .tags[] | (.labelRu, .labelEn) | type == "string" and (gsub("\\s"; "") | length > 0)] | all)
    and ([.ingredients[] | (.divisible // null) | . == null or type == "boolean"] | all)
    and ([.ingredients[] | (.removed // false) | type == "boolean"] | all)
    and ([.ingredients[] | (.factors // {}) | type == "object"
          and (to_entries | map((.key | IN("g", "ml", "pc", "tsp", "tbsp", "cup", "pinch"))
                                and (.value | type == "number" and . > 0)) | all)] | all)
    and ([.ingredients[].id] | length == (unique | length))
    and ([.tags[].id] | length == (unique | length))
    and ([.ingredients[] | select(.parentId == null) | .id] as $top
         | [.ingredients[] | select(.parentId != null) | .parentId as $p | any($top[]; . == $p)] | all)
' "$tmp" > /dev/null || { echo "the answer is not a valid pair of dictionaries; $out left as it was" >&2; exit 1; }

mkdir -p "$(dirname "$out")"
# The DictionariesDto fields in their order, a field at its default left out as the application encodes it,
# so the server's version stays the digest of what is written.
jq '{version, ingredients: [.ingredients[] | {id, labelRu, labelEn}
        + (if .parentId == null then {} else {parentId} end)
        + (if .divisible == null then {} else {divisible} end)
        + (if (.factors // {}) == {} then {} else {factors} end)
        + (if .removed == true then {removed} else {} end)],
     tags: [.tags[] | {id, labelRu, labelEn}]}' "$tmp" > "$out.tmp"
mv "$out.tmp" "$out"
echo "$out <- ${server%/}/api/v1/dictionaries (version $(jq -r .version "$out"))"

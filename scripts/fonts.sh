#!/usr/bin/env bash
# Rebuilds the five bundled fonts in app/src/main/res/font/ and their licences in
# app/src/main/assets/licenses/ from the official SIL OFL 1.1 sources (the redesign's type roles,
# docs/brand/identity/port-1-rodoslovnaya.html, «Шрифты и Typography»). The app never downloads a font.
#
#   scripts/fonts.sh
#
# Sources, pinned: google/fonts at commit $GOOGLE_FONTS_COMMIT —
#   ofl/literata/Literata[opsz,wght].ttf   (variable; upstream googlefonts/literata)
#   ofl/literata/Literata-Italic[opsz,wght].ttf (variable; the same family's italic)
#   ofl/golostext/GolosText[wght].ttf      (variable; upstream googlefonts/golos-text)
#   ofl/literata/OFL.txt, ofl/golostext/OFL.txt
# Each download is checked against its SHA-256 below, so a moved file fails loudly.
#
# Steps: fontTools.varLib.instancer pins each variable font to a static instance (Literata at
# opsz=18 and wght=400/500, Literata Italic at opsz=18 and wght=400 for margin notes, Golos Text at
# wght=400/500); fontTools.subset then keeps Basic Latin to Latin Extended-B (U+0000–024F), Cyrillic
# (U+0400–04FF) and the signs recipes and the interface use (general punctuation, fractions, arrows, ≈,
# −, №, ×, ₽, €), the OpenType layout features (tnum among them), all name records, and no hinting
# (Android does not run TrueType hinting at phone densities).
#
# Needs curl, sha256sum and fontTools $FONTTOOLS_VERSION: run through uv when it is installed, else
# the python3 on PATH must import fontTools. Works in a temporary directory outside the repository.
set -euo pipefail
cd "$(dirname "$0")/.."

GOOGLE_FONTS_COMMIT=45e3d5269e4a17d823f6d84470bc084dc5ae46f6
FONTTOOLS_VERSION=4.67.0
# fontTools stamps head.modified with this instead of the clock, so a rerun gives the same bytes.
export SOURCE_DATE_EPOCH=1767225600
RAW="https://raw.githubusercontent.com/google/fonts/$GOOGLE_FONTS_COMMIT/ofl"

# The criterion's ranges and signs, then the rest of the signs the app's text and recipes use.
UNICODES="U+0000-024F,U+0400-04FF,U+00BD,U+00BC,U+00BE,U+2153,U+2154,U+2191,U+2193,U+2248,U+00B7,U+00AB,U+00BB,U+2014,U+2013,U+2116,U+00D7"
UNICODES="$UNICODES,U+2000-206F,U+20AC,U+20BD,U+2122,U+2150-215F,U+2190-2193,U+2212,U+2264-2265"

fonts="app/src/main/res/font"
licenses="app/src/main/assets/licenses"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

python() {
    if command -v uv >/dev/null; then
        uv run --quiet --no-project --with "fonttools==$FONTTOOLS_VERSION" -- python -I "$@"
    else
        python3 -I "$@"
    fi
}

fetch() { # <path under ofl/> <sha256> <local name>
    curl --fail --silent --show-error --location --max-time 120 "$RAW/$1" -o "$work/$3"
    echo "$2  $work/$3" | sha256sum --check --quiet
}

fetch 'literata/Literata%5Bopsz,wght%5D.ttf' b41138c9373112f32abb589cc22e8674b06ed4048b0c513be922bdd26f274440 literata.ttf
fetch 'literata/Literata-Italic%5Bopsz,wght%5D.ttf' d483dfaeba9cbf4ce71d32a52ee65df82f7e35b15fff8d1011cdb242d1fcd465 literata-italic.ttf
fetch 'golostext/GolosText%5Bwght%5D.ttf' 17bb58fb69aec2dfb047a2ebf52534023e9b688c97a6b7ac795b0a72912c2063 golos.ttf
fetch literata/OFL.txt 8742963604cd89dc81437811a850018fc03b2bfad686d7422c8235967c87614e literata-OFL.txt
fetch golostext/OFL.txt ff532f9e8789f09a9fdffc3c0954eedfb0a48be77b2e2eb90f5f82e4f347f50c golos-OFL.txt

build() { # <variable font> <axis=value…> <resource name>
    local source="$1" name="${*: -1}"
    local axes=("${@:2:$#-2}")
    python -m fontTools.varLib.instancer --quiet --static "$work/$source" "${axes[@]}" -o "$work/$name.instance.ttf"
    python -m fontTools.subset "$work/$name.instance.ttf" --unicodes="$UNICODES" \
        --layout-features='*' --name-IDs='*' --name-languages='*' --no-hinting --notdef-outline \
        --output-file="$fonts/$name.ttf"
}

mkdir -p "$fonts" "$licenses"
build literata.ttf opsz=18 wght=400 literata_regular
build literata.ttf opsz=18 wght=500 literata_medium
build literata-italic.ttf opsz=18 wght=400 literata_italic
build golos.ttf wght=400 golos_text_regular
build golos.ttf wght=500 golos_text_medium
cp "$work/literata-OFL.txt" "$licenses/Literata-OFL.txt"
cp "$work/golos-OFL.txt" "$licenses/GolosText-OFL.txt"
ls -l "$fonts"

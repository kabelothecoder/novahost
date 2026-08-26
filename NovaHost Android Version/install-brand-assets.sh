#!/usr/bin/env bash
# Installs the NovaHost brand artwork into res/drawable.
#
# Usage:  ./install-brand-assets.sh <folder-with-the-6-images>
#
# Put the six files in one folder with these names (any of .png/.jpg/.jpeg/.webp):
#
#   novahost_mark        the robot head  -- app icon, splash, onboarding art
#   broker_exness        Exness
#   broker_trade245      Trade245
#   broker_xm            XM
#   broker_razor         Razor Markets
#   broker_justmarkets   JustMarkets
#
# Android resource names may only contain [a-z0-9_], and one resource name may
# only exist once regardless of extension -- so this script clears any previous
# copy of each name before installing, which is what stops the "duplicate
# resources" build error when you switch a file from .jpg to .png.

set -euo pipefail

SRC="${1:-}"
if [[ -z "$SRC" || ! -d "$SRC" ]]; then
    echo "usage: $0 <folder-with-the-6-images>" >&2
    exit 1
fi

DEST="$(cd "$(dirname "$0")" && pwd)/app/src/main/res/drawable"
NAMES=(novahost_mark broker_exness broker_trade245 broker_xm broker_razor broker_justmarkets)

installed=0
missing=()

for name in "${NAMES[@]}"; do
    found=""
    for ext in png jpg jpeg webp PNG JPG JPEG WEBP; do
        if [[ -f "$SRC/$name.$ext" ]]; then
            found="$SRC/$name.$ext"
            break
        fi
    done

    if [[ -z "$found" ]]; then
        missing+=("$name")
        continue
    fi

    # Drop every existing extension for this resource name first.
    rm -f "$DEST/$name".{png,jpg,jpeg,webp}

    ext="${found##*.}"
    cp "$found" "$DEST/$name.$(echo "$ext" | tr '[:upper:]' '[:lower:]')"
    echo "  installed  $name.$(echo "$ext" | tr '[:upper:]' '[:lower:]')"
    installed=$((installed + 1))
done

echo
echo "$installed of ${#NAMES[@]} installed into res/drawable"

if [[ ${#missing[@]} -gt 0 ]]; then
    echo "still missing: ${missing[*]}" >&2
    echo "(the five broker tiles fall back to a name label; novahost_mark is required to build)" >&2
fi

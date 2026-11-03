#!/bin/sh
# 4irmann's FaderPort Driver for Bitwig Studio - macOS installer (.pkg)
#
# usage: installer/macos/build-pkg.sh <version> <path to FaderPort4irmann.bwextension> <output dir>
#
# The package installs into the current user's home only ("Install for me only"),
# no administrator password needed:
#   ~/Documents/Bitwig Studio/Extensions/FaderPort4irmann.bwextension
# It is not signed: macOS asks for confirmation on first open (right click > Open).
set -eu

VERSION="$1"
EXTENSION="$2"
OUT_DIR="$3"

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="$(mktemp -d)"
IDENTIFIER="io.github.4irmann.bitwig-faderport-driver"

# payload, relative to the user's home (domain enable_currentUserHome)
mkdir -p "$WORK/payload/Documents/Bitwig Studio/Extensions"
cp "$EXTENSION" "$WORK/payload/Documents/Bitwig Studio/Extensions/FaderPort4irmann.bwextension"

# component package
pkgbuild \
  --root "$WORK/payload" \
  --identifier "$IDENTIFIER" \
  --version "$VERSION" \
  --install-location "/" \
  "$WORK/component.pkg"

# resources: localized welcome/conclusion, license
mkdir -p "$WORK/resources"
cp -R "$HERE/resources/." "$WORK/resources/"
for lang in en de; do
  cp "$ROOT/LICENSE" "$WORK/resources/$lang.lproj/License.txt"
done

sed -e "s/@VERSION@/$VERSION/g" -e "s/@IDENTIFIER@/$IDENTIFIER/g" \
  "$HERE/distribution.xml" > "$WORK/distribution.xml"

mkdir -p "$OUT_DIR"
productbuild \
  --distribution "$WORK/distribution.xml" \
  --resources "$WORK/resources" \
  --package-path "$WORK" \
  "$OUT_DIR/bitwig-faderport-driver-$VERSION.pkg"

rm -rf "$WORK"
echo "created $OUT_DIR/bitwig-faderport-driver-$VERSION.pkg"

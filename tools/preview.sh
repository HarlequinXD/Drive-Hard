#!/bin/sh
# Bakes the character sprites on the desktop (plain JDK, no Android SDK) and writes PNG previews.
set -e
cd "$(dirname "$0")/.."
OUT=build/preview
mkdir -p "$OUT/classes"
javac -d "$OUT/classes" $(find app/src/main/java/com/harlequinxd/drivehard/core -name '*.java') tools/BakePreview.java
java -cp "$OUT/classes" BakePreview "$OUT"
echo "Previews written to $OUT"

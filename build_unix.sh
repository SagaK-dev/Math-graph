#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes src/*.java
printf 'Main-Class: Launcher\n\n' > build/MANIFEST.MF
jar --create --file GeoSketch.jar --manifest build/MANIFEST.MF -C build/classes .
echo 'Built: GeoSketch.jar'

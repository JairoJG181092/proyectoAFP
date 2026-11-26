#!/bin/sh
# Compila para Java 8 y genera afp2pdf.jar (requiere JDK; sin dependencias).
set -e
cd "`dirname "$0"`"
rm -rf build
mkdir build
if javac --release 8 -version >/dev/null 2>&1; then OPT="--release 8"; else OPT="-source 1.8 -target 1.8"; fi
find src -name '*.java' > build/sources.txt
javac $OPT -encoding UTF-8 -d build/classes @build/sources.txt
jar cfe bin/afp2pdf.jar afp2pdf.Main -C build/classes .
echo "Listo: bin/afp2pdf.jar"

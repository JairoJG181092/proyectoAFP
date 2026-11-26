#!/bin/sh
# Linux / Solaris (Java 8, 64 bits). En Solaris agregar -d64 si hace falta.
DIR=$(cd "$(dirname "$0")/.." && pwd)
exec java -Xmx512m -cp "$DIR/build" mx.afp2pdf.reader.AfpInventory "$@"

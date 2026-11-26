#!/bin/sh
# Linux / Solaris. Variables opcionales: JAVA_HOME, AFP2PDF_XMX
DIR=`dirname "$0"`
JAR="$DIR/afp2pdf.jar"

if [ -n "$JAVA_HOME" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
OPTS="-Djava.awt.headless=true -Xmx${AFP2PDF_XMX:-1g}"
# Solaris con Java 8: forzar JVM de 64 bits
if [ "`uname -s`" = "SunOS" ] && "$JAVA" -d64 -version >/dev/null 2>&1; then OPTS="-d64 $OPTS"; fi
exec "$JAVA" $OPTS -jar "$JAR" "$@"

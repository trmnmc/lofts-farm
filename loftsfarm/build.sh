#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

API="C:/Users/truman/OneDrive/Documents/lofts-dev/libraries/io/papermc/paper/paper-api/1.21.10-R0.1-SNAPSHOT/paper-api-1.21.10-R0.1-SNAPSHOT.jar"
KYORI="C:/Users/truman/OneDrive/Documents/lofts-dev/libraries/net/kyori"
JAR="C:/Program Files/Java/jdk-21/bin/jar.exe"
OUT=build/classes
JARFILE=build/LoftsFarm.jar

[ -f "$API" ] || { echo "Paper API jar not found at $API"; exit 1; }
[ -d "$KYORI" ] || { echo "Adventure jars not found at $KYORI"; exit 1; }

# Paper's API jar does not bundle Adventure, whose types appear in JavaPlugin's
# supertype chain. Every local Adventure jar goes on the compile classpath.
# Note: ';' separator — javac here is a Windows binary.
CP="$API"
while IFS= read -r j; do CP="$CP;$j"; done < <(find "$KYORI" -name '*.jar' | sort)

rm -rf "$OUT"; mkdir -p "$OUT" build
find src/main/java -name '*.java' -print0 | xargs -0 -r javac -cp "$CP" -d "$OUT" -Xlint:all
cp -r src/main/resources/* "$OUT"/
"$JAR" --create --file "$JARFILE" -C "$OUT" .

echo "Built $JARFILE"

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

LIBS="C:/Users/truman/OneDrive/Documents/lofts-dev/libraries"
API="$LIBS/io/papermc/paper/paper-api/1.21.10-R0.1-SNAPSHOT/paper-api-1.21.10-R0.1-SNAPSHOT.jar"
JAR="C:/Program Files/Java/jdk-21/bin/jar.exe"
OUT=build/classes
JARFILE=build/LoftsFarm.jar

[ -f "$API" ] || { echo "Paper API jar not found at $API"; exit 1; }
[ -d "$LIBS" ] || { echo "Server libraries not found at $LIBS"; exit 1; }

# javac must resolve the full overload set of every Bukkit type we touch, which
# pulls in Paper's own transitive deps — Adventure, bungeecord-chat, Guava and
# friends. Compiling against the whole local library tree is the only stable
# answer; naming jars one at a time just moves the next failure later.
# ';' separator — javac here is a Windows binary.
CP="$API"
while IFS= read -r j; do CP="$CP;$j"; done < <(find "$LIBS" -name '*.jar' | sort)

rm -rf "$OUT"; mkdir -p "$OUT" build
find src/main/java -name '*.java' -print0 | xargs -0 -r javac -encoding UTF-8 -cp "$CP" -d "$OUT" -Xlint:all
cp -r src/main/resources/* "$OUT"/
"$JAR" --create --file "$JARFILE" -C "$OUT" .

echo "Built $JARFILE"

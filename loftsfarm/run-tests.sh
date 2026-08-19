#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

OUT=build/test-classes
rm -rf "$OUT"; mkdir -p "$OUT"

# Domain layer has no Bukkit dependency, so tests need no classpath beyond itself.
find src/main/java src/test/java -name '*.java' \
  ! -path '*/bukkit/*' \
  -print0 | xargs -0 -r javac -d "$OUT" -Xlint:all

java -cp "$OUT" place.games.loftsfarm.test.TestMain

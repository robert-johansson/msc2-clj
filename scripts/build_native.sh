#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if ! command -v native-image >/dev/null 2>&1; then
  echo "native-image not found on PATH. Please ensure GraalVM is installed and native-image component added." >&2
  exit 1
fi

echo "Building uberjar via tools.build..."
clojure -T:build uber

JAR="target/msc2-standalone.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Unable to find $JAR" >&2
  exit 1
fi

OUTPUT="target/NAR"
echo "Invoking native-image -> $OUTPUT"
native-image \
  --report-unsupported-elements-at-runtime \
  --initialize-at-build-time \
  --no-fallback \
  -H:+UnlockExperimentalVMOptions \
  -jar "$JAR" \
  -H:Name="$OUTPUT" \
  "$@"

echo ""
echo "Native binary created at $OUTPUT"

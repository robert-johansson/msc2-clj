#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FIXTURES=()
if [ "$#" -gt 0 ]; then
  for arg in "$@"; do
    FIXTURES+=("$arg")
  done
else
  while IFS= read -r path; do
    FIXTURES+=("$path")
  done < <(find "resources/experiments/micro" -maxdepth 1 -name 'exp3_*.nal' -print | sort)
fi

if [ "${#FIXTURES[@]}" -eq 0 ]; then
  echo "No exp3 fixtures found under resources/experiments/micro" >&2
  exit 1
fi

if [ ! -x "./external/msc2/NAR" ]; then
  echo "Missing ./external/msc2/NAR binary. Build the C reference before running this script." >&2
  exit 1
fi

mkdir -p experiments/micro

STATUS=0
for fixture in "${FIXTURES[@]}"; do
  if [ ! -f "$fixture" ]; then
    echo "Skipping missing fixture $fixture" >&2
    STATUS=1
    continue
  fi
  base="$(basename "$fixture" .nal)"
  clj_log="experiments/micro/${base}.clj.log"
  c_log="experiments/micro/${base}.c.log"
  clj_derived="experiments/micro/${base}.clj.derived"
  c_derived="experiments/micro/${base}.c.derived"
  diff_log="experiments/micro/${base}.diff"
  clj_norm="${clj_derived}.norm"
  c_norm="${c_derived}.norm"
  clj_terms="${clj_norm}.terms"
  c_terms="${c_norm}.terms"

  echo "==> Running Clojure shell for ${base}"
  clj -M -m msc2.shell < "$fixture" > "$clj_log"

  echo "==> Running C reference for ${base}"
  ./external/msc2/NAR shell < "$fixture" > "$c_log"

  rg --no-heading --no-line-number 'Derived:.*=/>\s+G>' "$clj_log" > "$clj_derived" || true
  rg --no-heading --no-line-number 'Derived:.*=/>\s+G>' "$c_log" > "$c_derived" || true

  perl -0pe 's/\[:atom "([^"]+)"\]/\1/g; s/\[:op "([^"]+)"\]/\1/g' "$clj_derived" > "$clj_norm"
  perl -0pe 's/\[:atom "([^"]+)"\]/\1/g; s/\[:op "([^"]+)"\]/\1/g' "$c_derived" > "$c_norm"

  sed -E 's/^Derived:[^<]*(<.*>)\. Priority=.*/\1/' "$clj_norm" | sed '/^$/d' | sort -u > "$clj_terms"
  sed -E 's/^Derived:[^<]*(<.*>)\. Priority=.*/\1/' "$c_norm" | sed '/^$/d' | sort -u > "$c_terms"

  if diff -u "$c_terms" "$clj_terms" > "$diff_log"; then
    echo "✔ ${base}: outputs match"
    rm -f "$diff_log"
  else
    echo "✖ ${base}: derived outputs differ (see ${diff_log})"
    STATUS=1
  fi
done

exit $STATUS

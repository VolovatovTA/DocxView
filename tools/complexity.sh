#!/usr/bin/env bash
# Cyclomatic complexity of every library function (lizard); fails when one exceeds MAX_CCN.
# Locally: pip install lizard==1.24.1 && tools/complexity.sh
set -euo pipefail

MAX_CCN="${MAX_CCN:-20}"
SOURCES=(docviewer-core/src/main docviewer/src/main)
REPORT=build/reports/complexity
mkdir -p "$REPORT"

lizard -l kotlin "${SOURCES[@]}" --csv > "$REPORT/functions.csv"

# The most complex functions, as a table in the GitHub job summary (or the terminal).
summary() {
  echo "### Most complex functions (limit: $MAX_CCN)"
  echo
  echo "| Complexity | Lines | Function | File |"
  echo "|---:|---:|---|---|"
  # awk reads to the end instead of head, which would break the pipe under pipefail.
  sort -t, -k2,2nr "$REPORT/functions.csv" |
    awk -F, 'NR <= 15 { gsub(/"/, ""); sub(/.*\/docviewer\//, "", $7); printf "| %s | %s | `%s` | %s |\n", $2, $1, $8, $7 }'
}
summary | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

# -i 0: no function may exceed the limit; lizard exits non-zero and prints the offenders.
lizard -l kotlin "${SOURCES[@]}" --CCN "$MAX_CCN" --warnings_only -i 0

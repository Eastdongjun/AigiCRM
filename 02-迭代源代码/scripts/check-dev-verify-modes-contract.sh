#!/usr/bin/env bash
# Keep dev-verify's public command surface documented and callable.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DEV_VERIFY" ] || fail "Missing $DEV_VERIFY"

case_modes="$(
  awk '
    /^case "\$MODE" in/ { in_case=1; next }
    in_case && /^[[:space:]]*help\|-h\|--help\)/ { next }
    in_case && /^[[:space:]]*\*\)/ { exit }
    in_case && /^[[:space:]]*[[:alnum:]_-]+\)/ {
      line=$1
      sub(/\).*/, "", line)
      print line
    }
  ' "$DEV_VERIFY" | sort -u
)"

[ -n "$case_modes" ] || fail "No public dev-verify modes found in case dispatcher"

help_output="$(bash "$DEV_VERIFY" help)"

missing=""
while IFS= read -r mode; do
  [ -n "$mode" ] || continue
  if ! printf '%s\n' "$help_output" | grep -Eq "^[[:space:]]{2}${mode}([[:space:]]|$)"; then
    missing="${missing}${mode}
"
  fi
done <<< "$case_modes"

[ -z "$missing" ] || fail "dev-verify modes missing from help: $(printf '%s' "$missing" | tr '\n' ' ')"

for required in changed untracked contracts shell-syntax python-syntax automation-safety verify-modes verify-executables verify-fail-fast verify-evidence-plans verify-dockerfile pr; do
  printf '%s\n' "$case_modes" | grep -Fxq "$required" || fail "Required dev-verify mode is not dispatched: $required"
  printf '%s\n' "$help_output" | grep -Eq "^[[:space:]]{2}${required}([[:space:]]|$)" || fail "Required dev-verify mode is not documented: $required"
done

echo "PASS: dev-verify documents all $(printf '%s\n' "$case_modes" | sed '/^$/d' | wc -l | tr -d ' ') public modes."

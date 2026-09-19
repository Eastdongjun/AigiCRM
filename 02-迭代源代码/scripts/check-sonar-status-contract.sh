#!/usr/bin/env bash
# Keep Sonar status reporting from confusing local scan status with server QG.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/check-sonar-status.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$SCRIPT" ] || fail "Missing $SCRIPT"

grep -Fq 'sonar quality-gate status -p "$PROJECT_KEY"' "$SCRIPT" ||
  fail "Sonar status must read the server Quality Gate"

grep -Fq 'SONAR_QUALITY_GATE=NOT_COMPUTED' "$SCRIPT" ||
  fail "Sonar status must explicitly report Not computed Quality Gate"

grep -Fq '不能声明 Sonar 门禁通过' "$SCRIPT" ||
  fail "Sonar status must warn when Quality Gate cannot prove pass"

grep -Fq 'status=2' "$SCRIPT" ||
  fail "Sonar status must use exit 2 for incomplete verification states"

grep -Fq 'SONAR_LOCAL_SCAN=INCOMPLETE_VORTEX_UNAVAILABLE' "$SCRIPT" ||
  fail "Sonar status must distinguish unavailable local Vortex analysis"

echo "PASS: Sonar status reports local scan and server Quality Gate as separate verification dimensions."

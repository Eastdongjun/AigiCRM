#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SMOKE="$ROOT/scripts/smoke-test.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq '[prod|test|local]' "$SMOKE" ||
  fail "smoke-test usage must document local mode"

grep -Fq 'if [ "$ENV" = "local" ]' "$SMOKE" ||
  fail "smoke-test must branch before deploy env loading for local mode"

grep -Fq 'AIGICRM_BACKEND_URL' "$SMOKE" ||
  fail "local smoke must accept AIGICRM_BACKEND_URL"

grep -Fq 'AIGICRM_FRONTEND_URL' "$SMOKE" ||
  fail "local smoke must optionally accept AIGICRM_FRONTEND_URL"

grep -Fq 'for route in / /login /403' "$SMOKE" ||
  fail "local smoke must verify the expected frontend preflight routes"

grep -Fq 'sys-user-controller' "$SMOKE" ||
  fail "local smoke must identify the AIGICRM backend"

grep -Fq '/api/system/configs/public' "$SMOKE" ||
  fail "local smoke must verify the DB-backed public config endpoint"

grep -Fq '/api/auth/info' "$SMOKE" ||
  fail "local smoke must verify the unauthenticated protected endpoint boundary"

local_branch_line="$(grep -nF 'if [ "$ENV" = "local" ]' "$SMOKE" | head -1 | cut -d: -f1)"
source_line="$(grep -nF 'source "$SCRIPT_DIR/deploy-lib.sh"' "$SMOKE" | head -1 | cut -d: -f1)"
[ -n "$local_branch_line" ] && [ -n "$source_line" ] && [ "$local_branch_line" -lt "$source_line" ] ||
  fail "local smoke must run before deploy-lib is sourced"

echo "PASS: smoke-test local mode is deploy-env-free and verifies AIGICRM public runtime."

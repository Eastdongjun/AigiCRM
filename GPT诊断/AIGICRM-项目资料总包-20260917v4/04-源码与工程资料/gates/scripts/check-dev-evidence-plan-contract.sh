#!/usr/bin/env bash
# Keep dev-evidence's generated central plans from silently losing critical checks.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_EVIDENCE="$ROOT/scripts/dev-evidence.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

plan_function_body() {
  local function_name="$1"
  awk -v name="$function_name" '
    $0 ~ "^" name "\\(\\)[[:space:]]*\\{" { in_fn=1 }
    in_fn { print }
    in_fn && /^}/ { exit }
  ' "$DEV_EVIDENCE"
}

assert_plan_contains() {
  local function_name="$1"
  local expected="$2"
  plan_function_body "$function_name" | grep -Fq "$expected" ||
    fail "$function_name missing required plan fragment: $expected"
}

[ -f "$DEV_EVIDENCE" ] || fail "Missing $DEV_EVIDENCE"

assert_plan_contains write_plan_quick '"id": "automation-safety"'
assert_plan_contains write_plan_quick '"id": "verify-modes"'
assert_plan_contains write_plan_quick '"id": "verify-executables"'
assert_plan_contains write_plan_quick '"id": "verify-fail-fast"'
assert_plan_contains write_plan_quick '"id": "verify-evidence-plans"'
assert_plan_contains write_plan_quick '"id": "sea-matrix-blocked"'

assert_plan_contains write_plan_changed '"id": "backend-http-preflight"'
assert_plan_contains write_plan_changed '"id": "frontend-http-preflight"'
assert_plan_contains write_plan_changed '"id": "changed-gate-preflight"'
assert_plan_contains write_plan_changed '"id": "diff-check-automation"'
assert_plan_contains write_plan_changed 'git diff --check -- 02-迭代源代码/scripts'

assert_plan_contains write_plan_sea '"id": "sea-contract"'
assert_plan_contains write_plan_sea '"id": "diff-check-automation"'
assert_plan_contains write_plan_sea 'git diff --check -- 02-迭代源代码/scripts'
assert_plan_contains write_plan_sea '02-迭代源代码/.githooks/pre-push'

assert_plan_contains write_plan_miniprogram '"id": "miniprogram-check"'
assert_plan_contains write_plan_miniprogram '"id": "diff-check-automation"'
assert_plan_contains write_plan_miniprogram 'git diff --check -- 02-迭代源代码/scripts'

assert_plan_contains write_plan_sonar_status '"id": "sonar-status"'
assert_plan_contains write_plan_sonar_status 'bash scripts/check-sonar-status.sh'
assert_plan_contains write_plan_sonar_status 'test \"\$rc\" -eq 0 -o \"\$rc\" -eq 2'

echo "PASS: dev-evidence generated plans retain required self-checks and diff scopes."

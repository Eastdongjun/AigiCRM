#!/usr/bin/env bash
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MATRIX="$ROOT/scripts/verify-sea-claim-matrix.sh"

expect_blocked() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "FAIL: $label should be blocked" >&2
    return 1
  fi
  echo "PASS: $label is blocked"
}

expect_blocked "missing matrix inputs" bash "$MATRIX" preflight
expect_blocked "non-test base URL" env \
  AIGICRM_MATRIX_BASE_URL="https://production.example" \
  AIGICRM_MATRIX_APPLICANT_USER=applicant \
  AIGICRM_MATRIX_APPROVER_USER=approver \
  AIGICRM_MATRIX_TEST_CUSTOMER_ID=1 \
  bash "$MATRIX" preflight
expect_blocked "mutation without explicit opt-in" env \
  AIGICRM_MATRIX_BASE_URL="http://127.0.0.1:8080" \
  AIGICRM_MATRIX_APPLICANT_USER=applicant \
  AIGICRM_MATRIX_APPROVER_USER=approver \
  AIGICRM_MATRIX_TEST_CUSTOMER_ID=1 \
  bash "$MATRIX" preflight

if env \
  AIGICRM_MATRIX_BASE_URL="http://127.0.0.1:8080" \
  AIGICRM_MATRIX_APPLICANT_USER=applicant \
  AIGICRM_MATRIX_APPROVER_USER=approver \
  AIGICRM_MATRIX_TEST_CUSTOMER_ID=1 \
  AIGICRM_MATRIX_ALLOW_MUTATION=1 \
  bash "$MATRIX" preflight >/dev/null; then
  echo "PASS: local test URL passes with explicit mutation opt-in"
else
  echo "FAIL: local test URL should pass with explicit mutation opt-in" >&2
  exit 1
fi

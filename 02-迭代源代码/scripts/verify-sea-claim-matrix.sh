#!/usr/bin/env bash
# Safe public-sea real-account acceptance matrix entrypoint.
# Default mode only validates that a non-production test environment is configured.

set -u

MODE="${1:-preflight}"

required_vars=(
  AIGICRM_MATRIX_BASE_URL
  AIGICRM_MATRIX_APPLICANT_USER
  AIGICRM_MATRIX_APPROVER_USER
  AIGICRM_MATRIX_TEST_CUSTOMER_ID
)

blocked() {
  echo "BLOCKED: $*" >&2
  exit 2
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_test_url() {
  case "${AIGICRM_MATRIX_BASE_URL:-}" in
    *localhost*|*127.0.0.1*|*test*|*staging*|*dev*) ;;
    "") blocked "AIGICRM_MATRIX_BASE_URL is required" ;;
    *) blocked "Refusing non-test base URL: $AIGICRM_MATRIX_BASE_URL" ;;
  esac
}

preflight() {
  local missing=()
  for name in "${required_vars[@]}"; do
    if [ -z "${!name:-}" ]; then
      missing+=("$name")
    fi
  done

  if [ "${#missing[@]}" -gt 0 ]; then
    printf 'BLOCKED: missing real matrix inputs:\n' >&2
    printf '  %s\n' "${missing[@]}" >&2
    exit 2
  fi

  require_test_url

  if [ "${AIGICRM_MATRIX_ALLOW_MUTATION:-0}" != "1" ]; then
    blocked "preflight passed, but mutation is disabled; set AIGICRM_MATRIX_ALLOW_MUTATION=1 only in a safe test environment"
  fi

  echo "PASS: real matrix inputs are present and mutation was explicitly enabled for a test URL."
}

case "$MODE" in
  preflight) preflight ;;
  *)
    fail "Unknown mode: $MODE"
    ;;
esac

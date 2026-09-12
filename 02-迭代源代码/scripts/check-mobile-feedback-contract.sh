#!/usr/bin/env bash
# Keep mobile request failures on the shared feedback convention.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FEEDBACK="$ROOT/aigicrm-miniprogram/utils/feedback.js"
fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq 'function errorMessage' "$FEEDBACK" || fail 'missing shared errorMessage'
grep -Fq 'function showError' "$FEEDBACK" || fail 'missing shared showError'
for page in $(find "$ROOT/aigicrm-miniprogram/pages" -name '*.js' -type f -exec grep -lE "require.*api/" {} + | sort -u); do
  grep -Fq "utils/feedback" "$page" || fail "page does not use shared feedback: $page"
  grep -Fq 'showError(' "$page" || fail "page does not call showError: $page"
  # Login and password recovery are the two intentional anonymous API flows.
  if [[ "$page" != *"/pages/login/index.js" && "$page" != *"/pages/login/forgot/index.js" ]]; then
    grep -Fq 'requireLogin' "$page" || fail "API page does not enforce login: $page"
  fi
done
echo 'PASS: mobile feedback contract is aligned.'

#!/usr/bin/env bash
# Keep the untracked inventory useful for delivery review.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DEV_VERIFY" ] || fail "Missing $DEV_VERIFY"

output="$(bash "$DEV_VERIFY" untracked)"

printf '%s\n' "$output" | grep -Fq 'AIGICRM untracked inventory' ||
  fail "untracked inventory must have a recognizable title"

printf '%s\n' "$output" | grep -Eq 'summary: total=[0-9]+; migrations=[0-9]+; backend=[0-9]+; frontend=[0-9]+; miniprogram=[0-9]+; scripts=[0-9]+; docs=[0-9]+; config=[0-9]+; other=[0-9]+' ||
  fail "untracked inventory must show the standard category summary"

for category in migrations backend frontend miniprogram scripts docs config other; do
  printf '%s\n' "$output" | grep -Eq "^  ${category} \\([0-9]+\\):$" ||
    fail "untracked inventory must include the ${category} category"
done

printf '%s\n' "$output" | grep -Fq 'aigicrm-backend/src/main/resources/db/migration/V' ||
  fail "untracked inventory must list untracked Flyway migrations when present"

printf '%s\n' "$output" | grep -Fq 'scripts/check-dev-verify-untracked-contract.sh' ||
  fail "untracked inventory must include newly added untracked contract scripts"

for batch in database-permissions backend-regression frontend-features automation-contracts delivery-docs; do
  printf '%s\n' "$output" | grep -Fq "    - ${batch}:" ||
    fail "untracked inventory must include the ${batch} review batch"
done

echo "PASS: dev-verify untracked inventory is categorized and reviewable."

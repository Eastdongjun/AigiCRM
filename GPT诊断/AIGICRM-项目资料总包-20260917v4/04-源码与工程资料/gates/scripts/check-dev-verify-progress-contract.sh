#!/usr/bin/env bash
# Keep dev-verify status/progress honest about the latest Sonar evidence boundary.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DEV_VERIFY" ] || fail "Missing $DEV_VERIFY"

grep -Fq 'latest_sonar_evidence_summary()' "$DEV_VERIFY" ||
  fail "dev-verify must provide a latest Sonar evidence summary helper"

grep -Fq 'changed_breakdown()' "$DEV_VERIFY" ||
  fail "dev-verify must provide a changed-file breakdown helper"

grep -Fq 'untracked_breakdown()' "$DEV_VERIFY" ||
  fail "dev-verify must provide an untracked-file breakdown helper"

grep -Fq 'changed_migrations()' "$DEV_VERIFY" ||
  fail "dev-verify must provide a changed Flyway migration list helper"

grep -Fq '^-?[[:space:]]*Result:[[:space:]]*' "$DEV_VERIFY" ||
  fail "recent-run parser must support list-style Result lines from handoff.md"

grep -Fq '状态：' "$DEV_VERIFY" ||
  fail "recent-run parser must support Chinese status fallback lines from handoff.md"

grep -Fq "SONAR_LOCAL_SCAN=" "$DEV_VERIFY" ||
  fail "Sonar summary must read the local scan status marker"

grep -Fq "SONAR_QUALITY_GATE=" "$DEV_VERIFY" ||
  fail "Sonar summary must read the server Quality Gate status marker"

grep -Fq 'echo "  sonar evidence: $(latest_sonar_evidence_summary)"' "$DEV_VERIFY" ||
  fail "status output must include latest Sonar evidence"

grep -Fq 'echo "  changed breakdown: $(changed_breakdown)"' "$DEV_VERIFY" ||
  fail "status output must include changed-file breakdown"

grep -Fq 'echo "  untracked breakdown: $(untracked_breakdown)"' "$DEV_VERIFY" ||
  fail "status output must include untracked-file breakdown"

grep -Fq 'echo "  changed migrations:"' "$DEV_VERIFY" ||
  fail "status output must include changed Flyway migrations when present"

grep -Fq 'echo "  Latest Sonar evidence: $(latest_sonar_evidence_summary)"' "$DEV_VERIFY" ||
  fail "progress output must include latest Sonar evidence"

status_output="$(bash "$DEV_VERIFY" status)"
progress_output="$(bash "$DEV_VERIFY" progress)"

printf '%s\n' "$status_output" | grep -Fq 'sonar evidence:' ||
  fail "status output did not show Sonar evidence"

printf '%s\n' "$status_output" | grep -Eq 'changed breakdown: total=[0-9]+; migrations=[0-9]+; backend=[0-9]+; frontend=[0-9]+; miniprogram=[0-9]+; scripts=[0-9]+; docs=[0-9]+; config=[0-9]+; other=[0-9]+' ||
  fail "status output did not show changed-file breakdown"

printf '%s\n' "$status_output" | grep -Eq 'untracked breakdown: total=[0-9]+; migrations=[0-9]+; backend=[0-9]+; frontend=[0-9]+; miniprogram=[0-9]+; scripts=[0-9]+; docs=[0-9]+; config=[0-9]+; other=[0-9]+' ||
  fail "status output did not show untracked-file breakdown"

printf '%s\n' "$status_output" | grep -Eq 'aigicrm-backend/src/main/resources/db/migration/V.+\.sql' ||
  fail "status output did not list changed Flyway migrations"

printf '%s\n' "$progress_output" | grep -Fq 'Latest Sonar evidence:' ||
  fail "progress output did not show Sonar evidence"

echo "PASS: dev-verify status and progress expose the latest Sonar evidence boundary, changed/untracked breakdowns, and changed Flyway migrations."

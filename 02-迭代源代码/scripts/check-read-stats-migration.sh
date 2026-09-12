#!/usr/bin/env bash
# Validate the local Flyway contract for the incentive read-stats permission.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V24__incentive_read_status.sql"

fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$MIGRATION" ] || fail "missing migration: $MIGRATION"
grep -Eq "'incentive:read-stats'" "$MIGRATION" || fail "permission code is missing"
grep -Eq "INSERT[[:space:]]+INTO[[:space:]]+sys_menu" "$MIGRATION" || fail "sys_menu insert is missing"
grep -Eq "INSERT[[:space:]]+INTO[[:space:]]+sys_role_menu" "$MIGRATION" || fail "sys_role_menu grant insert is missing"
grep -Eq "NOT[[:space:]]+EXISTS[[:space:]]*\(" "$MIGRATION" || fail "idempotency guard is missing"
grep -Eq "permission[[:space:]]*=[[:space:]]*'incentive:read-stats'" "$MIGRATION" || fail "idempotency guard does not target the permission"
grep -Eq "permission[[:space:]]*=[[:space:]]*'incentive:view'" "$MIGRATION" || fail "grant source must follow incentive:view roles"
grep -Eq "perm_version[[:space:]]*=[[:space:]]*COALESCE\\(u\\.perm_version,[[:space:]]*0\\)[[:space:]]*\\+[[:space:]]*1" "$MIGRATION" || fail "affected user permission cache version bump is missing"

echo "PASS: incentive:read-stats Flyway migration contract is valid."

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$ROOT/scripts/seed-local-statistics-fixture.sql"
schema="$ROOT/aigicrm-backend/src/main/resources/db/migration/V1__baseline.sql"
test -s "$fixture" || { echo "FAIL: local statistics fixture is missing" >&2; exit 1; }
for table in biz_customer biz_customer_indicator biz_invoice biz_payment biz_file; do
  grep -q "INSERT INTO \`$table\`" "$fixture" || { echo "FAIL: fixture is missing $table" >&2; exit 1; }
  grep -q "CREATE TABLE \`$table\`" "$schema" || { echo "FAIL: schema is missing $table" >&2; exit 1; }
done
grep -q "LOCAL-STAT-01" "$fixture" || { echo "FAIL: fixture has no stable customer marker" >&2; exit 1; }
grep -q "990001" "$fixture" || { echo "FAIL: fixture has no stable ID marker" >&2; exit 1; }
if grep -Eiq 'password|secret|token|api[_-]?key' "$fixture"; then echo "FAIL: statistics fixture contains credential-like text" >&2; exit 1; fi
echo "PASS: local statistics fixture contract is aligned."

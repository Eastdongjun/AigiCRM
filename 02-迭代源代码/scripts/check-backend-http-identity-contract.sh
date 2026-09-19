#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'http_body()' "$VERIFY" ||
  fail "backend HTTP preflight must be able to inspect response bodies"

grep -Fq '"title":"OpenAPI definition"' "$VERIFY" ||
  fail "backend HTTP preflight must validate the OpenAPI document shape"

grep -Fq 'sys-user-controller' "$VERIFY" ||
  fail "backend HTTP preflight must identify the AIGICRM system user controller"

grep -Fq '/api/system/configs/public' "$VERIFY" ||
  fail "backend HTTP preflight must require the AIGICRM public config endpoint"

grep -Fq 'public config=200' "$VERIFY" ||
  fail "backend HTTP preflight pass message must include the DB-backed public config check"

grep -Fq 'backend public config returned HTTP' "$VERIFY" ||
  fail "backend HTTP preflight must fail closed when public config is not HTTP 200"

echo "PASS: backend HTTP preflight validates AIGICRM identity and DB-backed public config."

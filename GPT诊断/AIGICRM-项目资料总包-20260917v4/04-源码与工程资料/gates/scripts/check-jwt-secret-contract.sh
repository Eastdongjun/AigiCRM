#!/usr/bin/env bash
# Keep JWT signing secrets explicit, strong, and outside the repository.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/aigicrm-backend/src/main/resources/application.yml"
PROVIDER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/security/JwtTokenProvider.java"
START="$ROOT/scripts/start-local-backend.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq 'secret: ${JWT_SECRET}' "$CONFIG" || fail 'application config must require JWT_SECRET without a fallback'
grep -Fq 'JWT_SECRET is required' "$START" || fail 'local startup must fail when JWT_SECRET is missing'
grep -Fq 'secret.getBytes(StandardCharsets.UTF_8).length < 32' "$PROVIDER" \
  || fail 'JWT provider must reject secrets shorter than 32 bytes'
! grep -Fq 'secret: ${JWT_SECRET:' "$CONFIG" || fail 'application config must not define a JWT fallback secret'
! grep -Fq 'secret: dev-' "$CONFIG" || fail 'application config must not contain a development JWT secret'
echo 'PASS: JWT secret contract requires explicit external 256-bit configuration.'

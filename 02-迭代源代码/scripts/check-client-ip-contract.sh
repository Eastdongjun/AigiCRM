#!/usr/bin/env bash
# Keep login rate limiting tied to a trusted proxy-aware client IP.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UTIL="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/common/util/ClientIpUtils.java"
AUTH="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq 'request.getRemoteAddr()' "$UTIL" || fail 'client IP utility must start from the direct remote address'
grep -Fq 'isTrustedProxy(remoteAddr)' "$UTIL" || fail 'proxy header must be gated by trusted proxy detection'
grep -Fq 'request.getHeader("X-Real-IP")' "$UTIL" || fail 'trusted proxy path must support X-Real-IP'
! grep -Fq 'getHeader("X-Forwarded-For")' "$UTIL" || fail 'client IP utility must not trust X-Forwarded-For'
grep -Fq 'ClientIpUtils.getClientIp(httpRequest)' "$AUTH" || fail 'login controller must use the trusted client IP utility'
echo 'PASS: client IP contract rejects spoofable forwarded headers.'

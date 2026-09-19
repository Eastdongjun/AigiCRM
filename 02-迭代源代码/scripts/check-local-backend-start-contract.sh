#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/start-local-backend.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'LOCAL_DOCKER_MODE' "$SCRIPT" ||
  fail "start-local-backend must expose explicit LOCAL_DOCKER_MODE opt-in"

grep -Fq 'docker inspect aigicrm-local-mysql' "$SCRIPT" ||
  fail "LOCAL_DOCKER_MODE must verify aigicrm-local-mysql exists before startup"

grep -Fq 'docker inspect aigicrm-local-redis' "$SCRIPT" ||
  fail "LOCAL_DOCKER_MODE must verify aigicrm-local-redis exists before startup"

grep -Fq 'docker exec aigicrm-local-mysql printenv MYSQL_ROOT_PASSWORD' "$SCRIPT" ||
  fail "LOCAL_DOCKER_MODE must source the local MySQL password from the container"

grep -Fq 'DB_PORT="${DB_PORT:-3306}"' "$SCRIPT" ||
  fail "LOCAL_DOCKER_MODE must default to the local mapped MySQL port 3306"

grep -Fq 'REDIS_PORT="${REDIS_PORT:-6380}"' "$SCRIPT" ||
  fail "LOCAL_DOCKER_MODE must default to the local mapped Redis port 6380"

if grep -Eq 'echo .*\$DB_PASSWORD|echo .*\$MYSQL_ROOT_PASSWORD|printf .*\$DB_PASSWORD|printf .*\$MYSQL_ROOT_PASSWORD' "$SCRIPT"; then
  fail "start-local-backend must not print local database password values"
fi

echo "PASS: local backend startup supports explicit Docker-backed local runtime without printing credentials."

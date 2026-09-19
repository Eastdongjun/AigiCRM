#!/usr/bin/env bash
# Guard production-sensitive configuration against weak defaults.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_CONFIG="$ROOT/aigicrm-backend/src/main/resources/application.yml"
COMPOSE="$ROOT/docker-compose.prod.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'password: ${DB_PASSWORD}' "$APP_CONFIG" \
  || fail 'application.yml must require DB_PASSWORD without a fallback'
! grep -Fq '${DB_PASSWORD:' "$APP_CONFIG" \
  || fail 'application.yml must not define a DB_PASSWORD fallback'
grep -Fq 'secret: ${JWT_SECRET}' "$APP_CONFIG" \
  || fail 'application.yml must require JWT_SECRET without a fallback'
! grep -Fq '${JWT_SECRET:' "$APP_CONFIG" \
  || fail 'application.yml must not define a JWT_SECRET fallback'

grep -Fq 'MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD 未设置' "$COMPOSE" \
  || fail 'production compose must require DB_PASSWORD for the MySQL root password'
grep -Fq 'DB_USER: ${APP_DB_USER:?APP_DB_USER 未设置' "$COMPOSE" \
  || fail 'production compose must require APP_DB_USER for the backend application account'
grep -Fq 'DB_PASSWORD: ${APP_DB_PASSWORD:?APP_DB_PASSWORD 未设置' "$COMPOSE" \
  || fail 'production compose must require APP_DB_PASSWORD for the backend application account'
grep -Fq 'JWT_SECRET: ${JWT_SECRET:?JWT_SECRET 未设置}' "$COMPOSE" \
  || fail 'production compose must require JWT_SECRET'
grep -Fq 'SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}' "$COMPOSE" \
  || fail 'production compose must default SPRING_PROFILES_ACTIVE to prod'
grep -Fq 'TENANT_ENABLE: ${TENANT_ENABLE:?' "$COMPOSE" \
  || fail 'production compose must require TENANT_ENABLE explicitly (no silent false default; backend fail-closed in prod)'
grep -Fq 'OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID:-}' "$COMPOSE" \
  || fail 'production compose must not ship an OSS access key placeholder default'
grep -Fq 'OSS_ACCESS_KEY_SECRET: ${OSS_ACCESS_KEY_SECRET:-}' "$COMPOSE" \
  || fail 'production compose must not ship an OSS access secret placeholder default'
grep -Fq 'OSS_BUCKET_NAME: ${OSS_BUCKET_NAME:-}' "$COMPOSE" \
  || fail 'production compose must not ship an OSS bucket placeholder default'
! grep -Eq 'your-(key|secret|bucket)' "$COMPOSE" \
  || fail 'production compose must not contain OSS placeholder defaults'
grep -Fq '"127.0.0.1:3306:3306"' "$COMPOSE" \
  || fail 'production MySQL port must bind to loopback only'
grep -Fq '"127.0.0.1:8080:8080"' "$COMPOSE" \
  || fail 'production backend port must bind to loopback only'

echo 'PASS: production config requires explicit secrets and loopback-only internal ports.'

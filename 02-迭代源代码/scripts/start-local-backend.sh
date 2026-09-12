#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: JWT_SECRET is required; inject a local-only value without writing it to the repository." >&2
  exit 2
fi

export STORAGE_TYPE="${STORAGE_TYPE:-local}"
export UPLOAD_DIR="${UPLOAD_DIR:-$ROOT/aigicrm-backend/uploads}"
# QA mode opts into Flyway only for an explicitly isolated local database.
if [ "${LOCAL_QA_MODE:-0}" = "1" ]; then
  export FLYWAY_ENABLED="${FLYWAY_ENABLED:-true}"
  export DB_HOST="${DB_HOST:-127.0.0.1}"
  export DB_PORT="${DB_PORT:-3307}"
  export DB_NAME="${DB_NAME:-aigicrm}"
  export DB_USER="${DB_USER:-root}"
  export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  export REDIS_PORT="${REDIS_PORT:-6379}"
fi
mkdir -p "$UPLOAD_DIR"

echo "Starting local backend with STORAGE_TYPE=$STORAGE_TYPE and UPLOAD_DIR=$UPLOAD_DIR"
cd "$ROOT/aigicrm-backend"
exec mvn -q spring-boot:run

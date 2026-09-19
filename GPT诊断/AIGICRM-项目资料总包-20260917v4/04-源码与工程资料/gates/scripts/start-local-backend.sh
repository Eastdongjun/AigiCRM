#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: JWT_SECRET is required; inject a local-only value without writing it to the repository." >&2
  exit 2
fi

export STORAGE_TYPE="${STORAGE_TYPE:-local}"
export UPLOAD_DIR="${UPLOAD_DIR:-$ROOT/aigicrm-backend/uploads}"

# Opt-in helper for the local Docker services used by this workspace.
# Reads only the local container's generated password and never prints it.
if [ "${LOCAL_DOCKER_MODE:-0}" = "1" ]; then
  command -v docker >/dev/null 2>&1 || {
    echo "ERROR: docker is required for LOCAL_DOCKER_MODE=1." >&2
    exit 2
  }
  docker inspect aigicrm-local-mysql >/dev/null 2>&1 || {
    echo "ERROR: LOCAL_DOCKER_MODE=1 requires running container aigicrm-local-mysql." >&2
    exit 2
  }
  docker inspect aigicrm-local-redis >/dev/null 2>&1 || {
    echo "ERROR: LOCAL_DOCKER_MODE=1 requires running container aigicrm-local-redis." >&2
    exit 2
  }
  export DB_PASSWORD="${DB_PASSWORD:-$(docker exec aigicrm-local-mysql printenv MYSQL_ROOT_PASSWORD)}"
  [ -n "$DB_PASSWORD" ] || {
    echo "ERROR: aigicrm-local-mysql does not expose MYSQL_ROOT_PASSWORD." >&2
    exit 2
  }
  export DB_HOST="${DB_HOST:-127.0.0.1}"
  export DB_PORT="${DB_PORT:-3306}"
  export DB_NAME="${DB_NAME:-aigicrm}"
  export DB_USER="${DB_USER:-root}"
  export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  export REDIS_PORT="${REDIS_PORT:-6380}"
  export FLYWAY_ENABLED="${FLYWAY_ENABLED:-false}"
fi

# QA mode is only for an explicitly isolated local database.
if [ "${LOCAL_QA_MODE:-0}" = "1" ]; then
  export LOCAL_QA_MIGRATE="${LOCAL_QA_MIGRATE:-true}"
  export FLYWAY_ENABLED="${FLYWAY_ENABLED:-false}"
  export DB_HOST="${DB_HOST:-127.0.0.1}"
  export DB_PORT="${DB_PORT:-3307}"
  export DB_NAME="${DB_NAME:-aigicrm}"
  export DB_USER="${DB_USER:-root}"
  export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  export REDIS_PORT="${REDIS_PORT:-6379}"
fi
mkdir -p "$UPLOAD_DIR"

if [ "${LOCAL_QA_MODE:-0}" = "1" ] && [ "${LOCAL_QA_MIGRATE:-true}" = "true" ]; then
  if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_PASSWORD is required for LOCAL_QA_MODE migration." >&2
    exit 2
  fi
  echo "Migrating isolated local QA database $DB_HOST:$DB_PORT/$DB_NAME before backend startup"
  (
    cd "$ROOT/aigicrm-backend"
    mvn -q flyway:migrate \
      -Dflyway.url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true" \
      -Dflyway.user="$DB_USER" \
      -Dflyway.password="$DB_PASSWORD" \
      -Dflyway.locations="filesystem:src/main/resources/db/migration" \
      -Dflyway.baselineOnMigrate=true \
      -Dflyway.validateMigrationNaming=true
  )
fi

echo "Starting local backend with STORAGE_TYPE=$STORAGE_TYPE and UPLOAD_DIR=$UPLOAD_DIR"
cd "$ROOT/aigicrm-backend"
exec mvn -q spring-boot:run

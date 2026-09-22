#!/usr/bin/env bash
# Clean Room 迁移: 与生产 deploy-db.sh 同构 —— flyway/flyway:9.22.3 容器直连 cleanroom-mysql 全量 migrate。
# 应用本身不跑迁移(部署规范: 应用账号无 DDL 权限, spring.flyway.enabled=false 是常态),
# 顺序必须是: up mysql → 本脚本 → up backend(否则租户白名单自检 fail-closed 拒启,属设计哨兵)。
# flyway 凭据由 .env 的 FLYWAY_* 官方变量提供(已 gitignore),经 -e 透传进容器,源码零明文。
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a   # -e KEY 只透传已导出变量,.env 变量须全量导出
MIG_DIR="$(cd ../../aigicrm-backend/src/main/resources/db/migration && pwd)"
NET=$(docker inspect cleanroom-mysql --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
[ -n "$NET" ] || { echo "cleanroom-mysql 不在运行?先 up mysql"; exit 1; }
[ -n "${FLYWAY_PASSWORD:-}" ] || { echo ".env 缺 FLYWAY_*(先跑 gen-cleanroom-env.sh)"; exit 1; }

docker run --rm --network "$NET" \
  -v "$MIG_DIR:/flyway/sql:ro" \
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
  flyway/flyway:9.22.3 \
  -connectRetries=10 -validateMigrationNaming=true \
  migrate

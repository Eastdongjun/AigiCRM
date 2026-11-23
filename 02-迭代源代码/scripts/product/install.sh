#!/usr/bin/env bash
# B1 最小交付安装：mysql + redis + backend（前端 S6.1c 再接入）。
# 安全默认：不加 --apply 只做预检与 dry-run；--apply 才创建容器/迁移数据库。
# 不 SSH、不部署生产、不删除已有数据卷；失败默认 down 保留卷。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE="$ROOT/scripts/product/docker-compose.yml"
PROJECT="aigicrm-product"
RUNTIME_ROOT="$ROOT/scripts/product/runtime"
HTTP_PORT=18080
WEB_PORT=18082
MYSQL_PORT=13306
REDIS_PORT=16379
FRONTEND=1
SEED_DEMO=0
MONITORING=0
BACKUP=1
SKIP_SMOKE=0
NODE_EXPORTER_PORT=19100
PROMETHEUS_PORT=19090
APPLY=0
DRY_ONLY=0
DOWN=0
BUILD=0
ENV_FILE=""
JAR_PATH="$ROOT/aigicrm-backend/target/aigicrm-backend-1.0.0.jar"
FAIL=0
APPLIED=0

usage() {
  cat <<'USAGE'
用法: bash scripts/product/install.sh [选项]

默认 dry-run（只预检/打印计划）。真正安装需显式 --apply。

选项:
  --apply             创建/启动容器、执行 Flyway、种子、健康检查
  --dry-run           仅预检并打印计划（默认）
  --env-file PATH     使用现有 env（必须含 DB_PASSWORD/APP_DB_USER/APP_DB_PASSWORD/
                      JWT_SECRET/TENANT_ENABLE=true/ADMIN_USER/ADMIN_PASSWORD）
  --project NAME      Compose 项目名（默认 aigicrm-product）
  --http-port N       后端宿主机端口（默认 18080）
  --web-port N        前端宿主机端口（默认 18082）
  --no-frontend       只装后端 API（跳过前端构建/冒烟）
  --seed-demo         导入 3 条演示客户（仅空库/无同名演示客户时）
  --monitoring        启动 node-exporter + Prometheus（可选 S6.4）
  --skip-smoke        跳过登录冒烟（升级时 admin 口令已改；health 仍校验）
  --no-backup         迁移前不备份（默认备份已有库；不推荐）
  --mysql-port N      MySQL 宿主机端口（默认 13306）
  --redis-port N      Redis 宿主机端口（默认 16379）
  --jar PATH          后端 jar 路径（默认 target/aigicrm-backend-1.0.0.jar）
  --build             先 mvn -o -DskipTests package
  --down              停止并删除本次 compose 容器（保留数据卷），然后退出
  -h, --help          显示本帮助
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=1; shift ;;
    --dry-run) DRY_ONLY=1; shift ;;
    --env-file) [ $# -ge 2 ] || { echo "--env-file 缺路径" >&2; exit 2; }; ENV_FILE="$2"; shift 2 ;;
    --project) [ $# -ge 2 ] || { echo "--project 缺值" >&2; exit 2; }; PROJECT="$2"; shift 2 ;;
    --http-port) [ $# -ge 2 ] || { echo "--http-port 缺值" >&2; exit 2; }; HTTP_PORT="$2"; shift 2 ;;
    --web-port) [ $# -ge 2 ] || { echo "--web-port 缺值" >&2; exit 2; }; WEB_PORT="$2"; shift 2 ;;
    --no-frontend) FRONTEND=0; shift ;;
    --seed-demo) SEED_DEMO=1; shift ;;
    --monitoring) MONITORING=1; shift ;;
    --skip-smoke) SKIP_SMOKE=1; shift ;;
    --no-backup) BACKUP=0; shift ;;
    --mysql-port) [ $# -ge 2 ] || { echo "--mysql-port 缺值" >&2; exit 2; }; MYSQL_PORT="$2"; shift 2 ;;
    --redis-port) [ $# -ge 2 ] || { echo "--redis-port 缺值" >&2; exit 2; }; REDIS_PORT="$2"; shift 2 ;;
    --jar) [ $# -ge 2 ] || { echo "--jar 缺路径" >&2; exit 2; }; JAR_PATH="$2"; shift 2 ;;
    --build) BUILD=1; shift ;;
    --down) DOWN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

RUNTIME_DIR="$RUNTIME_ROOT/$PROJECT"
MONITORING_DIR="$RUNTIME_DIR/monitoring"
if [ -z "$ENV_FILE" ]; then
  ENV_FILE="$RUNTIME_DIR/.env"
fi
export RUNTIME_DIR MONITORING_DIR JAR_PATH HTTP_PORT WEB_PORT MYSQL_PORT REDIS_PORT NODE_EXPORTER_PORT PROMETHEUS_PORT

dc() {
  docker compose -p "$PROJECT" -f "$COMPOSE" --env-file "$ENV_FILE" "$@"
}

die() {
  echo "FAIL: $*" >&2
  FAIL=1
  exit 1
}

gen_env() {
  mkdir -p "$RUNTIME_DIR/build"
  command -v openssl >/dev/null 2>&1 || die "生成 env 需要 openssl"
  command -v python3 >/dev/null 2>&1 || die "生成 admin 种子需要 python3"
  python3 -c 'import bcrypt' >/dev/null 2>&1 || die "生成 admin 种子需要 python3 bcrypt（pip install bcrypt）"
  local db_pw app_pw jwt admin_pw admin_hash
  db_pw="$(openssl rand -hex 16)"
  app_pw="$(openssl rand -hex 16)"
  jwt="$(openssl rand -hex 32)"
  admin_pw="AIG-$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)"
  admin_hash="$(ADMIN_PW="$admin_pw" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["ADMIN_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')"
  cat > "$ENV_FILE" <<ENVEOF
# 由 scripts/product/install.sh 生成，勿提交。重新生成=全部凭据轮换。
DB_NAME=aigicrm
DB_PASSWORD=$db_pw
APP_DB_USER=aigicrm_app
APP_DB_PASSWORD=$app_pw
JWT_SECRET=$jwt
TENANT_ENABLE=true
ADMIN_USER=admin
ADMIN_PASSWORD=$admin_pw
# 端口由命令行/默认值控制，不写入 env，避免旧值覆盖新 CLI
ENVEOF
  chmod 600 "$ENV_FILE"
  cat > "$RUNTIME_DIR/build/01-app-user.sql" <<SQLEOF
CREATE USER IF NOT EXISTS '${APP_DB_USER:-aigicrm_app}'@'%' IDENTIFIED BY '$app_pw';
GRANT ALL PRIVILEGES ON aigicrm.* TO '${APP_DB_USER:-aigicrm_app}'@'%';
FLUSH PRIVILEGES;
SQLEOF
  cat > "$RUNTIME_DIR/build/02-seed-org.sql" <<SQLEOF
INSERT INTO sys_group (id, group_name, group_code, description, sort_order, status)
SELECT 1, '默认集团', 'default', 'B1 初始集团', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_group WHERE id=1);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_type, sort_order, status, group_id)
SELECT 1, 0, '总部', 1, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE id=1);
INSERT INTO sys_user (id, username, password, real_name, org_id, dept_id, status, pwd_reset_required)
SELECT 1, 'admin', '${admin_hash}', '系统管理员', 1, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin');
INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, r.id FROM sys_role r
WHERE r.role_code='admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=1 AND ur.role_id=r.id)
LIMIT 1;
SQLEOF
  echo "已生成随机 env 与种子: $RUNTIME_DIR"
}

load_env() {
  [ -r "$ENV_FILE" ] || die "env 文件不存在: $ENV_FILE"
  # CLI 显式端口优先于 env 文件（旧 env 可能保留上次端口）
  local cli_http="$HTTP_PORT" cli_web="$WEB_PORT" cli_mysql="$MYSQL_PORT" cli_redis="$REDIS_PORT"
  local cli_node="$NODE_EXPORTER_PORT" cli_prom="$PROMETHEUS_PORT"
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  HTTP_PORT="$cli_http" WEB_PORT="$cli_web" MYSQL_PORT="$cli_mysql" REDIS_PORT="$cli_redis"
  NODE_EXPORTER_PORT="$cli_node" PROMETHEUS_PORT="$cli_prom"
  export HTTP_PORT WEB_PORT MYSQL_PORT REDIS_PORT NODE_EXPORTER_PORT PROMETHEUS_PORT
  RUNTIME_DIR="$RUNTIME_DIR" JAR_PATH="$JAR_PATH"
}

validate_env() {
  local missing=0 k
  for k in DB_PASSWORD APP_DB_USER APP_DB_PASSWORD JWT_SECRET ADMIN_USER ADMIN_PASSWORD; do
    if [ -z "${!k:-}" ]; then echo "FAIL: env 缺 $k" >&2; missing=1; fi
  done
  [ "$missing" = "0" ] || exit 1
  [ "${TENANT_ENABLE:-}" = "true" ] || { echo "FAIL: TENANT_ENABLE 必须为 true" >&2; exit 1; }
  [ "${#JWT_SECRET}" -ge 32 ] || { echo "FAIL: JWT_SECRET 长度不足 32" >&2; exit 1; }
  mkdir -p "$RUNTIME_DIR/build"
  if [ ! -f "$RUNTIME_DIR/build/01-app-user.sql" ] || [ ! -f "$RUNTIME_DIR/build/02-seed-org.sql" ]; then
    echo "FAIL: 缺 runtime 种子（自定义 env 时需先准备 $RUNTIME_DIR/build/01-app-user.sql 与 02-seed-org.sql）" >&2
    exit 1
  fi
}

preflight() {
  echo "== 安装预检 =="
  local skip_ports=""
  if dc ps -q 2>/dev/null | grep -q .; then skip_ports="--skip-ports"; fi
  # shellcheck disable=SC2086
  local ports="$HTTP_PORT,$MYSQL_PORT,$REDIS_PORT"
  [ "$FRONTEND" = "1" ] && ports="$ports,$WEB_PORT"
  [ "$MONITORING" = "1" ] && ports="$ports,$NODE_EXPORTER_PORT,$PROMETHEUS_PORT"
  bash "$ROOT/scripts/product/preflight.sh" --env-file "$ENV_FILE"     --ports "$ports" $skip_ports || die "预检未通过"
}

build_jar() {
  if [ "$BUILD" = "1" ]; then
    echo "== 构建后端 jar =="
    (cd "$ROOT/aigicrm-backend" && ./mvnw -o -DskipTests package) || die "jar 构建失败"
  fi
  [ -r "$JAR_PATH" ] || die "jar 不存在: $JAR_PATH（加 --build 或先构建）"
  if [ "$APPLY" = "1" ] && [ "$BUILD" = "0" ]; then
    if find "$ROOT/aigicrm-backend/src/main" -newer "$JAR_PATH" -print -quit 2>/dev/null | grep -q .; then
      die "jar 比后端源码旧（可能存在未编译改动）：加 --build 或先重新 package"
    fi
  fi
}

wait_mysql() {
  local i
  for i in $(seq 1 60); do
    if dc exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"$DB_PASSWORD" >/dev/null 2>&1; then
      echo "MySQL ready"
      return 0
    fi
    sleep 2
  done
  die "MySQL 60 次探测仍未 ready"
}

backup_if_needed() {
  [ "$BACKUP" = "1" ] || { echo "SKIP: --no-backup"; return 0; }
  local tables
  tables="$(dc exec -T mysql mysql -uroot -p"$DB_PASSWORD" -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME:-aigicrm}'" 2>/dev/null | tr -d '[:space:]')"
  if [ -z "$tables" ] || [ "$tables" = "0" ]; then
    echo "备份: 空库，跳过"
    return 0
  fi
  local dir="$RUNTIME_DIR/backups" file
  mkdir -p "$dir"
  file="$dir/${PROJECT}-pre-migrate-$(date +%Y%m%d-%H%M%S).sql"
  echo "== 迁移前备份: $file =="
  dc exec -T mysql mysqldump -uroot -p"$DB_PASSWORD" --single-transaction --routines --triggers \
    "${DB_NAME:-aigicrm}" > "$file" 2>/dev/null || die "备份失败，已中止（未迁移）"
  [ -s "$file" ] || die "备份文件为空，已中止（未迁移）"
  chmod 600 "$file"
  echo "备份完成: $(wc -c < "$file" | tr -d ' ') bytes"
}

migrate() {
  local net mig
  net="$(docker network ls --format '{{.Name}}' | grep -E "(^|_)${PROJECT}_default$" | head -1)"
  [ -n "$net" ] || die "找不到 compose 网络 ${PROJECT}_default"
  mig="$ROOT/aigicrm-backend/src/main/resources/db/migration"
  [ -d "$mig" ] || die "迁移目录不存在: $mig"
  echo "== Flyway migrate（${net}）=="
  local flyway_image="${FLYWAY_IMAGE:-flyway/flyway:9.22.3}"
  case "$(uname -m)" in
    arm64|aarch64)
      if [ -z "${FLYWAY_IMAGE:-}" ]; then flyway_image="flyway/flyway:9.22.3-alpine"; fi ;;
  esac
  docker run --rm --network "$net" -v "$mig:/flyway/sql:ro" \
    -e FLYWAY_URL="jdbc:mysql://mysql:3306/${DB_NAME:-aigicrm}?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true" \
    -e FLYWAY_USER=root -e FLYWAY_PASSWORD="$DB_PASSWORD" \
    "$flyway_image" -connectRetries=10 -validateMigrationNaming=true \
    -ignoreMigrationPatterns='*:missing' -baselineOnMigrate=true migrate || die "Flyway migrate 失败"
}

seed() {
  echo "== 组织/admin 种子 =="
  dc exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$DB_PASSWORD" "${DB_NAME:-aigicrm}" < "$RUNTIME_DIR/build/02-seed-org.sql" \
    || die "种子失败"
}

seed_demo() {
  [ "$SEED_DEMO" = "1" ] || return 0
  echo "== 演示客户种子（显式 --seed-demo）=="
  dc exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$DB_PASSWORD" "${DB_NAME:-aigicrm}" < "$ROOT/scripts/product/seed-demo.sql" \
    || die "演示数据种子失败"
}

wait_health() {
  local i body
  for i in $(seq 1 90); do
    body="$(curl -fsS -m 5 "http://127.0.0.1:$HTTP_PORT/actuator/health" 2>/dev/null || true)"
    if echo "$body" | grep -q '"status":"UP"'; then echo "Health UP"; return 0; fi
    sleep 2
  done
  die "健康检查未达 UP（最后响应: ${body:-empty}）"
}

smoke() {
  local login token info
  echo "== 登录冒烟 =="
  login="$(curl -fsS -m 10 -X POST "http://127.0.0.1:$HTTP_PORT/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASSWORD\"}" 2>/dev/null)" || die "登录请求失败"
  token="$(echo "$login" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("token") or "")' 2>/dev/null)"
  [ -n "$token" ] || die "登录未返回 token（检查 admin 种子/密码）"
  reset="$(echo "$login" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("pwdResetRequired") or 0)' 2>/dev/null)"
  if [ "$reset" = "1" ]; then
    echo "初始口令登录成功（pwdResetRequired=1，首登将强制改密）"
    return 0
  fi
  info="$(curl -fsS -m 10 "http://127.0.0.1:$HTTP_PORT/api/auth/info" -H "Authorization: Bearer $token" 2>/dev/null)" || die "auth/info 失败"
  echo "$info" | grep -q '"username":"admin"' || die "auth/info 未返回 admin"
  echo "登录冒烟通过（token 长度 ${#token}）"
}

wait_frontend() {
  local i body
  for i in $(seq 1 120); do
    body="$(curl -fsS -m 5 "http://127.0.0.1:$WEB_PORT/" 2>/dev/null || true)"
    if echo "$body" | grep -q '<div id="app">'; then echo "Frontend ready"; return 0; fi
    sleep 2
  done
  die "前端 120 次探测仍未就绪"
}

smoke_frontend() {
  local login token info
  echo "== 前端代理冒烟 =="
  curl -fsS -m 10 "http://127.0.0.1:$WEB_PORT/" >/dev/null || die "前端首页不可访问"
  login="$(curl -fsS -m 10 -X POST "http://127.0.0.1:$WEB_PORT/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASSWORD\"}" 2>/dev/null)" || die "经前端登录请求失败"
  token="$(echo "$login" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("token") or "")' 2>/dev/null)"
  [ -n "$token" ] || die "经前端登录未返回 token"
  reset="$(echo "$login" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("pwdResetRequired") or 0)' 2>/dev/null)"
  if [ "$reset" = "1" ]; then
    echo "前端代理登录冒烟通过（pwdResetRequired=1）"
    return 0
  fi
  info="$(curl -fsS -m 10 "http://127.0.0.1:$WEB_PORT/api/auth/info" -H "Authorization: Bearer $token" 2>/dev/null)" || die "经前端 auth/info 失败"
  echo "$info" | grep -q '"username":"admin"' || die "经前端 auth/info 未返回 admin"
  echo "前端代理登录冒烟通过"
}

start_monitoring() {
  [ "$MONITORING" = "1" ] || return 0
  echo "== 监控（S6.4 最小）：node-exporter + Prometheus =="
  mkdir -p "$MONITORING_DIR/textfile"
  bash "$ROOT/scripts/product/monitoring/probe.sh"     --api-url "http://127.0.0.1:$HTTP_PORT" --web-url "http://127.0.0.1:$WEB_PORT"     --out "$MONITORING_DIR/textfile/aigicrm.prom" || die "探针首次采集失败"
  dc --profile monitoring up -d node-exporter prometheus || die "监控容器启动失败"
  local i body
  for i in $(seq 1 30); do
    body="$(curl -fsS -m 5 "http://127.0.0.1:$PROMETHEUS_PORT/api/v1/query?query=aigicrm_api_up" 2>/dev/null || true)"
    if echo "$body" | grep -q '"value":\[.*"1"'; then
      echo "Prometheus 已抓到 aigicrm_api_up=1"
      return 0
    fi
    sleep 2
  done
  die "Prometheus 30 次查询仍未抓到 aigicrm_api_up=1（最后响应: ${body:-empty}）"
}

cleanup() {
  if [ "$APPLIED" = "1" ] && [ "$FAIL" != "0" ]; then
    echo "== 失败回滚：停止本次容器（保留数据卷）=="
    dc down --remove-orphans || true
  fi
}
trap cleanup EXIT

echo "== AIGICRM B1 最小安装 =="
echo "project=$PROJECT runtime=$RUNTIME_DIR"
if [ -z "${ENV_FILE:-}" ] || [ ! -r "$ENV_FILE" ]; then gen_env; fi
load_env
# 浏览器经 nginx 同源访问 /api 仍会带 Origin；默认按实际 Web 端口放行本机来源
if [ -z "${CORS_ORIGINS:-}" ]; then
  CORS_ORIGINS="http://localhost:$WEB_PORT,http://127.0.0.1:$WEB_PORT"
fi
export CORS_ORIGINS
validate_env

if [ "$DOWN" = "1" ]; then
  echo "== down（保留卷）=="
  dc down --remove-orphans
  exit 0
fi

preflight

if [ "$APPLY" = "0" ]; then
  echo "DRY-RUN：未创建容器。将执行："
  echo "  1) preflight"
  echo "  2) docker compose up -d mysql redis"
  echo "  3) 迁移前备份（已有库）"
  echo "  4) Flyway migrate"
  echo "  5) 组织/admin 种子"
  echo "  6) docker compose up -d backend"
  echo "  7) health + admin 登录冒烟"
  if [ "$SEED_DEMO" = "1" ]; then echo "  * 演示客户种子（--seed-demo）"; fi
  if [ "$MONITORING" = "1" ]; then echo "  * 监控：node-exporter + Prometheus + 首次探针"; fi
  if [ "$FRONTEND" = "1" ]; then
    echo "  8) docker compose up -d --build frontend"
    echo "  9) 前端首页 + 经 /api 代理登录冒烟"
  fi
  echo "  数据卷保留；失败 down 保留卷。确认后加 --apply。"
  exit 0
fi

build_jar

APPLIED=1
dc up -d mysql redis || die "mysql/redis 启动失败"
wait_mysql
backup_if_needed
migrate
seed
seed_demo
dc up -d --force-recreate backend || die "backend 启动失败"
wait_health
if [ "$SKIP_SMOKE" = "1" ]; then
  echo "SKIP: 登录冒烟（--skip-smoke；health 已通过）"
else
  smoke
fi
if [ "$FRONTEND" = "1" ]; then
  dc up -d --build frontend || die "frontend 启动失败"
  wait_frontend
  if [ "$SKIP_SMOKE" = "1" ]; then echo "SKIP: 前端代理登录冒烟"; else smoke_frontend; fi
fi
start_monitoring

echo
echo "PASS: 安装完成"
echo "  API: http://127.0.0.1:$HTTP_PORT"
if [ "$FRONTEND" = "1" ]; then echo "  Web: http://127.0.0.1:$WEB_PORT"; fi
if [ "$MONITORING" = "1" ]; then
  echo "  Prometheus: http://127.0.0.1:$PROMETHEUS_PORT"
  echo "  node-exporter: http://127.0.0.1:$NODE_EXPORTER_PORT"
fi
echo "  admin: ${ADMIN_USER}（密码在 ${ENV_FILE}，不要提交）"
echo "  停止: bash $ROOT/scripts/product/install.sh --project $PROJECT --env-file $ENV_FILE --down"
APPLIED=0

#!/usr/bin/env bash
# B1 本地/云主机验收一键跑：安装 -> 浏览器首启 E2E -> DB/监控断言 -> 清理。
# 用法:
#   bash scripts/product/acceptance.sh
#   bash scripts/product/acceptance.sh --project b1-acc --http-port 18110 --web-port 18111 \
#     --mysql-port 13320 --redis-port 16392 --report /tmp/b1-acceptance.json
# 需要 Docker、Playwright Chromium、干净端口；默认跑完 down -v，--keep 保留现场。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROJECT="b1-acceptance"
HTTP_PORT=18110
WEB_PORT=18111
MYSQL_PORT=13320
REDIS_PORT=16392
KEEP=0
REPORT=""
NEW_PASSWORD="Acc-$(date +%s)-Pwd1"

while [ $# -gt 0 ]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --http-port) HTTP_PORT="$2"; shift 2 ;;
    --web-port) WEB_PORT="$2"; shift 2 ;;
    --mysql-port) MYSQL_PORT="$2"; shift 2 ;;
    --redis-port) REDIS_PORT="$2"; shift 2 ;;
    --report) REPORT="$2"; shift 2 ;;
    --keep) KEEP=1; shift ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

ENV_FILE="$ROOT/scripts/product/runtime/$PROJECT/.env"
LOG="$(mktemp "${TMPDIR:-/tmp}/aigicrm-acceptance.XXXXXX")"
trap 'rm -f "$LOG"' EXIT
FAIL=0
pass(){ echo "PASS: $1"; }
fail(){ echo "FAIL: $1" >&2; FAIL=$((FAIL+1)); }

set -a
# shellcheck disable=SC1090
[ -r "$ENV_FILE" ] && . "$ENV_FILE"
set +a

cleanup() {
  if [ "$KEEP" = "0" ]; then
    echo "== 清理测试栈（含卷）=="
    MONITORING_DIR="$ROOT/scripts/product/runtime/$PROJECT/monitoring" \
    RUNTIME_DIR="$ROOT/scripts/product/runtime/$PROJECT" \
    JAR_PATH="$ROOT/aigicrm-backend/target/aigicrm-backend-1.0.0.jar" \
    HTTP_PORT="$HTTP_PORT" WEB_PORT="$WEB_PORT" MYSQL_PORT="$MYSQL_PORT" REDIS_PORT="$REDIS_PORT" \
    NODE_EXPORTER_PORT=19100 PROMETHEUS_PORT=19090 \
    docker compose --profile monitoring -p "$PROJECT" -f "$ROOT/scripts/product/docker-compose.yml" \
      --env-file "$ENV_FILE" down -v >/dev/null 2>&1 || true
  else
    echo "KEEP: 现场保留，停止: bash $ROOT/scripts/product/install.sh --project $PROJECT --down"
  fi
}
trap cleanup EXIT

echo "== B1 验收: $PROJECT ($HTTP_PORT/$WEB_PORT/$MYSQL_PORT/$REDIS_PORT) =="
if [ "$KEEP" = "1" ]; then echo "（--keep：结束保留现场）"; fi

bash "$ROOT/scripts/product/install.sh" --apply --build --monitoring \
  --project "$PROJECT" --http-port "$HTTP_PORT" --web-port "$WEB_PORT" \
  --mysql-port "$MYSQL_PORT" --redis-port "$REDIS_PORT" >"$LOG" 2>&1
if [ $? -ne 0 ]; then
  tail -40 "$LOG" >&2
  fail "安装失败（日志 ${LOG}）"
  exit 1
fi
pass "安装（Flyway/种子/health/前端/监控）"

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

# 浏览器首启：改密 -> 重登 -> 向导 -> 演示种子 -> 工作台
(
  cd "$ROOT/aigicrm-ui"
  E2E_BASE_URL="http://127.0.0.1:$WEB_PORT" \
  E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  E2E_NEW_PASSWORD="$NEW_PASSWORD" \
  node e2e/onboarding.mjs
) >"$LOG" 2>&1
if [ $? -eq 0 ]; then pass "浏览器 E2E（改密/向导/演示种子/工作台）"; else tail -30 "$LOG" >&2; fail "浏览器 E2E"; fi

BASE="http://127.0.0.1:$WEB_PORT"
LOGIN="$(curl -s -m 10 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$NEW_PASSWORD\"}")"
TOKEN="$(echo "$LOGIN" | python3 -c 'import sys,json; print((json.load(sys.stdin).get("data") or {}).get("token") or "")' 2>/dev/null)"
RESET="$(echo "$LOGIN" | python3 -c 'import sys,json; print((json.load(sys.stdin).get("data") or {}).get("pwdResetRequired"))' 2>/dev/null)"
[ -n "$TOKEN" ] && [ "$RESET" = "0" ] && pass "改密后重登 reset=0" || fail "改密后重登"

PUB="$(curl -s -m 5 "$BASE/api/system/configs/public")"
echo "$PUB" | grep -q '"onboarding.completed":"true"' && pass "public onboarding.completed=true" || fail "public 完成标记"

MYSQL_C="$(docker ps --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Names}}' | grep -m1 mysql || true)"
if [ -n "$MYSQL_C" ]; then
  Q="$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_C" mysql -uroot -N -B -e \
    "SELECT COUNT(*) FROM ${DB_NAME:-aigicrm}.flyway_schema_history WHERE success=1; \
     SELECT COUNT(*) FROM ${DB_NAME:-aigicrm}.sys_dict_data d JOIN ${DB_NAME:-aigicrm}.sys_dict_type t ON d.dict_type_id=t.id WHERE t.dict_code='industry'; \
     SELECT COUNT(*) FROM ${DB_NAME:-aigicrm}.biz_customer WHERE customer_code LIKE 'DEMO%';" 2>/dev/null | tr '\n' ' ')"
  set -- $Q
  [ "${1:-}" = "165" ] && pass "Flyway 165" || fail "Flyway=${1:-?}"
  [ "${2:-}" = "10" ] && pass "industry 字典 10" || fail "industry=${2:-?}"
  [ "${3:-}" = "3" ] && pass "演示客户 3" || fail "demo=${3:-?}"
else
  fail "找不到 mysql 容器"
fi

if [ "$(curl -s -m 5 'http://127.0.0.1:19090/api/v1/query?query=aigicrm_api_up' | grep -c '"value":\[.*"1"')" -ge 1 ]; then
  pass "Prometheus api_up=1"
else
  fail "Prometheus api_up"
fi
if curl -s -m 5 http://127.0.0.1:19090/api/v1/rules | grep -q 'AigicrmApiDown'; then pass "告警规则加载"; else fail "告警规则"; fi

echo
echo "===== B1 验收汇总: FAIL=$FAIL ====="
if [ -n "$REPORT" ]; then
  python3 - "$REPORT" "$FAIL" "$PROJECT" <<'PY'
import json,sys,datetime
path,fails,project=sys.argv[1],int(sys.argv[2]),sys.argv[3]
json.dump({"project":project,"result":"PASS" if fails==0 else "FAIL","failures":fails,
           "checked_at":datetime.datetime.now().isoformat(timespec="seconds")},
          open(path,"w"),ensure_ascii=False,indent=2)
PY
  echo "report: $REPORT"
fi
[ "$FAIL" -eq 0 ]

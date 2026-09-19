#!/bin/bash
# AIGICRM 核心链路冒烟测试: 生产/测试环境自动化断言
# 用法: ./smoke-test.sh [prod|test|local]
# 覆盖: 登录 / 核心API / 权限隔离 / 页面可用性
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

ENV="${1:-prod}"

PASS=0; FAIL=0
ok()  { echo "✅ $1"; PASS=$((PASS+1)); }
bad() { echo "❌ $1"; FAIL=$((FAIL+1)); }

if [ "$ENV" = "local" ]; then
  BASE="${AIGICRM_BACKEND_URL:-http://127.0.0.1:8080}"
  FRONTEND_BASE="${AIGICRM_FRONTEND_URL:-}"
  LABEL="本地预检"

  echo "════════ AIGICRM 冒烟测试 ($LABEL) $(date '+%H:%M:%S') ════════"

  DOCS_CODE=$(curl -sk -o /tmp/aigicrm-local-api-docs.json -w "%{http_code}" "$BASE/v3/api-docs" --connect-timeout 8)
  if [ "$DOCS_CODE" = "200" ] && grep -Fq 'sys-user-controller' /tmp/aigicrm-local-api-docs.json \
      && grep -Fq '/api/system/configs/public' /tmp/aigicrm-local-api-docs.json; then
    ok "本地后端身份: AIGICRM OpenAPI"
  else
    bad "本地后端身份: /v3/api-docs HTTP $DOCS_CODE 或缺少 AIGICRM 特征"
  fi

  INFO_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/auth/info" --connect-timeout 8)
  case "$INFO_CODE" in
    401|403) ok "未登录保护边界: /api/auth/info $INFO_CODE" ;;
    *) bad "未登录保护边界: /api/auth/info 返回 $INFO_CODE(应 401/403)" ;;
  esac

  PUBLIC_CODE=$(curl -sk -o /tmp/aigicrm-local-public-config.json -w "%{http_code}" "$BASE/api/system/configs/public" --connect-timeout 8)
  [ "$PUBLIC_CODE" = "200" ] && ok "公开配置接口 200" || bad "公开配置接口 $PUBLIC_CODE"

  LOGO_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/system/configs/logo" --connect-timeout 8)
  [ "$LOGO_CODE" = "200" ] && ok "Logo 匿名端点 200" || bad "Logo 匿名端点 $LOGO_CODE"

  if [ -n "$FRONTEND_BASE" ]; then
    for route in / /login /403; do
      PAGE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$FRONTEND_BASE$route" --connect-timeout 8)
      [ "$PAGE_CODE" = "200" ] && ok "前端$route 200" || bad "前端$route $PAGE_CODE"
    done
  else
    echo "⏭️  前端页面预检跳过(设置 AIGICRM_FRONTEND_URL 可启用)"
  fi

  echo "══════════════════════════════════════════════════"
  echo "结果: PASS=$PASS FAIL=$FAIL"
  [ "$FAIL" -gt 0 ] && { echo "❌ 本地冒烟预检未通过"; exit 1; } || { echo "✅ 本地冒烟预检全部通过"; exit 0; }
fi

source "$SCRIPT_DIR/deploy-lib.sh"

if [ "$ENV" = "prod" ]; then
  BASE="https://www.aigicrm.com"; LABEL="生产"
else
  BASE="http://TEST_SERVER_IP"; LABEL="测试"
fi

echo "════════ AIGICRM 冒烟测试 ($LABEL) $(date '+%H:%M:%S') ════════"

# [1] 登录 admin（凭据经部署 env 的 HEALTH_CHECK_USER/PASS 注入，禁止硬编码）
[ -n "${HEALTH_CHECK_PASS:-}" ] || { echo "❌ 缺少 HEALTH_CHECK_PASS（请在部署 env 配置）"; exit 1; }
TOKEN=$(curl -sk -o /dev/null -w "" -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H "Content-Type: application/json" \
  -d "{\"username\":\"${HEALTH_CHECK_USER:-admin}\",\"password\":\"$HEALTH_CHECK_PASS\"}" >/dev/null 2>&1; curl -sk -X POST "$BASE/api/auth/login" \
  -H "Origin: $BASE" -H "Content-Type: application/json" -d "{\"username\":\"${HEALTH_CHECK_USER:-admin}\",\"password\":\"$HEALTH_CHECK_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
[ -n "$TOKEN" ] && ok "admin 登录成功" || { bad "admin 登录失败"; echo "结果: PASS=$PASS FAIL=$FAIL"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

# [2] 核心 API(全部应 200 + code 200)
for ep in "auth/info" "customer/list?page=1&size=2" "incentive/categories" "file/folders" \
          "reminder/list?page=1&size=2" "approval/list?page=1&size=2" "system/depts" \
          "statistics/dashboard" "system/configs" "system/roles"; do
  RESP=$(curl -sk -X GET "$BASE/api/$ep" -H "$AUTH" --connect-timeout 8)
  CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  if [ "$CODE" = "200" ]; then ok "/api/$ep"; else bad "/api/$ep → code=$CODE"; fi
done

# [2.5] 角色-组织数据范围接口（2026-08-03 新增）
echo "--- [2.5] role-orgs ---"
FIRST_ROLE=$(curl -sk -X GET "$BASE/api/system/roles" -H "$AUTH" --connect-timeout 8 \
  | python3 -c "import sys,json;d=json.load(sys.stdin).get('data',[]);print(d[0]['id'] if d else '')" 2>/dev/null)
if [ -n "$FIRST_ROLE" ]; then
  RO=$(curl -sk -X GET "$BASE/api/system/roles/$FIRST_ROLE/orgs" -H "$AUTH" --connect-timeout 8)
  RCODE=$(echo "$RO" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  [ "$RCODE" = "200" ] && ok "role-orgs GET (role=$FIRST_ROLE)" || bad "role-orgs GET → code=$RCODE"
else
  bad "role-orgs 前置: 无法获取角色列表"
fi

# [3] 权限隔离: 销售员访问 users 应 403(账号密码必须经 SMOKE_TEST_USER/SMOKE_TEST_PASS 注入, 禁止硬编码默认口令)
# 未配置时显式跳过并提示——不要静默失败(默认口令可能过期, 静默跳过会让安全回归漏检)
TEST_USER="${SMOKE_TEST_USER:-}"
TEST_PASS="${SMOKE_TEST_PASS:-}"
WTOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
if [ -n "$WTOKEN" ]; then
  HTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X GET "$BASE/api/system/users?page=1&size=2" \
    -H "Authorization: Bearer $WTOKEN" --connect-timeout 8)
  [ "$HTTP" = "403" ] && ok "权限隔离: $TEST_USER users 403" || bad "权限隔离: $TEST_USER users 返回 $HTTP(应 403)"
else
  echo "⏭️  权限隔离测试跳过(未配置 SMOKE_TEST_USER/SMOKE_TEST_PASS 或登录失败, 生产可用 SMOKE_TEST_USER/PASS 配置)"
fi

# [3.5] 核心业务回归用例(激励/审批/文件/公海)
echo "--- [3.5] 业务回归 ---"
LOGO_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/system/configs/logo" --connect-timeout 8)
[ "$LOGO_CODE" = "200" ] && ok "回归: logo 匿名端点 200" || bad "回归: logo 端点 $LOGO_CODE"
for item in "incentive/categories" "incentive/records?page=1&size=2" "approval/list?page=1&size=2" \
            "file/folders" "reminder/list?page=1&size=2" "sea/statistics"; do
  RESP=$(curl -sk -X GET "$BASE/api/$item" -H "$AUTH" --connect-timeout 8)
  CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  [ "$CODE" = "200" ] && ok "回归: $item" || bad "回归: $item → code=$CODE"
done

# [4] 页面可用性
PAGE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/" --connect-timeout 8)
[ "$PAGE" = "200" ] && ok "首页 $PAGE" || bad "首页 $PAGE"

echo "══════════════════════════════════════════════════"
echo "结果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -gt 0 ] && { echo "❌ 冒烟测试未通过"; exit 1; } || { echo "✅ 冒烟测试全部通过"; exit 0; }

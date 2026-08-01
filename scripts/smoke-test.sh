#!/bin/bash
# YCMN 核心链路冒烟测试: 生产/测试环境自动化断言
# 用法: ./smoke-test.sh [prod|test]
# 覆盖: 登录 / 核心API / 权限隔离 / 页面可用性
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

ENV="${1:-prod}"
if [ "$ENV" = "prod" ]; then
  BASE="https://www.aigicrm.com"; LABEL="生产"
else
  BASE="http://111.229.37.220"; LABEL="测试"
fi

PASS=0; FAIL=0
ok()  { echo "✅ $1"; PASS=$((PASS+1)); }
bad() { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "════════ YCMN 冒烟测试 ($LABEL) $(date '+%H:%M:%S') ════════"

# [1] 登录 admin
TOKEN=$(curl -sk -o /dev/null -w "" -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' >/dev/null 2>&1; curl -sk -X POST "$BASE/api/auth/login" \
  -H "Origin: $BASE" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' \
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

# [3] 权限隔离: 销售员访问 users 应 403(账号密码可用环境变量覆盖)
# 生产环境无通用测试账号时, 通过 SMOKE_TEST_USER/SMOKE_TEST_PASS 配置, 否则跳过并提示
TEST_USER="${SMOKE_TEST_USER:-wangleilei}"
TEST_PASS="${SMOKE_TEST_PASS:-123456}"
WTOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
if [ -n "$WTOKEN" ]; then
  HTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X GET "$BASE/api/system/users?page=1&size=2" \
    -H "Authorization: Bearer $WTOKEN" --connect-timeout 8)
  [ "$HTTP" = "403" ] && ok "权限隔离: $TEST_USER users 403" || bad "权限隔离: $TEST_USER users 返回 $HTTP(应 403)"
else
  echo "⏭️  权限隔离测试跳过(无 $TEST_USER 测试账号, 生产可用 SMOKE_TEST_USER/SMOKE_TEST_PASS 配置)"
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

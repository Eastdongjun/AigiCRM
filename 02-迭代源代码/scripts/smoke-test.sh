#!/bin/bash
# AIGICRM 核心链路冒烟测试: 生产/测试环境自动化断言
# 用法: ./smoke-test.sh [prod|test|local]
# 覆盖: 登录 / 核心API / 法布施只读端点 / 权限隔离 / 页面可用性
#
# 账号(经 ~/.aigicrm-deploy.env 注入, 禁止硬编码; 按目标分开取, 不跨环境回退):
#   生产  管理员 HEALTH_CHECK_USER / HEALTH_CHECK_PASS          销售员 SMOKE_TEST_USER / SMOKE_TEST_PASS
#   测试  管理员 TEST_HEALTH_CHECK_USER / TEST_HEALTH_CHECK_PASS  销售员 TEST_SMOKE_USER / TEST_SMOKE_PASS
#   SMOKE_BASE_URL 可覆盖测试环境地址(生产地址不接受覆盖)
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
  # 生产地址不接受覆盖: 标成"生产"的冒烟只能打到生产
  BASE="https://www.aigicrm.com"; LABEL="生产"
else
  # 【GL-08 2026-11-24】测试环境地址可经 SMOKE_BASE_URL 覆盖: 测试子域名启用 HTTPS 后不必改脚本;
  #   行为测试也用它把脚本指向本机假后端。
  BASE="${SMOKE_BASE_URL:-http://111.229.37.220}"; LABEL="测试"
fi

echo "════════ AIGICRM 冒烟测试 ($LABEL) $(date '+%H:%M:%S') ════════"

# [1] 账号按目标分开取, 不跨环境回退(【GL-08 2026-11-24】)
#   以前 test 目标也读生产的健康检查账号。测试库的管理员口令与生产不同: 登不进去, 还白白给测试环境的账号记失败登录
#   (同一账号从同一 IP 连续失败 5 次会锁 15 分钟)。缺哪一项都在发任何请求之前停。
if [ "$ENV" = "prod" ]; then
  ADMIN_USER="${HEALTH_CHECK_USER:-admin}"; ADMIN_PASS="${HEALTH_CHECK_PASS:-}"
  SALES_USER="${SMOKE_TEST_USER:-}"; SALES_PASS="${SMOKE_TEST_PASS:-}"
  SALES_KEYS="SMOKE_TEST_USER / SMOKE_TEST_PASS"
else
  ADMIN_USER="${TEST_HEALTH_CHECK_USER:-}"; ADMIN_PASS="${TEST_HEALTH_CHECK_PASS:-}"
  SALES_USER="${TEST_SMOKE_USER:-}"; SALES_PASS="${TEST_SMOKE_PASS:-}"
  SALES_KEYS="TEST_SMOKE_USER / TEST_SMOKE_PASS"
fi
[ -n "$ADMIN_USER" ] && [ -n "$ADMIN_PASS" ] || {
  echo "❌ 缺少管理员账号(生产: HEALTH_CHECK_USER / HEALTH_CHECK_PASS; 测试: TEST_HEALTH_CHECK_USER / TEST_HEALTH_CHECK_PASS; 经部署 env 注入)。未发任何请求。"
  exit 1
}

# 登录只发一次。旧写法发了两次(第一次的响应被丢弃): 口令不对时每次运行白烧两次失败额度, 口令对时也多记一次成功登录。
TOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
[ -n "$TOKEN" ] && ok "$ADMIN_USER 登录成功" || {
  bad "$ADMIN_USER 登录失败(账号或口令不对; 同一账号从同一 IP 连续失败 5 次会锁 15 分钟, 排查时别反复跑)"
  echo "结果: PASS=$PASS FAIL=$FAIL"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

# [2] 核心 API(全部应 200 + code 200)
# 【2026-11-07 网络抖动护栏】本机→生产链路偶发单端点空响应(失败项漂移、服务端资源/日志恒干净、
# 手动复测恒过, 与同日 GitHub push 抖动同源): 空响应重试一次, 复测仍败才判 FAIL——
# 真回归不会重试成功, 判定标准不放松; 重试通过会显式标注以便观察抖动频率
for ep in "auth/info" "customer/list?page=1&size=2" "incentive/categories" "file/folders" \
          "reminder/list?page=1&size=2" "approval/list?page=1&size=2" "system/depts" \
          "statistics/dashboard" "system/configs" "system/roles"; do
  RESP=$(curl -sk -X GET "$BASE/api/$ep" -H "$AUTH" --connect-timeout 8)
  CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  if [ "$CODE" != "200" ]; then
    sleep 1
    RESP=$(curl -sk -X GET "$BASE/api/$ep" -H "$AUTH" --connect-timeout 8)
    CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
    if [ "$CODE" = "200" ]; then ok "/api/$ep (抖动重试通过)"; continue; fi
  fi
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

# 【GL-08 2026-11-24】调一个应返回 code 200 的接口。空响应或非 200 时重试一次(规则同 [2] 段), 复测仍败才判失败。
check_api() { # $1=路径(不含 /api/ 前缀)
  local ep="$1" resp code
  resp=$(curl -sk -X GET "$BASE/api/$ep" -H "$AUTH" --connect-timeout 8)
  code=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  if [ "$code" != "200" ]; then
    sleep 1
    resp=$(curl -sk -X GET "$BASE/api/$ep" -H "$AUTH" --connect-timeout 8)
    code=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
    if [ "$code" = "200" ]; then ok "/api/$ep (抖动重试通过)"; return; fi
  fi
  if [ "$code" = "200" ]; then ok "/api/$ep"; else bad "/api/$ep → code=$code"; fi
}

# [2.6] 法布施只读端点(【GL-08 2026-11-24】此前冒烟里没有任何法布施端点)
#   全部是 GET 且不产生数据; 带路径参数的(按书、按帖)不在这里调。
#   末两个是管理侧列表, 分别要书目管理、内容管理权限, 管理员账号应当都有。
echo "--- [2.6] fabushi ---"
for ep in "fabushi/books?current=1&size=2" "fabushi/posts?current=1&size=2" \
          "fabushi/awaken/status" "fabushi/shelf" "fabushi/shelf/groups" "fabushi/records" "fabushi/settings" \
          "fabushi/bookmarks/mine" "fabushi/reads/today" "fabushi/activities" \
          "fabushi/stats/dashboard" "fabushi/stats/monthly" "fabushi/stats/personal" "fabushi/stats/team" \
          "fabushi/books/pending" "fabushi/posts/deleted?current=1&size=2"; do
  check_api "$ep"
done

# [3] 权限隔离: 销售员访问 users 应 403(账号密码必须经部署 env 注入, 禁止硬编码默认口令; 键名见文件头)
# 【GL-08 2026-11-24】未配置 → 明确不通过。以前是打印一行"跳过"然后算整体通过, 而部署配置里一直没配这个账号,
#   这条安全回归因此长期空转。登录失败与越权分开报; 另加一条正向对照(销售员的令牌能读本人信息),
#   否则下面的 403 可能只是令牌坏了。
TEST_USER="$SALES_USER"
TEST_PASS="$SALES_PASS"
if [ -z "$TEST_USER" ] || [ -z "$TEST_PASS" ]; then
  bad "权限隔离: 未配置 $SALES_KEYS(销售员账号, 经部署 env 注入) —— 安全回归不能跳过"
else
  WTOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
  if [ -z "$WTOKEN" ]; then
    bad "权限隔离: $TEST_USER 登录失败(账号、口令或状态有变; 同一账号连续失败 5 次会锁 15 分钟, 排查时别反复试)"
  else
    WINFO_CODE=$(curl -sk -X GET "$BASE/api/auth/info" -H "Authorization: Bearer $WTOKEN" --connect-timeout 8 \
      | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
    [ "$WINFO_CODE" = "200" ] && ok "权限隔离: $TEST_USER 可读本人信息 auth/info(令牌有效)" \
                               || bad "权限隔离: $TEST_USER 读本人信息 auth/info → code=$WINFO_CODE(令牌无效时, 下面的 403 说明不了问题)"
    HTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X GET "$BASE/api/system/users?page=1&size=2" \
      -H "Authorization: Bearer $WTOKEN" --connect-timeout 8)
    [ "$HTTP" = "403" ] && ok "权限隔离: $TEST_USER users 403" || bad "权限隔离: $TEST_USER users 返回 $HTTP(应 403)"
  fi
fi

# [3.5] 核心业务回归用例(激励/审批/文件/公海)
echo "--- [3.5] 业务回归 ---"
LOGO_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/system/configs/logo" --connect-timeout 8)
[ "$LOGO_CODE" = "200" ] && ok "回归: logo 匿名端点 200" || bad "回归: logo 端点 $LOGO_CODE"
for item in "incentive/categories" "incentive/records?page=1&size=2" "approval/list?page=1&size=2" \
            "file/folders" "reminder/list?page=1&size=2" "sea/statistics"; do
  RESP=$(curl -sk -X GET "$BASE/api/$item" -H "$AUTH" --connect-timeout 8)
  CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
  if [ "$CODE" != "200" ]; then
    # 【2026-11-07 网络抖动护栏】同 [2] 段: 空响应重试一次, 复测仍败才判 FAIL
    sleep 1
    RESP=$(curl -sk -X GET "$BASE/api/$item" -H "$AUTH" --connect-timeout 8)
    CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
    [ "$CODE" = "200" ] && { ok "回归: $item (抖动重试通过)"; continue; }
  fi
  [ "$CODE" = "200" ] && ok "回归: $item" || bad "回归: $item → code=$CODE"
done

# [4] 页面可用性
PAGE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/" --connect-timeout 8)
[ "$PAGE" = "200" ] && ok "首页 $PAGE" || bad "首页 $PAGE"

echo "══════════════════════════════════════════════════"
echo "结果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -gt 0 ] && { echo "❌ 冒烟测试未通过"; exit 1; } || { echo "✅ 冒烟测试全部通过"; exit 0; }

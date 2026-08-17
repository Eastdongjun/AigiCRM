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

# [1] 登录 admin（凭据经 ~/.ycmn-deploy.env 的 HEALTH_CHECK_USER/PASS 注入，禁止硬编码）
[ -n "${HEALTH_CHECK_PASS:-}" ] || { echo "❌ 缺少 HEALTH_CHECK_PASS（请在 ~/.ycmn-deploy.env 配置）"; exit 1; }
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

# [3.6] 改密回归三场景(2026-08-17 生产实测修复防回归):
#   A=管理员重置后清除登录锁定(输错5次→锁定→重置→新密码立即登录成功)
#   B=改密后旧 token 立即失效(403) + 账号状态恢复
#   C=忘记密码重置后清除登录锁定与首登改密标记(验证码经服务器 Redis 注入, 不发真实短信)
# 全部使用 SMOKE_TEST_USER 闭环, 每场景以成功登录收尾自动清 IP 级失败计数, 结束密码恢复 SMOKE_TEST_PASS(幂等)
echo "--- [3.6] 改密回归 ---"
if [ -n "$WTOKEN" ] && [ -n "$TEST_PASS" ] && [ -n "$TEST_USER" ]; then
  UNAME="$TEST_USER"
  # 找 smoke 用户 id
  SID=$(curl -sk -X GET "$BASE/api/system/users?current=1&size=5&keyword=$UNAME" -H "$AUTH" --connect-timeout 8 \
    | python3 -c "import sys,json;d=json.load(sys.stdin).get('data',{});rows=d.get('records',[]) or [];print(next((str(r['id']) for r in rows if r.get('username')=='$UNAME'),''))" 2>/dev/null)
  if [ -z "$SID" ]; then
    echo "⏭️  改密回归跳过(找不到用户 $UNAME 的 id)"
  else
    # ---- 场景A: 输错5次触发锁定 ----
    for i in 1 2 3 4 5; do
      curl -sk -o /dev/null -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"wrongpw$i\"}" --connect-timeout 8
    done
    LOCKMSG=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
      -d "{\"username\":\"$UNAME\",\"password\":\"wrongpw6\"}" --connect-timeout 8 \
      | python3 -c "import sys,json;print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
    [ "$LOCKMSG" = "登录失败次数过多，请15分钟后再试" ] && ok "改密A1: 输错5次触发锁定" || bad "改密A1: 锁定未触发($LOCKMSG)"

    # ---- 场景A: 管理员重置 → 新密码立即登录成功(验证清锁定修复) ----
    NEWPW=$(curl -sk -X POST "$BASE/api/system/users/$SID/reset-password" -H "$AUTH" --connect-timeout 8 \
      | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',''))" 2>/dev/null)
    if [ -n "$NEWPW" ]; then
      TOKEN_A=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"$NEWPW\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
      [ -n "$TOKEN_A" ] && ok "改密A2: 重置后新密码立即登录成功(锁定已清除)" || bad "改密A2: 重置后新密码登录失败(锁定未清除?)"
    else
      bad "改密A2: 重置接口未返回临时密码"
    fi

    # ---- 场景B: 改密后旧 token 立即失效 + 状态恢复 ----
    if [ -n "${TOKEN_A:-}" ] && [ -n "$NEWPW" ]; then
      CHG=$(curl -sk -X POST "$BASE/api/system/users/change-password" -H "Authorization: Bearer $TOKEN_A" \
        -H "Content-Type: application/json" -d "{\"oldPassword\":\"$NEWPW\",\"newPassword\":\"$TEST_PASS\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
      [ "$CHG" = "200" ] && ok "改密B1: 临时密码改回 SMOKE_TEST_PASS" || bad "改密B1: change-password 失败(code=$CHG)"
      sleep 1  # 避开"同秒改密不拦截"的设计权衡(iat 与改密时刻同秒时按改密后签发处理)
      OLDHTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X GET "$BASE/api/system/users?page=1&size=1" \
        -H "Authorization: Bearer $TOKEN_A" --connect-timeout 8)
      [ "$OLDHTTP" = "403" ] && ok "改密B2: 改密后旧 token 访问被 403 拒绝" || bad "改密B2: 旧 token 仍可访问($OLDHTTP, 应 403)"
      RB_TOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"$TEST_PASS\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)
      [ -n "$RB_TOKEN" ] && ok "改密B3: 原密码登录成功(状态恢复幂等)" || bad "改密B3: 原密码登录失败(账号状态异常, 需人工检查)"
    fi

    # ---- 场景C: 忘记密码重置(Redis 注入验证码, 不发真实短信) ----
    if [ ${#TEST_PASS} -ge 6 ]; then
      for i in 1 2 3 4 5; do
        curl -sk -o /dev/null -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
          -d "{\"username\":\"$UNAME\",\"password\":\"wrongpw$i\"}" --connect-timeout 8
      done
      LOCKMSG2=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"wrongpw6\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
      [ "$LOCKMSG2" = "登录失败次数过多，请15分钟后再试" ] && ok "改密C1: 忘记密码场景输错5次触发锁定" || bad "改密C1: 锁定未触发($LOCKMSG2)"
      # 服务器注入验证码(等价短信发送成功后的 Redis 状态; 短信通道本身属阿里云侧, 不在此回归)
      if [ "$ENV" = "prod" ]; then
        ssh_prod "RP=\$(docker inspect ycmn-redis --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep -io 'requirepass=[^ ]*' | head -1 | cut -d= -f2); if [ -n \"\$RP\" ]; then docker exec ycmn-redis redis-cli -a \"\$RP\" set pwd_reset:$UNAME 123456 EX 300 >/dev/null 2>&1; else docker exec ycmn-redis redis-cli set pwd_reset:$UNAME 123456 EX 300 >/dev/null 2>&1; fi && echo INJECTED"
      else
        echo "(测试环境: 验证码注入需按测试服 Redis 路径适配, 暂跳过)"
      fi
      FRESET=$(curl -sk -X POST "$BASE/api/auth/forgot-password/reset" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"code\":\"123456\",\"newPassword\":\"$TEST_PASS\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','x'))" 2>/dev/null)
      [ "$FRESET" = "200" ] && ok "改密C2: 忘记密码重置成功" || bad "改密C2: 忘记密码重置失败(code=$FRESET)"
      FC_TOKEN=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"$TEST_PASS\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;d=json.load(sys.stdin).get('data',{});print(d.get('token',''))" 2>/dev/null)
      [ -n "$FC_TOKEN" ] && ok "改密C3: 忘记密码重置后立即登录成功(锁定已清除)" || bad "改密C3: 重置后登录仍失败(锁定未清除?)"
      FCPR=$(curl -sk -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$UNAME\",\"password\":\"$TEST_PASS\"}" --connect-timeout 8 \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('pwdResetRequired','x'))" 2>/dev/null)
      [ "$FCPR" = "0" ] && ok "改密C4: 首登强制改密标记已清除(pwdResetRequired=0)" || bad "改密C4: pwdResetRequired=$FCPR(应 0)"
    else
      echo "⏭️  改密场景C跳过(SMOKE_TEST_PASS 长度不足 6 位)"
    fi
  fi
else
  echo "⏭️  改密回归跳过(需配置 SMOKE_TEST_USER/SMOKE_TEST_PASS)"
fi

# [4] 页面可用性
PAGE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/" --connect-timeout 8)
[ "$PAGE" = "200" ] && ok "首页 $PAGE" || bad "首页 $PAGE"

echo "══════════════════════════════════════════════════"
echo "结果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -gt 0 ] && { echo "❌ 冒烟测试未通过"; exit 1; } || { echo "✅ 冒烟测试全部通过"; exit 0; }

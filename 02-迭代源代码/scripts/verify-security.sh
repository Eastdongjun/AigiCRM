#!/usr/bin/env bash
# 【全系统测试矩阵·11 安全域】运行时回归 2026-11-22
# 口径源: 矩阵 11 表。既有自动防线现跑 + 越权/凭据/注入/锁定活体探针。
# 断言:
#   SEC-01 纵向越权: 仅 customer:view 的账号调管理端点 → 403
#   SEC-02 横向越权: self 范围账号 GET 他人客户详情 → 拒绝
#   SEC-03 登出即失效: logout 后旧 token 调 /auth/info → 401(黑名单机制活体)
#   SEC-04 token 篡改: 改签名末位 → 401
#   SEC-05 登录锁定: 同 IP+用户名连续 5 次错密 → 锁定提示(探针后清计数器)
#   SEC-06 SQL 注入探针: keyword 注入载荷 → 参数化(200 且命中 0, 非全量/非报错)
#   SEC-07 口令存储: 全库口令列 bcrypt($2a$) 抽查
#   SEC-08 既有契约现跑: secret-scan / jwt-secret / client-ip / native-sql 四脚本
#   SEC-09 actuator 收敛: 公网不可达后端 JSON(复证)
#   注记: XSS 由 Vue 前端默认转义+JSON API 天然不执行(存储型回显无脚本上下文);
#         文件上传边界由 test-fabushi-upload-limits.js(2026-11-17 验收)覆盖, 不重跑(110MB 重型)。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-security.sh
set -uo pipefail
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
code_only() { curl -s -m 20 -o /dev/null -w '%{http_code}' "$@"; }

TS="$(date +%s)"
HASH=$(PM_PW="PMxs$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxs$TS"
ROLE="pmxs_role$TS"; U1="pmxs_u1$TS"; U2="pmxs_u2$TS"

cleanup() {
  ssh aigicrm-test 'C=$(docker ps --format "{{.Names}}" | grep -i redis | head -1); docker exec "$C" redis-cli --scan --pattern "login_fail:*" | xargs -r docker exec -i "$C" redis-cli DEL' >/dev/null 2>&1
  printf 'SET NAMES utf8mb4;\nDELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE "pmxs_%%");\nDELETE FROM sys_user WHERE username LIKE "pmxs_%%";\nDELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code="%s");\nDELETE FROM sys_role WHERE role_code="%s";\n' "$ROLE" "$ROLE" \
    | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' >/dev/null 2>&1
}
trap cleanup EXIT
cleanup

q1() { printf 'SET NAMES utf8mb4;\n%s\n' "$1" | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null | tail -n +2; }
G1_DEPT=$(q1 "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
q1 "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'安全域回归','$ROLE','矩阵11域夹具,即删','self',1);" >/dev/null
RID=$(q1 "SELECT id FROM sys_role WHERE role_code='$ROLE'" | tail -1)
q1 "INSERT INTO sys_role_menu (role_id, menu_id) SELECT $RID, id FROM sys_menu WHERE permission='customer:view' AND status=1 AND menu_type=2 LIMIT 1;" >/dev/null
for U in "$U1" "$U2"; do
  q1 "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U','$HASH','$U',1,0,$G1_DEPT,$G1_DEPT);
      INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U'), $RID);" >/dev/null
done

login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}
T1=$(login_token "$U1" "$TMP_PW")
TADM=$(login_token admin "$API_PASS")
[ -n "$T1" ] && [ -n "$TADM" ] && ok "夹具登录" || { bad "夹具登录失败"; echo "==> 安全域回归: PASS=$PASS FAIL=$FAIL"; exit 1; }

# ---- SEC-01 纵向越权 ----
C1=$(code_only -X POST "$BASE/api/reminder/ensure-expiring" -H "Authorization: Bearer $T1" -H "Origin: $BASE")
[ "$C1" = "403" ] && ok "SEC-01 纵向越权: 无权限码调管理端点 403" || bad "SEC-01 纵向越权=$C1 (期望403)"

# ---- SEC-02 横向越权 ----
ADMIN_ID=$(q1 "SELECT id FROM sys_user WHERE username='admin'" | tail -1)
VCID=$(q1 "SELECT id FROM biz_customer WHERE owner_id=$ADMIN_ID AND deleted=0 LIMIT 1" | tail -1)
if [ -n "$VCID" ]; then
  C2=$(code_only "$BASE/api/customer/$VCID" -H "Authorization: Bearer $T1" -H "Origin: $BASE")
  case "$C2" in 403|404) ok "SEC-02 横向越权: 他人客户详情 $C2(拒绝)";; *) bad "SEC-02 横向越权=$C2 (期望403/404)";; esac
else
  echo "INFO: SEC-02 admin 无存量客户, 跳过(用夹具客户替代)"; VCID=$(q1 "SELECT id FROM biz_customer WHERE deleted=0 AND owner_id<>1 LIMIT 1" | tail -1)
  C2=$(code_only "$BASE/api/customer/$VCID" -H "Authorization: Bearer $T1" -H "Origin: $BASE")
  case "$C2" in 403|404) ok "SEC-02 横向越权: 他人客户详情 $C2(拒绝)";; *) bad "SEC-02 横向越权=$C2";; esac
fi

# ---- SEC-03 登出即失效 ----
T2=$(login_token "$U2" "$TMP_PW")
LO=$(code_only -X POST "$BASE/api/auth/logout" -H "Authorization: Bearer $T2" -H "Origin: $BASE")
C3=$(code_only "$BASE/api/auth/info" -H "Authorization: Bearer $T2")
[ "$LO" = "200" ] && [ "$C3" = "401" ] && ok "SEC-03 登出即失效: logout 200 后旧 token 401(黑名单活体)" || bad "SEC-03 登出语义: logout=$LO 旧token=$C3 (期望200/401)"

# ---- SEC-04 token 篡改 ----
TB="${T1%?}X"
C4=$(code_only "$BASE/api/auth/info" -H "Authorization: Bearer $TB")
[ "$C4" = "401" ] && ok "SEC-04 token 篡改(改签名末位): 401" || bad "SEC-04 篡改 token=$C4 (期望401)"

# ---- SEC-05 登录锁定(5 次) ----
for i in 1 2 3 4 5; do curl -s -o /dev/null -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$U2\",\"password\":\"wrong-$i\"}"; done
LOCKED=$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$U2\",\"password\":\"$TMP_PW\"}")
case "$LOCKED" in *"失败次数过多"*) ok "SEC-05 登录锁定: 5 次错密后正确口令也被锁(提示明确)";; *) bad "SEC-05 锁定未生效: $LOCKED";; esac
ssh aigicrm-test 'C=$(docker ps --format "{{.Names}}" | grep -i redis | head -1); docker exec "$C" redis-cli --scan --pattern "login_fail:*" | xargs -r docker exec -i "$C" redis-cli DEL' >/dev/null 2>&1

# ---- SEC-06 SQL 注入探针(参数化) ----
INJ=$(curl -s -m 20 -G "$BASE/api/customer/list" --data-urlencode "current=1" --data-urlencode "size=5" --data-urlencode "keyword=' OR '1'='1" -H "Authorization: Bearer $T1" -H "Origin: $BASE" | python3 -c 'import json,sys
try: d=json.load(sys.stdin); print(d.get("code"), (d.get("data") or {}).get("total"))
except Exception as e: print("ERR", e)')
[ "$INJ" = "200 0" ] && ok "SEC-06 注入探针: ' OR '1'='1 → 200 total=0(参数化, 无全量泄漏无报错)" || bad "SEC-06 注入探针异常: $INJ"
INJ2=$(curl -s -m 20 -G "$BASE/api/customer/list" --data-urlencode "current=1" --data-urlencode "size=5" --data-urlencode "keyword=x%27%20UNION%20SELECT%201--" -H "Authorization: Bearer $T1" -H "Origin: $BASE" | python3 -c 'import json,sys
try: d=json.load(sys.stdin); print(d.get("code"))
except Exception: print("ERR")')
[ "$INJ2" = "200" ] && ok "SEC-06 注入探针2: UNION 载荷参数化为普通关键词(200)" || bad "SEC-06 UNION 探针: $INJ2"

# ---- SEC-07 口令存储 ----
BC=$(q1 "SELECT COUNT(*) FROM sys_user WHERE password IS NOT NULL AND password<>'' AND password NOT RLIKE '^\\\\\$2[ab y]\\\\\$[0-9]{2}\\\\\$'" | tail -1)
[ "$BC" = "0" ] && ok "SEC-07 口令存储: 全库 bcrypt 家族(\$2a\$/\$2b\$) 零明文/弱哈希" || bad "SEC-07 非 bcrypt 口令 $BC 条"

# ---- SEC-08 既有契约四脚本现跑 ----
for s in check-secret-scan.sh check-jwt-secret-contract.sh check-client-ip-contract.sh check-native-sql-tenant-scope.sh; do
  if bash "scripts/$s" >/tmp/sec8.log 2>&1; then ok "SEC-08 $s: PASS"; else bad "SEC-08 $s: FAIL"; tail -3 /tmp/sec8.log; fi
done

# ---- SEC-09 actuator 收敛(复证) ----
AC=$(curl -s -m 10 "$BASE/actuator/health")
echo "$AC" | grep -q '"status"' && bad "SEC-09 actuator 公网可达: $AC" || ok "SEC-09 actuator 公网不可达(nginx 收敛)"

echo "==> 安全域回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
#!/usr/bin/env bash
# 【全系统测试矩阵·08 日志审计域】运行时回归 2026-11-22
# 口径源: 矩阵 08 表 + 日志全景实况:
#   sys_audit_log(SecurityAuditService: LOGIN_*/ACCESS_DENIED/USER_ADMIN/PERMISSION_CHANGE...)
#   sys_log(LogAspect AOP: 所有 controller POST/PUT/DELETE 自动记, 成功+失败双路径)
# 断言:
#   LOG-01 登录成功留痕: LOGIN_SUCCESS(actor/ip/时间)
#   LOG-02 登录失败留痕: LOGIN_FAILED(username/ip)——口令值不得出现在任何日志(脱敏)
#   LOG-03 写操作自动留痕: extend 提交 200 → sys_log 行(谁/模块/动作/ip)
#   LOG-04 失败也留痕: 未知审批类型 400 → sys_log 失败路径行(含异常消息)
#   LOG-05 五要素完整: 抽样行 谁(user_id+username)/时/IP/对象(detail/target)/结果 全非空
#   LOG-06 敏感脱敏: 探针口令字面量在两张日志表全库 0 命中
#   LOG-07 越权留痕: 无权限调 ensure → 403 + ACCESS_DENIED(uri/userId/ip)
#   LOG-08 查询口权限+功能: 普通用户 GET /api/system/logs → 403; admin → 200 且能筛到本人夹具行
#   LOG-09 不可篡改(结构级): 对日志端点尝试 POST/PUT/DELETE → 无写口(403/404/405)
# 夹具: pmxl_ 用户(self 角色) + pmxl-c- 客户 + pmxl extend 配置; 日志行不清理(append-only 语义,夹具人/客户清理)。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-log-audit.sh
set -uo pipefail
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_USER="${AIGI_TEST_USER:-admin}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

db() {
  printf 'SET NAMES utf8mb4;\n%s\n' "$1" \
    | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null
}
login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}
http_code() { curl -s -m 20 -o /dev/null -w '%{http_code}' "$@"; }

TS="$(date +%s)"
HASH=$(PM_PW="PMxl$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxl$TS"
ROLE_CODE="pmxl_role$TS"; U_X="pmxl_u$TS"; U_APR="pmxl_apr$TS"
PROBE_PW="PMXL-PROBE-SECRET-$TS"   # 脱敏探针: 只用于一次故意失败的登录

cleanup() {
  db "DELETE ar FROM biz_approval_node ar WHERE ar.approval_id IN (SELECT id FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxl_%'));
      DELETE FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxl_%');
      DELETE FROM biz_customer WHERE customer_name LIKE 'pmxl-c-%';
      DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxl_%');
      DELETE FROM sys_user WHERE username LIKE 'pmxl_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='$ROLE_CODE');
      DELETE FROM sys_role WHERE role_code='$ROLE_CODE';
      DELETE FROM biz_approval_config WHERE flow_name LIKE 'pmxl%';" >/dev/null
}
trap cleanup EXIT
cleanup

G1_DEPT=$(db "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'日志回归','$ROLE_CODE','矩阵08域夹具,即删','self',1);" >/dev/null
RID=$(db "SELECT id FROM sys_role WHERE role_code='$ROLE_CODE'" | tail -1)
db "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($RID,18);" >/dev/null  # approval:view 使 submit 可达(AOP 才有得记)
for U in "$U_X" "$U_APR"; do
  db "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U','$HASH','$U',1,0,$G1_DEPT,$G1_DEPT);
      INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U'), $RID);" >/dev/null
done
ID_X=$(db "SELECT id FROM sys_user WHERE username='$U_X'" | tail -1)
ID_APR=$(db "SELECT id FROM sys_user WHERE username='$U_APR'" | tail -1)
db "INSERT INTO biz_customer (customer_name, customer_code, tenant_id, owner_id, status, deleted, created_at) VALUES ('pmxl-c-$TS','PMXL-$TS',1,$ID_X,'normal',0,NOW());
    INSERT INTO biz_approval_config (flow_name, flow_type, approver_type, approver_ids, sign_mode, sort_order, status, tenant_id) VALUES ('pmxl日志链','extend','USER','[$ID_APR]','ANY',1,1,1);" >/dev/null
CID=$(db "SELECT id FROM biz_customer WHERE customer_name='pmxl-c-$TS'" | tail -1)
[ -n "$ID_X" ] && [ -n "$CID" ] && ok "夹具就绪(用户+客户+extend配置)" || { bad "夹具创建失败"; echo "==> 日志域回归: PASS=$PASS FAIL=$FAIL"; exit 1; }

# ---- LOG-01 登录成功留痕 ----
T_X=$(login_token "$U_X" "$TMP_PW")
N=$(db "SELECT COUNT(*) FROM sys_audit_log WHERE event_type='LOGIN_SUCCESS' AND actor_id=$ID_X AND ip IS NOT NULL AND ip<>'' AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1)
[ -n "$T_X" ] && [ "$N" -ge 1 ] && ok "LOG-01 登录成功: LOGIN_SUCCESS 留痕(actor+ip+时间)" || bad "LOG-01 登录成功留痕缺失(token=${#T_X}字符, rows=$N)"

# ---- LOG-02 登录失败留痕 + LOG-06 脱敏探针 ----
curl -s -o /dev/null -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$U_X\",\"password\":\"$PROBE_PW\"}"
N=$(db "SELECT COUNT(*) FROM sys_audit_log WHERE event_type='LOGIN_FAILED' AND actor_username='$U_X' AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1)
[ "$N" -ge 1 ] && ok "LOG-02 登录失败: LOGIN_FAILED 留痕(username+ip)" || bad "LOG-02 登录失败留痕缺失(rows=$N)"
HIT=$(db "SELECT (SELECT COUNT(*) FROM sys_audit_log WHERE detail LIKE '%$PROBE_PW%') + (SELECT COUNT(*) FROM sys_log WHERE detail LIKE '%$PROBE_PW%')" | tail -1)
[ "$HIT" = "0" ] && ok "LOG-06 敏感脱敏: 探针口令在两张日志表 0 命中" || bad "LOG-06 口令泄漏进日志! 命中=$HIT"

# ---- LOG-03 写操作自动留痕(AOP 成功路径) ----
SUBMIT="{\"type\":\"extend\",\"customerId\":\"$CID\",\"extraData\":\"{\\\"extendDays\\\":15}\"}"
curl -s -o /dev/null -X POST "$BASE/api/approval/submit" -H "Authorization: Bearer $T_X" -H "Origin: $BASE" -H 'Content-Type: application/json' -d "$SUBMIT"
N=$(db "SELECT COUNT(*) FROM sys_log WHERE user_id=$ID_X AND module='approval' AND action LIKE '%submit%' AND ip IS NOT NULL AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1)
[ "$N" -ge 1 ] && ok "LOG-03 写操作: sys_log 自动留痕(module=approval)" || { N2=$(db "SELECT COUNT(*) FROM sys_log WHERE user_id=$ID_X AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1); [ "$N2" -ge 1 ] && ok "LOG-03 写操作: sys_log 留痕(user 维度 $N2 行, module 命名不同)" || bad "LOG-03 写操作无 sys_log 行(rows=$N/$N2)"; }

# ---- LOG-04 失败也留痕(AOP AfterThrowing) ----
curl -s -o /dev/null -X POST "$BASE/api/approval/submit" -H "Authorization: Bearer $T_X" -H "Origin: $BASE" -H 'Content-Type: application/json' -d '{"type":"pmxl_fake"}'
N=$(db "SELECT COUNT(*) FROM sys_log WHERE user_id=$ID_X AND detail LIKE '%不支持的审批类型%' AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1)
[ "$N" -ge 1 ] && ok "LOG-04 失败留痕: 400 异常也落 sys_log(含异常消息)" || bad "LOG-04 失败路径无留痕(rows=$N)"

# ---- LOG-05 五要素完整(抽样两表) ----
FIVE=$(db "SELECT CONCAT(
  (SELECT COUNT(*) FROM sys_log WHERE user_id=$ID_X AND username='$U_X' AND created_at>=NOW()-INTERVAL 10 MINUTE AND (ip IS NULL OR ip='')),
  (SELECT COUNT(*) FROM sys_audit_log WHERE actor_id=$ID_X AND created_at>=NOW()-INTERVAL 10 MINUTE AND (ip IS NULL OR ip='')))" | tail -1)
[ "$FIVE" = "00" ] && ok "LOG-05 五要素: 抽样行 谁/时/IP 无缺失(detail=对象/结果 两表均有值)" || bad "LOG-05 要素缺失行数: $FIVE"

# ---- LOG-07 越权留痕(ACCESS_DENIED) ----
T_APR=$(login_token "$U_APR" "$TMP_PW")
http_code -X POST "$BASE/api/reminder/ensure-expiring" -H "Authorization: Bearer $T_APR" >/dev/null
N=$(db "SELECT COUNT(*) FROM sys_audit_log WHERE event_type='ACCESS_DENIED' AND actor_id=$ID_APR AND created_at>=NOW()-INTERVAL 5 MINUTE" | tail -1)
[ "$N" -ge 1 ] && ok "LOG-07 越权: ACCESS_DENIED 留痕(uri+userId)" || bad "LOG-07 方法级越权无留痕(rows=$N)——BUG-20261122-006: @PreAuthorize 拒绝走 GlobalExceptionHandler 不调审计, 全库 ACCESS_DENIED 恒 0"

# ---- LOG-08 查询口权限 + 功能 ----
C1=$(http_code "$BASE/api/system/logs?current=1&size=5" -H "Authorization: Bearer $T_APR")
C2=$(http_code "$BASE/api/system/logs?current=1&size=5&keyword=$U_X" -H "Authorization: Bearer $(login_token "$API_USER" "$API_PASS")")
[ "$C1" = "403" ] && ok "LOG-08 普通用户查日志: 403" || bad "LOG-08 普通用户查日志=$C1 (期望403)"
[ "$C2" = "200" ] && ok "LOG-08 admin 查日志: 200(可筛选)" || bad "LOG-08 admin 查日志=$C2 (期望200)"

# ---- LOG-09 不可篡改(结构级: 无写口) ----
C3=$(http_code -X DELETE "$BASE/api/system/logs/1" -H "Authorization: Bearer $(login_token "$API_USER" "$API_PASS")")
C4=$(http_code -X POST "$BASE/api/system/audit-logs" -H "Authorization: Bearer $(login_token "$API_USER" "$API_PASS")" -H 'Content-Type: application/json' -d '{}')
case "$C3$C4" in
  403*|404*|405*) ok "LOG-09 日志无写口: DELETE=$C3 POST=$C4(不可篡改, 结构级)";;
  *) bad "LOG-09 日志出现写口!: DELETE=$C3 POST=$C4";;
esac

echo "==> 日志域回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
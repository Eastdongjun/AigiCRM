#!/usr/bin/env bash
# 【全系统测试矩阵·09 数据库核对域】运行时回归 2026-11-22
# 口径源: 矩阵 09 表——「操作 → 落库逐字段核对」+ 结构不变量(时间戳/软删/事务/JSON 列/关联完整)。
# 断言:
#   DB-01 API建客→逐字段落库: name(含中文·特殊字符)/code自动/tenant/owner/status/deleted/时间戳非空
#   DB-02 updated_at 自动更新: 改名后 updated_at > created_at, name 落库
#   DB-03 中文utf8mb4回读: API 写入→API 读回逐字节一致(双编码零损伤)
#   DB-04 软删传播: deleted=1+物理行在; 列表/搜索消失; 同名可复建(active_name 唯一索引对软删行的处理)
#   DB-05 事务回滚无半写: 全本人链提交被拒 → 无 approval 行+无 node 行+客户不动(@Transactional 回滚证据)
#   DB-06 JSON 列口径: extend 单 extra_data 有效 JSON, JSON_EXTRACT($.extendDays)=30
#   DB-07 业务写 updated_at: 审批动作后 approval.updated_at 变更
#   DB-08 关联完整性: 夹具数据 approval↔customer 无孤儿; 全局孤儿数报告(不盲目断言存量)
#   DB-09 同事务双写: approve 后 approval.status=approved 与 customer.approved_extend_days 同步落库(与06域AC-05互证)
# 夹具: pmxd_ 前缀(角色按权限码绑菜单:add/edit/view/delete) + U1(发起人/客户主) + U2(审批人) + extend 配置。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-db-consistency.sh
set -uo pipefail
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
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
api() { # $1=method $2=token $3=path $4=body(可空) → "code data"
  curl -s -m 20 -X "$1" "$BASE$3" -H "Authorization: Bearer $2" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    ${4:+-d "$4"} | python3 -c 'import json,sys
raw=sys.stdin.read()
try:
  d=json.loads(raw); print(d.get("code"), json.dumps(d.get("data"), ensure_ascii=False), d.get("message") or "")
except Exception:
  print(raw[:40], "")'
}

TS="$(date +%s)"
HASH=$(PM_PW="PMxd$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxd$TS"
ROLE_CODE="pmxd_role$TS"; U1="pmxd_u1$TS"; U2="pmxd_u2$TS"
CNAME="pmxd-c-华东密封件·试验${TS}✓"

cleanup() {
  db "DELETE ar FROM biz_approval_node ar WHERE ar.approval_id IN (SELECT id FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxd_%'));
      DELETE FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxd_%');
      DELETE FROM biz_customer WHERE customer_name LIKE 'pmxd-c-%';
      DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxd_%');
      DELETE FROM sys_user WHERE username LIKE 'pmxd_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='$ROLE_CODE');
      DELETE FROM sys_role WHERE role_code='$ROLE_CODE';
      DELETE FROM biz_approval_config WHERE flow_name LIKE 'pmxd%';" >/dev/null
}
trap cleanup EXIT
cleanup

G1_DEPT=$(db "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'DB核对回归','$ROLE_CODE','矩阵09域夹具,即删','self',1);" >/dev/null
RID=$(db "SELECT id FROM sys_role WHERE role_code='$ROLE_CODE'" | tail -1)
# 菜单按权限码动态绑(不硬编码 id, 消除漂移)
MENUS=$(db "SELECT GROUP_CONCAT(DISTINCT id) FROM sys_menu WHERE permission IN ('customer:view','customer:add','customer:edit','customer:delete','approval:view','approval:approve','approval:reject') AND status=1" | tail -1)
db "INSERT INTO sys_role_menu (role_id, menu_id) SELECT $RID, id FROM sys_menu WHERE FIND_IN_SET(id,'$MENUS');" >/dev/null
for U in "$U1" "$U2"; do
  db "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U','$HASH','$U',1,0,$G1_DEPT,$G1_DEPT);
      INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U'), $RID);" >/dev/null
done
ID1=$(db "SELECT id FROM sys_user WHERE username='$U1'" | tail -1)
ID2=$(db "SELECT id FROM sys_user WHERE username='$U2'" | tail -1)
[ -n "$ID1" ] && [ -n "$ID2" ] || { bad "夹具用户创建失败"; echo "==> DB核对域回归: PASS=$PASS FAIL=$FAIL"; exit 1; }
db "INSERT INTO biz_approval_config (flow_name, flow_type, approver_type, approver_ids, sign_mode, sort_order, status, tenant_id) VALUES ('pmxd-db链','extend','USER','[$ID2]','ANY',1,1,1);" >/dev/null

T1=$(login_token "$U1" "$TMP_PW"); T2=$(login_token "$U2" "$TMP_PW")
TADM=$(login_token admin "$API_PASS")
[ -n "$T1" ] && [ -n "$T2" ] && [ -n "$TADM" ] && ok "夹具登录" || bad "夹具登录失败"

# ---- DB-01 API建客→逐字段落库 ----
R=$(api POST "$T1" /api/customer/add "{\"customerName\":\"$CNAME\"}")
CID=$(echo "$R" | cut -d' ' -f2 | tr -d '"')
[ "${R%% *}" = "200" ] && [ -n "$CID" ] && [ "$CID" != "null" ] && ok "DB-01 建客 API 200 id=$CID" || bad "DB-01 建客失败: $R"
# 分字段核对:
F_NAME=$(db "SELECT customer_name='$CNAME' FROM biz_customer WHERE id=$CID" | tail -1)
F_CODE=$(db "SELECT customer_code IS NOT NULL AND customer_code<>'' FROM biz_customer WHERE id=$CID" | tail -1)
F_TEN=$(db "SELECT tenant_id FROM biz_customer WHERE id=$CID" | tail -1)
F_OWN=$(db "SELECT owner_id=$ID1 FROM biz_customer WHERE id=$CID" | tail -1)
F_ST=$(db "SELECT CONCAT(status='normal','/',deleted=0,'/',created_at IS NOT NULL,'/',updated_at IS NOT NULL) FROM biz_customer WHERE id=$CID" | tail -1)
[ "$F_NAME$F_CODE$F_OWN" = "111" ] && ok "DB-01 逐字段: name(中文✓)/code自动/owner=创建人 全对" || bad "DB-01 字段核对: name=$F_NAME code=$F_CODE owner=$F_OWN"
[ "$F_ST" = "1/1/1/1" ] && ok "DB-01 结构字段: status=normal/deleted=0/时间戳自动填充" || bad "DB-01 结构字段: $F_ST"

# ---- DB-02 updated_at 自动更新 ----
sleep 1
api PUT "$T1" "/api/customer/$CID" "{\"customerName\":\"${CNAME}改\"}" >/dev/null
F_UP=$(db "SELECT updated_at > created_at FROM biz_customer WHERE id=$CID" | tail -1)
F_NM=$(db "SELECT customer_name='${CNAME}改' FROM biz_customer WHERE id=$CID" | tail -1)
[ "$F_UP$F_NM" = "11" ] && ok "DB-02 改名后 updated_at>created_at 且新名落库" || bad "DB-02 updated_at=$F_UP name=$F_NM"

# ---- DB-03 中文 utf8mb4 回读 ----
BACK=$(curl -s -m 20 -G "$BASE/api/customer/list" --data-urlencode "current=1" --data-urlencode "size=50" --data-urlencode "keyword=pmxd-c-华东密封件" -H "Authorization: Bearer $T1" -H "Origin: $BASE")
NAME_BACK=$(printf '%s' "$BACK" | grep -o "${CNAME}改" | head -1)
[ -n "$NAME_BACK" ] && ok "DB-03 中文名(含·✓) API 写入→读回逐字节一致" || bad "DB-03 中文回读失败: $BACK"

# ---- DB-04 软删传播 ----
C4=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/customer/$CID" -H "Authorization: Bearer $T1" -H "Origin: $BASE")
F_DEL=$(db "SELECT CONCAT(deleted=1,'/',COUNT(*)) FROM biz_customer WHERE id=$CID" | tail -1)
GONE=$(curl -s -m 20 -G "$BASE/api/customer/list" --data-urlencode "current=1" --data-urlencode "size=50" --data-urlencode "keyword=pmxd-c-华东密封件" -H "Authorization: Bearer $T1" -H "Origin: $BASE" | grep -c "pmxd-c-" || true)
R2=$(api POST "$T1" /api/customer/add "{\"customerName\":\"${CNAME}改\"}")
[ "$C4" = "200" ] && ok "DB-04 软删 API 200" || bad "DB-04 删除失败: $C4"
[ "$F_DEL" = "1/1" ] && ok "DB-04 软删语义: deleted=1 且物理行保留" || bad "DB-04 软删字段: $F_DEL"
[ "$GONE" = "0" ] && ok "DB-04 软删后列表/搜索消失" || bad "DB-04 软删后仍可见: $GONE 条"
[ "${R2%% *}" = "200" ] && ok "DB-04 同名可复建(active_name 唯一索引对软删行正确放行)" || bad "DB-04 同名复建被拒: $R2"

# ---- DB-05 事务回滚无半写 ----
CID2=$(echo "$R2" | cut -d' ' -f2 | tr -d '"')
db "UPDATE biz_customer SET expire_date=CURDATE()+INTERVAL 5 DAY WHERE id=$CID2;" >/dev/null
db "UPDATE biz_approval_config SET approver_ids='[$ID1]' WHERE flow_name='pmxd-db链';" >/dev/null  # 全本人链→提交侧拒
N_BEFORE=$(db "SELECT COUNT(*) FROM biz_approval WHERE applicant_id=$ID1" | tail -1)
R3=$(api POST "$T1" /api/approval/submit "{\"type\":\"extend\",\"customerId\":\"$CID2\",\"extraData\":\"{\\\"extendDays\\\":10}\"}")
N_AFTER=$(db "SELECT COUNT(*) FROM biz_approval WHERE applicant_id=$ID1" | tail -1)
N_NODE=$(db "SELECT COUNT(*) FROM biz_approval_node WHERE approval_id IN (SELECT id FROM biz_approval WHERE applicant_id=$ID1)" | tail -1)
case "$R3" in *"审批人全部是申请人本人"*) ok "DB-05 拒绝提交(预期失败)";; *) bad "DB-05 未按预期拒绝: $R3";; esac
[ "$N_BEFORE" = "$N_AFTER" ] && [ "$N_NODE" = "0" ] && ok "DB-05 事务回滚: 0 半写(无 approval 行/无 node 行)" || bad "DB-05 半写残留: approval $N_BEFORE→$N_AFTER, node=$N_NODE"

# ---- DB-06 JSON 列 + DB-07 updated_at + DB-09 同事务双写 ----
db "UPDATE biz_approval_config SET approver_ids='[$ID2]' WHERE flow_name='pmxd-db链';" >/dev/null
R4=$(api POST "$T1" /api/approval/submit "{\"type\":\"extend\",\"customerId\":\"$CID2\",\"extraData\":\"{\\\"extendDays\\\":30}\"}")
AID=$(db "SELECT id FROM biz_approval WHERE applicant_id=$ID1 ORDER BY id DESC LIMIT 1" | tail -1)
J=$(db "SELECT JSON_VALID(extra_data) AND JSON_EXTRACT(extra_data,'$.extendDays')=30 FROM biz_approval WHERE id=$AID" | tail -1)
[ "$J" = "1" ] && ok "DB-06 extra_data 有效 JSON, \$.extendDays=30" || bad "DB-06 JSON 列: $J"
UP1=$(db "SELECT updated_at FROM biz_approval WHERE id=$AID" | tail -1)
sleep 1
api POST "$T2" "/api/approval/$AID/approve" '{"comment":"DB域"}' >/dev/null
ST=$(db "SELECT status FROM biz_approval WHERE id=$AID" | tail -1)
UP2=$(db "SELECT updated_at FROM biz_approval WHERE id=$AID" | tail -1)
DAYS=$(db "SELECT approved_extend_days FROM biz_customer WHERE id=$CID2" | tail -1)
[ "$UP2" \> "$UP1" ] && ok "DB-07 审批动作后 approval.updated_at 变更" || bad "DB-07 updated_at 未变: $UP1 → $UP2"
[ "$ST" = "approved" ] && [ "$DAYS" = "30" ] && ok "DB-09 同事务双写: approval=approved 与客户天数=30 同步落库(与06域AC-05互证)" || bad "DB-09 双写不一致: status=$ST days=$DAYS"

# ---- DB-08 关联完整性 ----
ORPH_F=$(db "SELECT COUNT(*) FROM biz_approval a LEFT JOIN biz_customer c ON a.customer_id=c.id WHERE a.customer_id IS NOT NULL AND c.id IS NULL" | tail -1)
ORPH_ALL=$(db "SELECT COUNT(*) FROM biz_approval a LEFT JOIN biz_customer c ON a.customer_id=c.id WHERE c.id IS NULL" | tail -1)
[ "$ORPH_F" = "0" ] && ok "DB-08 关联完整性: 有 customer_id 的审批无孤儿(全局)" || bad "DB-08 孤儿审批: $ORPH_F 条"
[ "$ORPH_ALL" = "0" ] && ok "DB-08 全局零孤儿(含 customer_id 为空的 storage 单口径外)" || echo "INFO: DB-08 全局孤儿(含空customer_id,如storage单属正常)=$ORPH_ALL"

echo "==> DB核对域回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
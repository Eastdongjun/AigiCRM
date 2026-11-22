#!/usr/bin/env bash
# 【全系统测试矩阵·07 消息域】到期提醒(本项目"消息"实体)运行时回归 2026-11-22
# 口径源: 矩阵 07 表 + ReminderService 真实实现(ensureExpiring 生成→ownerId 接收→
# 数据范围可见性→process 处理→当日去重)。
# 断言(与 07 表列对应):
#   MSG-01 触发生成: admin(reminder:ensure) 触发 → 到期客户物化提醒行
#   MSG-02 接收对象核对到人: 每条提醒 ownerId == 客户归属人(逐行, 非条数)
#   MSG-03 内容数值: overdueDays == expire_date - today (含逾期负值 -1)
#   MSG-04 去重口径: 再触发 → 每客户仍 1 条(pending 存在即跳过, 2026-09-23 口径)
#   MSG-05 可见性隔离(越权泄露): self 范围用户只见自己客户的提醒(名字集合精确 diff);
#         无客户用户 0 条 —— 2026 越权修复(applyOwnerScope)的活体验证
#   MSG-06 角标计数: pending-count 与本人可见 pending 数一致
#   MSG-07 处理+当日不重建: process → processed+processedAt; 再触发不重建(processedToday),
#         其余客户不受影响
#   MSG-08 处理守卫(对象级): 他人范围内提醒 process → 拒绝
#   MSG-09 ensure 权限收紧: 非 admin(无 reminder:ensure) 触发 → 403
# 夹具: pmxr_ 前缀(self 范围角色+三用户) + pmxr-c- 客户(3天后/2天后/昨天到期), 退出清理。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-reminder-message.sh
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
# GET 列表并提取 pmxr-c- 名字集合(排序)
list_names() { # $1=token
  curl -s -m 20 "$BASE/api/reminder/list?current=1&size=200" -H "Authorization: Bearer $1" \
    | python3 -c 'import json,sys
try:
  d=json.load(sys.stdin); recs=(d.get("data") or {}).get("records") or []
  print("\n".join(sorted(str(r.get("customerName"))[7:] for r in recs if str(r.get("customerName","")).startswith("pmxr-c-"))))
except Exception as e: print("PARSE_ERR:"+str(e))'
}
act() { # $1=token $2=方法+路径 $3=body(可空)
  curl -s -m 20 -X "${2%% *}" "$BASE${2#* }" -H "Authorization: Bearer $1" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} -w '\n%{http_code}' | python3 -c 'import json,sys
lines=sys.stdin.read().rsplit("\n",1); body,code=lines[0],lines[1]
try: m=json.loads(body).get("message") or ""
except Exception: m=body[:80]
print(code, m)'
}

TS="$(date +%s)"
HASH=$(PM_PW="PMxr$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxr$TS"
ROLE_CODE="pmxr_role$TS"
U_A="pmxr_a$TS"; U_B="pmxr_b$TS"; U_INTR="pmxr_intr$TS"

cleanup() {
  db "DELETE FROM biz_reminder WHERE customer_id IN (SELECT id FROM biz_customer WHERE customer_name LIKE 'pmxr-c-%');
      DELETE FROM biz_customer WHERE customer_name LIKE 'pmxr-c-%';
      DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxr_%');
      DELETE FROM sys_user WHERE username LIKE 'pmxr_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='$ROLE_CODE');
      DELETE FROM sys_role WHERE role_code='$ROLE_CODE';" >/dev/null
}
trap cleanup EXIT
cleanup

G1_DEPT=$(db "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
# 角色: self 范围 + reminder:view(16); ensure(160) 刻意不配(验证收紧)
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'提醒消息回归','$ROLE_CODE','矩阵07域夹具,即删','self',1);" >/dev/null
RID=$(db "SELECT id FROM sys_role WHERE role_code='$ROLE_CODE'" | tail -1)
db "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($RID,16);" >/dev/null
for U in "$U_A" "$U_B" "$U_INTR"; do
  db "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U','$HASH','$U',1,0,$G1_DEPT,$G1_DEPT);
      INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U'), $RID);" >/dev/null
done
ID_A=$(db "SELECT id FROM sys_user WHERE username='$U_A'" | tail -1)
ID_B=$(db "SELECT id FROM sys_user WHERE username='$U_B'" | tail -1)
[ -n "$ID_A" ] && [ -n "$ID_B" ] || { bad "夹具用户创建失败"; echo "==> 消息域回归: PASS=$PASS FAIL=$FAIL"; exit 1; }
# 客户: CA(A,3天后) CB(B,3天后) CC(B,2天后) CD(A,昨天=逾期)
mkcust() { # $1=tag $2=owner $3=DATE_ADD 表达式
  db "INSERT INTO biz_customer (customer_name, customer_code, tenant_id, owner_id, status, deleted, expire_date, created_at)
      VALUES ('pmxr-c-$1','PMXR-$1-$TS',1,$2,'normal',0,CURDATE() + INTERVAL $3 DAY, NOW());" >/dev/null
}
mkcust a1 "$ID_A" "3"; mkcust b1 "$ID_B" "3"; mkcust b2 "$ID_B" "2"; mkcust a2 "$ID_A" "-1"
N_CUST=$(db "SELECT COUNT(*) FROM biz_customer WHERE customer_name LIKE 'pmxr-c-%'" | tail -1)
[ "$N_CUST" = "4" ] && ok "夹具: 4 客户(3d/2d/逾期-1d)" || bad "夹具客户数=$N_CUST (期望4)"

T_ADM=$(login_token "$API_USER" "$API_PASS")
T_A=$(login_token "$U_A" "$TMP_PW"); T_B=$(login_token "$U_B" "$TMP_PW"); T_INTR=$(login_token "$U_INTR" "$TMP_PW")
[ -n "$T_ADM" ] && [ -n "$T_A" ] && [ -n "$T_B" ] && [ -n "$T_INTR" ] && ok "夹具登录(admin+A+B+INTR)" || bad "夹具登录失败"

# ---- MSG-01 触发生成 ----
R=$(act "$T_ADM" "POST /api/reminder/ensure-expiring")
[ "${R%% *}" = "200" ] && ok "MSG-01 admin 触发 ensure: 200" || bad "MSG-01 ensure 失败: $R"
N=$(db "SELECT COUNT(*) FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name LIKE 'pmxr-c-%' AND r.status='pending'" | tail -1)
[ "$N" = "4" ] && ok "MSG-01 物化: 4 条 pending" || bad "MSG-01 物化数=$N (期望4)"

# ---- MSG-02 接收对象核对到人(逐行 owner 映射, 四条独立查询避免长 SQL 静默错) ----
CHK=""
for pair in "a1:$ID_A" "b1:$ID_B" "b2:$ID_B" "a2:$ID_A"; do
  TAG="${pair%%:*}"; EXPECT_ID="${pair#*:}"
  GOT=$(db "SELECT r.owner_id FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-$TAG' LIMIT 1" | tail -1)
  [ "$GOT" = "$EXPECT_ID" ] && CHK="${CHK}OK|" || CHK="${CHK}BAD($TAG=$GOT)|"
done
CHK="${CHK%|}"
[ "$CHK" = "OK|OK|OK|OK" ] && ok "MSG-02 接收对象=客户归属人(四行逐行核对)" || bad "MSG-02 owner 映射: $CHK"

# ---- MSG-03 内容数值(overdueDays, 含逾期负值) ----
OD=$(db "SELECT CONCAT(
  (SELECT overdue_days FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a1' LIMIT 1),'|',
  (SELECT overdue_days FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-b2' LIMIT 1),'|',
  (SELECT overdue_days FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a2' LIMIT 1))" | tail -1)
[ "$OD" = "3|2|-1" ] && ok "MSG-03 overdueDays: 3|2|-1(逾期为负, 内容数值精确)" || bad "MSG-03 overdueDays: '$OD' (期望 3|2|-1)"

# ---- MSG-04 去重口径 ----
act "$T_ADM" "POST /api/reminder/ensure-expiring" >/dev/null
N=$(db "SELECT COUNT(*) FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name LIKE 'pmxr-c-%'" | tail -1)
[ "$N" = "4" ] && ok "MSG-04 再触发去重: 仍 4 条(pending 存在即跳过)" || bad "MSG-04 去重失效: $N 条"

# ---- MSG-05 可见性隔离(名字集合精确 diff) ----
GA=$(list_names "$T_A"); GB=$(list_names "$T_B"); GI=$(list_names "$T_INTR")
[ "$GA" = "a1
a2" ] && ok "MSG-05 A(self) 可见集={a1,a2} 精确" || bad "MSG-05 A 可见集: '$GA' (期望 a1,a2)"
[ "$GB" = "b1
b2" ] && ok "MSG-05 B(self) 可见集={b1,b2} 精确" || bad "MSG-05 B 可见集: '$GB' (期望 b1,b2)"
[ -z "$GI" ] && ok "MSG-05 INTR(无客户) 0 条(越权泄露=无)" || bad "MSG-05 INTR 可见: '$GI' (期望空)"

# ---- MSG-06 角标计数 ----
PC=$(curl -s -m 15 "$BASE/api/reminder/pending-count" -H "Authorization: Bearer $T_A" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("data"))')
[ "$PC" = "2" ] && ok "MSG-06 A pending-count=2(与可见 pending 一致)" || bad "MSG-06 A pending-count=$PC (期望2)"

# ---- MSG-07 处理 + 当日不重建 ----
RID_A1=$(db "SELECT r.id FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a1' AND r.status='pending' LIMIT 1" | tail -1)
R=$(act "$T_A" "POST /api/reminder/$RID_A1/process")
[ "${R%% *}" = "200" ] && ok "MSG-07 A 处理自己的提醒: 200" || bad "MSG-07 处理失败: $R"
ST=$(db "SELECT CONCAT(status,'|',IF(processed_at IS NULL,'null','set')) FROM biz_reminder WHERE id=$RID_A1" | tail -1)
[ "$ST" = "processed|set" ] && ok "MSG-07 状态 processed + processedAt 落库" || bad "MSG-07 状态: $ST"
act "$T_ADM" "POST /api/reminder/ensure-expiring" >/dev/null
N_A1=$(db "SELECT COUNT(*) FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a1'" | tail -1)
N_A2=$(db "SELECT COUNT(*) FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a2' AND r.status='pending'" | tail -1)
[ "$N_A1" = "1" ] && ok "MSG-07 当日不重建(a1 仍 1 条, processedToday 口径)" || bad "MSG-07 a1 重建了: $N_A1 条"
[ "$N_A2" = "1" ] && ok "MSG-07 其余客户不受影响(a2 仍 pending)" || bad "MSG-07 a2 异常: $N_A2"

# ---- MSG-08 处理守卫(对象级) ----
RID_A2=$(db "SELECT r.id FROM biz_reminder r JOIN biz_customer c ON r.customer_id=c.id WHERE c.customer_name='pmxr-c-a2' AND r.status='pending' LIMIT 1" | tail -1)
R=$(act "$T_B" "POST /api/reminder/$RID_A2/process")
case "$R" in *"资源不存在或无权访问"*|*"无权"*|403*) ok "MSG-08 B 处理 A 的提醒: 拒绝($R)";; *) bad "MSG-08 对象守卫未拦: $R";; esac

# ---- MSG-09 ensure 权限收紧 ----
R=$(act "$T_A" "POST /api/reminder/ensure-expiring")
[ "${R%% *}" = "403" ] && ok "MSG-09 非 admin 触发 ensure: 403(P1-09 收紧生效)" || bad "MSG-09 ensure 未收紧: $R"

echo "==> 消息域回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
#!/usr/bin/env bash
# 权限矩阵运行时回归(2026-09-21 权限体系优化 P2)
# 核对: 1) 每个角色 /api/auth/info 生效页面权限码 == 库内 type=2 绑定集
#       2) 勾选/撤销往返(403→200→403) + 数据范围 self/all 行为差
#       3) sys_role_menu 无孤儿绑定
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-perm-matrix.sh
# 依赖: ssh 别名 aigicrm-test 免密; 服务器 ~/aigicrm/.env 提供 DB_PASSWORD(不在本脚本出现)。
set -uo pipefail

BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_USER="${AIGI_TEST_USER:-admin}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS(测试账号密码, 禁止硬编码)}"
TMP_USER="perm-matrix-bot"
TMP_PW='PermMatrix#2609'
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

# 密钥只在服务器侧 .env 展开, 经 docker exec -e 注入容器; SQL 走 stdin, 不拼远程命令
db() {
  printf 'SET NAMES utf8mb4;\n%s\n' "$1" \
    | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null
}

login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}

cleanup() {
  db "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='$TMP_USER');
      DELETE FROM sys_user WHERE username='$TMP_USER';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='perm_matrix_tmp');
      DELETE FROM sys_role WHERE role_code='perm_matrix_tmp';" >/dev/null
}
trap cleanup EXIT
cleanup

HASH=$(PM_PW="$TMP_PW" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'权限矩阵回归-临时','perm_matrix_tmp','自动验证,即删','self',1);
    INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required) VALUES ('$TMP_USER','$HASH','权限矩阵机器人',1,0);" >/dev/null
BOT_UID=$(db "SELECT id FROM sys_user WHERE username='$TMP_USER'" | tail -1)
BOT_RID=$(db "SELECT id FROM sys_role WHERE role_code='perm_matrix_tmp'" | tail -1)
# 【G4 2026-09-21】租户插件已开启: 无部门用户无集团归属 → tenant 条件恒假(fail-closed 实测),
# 夹具必须给机器人挂集团1根部门, 否则 scope=all 用例恒 0
G1_DEPT=$(db "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
db "UPDATE sys_user SET org_id=$G1_DEPT, dept_id=$G1_DEPT WHERE id=$BOT_UID;" >/dev/null
[ -n "$BOT_UID" ] && [ -n "$BOT_RID" ] || { bad "临时角色/用户创建失败"; exit 1; }
ok "临时载体就绪(uid=$BOT_UID rid=$BOT_RID)"

# ---- 1) 每个角色: 生效页面权限码与库内绑定一致 ----
while IFS= read -r ROLE; do
  [ -n "$ROLE" ] || continue
  db "DELETE FROM sys_user_role WHERE user_id=$BOT_UID;
      INSERT INTO sys_user_role (user_id, role_id) VALUES ($BOT_UID, $ROLE);
      UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
  T=$(login_token "$TMP_USER" "$TMP_PW")
  [ -n "$T" ] || { bad "角色$ROLE 登录失败"; continue; }
  curl -s -m 15 "$BASE/api/auth/info" -H "Authorization: Bearer $T" > /tmp/pm-info.json
  db "SELECT DISTINCT m.permission FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id
      WHERE rm.role_id=$ROLE AND m.menu_type=2 AND m.permission<>'' ORDER BY 1;" | tail -n +2 | sort > /tmp/pm-db.txt
  python3 -c 'import json
api, stack = set(), list((json.load(open("/tmp/pm-info.json")).get("data") or {}).get("menus") or [])
while stack:
    n = stack.pop()
    if n.get("permission") and n.get("menuType") == 2: api.add(n["permission"])
    stack.extend(n.get("children") or [])
print("\n".join(sorted(api)))' > /tmp/pm-api.txt
  if diff -q /tmp/pm-db.txt /tmp/pm-api.txt >/dev/null; then
    ok "角色$ROLE 生效页面权限码与库内一致($(wc -l < /tmp/pm-api.txt | tr -d " ") 项)"
  else
    bad "角色$ROLE 权限码不一致 api=$(paste -sd, /tmp/pm-api.txt) db=$(paste -sd, /tmp/pm-db.txt)"
  fi
done < <(db "SELECT id FROM sys_role WHERE status=1 AND role_code<>'perm_matrix_tmp'" | tail -n +2)

# ---- 2) 勾选/撤销往返 + 数据范围行为(空白临时角色) ----
db "DELETE FROM sys_user_role WHERE user_id=$BOT_UID;
    INSERT INTO sys_user_role (user_id, role_id) VALUES ($BOT_UID, $BOT_RID);
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
MENU_ID=$(db "SELECT id FROM sys_menu WHERE permission='customer:view' AND menu_type=2 AND status=1 ORDER BY id LIMIT 1" | tail -1)
T0=$(login_token "$TMP_USER" "$TMP_PW")
C0=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $T0")
[ "$C0" = "403" ] && ok "未勾选 → 403" || bad "未勾选期望403 实得 $C0"

db "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($BOT_RID, $MENU_ID);
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
T1=$(login_token "$TMP_USER" "$TMP_PW")
R1=$(curl -s -m 15 "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $T1" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("code"), (d.get("data") or {}).get("total"))')
[ "$R1" = "200 0" ] && ok "勾选+self → 200 且 total=0" || bad "勾选+self 期望 '200 0' 实得 '$R1'"

db "UPDATE sys_role SET data_scope='all' WHERE id=$BOT_RID;
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
T2=$(login_token "$TMP_USER" "$TMP_PW")
TOTAL_ALL=$(curl -s -m 15 "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $T2" \
  | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("total"))')
if [ -n "$TOTAL_ALL" ] && [ "$TOTAL_ALL" != "0" ] && [ "$TOTAL_ALL" != "None" ]; then
  ok "scope=all → total=$TOTAL_ALL (>0)"
else
  bad "scope=all 期望>0 实得 '$TOTAL_ALL'"
fi

db "DELETE FROM sys_role_menu WHERE role_id=$BOT_RID AND menu_id=$MENU_ID;
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
T3=$(login_token "$TMP_USER" "$TMP_PW")
C3=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $T3")
[ "$C3" = "403" ] && ok "撤销勾选 → 403" || bad "撤销期望403 实得 $C3"

# ---- 3) 绑定卫生 ----
ORPHAN=$(db "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE m.id IS NULL" | tail -1)
[ "$ORPHAN" = "0" ] && ok "sys_role_menu 无孤儿绑定" || bad "发现 $ORPHAN 条孤儿绑定"

# ---- 4) 权限审计批次1 负向用例: finance 对象校验 + task 参数 IDOR(需后端含 2026-09-21 批次1 修复) ----
db "UPDATE sys_role SET data_scope='self' WHERE id=$BOT_RID;
    DELETE FROM sys_role_menu WHERE role_id=$BOT_RID;
    INSERT INTO sys_role_menu (role_id, menu_id) SELECT $BOT_RID, id FROM sys_menu WHERE id IN (11,41,128,136);
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
FT=$(login_token "$TMP_USER" "$TMP_PW")
ADMIN_ID=$(db "SELECT id FROM sys_user WHERE username='$API_USER'" | tail -1)
TARGET_CID=$(db "SELECT id FROM biz_customer WHERE owner_id=$ADMIN_ID AND deleted=0 LIMIT 1" | tail -1)
# 服务层归属拒绝走 BusinessException, HTTP 可能是 400/403 —— 以"拒绝且原因为权限"为准, 与"发票不存在"(=已穿透服务层)区分
FB=$(curl -s -m 15 -X POST "$BASE/api/customer/$TARGET_CID/invoices/999999/confirm" -H "Authorization: Bearer $FT" -H "Origin: $BASE")
if printf '%s' "$FB" | grep -q "无权"; then
  ok "finance confirm 范围外客户 → 拒绝(对象校验生效: $FB)"
else
  bad "finance confirm 未被权限拒绝(疑似穿透服务层): $FB"
fi
TC1=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/task/list?assigneeId=$ADMIN_ID&size=1" -H "Authorization: Bearer $FT")
[ "$TC1" = "403" ] && ok "task 他人 assigneeId → 403(IDOR已收口)" || bad "task 他人期望403 实得 $TC1(后端未含批次1修复?)"
TC2=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/task/list?assigneeId=$BOT_UID&size=1" -H "Authorization: Bearer $FT")
[ "$TC2" = "200" ] && ok "task 本人 assigneeId → 200" || bad "task 本人期望200 实得 $TC2"

# ---- 5) G2 统计口径: scope=self 的用户 coveredCount 必须 < admin(修复前全员口径无过滤) ----
AT=$(login_token "$API_USER" "$API_PASS")
AC=$(curl -s -m 20 "$BASE/api/statistics/indicator-stats" -H "Authorization: Bearer $AT" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("coveredCount"))')
db "UPDATE sys_role SET data_scope='self' WHERE id=$BOT_RID;
    DELETE FROM sys_role_menu WHERE role_id=$BOT_RID;
    INSERT INTO sys_role_menu (role_id, menu_id) SELECT $BOT_RID, id FROM sys_menu WHERE id IN (21);
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
BT=$(login_token "$TMP_USER" "$TMP_PW")
BC=$(curl -s -m 20 "$BASE/api/statistics/indicator-stats" -H "Authorization: Bearer $BT" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("coveredCount"))')
if [ -n "$AC" ] && [ -n "$BC" ] && [ "$BC" -lt "$AC" ] 2>/dev/null; then
  ok "统计口径: self范围 coveredCount=$BC < admin=$AC(数据范围生效)"
else
  bad "统计口径失效: admin=$AC bot=$BC(全员口径未过滤?)"
fi

# ---- 6) G4 双集团夹具: 集团2客户对集团1用户必须不可见(租户隔离活体) ----
AT2=$(login_token "$API_USER" "$API_PASS")
BASE_TOTAL=$(curl -s -m 20 "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $AT2" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("total"))')
db "INSERT IGNORE INTO sys_group (id, group_name, group_code, description, status) VALUES (9901,'审计夹具集团','pmx_g2','矩阵回归夹具,即删',1);
    INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES (9901, 0, '夹具集团2根部门', 1, 1);
    SET @g2d = LAST_INSERT_ID();
    INSERT INTO biz_customer (customer_name, tenant_id, owner_id, status, deleted, created_at) VALUES ('夹具-集团2隔离客户', 9901, $ADMIN_ID, 'normal', 0, NOW());" >/dev/null
WITH_G2=$(curl -s -m 20 "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $AT2" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("total"))')
if [ "$BASE_TOTAL" = "$WITH_G2" ]; then
  ok "双集团隔离: 集团2客户不进入集团1列表(total $BASE_TOTAL 前后一致)"
else
  bad "双集团隔离失效: 基线=$BASE_TOTAL 插入集团2客户后=$WITH_G2"
fi
db "DELETE FROM biz_customer WHERE tenant_id=9901; DELETE FROM sys_dept WHERE group_id=9901; DELETE FROM sys_group WHERE id=9901;" >/dev/null

# ---- 7) G4 导出负向(#13): scope=self 用户导出不得突破 view 范围(xlsx 数据量 < admin) ----
db "UPDATE sys_role SET data_scope='self' WHERE id=$BOT_RID;
    DELETE FROM sys_role_menu WHERE role_id=$BOT_RID;
    INSERT INTO sys_role_menu (role_id, menu_id) SELECT $BOT_RID, id FROM sys_menu WHERE id IN (11, 43);
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
BT2=$(login_token "$TMP_USER" "$TMP_PW")
curl -s -m 30 -w "%{http_code} %{size_download}" "$BASE/api/customer/export" -H "Authorization: Bearer $AT2" -o /tmp/pm-admin.xlsx >/dev/null
BOT_EXP=$(curl -s -m 30 -w "%{http_code} %{size_download}" "$BASE/api/customer/export" -H "Authorization: Bearer $BT2" -o /tmp/pm-bot.xlsx)
BOT_EXP_CODE=${BOT_EXP%% *}; BOT_EXP_SIZE=${BOT_EXP##* }
XS_ADMIN=$(python3 -c 'import zipfile,os
try:
    z=zipfile.ZipFile("/tmp/pm-admin.xlsx"); print(sum(zi.file_size for zi in z.infolist()))
except Exception: print(0)')
XS_BOT=$(python3 -c 'import zipfile,os
try:
    z=zipfile.ZipFile("/tmp/pm-bot.xlsx"); print(sum(zi.file_size for zi in z.infolist()))
except Exception: print(0)')
if [ "$BOT_EXP_CODE" = "200" ] && [ "$BOT_EXP_SIZE" -gt 0 ] 2>/dev/null && [ "$XS_BOT" -lt "$XS_ADMIN" ] 2>/dev/null; then
  ok "导出随 view 范围收口: self 导出 HTTP=$BOT_EXP_CODE xlsx=$XS_BOT 字节 < admin=$XS_ADMIN(有权限且范围内, 非权限拒绝)"
elif [ "$BOT_EXP_CODE" != "200" ]; then
  bad "导出异常: HTTP=$BOT_EXP_CODE(有权限仍失败?)"
else
  bad "导出疑似突破范围或空响应: bot=$XS_BOT admin=$XS_ADMIN"
fi
rm -f /tmp/pm-admin.xlsx /tmp/pm-bot.xlsx

echo "==> 权限矩阵回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]

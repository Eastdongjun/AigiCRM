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

echo "==> 权限矩阵回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]

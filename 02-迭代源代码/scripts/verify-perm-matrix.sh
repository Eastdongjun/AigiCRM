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
# 【T0 整改】临时bot口令改运行期随机生成(原为硬编码字面量, 被新 p8 形态抓获)
TMP_PW="PM$(date +%s)$RANDOM"
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
  # 【七档矩阵 2026-11-22】pmx7 夹具回收(幂等; 顺序: 授权→数据→人→角色→组织树)
  db "DELETE FROM sys_role_org WHERE role_id IN (SELECT id FROM sys_role WHERE role_code IN ('perm_matrix_tmp','pmx7_stack'));
      DELETE FROM biz_customer WHERE customer_name LIKE 'pmx7-c-%';
      DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmx7\_%');
      DELETE FROM role_perm_scope WHERE assignee_type='USER' AND CAST(assignee_key AS UNSIGNED) IN (SELECT id FROM sys_user WHERE username LIKE 'pmx7\_%');
      DELETE FROM sys_user WHERE username LIKE 'pmx7\_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='pmx7_stack');
      DELETE FROM sys_role WHERE role_code='pmx7_stack';
      DELETE FROM sys_dept WHERE dept_name LIKE 'pmx7\_%';" >/dev/null
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

# ---- 0) 可观测性端点姿态(2026-09-22 发布成熟度④): 公网不暴露 actuator + 容器内 health UP无细节 + metrics 认证保护 ----
# 层次: 测试服 nginx 不路由 /actuator(公网落前端兜底 HTML)= 安全收敛,属期望;
#       后端真实姿态在容器内直验(68d62733 可观测性批次的部署后复验)。
HC=$(curl -s -m 15 "$BASE/actuator/health")
if echo "$HC" | grep -q '"status":"UP"'; then
  bad "actuator 公网可达: $HC(nginx 未屏蔽 /actuator, 期望仅容器内可见)"
else
  ok "actuator 公网不可达(nginx 收敛, 响应非后端 JSON)"
fi
IN_HC=$(ssh aigicrm-test 'docker exec $(docker ps --format "{{.Names}}" | grep -i backend | head -1) curl -s -m 5 -w "\n%{http_code}" http://localhost:8080/actuator/health' 2>/dev/null)
IN_CODE=$(echo "$IN_HC" | tail -1); IN_BODY=$(echo "$IN_HC" | head -1)
if [ "$IN_CODE" = "200" ] && echo "$IN_BODY" | grep -q '"status":"UP"'; then
  ok "health: 容器内 HTTP200 UP"
else
  bad "health: 容器内 HTTP=$IN_CODE body=$IN_BODY(期望 200 UP)"
fi
if echo "$IN_BODY" | grep -qE '"db"|"redis"|diskSpace'; then
  bad "health 泄漏组件细节: $IN_BODY(应仅 status/groups)"
else
  ok "health: 响应无组件细节"
fi
IN_MC=$(ssh aigicrm-test 'docker exec $(docker ps --format "{{.Names}}" | grep -i backend | head -1) curl -s -m 5 -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/metrics' 2>/dev/null)
if [ "$IN_MC" = "401" ] || [ "$IN_MC" = "403" ]; then
  ok "metrics: 匿名被拒(HTTP=$IN_MC, 认证保护生效)"
else
  bad "metrics: 匿名返回 HTTP=$IN_MC(期望 401/403)"
fi

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
# 【2026-11-22 修正】菜单绑定由硬编码 id(11,41,128,136——环境菜单 id 已漂移, 136 现为 customer:date:edit)
# 改为按权限码解析, 消除 id 漂移导致的假红
db "UPDATE sys_role SET data_scope='self' WHERE id=$BOT_RID;
    DELETE FROM sys_role_menu WHERE role_id=$BOT_RID;
    INSERT INTO sys_role_menu (role_id, menu_id) SELECT $BOT_RID, id FROM sys_menu
      WHERE menu_type=2 AND status=1 AND permission IN ('customer:view','customer:edit','task:view');
    UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
FT=$(login_token "$TMP_USER" "$TMP_PW")
ADMIN_ID=$(db "SELECT id FROM sys_user WHERE username='$API_USER'" | tail -1)
TARGET_CID=$(db "SELECT id FROM biz_customer WHERE owner_id=$ADMIN_ID AND deleted=0 LIMIT 1" | tail -1)
# 服务层归属拒绝走 BusinessException, HTTP 可能是 400/403 —— 以"拒绝且原因为权限"为准, 与参数校验错误(=已穿透服务层)区分
# 【死码清理 2026-11-08】原探针 POST /invoices/999999/confirm(逐笔确认端点已删)重锚到同守卫链的
# 阶段总额写端点 /stage-amounts(同一 assertCanManageFinance 且先于参数校验执行; 空 body 防穿透误写)
# 【2026-11-22 修正】拒绝判据扩至现行 PERM_DENIED 文案(「您的权限不支持此访问…」), 原「无权」已不匹配
FB=$(curl -s -m 15 -X POST "$BASE/api/customer/$TARGET_CID/stage-amounts" -H "Authorization: Bearer $FT" -H "Origin: $BASE" -H "Content-Type: application/json" -d '{}')
if printf '%s' "$FB" | grep -qE '无权|权限不支持|PERM_DENIED'; then
  ok "finance stage-amounts 范围外客户 → 拒绝(对象校验生效: $(printf '%s' "$FB" | head -c 120))"
else
  bad "finance stage-amounts 未被权限拒绝(疑似穿透服务层): $FB"
fi
TC1=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/task/list?assigneeId=$ADMIN_ID&size=1" -H "Authorization: Bearer $FT")
[ "$TC1" = "403" ] && ok "task 他人 assigneeId → 403(IDOR已收口)" || bad "task 他人期望403 实得 $TC1(后端未含批次1修复?)"
TC2=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$BASE/api/task/list?assigneeId=$BOT_UID&size=1" -H "Authorization: Bearer $FT")
[ "$TC2" = "200" ] && ok "task 本人 assigneeId → 200" || bad "task 本人期望200 实得 $TC2"

# ---- 5) G2 统计口径: scope=self 的用户 coveredCount 必须 < admin(修复前全员口径无过滤) ----
# 【2026-11-22 fail-loud】admin 凭据失效时显式报障并跳过 5/6/7(此前空 token → None 连坐三条假红/假绿)
AT=$(login_token "$API_USER" "$API_PASS")
if [ -z "$AT" ]; then
  bad "admin 测试凭据登录失败(AIGI_TEST_PASS 失效? 更新 ~/.aigicrm-deploy.env 后重跑)——第5/6/7段跳过"
fi
if [ -n "$AT" ]; then
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
fi  # end admin 凭据守卫(5/6/7)

# ---- 8) 【2026-11-22 全系统测试矩阵】七档数据范围 ID 集合 diff(手工表管抽查,本节管全量) ----
# 夹具: pmx7_ 前缀组织树(大区rgn→办事处off→部门d2→子部门d21; 隔壁办事处off2→部门d3) + 每人一条 pmx7-c-* 客户
# + 5 层 manager 链(t2..t6, 第6层 t6 必须不可见)。断言: 各档位下 keyword=pmx7-c- 拉回的客户名集合
# == 按档位语义推导的期望集合(精确 diff, 非条数比对)。口径源: OrgScopeResolver + DataScopeType(七档)。
PFX="pmx7-c-"
TG=$(db "SELECT id FROM sys_group WHERE id=1 AND status=1" | tail -1)
[ -n "$TG" ] || TG=1
mkuser() { # $1=username $2=dept_id $3=manager_id(可空)
  db "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id, manager_id)
      VALUES ('$1','$HASH','$1',1,0,${2:-NULL},${2:-NULL},${3:-NULL});" >/dev/null
}
uidof() { db "SELECT id FROM sys_user WHERE username='$1'" | tail -1; }
# 组织树(纯字面量父id分批插: MySQL 1093 禁止 INSERT VALUES 内同表子查询, 各 db 调用独立会话也不能用 LAST_INSERT_ID)
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$G1_DEPT,'pmx7_rgn',2,1);" >/dev/null
D_RGN=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_rgn'" | tail -1)
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$D_RGN,'pmx7_off',3,1),($TG,$D_RGN,'pmx7_off2',3,1);" >/dev/null
D_OFF=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_off'" | tail -1)
D_OFF2=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_off2'" | tail -1)
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$D_OFF,'pmx7_d2',4,1),($TG,$D_OFF2,'pmx7_d3',4,1);" >/dev/null
D_D2=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_d2'" | tail -1)
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$D_D2,'pmx7_d21',4,1);" >/dev/null
D_D21=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_d21'" | tail -1)
D_D3=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_d3'" | tail -1)
[ -n "$D_RGN" ] && [ -n "$D_OFF" ] && [ -n "$D_D2" ] && [ -n "$D_D21" ] && [ -n "$D_D3" ] || { bad "pmx7 组织树创建失败"; echo "==> 权限矩阵回归: PASS=$PASS FAIL=$FAIL"; exit 1; }
# 数据 owner。★开关隔离设计: bot 平时不挂任何下级(KEY_SUPERIOR_SEE_SUBORDINATE 默认开,
# view 类按 manager 链并集追加——bot 有下级会污染全部 org 档期望集); team 档单独临时挂链。
# b 链(b2→b3→…→b7)放在独立大区 pmx7_rgn2 下的 pmx7_db, 不进 rgn 子树 → org 档不被 b 客户污染;
# team 档时 b2 挂到 bot: b2..b6 为 bot 的 1..5 层下级(≤5层递归可见), b7 为第 6 层不可见探针。
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$G1_DEPT,'pmx7_rgn2',2,1);" >/dev/null
D_RGN2=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_rgn2'" | tail -1)
db "INSERT INTO sys_dept (group_id, parent_id, dept_name, dept_type, status) VALUES ($TG,$D_RGN2,'pmx7_db',4,1);" >/dev/null
D_DB=$(db "SELECT id FROM sys_dept WHERE dept_name='pmx7_db'" | tail -1)
mkuser pmx7_u_d2  "$D_D2"
mkuser pmx7_u_d21 "$D_D21"
mkuser pmx7_u_d3  "$D_D3"
mkuser pmx7_b2 "$D_DB"
mkuser pmx7_b3 "$D_DB" "$(uidof pmx7_b2)"
mkuser pmx7_b4 "$D_DB" "$(uidof pmx7_b3)"
mkuser pmx7_b5 "$D_DB" "$(uidof pmx7_b4)"
mkuser pmx7_b6 "$D_DB" "$(uidof pmx7_b5)"
mkuser pmx7_b7 "$D_DB" "$(uidof pmx7_b6)"
mkcust() { # customer_code 为 NOT NULL 无默认 + tenant 内唯一(uk_tenant_customer_code), 带运行时间戳防残留撞码
  db "INSERT INTO biz_customer (customer_name, customer_code, tenant_id, owner_id, status, deleted, created_at)
      VALUES ('$PFX$1','PMX7-$1-$(date +%s)',$TG,$2,'normal',0,NOW());" >/dev/null; }
mkcust bot  "$BOT_UID"
mkcust d2   "$(uidof pmx7_u_d2)"
mkcust d21  "$(uidof pmx7_u_d21)"
mkcust d3   "$(uidof pmx7_u_d3)"
mkcust b2 "$(uidof pmx7_b2)"; mkcust b3 "$(uidof pmx7_b3)"; mkcust b4 "$(uidof pmx7_b4)"
mkcust b5 "$(uidof pmx7_b5)"; mkcust b6 "$(uidof pmx7_b6)"; mkcust b7 "$(uidof pmx7_b7)"

# tier_check <标签> <档位> <bot所在dept> <期望客户名csv(不带前缀)> [custom行sql] [角色data_scope覆盖]
tier_check() {
  local label="$1" scope="$2" botdept="$3" expected="$4" custom_sql="${5:-}" role_scope="${6:-$2}"
  db "DELETE FROM sys_role_org WHERE role_id=$BOT_RID;
      UPDATE sys_role SET data_scope='$role_scope' WHERE id=$BOT_RID;
      UPDATE sys_user SET dept_id=$botdept, org_id=$botdept, manager_id=NULL WHERE id=$BOT_UID;" >/dev/null
  [ -n "$custom_sql" ] && db "$custom_sql" >/dev/null
  db "UPDATE sys_user SET perm_version=perm_version+1 WHERE id=$BOT_UID;" >/dev/null
  local T=$(login_token "$TMP_USER" "$TMP_PW")
  [ -n "$T" ] || { bad "$label: 登录失败"; return; }
  local GOT=$(curl -s -m 20 "$BASE/api/customer/list?current=1&size=200&keyword=$PFX" -H "Authorization: Bearer $T" \
    | python3 -c 'import json,sys
try:
  d=json.load(sys.stdin); recs=(d.get("data") or {}).get("records") or []
  print("\n".join(sorted(r["customerName"][len("pmx7-c-"):] for r in recs if str(r.get("customerName","")).startswith("pmx7-c-"))))
except Exception as e: print("PARSE_ERR:"+str(e))')
  if [ "${GOT%%$'\n'*}" = "PARSE_ERR:"* ] || [ "$GOT" = "PARSE_ERR:"* ]; then bad "$label: 响应解析失败 $GOT"; return; fi
  local EXP=$(printf '%s' "$expected" | tr ',' '\n' | sed '/^$/d' | sort)
  if [ "$GOT" = "$EXP" ]; then
    ok "$label: 集合精确匹配($(echo "$EXP" | grep -c .)项: $(echo "$EXP" | paste -sd, -))"
  else
    bad "$label: 集合不一致 期望[$(echo "$EXP" | paste -sd, -)] 实得[$(echo "$GOT" | paste -sd, -)]"
  fi
}
db "DELETE FROM sys_role_menu WHERE role_id=$BOT_RID;
    INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($BOT_RID, $MENU_ID);" >/dev/null
tier_check "self 档(仅本人)"        self   "$D_D21" "bot"
tier_check "dept 档(本部门及以下)"  dept   "$D_D21" "bot,d21"
tier_check "office 档(本办事处及以下)" office "$D_D21" "bot,d2,d21"
tier_check "region 档(本大区及以下)" region "$D_D21" "bot,d2,d21,d3"
tier_check "custom 档(off子树+d3不含子)" custom "$D_D21" "bot,d2,d21,d3" \
  "INSERT INTO sys_role_org (role_id, org_id, include_children) VALUES ($BOT_RID,$D_OFF,1),($BOT_RID,$D_D3,0);"
# 多角色叠加: self(角色1) + dept(角色2) → 取最宽 dept(bot@d2: {d2,d21}∪本人)
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES ($TG,'pmx7叠加','pmx7_stack','七档矩阵夹具,即删','dept',1);" >/dev/null
R2=$(db "SELECT id FROM sys_role WHERE role_code='pmx7_stack'" | tail -1)
db "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($R2, $MENU_ID);
    INSERT INTO sys_user_role (user_id, role_id) VALUES ($BOT_UID, $R2);" >/dev/null
tier_check "叠加 self+dept→dept(取最宽)" self "$D_D2" "bot,d2,d21"
db "DELETE FROM sys_user_role WHERE user_id=$BOT_UID AND role_id=$R2;" >/dev/null
# team 档: 临时把 b2 挂到 bot(其余链不变) → bot 下级=b2..b6(1..5层, ≤5层递归可见), b7=第6层不可见
db "UPDATE sys_user SET manager_id=$BOT_UID WHERE username='pmx7_b2';" >/dev/null
tier_check "team 档(5层链,第6层b7不可见)" team "$D_D21" "bot,b2,b3,b4,b5,b6"
# 【BUG-20261122-001 回归】用户级通配 team（R2-FABUSHI-SCOPE-001 同型配置）：
# 角色 data_scope=self + USER */*=team，用户级覆盖路径仍须 5 层截断（b7 第6层不可见）。
db "INSERT INTO role_perm_scope (role_code,assignee_type,assignee_key,module,action,scope_level,anchor)
    VALUES ('','USER','$BOT_UID','*','*','team','OWNER');" >/dev/null
tier_check "USER 通配 team(5层链,第6层b7不可见)" team "$D_D21" "bot,b2,b3,b4,b5,b6" "" self
db "DELETE FROM role_perm_scope WHERE assignee_type='USER' AND assignee_key='$BOT_UID' AND module='*' AND action='*';" >/dev/null
db "UPDATE sys_user SET manager_id=NULL WHERE username='pmx7_b2';" >/dev/null
tier_check "all 档(全量fixture可见)" all "$D_D21" "bot,d2,d21,d3,b2,b3,b4,b5,b6,b7"

echo "==> 权限矩阵回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]

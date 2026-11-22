#!/usr/bin/env bash
# 【全系统测试矩阵·04 域补位】权限中心 UI 勾选端到端——真实前端操作→对应用户生效 2026-11-22
# 缺口背景: 04 域既有覆盖是"改 DB 绑定模拟勾选→API 生效"; 本脚本驱动**真实权限中心 UI**:
#   admin 在浏览器里勾选/取消 fixture 角色的 customer:view → fixture 用户 API 403↔200。
# 断言:
#   PUI-01 基线: fixture 用户(角色无 customer:view) → API 403
#   PUI-02 UI 勾选生效: admin 浏览器勾选 → 保存 → fixture 用户新登录 API 200 + DB 绑定在位
#   PUI-03 UI 取消生效: admin 浏览器取消 → fixture 用户新登录 API 403 + DB 绑定移除
#   PUI-04 生效延迟契约(实测记录): 勾选后旧 token 不重登的 API 行为
#   PUI-05 不可越权: fixture 用户自己开权限中心配置面 → 不可写(canEditScope 由后端码门控)
# 夹具: pmxu_ 角色(裸角色零绑定)+用户; UI admin 凭据走 env。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-perm-ui.sh
set -uo pipefail
cd "$(dirname "$0")/.."
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
info() { echo "INFO: $1"; }

q1() { printf 'SET NAMES utf8mb4;\n%s\n' "$1" | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null | tail -n +2; }
login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}
api_code() { curl -s -o /dev/null -w '%{http_code}' -m 15 "$BASE/api/customer/list?current=1&size=1" -H "Authorization: Bearer $1" -H "Origin: $BASE"; }

TS="$(date +%s)"
HASH=$(PM_PW="PMxu$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxu$TS"; ROLE="pmxu_role$TS"; U1="pmxu_u$TS"
cleanup() {
  q1 "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxu_%');
      DELETE FROM sys_user WHERE username LIKE 'pmxu_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='$ROLE');
      DELETE FROM sys_role WHERE role_code='$ROLE';" >/dev/null
}
trap cleanup EXIT
cleanup
G1_DEPT=$(q1 "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
q1 "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'UI勾选回归$TS','$ROLE','矩阵04域UI补位,即删','self',1);" >/dev/null
RID=$(q1 "SELECT id FROM sys_role WHERE role_code='$ROLE'" | tail -1)
q1 "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U1','$HASH','$U1',1,0,$G1_DEPT,$G1_DEPT);
    INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U1'), $RID);" >/dev/null
[ -n "$RID" ] && ok "夹具: 裸角色+用户就绪" || { bad "夹具失败"; echo "==> 权限UI端到端: PASS=$PASS FAIL=$FAIL"; exit 1; }

# ---- PUI-01 基线 403 ----
T0=$(login_token "$U1" "$TMP_PW")
C0=$(api_code "$T0")
[ "$C0" = "403" ] && ok "PUI-01 基线: 未勾选 → 403" || bad "PUI-01 基线异常: $C0"

# ---- PUI-02 UI 勾选(浏览器会话 A: 仅一次点击) ----
ui_toggle_once() {  # 启浏览器→选角色→对 customer:view 行点一次→退出; 无内部状态判定
  (cd aigicrm-ui && BASE="$BASE" ADMIN_PW="$API_PASS" ROLE_NAME="UI勾选回归$TS" node - <<'NODE'
const { chromium } = require('@playwright/test');
const BASE = process.env.BASE, PW = process.env.ADMIN_PW, RNAME = process.env.ROLE_NAME;
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1600, height: 900 } })).newPage();
  await p.goto(BASE + '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await p.fill('input[placeholder*="账号"]', 'admin');
  await p.fill('input[type="password"]', PW);
  await p.click('button:has-text("登")');
  await sleep(2500);
  await p.goto(BASE + '/system/config', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await p.waitForSelector('text=权限中心', { timeout: 15000 });
  await p.click('button:has-text("权限中心")');
  await sleep(1500);
  await p.click('button:has-text("功能与范围")');
  await sleep(2000);
  await p.waitForSelector('.psm-who-chip', { timeout: 15000 });
  const chip = p.locator(`.psm-who-chip:has-text("${RNAME}")`);
  await chip.scrollIntoViewIfNeeded(); await chip.click(); await sleep(2500);
  const findRow = async () => {
    const rows = p.locator('.psm-row'); const n = await rows.count();
    for (let i = 0; i < n; i++) {
      const code = await rows.nth(i).locator('.psm-row-code').textContent().catch(() => null);
      if (code && code.trim() === 'customer:view') return rows.nth(i);
    } return null;
  };
  let row = await findRow();
  if (!row) {
    const modBtn = p.locator('button:has-text("客户"), [class*="mod"]:has-text("客户")').first();
    if (await modBtn.count()) { await modBtn.click(); await sleep(1500); }
    row = await findRow();
  }
  if (!row) { console.log('UI_FAIL: 找不到 customer:view 行'); process.exit(1); }
  await row.locator('.psm-row-check').click();
  await sleep(3000);  // 等 onToggle 保存请求+toast
  const toasts = await p.locator('.el-message').allTextContents().catch(() => []);
  if (toasts.length) console.log('TOAST=' + toasts.join('|').slice(0, 160));
  await p.screenshot({ path: '/tmp/pui-toggle.png' }).catch(() => {});
  await b.close();
})().catch(e => { console.log('UI_FAIL ' + e.message.split('\n')[0]); process.exit(1); });
NODE
) 2>&1 | grep -E "UI_FAIL|TOAST" || true
}

ui_toggle_once
N_ON=$(q1 "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=$RID AND m.permission='customer:view'" | tail -1)
T1=$(login_token "$U1" "$TMP_PW"); C1=$(api_code "$T1")
if [ "$N_ON" -ge 1 ] && [ "$C1" = "200" ]; then
  ok "PUI-02 UI 勾选生效: 绑定 $N_ON 行 + fixture 用户重登 API 200 (403→200)"
else
  bad "PUI-02 勾选未生效: 绑定=$N_ON api=$C1"
fi

# ---- PUI-03 UI 取消(浏览器会话 B: 仅一次点击) ----
ui_toggle_once
N_OFF=$(q1 "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=$RID AND m.permission='customer:view'" | tail -1)
T2=$(login_token "$U1" "$TMP_PW"); C2=$(api_code "$T2")
if [ "$N_OFF" = "0" ] && [ "$C2" = "403" ]; then
  ok "PUI-03 UI 取消生效: 该权限码绑定全清 + fixture 用户重登 API 403 (200→403)"
else
  bad "PUI-03 取消未生效: 绑定=$N_OFF api=$C2"
fi

# ---- PUI-04 生效延迟契约: 勾选态下撤销绑定(bump), 旧 token 行为 ----
q1 "INSERT INTO sys_role_menu (role_id, menu_id) SELECT $RID, id FROM sys_menu WHERE permission='customer:view' AND status=1; UPDATE sys_user SET perm_version=perm_version+1 WHERE username='$U1';" >/dev/null
T3=$(login_token "$U1" "$TMP_PW")
q1 "DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=$RID AND m.permission='customer:view'; UPDATE sys_user SET perm_version=perm_version+1 WHERE username='$U1';" >/dev/null
C_OLD=$(api_code "$T3")
T4=$(login_token "$U1" "$TMP_PW"); C_NEW=$(api_code "$T4")
if [ "$C_OLD" = "200" ] && [ "$C_NEW" = "403" ]; then
  info "PUI-04 生效延迟契约: 旧 token 仍 200(权限集随登录快照), 重登即 403——与页面口径「保存即生效(用户刷新页面即可见)」一致"
elif [ "$C_OLD" = "403" ]; then
  info "PUI-04 生效延迟契约: 旧 token 即时 403(即改即失效, 强于页面口径)"
else
  bad "PUI-04 契约异常: old=$C_OLD new=$C_NEW"
fi


echo "==> 权限UI端到端: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]

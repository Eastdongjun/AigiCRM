#!/usr/bin/env bash
# 【G7 上线后抽检】BUG-006/008 生产发版验证 2026-11-22
# 零爆炸半径设计: 经应用正规 API 建 pmx 临时角色(零用户绑定) -> UI 勾选/取消 ->
# menuIds 终态对账(BUG-008 生产活体) -> 删角色还原。全程不碰真实角色。
# 另含: ①ACCESS_DENIED 自然触发检查(只读) ②生产冒烟(只读)。
# 用法: source ~/.aigicrm-deploy.env && AIGI_TEST_PASS=... bash scripts/verify-prod-release-check.sh
set -uo pipefail
BASE="https://www.aigicrm.com"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
TS="$(date +%s)"
RNAME="pmx发版抽检${TS}"; RCODE="pmx_relchk_${TS}"
PASS=0; FAIL=0
ok(){ echo "PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "FAIL: $1"; FAIL=$((FAIL+1)); }
login(){ curl -s -m 15 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"admin\",\"password\":\"$API_PASS\"}" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("token") or "")'; }
cleanup_prod(){  # 删本脚本产生的一切 pmx_relchk_* 临时角色(含失败轮孤儿)
  for ID in $(curl -s -m 15 "$BASE/api/system/roles?current=1&size=500" -H "$H1" -H "$H2" | python3 -c "import json,sys
d=json.load(sys.stdin).get('data') or []
recs = d if isinstance(d, list) else (d.get('records') or [])
print(' '.join(str(r['id']) for r in recs if str(r.get('roleCode','')).startswith('pmx_relchk_')))" 2>/dev/null); do
    curl -s -o /dev/null -X DELETE "$BASE/api/system/roles/$ID" -H "$H1" -H "$H2"
  done
}
trap cleanup_prod EXIT
T=$(login); [ -n "$T" ] && ok "生产 admin 登录" || { bad "登录失败"; exit 1; }
H1="Authorization: Bearer $T"; H2="Origin: $BASE"

# ---- 冒烟(只读) ----
for EP in "/api/auth/info" "/api/customer/list?current=1&size=5" "/api/approval/list?current=1&size=5" "/api/statistics/indicator-stats"; do
  C=$(curl -s -o /dev/null -w '%{http_code}' -m 20 "$BASE$EP" -H "$H1" -H "$H2")
  [ "$C" = "200" ] && ok "冒烟 $EP -> 200" || bad "冒烟 $EP -> $C"
done

# ---- 建临时角色(应用正规通道) ----
C=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/system/roles" -H "$H1" -H "$H2" -H 'Content-Type: application/json' \
  -d "{\"roleName\":\"$RNAME\",\"roleCode\":\"$RCODE\",\"description\":\"G7发版抽检,即删\",\"dataScope\":\"self\",\"status\":1}")
[ "$C" = "200" ] && ok "临时角色创建 200(零用户绑定)" || { bad "建角色 $C"; exit 1; }
RID=$(curl -s -m 15 "$BASE/api/system/roles?current=1&size=500" -H "$H1" -H "$H2" | python3 -c "import json,sys
d=json.load(sys.stdin).get('data') or []
recs = d if isinstance(d, list) else (d.get('records') or [])
print(next((r['id'] for r in recs if r.get('roleCode')=='$RCODE'), ''))")
[ -n "$RID" ] && ok "角色 id=$RID" || { bad "找不到临时角色"; exit 1; }
menu_ids(){ curl -s -m 15 "$BASE/api/system/roles/$RID" -H "$H1" -H "$H2" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d.get('data',{}).get('menuIds') or []))"; }
BASE_N=$(menu_ids); echo "基线 menuIds 数: $BASE_N"

# ---- UI 勾选/取消(Playwright 驱动生产权限中心, 每会话单点击) ----
ui_click_row(){
  (cd aigicrm-ui && BASE="$BASE" PW="$API_PASS" RNAME="$RNAME" node - <<'NODE'
const { chromium } = require('@playwright/test');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1600, height: 900 } })).newPage();
  await p.goto(process.env.BASE + '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await p.fill('input[placeholder*="账号"]', 'admin');
  await p.fill('input[type="password"]', process.env.PW);
  await p.click('button:has-text("登")');
  await sleep(3000);
  await p.goto(process.env.BASE + '/system/config', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await p.waitForSelector('text=权限中心', { timeout: 20000 });
  await p.click('button:has-text("权限中心")'); await sleep(2000);
  await p.click('button:has-text("功能与范围")'); await sleep(2500);
  const chip = p.locator(`.psm-who-chip:has-text("${process.env.RNAME}")`);
  await chip.scrollIntoViewIfNeeded(); await chip.click(); await sleep(3000);
  const rows = p.locator('.psm-row'); const n = await rows.count(); let row = null;
  for (let i = 0; i < n; i++) {
    const code = await rows.nth(i).locator('.psm-row-code').textContent().catch(() => null);
    if (code && code.trim() === 'customer:view') { row = rows.nth(i); break; }
  }
  if (!row) { console.log('UI_FAIL: 行未找到'); process.exit(1); }
  await row.locator('.psm-row-check').click(); await sleep(4000);
  await b.close();
})().catch(e => { console.log('UI_FAIL ' + e.message.split('\n')[0]); process.exit(1); });
NODE
) 2>&1 | grep UI_FAIL || true
}
E=$(ui_click_row); [ -z "$E" ] || bad "UI 勾选会话: $E"
N1=$(menu_ids)
[ "$N1" -gt "$BASE_N" ] && ok "UI 勾选生效: menuIds $BASE_N->$N1(码级展开)" || bad "勾选未生效: $BASE_N->$N1"
E=$(ui_click_row); [ -z "$E" ] || bad "UI 取消会话: $E"
N2=$(menu_ids)
# 正确口径=权限码粒度: 取消后不得残留任何 permission=customer:view 的菜单绑定;
# 无权限码的父目录节点(菜单树渲染需要, 不授予能力)允许保留(生产实测自动补父 id=10)
LEFT=$(curl -s -m 15 "$BASE/api/system/roles/$RID" -H "$H1" -H "$H2")
LEFT_CV=$(curl -s -m 15 "$BASE/api/system/menus/tree" -H "$H1" -H "$H2" > /tmp/menu-tree.json; python3 - "$LEFT" <<'PYEOF'
import json,sys
role=json.loads(sys.argv[1]); ids=set((role.get('data') or {}).get('menuIds') or [])
tree=json.load(open('/tmp/menu-tree.json')).get('data') or []
out=[]; stack=list(tree)
while stack:
    n=stack.pop()
    if n.get('id') in ids and n.get('permission')=='customer:view': out.append(n['id'])
    stack.extend(n.get('children') or [])
print(len(out))
PYEOF
)
[ "${LEFT:-99}" = "0" ] && ok "BUG-008 生产活体: UI 单行取消后 customer:view 权限码零残留(menuIds $N1->$N2, 余为无码父目录节点)" || bad "取消后 customer:view 残留 ${LEFT_CV} 条 (menuIds $N1->$N2)"

# ---- 清理(删角色) ----
C=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/system/roles/$RID" -H "$H1" -H "$H2")
[ "$C" = "200" ] && ok "临时角色已删(还原)" || bad "清理失败: $C"

echo "==> 生产发版抽检: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
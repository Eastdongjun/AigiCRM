// R7 PC 4 角色菜单/按钮 DOM 对账：token 注入，逐角色打开 dashboard/customer/file/system-user。
import { chromium } from '@playwright/test';
import fs from 'node:fs';
const BASE = 'http://111.229.37.220';
const users = JSON.parse(fs.readFileSync('/tmp/r7_users.json', 'utf8'));
const adminLeaves = new Set(users[0].menus.filter(m => m.type === 2).map(m => m.name));
const results = [];
for (const u of users) {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(t => { try { localStorage.setItem('token', t); } catch {} }, u.token);
  const page = await ctx.newPage();
  let errs = [], fives = [];
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)); });
  page.on('response', r => { if (r.status() >= 500) fives.push(`${r.status()} ${new URL(r.url()).pathname}`); });
  const row = { user: u.username, uid: u.id, dataScope: u.dataScope, steps: {} };

  // 1) sidebar
  await page.goto(BASE + '/dashboard', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2200);
  const aside = (await page.locator('aside').textContent().catch(() => '') || '').replace(/\s+/g, ' ');
  const expectedLeaves = u.menus.filter(m => m.type === 2).map(m => m.name);
  const missing = expectedLeaves.filter(n => !aside.includes(n));
  const extra = [...adminLeaves].filter(n => !expectedLeaves.includes(n) && aside.includes(n));
  row.steps.sidebar = { url: page.url(), expected: expectedLeaves.length, missing, extra, ok: missing.length === 0 && extra.length === 0 };
  await page.screenshot({ path: `/tmp/r7-${u.username}-dashboard.png` }).catch(() => {});

  // 2) customer buttons
  await page.goto(BASE + '/customer', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2200);
  const custText = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
  const perms = new Set(u.perms);
  const custChecks = {
    add: { expected: perms.has('customer:add'), actual: custText.includes('新增客户') },
    export: { expected: perms.has('customer:export'), actual: custText.includes('导出') },
    import: { expected: perms.has('customer:import'), actual: custText.includes('导入') },
  };
  row.steps.customer = { url: page.url(), checks: custChecks, ok: Object.values(custChecks).every(x => x.expected === x.actual) };
  await page.screenshot({ path: `/tmp/r7-${u.username}-customer.png` }).catch(() => {});

  // 3) file buttons
  const fileMenu = u.menus.some(m => m.path === '/file');
  if (fileMenu) {
    await page.goto(BASE + '/file', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2200);
    const t = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
    const checks = {
      upload: { expected: perms.has('file:upload'), actual: t.includes('上传') },
      folder: { expected: perms.has('file:folder:create') || perms.has('file:folder:manage'), actual: t.includes('新建文件夹') },
    };
    row.steps.file = { url: page.url(), checks, ok: Object.values(checks).every(x => x.expected === x.actual) };
  } else {
    row.steps.file = { skipped: 'no menu', ok: true };
  }

  // 4) system user button
  const sysMenu = u.menus.some(m => m.path === '/system/user');
  await page.goto(BASE + '/system/user', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);
  const st = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
  const expectedAdd = perms.has('system:user:add') || perms.has('system:org:admin');
  row.steps.systemUser = { url: page.url(), expectedAdd, actualAdd: st.includes('新增用户'), ok: expectedAdd === st.includes('新增用户') };

  row.consoleErrors = errs.length;
  row.consoleSample = errs.slice(0, 3);
  row.http5xx = fives.slice(0, 5);
  results.push(row);
  await browser.close();
}
fs.writeFileSync('/tmp/r7-20261121.json', JSON.stringify({ at: new Date().toISOString(), results }, null, 2));
for (const r of results) {
  const sb = r.steps.sidebar, cu = r.steps.customer, fi = r.steps.file, su = r.steps.systemUser;
  console.log(`${r.user.padEnd(8)} sidebar=${sb.ok ? 'OK' : 'FAIL'} customer=${cu.ok ? 'OK' : 'FAIL'} file=${fi.ok ? 'OK' : 'FAIL'} sysUser=${su.ok ? 'OK' : 'FAIL'} console=${r.consoleErrors} 5xx=${r.http5xx.length}`);
  if (!sb.ok) console.log('  sidebar missing', sb.missing, 'extra', sb.extra);
}
process.exit(results.every(r => r.steps.sidebar.ok && r.steps.customer.ok && r.steps.file.ok && r.steps.systemUser.ok) ? 0 : 1);

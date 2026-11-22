// L3 PC 只读页面冒烟：用本地签发的 admin JWT 注入 localStorage，绕过登录口令，
// 逐页打开文件/法布施/审批/回收站/系统页，记录 5xx、console error、最终 URL、可见文本与截图。
// 用法：AIGI_BASE=http://111.229.37.220 AIGI_TOKEN=<jwt> node /tmp/aigicrm-l3-token-smoke.mjs
import { chromium } from '@playwright/test';
import fs from 'node:fs';

const BASE = process.env.AIGI_BASE;
const TOKEN = process.env.AIGI_TOKEN;
if (!BASE || !TOKEN) { console.error('缺少 AIGI_BASE / AIGI_TOKEN'); process.exit(2); }

const routes = [
  ['dashboard', '/dashboard'],
  ['customer', '/customer'],
  ['customer-detail', '/customer/detail/624'],
  ['file', '/file'],
  ['fabushi-feed', '/fabushi/feed'],
  ['fabushi-library', '/fabushi/library'],
  ['approval', '/approval'],
  ['reminder', '/reminder'],
  ['recycle', '/system/recycle'],
  ['system-user', '/system/user'],
];

const browser = await chromium.launch();
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
await context.addInitScript(t => { try { localStorage.setItem('token', t); } catch {} }, TOKEN);
const page = await context.newPage();
const results = [];
let pending5xx = [];
let pendingConsole = [];
page.on('response', r => { if (r.status() >= 500) pending5xx.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`); });
page.on('console', m => { if (m.type() === 'error') pendingConsole.push(m.text().slice(0, 200)); });
page.on('pageerror', e => pendingConsole.push('pageerror: ' + String(e).slice(0, 200)));

for (const [name, path] of routes) {
  pending5xx = []; pendingConsole = [];
  let status = 'ok', note = '';
  try {
    await page.goto(BASE + path, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(1800);
    const url = page.url();
    const text = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    const shot = `/tmp/aigicrm-l3-${name}-20261121.png`;
    await page.screenshot({ path: shot, fullPage: false }).catch(() => {});
    if (/\/login|\/403/.test(url)) { status = 'fail'; note = 'redirected to ' + url; }
    else if (text.length < 80) { status = 'fail'; note = 'body text too short: ' + text.slice(0, 60); }
    if (pending5xx.length) { status = 'fail'; note += ' | 5xx: ' + pending5xx.join(', '); }
    results.push({ name, path, status, url, textLen: text.length, text: text.slice(0, 160), consoleErrors: pendingConsole.length, consoleSample: pendingConsole.slice(0, 3), http5xx: pending5xx.slice(0, 5), shot });
  } catch (e) {
    results.push({ name, path, status: 'error', note: String(e).slice(0, 200), http5xx: pending5xx.slice(0, 5), consoleErrors: pendingConsole.length });
  }
}
await browser.close();
const out = { base: BASE, at: new Date().toISOString(), results };
fs.writeFileSync('/tmp/aigicrm-l3-token-smoke-20261121.json', JSON.stringify(out, null, 2));
for (const r of results) console.log(`${r.status.toUpperCase().padEnd(5)} ${r.name.padEnd(16)} ${r.path} | text=${r.textLen ?? '-'} | console=${r.consoleErrors ?? '-'} | ${r.note || ''}`);
process.exit(results.every(r => r.status === 'ok') ? 0 : 1);

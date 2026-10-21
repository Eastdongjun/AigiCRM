// AIGICRM 回收站详情与彻底删除 UI 冒烟 —— 2026-10-18
// 【审计 F1 2026-10-21】移除内置站点/账号/口令, 三值经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node smoke-recycle-detail-20261018.mjs
import { chromium } from '@playwright/test';
function requireSmokeEnv(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`缺少环境变量 ${name}: 冒烟脚本已移除全部内置凭据(审计 F1), 请显式注入后重跑。`);
    process.exit(1);
  }
  return value;
}
const BASE = requireSmokeEnv('AIGICRM_SMOKE_BASE');
const USER = requireSmokeEnv('AIGICRM_SMOKE_USER');
const PASS = requireSmokeEnv('AIGICRM_SMOKE_PASS');
const results = [];
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
  if (!cond) process.exitCode = 1;
}
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1500, height: 950 } });
await page.goto(`${BASE}/login`);
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill(USER);
await inputs.nth(1).fill(PASS);
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });
await page.goto(`${BASE}/system/recycle`);
await page.waitForSelector('.recycle-table .el-table__row', { timeout: 15000 });

// 客户详情
await page.locator('.recycle-table .entity-cell.clickable').first().click();
await page.waitForSelector('.el-drawer', { timeout: 10000 });
await page.waitForTimeout(800);
const drawerText = (await page.locator('.el-drawer').innerText()).replace(/\s+/g, ' ');
ok('客户详情抽屉打开', drawerText.includes('客户详情'));
ok('客户详情含关联数据', drawerText.includes('关联数据') && drawerText.includes('发票'));
ok('客户详情底部有彻底删除', drawerText.includes('彻底删除'));
ok('客户详情底部有恢复', drawerText.includes('恢复'));
await page.screenshot({ path: '/tmp/recycle-detail-customer.png' });
await page.locator('.detail-footer').getByRole('button', { name: '关闭' }).click();
await page.waitForTimeout(600);

// 文件详情
await page.locator('.seg-item', { hasText: '已删除文件' }).click();
await page.waitForTimeout(1200);
await page.locator('.recycle-table .entity-cell.clickable').first().click();
await page.waitForSelector('.el-drawer', { timeout: 10000 });
await page.waitForTimeout(800);
const fileDrawer = (await page.locator('.el-drawer').innerText()).replace(/\s+/g, ' ');
ok('文件详情抽屉打开', fileDrawer.includes('文件详情'));
ok('文件详情含内容状态与大小', fileDrawer.includes('内容状态') && fileDrawer.includes('大小'));
ok('文件详情底部有彻底删除', fileDrawer.includes('彻底删除'));
await page.screenshot({ path: '/tmp/recycle-detail-file.png' });
await page.locator('.detail-footer').getByRole('button', { name: '关闭' }).click();

// 行内彻底删除按钮存在
await page.waitForTimeout(500);
const hardDeleteLinks = await page.locator('.el-table__row').first().getByText('彻底删除').count();
ok('行内彻底删除按钮可见', hardDeleteLinks > 0);

await browser.close();
console.log(results.join('\n'));
console.log(results.some(r => r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

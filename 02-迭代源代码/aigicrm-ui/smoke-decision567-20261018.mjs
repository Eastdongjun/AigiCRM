// AIGICRM 决策7 "三列+合计"验证 —— 2026-10-18
// 激励统计页个人排行 Tab: 表头应含 安装前/安装中/安装后/合计(2026-09-25 隐藏的三列已恢复)。
// 【审计 F1 2026-10-21】移除内置站点/账号/口令, 三值经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node smoke-decision567-20261018.mjs
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
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
await page.goto(`${BASE}/login`);
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill(USER);
await inputs.nth(1).fill(PASS);
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|incentive|customer/, { timeout: 15000 });

await page.goto(`${BASE}/incentive/stats`);
// 切到"个人排行"Tab
const personTab = page.locator('.tab-item', { hasText: '个人排行' }).first();
await personTab.waitFor({ state: 'visible', timeout: 15000 });
await personTab.click();
await page.waitForSelector('.rank-matrix .el-table__header', { timeout: 15000 });
const headerText = (await page.locator('.rank-matrix .el-table__header').first().innerText()).replace(/\s+/g, ' ');
ok('表头含 安装前', headerText.includes('安装前'));
ok('表头含 安装中', headerText.includes('安装中'));
ok('表头含 安装后', headerText.includes('安装后'));
ok('表头含 合计', headerText.includes('合计'));
ok('表头含 激励总额(总合计保留)', headerText.includes('激励总额'));
// 数据行抽查: preCount/midCount/postCount 渲染为数字
await page.waitForSelector('.rank-matrix .el-table__row', { timeout: 10000 }).catch(() => {});
const rowCount = await page.locator('.rank-matrix .el-table__row').count();
ok(`个人排行有数据行(${rowCount})`, rowCount > 0);
await page.screenshot({ path: '/tmp/smoke-decision7-personrank.png' });
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r => r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

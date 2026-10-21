// AIGICRM 回收站恢复链路 UI 冒烟。【审计 F1 2026-10-21】移除内置站点/账号/口令, 三值经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node smoke-recycle-restore-20261018.mjs
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
const ok = (n,c,x='') => { results.push(`${c?'✅':'❌'} ${n}${x?' — '+x:''}`); if(!c) process.exitCode=1; };
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(`${BASE}/login`);
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill(USER); await inputs.nth(1).fill(PASS);
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });
await page.goto(`${BASE}/system/recycle`);
await page.waitForSelector('.recycle-page');
await page.locator('input[placeholder*="搜索客户"]').first().fill('回收站功能验证客户');
await page.keyboard.press('Enter');
await page.waitForTimeout(1200);
const rows = await page.locator('.el-table__row').count();
ok('搜索命中测试客户', rows === 1, `rows=${rows}`);
await page.locator('.el-table__row').first().getByText('恢复', { exact: true }).click();
await page.waitForSelector('.el-message-box', { timeout: 8000 });
ok('确认弹窗出现', (await page.locator('.el-message-box').innerText()).includes('确认恢复'));
await page.locator('.el-message-box').getByRole('button', { name: '确认恢复' }).click();
await page.waitForTimeout(2000);
const success = await page.locator('.el-message--success').count();
ok('恢复成功提示', success > 0);
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r=>r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

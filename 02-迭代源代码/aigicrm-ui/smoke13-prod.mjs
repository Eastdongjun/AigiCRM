// AIGICRM ⑬ 生产空态/默认页签复验(生产站)。【审计 F1 2026-10-21】移除内置站点/账号/口令,
//   三值经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node smoke13-prod.mjs
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
function ok(n, c, note='') { results.push(`${c?'✅':'❌'} ${n}${note?' — '+note:''}`); if(!c) process.exitCode=1; }
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(`${BASE}/login`);
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill(USER);
await inputs.nth(1).fill(PASS);
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });
await page.goto(`${BASE}/customer`);
await page.waitForSelector('.el-tabs__item.is-active', { timeout: 15000 });
const tab = (await page.locator('.el-tabs__item.is-active').innerText()).trim();
ok('生产默认页签=全部', tab.startsWith('全部'), tab);
await page.waitForFunction(() => document.querySelectorAll('.el-table__row').length > 0, null, { timeout: 15000 });
ok('默认即见数据行', (await page.locator('.el-table__row').count()) > 0);
// 切成熟创新验空态文案
await page.locator('.el-tabs__item', { hasText: '成熟创新' }).first().click();
await page.waitForFunction(() => {
  const t = document.querySelector('.table-empty-text')?.textContent || '';
  return t.includes('分类下暂无客户') || t.includes('暂无');
}, null, { timeout: 10000 }).catch(()=>{});
const empty = (await page.locator('.table-empty-text').innerText().catch(()=>'' )).trim();
ok('成熟创新空态=该分类为空口径', empty.includes('分类下暂无客户'), empty);
ok('不再出现"联系管理员"', !empty.includes('联系管理员'));
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r=>r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

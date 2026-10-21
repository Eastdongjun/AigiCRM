// AIGICRM ⑬B(+A) 客户列表空态文案/默认页签修复验证 —— 2026-10-17
// 场景: NM005(王磊磊)成熟创新=0。
//   B 修复前: 默认页签空表 + "当前数据范围内暂无可见客户，请联系管理员检查数据范围"(误导成权限问题)
//   B 修复后: "「成熟创新」分类下暂无客户，可切换「全部」页签查看（共 N 个）"
//   A 修复后: 默认页签=「全部」(不再落在成熟创新)
// 附带回归: 搜索无匹配仍走"未找到与XX匹配"口径。
// 用法: AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> \
//        node smoke-empty-state-13b-20261017.mjs  (expectA=1 校验A,默认只验B)
// 【审计 F1 2026-10-21】移除内置站点/账号/口令(历史字面量已入 git 历史, 轮换由运维负责)。
import { chromium } from '@playwright/test';

const EXPECT_A = process.env.expectA === '1';
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
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const emptyTextEl = page.locator('.table-empty-text');

// 登录
await page.goto(`${BASE}/login`);
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill(USER);
await inputs.nth(1).fill(PASS);
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer|workbench|home/, { timeout: 15000 });

// 客户列表
await page.goto(`${BASE}/customer`);
await page.waitForSelector('.el-tabs__item.is-active', { timeout: 15000 });
const activeTab = (await page.locator('.el-tabs__item.is-active').innerText()).trim();
if (EXPECT_A) {
  ok('[A] 默认页签=全部', activeTab.startsWith('全部'), activeTab);
  await page.waitForFunction(() => document.querySelectorAll('.el-table__row').length > 0, null, { timeout: 15000 });
  ok('[A] 默认页签即见数据行(NM005 12 客户)', (await page.locator('.el-table__row').count()) > 0);
  // 切到成熟创新验证 B 文案
  await page.locator('.el-tabs__item', { hasText: '成熟创新' }).first().click();
} else {
  ok('[B前置] 默认页签=成熟创新(A未实施时)', activeTab.includes('成熟创新'), activeTab);
}

// 空态文案终态(等待 counts 回来后的含计数形态)
await emptyTextEl.waitFor({ state: 'visible', timeout: 15000 });
await page.waitForFunction(() => {
  const t = document.querySelector('.table-empty-text')?.textContent || '';
  return t.includes('分类下暂无客户') && /共 \d+ 个/.test(t);
}, null, { timeout: 10000 }).catch(() => {});
const emptyText = (await emptyTextEl.innerText()).trim();
ok('[B] 空态文案=该分类为空+全部页签计数', emptyText.includes('分类下暂无客户') && /共 \d+ 个/.test(emptyText), emptyText);
ok('[B] 不再出现"联系管理员"误导', !emptyText.includes('联系管理员'));
const m = emptyText.match(/共 (\d+) 个/);
const allCount = m ? Number(m[1]) : 0;

// 切「全部」页签
await page.locator('.el-tabs__item', { hasText: '全部' }).first().click();
await page.waitForFunction(() => document.querySelectorAll('.el-table__row').length > 0, null, { timeout: 15000 });
const rowCount = await page.locator('.el-table__row').count();
ok('切「全部」页签后行数=文案计数', rowCount === allCount, `行=${rowCount} 文案=${allCount}`);

// 回归: 搜索无匹配仍走搜索口径
await page.locator('input[placeholder*="搜索"], input[placeholder*="客户"]').first().fill('ZZZ不存在客户ZZZ');
await page.keyboard.press('Enter');
await page.waitForFunction(() => (document.querySelector('.table-empty-text')?.textContent || '').includes('未找到'), null, { timeout: 15000 }).catch(() => {});
const searchText = (await emptyTextEl.innerText()).trim();
ok('回归: 搜索无匹配仍走"未找到与XX匹配"口径', searchText.includes('未找到'), searchText);

await page.screenshot({ path: '/tmp/smoke-empty-state.png' });
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r => r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

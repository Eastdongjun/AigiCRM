import { chromium } from '@playwright/test';
const results = [];
function ok(n, c, note='') { results.push(`${c?'✅':'❌'} ${n}${note?' — '+note:''}`); if(!c) process.exitCode=1; }
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto('https://www.aigicrm.com/login');
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill('NM005');
await inputs.nth(1).fill('12345678');
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });
await page.goto('https://www.aigicrm.com/customer');
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

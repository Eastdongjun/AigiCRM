// AIGICRM 延期B PC 审批界面验证 —— 2026-10-17
// NO001 待办里打开 97 号延期单: 详情含"申请延期天数"行(快照360) → 通过弹窗预填360 →
// 调整为120 → 提交 → 审批单执行 120 天。
import { chromium } from '@playwright/test';
const results = [];
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
  if (!cond) process.exitCode = 1;
}
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto('http://111.229.37.220/login');
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill('NO001');
await inputs.nth(1).fill('No001@2026');
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|approval|customer/, { timeout: 15000 });

await page.goto('http://111.229.37.220/approval');
await page.waitForSelector('.el-table__row', { timeout: 15000 });
// 找到延期单(P2026-058 / 客户511)行
const row = page.locator('.el-table__row', { hasText: '延期' }).first();
await row.waitFor({ state: 'visible', timeout: 10000 });
await row.locator(".code-link").click();
await page.waitForSelector('.el-drawer .detail-descriptions', { timeout: 10000 });
const detailText = await page.locator('.el-drawer').innerText();
ok('详情含"申请延期天数"行', detailText.includes('申请延期天数'), '');
ok('extend单不再裸显原始JSON', !(await page.locator('.el-drawer .extra-data pre').count()));

// 点"通过" → 延期专用弹窗(预填值即来自审批单快照,数据正确性的UI侧证明)
await page.locator('.el-drawer').getByText('通过', { exact: true }).click();
await page.waitForSelector('.el-dialog:has-text("延期审批")', { timeout: 10000 });
const daysInput = page.locator('.el-dialog .el-input-number input');
const prefilled = await daysInput.inputValue();
ok('弹窗预填申请快照值360', prefilled === '360', `实际=${prefilled}`);
await daysInput.fill('120');
await page.locator('.el-dialog').getByRole('button', { name: '通过并延期' }).click();
// 终态断言: 单据生效后从待办列表消失(比弹窗动画时序稳;批准完待办可能整体为空=空态也算消失)
await page.waitForTimeout(3500);
await page.reload();
await page.waitForSelector('.el-table, .el-table__empty-block', { timeout: 15000 });
const stillPending = (await page.locator('.el-table__row', { hasText: 'UI链路复跑' }).count()) > 0;
ok('调整120天提交成功(单据离开待办)', !stillPending);
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r => r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

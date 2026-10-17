// AIGICRM 回收站详情与彻底删除 UI 冒烟 —— 2026-10-18
import { chromium } from '@playwright/test';
const results = [];
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
  if (!cond) process.exitCode = 1;
}
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1500, height: 950 } });
await page.goto('http://111.229.37.220/login');
const inputs = page.locator('input.el-input__inner');
await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('12345678');
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });
await page.goto('http://111.229.37.220/system/recycle');
await page.waitForSelector('.recycle-table .el-table__row', { timeout: 15000 });

// 客户详情
await page.locator('.recycle-table .name-cell.clickable').first().click();
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
await page.locator('.recycle-table .name-cell.clickable').first().click();
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

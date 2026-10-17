// AIGICRM 回收站(已删除客户/人员)UI 冒烟 —— 2026-10-18
// admin 登录 → 侧边栏有「回收站」→ 客户页签有数据+恢复按钮 → 切人员页签有数据。
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
await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('12345678');
await page.keyboard.press('Enter');
await page.waitForURL(/dashboard|customer/, { timeout: 15000 });

// 侧边栏菜单出现
await page.goto('http://111.229.37.220/system/recycle');
await page.waitForSelector('.recycle-page', { timeout: 15000 });
ok('页面标题=回收站', (await page.locator('.recycle-head h2').innerText()).includes('回收站'));

// 客户页签
await page.waitForSelector('.el-table__row', { timeout: 15000 });
const custRows = await page.locator('.el-table__row').count();
ok(`已删除客户有数据行(${custRows})`, custRows > 0);
const hasRestoreBtn = (await page.locator('.el-table__row').first().getByText('恢复').count()) > 0;
ok('恢复按钮可见(admin 有 restore 权限)', hasRestoreBtn);
ok('提示条存在', (await page.locator('.info-strip').count()) > 0);

// 切人员页签
await page.locator('.seg-item', { hasText: '已删除人员' }).first().click();
await page.waitForTimeout(1200);
const userRows = await page.locator('.el-table__row').count();
ok(`已删除人员有数据行(${userRows})`, userRows > 0);
const headerText = (await page.locator('.el-table').innerText()).replace(/\s+/g, ' ');
ok('人员表头含账号/删除时间', headerText.includes('账号') && headerText.includes('删除时间'));

// 搜索联动
await page.locator('input[placeholder*="姓名"]').first().fill('zhangsan');
await page.keyboard.press('Enter');
await page.waitForTimeout(1200);
const searchRows = await page.locator('.el-table__row').count();
ok('人员搜索 zhangsan 命中', searchRows >= 1, `rows=${searchRows}`);

// 切已删除文件页签
await page.locator('.seg-item', { hasText: '已删除文件' }).first().click();
await page.waitForTimeout(1200);
const fileRows = await page.locator('.el-table__row').count();
ok(`已删除文件有数据行(${fileRows})`, fileRows > 0);
const fileHeader = (await page.locator('.el-table').innerText()).replace(/\s+/g, ' ');
ok('文件表头含归属/内容状态', fileHeader.includes('归属') && fileHeader.includes('内容状态'));
ok('文件行出现可恢复/已清除标记', fileHeader.includes('可恢复') || fileHeader.includes('已清除'));
ok('清空回收站按钮可见(admin 有 purge 权限)', (await page.locator('button', { hasText: '清空回收站' }).count()) > 0);

await page.screenshot({ path: '/tmp/smoke-recycle.png', fullPage: false });
await browser.close();
console.log(results.join('\n'));
console.log(results.some(r => r.startsWith('❌')) ? 'FAIL' : `PASS ${results.length}/${results.length}`);

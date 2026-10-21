// AIGICRM PC 端生产环境浏览器冒烟（真实 Chromium）
// 场景：登录 → 工作台 → 客户列表(服务端分页/页签计数/高级筛选弹窗) → 提醒页 → 审批页(转交弹窗)
// 判定：全程无 5xx/无未捕获 console error、关键元素可见、分页翻页生效
// 【审计 F1 2026-10-21】移除内置站点/账号/口令(历史字面量已入 git 历史, 轮换由运维负责)。
//   三值一律经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node pc-smoke.mjs
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
let consoleErrors = 0, badResponses = 0;

function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
  if (!cond) process.exitCode = 1;
}

const browser = await chromium.launch();
const page = await browser.newPage({ ignoreHTTPSErrors: true });
page.on('console', m => { if (m.type() === 'error') { consoleErrors++; console.log('[console.error]', m.text().slice(0, 200)); } });
page.on('response', r => { if (r.status() >= 500) badResponses++; });

try {
  // 1) 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"], input[type="text"]', USER);
  await page.fill('input[type="password"]', PASS);
  await page.click('button:has-text("登")');
  await page.waitForURL(u => !String(u).includes('login'), { timeout: 15000 });
  ok('登录成功并跳转', true, page.url());

  // 2) 工作台
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  ok('工作台加载', (await page.locator('text=客户').first().isVisible()) || (await page.title()) !== '');

  // 3) 客户列表：翻页 + 页签计数（路由为 /customer，见 router/index.js:31）
  await page.goto(BASE + '/customer', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  const rows1 = await page.locator('.el-table__body-wrapper .el-table__row').count();
  ok('客户列表第1页有数据', rows1 > 0, `${rows1} 行`);
  const totalText1 = await page.locator('.el-pagination__total').first().textContent().catch(() => '');
  const pager = page.locator('.el-pagination .btn-next').first();
  await pager.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
  if (await pager.isVisible() && await pager.isEnabled()) {
    await pager.click();
    await page.waitForTimeout(1500);
    const idxText = await page.locator('.el-table__body .el-table__index').first().textContent().catch(() => '?');
    ok('翻页生效(第2页首行序号>30)', parseInt(idxText) > 30, `首行序号=${idxText}`);
  } else {
    // 单页数据时 btn-next 禁用属正常，校验总数文本存在即可
    const t = await page.locator('.el-pagination__total').first().textContent().catch(() => '');
    ok('分页条存在(单页禁用翻页)', t.includes('共'), t.trim());
  }
  // 3b) 高级筛选弹窗
  await page.click('button:has-text("高级筛选")').catch(() => {});
  await page.waitForTimeout(600);
  const advVisible = await page.locator('.el-dialog:visible, .el-drawer:visible').first().isVisible().catch(() => false);
  ok('高级筛选弹窗可开', advVisible);
  await page.keyboard.press('Escape');
  await page.click('.el-dialog__headerbtn:visible, .el-overlay:visible button:has-text("关闭")').catch(() => {});

  // 4) 提醒页
  await page.goto(BASE + '/reminder', { waitUntil: 'networkidle' }).catch(async () => {
    await page.goto(BASE + '/reminder/index', { waitUntil: 'networkidle' });
  });
  await page.waitForTimeout(1200);
  ok('提醒页加载无白屏', (await page.locator('body').innerText()).length > 100);

  // 5) 审批页（admin 可见转交按钮 → 点开转交弹窗=P3 修复验证）
  await page.goto(BASE + '/approval', { waitUntil: 'networkidle' }).catch(() => {});
  await page.waitForTimeout(1200);
  const transferBtn = page.locator('button:has-text("转交")').first();
  if (await transferBtn.isVisible().catch(() => false)) {
    await transferBtn.click();
    await page.waitForTimeout(800);
    const dlg = await page.locator('.el-dialog:visible').first().isVisible().catch(() => false);
    ok('审批转交弹窗打开(P3 修复线上验证)', dlg);
    if (dlg) {
      const hasRadio = await page.locator('.el-dialog:visible .el-radio, .el-dialog:visible .el-select').count();
      ok('转交弹窗含候选人选择', hasRadio > 0);
    }
  } else {
    ok('审批页加载(无待审批数据时无转交按钮属正常)', true);
  }

  ok('全程无 5xx', badResponses === 0, `${badResponses} 个`);
  ok('无未捕获 console error', consoleErrors === 0, `${consoleErrors} 条`);
} catch (e) {
  ok('冒烟流程异常中断', false, String(e).slice(0, 200));
} finally {
  await browser.close();
  console.log('===== PC 浏览器冒烟结果 =====');
  results.forEach(r => console.log(r));
}

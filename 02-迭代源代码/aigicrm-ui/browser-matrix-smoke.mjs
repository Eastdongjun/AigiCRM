// AIGICRM 生产站浏览器矩阵冒烟（真实引擎：webkit=Safari 引擎 / firefox）
// 以 pc-smoke.mjs 为蓝本，参数化 engine。只读操作：登录 / 工作台 / 客户列表翻一页 / 高级筛选弹窗开关 / 提醒页
// 断言：登录跳转成功、列表行数>0、弹窗可开、无 5xx；console error 如实计数（>0 抄前 3 条）
// 用法: AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> \
//       node browser-matrix-smoke.mjs <webkit|firefox|all>   （默认 all）
// 【审计 F1 2026-10-21】移除内置站点/账号/口令(历史字面量已入 git 历史, 轮换由运维负责)。
import { firefox, webkit } from '@playwright/test';

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
const arg = process.argv[2] || 'all';
const ENGINES = arg === 'all' ? ['webkit', 'firefox'] : [arg];

async function runSmoke(engineName) {
  const results = [];
  let consoleErrors = 0; const errorTexts = [];
  let badResponses = 0; const badUrls = [];
  let allPass = true;
  const ok = (name, cond, note = '') => {
    results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
    if (!cond) allPass = false;
  };

  const launchMap = { webkit, firefox };
  let browser;
  try {
    browser = await launchMap[engineName].launch();
  } catch (e) {
    ok(`引擎 ${engineName} launch`, false, '启动失败: ' + String(e).split('\n').slice(0, 3).join(' | ').slice(0, 300));
    return { engine: engineName, allPass: false, results, consoleErrors, errorTexts, badResponses, badUrls, launchFailed: true };
  }

  const page = await browser.newPage({ ignoreHTTPSErrors: true });
  page.on('console', m => {
    if (m.type() === 'error') {
      consoleErrors++;
      const t = m.text().slice(0, 200);
      console.log(`[${engineName}][console.error]`, t);
      if (errorTexts.length < 3) errorTexts.push(t);
    }
  });
  page.on('response', r => {
    if (r.status() >= 500) { badResponses++; if (badUrls.length < 3) badUrls.push(`${r.status()} ${r.url().slice(0, 120)}`); }
  });

  try {
    // 1) 登录
    await page.goto(BASE + '/login', { waitUntil: 'networkidle', timeout: 45000 });
    await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"], input[type="text"]', USER);
    await page.fill('input[type="password"]', PASS);
    await page.click('button:has-text("登")');
    await page.waitForURL(u => !String(u).includes('login'), { timeout: 20000 });
    ok('登录跳转成功', true, page.url());

    // 2) 工作台
    await page.goto(BASE + '/', { waitUntil: 'networkidle', timeout: 45000 });
    await page.waitForTimeout(1500);
    ok('工作台加载', (await page.locator('text=客户').first().isVisible().catch(() => false)) || (await page.title()) !== '', await page.title());

    // 3) 客户列表：第1页行数 + 翻一页（只读查询）
    await page.goto(BASE + '/customer', { waitUntil: 'networkidle', timeout: 45000 });
    await page.waitForTimeout(2000);
    const rows1 = await page.locator('.el-table__body-wrapper .el-table__row').count();
    ok('客户列表行数>0', rows1 > 0, `${rows1} 行`);
    const pager = page.locator('.el-pagination .btn-next').first();
    await pager.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
    if (await pager.isVisible().catch(() => false) && await pager.isEnabled()) {
      await pager.click();
      await page.waitForTimeout(2000);
      const idxText = await page.locator('.el-table__body .el-table__index').first().textContent().catch(() => '?');
      ok('翻页生效(第2页首行序号>30)', parseInt(idxText) > 30, `首行序号=${(idxText || '').trim()}`);
    } else {
      const t = await page.locator('.el-pagination__total').first().textContent().catch(() => '');
      ok('分页条存在(单页禁用翻页属正常)', (t || '').includes('共'), (t || '').trim());
    }

    // 3b) 高级筛选弹窗：开 → 关
    await page.click('button:has-text("高级筛选")').catch(() => {});
    await page.waitForTimeout(800);
    const advVisible = await page.locator('.el-dialog:visible, .el-drawer:visible').first().isVisible().catch(() => false);
    ok('高级筛选弹窗可开', advVisible);
    if (advVisible) {
      await page.keyboard.press('Escape');
      await page.click('.el-dialog__headerbtn:visible, .el-overlay:visible button:has-text("关闭")').catch(() => {});
      await page.waitForTimeout(400);
      const advClosed = !(await page.locator('.el-dialog:visible, .el-drawer:visible').first().isVisible().catch(() => false));
      results.push(`${advClosed ? '✅' : '⚠️'} 高级筛选弹窗可关(非断言项) — closed=${advClosed}`);
    }

    // 4) 提醒页
    await page.goto(BASE + '/reminder', { waitUntil: 'networkidle', timeout: 45000 }).catch(async () => {
      await page.goto(BASE + '/reminder/index', { waitUntil: 'networkidle', timeout: 45000 });
    });
    await page.waitForTimeout(1500);
    ok('提醒页加载无白屏', (await page.locator('body').innerText()).length > 100);

    // 5) 汇总断言
    ok('全程无 5xx', badResponses === 0, badResponses === 0 ? '0 个' : badUrls.join(' ; '));
  } catch (e) {
    ok('冒烟流程异常中断', false, String(e).split('\n')[0].slice(0, 250));
  } finally {
    await browser.close().catch(() => {});
  }
  // console error 只如实记录（>0 = 发现），不计入 allPass（按任务口径：断言为 登录/行数/弹窗/5xx）
  results.push(`${consoleErrors === 0 ? '✅' : '🔎'} console error 计数 — ${consoleErrors} 条${errorTexts.length ? '，前3条: ' + JSON.stringify(errorTexts) : ''}`);
  return { engine: engineName, allPass, results, consoleErrors, errorTexts, badResponses, badUrls, launchFailed: false };
}

const summaries = [];
for (const eng of ENGINES) {
  console.log(`\n===== 引擎 ${eng} 开始 =====`);
  const r = await runSmoke(eng);
  console.log(`===== 引擎 ${eng} 结果 =====`);
  r.results.forEach(x => console.log(x));
  summaries.push({
    engine: r.engine, allPass: r.allPass, launchFailed: r.launchFailed,
    consoleErrors: r.consoleErrors, consoleErrorSamples: r.errorTexts,
    bad5xx: r.badResponses, badUrls: r.badUrls,
  });
}
console.log('\n=====SUMMARY_JSON=====');
console.log(JSON.stringify(summaries));
process.exitCode = summaries.every(s => s.allPass) ? 0 : 1;

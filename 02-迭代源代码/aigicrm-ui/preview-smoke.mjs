// AIGICRM PC 端「文件阅读器」受限角色冒烟（真实 Chromium）——2026-10-16
// 为什么用受限账号: 10-08 FM3 / 10-14 郑磊案 / 10-16 王磊磊案三次漏检, 根因都是用 admin/总监验证看不到受限视角;
//   本脚本默认以销售员(team 范围)身份跑, 这是 61% 真实用户的视角。
// 场景: 登录 → 文件管理「私人」页签 → 逐个打开「测试TA-预览-*」样本(docx/xlsx/pdf/csv×2/txt)
// 判定: 每个样本的渲染器真的渲染出内容(DOM 断言, 不只看对话框打开)、全程 console.error == 0、无 5xx。
// 用法(2026-10-21 起统一, 见审计 F1): 三值一律经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<销售员账号> AIGICRM_SMOKE_PASS=<口令> node preview-smoke.mjs
//   (旧 BASE/USER_NAME/PASS 变量名已废弃; 曾内置的测试环境账号/口令字面量已移除, 轮换由运维负责)
// 前置: 目标环境里该账号私人区已有 测试TA-预览-{文档.docx,表格.xlsx,文件.pdf,文本.csv,GBK编码.csv,说明.txt}
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
let consoleErrors = [];
let badResponses = 0;
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`);
  if (!cond) process.exitCode = 1;
}

const browser = await chromium.launch();
const page = await browser.newPage({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + String(e).slice(0, 300)));
page.on('response', (r) => { if (r.status() >= 500) badResponses++; });

// Element Plus 2.14 把 <el-dialog class> 挂在 .el-dialog 自身, 不能用后代选择器; 以组件内部的 .preview-body 反向锁定对话框
const dialog = () => page.locator('.el-dialog').filter({ has: page.locator('.preview-body') });

async function openPreview(fileName) {
  const row = page.locator('.el-table__row', { hasText: fileName }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  await row.locator('.act-icon').first().click();
  await dialog().waitFor({ state: 'visible', timeout: 10000 });
}
async function closePreview() {
  await dialog().locator('.el-dialog__footer button', { hasText: '关闭' }).click();
  await dialog().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(300);
}
async function noStatusError() {
  const err = await page.locator('.preview-body .pv-status--error, .preview-body .pv-status--warn').count();
  return err === 0;
}
async function step(name, fn) {
  const before = consoleErrors.length;
  try {
    await fn();
    const delta = consoleErrors.slice(before);
    ok(`${name}·无 console 错误`, delta.length === 0, delta.join(' | '));
  } catch (e) {
    ok(name, false, String(e).slice(0, 200));
  }
}

try {
  // 1) 登录(受限账号)
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"], input[type="text"]', USER);
  await page.fill('input[type="password"]', PASS);
  await page.click('button:has-text("登")');
  await page.waitForURL((u) => !String(u).includes('login'), { timeout: 15000 });
  ok(`登录 ${USER}`, true, page.url());

  // 2) 文件管理 → 私人页签
  await page.goto(BASE + '/file', { waitUntil: 'networkidle' });
  await page.locator('text=私人').first().click();
  await page.waitForTimeout(1200);
  const sampleRows = await page.locator('.el-table__row', { hasText: '测试TA-预览-' }).count();
  ok('私人区存在测试TA预览样本', sampleRows >= 6, `${sampleRows} 行`);

  // 3) Word .docx → docx-preview 渲染出分页 DOM 与正文
  await step('Word(.docx) 内嵌渲染', async () => {
    await openPreview('测试TA-预览-文档.docx');
    await page.locator('.preview-body .docx-wrapper section.docx').first().waitFor({ state: 'visible', timeout: 25000 });
    const pages = await page.locator('.preview-body .docx-wrapper section.docx').count();
    const text = await page.locator('.preview-body .docx-wrapper').innerText();
    ok('docx 渲染出页面', pages >= 1, `${pages} 页`);
    ok('docx 正文/表格内容可见', text.includes('测试TA Word 在线预览样本') && text.includes('测试TA客户A'));
    ok('docx 无错误/超限提示', await noStatusError());
    await closePreview();
  });

  // 4) Excel .xlsx → sheet 页签 + 沙箱 iframe 表格(含合并单元格)
  await step('Excel(.xlsx) 内嵌渲染', async () => {
    await openPreview('测试TA-预览-表格.xlsx');
    await page.locator('.preview-body .pv-sheet-frame').waitFor({ state: 'visible', timeout: 25000 });
    const tabs = await page.locator('.preview-body .pv-sheet-tab').allInnerTexts();
    ok('xlsx 两个 sheet 页签', tabs.length === 2 && tabs.includes('客户清单') && tabs.includes('汇总'), tabs.join('/'));
    const srcdoc1 = await page.locator('.preview-body .pv-sheet-frame').getAttribute('srcdoc');
    ok('xlsx 首个 sheet 表格含数据与合并标题', !!srcdoc1 && srcdoc1.includes('测试TA客户01') && srcdoc1.includes('合并标题') && /colspan/i.test(srcdoc1));
    const frame = page.frameLocator('.preview-body .pv-sheet-frame');
    const cells = await frame.locator('td').count();
    ok('xlsx 沙箱 iframe 实际渲染出单元格', cells > 200, `${cells} 个 td`);
    await page.locator('.preview-body .pv-sheet-tab', { hasText: '汇总' }).click();
    await page.waitForTimeout(500);
    const srcdoc2 = await page.locator('.preview-body .pv-sheet-frame').getAttribute('srcdoc');
    ok('xlsx 切换 sheet 生效', !!srcdoc2 && srcdoc2.includes('总客户数'));
    ok('xlsx 无错误/超限提示', await noStatusError());
    await closePreview();
  });

  // 5) PDF → iframe 直链流式(2026-10-17 inline+Range 边下边看; 头未生效时 8s 看门狗回落 blob)
  await step('PDF 内嵌渲染', async () => {
    await openPreview('测试TA-预览-文件.pdf');
    await page.locator('.preview-body .pv-pdf-frame').waitFor({ state: 'visible', timeout: 25000 });
    const src = await page.locator('.preview-body .pv-pdf-frame').getAttribute('src');
    ok('pdf iframe 直链流式(或兜底 blob)', !!src && (src.includes('/api/file/download/') || src.startsWith('blob:')), src?.slice(0, 48));
    await page.waitForTimeout(1500);
    // CSP 拦截 blob 帧时 Chromium 会记一条 console error("Refused to frame"), 由 step 的 console 断言兜底
    ok('pdf 无错误/超限提示', await noStatusError());
    await closePreview();
  });

  // 6) CSV(UTF-8) 与 CSV(GBK) → 都应正确解码为中文表格
  for (const [name, label] of [['测试TA-预览-文本.csv', 'CSV(UTF-8)'], ['测试TA-预览-GBK编码.csv', 'CSV(GBK)']]) {
    await step(`${label} 内嵌渲染`, async () => {
      await openPreview(name);
      await page.locator('.preview-body .pv-sheet-frame').waitFor({ state: 'visible', timeout: 20000 });
      const srcdoc = await page.locator('.preview-body .pv-sheet-frame').getAttribute('srcdoc');
      ok(`${label} 中文解码正确`, !!srcdoc && srcdoc.includes('测试TA客户A') && srcdoc.includes('煤矿') && !srcdoc.includes('\uFFFD'));
      ok(`${label} 无错误/超限提示`, await noStatusError());
      await closePreview();
    });
  }

  // 7) TXT → 纯文本
  await step('TXT 内嵌渲染', async () => {
    await openPreview('测试TA-预览-说明.txt');
    await page.locator('.preview-body .pv-text-body').waitFor({ state: 'visible', timeout: 20000 });
    const text = await page.locator('.preview-body .pv-text-body').innerText();
    ok('txt 内容可见', text.includes('测试TA 文本预览样本') && text.includes('第 39 行'));
    ok('txt 无错误/超限提示', await noStatusError());
    await closePreview();
  });

  ok('全程无 5xx', badResponses === 0, `${badResponses} 次`);
  ok('全程 console.error 为零', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));
} catch (e) {
  ok('脚本执行', false, String(e).slice(0, 300));
} finally {
  await browser.close();
  console.log(results.join('\n'));
  console.log(`\n${results.filter((r) => r.startsWith('✅')).length}/${results.length} 通过`);
}

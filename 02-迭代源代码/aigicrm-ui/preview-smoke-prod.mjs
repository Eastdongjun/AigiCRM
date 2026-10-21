// AIGICRM PC「文件阅读器」生产数据复测 —— 2026-10-17
// 目的: 测试环境已切生产快照(aigicrm_prod_20261016), 用**真实生产文件**(公共 48 PDF / 激励 DOCX·XLSX·TXT)
//       复验阅读器, 覆盖生产文件的真实形态(而非合成样本)。受限角色维度已由 preview-smoke.mjs 26/26 覆盖。
// 用法(2026-10-21 起统一, 见审计 F1): 三值一律经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node preview-smoke-prod.mjs
//   (旧 BASE/USER_NAME/PASS 变量名与默认账号已移除, 凭据轮换由运维负责)
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
// 在当前表格里找指定后缀的第一行文件名(真实生产文件名动态发现,不硬编码)。
// 注意行文本首列是类型字母(F/P/D),文件名在后——按"以该后缀结尾的行片段"匹配。
async function firstNameByExt(exts) {
  const rows = page.locator('.el-table__row');
  const count = await rows.count();
  for (let i = 0; i < count; i += 1) {
    const lines = (await rows.nth(i).innerText()).split('\n').map((s) => s.trim()).filter(Boolean);
    const hit = lines.find((line) => exts.some((ext) => line.toLowerCase().endsWith(ext.toLowerCase())) && line.length > 4);
    if (hit) return hit;
  }
  return '';
}

try {
  // 1) 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"], input[type="text"]', USER);
  await page.fill('input[type="password"]', PASS);
  await page.click('button:has-text("登")');
  await page.waitForURL((u) => !String(u).includes('login'), { timeout: 15000 });
  ok(`登录 ${USER}`, true, page.url());

  // 2) 公共区: 真实生产 PDF(48 个) 取第一个 + MD
  await page.goto(BASE + '/file', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  const pdfName = await firstNameByExt(['.pdf']);
  ok('公共区发现真实 PDF', !!pdfName, pdfName);
  if (pdfName) {
    await step(`生产PDF「${pdfName.slice(0, 24)}」内嵌渲染`, async () => {
      await openPreview(pdfName);
      await page.locator('.preview-body .pv-pdf-frame').waitFor({ state: 'visible', timeout: 30000 });
      const src = await page.locator('.preview-body .pv-pdf-frame').getAttribute('src');
      ok('pdf iframe 直链流式(或兜底 blob)', !!src && (src.includes('/api/file/download/') || src.startsWith('blob:')), src?.slice(0, 40));
      await page.waitForTimeout(1500);
      ok('pdf 无错误/超限提示', await noStatusError());
      await closePreview();
    });
  }
  const mdName = await firstNameByExt(['.md']);
  if (mdName) {
    await step(`生产MD「${mdName.slice(0, 24)}」文本渲染`, async () => {
      await openPreview(mdName);
      await page.locator('.preview-body .pv-text-body').waitFor({ state: 'visible', timeout: 25000 });
      const len = (await page.locator('.preview-body .pv-text-body').innerText()).trim().length;
      ok('md 内容非空', len > 0, `${len} 字符`);
      ok('md 无错误/超限提示', await noStatusError());
      await closePreview();
    });
  }

  // 3) 四项激励区: 真实 DOCX/XLSX/TXT (tab 是自定义 .tab-bar/.tab-item, 非 el-tabs;
  //    该区 1727 文件且按时间倒序,首页全是近期 JPG——搜索定位后须**当场**打开,
  //    下一次搜索会替换表格内容,名字不能跨搜索复用)
  await page.locator('.tab-item', { hasText: '四项激励' }).first().click();
  await page.waitForTimeout(1500);
  const searchByName = async (name) => {
    const input = page.locator('.st-search input').first();
    await input.fill(name);
    await input.press('Enter');
    await page.waitForTimeout(1800);
  };
  // DOCX: 搜索 → 当场打开 → 断言
  await searchByName('.docx');
  let docxName = await firstNameByExt(['.docx']);
  if (!docxName) { await searchByName('docx'); docxName = await firstNameByExt(['docx']) }
  ok('激励区发现真实 DOCX', !!docxName, docxName);
  if (docxName) {
    await step(`生产DOCX「${docxName.slice(0, 24)}」内嵌渲染`, async () => {
      await openPreview(docxName);
      await page.locator('.preview-body .docx-wrapper section.docx').first().waitFor({ state: 'visible', timeout: 30000 });
      const pages = await page.locator('.preview-body .docx-wrapper section.docx').count();
      const textLen = (await page.locator('.preview-body .docx-wrapper').innerText()).trim().length;
      ok('docx 渲染出页面', pages >= 1, `${pages} 页`);
      ok('docx 正文可见', textLen > 20, `${textLen} 字符`);
      ok('docx 无错误/超限提示', await noStatusError());
      await closePreview();
    });
  }
  // XLSX
  await searchByName('.xlsx');
  const xlsxName = await firstNameByExt(['.xlsx']);
  ok('激励区发现真实 XLSX', !!xlsxName, xlsxName);
  if (xlsxName) {
    await step(`生产XLSX「${xlsxName.slice(0, 24)}」内嵌渲染`, async () => {
      await openPreview(xlsxName);
      await page.locator('.preview-body .pv-sheet-frame').waitFor({ state: 'visible', timeout: 30000 });
      const tabs = await page.locator('.preview-body .pv-sheet-tab').allInnerTexts();
      ok('xlsx sheet 页签出现', tabs.length >= 1, tabs.join('/'));
      const frame = page.frameLocator('.preview-body .pv-sheet-frame');
      const cells = await frame.locator('td').count();
      ok('xlsx 沙箱 iframe 渲染出单元格', cells > 0, `${cells} 个 td`);
      ok('xlsx 无错误/超限提示', await noStatusError());
      await closePreview();
    });
  }
  // TXT
  await searchByName('.txt');
  const txtName = await firstNameByExt(['.txt']);
  ok('激励区发现真实 TXT', !!txtName, txtName);
  if (txtName) {
    await step(`生产TXT「${txtName.slice(0, 24)}」文本渲染`, async () => {
      await openPreview(txtName);
      await page.locator('.preview-body .pv-text-body').waitFor({ state: 'visible', timeout: 25000 });
      const len = (await page.locator('.preview-body .pv-text-body').innerText()).trim().length;
      ok('txt 内容非空', len > 0, `${len} 字符`);
      ok('txt 无错误/超限提示', await noStatusError());
      await closePreview();
    });
  }

  ok('全程无 5xx', badResponses === 0, `${badResponses} 次`);
  ok('全程 console.error 为零', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));
} catch (e) {
  ok('脚本执行', false, String(e).slice(0, 300));
} finally {
  await browser.close();
  console.log(results.join('\n'));
  console.log(`\n${results.filter((r) => r.startsWith('✅')).length}/${results.length} 通过`);
}

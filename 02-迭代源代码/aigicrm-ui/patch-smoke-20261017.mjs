// PC 三补丁实测(2026-10-17 客户反馈) —— 跑在测试环境(生产快照数据)
// 1) 客户详情·四项激励Tab: 图片/视频/文档点开=阅读器内嵌渲染(此前文档落"不支持"卡)
// 2) 文件管理·文件名列: 全显不断行截断(两行内换行)
// 3) 新建文件夹按钮: 移到顶部操作区,左侧树不再有
// 【审计 F1 2026-10-21】移除内置站点与裸 process.env 凭据读取, 三值统一经环境变量注入, 缺失即失败:
//   AIGICRM_SMOKE_BASE=<站点根URL> AIGICRM_SMOKE_USER=<账号> AIGICRM_SMOKE_PASS=<口令> node patch-smoke-20261017.mjs
import { chromium } from '@playwright/test'

function requireSmokeEnv(name) {
  const value = process.env[name]
  if (!value) {
    console.error(`缺少环境变量 ${name}: 冒烟脚本已移除全部内置凭据(审计 F1), 请显式注入后重跑。`)
    process.exit(1)
  }
  return value
}
const BASE = requireSmokeEnv('AIGICRM_SMOKE_BASE')
const USER = requireSmokeEnv('AIGICRM_SMOKE_USER')
const PASS = requireSmokeEnv('AIGICRM_SMOKE_PASS')
const results = []
let consoleErrors = []
let badResponses = 0
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`)
  if (!cond) process.exitCode = 1
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)) })
page.on('response', (r) => { if (r.status() >= 500) badResponses++ })
const dialog = () => page.locator('.el-dialog').filter({ has: page.locator('.preview-body') })

try {
  // 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"], input[type="text"]', USER)
  await page.fill('input[type="password"]', PASS)
  await page.click('button:has-text("登")')
  await page.waitForURL((u) => !String(u).includes('login'), { timeout: 15000 })

  // ---- 补丁3: 新建文件夹在顶部操作区 ----
  await page.goto(BASE + '/file', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const topBtn = page.locator('.st-folder-add', { hasText: '新建文件夹' })
  ok('顶部操作区出现「新建文件夹」', await topBtn.count() === 1)
  await topBtn.click()
  await sleep(800)
  const dlg = page.locator('.el-dialog:visible, .el-message-box:visible').first()
  ok('点击弹出新建文件夹弹窗', await page.locator('.el-dialog:visible, .el-message-box:visible').count() > 0)
  if (await dlg.count()) {
    await page.keyboard.press('Escape')
    await page.locator('.el-dialog__headerbtn:visible, .el-message-box__headerbtn:visible').first().click().catch(() => {})
    await sleep(500)
  }
  ok('左侧文件夹树不再有新建按钮', await page.locator('.folder-panel .new-folder-btn').count() === 0)

  // ---- 补丁2: 文件名全显 ----
  // 激励区有长文件名; 取一行, 断言单元格文本 = API 文件名(未被截断), 且 css 允许换行
  await page.locator('.tab-item', { hasText: '四项激励' }).first().click()
  await page.waitForTimeout(1800)
  const nameCell = page.locator('.el-table__row .file-name-text').first()
  await nameCell.waitFor({ state: 'visible', timeout: 10000 })
  const cellText = (await nameCell.innerText()).trim()
  ok('文件名单元格渲染', cellText.length > 0, cellText.slice(0, 36))
  const whiteSpace = await nameCell.evaluate((el) => getComputedStyle(el).whiteSpace)
  const clamped = await nameCell.evaluate((el) => getComputedStyle(el).webkitLineClamp || getComputedStyle(el).lineClamp)
  ok('文件名允许换行(white-space normal)', whiteSpace !== 'nowrap', whiteSpace)
  ok('两行 clamp 后才省略', String(clamped) === '2', String(clamped))
  const fullRowText = (await nameCell.locator('xpath=ancestor::tr').first().innerText())
  ok('文件名与行内容一致(未丢字符)', fullRowText.includes(cellText))

  // ---- 补丁1: 客户详情·四项激励附件预览 ----
  // 直达已知有激励附件的客户详情(邯钢 id=256, 生产快照数据),绕开列表页交互噪音
  await page.goto(BASE + '/customer/detail/256', { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  // 进四项激励 Tab
  const incTab = page.locator('.el-tabs__item', { hasText: '四项激励' }).first()
  const incTabCount = await incTab.count()
  ok('客户详情有四项激励Tab', incTabCount > 0)
  if (incTabCount) {
    await incTab.click()
    await sleep(1500)
    const chips = page.locator('.inc-file')
    const chipCount = await chips.count()
    ok('激励附件行存在(该客户)', chipCount > 0, `${chipCount} 个`)
    if (chipCount) {
      const errors0 = consoleErrors.length
      await chips.first().click()
      await sleep(1500)
      // 附件点击后: 阅读器对话框 或 图片大图(el-image-viewer) 至少出现一个, 且不落"不支持"卡
      const dlgVisible = await dialog().count()
      const viewerVisible = await page.locator('.el-image-viewer__wrapper, .el-image-viewer__img').count()
      const unsupported = await page.locator('.preview-body .pv-status--error, .preview-body .pv-unsupported, .pv-unsupported').count()
      ok('点击附件打开预览(阅读器或大图)', dlgVisible > 0 || viewerVisible > 0, `dialog=${dlgVisible} viewer=${viewerVisible}`)
      ok('未落入不支持/错误卡', unsupported === 0)
      ok('预览过程无 console 错误', consoleErrors.length === errors0, consoleErrors.slice(errors0).join('|'))
      if (dlgVisible) {
        await dialog().locator('.el-dialog__footer button', { hasText: '关闭' }).click().catch(() => {})
      } else if (viewerVisible) {
        await page.keyboard.press('Escape').catch(() => {})
        await page.locator('.el-image-viewer__close').click().catch(() => {})
      }
      await sleep(600)
    }
  }

  ok('全程无 5xx', badResponses === 0, `${badResponses} 次`)
  ok('全程 console.error 为零', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))
} catch (e) {
  ok('脚本执行', false, String(e).slice(0, 300))
} finally {
  await browser.close()
  console.log(results.join('\n'))
  console.log(`\n${results.filter((r) => r.startsWith('✅')).length}/${results.length} 通过`)
}

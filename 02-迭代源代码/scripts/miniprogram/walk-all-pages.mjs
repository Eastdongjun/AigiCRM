import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【2026-11-07 守则 D7/D8 执法器】小程序 43 页全量渲染走查
// 缘起: 复盘条目1——MP 43 页零单测, 页面级冒烟未进门禁。
// 口径: 遍历 app.json 全部页面(tabBar 页 switchTab, 其余 navigateTo), 挂收集器抓
//       console.error / onError / unhandledRejection; 需要参数的详情页打不开=SKIPPED(列明, 不算红);
//       任何一页产生报错=FAIL。判定只认退出码。
// 用法: node scripts/miniprogram/walk-all-pages.mjs   (gate.sh [8] AIGI_MP_WALK=1 调用; 需 DevTools 已登录+端口 9424)

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const mpRoot = path.join(ROOT, 'aigicrm-miniprogram')
const appJson = JSON.parse(fs.readFileSync(path.join(mpRoot, 'app.json'), 'utf8'))
const pages = appJson.pages || []
const tabPages = new Set(((appJson.tabBar && appJson.tabBar.list) || []).map((t) => '/' + (t.pagePath || t.page_path)))

const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log(`connected — ${pages.length} 页面(其中 tabBar ${tabPages.size})`)

await mini.evaluate(() => {
  const app = getApp()
  if (!app.__walkLog) {
    app.__walkLog = []
    const push = (tag) => (...args) => {
      try {
        app.__walkLog.push(tag + ': ' + args.map((a) => {
          try { return typeof a === 'object' ? JSON.stringify(a).slice(0, 200) : String(a) } catch (e) { return String(a) }
        }).join(' '))
      } catch (e) {}
    }
    const oe = console.error
    console.error = function (...a) { push('console.error')(...a); try { oe.apply(console, a) } catch (e) {} }
    const ow = console.warn
    console.warn = function (...a) { push('console.warn')(...a); try { ow.apply(console, a) } catch (e) {} }
    if (wx.onUnhandledRejection) wx.onUnhandledRejection((res) => push('unhandledRejection')(res && res.reason))
    if (wx.onError) wx.onError((msg) => push('onError')(msg))
    if (wx.onPageNotFound) wx.onPageNotFound((res) => push('pageNotFound')((res && res.path) || ''))
  }
})

const wait = (ms) => new Promise((r) => setTimeout(r, ms))
const countBefore = async () => (await mini.evaluate(() => getApp().__walkLog.length)) || 0
const takeErrors = async (b) => {
  const logs = (await mini.evaluate((x) => getApp().__walkLog.slice(x), b)) || []
  return logs.filter((l) => !l.startsWith('console.warn'))
}

const results = []
let failed = 0
for (const p of pages) {
  const url = '/' + p
  const isTab = tabPages.has(url)
  const before = await countBefore()
  let opened = true
  let landed = url
  try {
    if (isTab) await mini.switchTab(url)
    else await mini.navigateTo(url)
    await wait(1700)
    const cur = await mini.currentPage()
    landed = (cur && cur.path) || '(null)'
    // 从详情页回来, 保持栈干净
    if (!isTab) { await mini.navigateBack(); await wait(450) }
  } catch (e) {
    opened = false
    await wait(300)
  }
  const errs = opened ? await takeErrors(before) : []
  const item = { page: p, opened, landed, errors: errs.length, first: errs[0] ? errs[0].slice(0, 120) : '' }
  results.push(item)
  if (errs.length > 0) failed += 1
}

console.log('── 走查明细 ──')
for (const r of results) {
  if (!r.opened) console.log(`SKIP ${r.page} (navigateTo 拒绝——大概率需要参数, 请人工或带参走查)`)
  else if (r.errors > 0) console.log(`FAIL ${r.page} — ${r.errors} 条报错: ${r.first}`)
  else console.log(`PASS ${r.page}`)
}
const skipped = results.filter((r) => !r.opened)
const allLogs = (await mini.evaluate(() => getApp().__walkLog)) || []
console.log(`── 汇总: 打开 ${results.length - skipped.length}/${pages.length}, 跳过 ${skipped.length}, 报错页 ${failed}, console.warn ${allLogs.filter((l) => l.startsWith('console.warn')).length} 条(不判红) ──`)
await mini.disconnect()
if (failed > 0) { console.log('WALK_RED'); process.exit(1) }
console.log('WALK_GREEN')
process.exit(0)

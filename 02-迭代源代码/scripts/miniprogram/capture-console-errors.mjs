import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【2026-11-07 用户令"调试器有报错 检查影响范围"】挂 console.error/warn + 未处理 rejection 收集器,
// 驱动近期全部改动面(学分明细表格/部门明细/审批优化/文件管理/激励统计), 收齐后回读
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')

await mini.evaluate(() => {
  const app = getApp()
  if (!app.__errLog) {
    app.__errLog = []
    const push = (tag) => (...args) => {
      try { app.__errLog.push(tag + ': ' + args.map((a) => { try { return typeof a === 'object' ? JSON.stringify(a).slice(0, 300) : String(a) } catch (e) { return String(a) } }).join(' ')) } catch (e) {}
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
console.log('collector installed')

const walk = async (label, fn) => {
  const before = await mini.evaluate(() => getApp().__errLog.length)
  try { await fn() } catch (e) { console.log(label, 'walk-err:', String(e).slice(0, 100)) }
  await new Promise((r) => setTimeout(r, 1200))
  const logs = await mini.evaluate((b) => getApp().__errLog.slice(b), before)
  if (logs && logs.length) logs.forEach((l) => console.log('[' + label + ']', l))
  console.log('[' + label + '] 新增报错: ' + ((logs && logs.length) || 0))
}

// ① 学分排行 → 个人明细表格
await walk('排行/明细', async () => {
  await mini.reLaunch('/pages/ranking/index')
  await new Promise((r) => setTimeout(r, 2200))
  const page = await mini.currentPage()
  const d = await page.data()
  const first = (d.scoreList || []).find((x) => x.userId) || (d.scoreList || [])[0]
  if (first && first.userId) {
    const q = 'name=' + encodeURIComponent(first.realName || '') + '&score=' + encodeURIComponent(first.totalScore || 0) + '&dept=' + encodeURIComponent(first.officeName || '') + '&period=' + encodeURIComponent('2026全年') + '&userId=' + encodeURIComponent(first.userId) + '&rank=' + encodeURIComponent(first.rank || 1)
    await mini.navigateTo('/pages/ranking/detail/index?' + q)
    await new Promise((r) => setTimeout(r, 2200))
    await mini.navigateBack()
    await new Promise((r) => setTimeout(r, 800))
  }
})

// ② 部门明细 → 成员钻取
await walk('部门明细', async () => {
  const page = await mini.currentPage()
  const d = await page.data()
  const deptRow = (d.deptList || [])[0]
  if (deptRow && deptRow.deptName) {
    const q = 'name=' + encodeURIComponent(deptRow.deptName) + '&score=' + encodeURIComponent(deptRow.totalScore || 0) + '&period=' + encodeURIComponent('2026全年') + '&rank=' + encodeURIComponent(deptRow.rank || 1)
    await mini.navigateTo('/pages/ranking/detail/index?' + q)
    await new Promise((r) => setTimeout(r, 2400))
    const dp = await mini.currentPage()
    const dd = await dp.data()
    const m = (dd.membersView && dd.membersView.rows || [])[0]
    await mini.navigateBack()
    await new Promise((r) => setTimeout(r, 800))
    if (m && m.userId) {
      const q2 = 'name=' + encodeURIComponent(m.name) + '&score=' + encodeURIComponent(m.score || 0) + '&dept=' + encodeURIComponent(deptRow.deptName) + '&period=' + encodeURIComponent('2026全年') + '&userId=' + encodeURIComponent(m.userId)
      await mini.navigateTo('/pages/ranking/detail/index?' + q2)
      await new Promise((r) => setTimeout(r, 2200))
      await mini.navigateBack()
      await new Promise((r) => setTimeout(r, 800))
    }
  }
})

// ③ 审批管理(含队列单行)
await walk('审批管理', async () => {
  await mini.navigateTo('/pages/approval/index')
  await new Promise((r) => setTimeout(r, 2600))
  const page = await mini.currentPage()
  await page.callMethod('changeTab', { currentTarget: { dataset: { tab: 'mine' } } })
  await new Promise((r) => setTimeout(r, 1500))
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 800))
})

// ④ 文件管理(选夹面板+组预览收集)
await walk('文件管理', async () => {
  await mini.switchTab('/pages/files/index')
  await new Promise((r) => setTimeout(r, 2600))
  const page = await mini.currentPage()
  await page.callMethod('openFolderSelect', { mode: 'album' })
  await new Promise((r) => setTimeout(r, 900))
  await page.callMethod('cancelFolderSelect')
  await new Promise((r) => setTimeout(r, 600))
  const d = await page.data()
  const idx = (d.filteredFiles || []).findIndex((x) => /\.(png|jpg|jpeg)$/i.test(String(x.name || '')))
  if (idx >= 0) {
    await page.callMethod('previewFile', { currentTarget: { dataset: { index: idx } } })
    await new Promise((r) => setTimeout(r, 1500))
  }
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 600))
})

// ⑤ 激励统计(大类统计+明细弹层)
await walk('激励统计', async () => {
  await mini.navigateTo('/pages/incentive/index?mode=stats')
  await new Promise((r) => setTimeout(r, 2600))
  const page = await mini.currentPage()
  await page.callMethod('switchRankTab', { currentTarget: { dataset: { tab: 'scoreboard' } } })
  await new Promise((r) => setTimeout(r, 1200))
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 600))
})

const total = await mini.evaluate(() => getApp().__errLog)
console.log('=== 全量收集(' + ((total && total.length) || 0) + ' 条) ===')
;(total || []).slice(0, 30).forEach((l) => console.log(l))
await mini.disconnect()
console.log('CAPTURE_DONE')

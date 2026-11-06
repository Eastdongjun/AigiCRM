import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【2026-11-07 续】剩余页面全走查 + 审批详情钻取
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
  }
})
console.log('collector installed')

const walk = async (label, fn) => {
  const before = await mini.evaluate(() => getApp().__errLog.length)
  try { await fn() } catch (e) { console.log(label, 'walk-err:', String(e).slice(0, 90)) }
  await new Promise((r) => setTimeout(r, 1000))
  const logs = await mini.evaluate((b) => getApp().__errLog.slice(b), before)
  if (logs && logs.length) logs.forEach((l) => console.log('[' + label + ']', l))
  console.log('[' + label + '] 新增报错: ' + ((logs && logs.length) || 0))
}

await walk('首页tab', async () => {
  await mini.switchTab('/pages/home/index')
  await new Promise((r) => setTimeout(r, 2000))
})
await walk('四项服务tab', async () => {
  await mini.switchTab('/pages/incentive/home/index')
  await new Promise((r) => setTimeout(r, 2000))
})
await walk('文件tab', async () => {
  await mini.switchTab('/pages/files/index')
  await new Promise((r) => setTimeout(r, 2000))
})
await walk('应用tab', async () => {
  await mini.switchTab('/pages/app/index')
  await new Promise((r) => setTimeout(r, 1200))
})
await walk('我的tab', async () => {
  await mini.switchTab('/pages/profile/index')
  await new Promise((r) => setTimeout(r, 1500))
})
await walk('客户列表+详情', async () => {
  await mini.navigateTo('/pages/customer/index')
  await new Promise((r) => setTimeout(r, 2200))
  const page = await mini.currentPage()
  const d = await page.data()
  const first = (d.filteredCustomers || d.customers || [])[0]
  if (first && first.id) {
    await mini.navigateTo('/pages/customer/detail/index?id=' + first.id)
    await new Promise((r) => setTimeout(r, 2600))
    await mini.navigateBack()
    await new Promise((r) => setTimeout(r, 700))
  }
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 700))
})
await walk('审批详情钻取', async () => {
  await mini.navigateTo('/pages/approval/index')
  await new Promise((r) => setTimeout(r, 2400))
  const page = await mini.currentPage()
  const d = await page.data()
  // 切到「我发起的」找一条带 id 的单据, 从行按钮区找详情入口
  await page.callMethod('changeTab', { currentTarget: { dataset: { tab: 'mine' } } })
  await new Promise((r) => setTimeout(r, 1500))
  const d2 = await page.data()
  const row = (d2.approvalView && d2.approvalView.list || [])[0]
  if (row && row.id) {
    await mini.navigateTo('/pages/approval/detail/index?id=' + row.id)
    await new Promise((r) => setTimeout(r, 2400))
    await mini.navigateBack()
    await new Promise((r) => setTimeout(r, 700))
  }
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 700))
})
await walk('公海池', async () => {
  await mini.navigateTo('/pages/sea/index')
  await new Promise((r) => setTimeout(r, 2400))
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 700))
})
await walk('提醒管理', async () => {
  await mini.navigateTo('/pages/reminder/index')
  await new Promise((r) => setTimeout(r, 2400))
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 700))
})

const total = await mini.evaluate(() => getApp().__errLog)
console.log('=== 全量收集(' + ((total && total.length) || 0) + ' 条) ===')
;(total || []).slice(0, 40).forEach((l) => console.log(l))
await mini.disconnect()
console.log('CAPTURE2_DONE')

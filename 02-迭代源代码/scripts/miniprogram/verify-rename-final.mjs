import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【终验】①还原 2483 原名 ②UI 全链复测(弹窗→改名→等 4s→页面数据回读)
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
const wait = (ms) => new Promise((r) => setTimeout(r, ms))
const ORIG = 'e7Uq769J17uy806011c666ae8523378dc295e73beaf0.png'

const info = await mini.evaluate(() => {
  const keys = (wx.getStorageInfoSync() || {}).keys || []
  const tokKey = keys.find((k) => k.startsWith('AIGICRM_TOKEN')) || ''
  return { base: tokKey ? tokKey.replace('AIGICRM_TOKEN::', '') : '', token: tokKey ? wx.getStorageSync(tokKey) : '' }
})
const call = (method, url, data) => mini.evaluate((args) => new Promise((resolve) => {
  wx.request({ url: args.base + args.url, method: args.method, header: { Authorization: 'Bearer ' + args.token, 'Content-Type': 'application/json' }, data: args.data, success: (r) => resolve({ status: r.statusCode, body: r.data }), fail: (e) => resolve({ fail: e && e.errMsg }) })
}), { base: info.base, token: info.token, method, url, data })

const back = await call('PUT', '/api/file/2483/rename', { fileName: ORIG })
console.log('还原原名 →', JSON.stringify(back).slice(0, 120))

// UI 全链复测
await mini.reLaunch('/pages/files/index')
await wait(3000)
const page = await mini.currentPage()
await page.callMethod('openActionSheet', { currentTarget: { dataset: { index: 0 } } })
await wait(800)
await page.callMethod('onSheetAction', { currentTarget: { dataset: { act: 'rename', index: 0 } } })
await wait(800)
let d = await page.data()
console.log('弹窗:', JSON.stringify(d.renameModal))
if (d.renameModal.visible) {
  await page.setData({ 'renameModal.input': 'E2E-RenameCheck-20261107.png' })
  await wait(200)
  await page.callMethod('confirmRename')
  await wait(4500)
  const d2 = await (await mini.currentPage()).data()
  const row = (d2.filteredFiles || []).find((x) => String(x.id) === '2483')
  console.log('UI改名后列表回读 id=2483:', row ? String(row.name) : '(行不在当前页)')
  // 还原
  const r3 = await call('PUT', '/api/file/2483/rename', { fileName: ORIG })
  console.log('终还原 →', JSON.stringify(r3).slice(0, 120))
  await wait(600)
}
await mini.disconnect()
console.log('FINAL_DONE')

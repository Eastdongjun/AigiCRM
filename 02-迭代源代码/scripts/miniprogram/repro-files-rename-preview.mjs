import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【改名定性探针】取 storage 里的 token(键含 base) 直打 rename 接口, 看原始 status/body; 再回读列表
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

const info = await mini.evaluate(() => {
  const keys = (wx.getStorageInfoSync() || {}).keys || []
  const tokKey = keys.find((k) => k.startsWith('AIGICRM_TOKEN')) || ''
  return { tokKey, base: tokKey ? tokKey.replace('AIGICRM_TOKEN::', '') : '', token: tokKey ? wx.getStorageSync(tokKey) : '' }
})
console.log('base =', info.base, '| token存在 =', !!info.token)

const call = (method, url, data) => mini.evaluate((args) => new Promise((resolve) => {
  wx.request({
    url: args.base + args.url,
    method: args.method,
    header: { Authorization: 'Bearer ' + args.token, 'Content-Type': 'application/json' },
    data: args.data,
    success: (r) => resolve({ status: r.statusCode, body: r.data }),
    fail: (e) => resolve({ fail: e && e.errMsg })
  })
}), { base: info.base, token: info.token, method, url, data })

// ① 看当前首行名(id 2483)
const list1 = await call('GET', '/api/file/page?current=1&size=3', {})
const recs = (list1.body && (list1.body.data && (list1.body.data.records || list1.body.data))) || []
console.log('HTTP GET 列表 status =', list1.status, ' 首条 =', recs[0] ? JSON.stringify({ id: recs[0].id, name: recs[0].fileName || recs[0].name }) : JSON.stringify(list1.body).slice(0, 150))

// ② 直打改名(改成 E2E 名)
const r1 = await call('PUT', '/api/file/2483/rename', { fileName: 'E2E-RenameCheck-20261107.png' })
console.log('PUT rename →', JSON.stringify(r1).slice(0, 220))

// ③ 回读
const list2 = await call('GET', '/api/file/page?current=1&size=3', {})
const recs2 = (list2.body && (list2.body.data && (list2.body.data.records || list2.body.data))) || []
console.log('改名后首条 =', recs2[0] ? JSON.stringify({ id: recs2[0].id, name: recs2[0].fileName || recs2[0].name }) : JSON.stringify(list2.body).slice(0, 150))

// ④ 改回原名(若②成功)
if (r1.status === 200) {
  const orig = recs[0] ? (recs[0].fileName || recs[0].name) : null
  if (orig) {
    const r2 = await call('PUT', '/api/file/2483/rename', { fileName: orig })
    console.log('PUT 还原 →', JSON.stringify(r2).slice(0, 160))
  }
}
await mini.disconnect()
console.log('PROBE3_DONE')

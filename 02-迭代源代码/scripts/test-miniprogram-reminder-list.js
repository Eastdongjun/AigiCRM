#!/usr/bin/env node
// 小程序提醒列表 C1/C3 修复行为测试(2026-10-21 全局债务审计 P1 批次)
// 在 node 下驱动 pages/reminder/index.js + utils/request.js,钉住:
// 1) 【C1】真实模式搜索: keyword 随 /reminder/list 发给服务端,前端不再对当前页做客户端过滤,
//    渲染行 = 服务端返回行(以服务端为准),total = 服务端 total(同口径);
// 2) 【C1】翻页重新请求: onPagerChange 触发新的 /reminder/list,current=目标页且 keyword 保留;
// 3) 【C1】演示模式保留本地过滤(filterRemindersDemo 仅 demo 分支可用,旧名已删除);
// 4) 【C3】"一键清空"确认弹窗: 标题/文案明确"当前页 N 条"并展示筛选全量 total,
//    多页时提示其余条数需翻页;确认后逐条处理,完成 toast 同样点明"当前页"。

const assert = require('node:assert/strict')

// ---- 测试环境: 捕获 wx.request / wx.showModal / wx.showToast ----
const requests = []          // 每次 wx.request 的 opts(手动 success 驱动 Promise)
let modalOpts = null         // wx.showModal 捕获
let lastToast = null         // wx.showToast 捕获
let demoMode = false

global.wx = {
  request: (opts) => { requests.push(opts) },
  getStorageSync: () => undefined,
  setStorageSync: () => {},
  removeStorageSync: () => {},
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
  showToast: (opts) => { lastToast = opts },
  showModal: (opts) => { modalOpts = opts },
  navigateBack: () => {},
  navigateTo: () => {},
  redirectTo: () => {},
  switchTab: () => {}
}
global.getApp = () => ({ globalData: { demoMode, currentUser: { id: 7, roles: [] }, apiBaseUrl: '' } })

let pageConfig = null
global.Page = (config) => { pageConfig = config }
require('../aigicrm-miniprogram/pages/reminder/index.js')
assert.ok(pageConfig, 'reminder page config captured')

const tick = () => new Promise((r) => setImmediate(r))

function respond(opts, data) {
  opts.success && opts.success({ statusCode: 200, data: { code: 200, data } })
}

function createPage() {
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, cb) { Object.assign(this.data, patch); if (cb) cb() }
  return page
}

function serverRow(id, customerName) {
  return { id, reminderId: id, customerId: id, customerName, customerCode: 'C-' + id, type: 'follow', status: 'pending', ownerName: '张三', officeName: '华东办', content: '请及时跟进' }
}

async function main() {
  // ============ 1+2. 真实模式: keyword 上服务端, 不做当前页客户端过滤, total 同口径 ============
  demoMode = false
  const page = createPage()
  page.applyState({ keyword: '华东' })
  requests.length = 0
  page.loadReminders(1, 10)

  assert.equal(requests.length, 1, '真实模式加载只发一次列表请求')
  const listReq = requests[0]
  assert.equal(listReq.url, '/api/reminder/list', '待处理视图走 /reminder/list')
  assert.equal(listReq.data.keyword, '华东', 'keyword 必须随请求发给服务端(C1)')
  assert.equal(listReq.data.current, 1, '第 1 页')
  assert.equal(listReq.data.size, 10, '页大小 10')
  assert.equal(listReq.data.status, 'pending', '待处理视图固定 status=pending')

  // 服务端按 keyword 过滤后返回第 1 页; 故意让第 2 行的 customerName 不含"华东",
  // 证明前端不再对服务端行做二次本地过滤(服务端口径为准)
  respond(listReq, { records: [serverRow(101, '华东智能制造'), serverRow(102, '华南贸易')], total: 35 })
  await tick()
  assert.equal(page.data.reminders.length, 2, '服务端返回的行必须全量渲染,不做当前页客户端过滤(C1)')
  assert.equal(page.data.reminders[1].customerName, '华南贸易', '本地 keyword 不匹配的行也按服务端口径保留')
  assert.equal(page.data.total, 35, 'total 必须取服务端同口径总数(C1)')
  assert.equal(page.data.page, 1, '页码停留在第 1 页')

  // ---- 翻页重新请求: current=2 且 keyword 保留 ----
  requests.length = 0
  page.onPagerChange({ detail: { page: 2, pageSize: 10 } })
  assert.equal(requests.length, 1, '翻页必须重新发起服务端请求(C1)')
  const page2Req = requests[0]
  assert.equal(page2Req.data.current, 2, '翻页请求 current=2')
  assert.equal(page2Req.data.keyword, '华东', '翻页请求保留 keyword(C1)')
  respond(page2Req, { records: [serverRow(201, '华东机械')], total: 35 })
  await tick()
  assert.equal(page.data.reminders.length, 1, '第 2 页数据替换当前页')
  assert.deepEqual(page.data.reminders.map((r) => r.id), [201], '当前页被第 2 页行替换')
  assert.equal(page.data.total, 35, '翻页后 total 仍为服务端总数')

  // ---- pageSize 切换回到第 1 页 ----
  requests.length = 0
  page.onPagerChange({ detail: { page: 3, pageSize: 30 } })
  assert.equal(requests[0].data.current, 1, '切换页大小必须回到第 1 页')
  assert.equal(requests[0].data.size, 30, '页大小随选择切换')

  // ============ 3. 演示模式: 本地过滤保留(仅 demo 分支), 旧方法名已删除 ============
  demoMode = true
  const demoPage = createPage()
  assert.equal(typeof demoPage.filterRemindersDemo, 'function', '演示模式本地过滤保留为 filterRemindersDemo')
  assert.equal(typeof demoPage.filterReminders, 'undefined', '真实模式客户端过滤入口必须删除(C1)')
  demoPage.applyState({ keyword: '山西' })
  demoPage.loadReminders(1, 10)
  assert.equal(demoPage.data.reminders.length, 1, 'demo 数据按 keyword 过滤后剩 1 条')
  assert.equal(demoPage.data.reminders[0].customerName, '山西晋能集团中阳煤业', 'demo 过滤命中山西客户')
  assert.equal(demoPage.data.total, 1, 'demo total 为本地过滤后总数(同口径)')
  demoMode = false

  // ============ 4. C3: 一键清空弹窗口径 ============
  const bulkPage = createPage()
  const rows = []
  for (let i = 1; i <= 10; i++) rows.push(serverRow(i, '客户' + i))
  bulkPage.applyState({ reminders: rows, total: 35, page: 1, pageSize: 10 })

  modalOpts = null
  bulkPage.processAll()
  assert.ok(modalOpts, 'processAll 必须弹确认框')
  assert.equal(modalOpts.title, '清空当前页提醒', '标题点明"当前页"(C3)')
  assert.ok(modalOpts.content.includes('当前页 10 条'), '文案必须写明当前页条数: ' + modalOpts.content)
  assert.ok(modalOpts.content.includes('当前筛选共 35 条'), '弹窗必须展示筛选全量 total(C3): ' + modalOpts.content)
  assert.ok(modalOpts.content.includes('其余 25 条需翻页分批处理'), '多页时提示剩余条数需翻页: ' + modalOpts.content)
  assert.ok(!modalOpts.content.includes('全部标记为已处理'), '不得再出现"筛选出的 N 条全部标记"误导文案: ' + modalOpts.content)

  // 确认后: 仅当前页 10 条逐个 POST /process; 完成 toast 点明"当前页"
  requests.length = 0
  lastToast = null
  modalOpts.success({ confirm: true })
  const processReqs = requests.filter((r) => r.url.indexOf('/api/reminder/') === 0 && r.url.endsWith('/process'))
  assert.equal(processReqs.length, 10, '只处理当前页 10 条(C3)')
  processReqs.forEach((r) => respond(r, true))
  await tick()
  assert.ok(lastToast && lastToast.title.includes('当前页 10 条'), '完成 toast 点明当前页范围(C3): ' + (lastToast && lastToast.title))
  assert.equal(bulkPage.data.processingAll, false, '批量状态复位')

  // ---- 单页场景: total 等于当前页条数,不出现"翻页"提示 ----
  const singlePage = createPage()
  const rows2 = []
  for (let i = 1; i <= 5; i++) rows2.push(serverRow(i, '客户' + i))
  singlePage.applyState({ reminders: rows2, total: 5, page: 1, pageSize: 10 })
  modalOpts = null
  singlePage.processAll()
  assert.ok(modalOpts.content.includes('当前页 5 条'), '单页场景文案含当前页条数: ' + modalOpts.content)
  assert.ok(modalOpts.content.includes('当前筛选共 5 条'), '单页场景仍展示全量 total: ' + modalOpts.content)
  assert.ok(modalOpts.content.includes('已全部在本页'), '单页场景明示已全部在本页: ' + modalOpts.content)
  assert.ok(!modalOpts.content.includes('需翻页'), '单页场景不得出现翻页提示: ' + modalOpts.content)
}

main().then(() => console.log('PASS: miniprogram reminder list (server-caliber keyword & paging C1, clear-current-page modal copy C3)')).catch((e) => { console.error(e.message); process.exit(1) })

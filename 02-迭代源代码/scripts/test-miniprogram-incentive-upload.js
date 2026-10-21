#!/usr/bin/env node
// 激励上传页 C2/C4 行为测试(2026-10-21)
// 在 node 下驱动真实页面模块 pages/incentive/upload/index.js(参照 test-miniprogram-customer-idempotency.js
// 的 Page 捕获模式), 在 wx.request 路由层注入服务端应答, 钉住:
// 1) 【C2】客户选择半屏=服务端搜索+分页: 请求带 keyword/current/size; total 用服务端匹配总数
//    (不再用本页行数冒充); 滚到底追加下一页; 选中按 customerId 定位(不按数组下标);
// 2) 【C2】草稿恢复按 id 补拉客户(getCustomer): 客户存在→弹恢复且不删草稿; 404/403→清草稿;
//    网络错误→保留草稿(不得把未完成录入当"选项失效"误删);
// 3) 【C4】编辑态清空字段发 ''(remark/relatedProducts/specificProduct/preInstallQuestion),
//    PUT body 保留空串; 新建态维持 null(剥除)语义。

const assert = require('node:assert/strict')

const requests = []          // 全部 wx.request 调用(按序)
const modals = []            // wx.showModal 调用
const toasts = []
let uploadPageConfig = null

global.wx = {
  request: (opts) => { requests.push(opts); route(opts) },
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: (opts) => modals.push(opts),
  showLoading: () => {},
  hideLoading: () => {},
  navigateBack: () => {},
  navigateTo: () => {},
  redirectTo: () => {},
  switchTab: () => {},
  chooseMedia: () => {},
  chooseMessageFile: () => {}
}
global.getApp = () => ({ globalData: { demoMode: false, currentUser: { id: 7 }, apiBaseUrl: '', forceRefresh: {} } })
global.Page = (config) => { uploadPageConfig = config }

// ---- 服务端应答路由(按需在用例里覆写 listPages/customerById/draft) ----
let listResponder = null    // (params) => ({ records, total })
let customerById = null     // (id) => 应答控制
let draftPayload = null
const deletedDrafts = []
function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  if (url.indexOf('/api/customer/list') >= 0) {
    return opts.success(ok(listResponder ? listResponder(opts.data || {}) : { records: [], total: 0 }))
  }
  const custMatch = url.match(/\/api\/customer\/(\d+)$/)
  if (custMatch && customerById) return customerById(Number(custMatch[1]), opts)
  if (url.indexOf('/api/draft/incentive_upload') >= 0) {
    if (method === 'DELETE') { deletedDrafts.push(url); return opts.success(ok(true)) }
    return opts.success(ok(draftPayload ? { draftData: JSON.stringify(draftPayload), version: 3, updatedAt: '2026-10-21 10:00:00' } : null))
  }
  if (url.indexOf('/api/incentive/records') >= 0 && (method === 'PUT' || method === 'POST')) {
    return opts.success(ok(true)) // 落库成功(体断言读 requests 捕获)
  }
  return opts.success(ok(null))
}

require('../aigicrm-miniprogram/pages/incentive/upload/index.js')
assert.ok(uploadPageConfig, 'upload page config captured')

const lastPut = () => [...requests].reverse().find((r) => (r.method === 'PUT' || r.method === 'POST') && String(r.url).indexOf('/api/incentive/records') >= 0)

function createUploadPage() {
  const page = Object.create(uploadPageConfig)
  page.data = JSON.parse(JSON.stringify(uploadPageConfig.data))
  // setData 需支持 'customerSheet.filtered' 形式的路径键
  page.setData = function (patch, cb) {
    Object.keys(patch).forEach((key) => {
      if (key.indexOf('.') > 0) {
        const path = key.split('.')
        let target = this.data
        for (let i = 0; i < path.length - 1; i += 1) target = target[path[i]]
        target[path[path.length - 1]] = patch[key]
      } else {
        this.data[key] = patch[key]
      }
    })
    if (cb) cb()
  }
  return page
}

const tick = () => new Promise((r) => setImmediate(r))
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  // ========== 1. 【C2】服务端搜索 + 分页 ==========
  let page = createUploadPage()
  listResponder = (params) => {
    // 断言请求参数形状: current/size 服务端分页; 空关键字不发 keyword(传输层剥空串)
    assert.equal(params.current, page.data.customerSheet.page, '请求页码必须来自半屏当前页')
    assert.equal(params.size, 20, '半屏分页页大小=20')
    if (page.data.customerSheet.keyword) assert.equal(params.keyword, page.data.customerSheet.keyword)
    else assert.equal(params.keyword, undefined, '空关键字不得下发 keyword')
    if (params.current === 1) return { records: [{ id: 11, customerName: '客户A', industry: '煤矿' }, { id: 12, customerName: '客户B' }], total: 57 }
    if (params.current === 2) return { records: [{ id: 13, customerName: '客户C' }], total: 3 } // 换口径: 第2页后收尾
    return { records: [], total: 3 }
  }
  page.openCustomerSheet()
  await tick()
  assert.equal(page.data.customerSheet.total, 57, 'total 必须用服务端匹配总数, 不得用本页行数(2)冒充')
  assert.equal(page.data.customerSheet.filtered.length, 2)
  assert.equal(page.data.customerSheet.finished, false, '2 < 57, 还有下一页')

  // 滚到底追加下一页
  listResponder = () => ({ records: [{ id: 13, customerName: '客户C' }], total: 3 })
  page.loadMoreCustomers()
  await tick()
  assert.equal(page.data.customerSheet.page, 2)
  assert.equal(page.data.customerSheet.filtered.length, 3, '追加而非替换')
  assert.equal(page.data.customerSheet.finished, true, '已加载 3/3, 无更多')
  assert.equal(page.data.customerSheet.total, 3, '搜索口径变化后 total 跟随服务端')

  // 关键字搜索: 防抖后按 keyword 拉第 1 页, 结果替换
  page.inputCustomerKeyword({ detail: { value: '华东' } })
  listResponder = (params) => {
    assert.equal(params.keyword, '华东')
    assert.equal(params.current, 1, '搜索必须回到第 1 页')
    return { records: [{ id: 99, customerName: '华东智能制造集团', industry: '制造业' }], total: 1 }
  }
  await sleep(360) // 越过 300ms 防抖
  assert.equal(page.data.customerSheet.filtered.length, 1)
  assert.equal(page.data.customerSheet.total, 1)
  assert.equal(page.data.customerSheet.finished, true)

  // 选中按 customerId 定位(非数组下标)
  page.pickCustomer({ currentTarget: { dataset: { id: '99' } } })
  assert.equal(page.data.selectedCustomer && page.data.selectedCustomer.id, 99, '选中客户按 id 快照')
  assert.equal(page.data.selectedCustomerId, 99)
  assert.equal(page.data.customerSheet.open, false, '选中后关闭半屏')

  // ========== 2. 【C2】草稿恢复按 id 补拉 ==========
  page = createUploadPage()
  page.data.categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }]
  deletedDrafts.length = 0
  modals.length = 0

  // 2a. 客户存在(不在任何本地列表, 只能靠按 id 补拉): 弹恢复且不删草稿
  draftPayload = { customerId: 42, categoryId: 1, remark: '未完成备注', uploadedFiles: [] }
  customerById = (id, opts) => opts.success(ok({ id, customerName: '草稿客户甲', industry: '煤矿' }))
  page.tryRestoreDraft()
  await tick()
  await tick()
  assert.equal(deletedDrafts.length, 0, '客户仍存在时不得删除草稿')
  assert.equal(modals.length, 1, '应弹出恢复确认')
  assert.ok(modals[0].content.indexOf('草稿客户甲') >= 0, '弹窗应展示按 id 补拉到的客户名')
  modals[0].success({ confirm: true })
  assert.equal(page.data.selectedCustomer && page.data.selectedCustomer.id, 42, '恢复后选中草稿客户')
  assert.equal(page.data.selectedCustomerId, 42)
  assert.equal(page.data.remark, '未完成备注')

  // 2b. 客户 404(已删除): 清草稿
  page = createUploadPage()
  page.data.categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }]
  deletedDrafts.length = 0
  modals.length = 0
  draftPayload = { customerId: 43, categoryId: 1 }
  customerById = (id, opts) => opts.success({ statusCode: 404, data: { code: 404, message: '客户不存在' } })
  page.tryRestoreDraft()
  await tick()
  await tick()
  assert.equal(deletedDrafts.length, 1, '404=客户已删, 草稿永久不可达应清除')
  assert.equal(modals.length, 0)

  // 2c. 网络错误: 保留草稿
  page = createUploadPage()
  page.data.categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }]
  deletedDrafts.length = 0
  modals.length = 0
  draftPayload = { customerId: 44, categoryId: 1 }
  customerById = (id, opts) => opts.fail({ errMsg: 'request:fail timeout' })
  page.tryRestoreDraft()
  await tick()
  await tick()
  assert.equal(deletedDrafts.length, 0, '临时网络失败不得删草稿(下次进入再试)')
  assert.equal(modals.length, 0)

  // ========== 3. 【C4】编辑清空发 '' / 新建维持 null ==========
  const trialCategory = { id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }
  // 3a. 编辑态: 四个文本字段全部清空
  page = createUploadPage()
  page.data.editId = '5'
  page.data.categories = [trialCategory]
  page.data.categoryParentNodes = [{ ...trialCategory, index: 0, children: [] }]
  page.data.selectedCategoryIndex = 0
  page.data.selectedCustomer = { id: 7, customerName: '编辑客户', industry: '' }
  page.data.selectedCustomerId = 7
  page.data.canToggleShare = true
  // 既有文件(existing+fileId) → 提交无待传项, 直接落库
  page.data.fileSlots = page.data.fileSlots.map((slot) => (
    slot.key === 'filePreId' ? { ...slot, hidden: false, files: [{ fileId: 301, fileName: '旧照片.jpg', existing: true }], fileName: '旧照片.jpg' } : slot
  ))
  requests.length = 0
  page.submit()
  await tick()
  await tick()
  const put = lastPut()
  assert.ok(put, '编辑态必须发出 PUT /api/incentive/records/5')
  assert.equal(put.method, 'PUT')
  assert.equal(put.url, '/api/incentive/records/5')
  assert.equal(put.data.remark, '', '编辑清空 remark 必须以空串落请求体')
  assert.equal(put.data.relatedProducts, '', '编辑清空 relatedProducts 必须以空串落请求体')
  assert.equal(put.data.specificProduct, '', '编辑清空 specificProduct 必须以空串落请求体')
  assert.equal(put.data.preInstallQuestion, '', '编辑清空 preInstallQuestion 必须以空串落请求体')
  assert.equal(put.data.customerId, 7, '编辑保持归属客户等值回传')
  assert.ok(!('categoryId' in put.data) && !('period' in put.data), '编辑态不得改分类/周期')

  // 3b. 新建态: 清空字段维持 null(剥除)语义
  page = createUploadPage()
  page.data.categories = [trialCategory]
  page.data.selectedCategoryIndex = 0
  page.data.selectedCustomer = { id: 9, customerName: '新建客户', industry: '' }
  page.data.selectedCustomerId = 9
  requests.length = 0
  page.submit()
  await tick()
  await tick()
  const post = lastPut()
  assert.ok(post && post.method === 'POST', '新建态必须 POST /api/incentive/records')
  assert.ok(!('remark' in post.data), '新建空字段维持 null(剥除)语义, 不发空串')
  assert.ok(!('relatedProducts' in post.data))
  assert.equal(post.data.customerId, 9)

  // ========== 4. 【B4 2026-10-21】防抖在途竞态: 新搜索不得被在途请求丢弃 ==========
  page = createUploadPage()
  const heldRequests = []
  const seenKeywords = []
  const origRequest = global.wx.request
  global.wx.request = (opts) => { requests.push(opts); seenKeywords.push((opts.data && opts.data.keyword) || '(空)'); heldRequests.push(opts) }
  page.setData({ 'customerSheet.keyword': '华东' })
  page.fetchCustomerSheetPage(1, false) // 第一发(华东): 应答挂起
  page.setData({ 'customerSheet.keyword': '苏州' })
  page.fetchCustomerSheetPage(1, false) // 第二发(苏州): 旧实现被 loading 早退直接丢弃
  assert.equal(heldRequests.length, 2, '在途时新搜索必须照发(旧实现 loading 早退丢弃且不补发, 复现"输入=华东仍显示旧关键词结果")')
  assert.equal(seenKeywords[0], '华东')
  assert.equal(seenKeywords[1], '苏州')
  // 后发的(苏州)先返回: 列表呈现苏州结果, loading 归位
  heldRequests[1].success(ok({ records: [{ id: 21, customerName: '苏州精密制造' }], total: 1 }))
  await tick()
  assert.equal(page.data.customerSheet.loading, false, '最新请求完成后 loading 归位')
  assert.equal(page.data.customerSheet.total, 1)
  assert.equal(page.data.customerSheet.filtered.length, 1)
  assert.equal(page.data.customerSheet.filtered[0].name, '苏州精密制造')
  // 先发的(华东)慢返回: 序号守卫丢弃, 不得覆盖苏州结果
  heldRequests[0].success(ok({ records: [{ id: 11, customerName: '华东老结果' }], total: 99 }))
  await tick()
  assert.equal(page.data.customerSheet.filtered[0].name, '苏州精密制造', '旧请求慢返回不得覆盖新结果')
  assert.equal(page.data.customerSheet.total, 1, 'total 不得被旧请求回写')
  global.wx.request = origRequest
}

main().then(() => console.log('PASS: incentive upload (C2 server-side customer search/pagination + draft refetch by id, C4 edit-mode empty-string clear)')).catch((e) => { console.error(e && e.stack || e); process.exit(1) })

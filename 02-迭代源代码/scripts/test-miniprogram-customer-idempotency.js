#!/usr/bin/env node
// 客户表单幂等键冒烟测试(2026-09-21 PR-05 小程序收尾轮; 2026-10-21 C5 重写键生命周期断言)
// 在 node 下驱动 utils/request.js 与 pages/customer/form/index.js,钉住:
// 1) request 层透传调用方 header 且同名以调用方为准;
// 2) api/customer.saveCustomerWithIndicator 带键发 Idempotency-Key 头,无键不发;
// 3) 【C5】键绑定表单实例(this._submitKey): 同一实例失败重试共用同一键(超时但服务端
//    已落库→后端回放同一客户 id, 不重复建客); 成功后清键; 另一个新客户(新实例/同实例
//    改客户名)必须换新键——旧实现是模块级变量, 跨实例复用会让后端把上一个客户的 id
//    回放给新客户, 界面却报"新增成功"(该缺陷曾被视为预期, 本测试已反转钉死);
// 4) 演示模式/编辑分支不带键。

const assert = require('node:assert/strict')

// ---- request 层:捕获 wx.request 的 header ----
let lastRequest = null
global.wx = {
  request: (opts) => { lastRequest = opts },
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: () => {},
  showModal: () => {},
  navigateBack: () => {},
  navigateTo: () => {},
  redirectTo: () => {},
  switchTab: () => {}
}
global.getApp = () => ({ globalData: { demoMode: false, currentUser: { id: 7 }, apiBaseUrl: '' } })
global.Page = () => {}

const request = require('../aigicrm-miniprogram/utils/request.js')
const api = require('../aigicrm-miniprogram/api/customer.js')

// --- 1. header 透传与同名覆盖 ---
request({ url: '/api/x', method: 'POST', data: {}, header: { 'Idempotency-Key': 'k-12345678', 'content-type': 'text/plain' } })
assert.equal(lastRequest.header['Idempotency-Key'], 'k-12345678', '自定义头必须透传')
assert.equal(lastRequest.header['content-type'], 'text/plain', '同名头以调用方为准')
assert.equal(lastRequest.header.Authorization, 'Bearer tok', 'token 头保留')

// --- 2. api 层: 有键发头/无键不发 ---
api.saveCustomerWithIndicator({ customer: {} }, 'key-abcdef12')
assert.equal(lastRequest.header['Idempotency-Key'], 'key-abcdef12', '组合提交带幂等键')
assert.equal(lastRequest.url.includes('/api/customer/full'), true)
api.saveCustomerWithIndicator({ customer: {} })
assert.equal(lastRequest.header['Idempotency-Key'], undefined, '未传键不头发(旧调用兼容)')

// --- 3. 表单键生命周期: 绑定表单实例 ---
let formPageConfig = null
global.Page = (config) => { formPageConfig = config }
require('../aigicrm-miniprogram/pages/customer/form/index.js')
assert.ok(formPageConfig, 'form page config captured')

let pending = null            // wx.request 捕获的 opts, 由测试手动 fail/success 驱动 Promise
global.wx.request = (opts) => { lastRequest = opts; pending = opts }
function respondFail() { pending.fail && pending.fail({ errMsg: 'request:fail' }) }
function respondOk() { pending.success && pending.success({ statusCode: 200, data: { code: 200, data: 1 } }) }

function createFormPage() {
  const page = Object.create(formPageConfig)
  page.data = JSON.parse(JSON.stringify(formPageConfig.data))
  page.data.form.customerName = '幂等测试客户'
  page.data.isEdit = false
  page.setData = function (patch, cb) { Object.assign(this.data, patch); if (cb) cb() }
  page.refreshFormView = () => {}
  page.indicatorPayload = () => null
  page.missingRequired = () => null
  page.checkDuplicateName = () => Promise.resolve({ exists: false })
  global.getApp = () => ({ globalData: { demoMode: false, currentUser: { id: 7 }, apiBaseUrl: '' } })
  return page
}

async function main() {
const tick = () => new Promise((r) => setImmediate(r))

// 3a. 首次提交(网络失败): 带实例键
let page = createFormPage()
page.submit()
await tick()
assert.ok(lastRequest.header['Idempotency-Key'], '真实新建提交必须带幂等键')
const key1 = lastRequest.header['Idempotency-Key']
assert.ok(/^mp-/.test(key1), '键格式 mp- 前缀: ' + key1)
respondFail()
await tick()

// 3b. 【C5】同一实例重试(超时场景): 必须复用同一键——服务端若已落库会回放同一客户 id
page.submit()
await tick()
assert.equal(lastRequest.header['Idempotency-Key'], key1, '同一份表单的重试必须共用同一键')
respondOk()
await tick()

// 3c. 【C5】成功后同实例再提交: 必须换新键(成功即清键, 下一次新建是全新提交)
page.submit()
await tick()
const key2 = lastRequest.header['Idempotency-Key']
assert.notEqual(key2, key1, '成功后必须换新键')
respondFail()
await tick()

// 3d. 【C5】另一个新客户=新表单实例: 不得复用上一个实例的键(旧模块级变量的缺陷场景:
// 超时报失败→用户另开表单建 B→复用 A 的键→后端回放 A 的 id→B 静默未建)
let page2 = createFormPage()
page2.submit()
await tick()
const key3 = lastRequest.header['Idempotency-Key']
assert.notEqual(key3, key2, '不同客户(不同表单实例)必须使用不同的键')
respondFail()
await tick()

// 3e. 【C5】同一实例改客户名(=换了客户)再提交: 必须换新键, 防止把上一个名字的回放 id 安到新客户
page2.data.form.customerName = '另一个新客户'
page2.submit()
await tick()
const key4 = lastRequest.header['Idempotency-Key']
assert.notEqual(key4, key3, '同实例改客户名后必须换新键')
respondOk()
await tick()

// 3f. 编辑分支(PUT)无幂等键
page = createFormPage()
page.data.isEdit = true
page.data.id = 9
page.submit()
await tick()
assert.equal(lastRequest.method, 'PUT', '编辑走 PUT')
assert.equal(lastRequest.header['Idempotency-Key'], undefined, '编辑分支无幂等键')
respondOk()
}

main().then(() => console.log('PASS: miniprogram customer idempotency (header passthrough, instance-bound key: retry same key, new customer/name new key, no key on edit)')).catch((e) => { console.error(e.message); process.exit(1) })

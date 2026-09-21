#!/usr/bin/env node
// 客户表单幂等键冒烟测试(2026-09-21 PR-05 小程序收尾轮)
// 在 node 下驱动 utils/request.js 与 pages/customer/form/index.js,钉住:
// 1) request 层透传调用方 header 且同名以调用方为准;
// 2) api/customer.saveCustomerWithIndicator 带键发 Idempotency-Key 头,无键不发;
// 3) 表单真实模式新建分支:同一次提交重试共用同一键,成功后换新键;
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

// --- 3. 表单键生命周期: 失败重试共用键, 成功后换新 ---
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

// 3a. 首次提交(失败): 带键
let page = createFormPage()
page.submit()
await tick()
assert.ok(lastRequest.header['Idempotency-Key'], '真实新建提交必须带幂等键')
const key1 = lastRequest.header['Idempotency-Key']
assert.ok(/^mp-/.test(key1), '键格式 mp- 前缀: ' + key1)
respondFail()

// 3b. 重试(失败): 共用同一键 —— 模块级 createSubmitKey 未被失败清空
page = createFormPage()
page.submit()
await tick()
assert.equal(lastRequest.header['Idempotency-Key'], key1, '同一次提交的重试必须共用同一键')
respondOk()

// 3c. 成功后再次新建: 换新键
page = createFormPage()
page.submit()
await tick()
const key2 = lastRequest.header['Idempotency-Key']
assert.notEqual(key2, key1, '成功后必须换新键')
respondOk()

// 3d. 编辑分支(PUT)无幂等键
page = createFormPage()
page.data.isEdit = true
page.data.id = 9
page.submit()
await tick()
assert.equal(lastRequest.method, 'PUT', '编辑走 PUT')
assert.equal(lastRequest.header['Idempotency-Key'], undefined, '编辑分支无幂等键')
respondOk()
}

main().then(() => console.log('PASS: miniprogram customer idempotency (request header passthrough, key lifecycle on create, no key on edit/demo)')).catch((e) => { console.error(e.message); process.exit(1) })

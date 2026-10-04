#!/usr/bin/env node
// 客户列表筛选冒烟测试(2026-09-21 对齐 PC 高级筛选轮)
// 在 node 下驱动 pages/customer/index.js 与 pages/customer/filter/index.js 的视图模型,钉住:
// 1) 快捷「保护状态」与高级「保护」都走 protectionStatus 域(active/expiring/expired),不再误传 status;
// 2) status 域只收 normal/warning(高级筛选页选项即合法域);
// 3) province/city 透传(后端 LIKE 包含);
// 4) 演示模式本地过滤对齐 PC contains 语义; 5) 高级筛选 apply 草稿字段齐全。
// 【D27 2026-10-04】筛选参数拼装已收敛到 pages/customer/shared.js 的 buildCustomerFilterParams
// (列表页不再持有 buildCustomerParams 方法),本脚本调用点同步改为直接驱动 shared 函数。

const assert = require('node:assert/strict')
const { buildCustomerFilterParams } = require('../aigicrm-miniprogram/pages/customer/shared.js')

let listPageConfig = null
global.Page = (config) => { listPageConfig = config }
global.getApp = () => ({ globalData: { demoMode: true, currentUser: { id: 7 }, apiBaseUrl: '' } })
global.wx = {
  request: () => {},
  getStorageSync: () => '',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: () => {},
  showModal: () => {},
  navigateBack: () => {},
  navigateTo: () => {},
  switchTab: () => {}
}

const listModule = require('../aigicrm-miniprogram/pages/customer/index.js')
assert.ok(listPageConfig, 'list page config captured')

function createListPage() {
  const page = Object.create(listPageConfig)
  page.data = JSON.parse(JSON.stringify(listPageConfig.data))
  page.setData = function (patch) { Object.assign(this.data, patch) }
  page.setCustomerData = listPageConfig.setCustomerData
  return page
}

// --- 1. 筛选参数拼装(shared.buildCustomerFilterParams): 保护域/状态域分离 + 省市透传 ---
{
  const page = createListPage()
  page.data.statusFilter = 'expiring'
  page.data.advancedFilter = { industry: '煤矿', status: 'warning', deptId: '', ownerId: '11', protect: 'expired', province: '山西', city: '太原' }
  const params = buildCustomerFilterParams(page.data)
  assert.equal(params.industry, '煤矿')
  assert.equal(params.status, 'warning', '高级客户状态应走 status 域')
  assert.equal(params.ownerId, '11')
  assert.ok(!('deptId' in params) || !params.deptId)
  assert.equal(params.protectionStatus, 'expiring,expired', '快捷+高级保护筛选应合并为逗号多值 protectionStatus')
  assert.equal(params.province, '山西')
  assert.equal(params.city, '太原')
  assert.ok(!('status' in params) || params.status !== 'active', '保护状态值不得落入 status 参数(历史回归)')
}

{
  const page = createListPage()
  const params = buildCustomerFilterParams(page.data)
  assert.ok(!('protectionStatus' in params), '未选保护状态时不应产生 protectionStatus 参数')
  assert.ok(!('status' in params), '未选客户状态时不应产生 status 参数')
}

// --- 2. 演示模式本地过滤: 保护/行业/省市 contains 语义 ---
{
  const page = createListPage()
  // 种入带省市/保护状态的演示客户, 过滤断言才有非空样本
  global.wx.getStorageSync = (k) => (k === 'demoCustomers' ? [
    { id: 901, customerName: '内蒙古鄂尔多斯化工', industry: '化工', province: '内蒙古自治区', city: '鄂尔多斯市', status: '保护期', devType: '成熟创新' },
    { id: 902, customerName: '山西太原煤业', industry: '煤矿', province: '山西省', city: '太原市', status: '正常', devType: '成熟创新' }
  ] : '')
  page.data.keyword = ''
  page.data.filter = '全部'
  page.data.statusFilter = ''
  page.data.advancedFilter = { industry: '', status: '', deptId: '', ownerId: '', protect: '', province: '内蒙古', city: '鄂尔多斯' }
  page.loadCustomers()
  const rows = page.data.customers
  assert.equal(rows.length, 1, '省市 contains 过滤应只保留内蒙古鄂尔多斯客户')
  assert.equal(rows[0].name, '内蒙古鄂尔多斯化工')

  page.data.advancedFilter.province = ''
  page.data.advancedFilter.city = ''
  page.loadCustomers()
  assert.equal(page.data.customers.length, 4, '清空省市后内置 2 条 + 种入 2 条全部返回')
  global.wx.getStorageSync = () => ''
}

// --- 3. 高级筛选页: 选项域合法 + apply 草稿字段齐全 ---
let filterPageConfig = null
global.Page = (config) => { filterPageConfig = config }
require('../aigicrm-miniprogram/pages/customer/filter/index.js')
assert.ok(filterPageConfig, 'filter page config captured')

{
  const page = Object.create(filterPageConfig)
  page.data = JSON.parse(JSON.stringify(filterPageConfig.data))
  page.setData = function (patch) { Object.assign(this.data, patch) }
  page.applyState = filterPageConfig.applyState

  const statusDomain = page.data.statusOptions.map((o) => o.value).filter(Boolean)
  assert.deepEqual(statusDomain.sort(), ['normal', 'warning'].sort(), '客户状态域只允许 normal/warning')
  const protectDomain = page.data.protectOptions.map((o) => o.value).filter(Boolean)
  assert.deepEqual(protectDomain.sort(), ['active', 'expired', 'expiring'].sort(), '保护状态域应为 active/expiring/expired')

  page.applyState({ province: ' 山西 ', city: '太原' })
  page.data.selectedIndustryIndex = 1
  page.data.industryOptions = [{ value: '', label: '全部行业' }, { value: '煤矿', label: '煤矿' }]
  page.data.selectedProtectIndex = 2
  let saved = null
  global.wx.setStorageSync = (k, v) => { saved = { key: k, value: v } }
  global.wx.navigateBack = () => {}
  page.apply()
  assert.equal(saved.key, 'customerFilterDraft')
  assert.equal(saved.value.industry, '煤矿')
  assert.equal(saved.value.protect, 'expiring')
  assert.equal(saved.value.province, '山西', '省市应 trim 后落草稿')
  assert.equal(saved.value.city, '太原')
}

// --- 4. 列表页消费草稿: 新字段齐全 + hasAdvancedFilter 感知 ---
{
  const page = createListPage()
  page.didInitialShow = true
  page.hasLoaded = true
  let draft = { keyword: '', devType: '全部', industry: '化工', status: '', protect: 'expired', province: '山西', city: '', deptId: '', ownerId: '' }
  global.wx.getStorageSync = (k) => (k === 'customerFilterDraft' ? draft : '')
  page.onShow()
  assert.equal(page.data.advancedFilter.protect, 'expired')
  assert.equal(page.data.advancedFilter.province, '山西')
  assert.equal(page.data.hasAdvancedFilter, true)
}

console.log('PASS: customer filter smoke — status/protection 域分离, protectionStatus 合并, 省市 contains, 草稿字段齐全')

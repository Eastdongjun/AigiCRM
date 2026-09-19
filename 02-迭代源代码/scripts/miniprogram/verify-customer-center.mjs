import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const wsEndpoint = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421'
const screenshotDir = process.env.MINIPROGRAM_AUTOMATOR_SCREENSHOT_DIR || ''
const shouldScreenshot = Boolean(screenshotDir)

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function eventWithDataset(dataset) {
  return { currentTarget: { dataset } }
}

async function textList(elements) {
  const texts = []
  for (const element of elements) texts.push(await element.text())
  return texts
}

async function screenshot(miniProgram, name) {
  if (!shouldScreenshot) return
  const path = resolve(screenshotDir, name)
  mkdirSync(dirname(path), { recursive: true })
  await miniProgram.screenshot({ path })
}

async function installCustomerActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__customerNavigationCalls = []
    globalThis.__customerToastCalls = []
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__customerNavigationCalls = globalThis.__customerNavigationCalls || []
    globalThis.__customerNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__customerToastCalls = globalThis.__customerToastCalls || []
    globalThis.__customerToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreCustomerActionMocks(miniProgram) {
  for (const method of ['navigateTo', 'showToast']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableCustomerDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化客户用户' }
    app.globalData.permissions = [
      'customer:view',
      'customer:add',
      'customer:edit',
      'contact:view',
      'finance:view',
      'approval:view',
      'sea:view'
    ]
  })
}

async function verifyCustomerListPage(miniProgram) {
  await enableCustomerDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/customer/index')
  await page.waitFor(1000)

  await page.setData({
    keyword: '',
    filter: '全部',
    customers: [
      { id: 501, name: '华东智能制造集团', industry: '制造业', ownerName: '张三', officeName: '华东办事处', status: '保护期', devType: '成熟创新' },
      { id: 502, name: '山西晋能集团中阳煤业', industry: '煤矿', ownerName: '李四', officeName: '晋蒙业务部', status: '正常', devType: '突破开拓' }
    ],
    countCards: [
      { label: '全部', value: 2 },
      { label: '我的', value: 1 },
      { label: '共享', value: 1 },
      { label: '公海', value: 0 }
    ],
    advancedFilter: { industry: '制造业', status: '保护期', deptId: 21, ownerId: 1001 },
    canAdd: true,
    canViewSea: true,
    loading: false
  })
  await page.waitFor(500)

  assert((await page.$$('.customer-card')).length === 2, 'customer list cards are missing')
  assert((await page.$$('.customer-search')).length === 1, 'customer search area is missing')
  assert((await page.$$('.filter-pill')).length >= 1, 'customer filter controls are missing')

  const pageText = [
    ...(await textList(await page.$$('.customer-title'))),
    ...(await textList(await page.$$('.customer-name'))),
    ...(await textList(await page.$$('.customer-meta'))),
    ...(await textList(await page.$$('.owner-line'))),
    ...(await textList(await page.$$('.filter-count')))
  ].join('\n')
  for (const expected of ['客户', '华东智能制造集团', '山西晋能集团中阳煤业', '制造业', '华东办事处', '共 2 家']) {
    assert(pageText.includes(expected), `customer list missing ${expected}`)
  }
  await page.setData({ keyword: '华东', filter: '成熟创新' })
  const params = await page.callMethod('buildCustomerParams')
  assert(params.keyword === '华东', `customer query params must send keyword: ${JSON.stringify(params)}`)
  assert(params.devType === '成熟创新', `customer query params must send devType: ${JSON.stringify(params)}`)
  assert(params.industry === '制造业', `customer query params must send industry like PC: ${JSON.stringify(params)}`)
  assert(params.status === '保护期', `customer query params must send status like PC: ${JSON.stringify(params)}`)
  assert(params.deptId === 21, `customer query params must send deptId like PC: ${JSON.stringify(params)}`)
  assert(params.ownerId === 1001, `customer query params must send ownerId like PC: ${JSON.stringify(params)}`)

  await page.callMethod('openDetail', eventWithDataset({ id: 501 }))
  await page.callMethod('addCustomer')
  await page.callMethod('openSea')
  await page.waitFor(300)
  const navigations = await miniProgram.evaluate(() => globalThis.__customerNavigationCalls || [])
  for (const expectedUrl of ['/pages/customer/detail/index?id=501&from=customer', '/pages/customer/form/index', '/pages/sea/index']) {
    assert(navigations.some((item) => item.url === expectedUrl), `customer list navigation missing ${expectedUrl}: ${JSON.stringify(navigations)}`)
  }
  await screenshot(miniProgram, 'customer-center-list.png')
}

async function verifyCustomerDetailPage(miniProgram) {
  await enableCustomerDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/customer/detail/index?id=501')
  await page.waitFor(1000)

  await page.setData({
    id: '501',
    loading: false,
    canEdit: true,
    canDelete: false,
    canViewFinance: true,
    canViewContacts: true,
    detail: {
      id: 501,
      customerCode: 'C501',
      customerName: '华东智能制造集团',
      industry: '制造业',
      devType: '成熟创新',
      capacity: '800',
      terminalLabel: '终端客户',
      ownerName: '张三',
      managerName: '王总',
      deptName: '销售一部',
      officeName: '华东办事处',
      regionName: '华东大区',
      groupName: '华东智能制造集团',
      branchCompany: '华东制造分公司',
      unitName: '浦东制造基地',
      areaText: '上海市浦东新区',
      address: '上海市浦东新区',
      status: '保护期',
      registerDate: '2026-01-20',
      lastInvoiceDate: '2026-08-30',
      expireDate: '2026-12-31',
      invoiceName: '华东智能制造集团有限公司',
      remark: '自动化客户详情备注'
    },
    contacts: [
      { id: 1, name: '王经理', mobile: '13800000000' },
      { id: 2, contactName: '赵工', phone: '13900000000' }
    ],
    logs: [
      { id: 1, content: '完成现场拜访并确认试用计划', createdAt: '2026-09-15 10:00' }
    ],
    invoiceSummary: { totalAmount: 160 },
    paymentSummary: { totalAmount: 125, unpaidAmount: 35 },
    indicator: { relationshipScore: 2, indicator02: 3, indicator03: 2, indicator04: 3, indicator05: 2 },
    indicatorSummary: {
      invoiceAmount: 160,
      paymentAmount: 125,
      invoiceMet: true,
      paymentMet: true,
      bothMet: true,
      scoreTotal: 12,
      scoreMet: true,
      coreMet: true
    }
  })
  await page.waitFor(500)

  assert((await page.$$('.customer-overview-card')).length === 1, 'customer overview card is missing')
  assert((await page.$$('.customer-section-card')).length >= 5, 'customer detail sections are missing')
  assert((await page.$$('.indicator-summary-item')).length === 4, 'customer indicator summary grid is missing')

  const pageText = [
    ...(await textList(await page.$$('.customer-overview-title'))),
    ...(await textList(await page.$$('.detail-row'))),
    ...(await textList(await page.$$('.section-title'))),
    ...(await textList(await page.$$('.indicator-summary-item'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['华东智能制造集团', '产能(万吨)', '终端客户', '销售一部', '王总', '华东制造分公司', '浦东制造基地', '注册日', '最近开票', '自动化客户详情备注', '财务摘要', '客情.商务关系深度', '核心客户六项指标', '开票门槛', '回款门槛', '编辑六项指标', '全部联系人', '客户分享', '完整跟进', '新增跟进记录']) {
    assert(pageText.includes(expected), `customer detail missing ${expected}`)
  }

  await page.callMethod('openFinance')
  await page.callMethod('openContacts')
  await page.callMethod('openShares')
  await page.callMethod('openTimeline')
  await page.callMethod('openLogForm')
  await page.callMethod('openIndicatorForm')
  await page.callMethod('submitExtendApproval')
  await page.callMethod('selectRelationship', eventWithDataset({ score: 3 }))
  await page.waitFor(300)

  const navigations = await miniProgram.evaluate(() => globalThis.__customerNavigationCalls || [])
  for (const expectedUrl of [
    '/pages/customer/finance/index?id=501',
    '/pages/customer/contact/index?id=501',
    '/pages/customer/share/index?id=501',
    '/pages/customer/timeline/index?id=501',
    '/pages/customer/log/index?id=501',
    '/pages/customer/indicator/index?id=501'
  ]) {
    assert(navigations.some((item) => item.url === expectedUrl), `customer detail navigation missing ${expectedUrl}: ${JSON.stringify(navigations)}`)
  }

  const toasts = await miniProgram.evaluate(() => globalThis.__customerToastCalls || [])
  assert(toasts.some((item) => item.title === '延期申请已提交'), `extend approval toast is missing: ${JSON.stringify(toasts)}`)
  assert(toasts.some((item) => item.title === '客情已保存'), `relationship save toast is missing: ${JSON.stringify(toasts)}`)
  const relationshipScore = await page.data('indicator.relationshipScore')
  assert(relationshipScore === 3, `relationship score did not update: ${relationshipScore}`)
  await screenshot(miniProgram, 'customer-center-detail.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installCustomerActionMocks(miniProgram)
    await verifyCustomerListPage(miniProgram)
    await verifyCustomerDetailPage(miniProgram)
  } finally {
    await restoreCustomerActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified customer list, detail, finance/contact/share/timeline/indicator navigation, extension submission, and relationship update UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

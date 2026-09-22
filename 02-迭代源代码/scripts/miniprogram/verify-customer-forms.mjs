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

function eventWithDataset(dataset, value) {
  return { currentTarget: { dataset }, detail: { value } }
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

async function installActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__customerFormsToastCalls = []
    globalThis.__customerFormsNavigationCalls = []
    globalThis.__customerFormsBackCalls = []
    globalThis.__customerFormsModalCalls = []
    globalThis.__customerFormsPhoneCalls = []
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__customerFormsToastCalls = globalThis.__customerFormsToastCalls || []
    globalThis.__customerFormsToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__customerFormsNavigationCalls = globalThis.__customerFormsNavigationCalls || []
    globalThis.__customerFormsNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateBack', function (options) {
    globalThis.__customerFormsBackCalls = globalThis.__customerFormsBackCalls || []
    globalThis.__customerFormsBackCalls.push({ delta: options && options.delta })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showModal', function (options) {
    globalThis.__customerFormsModalCalls = globalThis.__customerFormsModalCalls || []
    globalThis.__customerFormsModalCalls.push({ title: options && options.title, content: options && options.content, editable: options && options.editable })
    if (options && typeof options.success === 'function') options.success({ confirm: true, cancel: false, content: '自动化集团' })
  })
  await miniProgram.mockWxMethod('makePhoneCall', function (options) {
    globalThis.__customerFormsPhoneCalls = globalThis.__customerFormsPhoneCalls || []
    globalThis.__customerFormsPhoneCalls.push({ phoneNumber: options && options.phoneNumber })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreActionMocks(miniProgram) {
  for (const method of ['showToast', 'navigateTo', 'navigateBack', 'showModal', 'makePhoneCall']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableDemoUser(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, userId: 1, username: 'customer-form-auto', realName: '客户表单自动化用户', roles: ['admin'], admin: true }
    app.globalData.permissions = ['customer:view', 'customer:add', 'customer:edit', 'customer:delete', 'contact:view', 'finance:view']
    wx.setStorageSync('demoCustomers', [])
    wx.setStorageSync('demoContacts', {})
    wx.setStorageSync('demoFinance', {})
  })
}

async function verifyCustomerForm(miniProgram) {
  await enableDemoUser(miniProgram)
  const page = await miniProgram.reLaunch('/pages/customer/form/index')
  await page.waitFor(800)

  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.section-title'))),
    ...(await textList(await page.$$('.tip'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['新增客户', '基本信息', '业务分类', '归属与负责人', '地址与日期', '客户保存后可继续添加联系人', '取消']) {
    assert(pageText.includes(expected), `customer form missing ${expected}`)
  }

  await page.callMethod('submit')
  await page.waitFor(100)
  await page.setData({
    form: {
      ...await page.data('form'),
      customerName: '自动化客户一号',
      invoiceName: '自动化客户一号有限公司',
      industry: '煤矿',
      devType: '成熟创新',
      hasTerminal: 1,
      regionId: 1,
      officeId: 11,
      ownerId: 1,
      managerId: 2,
      groupName: '陕煤集团',
      province: '陕西省',
      city: '榆林市',
      district: '神木市',
      address: '榆神工业区',
      remark: '自动化新增客户'
    },
    industryIndex: 0,
    devTypeIndex: 0,
    terminalIndex: 1,
    regionIndex: 0,
    officeIndex: 0,
    ownerIndex: 0,
    managerIndex: 1,
    groupIndex: 0,
    region: ['陕西省', '榆林市', '神木市']
  })
  await page.callMethod('chooseGroup', { detail: { value: 2 } })
  await page.callMethod('submit')
  await page.waitFor(700)

  const toasts = await miniProgram.evaluate(() => globalThis.__customerFormsToastCalls || [])
  const backs = await miniProgram.evaluate(() => globalThis.__customerFormsBackCalls || [])
  const modals = await miniProgram.evaluate(() => globalThis.__customerFormsModalCalls || [])
  const customers = await miniProgram.evaluate(() => wx.getStorageSync('demoCustomers') || [])
  assert(toasts.some((item) => item.title === '请输入客户名称'), `customer form empty-name toast missing: ${JSON.stringify(toasts)}`)
  assert(toasts.some((item) => item.title === '新增成功'), `customer form save toast missing: ${JSON.stringify(toasts)}`)
  assert(modals.some((item) => item.title === '输入集团名称'), `customer group manual modal missing: ${JSON.stringify(modals)}`)
  assert(backs.length >= 1, `customer form did not navigate back after save: ${JSON.stringify(backs)}`)
  assert(customers.some((item) => item.customerName === '自动化客户一号'), `demo customer was not saved: ${JSON.stringify(customers)}`)
  await screenshot(miniProgram, 'customer-form.png')
}

async function verifyContacts(miniProgram) {
  await enableDemoUser(miniProgram)
  await miniProgram.evaluate(() => wx.setStorageSync('demoContacts', { 501: [{ id: 'c-1', name: '自动化联系人', position: '采购负责人', phone: '13800000001', email: 'contact@example.com', remark: '重点维护联系人' }] }))
  let page = await miniProgram.reLaunch('/pages/customer/contact/index?id=501')
  await page.waitFor(700)

  assert((await page.$$('.contact-card')).length >= 3, 'contact cards are missing')
  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.contact-card'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['联系人', '自动化联系人', '新增联系人', '拨打电话', '编辑', '删除']) {
    assert(pageText.includes(expected), `contact list missing ${expected}`)
  }

  const contactRows = await page.data('contacts')
  const contactIndex = contactRows.findIndex((item) => item.id === 'c-1')
  assert(contactIndex >= 0, `seeded contact c-1 not found: ${JSON.stringify(contactRows)}`)
  await page.callMethod('call', eventWithDataset({ phone: '138****0001' }))
  await page.callMethod('call', eventWithDataset({ phone: '13800000001' }))
  await page.callMethod('openDetail', eventWithDataset({ index: contactIndex }))
  await page.callMethod('editContact', eventWithDataset({ index: contactIndex }))
  await page.callMethod('deleteContact', eventWithDataset({ index: contactIndex }))
  await page.waitFor(1000)

  const navigations = await miniProgram.evaluate(() => globalThis.__customerFormsNavigationCalls || [])
  const phones = await miniProgram.evaluate(() => globalThis.__customerFormsPhoneCalls || [])
  const toasts = await miniProgram.evaluate(() => globalThis.__customerFormsToastCalls || [])
  const modals = await miniProgram.evaluate(() => globalThis.__customerFormsModalCalls || [])
  assert(toasts.some((item) => item.title === '手机号已脱敏'), `masked phone toast missing: ${JSON.stringify(toasts)}`)
  assert(phones.some((item) => item.phoneNumber === '13800000001'), `phone call missing: ${JSON.stringify(phones)}`)
  const detailNavigation = navigations.find((item) => item.url.includes('/pages/customer/contact/detail/index?'))
  assert(detailNavigation, `contact detail navigation missing: ${JSON.stringify(navigations)}`)
  assert(detailNavigation.url.includes('remark='), `contact detail navigation must carry remark: ${JSON.stringify(detailNavigation)}`)
  assert(decodeURIComponent(detailNavigation.url).includes('重点维护联系人'), `contact detail navigation remark missing: ${JSON.stringify(detailNavigation)}`)
  assert(navigations.some((item) => item.url.includes('/pages/customer/contact/form/index?id=501&contactId=c-1')), `contact edit navigation missing: ${JSON.stringify(navigations)}`)
  assert(modals.some((item) => item.title === '删除联系人'), `contact delete confirmation missing: ${JSON.stringify(modals)}`)
  await screenshot(miniProgram, 'customer-contact-list.png')

  page = await miniProgram.reLaunch('/pages/customer/contact/form/index?id=501')
  await page.waitFor(500)
  await page.callMethod('submit')
  await page.callMethod('input', eventWithDataset({ field: 'name' }, '新增自动化联系人'))
  await page.callMethod('input', eventWithDataset({ field: 'position' }, '技术负责人'))
  await page.callMethod('input', eventWithDataset({ field: 'phone' }, '13900000002'))
  await page.callMethod('submit')
  await page.waitFor(700)
  const contacts = await miniProgram.evaluate(() => wx.getStorageSync('demoContacts')['501'] || [])
  assert(contacts.some((item) => item.name === '新增自动化联系人'), `new contact was not saved: ${JSON.stringify(contacts)}`)
  await screenshot(miniProgram, 'customer-contact-form.png')
}

async function verifyFinance(miniProgram) {
  await enableDemoUser(miniProgram)
  await miniProgram.evaluate(() => wx.setStorageSync('demoFinance', { 501: { invoices: [{ id: 'inv-1', invoiceNo: 'INV-AUTO-1', invoiceType: '增值税专票', invoiceCompany: '自动化开票单位', amount: 120000, invoiceDate: '2026-09-01', status: '已开票' }], payments: [{ id: 'pay-1', paymentNo: 'PAY-AUTO-1', amount: 60000, paymentDate: '2026-09-08', paymentMethod: '银行转账', remark: '自动化回款备注', status: '已回款' }] } }))
  let page = await miniProgram.reLaunch('/pages/customer/finance/index?id=501')
  await page.waitFor(700)

  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.tabs'))),
    ...(await textList(await page.$$('.finance-row'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['财务明细', '开票明细', '回款明细', 'INV-AUTO-1', '增值税专票', '自动化开票单位', '新增开票', '删除']) {
    assert(pageText.includes(expected), `finance invoice page missing ${expected}`)
  }

  await page.callMethod('openForm')
  const invoiceRows = await page.data('invoices')
  const invoiceIndex = invoiceRows.findIndex((item) => item.id === 'inv-1')
  assert(invoiceIndex >= 0, `seeded invoice inv-1 not found: ${JSON.stringify(invoiceRows)}`)
  await page.callMethod('deleteRecord', eventWithDataset({ mode: 'invoice', index: invoiceIndex }))
  await page.waitFor(300)
  await page.callMethod('switchTab', eventWithDataset({ tab: 'payment' }))
  await page.waitFor(100)
  pageText = [
    ...(await textList(await page.$$('.finance-row'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['PAY-AUTO-1', '银行转账', '自动化回款备注']) {
    assert(pageText.includes(expected), `finance payment row missing ${expected}: ${pageText}`)
  }
  const paymentRows = await page.data('payments')
  const paymentIndex = paymentRows.findIndex((item) => item.id === 'pay-1')
  assert(paymentIndex >= 0, `seeded payment pay-1 not found: ${JSON.stringify(paymentRows)}`)
  await page.callMethod('deleteRecord', eventWithDataset({ mode: 'payment', index: paymentIndex }))
  await page.waitFor(300)
  const modals = await miniProgram.evaluate(() => globalThis.__customerFormsModalCalls || [])
  assert(modals.some((item) => item.title === '删除开票记录'), `invoice delete confirmation missing: ${JSON.stringify(modals)}`)
  assert(modals.some((item) => item.title === '删除回款记录'), `payment delete confirmation missing: ${JSON.stringify(modals)}`)
  await screenshot(miniProgram, 'customer-finance-list.png')

  page = await miniProgram.reLaunch('/pages/customer/finance/form/index?id=501&mode=invoice')
  await page.waitFor(500)
  await page.callMethod('submit')
  await page.callMethod('input', eventWithDataset({ field: 'invoiceDate' }, '2026-09-15'))
  await page.callMethod('input', eventWithDataset({ field: 'invoiceNumber' }, 'INV-AUTO-2'))
  await page.callMethod('input', eventWithDataset({ field: 'amount' }, '180000'))
  await page.callMethod('submit')
  await page.waitFor(700)
  const afterInvoice = await miniProgram.evaluate(() => wx.getStorageSync('demoFinance')['501'])
  assert(afterInvoice.invoices.some((item) => item.invoiceNumber === 'INV-AUTO-2'), `invoice form did not save: ${JSON.stringify(afterInvoice)}`)
  await screenshot(miniProgram, 'customer-finance-form.png')
}

async function verifyShareLogIndicator(miniProgram) {
  await enableDemoUser(miniProgram)
  let page = await miniProgram.reLaunch('/pages/customer/share/index?id=501')
  await page.waitFor(700)
  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.share-card'))),
    ...(await textList(await page.$$('.share-form'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['客户分享', '李经理', '新增分享', '确认分享', '撤回共享']) {
    assert(pageText.includes(expected), `customer share missing ${expected}`)
  }
  await page.callMethod('addShares')
  await page.callMethod('selectUsers', { detail: { value: ['2', '3'] } })
  await page.callMethod('addShares')
  await page.setData({ shares: [{ id: 9, sharedToUserId: 2, sharedToUserName: '王工', createdAt: '2026-09-15 10:00' }] })
  await page.callMethod('removeShare', eventWithDataset({ index: 0 }))
  await page.waitFor(300)

  page = await miniProgram.reLaunch('/pages/customer/log/index?id=501')
  await page.waitFor(500)
  await page.callMethod('submit')
  await page.callMethod('onContentInput', { detail: { value: '自动化跟进记录：已完成方案沟通。' } })
  await page.callMethod('submit')
  await page.waitFor(700)

  page = await miniProgram.reLaunch('/pages/customer/indicator/index?id=501')
  await page.waitFor(700)
  assert((await page.$$('.metric-option')).length === 5, 'indicator metric cards are missing')
  pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.form-section-title'))),
    ...(await textList(await page.$$('.threshold-card'))),
    ...(await textList(await page.$$('.metric-option'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['编辑客户指标', '核心客户六项指标', '高层', '渠道', '基层', '产品', '项目', '保存指标']) {
    assert(pageText.includes(expected), `indicator form missing ${expected}`)
  }
  await page.callMethod('selectScore', eventWithDataset({ field: 'relationshipScore', score: 3 }))
  await page.callMethod('selectScore', eventWithDataset({ field: 'indicator02', score: 3 }))
  await page.callMethod('submit')
  await page.waitFor(300)
  const form = await page.data('form')
  const toasts = await miniProgram.evaluate(() => globalThis.__customerFormsToastCalls || [])
  const modals = await miniProgram.evaluate(() => globalThis.__customerFormsModalCalls || [])
  assert(form.relationshipScore === 3 && form.indicator02 === 3, `indicator scores did not update: ${JSON.stringify(form)}`)
  assert(modals.some((item) => item.title === '撤回分享'), `share revoke confirmation missing: ${JSON.stringify(modals)}`)
  for (const expected of ['请选择分享对象', '已分享', '请填写跟进内容', '已记录', '指标已保存']) {
    assert(toasts.some((item) => item.title === expected), `toast missing ${expected}: ${JSON.stringify(toasts)}`)
  }
  await screenshot(miniProgram, 'customer-share-log-indicator.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installActionMocks(miniProgram)
    await verifyCustomerForm(miniProgram)
    await verifyContacts(miniProgram)
    await verifyFinance(miniProgram)
    await verifyShareLogIndicator(miniProgram)
  } finally {
    await restoreActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified mobile customer forms, contact CRUD, finance CRUD, share actions, follow-up log, and indicator editing UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

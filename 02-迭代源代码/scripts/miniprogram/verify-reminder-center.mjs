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

async function installReminderActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__reminderNavigationCalls = []
    globalThis.__reminderToastCalls = []
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__reminderNavigationCalls = globalThis.__reminderNavigationCalls || []
    globalThis.__reminderNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__reminderToastCalls = globalThis.__reminderToastCalls || []
    globalThis.__reminderToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreReminderActionMocks(miniProgram) {
  for (const method of ['navigateTo', 'showToast']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableReminderDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化提醒用户' }
    app.globalData.permissions = ['reminder:view', 'customer:view']
  })
}

function reminderRows() {
  return [
    {
      id: 901,
      customerId: 501,
      customerName: '华东智能制造集团',
      customerCode: 'C-20260915-501',
      customerStatus: 'protected',
      industry: '制造业',
      region: '上海',
      type: 'PROTECT_EXPIRING',
      typeLabel: '到期提醒',
      processActionLabel: '已跟进',
      processActionDisplay: '已跟进',
      canExtend: true,
      status: 'PENDING',
      statusText: '待处理',
      timingText: '剩余 15 天到期',
      expireDate: '2026-09-30',
      remainingDays: 15,
      lastInvoiceDate: '2026-08-31',
      content: '客户保护期即将到期，请跟进延期或释放',
      ownerName: '张三',
      officeName: '华东办',
      // 【2026-09-22 校准】ownerDisplay 等由加载路径 normalizeReminder 生成, 沙箱直注须自带
      ownerDisplay: '张三',
      officeDisplay: '华东办',
      statusDisplay: '待处理',
      expireDateDisplay: '2026-09-30',
      customerCodeDisplay: 'C-20260915-501',
      customerStatusDisplay: 'protected',
      industryDisplay: '制造业',
      regionDisplay: '上海',
      reminderIdDisplay: '901',
      typeDisplay: '到期提醒',
      timingDisplay: '剩余 15 天到期',
      remainingDaysDisplay: '15 天',
      lastInvoiceDateDisplay: '2026-08-31',
      createdAtDisplay: '2026-09-15 09:30',
      processedAtDisplay: '—',
      updatedAtDisplay: '2026-09-15 10:00',
      createdAt: '2026-09-15 09:30',
      updatedAt: '2026-09-15 10:00',
      processedAt: ''
    },
    {
      id: 902,
      customerId: 502,
      customerName: '山西晋能集团中阳煤业',
      customerCode: 'C-20260915-502',
      customerStatus: 'active',
      industry: '能源',
      region: '山西',
      type: 'INVOICE_OVERDUE',
      typeLabel: '开票超期提醒',
      processActionLabel: '已读',
      processActionDisplay: '已读',
      ownerDisplay: '李四',
      officeDisplay: '山西办',
      statusDisplay: '待处理',
      expireDateDisplay: '2026-10-10',
      canExtend: false,
      status: 'PENDING',
      statusText: '待处理',
      timingText: '已超期 — 天未开票',
      expireDate: '2026-10-05',
      remainingDays: 20,
      lastInvoiceDate: '2026-07-20',
      content: '客户开票周期异常，请补充跟进记录',
      ownerName: '李四',
      officeName: '华北办',
      createdAt: '2026-09-14 16:20',
      updatedAt: '2026-09-15 08:40',
      processedAt: ''
    }
  ]
}

async function verifyReminderCenterPage(miniProgram) {
  await enableReminderDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/reminder/index')
  await page.waitFor(1000)

  // 【2026-09-22 校准】reminder 页走 reminderView, 须 applyState 重建
  await page.callMethod('applyState', {
    reminders: reminderRows(),
    pendingCount: 2,
    keyword: '',
    viewMode: 'pending',
    loading: false,
    selectedReminder: null,
    deptOptions: [{ id: '', name: '全部部门' }, { id: 21, name: '华东办' }],
    ownerOptions: [{ id: '', name: '全部负责人' }, { id: 1001, name: '张三' }],
    selectedDeptIndex: 0,
    selectedOwnerIndex: 0
  })
  await page.waitFor(500)

  assert((await page.$$('.reminder-mode-card')).length === 1, 'reminder mode tabs are missing')
  assert((await page.$$('.reminder-card')).length === 2, 'reminder cards are missing')
  assert((await page.$$('.reminder-mode.active')).length === 1, 'reminder active tab is missing')

  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.hero-sub'))),
    ...(await textList(await page.$$('.reminder-mode'))),
    ...(await textList(await page.$$('.reminder-filter-chip'))),
    ...(await textList(await page.$$('.reminder-card')))
  ].join('\n')
  for (const expected of ['提醒中心', '当前待处理 2 条', '待处理', '到期客户', '全部部门', '全部负责人', '华东智能制造集团', '客户保护期即将到期', '负责人 张三', '部门 华东办', '状态 待处理', '申请延期', '已跟进', '已读']) {
    assert(pageText.includes(expected), `reminder center missing ${expected}`)
  }
  await page.callMethod('applyState', { keyword: '华东', selectedDeptIndex: 1, selectedOwnerIndex: 1 })
  const params = await page.callMethod('buildReminderParams')
  assert(params.keyword === '华东', `reminder query params must send keyword: ${JSON.stringify(params)}`)
  assert(params.deptId === 21, `reminder query params must send deptId like PC: ${JSON.stringify(params)}`)
  assert(params.ownerId === 1001, `reminder query params must send ownerId like PC: ${JSON.stringify(params)}`)

  await page.callMethod('switchView', eventWithDataset({ mode: 'expiring' }))
  await page.waitFor(200)
  const viewMode = await page.data('viewMode')
  assert(viewMode === 'expiring', `reminder mode did not switch: ${viewMode}`)
  await page.callMethod('applyState', { reminders: reminderRows(), loading: false })
  await page.waitFor(100)
  assert((await page.$$('.reminder-mode.active')).length === 1, 'reminder expiring active tab is missing')
  const activeText = (await textList(await page.$$('.reminder-mode.active'))).join('\n')
  assert(activeText.includes('到期客户'), `reminder active tab should be expiring: ${activeText}`)

  await page.callMethod('openDetail', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  assert((await page.$$('.reminder-modal')).length === 1, 'reminder detail modal is missing')
  pageText = (await textList(await page.$$('.reminder-modal'))).join('\n')
  assert(pageText.includes('提醒详情'), `reminder modal title missing: ${pageText}`)
  assert(pageText.includes('华东智能制造集团'), `reminder modal customer missing: ${pageText}`)
  for (const expected of ['客户编号', 'C-20260915-501', '客户状态', 'protected', '行业', '制造业', '地区', '上海', '剩余天数', '15 天', '最后开票日', '2026-08-31', '提醒编号', '处理状态', '待处理', '到期/超期口径', '剩余 15 天到期', '负责人', '所属部门', '生成时间', '处理时间', '最后更新', '2026-09-15 09:30']) {
    assert(pageText.includes(expected), `reminder modal missing ${expected}: ${pageText}`)
  }

  await page.callMethod('openCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  const navigations = await miniProgram.evaluate(() => globalThis.__reminderNavigationCalls || [])
  assert(navigations.some((item) => item.url === '/pages/customer/detail/index?id=501&from=reminder'), `reminder customer navigation is missing: ${JSON.stringify(navigations)}`)

  await page.callMethod('process', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  const reminders = await page.data('reminders')
  assert(reminders.length === 1, `processed reminder should be removed: ${JSON.stringify(reminders)}`)
  assert((await page.$$('.reminder-modal')).length === 0, 'processed reminder should close modal')
  const toasts = await miniProgram.evaluate(() => globalThis.__reminderToastCalls || [])
  assert(toasts.some((item) => item.title === '已标记今日跟进'), `reminder followed toast is missing: ${JSON.stringify(toasts)}`)

  await page.callMethod('applyState', { reminders: reminderRows(), loading: false, selectedReminder: null })
  await page.callMethod('extend', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  const extendToasts = await miniProgram.evaluate(() => globalThis.__reminderToastCalls || [])
  assert(extendToasts.some((item) => item.title === '延期申请已提交'), `reminder extend toast is missing: ${JSON.stringify(extendToasts)}`)

  await page.callMethod('closeDetail')
  await page.waitFor(100)
  await screenshot(miniProgram, 'reminder-center.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installReminderActionMocks(miniProgram)
    await verifyReminderCenterPage(miniProgram)
  } finally {
    await restoreReminderActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified reminder center pending/expiring tabs, detail modal, customer navigation, and process action UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

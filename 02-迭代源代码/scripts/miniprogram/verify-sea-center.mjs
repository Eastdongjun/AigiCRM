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

async function installSeaActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__seaActionCalls = { modals: [], toasts: [], navigations: [] }
  })
  await miniProgram.mockWxMethod('showModal', function (options) {
    const calls = globalThis.__seaActionCalls || (globalThis.__seaActionCalls = { modals: [], toasts: [], navigations: [] })
    calls.modals.push({ title: options && options.title, content: options && options.content })
    if (options && typeof options.success === 'function') options.success({ confirm: true, cancel: false })
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    const calls = globalThis.__seaActionCalls || (globalThis.__seaActionCalls = { modals: [], toasts: [], navigations: [] })
    calls.toasts.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    const calls = globalThis.__seaActionCalls || (globalThis.__seaActionCalls = { modals: [], toasts: [], navigations: [] })
    calls.navigations.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreSeaActionMocks(miniProgram) {
  for (const method of ['showModal', 'showToast', 'navigateTo']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableSeaDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化公海用户' }
    app.globalData.permissions = ['sea:view']
  })
}

async function verifySeaCenterPage(miniProgram) {
  await enableSeaDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/sea/index')
  await page.waitFor(1000)

  const records = [
    {
      id: 901,
      customerId: 501,
      customerName: '自动化公海客户',
      customerCode: 'SEA-501',
      industry: '制造业',
      region: '华东',
      ownerName: '原负责人',
      enterReason: '保护期满',
      enterDate: '2026-09-15',
      status: 'available',
      canClaim: true,
      canEdit: true,
      canDelete: true,
      // 【2026-09-22 校准】canClaimNow 等派生字段由加载路径 normalizeSea 生成, 沙箱直注须自带
      canClaimNow: true,
      canDeleteNow: true,
      canShowActions: true,
      permissionText: '可捞取 可编辑 可删除'
    },
    {
      id: 902,
      customerId: 502,
      customerName: '仅查看客户',
      customerCode: 'SEA-502',
      industry: '能源',
      region: '华北',
      ownerName: '李四',
      enterReason: '超期未跟进',
      enterDate: '2026-09-14',
      status: 'pending_approval',
      canClaim: false,
      canEdit: false,
      canDelete: false,
      canClaimNow: false,
      canDeleteNow: false,
      canShowActions: false,
      permissionText: '仅查看'
    }
  ]
  // 【2026-09-22 校准】sea 页重构走 seaView, 须 setSeaData 重建
  await page.callMethod('setSeaData', {
    records,
    stats: { total: 2, newThisMonth: 1, pendingApproval: 1, claimedThisMonth: 0 },
    pools: [{ id: 1, poolName: '自动化公海池' }],
    deptOptions: [{ id: '', label: '全部部门' }, { id: 21, label: '山西办' }],
    selectedDeptIndex: 0,
    industryPickerOptions: ['全部行业', '制造业', '能源'],
    enterReasonOptions: [
      { label: '全部原因', value: '' },
      { label: '保护期满', value: '保护期满' },
      { label: '放弃客户', value: '放弃客户' },
      { label: '离职交接', value: '离职交接' }
    ],
    claimRecords: [{ id: 7001, customerName: '历史认领客户', status: 'WAITING', statusLabel: '待审批', applyDate: '2026-09-15', applicantName: '张三', approverName: '经理', approveTime: '', reason: '测试申请' }],
    loading: false,
    claimingId: '',
    deletingId: ''
  })
  await page.waitFor(500)

  assert((await page.$$('.sea-record-card')).length === 2, 'sea list cards are missing')
  assert((await page.$$('.sea-claim-card')).length === 1, 'sea claim record card is missing')
  const seaText = [
    ...(await textList(await page.$$('.grid'))),
    ...(await textList(await page.$$('.sea-record-card'))),
    ...(await textList(await page.$$('.sea-claim-card')))
  ].join('\n')
  for (const expected of ['池内待捞', '本月入池', '待审批捞取', '本月成功', '待捞取', '审批中', '待审批']) {
    assert(seaText.includes(expected), `sea center missing PC status label ${expected}: ${seaText}`)
  }
  const buttons = await textList(await page.$$('button'))
  for (const label of ['捞取', '编辑', '删除', '最新申请']) {
    assert(buttons.includes(label), `sea center missing action: ${label}`)
  }
  await page.callMethod('chooseIndustry', { detail: { value: 1 } })
  await page.waitFor(100)
  await page.callMethod('chooseDept', { detail: { value: 1 } })
  await page.waitFor(100)
  await page.callMethod('chooseEnterReason', { detail: { value: 1 } })
  await page.waitFor(100)
  const filterState = await page.data()
  assert(filterState.industry === '制造业', `sea industry filter not selected: ${JSON.stringify(filterState)}`)
  assert(filterState.deptId === 21, `sea department filter not selected: ${JSON.stringify(filterState)}`)
  assert(filterState.enterReason === '保护期满', `sea enter-reason filter not selected: ${JSON.stringify(filterState)}`)

  // 【2026-09-22 校准】上方三个筛选 handler 触发 demo 重载覆盖夹具(动作对象变 demo 记录);
  // 动作断言前重注入, 使 claim/edit/delete 作用于带完整能力位的夹具行
  await page.callMethod('setSeaData', { records, loading: false })
  await page.waitFor(200)
  await page.callMethod('claim', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.callMethod('submitClaim', records[0])
  await page.waitFor(300)
  await page.callMethod('openCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.callMethod('editSeaCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.callMethod('deleteSeaCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.callMethod('openClaimDetail', eventWithDataset({ index: 0 }))
  await page.waitFor(300)

  const calls = await miniProgram.evaluate(() => globalThis.__seaActionCalls || { modals: [], toasts: [], navigations: [] })
  assert(calls.modals.some((item) => item.title === '捞取确认'), `sea claim confirm modal is missing: ${JSON.stringify(calls)}`)
  assert(calls.toasts.some((item) => item.title === '捞取申请已提交'), `sea claim toast is missing: ${JSON.stringify(calls)}`)
  assert(calls.toasts.some((item) => item.title === '演示记录不可删除'), `sea delete guard toast is missing: ${JSON.stringify(calls)}`)
  assert(calls.navigations.some((item) => String(item.url || '').includes('/pages/customer/detail/index?id=501&from=sea')), `sea customer archive navigation is missing: ${JSON.stringify(calls)}`)
  assert(calls.navigations.some((item) => String(item.url || '').includes('/pages/customer/form/index?id=501&from=sea')), `sea edit navigation is missing: ${JSON.stringify(calls)}`)
  assert(calls.navigations.some((item) => String(item.url || '').includes('/pages/sea/claim-detail/index')), `sea claim detail navigation is missing: ${JSON.stringify(calls)}`)
  assert(calls.navigations.some((item) => String(item.url || '').includes('statusLabel=%E5%BE%85%E5%AE%A1%E6%89%B9')), `sea claim detail status label is missing: ${JSON.stringify(calls)}`)
  assert(calls.navigations.some((item) => String(item.url || '').includes('applicantName=%E5%BC%A0%E4%B8%89')), `sea claim detail applicant is missing: ${JSON.stringify(calls)}`)
  await screenshot(miniProgram, 'sea-center-actions.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installSeaActionMocks(miniProgram)
    await verifySeaCenterPage(miniProgram)
  } finally {
    await restoreSeaActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified public sea stats, list, claim, edit, delete guard, and claim-detail UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

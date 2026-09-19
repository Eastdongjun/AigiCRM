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

async function installUiActionMocks(miniProgram) {
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__approvalToastCalls = globalThis.__approvalToastCalls || []
    globalThis.__approvalToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showLoading', function (options) {
    globalThis.__approvalLoadingCalls = globalThis.__approvalLoadingCalls || []
    globalThis.__approvalLoadingCalls.push({ title: options && options.title })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('hideLoading', function () {})
}

async function restoreUiActionMocks(miniProgram) {
  for (const method of ['showToast', 'showLoading', 'hideLoading']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableApprovalDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化审批人' }
    app.globalData.permissions = ['approval:view', 'approval:approve', 'approval:reject']
  })
}

async function verifyApprovalCenterPage(miniProgram) {
  await enableApprovalDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/approval/index')
  await page.waitFor(1000)

  await page.setData({
    approvals: [
      {
        id: 801,
        approvalCode: 'AP-20260915-001',
        title: '客户释放审批',
        type: 'customer_release',
        typeLabel: '客户释放',
        customerName: '华东智能制造集团',
        customerIndustry: '制造业',
        status: 'pending',
        statusLabel: '待提交',
        applicant: '张三',
        currentNode: '直属经理审批',
        actionAfterApprove: 'release',
        createdAt: '2026-09-15 18:30:00',
        updatedAt: '2026-09-15 18:35:00',
        reason: '客户转入公海后释放',
        nodes: [{ id: 1, nodeOrder: 1, approverName: '经理', status: 'pending', statusLabel: '待审批', action: '', operatedAt: '', comment: '' }]
      },
      {
        id: 802,
        approvalCode: 'AP-20260915-002',
        title: '客户认领审批',
        type: 'claim',
        typeLabel: '捞取',
        customerName: '山西晋能集团中阳煤业',
        customerIndustry: '能源',
        status: 'pending',
        statusLabel: '待提交',
        applicant: '李四',
        currentNode: '区域经理审批',
        actionAfterApprove: '客户归属→本人',
        createdAt: '2026-09-15 18:31:00',
        updatedAt: '2026-09-15 18:36:00',
        reason: '公海客户认领',
        nodes: []
      }
    ],
    stats: { pending: 2, mine: 3, reviewing: 1, approvedThisMonth: 5, rejectedThisMonth: 1, all: 12 },
    keyword: '',
    typeOptions: [{ value: '', label: '全部类型' }, { value: 'claim', label: '捞取' }],
    deptOptions: [{ id: '', name: '全部部门' }, { id: 21, name: '华东办' }],
    selectedTypeIndex: 0,
    selectedDeptIndex: 0,
    canApprove: true,
    canReject: true,
    loading: false,
    submitCustomerId: '501'
  })
  await page.waitFor(500)

  assert((await page.$$('.approval-stat-card')).length === 4, 'approval PC-parity stats cards are missing')
  assert((await page.$$('.approval-list-card')).length === 2, 'approval list cards are missing')
  assert((await page.$$('.approval-tab')).length === 6, 'approval PC-parity tabs are missing')
  const cardText = (await textList(await page.$$('.approval-list-card'))).join('\n')
  for (const expected of ['编号 AP-20260915-001', '客户释放', '待提交', '当前节点 直属经理审批', '通过后动作 release', '客户 华东智能制造集团']) {
    assert(cardText.includes(expected), `approval list missing PC field ${expected}: ${cardText}`)
  }
  const buttons = await textList(await page.$$('button'))
  for (const label of ['查看详情', '驳回', '通过', '提交审批']) {
    assert(buttons.includes(label), `approval center missing action: ${label}`)
  }
  const filterText = [
    ...(await textList(await page.$$('.approval-stat-card'))),
    ...(await textList(await page.$$('.approval-tab'))),
    ...(await textList(await page.$$('.approval-filter-chip')))
  ].join('\n')
  for (const expected of ['待审批2', '我发起的3', '本月通过5', '本月驳回1', '审批中1', '已通过5', '已驳回1', '全部12', '全部类型', '全部部门']) {
    assert(filterText.includes(expected), `approval filter area missing ${expected}: ${filterText}`)
  }
  await page.setData({ keyword: '华东', selectedTypeIndex: 1, selectedDeptIndex: 1, tab: 'reviewing' })
  const params = await page.callMethod('buildApprovalParams')
  assert(params.keyword === '华东', `approval query params must send keyword: ${JSON.stringify(params)}`)
  assert(params.type === 'claim', `approval query params must send type: ${JSON.stringify(params)}`)
  assert(params.deptId === 21, `approval query params must send deptId: ${JSON.stringify(params)}`)
  assert(params.tab === 'reviewing', `approval query params must send active tab: ${JSON.stringify(params)}`)

  await page.callMethod('approve', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  const approvedStatus = await page.data('approvals[0].status')
  assert(approvedStatus === 'approved', `approve action did not update status: ${approvedStatus}`)

  await page.callMethod('reject', eventWithDataset({ index: 1 }))
  await page.waitFor(300)
  const rejectedStatus = await page.data('approvals[1].status')
  assert(rejectedStatus === 'rejected', `reject action did not update status: ${rejectedStatus}`)

  await page.callMethod('submitReleaseApproval')
  await page.waitFor(300)
  const toastCalls = await miniProgram.evaluate(() => globalThis.__approvalToastCalls || [])
  assert(toastCalls.some((item) => item.title === '已提交审批'), `submit approval toast is missing: ${JSON.stringify(toastCalls)}`)
  await screenshot(miniProgram, 'approval-center-actions.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installUiActionMocks(miniProgram)
    await verifyApprovalCenterPage(miniProgram)
  } finally {
    await restoreUiActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified approval center list, stats, approve/reject, and release submission UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

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
    globalThis.__feedbackToastCalls = []
    globalThis.__feedbackNavigateCalls = []
    globalThis.__feedbackNavigateBackCalls = []
    globalThis.__feedbackRedirectCalls = []
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__feedbackToastCalls = globalThis.__feedbackToastCalls || []
    globalThis.__feedbackToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__feedbackNavigateCalls = globalThis.__feedbackNavigateCalls || []
    globalThis.__feedbackNavigateCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateBack', function (options) {
    globalThis.__feedbackNavigateBackCalls = globalThis.__feedbackNavigateBackCalls || []
    globalThis.__feedbackNavigateBackCalls.push({ delta: options && options.delta })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('redirectTo', function (options) {
    globalThis.__feedbackRedirectCalls = globalThis.__feedbackRedirectCalls || []
    globalThis.__feedbackRedirectCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreActionMocks(miniProgram) {
  for (const method of ['showToast', 'navigateTo', 'navigateBack', 'redirectTo']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableDemoUser(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 7001, username: 'feedback-auto', realName: '反馈自动化用户' }
    app.globalData.permissions = ['reminder:view', 'customer:view']
  })
}

async function verifyFeedbackDraft(miniProgram) {
  await enableDemoUser(miniProgram)
  await miniProgram.evaluate(() => wx.removeStorageSync('feedbackDraft:7001'))
  const page = await miniProgram.reLaunch('/pages/profile/feedback/index')
  await page.waitFor(500)

  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.hero-sub'))),
    ...(await textList(await page.$$('.feedback-notice'))),
    ...(await textList(await page.$$('.form-section-title'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['意见反馈', '记录问题和改进建议', '尚未接入服务端反馈提交', '反馈内容', '清空', '保存草稿']) {
    assert(pageText.includes(expected), `feedback page missing ${expected}`)
  }

  await page.callMethod('saveDraft')
  await page.waitFor(100)
  await page.callMethod('onInput', { detail: { value: '移动端附件预览建议：列表增加文件类型和下载状态。' } })
  await page.callMethod('saveDraft')
  await page.waitFor(200)

  const state = await page.data()
  const storageValue = await miniProgram.evaluate(() => wx.getStorageSync('feedbackDraft:7001'))
  const toasts = await miniProgram.evaluate(() => globalThis.__feedbackToastCalls || [])
  assert(toasts.some((item) => item.title === '请先填写反馈内容'), `feedback empty validation toast missing: ${JSON.stringify(toasts)}`)
  assert(toasts.some((item) => item.title === '已保存草稿'), `feedback saved toast missing: ${JSON.stringify(toasts)}`)
  assert(state.saved === true, `feedback saved state is false: ${JSON.stringify(state)}`)
  assert(storageValue.includes('附件预览建议'), `feedback draft was not stored by current account: ${storageValue}`)
  assert((await page.$$('.feedback-saved')).length === 1, 'feedback saved hint is missing')

  await page.callMethod('clearDraft')
  await page.waitFor(100)
  const clearedValue = await miniProgram.evaluate(() => wx.getStorageSync('feedbackDraft:7001'))
  assert(clearedValue === '', `feedback draft was not cleared: ${clearedValue}`)
  assert((await page.data('content')) === '', 'feedback content did not clear')
  await page.callMethod('goBack')
  await page.waitFor(100)
  const backs = await miniProgram.evaluate(() => globalThis.__feedbackNavigateBackCalls || [])
  assert(backs.length >= 1, `feedback back did not call navigateBack: ${JSON.stringify(backs)}`)
  await screenshot(miniProgram, 'feedback-draft.png')
}

async function verifyNotificationCenter(miniProgram) {
  await enableDemoUser(miniProgram)
  const page = await miniProgram.reLaunch('/pages/notification/index')
  await page.waitFor(800)

  const notifications = [
    { id: 'n-1', title: '保护期即将到期', content: '客户保护期剩余 3 天，请及时跟进。', customerName: '华东智能制造集团', customerId: 501 },
    { id: 'n-2', title: '开票提醒', content: '客户已有 30 天未开票，请补充跟进记录。', customerName: '山西晋能集团中阳煤业', customerId: 502 }
  ]
  // 【2026-09-22 校准】notification 页走 notificationView, 须 applyState 重建(customerText)
  await page.callMethod('applyState', { notifications, loading: false, processing: false })
  await page.waitFor(300)

  assert((await page.$$('.notification-card')).length === 2, 'notification cards are missing')
  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.hero-sub'))),
    ...(await textList(await page.$$('.notification-card')))
  ].join('\n')
  for (const expected of ['消息中心', '业务通知与待处理提醒', '保护期即将到期', '关联客户：华东智能制造集团', '详情', '客户', '已处理']) {
    assert(pageText.includes(expected), `notification center missing ${expected}`)
  }

  await page.callMethod('openDetail', eventWithDataset({ index: 0 }))
  await page.callMethod('openCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  let navigations = await miniProgram.evaluate(() => globalThis.__feedbackNavigateCalls || [])
  assert(navigations.some((item) => item.url.includes('/pages/notification/detail/index') && item.url.includes('customerId=501')), `notification detail navigation missing: ${JSON.stringify(navigations)}`)
  assert(navigations.some((item) => item.url === '/pages/customer/detail/index?id=501&from=notification'), `notification customer navigation missing: ${JSON.stringify(navigations)}`)

  await page.callMethod('process', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  const remaining = await page.data('notifications')
  assert(remaining.length === 1 && remaining[0].id === 'n-2', `notification process did not remove row: ${JSON.stringify(remaining)}`)
  await page.callMethod('applyState', { notifications: [], loading: false })
  await page.waitFor(100)
  assert((await page.$$('.compact-empty')).length === 1, 'notification empty state is missing')
  await screenshot(miniProgram, 'notification-center.png')

  const detail = await miniProgram.reLaunch('/pages/notification/detail/index?id=n-9&title=%E5%AE%A2%E6%88%B7%E5%88%B0%E6%9C%9F&content=%E8%AF%B7%E5%A4%84%E7%90%86%E5%88%B0%E6%9C%9F%E5%AE%A2%E6%88%B7&customer=%E5%8D%8E%E4%B8%9C%E6%99%BA%E8%83%BD%E5%88%B6%E9%80%A0%E9%9B%86%E5%9B%A2&customerId=501')
  await detail.waitFor(500)
  pageText = [
    ...(await textList(await detail.$$('.hero-title'))),
    ...(await textList(await detail.$$('.detail-info-card'))),
    ...(await textList(await detail.$$('button')))
  ].join('\n')
  for (const expected of ['消息详情', '客户到期', '请处理到期客户', '华东智能制造集团', '查看客户', '标记已处理']) {
    assert(pageText.includes(expected), `notification detail missing ${expected}`)
  }
  await detail.callMethod('openCustomer')
  await detail.callMethod('process')
  await detail.callMethod('goBack')
  await detail.waitFor(200)
  navigations = await miniProgram.evaluate(() => globalThis.__feedbackNavigateCalls || [])
  const toasts = await miniProgram.evaluate(() => globalThis.__feedbackToastCalls || [])
  assert(navigations.some((item) => item.url === '/pages/customer/detail/index?id=501&from=notification'), `notification detail customer navigation missing: ${JSON.stringify(navigations)}`)
  assert(toasts.some((item) => item.title === '已处理'), `notification detail processed toast missing: ${JSON.stringify(toasts)}`)
  await screenshot(miniProgram, 'notification-detail.png')
}

async function verifyProfileLegalDocs(miniProgram) {
  await enableDemoUser(miniProgram)
  const pages = [
    {
      url: '/pages/profile/about/index',
      screenshot: 'profile-about.png',
      selectors: ['.doc-brand-card', '.doc-section'],
      expected: ['AIGICRM', '关于系统', 'PC 与小程序共用同一套账号', '服务范围']
    },
    {
      url: '/pages/profile/agreement/index',
      screenshot: 'profile-agreement.png',
      selectors: ['.title', '.updated', '.doc-section'],
      expected: ['AIGICRM 用户协议', '移动端预览版', '服务说明', '账号与数据', '服务使用']
    },
    {
      url: '/pages/profile/privacy/index',
      screenshot: 'profile-privacy.png',
      selectors: ['.title', '.updated', '.doc-section'],
      expected: ['AIGICRM 隐私政策', '我们处理的信息', '信息使用目的', '你的控制权']
    }
  ]

  for (const item of pages) {
    const page = await miniProgram.reLaunch(item.url)
    await page.waitFor(500)
    const texts = []
    for (const selector of item.selectors) {
      texts.push(...(await textList(await page.$$(selector))))
    }
    const pageText = texts.join('\n')
    for (const expected of item.expected) {
      assert(pageText.includes(expected), `${item.url} missing ${expected}`)
    }
    await screenshot(miniProgram, item.screenshot)
  }
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installActionMocks(miniProgram)
    await verifyFeedbackDraft(miniProgram)
    await verifyNotificationCenter(miniProgram)
    await verifyProfileLegalDocs(miniProgram)
  } finally {
    await restoreActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified feedback draft, notification center/detail actions, and profile legal document pages.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

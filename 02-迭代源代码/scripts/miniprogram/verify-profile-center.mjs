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

async function installProfileActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__profileNavigationCalls = []
    globalThis.__profileToastCalls = []
    globalThis.__profileModalCalls = []
    globalThis.__profileRedirectCalls = []
  })
  // 【2026-09-22 校准】tabBar 页入口(incentive/statistics)走 switchTab, 须同口径记录
  await miniProgram.mockWxMethod('switchTab', function (options) {
    globalThis.__profileNavigationCalls = globalThis.__profileNavigationCalls || []
    globalThis.__profileNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__profileNavigationCalls = globalThis.__profileNavigationCalls || []
    globalThis.__profileNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('redirectTo', function (options) {
    globalThis.__profileRedirectCalls = globalThis.__profileRedirectCalls || []
    globalThis.__profileRedirectCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__profileToastCalls = globalThis.__profileToastCalls || []
    globalThis.__profileToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showModal', function (options) {
    globalThis.__profileModalCalls = globalThis.__profileModalCalls || []
    globalThis.__profileModalCalls.push({ title: options && options.title, content: options && options.content, showCancel: options && options.showCancel })
    if (options && typeof options.success === 'function') options.success({ confirm: true, cancel: false })
  })
}

async function restoreProfileActionMocks(miniProgram) {
  for (const method of ['navigateTo', 'switchTab', 'redirectTo', 'showToast', 'showModal']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableProfileDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化用户', username: 'profile-auto' }
    app.globalData.permissions = ['customer:view', 'incentive:view', 'sea:view']
    app.globalData.groupIds = [1, 2]
    app.globalData.currentGroupId = 1
  })
}

async function verifyProfileHome(miniProgram) {
  await enableProfileDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/profile/index')
  await page.waitFor(1000)

  // 【2026-09-22 校准】profile 页走 profileView, 须 applyState 重建
  await page.callMethod('applyState', {
    userName: '张三',
    roleName: '销售经理',
    orgName: '华东办事处',
    scopeName: '本部门及以下',
    positionNames: '销售经理、客户负责人',
    groupIds: [1, 2],
    currentGroupId: 1,
    groupIndex: 0,
    switchingGroup: false
  })
  await page.waitFor(300)

  assert((await page.$$('.menu-item')).length >= 10, 'profile menu entries are missing')
  assert((await page.$$('.scope-card')).length >= 3, 'profile identity/scope cards are missing')

  const pageText = [
    ...(await textList(await page.$$('.profile-name'))),
    ...(await textList(await page.$$('.hero-sub'))),
    ...(await textList(await page.$$('.scope-card'))),
    ...(await textList(await page.$$('.menu-item'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['张三', '华东办事处', '销售经理', '当前岗位', '当前集团', '数据范围', '我的客户', '我的四项服务', '我的捞取记录', '修改密码', '数据权限说明', '微信绑定', '意见反馈', '退出登录']) {
    assert(pageText.includes(expected), `profile home missing ${expected}`)
  }

  await page.callMethod('openCustomers')
  await page.callMethod('openIncentive')
  await page.callMethod('openClaims')
  await page.callMethod('openPassword')
  await page.callMethod('openScope')
  await page.callMethod('openWechat')
  await page.callMethod('openFeedback')
  await page.callMethod('openAbout')
  await page.callMethod('openAgreement')
  await page.callMethod('openPrivacy')
  await page.waitFor(300)
  const navigations = await miniProgram.evaluate(() => globalThis.__profileNavigationCalls || [])
  for (const expectedUrl of [
    '/pages/customer/index',
    '/pages/incentive/index',
    '/pages/sea/index',
    '/pages/profile/password/index',
    '/pages/profile/scope/index',
    '/pages/profile/wechat/index',
    '/pages/profile/feedback/index',
    '/pages/profile/about/index',
    '/pages/profile/agreement/index',
    '/pages/profile/privacy/index'
  ]) {
    assert(navigations.some((item) => item.url === expectedUrl), `profile navigation missing ${expectedUrl}: ${JSON.stringify(navigations)}`)
  }

  await page.callMethod('clearCache')
  await page.waitFor(300)
  const modals = await miniProgram.evaluate(() => globalThis.__profileModalCalls || [])
  assert(modals.some((item) => item.title === '清除缓存'), `profile clear-cache confirmation is missing: ${JSON.stringify(modals)}`)
  await screenshot(miniProgram, 'profile-center-home.png')
}

async function verifyPasswordPage(miniProgram) {
  await enableProfileDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/profile/password/index')
  await page.waitFor(500)

  assert((await page.$$('.polished-input')).length === 3, 'profile password inputs are missing')
  assert((await page.$$('.polished-submit')).length === 1, 'profile password submit is missing')

  await page.callMethod('submit')
  await page.waitFor(100)
  await page.callMethod('onInput', eventWithDataset({ field: 'oldPassword' }, 'old-pass'))
  await page.callMethod('onInput', eventWithDataset({ field: 'newPassword' }, '123'))
  await page.callMethod('onInput', eventWithDataset({ field: 'confirmPassword' }, '123'))
  await page.callMethod('submit')
  await page.waitFor(100)
  await page.callMethod('onInput', eventWithDataset({ field: 'newPassword' }, '123456'))
  await page.callMethod('onInput', eventWithDataset({ field: 'confirmPassword' }, '654321'))
  await page.callMethod('submit')
  await page.waitFor(100)

  const toasts = await miniProgram.evaluate(() => globalThis.__profileToastCalls || [])
  for (const expected of ['请完整填写密码', '新密码至少 6 位', '两次新密码不一致']) {
    assert(toasts.some((item) => item.title === expected), `profile password validation toast missing ${expected}: ${JSON.stringify(toasts)}`)
  }
  await screenshot(miniProgram, 'profile-password.png')
}

async function verifyScopeAndWechatPages(miniProgram) {
  await enableProfileDemoMode(miniProgram)
  let page = await miniProgram.reLaunch('/pages/profile/scope/index')
  await page.waitFor(500)
  let pageText = [
    ...(await textList(await page.$$('.title'))),
    ...(await textList(await page.$$('.heading'))),
    ...(await textList(await page.$$('.scope-row'))),
    ...(await textList(await page.$$('.body')))
  ].join('\n')
  for (const expected of ['常见范围', '本人', '本部门及以下', '全部数据', '操作权限', '服务端校验']) {
    assert(pageText.includes(expected), `profile scope page missing ${expected}`)
  }

  page = await miniProgram.reLaunch('/pages/profile/wechat/index')
  await page.waitFor(500)
  pageText = [
    ...(await textList(await page.$$('.title'))),
    ...(await textList(await page.$$('.status'))),
    ...(await textList(await page.$$('.body'))),
    ...(await textList(await page.$$('.wechat-notice'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['微信账号', '待后端接入', '当前版本不会请求微信身份信息', '查看接入说明']) {
    assert(pageText.includes(expected), `profile wechat page missing ${expected}`)
  }
  await page.callMethod('bindWechat')
  await page.waitFor(200)
  const modals = await miniProgram.evaluate(() => globalThis.__profileModalCalls || [])
  assert(modals.some((item) => item.title === '微信绑定暂未接入' && item.content.includes('不会请求微信身份信息')), `profile wechat boundary modal missing: ${JSON.stringify(modals)}`)
  await screenshot(miniProgram, 'profile-wechat-boundary.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installProfileActionMocks(miniProgram)
    await verifyProfileHome(miniProgram)
    await verifyPasswordPage(miniProgram)
    await verifyScopeAndWechatPages(miniProgram)
  } finally {
    await restoreProfileActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified profile center identity/scope/menu navigation, password validation, data-scope copy, and WeChat binding boundary UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

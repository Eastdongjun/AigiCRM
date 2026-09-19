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

async function installAuthActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__authToastCalls = []
    globalThis.__authSwitchTabCalls = []
    globalThis.__authNavigateCalls = []
    globalThis.__authNavigateBackCalls = []
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__authToastCalls = globalThis.__authToastCalls || []
    globalThis.__authToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('switchTab', function (options) {
    globalThis.__authSwitchTabCalls = globalThis.__authSwitchTabCalls || []
    globalThis.__authSwitchTabCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__authNavigateCalls = globalThis.__authNavigateCalls || []
    globalThis.__authNavigateCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('navigateBack', function (options) {
    globalThis.__authNavigateBackCalls = globalThis.__authNavigateBackCalls || []
    globalThis.__authNavigateBackCalls.push({ delta: options && options.delta })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreAuthActionMocks(miniProgram) {
  for (const method of ['showToast', 'switchTab', 'navigateTo', 'navigateBack']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function setDemoMode(miniProgram, enabled) {
  await miniProgram.evaluate((demoMode) => {
    const app = getApp()
    app.globalData.demoMode = demoMode
    app.globalData.currentUser = null
    app.globalData.permissions = []
  }, enabled)
}

async function verifyLoginPage(miniProgram) {
  await setDemoMode(miniProgram, false)
  const page = await miniProgram.reLaunch('/pages/login/index')
  await page.waitFor(500)

  assert((await page.$$('.login-panel')).length === 1, 'login panel is missing')
  assert((await page.$$('.login-field')).length === 2, 'username/password fields are missing in account mode')
  const accountText = [
    ...(await textList(await page.$$('.login-title'))),
    ...(await textList(await page.$$('.login-copy'))),
    ...(await textList(await page.$$('.field-label'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['AIGICRM', '使用 PC 端同一账号登录', '用户名', '密码', '登录', '忘记密码']) {
    assert(accountText.includes(expected), `login page missing ${expected}`)
  }

  await page.callMethod('submit')
  await page.waitFor(100)
  await page.callMethod('openForgot')
  await page.waitFor(100)
  const toasts = await miniProgram.evaluate(() => globalThis.__authToastCalls || [])
  const navigations = await miniProgram.evaluate(() => globalThis.__authNavigateCalls || [])
  assert(toasts.some((item) => item.title === '请输入用户名和密码'), `login empty validation toast is missing: ${JSON.stringify(toasts)}`)
  assert(navigations.some((item) => item.url === '/pages/login/forgot/index'), `forgot password navigation is missing: ${JSON.stringify(navigations)}`)
  await screenshot(miniProgram, 'auth-login-account.png')
}

async function verifyDemoLogin(miniProgram) {
  await setDemoMode(miniProgram, true)
  const page = await miniProgram.reLaunch('/pages/login/index')
  await page.waitFor(500)

  assert((await page.$$('.login-field')).length === 0, 'demo mode should not render credential inputs')
  const pageText = [
    ...(await textList(await page.$$('.login-copy'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['当前为演示模式', '进入原型']) {
    assert(pageText.includes(expected), `demo login page missing ${expected}`)
  }

  await page.callMethod('submit')
  await page.waitFor(100)
  const switchTabs = await miniProgram.evaluate(() => globalThis.__authSwitchTabCalls || [])
  const appState = await miniProgram.evaluate(() => {
    const app = getApp()
    return {
      currentUser: app.globalData.currentUser,
      permissions: app.globalData.permissions
    }
  })
  assert(switchTabs.some((item) => item.url === '/pages/home/index'), `demo login did not enter home: ${JSON.stringify(switchTabs)}`)
  assert(appState.currentUser && appState.currentUser.username === 'demo', `demo user was not installed: ${JSON.stringify(appState)}`)
  assert(Array.isArray(appState.permissions) && appState.permissions.includes('customer:view') && appState.permissions.includes('incentive:view'), `demo permissions are incomplete: ${JSON.stringify(appState.permissions)}`)
  await screenshot(miniProgram, 'auth-login-demo.png')
}

async function verifyForgotPassword(miniProgram) {
  await setDemoMode(miniProgram, false)
  const page = await miniProgram.reLaunch('/pages/login/forgot/index')
  await page.waitFor(500)

  let pageText = [
    ...(await textList(await page.$$('.hero-title'))),
    ...(await textList(await page.$$('.step'))),
    ...(await textList(await page.$$('.label'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['找回密码', '1 验证账号', '2 验证身份', '3 重置密码', '登录账号', '下一步', '返回登录']) {
    assert(pageText.includes(expected), `forgot step 1 missing ${expected}`)
  }

  await page.callMethod('next')
  await page.waitFor(100)
  await page.callMethod('back')
  await page.waitFor(100)
  let toasts = await miniProgram.evaluate(() => globalThis.__authToastCalls || [])
  let backs = await miniProgram.evaluate(() => globalThis.__authNavigateBackCalls || [])
  assert(toasts.some((item) => item.title === '请输入账号'), `forgot username validation toast is missing: ${JSON.stringify(toasts)}`)
  assert(backs.length >= 1, `forgot back to login did not call navigateBack: ${JSON.stringify(backs)}`)

  await page.setData({ step: 2, username: 'demo-user', maskedPhone: '138****8000', code: '', countdown: 0 })
  await page.waitFor(100)
  await page.callMethod('verifyCode')
  await page.waitFor(100)
  pageText = [
    ...(await textList(await page.$$('.muted'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  assert(pageText.includes('138****8000'), 'forgot step 2 masked phone is missing')
  assert(pageText.includes('获取验证码'), 'forgot step 2 send-code button is missing')

  await page.callMethod('input', eventWithDataset({ field: 'code' }, '123456'))
  await page.callMethod('next')
  await page.waitFor(100)
  assert((await page.data()).step === 3, 'forgot code verification did not enter reset step')

  await page.callMethod('input', eventWithDataset({ field: 'newPassword' }, '123'))
  await page.callMethod('input', eventWithDataset({ field: 'confirmPassword' }, '123'))
  await page.callMethod('next')
  await page.waitFor(100)
  await page.callMethod('input', eventWithDataset({ field: 'newPassword' }, '123456'))
  await page.callMethod('input', eventWithDataset({ field: 'confirmPassword' }, '654321'))
  await page.callMethod('next')
  await page.waitFor(100)
  toasts = await miniProgram.evaluate(() => globalThis.__authToastCalls || [])
  assert(toasts.filter((item) => item.title === '请检查新密码').length >= 2, `forgot reset validation toasts are missing: ${JSON.stringify(toasts)}`)

  pageText = [
    ...(await textList(await page.$$('.label'))),
    ...(await textList(await page.$$('button')))
  ].join('\n')
  for (const expected of ['新密码', '确认密码', '确认重置', '上一步']) {
    assert(pageText.includes(expected), `forgot reset step missing ${expected}`)
  }
  await screenshot(miniProgram, 'auth-forgot-password.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installAuthActionMocks(miniProgram)
    await verifyLoginPage(miniProgram)
    await verifyDemoLogin(miniProgram)
    await verifyForgotPassword(miniProgram)
  } finally {
    await restoreAuthActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified mobile auth login, demo entry, forgot-password UI, validation, and navigation boundaries.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

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

async function installBoundaryMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__indicatorBoundaryCalls = { switchTab: [], showToast: [] }
  })
  await miniProgram.mockWxMethod('switchTab', function (options) {
    globalThis.__indicatorBoundaryCalls = globalThis.__indicatorBoundaryCalls || { switchTab: [], showToast: [] }
    globalThis.__indicatorBoundaryCalls.switchTab.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__indicatorBoundaryCalls = globalThis.__indicatorBoundaryCalls || { switchTab: [], showToast: [] }
    globalThis.__indicatorBoundaryCalls.showToast.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreBoundaryMocks(miniProgram) {
  await miniProgram.restoreWxMethod('switchTab')
  await miniProgram.restoreWxMethod('showToast')
}

async function enableIndicatorDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化指标用户' }
    app.globalData.permissions = ['statistics:view', 'customer:view']
  })
}

async function disableIndicatorPermission(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = false
    app.globalData.currentUser = { id: 1002, realName: '无统计权限用户' }
    app.globalData.permissions = ['customer:view']
  })
}

async function verifyIndicatorOverviewPage(miniProgram) {
  await enableIndicatorDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/indicator/index')
  await page.waitFor(800)

  await page.setData({
    loading: false,
    stats: {
      coveredCount: 12,
      coreCount: 5,
      avgScore: 9.6,
      qualifyRate: 66.7
    },
    ranking: [
      { customerId: 501, customerName: '华东智能制造集团', core: true, totalScore: 13 },
      { customerId: 502, customerName: '山西晋能集团中阳煤业', core: false, totalScore: 9 }
    ]
  })
  await page.waitFor(500)

  assert((await page.$$('.indicator-grid')).length === 1, 'indicator metric grid is missing')
  assert((await page.$$('.metric')).length === 4, 'indicator page must render four summary metrics')
  assert((await page.$$('.indicator-rank-card')).length === 1, 'indicator ranking card is missing')
  assert((await page.$$('.rank-row')).length === 2, 'indicator ranking rows are missing')

  const pageText = [
    ...(await textList(await page.$$('.hero'))),
    ...(await textList(await page.$$('.metric'))),
    ...(await textList(await page.$$('.section-title'))),
    ...(await textList(await page.$$('.rank-row')))
  ].join('\n')
  for (const expected of ['核心客户六项指标', '覆盖客户', '12', '核心客户', '5', '平均分', '9.6 / 15', '财务达标率', '66.7%', '客户排行', '华东智能制造集团', '核心', '13', '山西晋能集团中阳煤业', '非核心', '9']) {
    assert(pageText.includes(expected), `indicator overview missing ${expected}`)
  }

  await screenshot(miniProgram, 'indicator-overview.png')
}

async function verifyPermissionBoundary(miniProgram) {
  await disableIndicatorPermission(miniProgram)
  const page = await miniProgram.reLaunch('/pages/indicator/index')
  await page.waitFor(500)

  const calls = await miniProgram.evaluate(() => globalThis.__indicatorBoundaryCalls || { switchTab: [], showToast: [] })
  assert(calls.showToast.some((item) => item.title === '没有统计查看权限'), `indicator permission toast is missing: ${JSON.stringify(calls)}`)
  assert(calls.switchTab.some((item) => item.url === '/pages/home/index'), `indicator permission fallback is missing: ${JSON.stringify(calls)}`)
  await screenshot(miniProgram, 'indicator-permission-boundary.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installBoundaryMocks(miniProgram)
    await verifyIndicatorOverviewPage(miniProgram)
    await verifyPermissionBoundary(miniProgram)
  } finally {
    await restoreBoundaryMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified mobile indicator overview metrics, ranking, and statistics:view permission boundary UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

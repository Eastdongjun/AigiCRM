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

async function installRankingActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__rankingNavigationCalls = []
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__rankingNavigationCalls = globalThis.__rankingNavigationCalls || []
    globalThis.__rankingNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreRankingActionMocks(miniProgram) {
  await miniProgram.restoreWxMethod('navigateTo')
}

async function enableRankingDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化排行用户' }
    app.globalData.permissions = ['score:view', 'statistics:view', 'ranking:customer:view']
  })
}

async function verifyRankingCenterPage(miniProgram) {
  await enableRankingDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/ranking/index')
  await page.waitFor(1000)

  const rankings = {
    incentive: [
      {
        customerId: 501,
        customerName: '华东智能制造集团',
        ownerName: '张三',
        industry: '制造业',
        totalScore: 36,
        recordCount: 4,
        byParent: {
          pre: { count: 1, score: 8 },
          mid: { count: 1, score: 10 },
          post: { count: 1, score: 12 },
          track: { count: 1, score: 6 }
        }
      }
    ],
    relationship: [
      { customerId: 501, customerName: '华东智能制造集团', ownerName: '张三', industry: '制造业', relationshipScore: 3 }
    ],
    core: [
      {
        customerId: 501,
        customerName: '华东智能制造集团',
        ownerName: '张三',
        industry: '制造业',
        relationshipScore: 3,
        indicator02: 3,
        indicator03: 2,
        indicator04: 2,
        indicator05: 2,
        totalScore: 12,
        invoiceTotal: 120,
        paymentTotal: 110,
        core: true
      }
    ]
  }
  const scoreRankings = {
    personal: [
      { rank: 1, realName: '张三', officeName: '华东办', totalScore: 96, itemCount: 42, trend: '领先' },
      { rank: 2, realName: '李四', officeName: '华南办', totalScore: 88, itemCount: 37, trend: '上升' }
    ],
    dept: [
      { rank: 1, deptName: '华东办', totalScore: 386, participantCount: 6, avgScore: 64 },
      { rank: 2, deptName: '华南办', totalScore: 342, participantCount: 5, avgScore: 68 }
    ]
  }
  // 【2026-09-22 校准】ranking 页走 rankingView, 须 setRankingData 重建
  await page.callMethod('setRankingData', {
    activeTab: 'incentive',
    scoreActiveTab: 'personal',
    periodOptions: [
      { label: '2026全年', value: '2026全年' },
      { label: '2026下半年', value: '2026下半年' },
      { label: '2026上半年', value: '2026上半年' }
    ],
    periodIndex: 0,
    customerPeriodOptions: [
      { label: '全部', value: '' },
      { label: '2026全年', value: '2026全年' },
      { label: '2026下半年', value: '2026下半年' }
    ],
    customerPeriodIndex: 0,
    incentiveParents: [
      { id: 'pre', categoryName: '安装前' },
      { id: 'mid', categoryName: '安装中' },
      { id: 'post', categoryName: '安装后' },
      { id: 'track', categoryName: '跟踪' }
    ],
    rankings,
    scoreRankings,
    scoreStats: { participantCount: 18, deptCount: 4, topScore: 96, topScoreName: '张三', topItemCount: 42, totalItemCount: 42 },
    list: rankings.incentive,
    scoreList: scoreRankings.personal,
    loading: false
  })
  await page.waitFor(500)

  assert((await page.$$('.summary-cell')).length === 4, 'ranking summary cells are missing')
  assert((await page.$$('.score-rank-card')).length === 2, 'score ranking cards are missing')
  assert((await page.$$('.ranking-card')).length === 1, 'customer ranking cards are missing')
  assert((await page.$$('.period-chip')).length === 2, 'ranking period selectors are missing')
  const periodOptions = await page.data('periodOptions')
  assert(periodOptions[0].value === '2026全年', `score ranking should default to a full-year period: ${JSON.stringify(periodOptions)}`)
  assert(!periodOptions.some((item) => item.value === ''), `score ranking should not expose an empty all-period option: ${JSON.stringify(periodOptions)}`)
  const customerPeriodOptions = await page.data('customerPeriodOptions')
  assert(customerPeriodOptions[0].value === '', `customer ranking should expose the PC all-period option: ${JSON.stringify(customerPeriodOptions)}`)
  assert(await page.data('customerPeriodIndex') === 0, 'customer ranking should default to all-period like PC')

  const initialText = [
    ...(await textList(await page.$$('.hero-title'))),
    // 【2026-09-22 校准】周期文本渲染于 period-chip(视图模型 scorePeriodText)
    ...(await textList(await page.$$('.period-chip'))),
    ...(await textList(await page.$$('.summary-cell'))),
    ...(await textList(await page.$$('.score-rank-card'))),
    ...(await textList(await page.$$('.ranking-card'))),
    ...(await textList(await page.$$('.score-rule-card')))
  ].join('\n')
  // 【2026-11-06 重锚】'排行中心'系 11-03 重设计前旧标题(现仅存注释), 改钉现行默认标题;
  // 入口拆分后标题随主页签动态化, 无参进入默认 score 榜='学分排行'
  for (const expected of ['学分排行', '参与人数', '张三', '96', '42 个文件', '2026全年', '华东智能制造集团', '安装前', '积分规则']) {
    assert(initialText.includes(expected), `ranking page missing ${expected}`)
  }

  await page.callMethod('switchScoreTab', eventWithDataset({ key: 'dept' }))
  await page.waitFor(200)
  const deptText = (await textList(await page.$$('.score-rank-card'))).join('\n')
  assert(deptText.includes('华东办'), `dept score ranking is missing: ${deptText}`)
  assert(deptText.includes('人均'), `dept score ranking avg label is missing: ${deptText}`)

  await page.callMethod('switchTab', eventWithDataset({ key: 'relationship' }))
  await page.waitFor(200)
  let customerText = (await textList(await page.$$('.ranking-card'))).join('\n')
  assert(customerText.includes('3 分'), `relationship ranking score is missing: ${customerText}`)

  await page.callMethod('switchTab', eventWithDataset({ key: 'core' }))
  await page.waitFor(200)
  customerText = (await textList(await page.$$('.ranking-card'))).join('\n')
  assert(customerText.includes('核心 是'), `core ranking label is missing: ${customerText}`)
  assert(customerText.includes('开票 120 万'), `core ranking invoice metric is missing: ${customerText}`)

  await page.callMethod('openDetail', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  const navigations = await miniProgram.evaluate(() => globalThis.__rankingNavigationCalls || [])
  assert(navigations.some((item) => item.url === '/pages/customer/detail/index?id=501&from=ranking'), `ranking customer archive navigation is missing: ${JSON.stringify(navigations)}`)

  await page.callMethod('onPeriodChange', { detail: { value: 1 } })
  await page.waitFor(200)
  const periodIndex = await page.data('periodIndex')
  assert(periodIndex === 1, `period selector did not update: ${periodIndex}`)
  await page.callMethod('onCustomerPeriodChange', { detail: { value: 1 } })
  await page.waitFor(200)
  const customerPeriodIndex = await page.data('customerPeriodIndex')
  assert(customerPeriodIndex === 1, `customer period selector did not update: ${customerPeriodIndex}`)
  await screenshot(miniProgram, 'ranking-center.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installRankingActionMocks(miniProgram)
    await verifyRankingCenterPage(miniProgram)
  } finally {
    await restoreRankingActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified ranking center score stats, personal/dept rankings, customer rankings, period filter, and customer detail navigation UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

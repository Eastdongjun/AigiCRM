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

async function installNavigationMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__statisticsNavigationCalls = []
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__statisticsNavigationCalls = globalThis.__statisticsNavigationCalls || []
    globalThis.__statisticsNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreNavigationMocks(miniProgram) {
  await miniProgram.restoreWxMethod('navigateTo')
}

async function enableStatisticsDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.currentUser = { id: 1001, realName: '自动化统计用户' }
    app.globalData.permissions = ['statistics:view']
  })
}

async function verifyStatisticsCenterPage(miniProgram) {
  await enableStatisticsDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/statistics/index')
  await page.waitFor(1000)

  await page.setData({
    loading: false,
    yearOptions: ['2026', '2025', '2024'],
    selectedYearIndex: 0,
    deptOptions: [{ id: '', label: '全部部门' }, { id: 21, label: '山西办' }],
    selectedDeptIndex: 0,
    deptId: '',
    stats: {
      myCustomers: 128,
      inProtection: 86,
      expiring: 8,
      expired: 5,
      todayExpiring: 2,
      monthNew: 12,
      approvalStats: { pending: 3, approved: 8, rejected: 1 }
    },
    analysis: {
      totalCustomers: 128,
      yearNew: 36,
      inProtection: 86,
      expiring: 8,
      expired: 5,
      todayExpiring: 2,
      industryStats: { 制造业: 52, 能源: 31, 金融: 18, 政务: 12 },
      monthlyCustomers: [2, 4, 3, 5, 4, 6, 3, 5, 4, 2, 1, 0],
      monthlyFiles: [8, 12, 9, 14, 11, 16, 10, 13, 12, 7, 5, 3]
    },
    monthlyCustomerSummary: {
      total: 39,
      average: 3,
      peak: '6月',
      bars: [
        { label: '1月', value: 2, height: 33 },
        { label: '2月', value: 4, height: 67 },
        { label: '3月', value: 3, height: 50 },
        { label: '4月', value: 5, height: 83 },
        { label: '5月', value: 4, height: 67 },
        { label: '6月', value: 6, height: 100 },
        { label: '7月', value: 3, height: 50 },
        { label: '8月', value: 5, height: 83 },
        { label: '9月', value: 4, height: 67 },
        { label: '10月', value: 2, height: 33 },
        { label: '11月', value: 1, height: 17 },
        { label: '12月', value: 0, height: 10 }
      ]
    },
    monthlyFileSummary: {
      total: 120,
      average: 10,
      peak: '6月',
      bars: [
        { label: '1月', value: 8, height: 50 },
        { label: '2月', value: 12, height: 75 },
        { label: '3月', value: 9, height: 56 },
        { label: '4月', value: 14, height: 88 },
        { label: '5月', value: 11, height: 69 },
        { label: '6月', value: 16, height: 100 },
        { label: '7月', value: 10, height: 63 },
        { label: '8月', value: 13, height: 81 },
        { label: '9月', value: 12, height: 75 },
        { label: '10月', value: 7, height: 44 },
        { label: '11月', value: 5, height: 31 },
        { label: '12月', value: 3, height: 19 }
      ]
    },
    statusRows: [
      { key: 'inProtection', label: '保护中', value: 86, pct: 67.2, tone: 'green' },
      { key: 'expiring', label: '即将到期', value: 8, pct: 6.3, tone: 'orange' },
      { key: 'expired', label: '已超期', value: 5, pct: 3.9, tone: 'red' }
    ],
    officeComparison: [
      { officeName: '华东办事处', customerCount: 46, monthNew: 7, activeRate: 82.6 },
      { officeName: '北方办事处', customerCount: 31, monthNew: 4, activeRate: 77.4 }
    ],
    industryRows: [
      { name: '制造业', count: 52, pct: 46, rank: 1 },
      { name: '能源', count: 31, pct: 27.4, rank: 2 },
      { name: '金融', count: 18, pct: 15.9, rank: 3 },
      { name: '政务', count: 12, pct: 10.6, rank: 4 }
    ],
    scoreTop5: [
      { realName: '张三', totalScore: 96 },
      { realName: '李四', totalScore: 88 },
      { realName: '王五', totalScore: 76 },
      { realName: '赵六', totalScore: 63 },
      { realName: '钱七', totalScore: 58 }
    ],
    expiringDetails: [
      { customerName: '华东智能制造集团', daysLeft: 2 },
      { customerName: '山西晋能集团中阳煤业', daysLeft: 5 }
    ]
  })
  await page.waitFor(500)

  assert((await page.$$('.stats-filter-card')).length === 1, 'statistics analysis filter card is missing')
  assert((await page.$$('.stats-primary-card')).length === 1, 'statistics primary card is missing')
  assert((await page.$$('.stats-risk-panel')).length === 1, 'statistics risk panel is missing')
  assert((await page.$$('.stats-office-card')).length === 1, 'statistics office comparison card is missing')
  assert((await page.$$('.stats-approval-card')).length === 1, 'statistics approval card is missing')
  assert((await page.$$('.stats-warning-card')).length === 1, 'statistics warning card is missing')
  assert((await page.$$('.stats-industry-card')).length === 1, 'statistics industry distribution card is missing')
  assert((await page.$$('.industry-row')).length === 4, 'statistics industry distribution rows are missing')
  assert((await page.$$('.stats-score-card')).length === 1, 'statistics score top5 card is missing')
  assert((await page.$$('.score-row')).length === 5, 'statistics score top5 rows are missing')
  assert((await page.$$('.stats-trend-card')).length === 2, 'statistics monthly trend cards are missing')
  assert((await page.$$('.trend-bar-fill.customer')).length === 12, 'statistics monthly customer bars are missing')
  assert((await page.$$('.trend-bar-fill.file')).length === 12, 'statistics monthly file bars are missing')
  assert((await page.$$('.stats-status-card')).length === 1, 'statistics customer status card is missing')
  assert((await page.$$('.status-row')).length === 3, 'statistics customer status rows are missing')

  const pageText = [
    ...(await textList(await page.$$('.stats-filter-card'))),
    ...(await textList(await page.$$('.stats-section-head'))),
    ...(await textList(await page.$$('.stats-tile'))),
    ...(await textList(await page.$$('.stats-trend-card'))),
    ...(await textList(await page.$$('.stats-status-card'))),
    ...(await textList(await page.$$('.stats-industry-card'))),
    ...(await textList(await page.$$('.stats-score-card'))),
    ...(await textList(await page.$$('.stats-warning-row'))),
    ...(await textList(await page.$$('.stats-approval-card')))
  ].join('\n')
  for (const expected of ['分析筛选', '2026', '全部部门', '经营分析', '月度趋势', '月度新增客户', '月度文件上传', '客户状态分布', '保护中', '行业分布', '制造业', '人员积分 Top5', '张三', '办事处对比', '审批摘要', '到期预警', '华东办事处', '待审批', '华东智能制造集团']) {
    assert(pageText.includes(expected), `statistics page missing ${expected}`)
  }

  await page.callMethod('chooseYear', { detail: { value: 1 } })
  await page.waitFor(100)
  await page.callMethod('chooseDept', { detail: { value: 1 } })
  await page.waitFor(100)
  const filterState = await page.data()
  assert(filterState.selectedYearIndex === 1, `statistics year filter not selected: ${JSON.stringify(filterState)}`)
  assert(filterState.deptId === 21, `statistics department filter not selected: ${JSON.stringify(filterState)}`)

  await page.callMethod('openDetail')
  await page.waitFor(300)
  const navigations = await miniProgram.evaluate(() => globalThis.__statisticsNavigationCalls || [])
  assert(navigations.some((item) => item.url === '/pages/statistics/detail/index'), `statistics detail navigation is missing: ${JSON.stringify(navigations)}`)
  await screenshot(miniProgram, 'statistics-center.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installNavigationMocks(miniProgram)
    await verifyStatisticsCenterPage(miniProgram)
  } finally {
    await restoreNavigationMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified statistics center summary, monthly trends, customer status distribution, industry distribution, score top5, office comparison, approval summary, warnings, and detail navigation UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

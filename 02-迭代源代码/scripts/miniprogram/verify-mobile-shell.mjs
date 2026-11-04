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

async function installShellActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__shellNavigationCalls = []
    globalThis.__shellToastCalls = []
  })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__shellNavigationCalls = globalThis.__shellNavigationCalls || []
    globalThis.__shellNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  // 【2026-09-22 校准】f9b02c22 死按钮修复: tabBar 页(incentive/statistics 等)改走 switchTab,
  // 只 mock navigateTo 会漏记 → 同口径记录 switchTab
  await miniProgram.mockWxMethod('switchTab', function (options) {
    globalThis.__shellNavigationCalls = globalThis.__shellNavigationCalls || []
    globalThis.__shellNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('showToast', function (options) {
    globalThis.__shellToastCalls = globalThis.__shellToastCalls || []
    globalThis.__shellToastCalls.push({ title: options && options.title, icon: options && options.icon })
    if (options && typeof options.success === 'function') options.success({})
  })
}

async function restoreShellActionMocks(miniProgram) {
  for (const method of ['navigateTo', 'switchTab', 'showToast', 'request']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableShellDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = true
    app.globalData.appName = 'AIGICRM'
    app.globalData.currentUser = { id: 1001, realName: '自动化工作台用户' }
    app.globalData.permissions = [
      'customer:view',
      'customer:add',
      'incentive:view',
      'incentive:add',
      'approval:view',
      'reminder:view',
      'statistics:view',
      'sea:view'
    ]
  })
}

async function enablePermissionMode(miniProgram, permissions) {
  // 【2026-09-22 校准】权限负向段须沙箱化网络: 本机 env.local.js 指向真实测试服时,
  // 假 token 触发 /api/auth/info 401 → request.js redirectToLoginOnce → 断言跑在登录页(实测)。
  // mock request 走网络失败语义: app.onShow 的 loadCurrentUser().catch 保留会话, 不触发跳转
  await miniProgram.mockWxMethod('request', function (options) {
    if (options && typeof options.fail === 'function') {
      options.fail({ errMsg: 'request:fail automator-sandbox' })
    }
  })
  await miniProgram.evaluate((perms) => {
    const app = getApp()
    app.globalData.demoMode = false
    app.globalData.appName = 'AIGICRM'
    app.globalData.currentUser = { id: 1001, realName: '自动化权限用户' }
    app.globalData.permissions = perms
    const app0 = getApp()
    // 【PR-04 令牌缓存隔离】与 utils/auth.js 同口径: 键按 apiBaseUrl 分环境
    wx.setStorageSync('AIGICRM_TOKEN::' + (app0.globalData.apiBaseUrl || 'demo'), 'aigicrm-shell-automator-token')
  }, permissions)
}

async function verifyHomePage(miniProgram) {
  await enableShellDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/home/index')
  await page.waitFor(1000)

  await page.callMethod('applyState', {
    appName: 'AIGICRM',
    userName: '张三',
    orgName: '华东办事处',
    roleName: '销售经理',
    pendingCount: 3,
    showDashboardCards: true,
    cards: [
      [128, '我的客户'],
      [86, '在保护期'],
      [8, '即将到期'],
      [5, '已超期'],
      [2, '今日到期']
    ],
    expiring: [
      { id: 1, customerId: 501, customerName: '华东智能制造集团', ownerName: '张三', expireDate: '2026-09-30', remainingDays: 5 }
    ],
    today: { greeting: '下午好', date: '9月15日', weekday: '星期二' }
  })
  await page.waitFor(500)

  assert((await page.$$('.hm-chart-row')).length === 3, 'home dashboard chart rows are missing')
  assert((await page.$$('.hm-quick-item')).length === 6, 'home quick entries are missing')
  assert((await page.$$('.hm-warning')).length === 1, 'home expiring warning row is missing')

  const pageText = [
    ...(await textList(await page.$$('.hm-brand'))),
    ...(await textList(await page.$$('.hm-hero'))),
    ...(await textList(await page.$$('.hm-chart'))),
    ...(await textList(await page.$$('.hm-quick-item'))),
    ...(await textList(await page.$$('.hm-warning')))
  ].join('\n')
  // 【2026-10-23 二轮重锚】首页重设计: 用户令移除「我的客户 N 家」大数字指标块
  // (AIGICRM 居中放大+顶部再压缩), '我的客户'文本不再呈现于页面; 保护率迁至客户结构段头。
  for (const expected of ['AIGICRM', '张三', '今日到期', '新增客户', '四项服务', '激励上传', '审批管理', '提醒管理', '统计分析', '华东智能制造集团']) {
    assert(pageText.includes(expected), `home page missing ${expected}`)
  }

  await page.callMethod('openNewCustomer')
  await page.callMethod('openIncentive')
  await page.callMethod('openIncentiveUpload')
  await page.callMethod('openApproval')
  await page.callMethod('openReminder')
  await page.callMethod('openStatistics')
  await page.callMethod('openSea')
  await page.callMethod('openCustomer', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  const navigations = await miniProgram.evaluate(() => globalThis.__shellNavigationCalls || [])
  for (const expectedUrl of [
    '/pages/customer/form/index',
    // 【2026-10-23 拆分批补锚】首页四项服务快捷入口随 tabBar 拆分改指轻量页(switchTab),
    // 原钉 /pages/incentive/index 已陈旧——完整工作台入口在应用中心, 不再由首页直达
    '/pages/incentive/home/index',
    '/pages/incentive/upload/index',
    '/pages/approval/index',
    '/pages/reminder/index',
    '/pages/statistics/index',
    '/pages/sea/index',
    '/pages/customer/detail/index?id=501&from=home'
  ]) {
    assert(navigations.some((item) => item.url === expectedUrl), `home navigation missing ${expectedUrl}: ${JSON.stringify(navigations)}`)
  }
  await screenshot(miniProgram, 'mobile-shell-home.png')
}

async function verifyHomePermissionDenials(miniProgram) {
  await enablePermissionMode(miniProgram, ['customer:view'])
  const page = await miniProgram.reLaunch('/pages/home/index')
  await page.waitFor(800)

  assert(await page.data('showDashboardCards') === false, 'home dashboard cards must be hidden without dashboard/statistics permission')
  assert((await page.$$('.hm-chart-row')).length === 0, 'home dashboard chart rows must not render without dashboard/statistics permission')

  await page.callMethod('openIncentiveUpload')
  await page.callMethod('openApproval')
  await page.callMethod('openReminder')
  await page.callMethod('openStatistics')
  await page.callMethod('openSea')
  await page.waitFor(300)
  const toasts = await miniProgram.evaluate(() => globalThis.__shellToastCalls || [])
  for (const expected of ['没有四项服务上传权限', '没有审批权限', '没有提醒权限', '没有统计查看权限', '没有公海权限']) {
    assert(toasts.some((item) => item.title === expected), `home permission toast missing ${expected}: ${JSON.stringify(toasts)}`)
  }
}

async function verifyAppCenter(miniProgram) {
  await enablePermissionMode(miniProgram, ['customer:view', 'incentive:view', 'statistics:view'])
  const page = await miniProgram.reLaunch('/pages/app/index')
  await page.waitFor(1000)

  let apps = await page.data('apps')
  const appKeys = apps.map((item) => item.key).sort()
  for (const expected of ['customer', 'incentiveQuery', 'ranking', 'indicator', 'statistics']) {
    assert(appKeys.includes(expected), `app center missing allowed app ${expected}: ${JSON.stringify(appKeys)}`)
  }
  for (const denied of ['incentiveUpload', 'approval', 'reminder', 'sea']) {
    assert(!appKeys.includes(denied), `app center should hide unauthorized app ${denied}: ${JSON.stringify(appKeys)}`)
  }

  await page.callMethod('openApp', eventWithDataset({ key: 'customer' }))
  await page.callMethod('openApp', eventWithDataset({ key: 'incentiveQuery' }))
  await page.callMethod('openApp', eventWithDataset({ key: 'statistics' }))
  await page.callMethod('openApp', eventWithDataset({ key: 'ranking' }))
  await page.waitFor(300)
  let navigations = await miniProgram.evaluate(() => globalThis.__shellNavigationCalls || [])
  for (const expectedUrl of ['/pages/customer/index', '/pages/incentive/index', '/pages/statistics/index', '/pages/ranking/index']) {
    assert(navigations.some((item) => item.url === expectedUrl), `app center navigation missing ${expectedUrl}: ${JSON.stringify(navigations)}`)
  }

  await enablePermissionMode(miniProgram, [])
  const emptyPage = await miniProgram.reLaunch('/pages/app/index')
  await emptyPage.waitFor(800)
  apps = await emptyPage.data('apps')
  assert(apps.length === 0, `app center should show no apps without permissions: ${JSON.stringify(apps)}`)
  const emptyText = (await textList(await emptyPage.$$('.muted'))).join('\n')
  assert(emptyText.includes('暂无可用应用'), `app center empty state is missing: ${emptyText}`)
  await screenshot(miniProgram, 'mobile-shell-app-center.png')
}

async function verifyPcOnlyBoundary(miniProgram) {
  await enablePermissionMode(miniProgram, [
    'customer:view',
    'customer:import',
    'customer:export',
    'file:view',
    'file:acl:manage',
    'incentive:view',
    'incentive:edit',
    // 【2026-09-22 校准】应用中心"四项服务上传"入口守 incentive:add(pages/app/index.js:33),
    // 旧授权集缺此码 → 入口被正确隐藏而断言误报缺失
    'incentive:add',
    'system:config:view',
    'system:config:edit',
    'sea:view',
    'sea:import',
    'sea:config:view',
    'statistics:view',
    'score:view',
    'score:entry',
    'score:manage',
    'system:user:view',
    'system:role:view'
  ])
  const page = await miniProgram.reLaunch('/pages/app/index')
  await page.waitFor(800)

  const apps = await page.data('apps')
  const appText = (await textList(await page.$$('.ap-tile'))).join('\n')
  const rendered = `${apps.map((item) => `${item.key}:${item.title}:${item.subtitle}`).join('\n')}\n${appText}`
  // 【2026-11-04 统一命名】入口名对齐 PC 菜单权威名(激励上传), 模块副标题为当前文案
  for (const expected of ['激励上传', '四项服务', '上传、查询与统计']) {
    assert(rendered.includes(expected), `app center missing four-incentive naming ${expected}: ${rendered}`)
  }

  for (const expected of ['customer', 'incentiveQuery', 'ranking', 'indicator', 'statistics', 'sea']) {
    assert(apps.some((item) => item.key === expected), `PC-only boundary setup missing allowed mobile app ${expected}: ${JSON.stringify(apps)}`)
  }

  for (const forbidden of [
    '导入',
    '导出',
    '批量',
    '系统管理',
    '角色',
    '用户管理',
    '权限配置',
    '配置',
    'ACL',
    '下载设置',
    '公海配置',
    '审批配置',
    '激励授权',
    '分类配置',
    '学分录入',
    '积分配置'
  ]) {
    assert(!rendered.includes(forbidden), `app center must not expose PC-only capability ${forbidden}: ${rendered}`)
  }
  await screenshot(miniProgram, 'mobile-shell-pc-only-boundary.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installShellActionMocks(miniProgram)
    await verifyHomePage(miniProgram)
    await verifyHomePermissionDenials(miniProgram)
    await verifyAppCenter(miniProgram)
    await verifyPcOnlyBoundary(miniProgram)
  } finally {
    await restoreShellActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified mobile shell home dashboard, quick actions, permission denials, app-center filtering, app navigation UI, and PC-only boundary hiding.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})

#!/usr/bin/env node
// BUG-MP-TABBAR-PERM-RACE-20261127 回归（Node 桩，不依赖开发者工具）
//
// 缺陷: 冷启动时 app.onShow 才异步回填 /auth/info; 回填前 globalData.permissions=[],
// hasPermission() 把"尚未回填"误判成"没有权限"——底部「四项服务/法布施」被置灰并吞掉点击,
// 两个 tab 页的 onLoad/onShow 守卫还会把用户踢回首页。点一下「我的」触发 loadCurrentUser
// 后恢复, 所以表现为偶发。
//
// 本测试钉住:
//   ①未回填(有在途刷新) → 两个门控 tab 不 disabled, 点击能 switchTab;
//   ②回填完成(无码) → tabs 收敛为 disabled;
//   ③真无码(已回填) → onTap 仍弹原提示且不导航;
//   ④selected 滞留时按"当前路由"去重: 点当前页不重复 switchTab, 点其他 tab 正常;
//   ⑤四项服务页守卫: 未回填不踢人, 回填有码继续初始化, 无码才回首页;
//   ⑥法布施页 onLoad/onShow 守卫同口径;
//   ⑦无在途请求时 ensurePermissionsReady 主动补拉一次(首次 /auth/info 失败的恢复路径)。
// 所有 wx.* 都是桩, 不发真实请求; 不依赖"今天"的日期。
const assert = require('node:assert/strict')

const toasts = []
const navigations = []
const storage = {}
let routes = ['pages/home/index']
let app

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

function resetApp(patch) {
  toasts.length = 0
  navigations.length = 0
  routes = ['pages/home/index']
  app = {
    globalData: Object.assign({
      demoMode: false,
      apiBaseUrl: 'https://api.test',
      currentUser: { id: 2, username: 'NO001', roles: ['0'] },
      permissions: [],
      permissionsLoaded: false,
      permissionRefreshPromise: null,
      fabushiUnread: false
    }, patch || {})
  }
  global.getApp = () => app
}

global.wx = {
  request(opts) {
    // 只服务"主动补拉权限"路径: 返回 /auth/info 的最小成功响应
    if (opts && typeof opts.success === 'function') {
      opts.success({ statusCode: 200, data: { code: 200, data: { permissions: [] } }, header: {} })
    }
  },
  getStorageSync: (key) => (Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : 'tok'),
  setStorageSync: (key, value) => { storage[key] = value },
  removeStorageSync: (key) => { delete storage[key] },
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: () => {},
  switchTab: (opts) => navigations.push(opts && opts.url),
  redirectTo: (opts) => navigations.push(opts && opts.url),
  navigateTo: (opts) => navigations.push(opts && opts.url),
  navigateBack: () => {},
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } })
}
global.getCurrentPages = () => routes.map((route) => ({ route }))

resetApp()

// ---- 抓取组件与页面配置（字面量路径 require，与其它 test-miniprogram-*.js 同款）----
let componentConfig = null
global.Component = (cfg) => { componentConfig = cfg }
require('../aigicrm-miniprogram/custom-tab-bar/index.js')
assert.ok(componentConfig, 'tabBar 组件配置已捕获')

let incentiveConfig = null
global.Page = (cfg) => { incentiveConfig = cfg }
require('../aigicrm-miniprogram/pages/incentive/home/index.js')
assert.ok(incentiveConfig, '四项服务页配置已捕获')

let fabushiConfig = null
global.Page = (cfg) => { fabushiConfig = cfg }
require('../aigicrm-miniprogram/pages/fabushi/index.js')
assert.ok(fabushiConfig, '法布施页配置已捕获')

function makeTabBar() {
  const bar = {
    data: { selected: 0, tabs: [] },
    setData(patch) { Object.assign(this.data, patch) }
  }
  Object.assign(bar, componentConfig.methods)
  return bar
}

function makePage(config, overrides) {
  const page = Object.create(config)
  page.data = JSON.parse(JSON.stringify(config.data))
  page.setData = function (patch, done) {
    Object.assign(this.data, patch)
    if (typeof done === 'function') done()
  }
  Object.assign(page, overrides || {})
  return page
}

function tab(bar, key) {
  const found = bar.data.tabs.find((item) => item.key === key)
  assert.ok(found, 'tab 存在: ' + key)
  return found
}

function tap(bar, index) {
  bar.onTap({ currentTarget: { dataset: { index } } })
}

async function main() {
  let passed = 0
  const check = async (name, fn) => {
    await fn()
    passed += 1
    console.log('  ok  ' + name)
  }

  await check('未回填+在途: 两个门控 tab 不禁用, 点击能进入; 回填后按真实权限收敛', async () => {
    resetApp()
    let resolveRefresh
    app.globalData.permissionRefreshPromise = new Promise((resolve) => { resolveRefresh = resolve })
    const bar = makeTabBar()
    bar.refreshTabs()
    assert.equal(tab(bar, 'service').disabled, false, '未回填时四项服务不置灰')
    assert.equal(tab(bar, 'fabushi').disabled, false, '未回填时法布施不置灰')
    tap(bar, 1)
    assert.deepEqual(navigations, ['/pages/incentive/home/index'], '未回填时点四项服务应能导航')
    toasts.length = 0
    navigations.length = 0
    // 回填完成: 该账号无这两个码 → 收敛为 disabled
    app.globalData.permissionsLoaded = true
    app.globalData.permissions = []
    resolveRefresh(true)
    await flush()
    assert.equal(tab(bar, 'service').disabled, true, '回填后无码 → 四项服务置灰')
    assert.equal(tab(bar, 'fabushi').disabled, true, '回填后无码 → 法布施置灰')
    tap(bar, 1)
    assert.deepEqual(navigations, [], '回填后无码点击不导航')
    assert.deepEqual(toasts, ['暂无该模块权限'], '回填后无码弹原提示')
  })

  await check('真无码(已回填): 两个 tab 恒 disabled, onTap 不导航', async () => {
    resetApp({ permissions: [], permissionsLoaded: true })
    const bar = makeTabBar()
    bar.refreshTabs()
    assert.equal(tab(bar, 'service').disabled, true)
    assert.equal(tab(bar, 'fabushi').disabled, true)
    tap(bar, 1)
    tap(bar, 2)
    assert.deepEqual(navigations, [])
    assert.deepEqual(toasts, ['暂无该模块权限', '暂无该模块权限'])
  })

  await check('selected 滞留: 按当前路由去重, 点当前页不重复 switchTab, 点其他 tab 正常', async () => {
    resetApp({ permissions: ['incentive:view', 'fabushi:view'], permissionsLoaded: true })
    routes = ['pages/incentive/home/index']
    const bar = makeTabBar()
    bar.refreshTabs()
    assert.equal(bar.data.selected, 1, 'refreshTabs 应按路由校正 selected')
    // 模拟旧缺陷: selected 滞留首页(0), 但当前实际在四项服务页
    bar.data.selected = 0
    tap(bar, 1)
    assert.deepEqual(navigations, [], '点当前页(路由相同)不得重复 switchTab')
    tap(bar, 2)
    assert.deepEqual(navigations, ['/pages/fabushi/index'], '点其他 tab 正常导航')
  })

  await check('四项服务页守卫: 未回填不踢人, 回填有码继续初始化', async () => {
    resetApp()
    let resolveRefresh
    app.globalData.permissionRefreshPromise = new Promise((resolve) => { resolveRefresh = resolve })
    const calls = []
    const page = makePage(incentiveConfig, {
      loadCategoryTree: () => calls.push('categories'),
      loadIndustryOptions: () => calls.push('industry'),
      loadUsers: () => calls.push('users'),
      ensureDlToken: () => Promise.resolve(''),
      loadRecords: () => calls.push('records')
    })
    page.onLoad()
    assert.deepEqual(toasts, [], '未回填不得弹"没有四项服务查看权限"')
    assert.deepEqual(navigations, [], '未回填不得踢回首页')
    assert.deepEqual(calls, [], '未回填不得提前取数')
    app.globalData.permissionsLoaded = true
    app.globalData.permissions = ['incentive:view']
    resolveRefresh(true)
    await flush()
    assert.equal(page._loadedOnce, true, '回填有码后完成初始化')
    assert.ok(calls.includes('categories'), '回填后继续取分类')
    assert.deepEqual(navigations, [], '有码不踢人')
  })

  await check('四项服务页守卫: 已回填且无码 → 原提示 + 回首页', async () => {
    resetApp({ permissions: [], permissionsLoaded: true })
    const page = makePage(incentiveConfig, {})
    page.onLoad()
    assert.deepEqual(toasts, ['没有四项服务查看权限'])
    assert.deepEqual(navigations, ['/pages/home/index'])
    assert.equal(page._loadedOnce, undefined, '无码不进入初始化')
  })

  await check('法布施页 onLoad/onShow: 未回填不踢人, 回填有码继续; 无码才回首页', async () => {
    resetApp()
    let resolveRefresh
    app.globalData.permissionRefreshPromise = new Promise((resolve) => { resolveRefresh = resolve })
    const calls = []
    const page = makePage(fabushiConfig, {
      loadAll: () => calls.push('all'),
      loadPersonal: () => calls.push('personal'),
      loadAwaken: () => calls.push('awaken'),
      refreshReading: () => calls.push('reading'),
      loadReminder: () => calls.push('reminder')
    })
    page.onLoad()
    page.onShow()
    assert.deepEqual(toasts, [], '未回填不得弹"没有法布施查看权限"')
    assert.deepEqual(navigations, [], '未回填不得踢回首页')
    assert.equal(page._loadedOnce, undefined, '未回填不得提前完成初始化')
    app.globalData.permissionsLoaded = true
    app.globalData.permissions = ['fabushi:view']
    resolveRefresh(true)
    await flush()
    assert.equal(page._loadedOnce, true, '回填有码后完成初始化')
    assert.ok(calls.includes('all'), 'onLoad 继续取数')
    assert.ok(calls.includes('personal'), 'onShow 继续取数')
    assert.deepEqual(navigations, [], '有码不踢人')

    // 真无码: 已回填 → onShow 原提示 + 延迟回首页
    resetApp({ permissions: [], permissionsLoaded: true })
    const denied = makePage(fabushiConfig, {
      loadAll: () => {}, loadPersonal: () => {}, loadAwaken: () => {},
      refreshReading: () => {}, loadReminder: () => {}
    })
    denied.onShow()
    assert.deepEqual(toasts, ['没有法布施查看权限'])
    await new Promise((resolve) => setTimeout(resolve, 320))
    assert.deepEqual(navigations, ['/pages/home/index'], '无码延迟 300ms 回首页')
  })

  await check('无在途请求时主动补拉权限(首次 /auth/info 失败的恢复路径)', async () => {
    resetApp({ permissions: [], permissionsLoaded: false })
    const bar = makeTabBar()
    bar.refreshTabs()
    assert.equal(tab(bar, 'service').disabled, false, '补拉前按"未知"处理不禁用')
    await flush()
    assert.equal(app.globalData.permissionsLoaded, true, 'ensurePermissionsReady 主动拉到了 /auth/info')
    assert.equal(app.globalData.permissionRefreshPromise, null, '补拉结束后清空槽位')
    assert.equal(tab(bar, 'service').disabled, true, '拉取结果无码 → 收敛为置灰')
  })

  console.log('PASS: test-miniprogram-tabbar-perm (' + passed + ' 条)')
}

main().catch((error) => {
  console.error('FAIL: test-miniprogram-tabbar-perm')
  console.error(error && error.stack ? error.stack : error)
  process.exit(1)
})

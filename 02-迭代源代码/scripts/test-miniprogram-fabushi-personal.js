#!/usr/bin/env node
// 阅读记录(C3)与每日提醒(C4)两个页面(小程序)·页面逻辑·单元测试(2026-11-25)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”。阅读记录页(pages/fabushi/records/index.js)是新页面;
//   提醒设置页(pages/fabushi/settings/index.js)按原型重排, 频率改成了可以直接点的小按钮。
//   在 node 下驱动真实的页面 JS, wx.* 都是桩, 不联网。日历与时间线怎么算在 view-model 的测试里, 这里只管页面怎么取数、怎么翻月、怎么保存。
const assert = require('node:assert/strict')

const BASE = 'https://api.test'
const vm = require('../aigicrm-miniprogram/utils/fabushi-view-model')
const requests = []
const toasts = []
const backs = []
let fixtures = {}

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
const pick = (name, fallback) => (Object.prototype.hasOwnProperty.call(fixtures, name) ? fixtures[name] : fallback)
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  const respond = () => {
    if (/\/fabushi\/records$/.test(url)) return opts.success(ok(typeof fixtures.records === 'function' ? fixtures.records(opts.data || {}) : pick('records', { days: [] })))
    if (/\/fabushi\/stats\/personal$/.test(url)) return opts.success(ok(pick('personal', {})))
    if (/\/fabushi\/awaken\/status$/.test(url)) return opts.success(ok(pick('awake', {})))
    if (/\/fabushi\/settings$/.test(url)) {
      if (method === 'PUT') { if (fixtures.saveFail) return opts.fail({ errMsg: 'request:fail' }); return opts.success(ok(null)) }
      return opts.success(ok(pick('settings', {})))
    }
    return opts.success(ok(null))
  }
  const delay = fixtures.delay ? Number(fixtures.delay(url)) || 0 : 0
  if (delay > 0) setTimeout(respond, delay)
  else respond()
}

global.wx = {
  request: (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); route(opts) },
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: () => {},
  setNavigationBarTitle: () => {},
  navigateTo: () => {},
  navigateBack: () => backs.push(1),
  switchTab: () => {},
  stopPullDownRefresh: () => {},
}
const app = { globalData: { demoMode: false, apiBaseUrl: BASE, currentUser: { userId: 1, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let recordsConfig = null
let settingsConfig = null
// 两个页面文件各注册一次 Page: 按加载顺序接住(先阅读记录, 后每日提醒)
let pageCalls = 0
global.Page = (config) => { if (pageCalls === 0) recordsConfig = config; else settingsConfig = config; pageCalls += 1 }
require('../aigicrm-miniprogram/pages/fabushi/records/index.js')
require('../aigicrm-miniprogram/pages/fabushi/settings/index.js')
assert.ok(recordsConfig && settingsConfig, '两个页面的配置都已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 50))
const recordsCalls = () => requests.filter((r) => /\/fabushi\/records$/.test(r.url))
const settingsPuts = () => requests.filter((r) => /\/fabushi\/settings$/.test(r.url) && r.method === 'PUT')

function open(config) {
  requests.length = 0
  toasts.length = 0
  backs.length = 0
  const page = Object.create(config)
  page.data = JSON.parse(JSON.stringify(config.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad()
  return page
}

async function run() {
  let passed = 0
  const check = async (name, fn) => { await fn(); passed++; console.log('  ok  ' + name) }

  // ===== 阅读记录 =====
  await check('阅读记录: 进页面取当月的记录 + 个人统计 + 今日觉醒, 各一次; 按接口数据显示日历与四格', async () => {
    const now = new Date()
    const today = String(now.getDate()).padStart(2, '0')
    fixtures = { records: (params) => ({ month: params.month, days: [{ date: `${params.month}-${today}`, count: 2 }], readDays: 1, readCount: 2 }),
      personal: { myStreakDays: 3, myReadDays: 9, monthSongdu: 4, monthSuishi: 5 }, awake: { todayRecitations: 1, todayGifts: 0, todayPosts: 1 } }
    const page = open(recordsConfig)
    await settle()
    assert.deepEqual(recordsCalls().map((r) => r.data), [{ month: vm.monthKey(now) }], '记录接口带当月')
    assert.deepEqual({ month: page.data.monthKey, label: page.data.monthLabel, read: page.data.readDays, tiles: page.data.tiles.map((t) => t.num), today: page.data.timeline[0].right, loading: page.data.loading, next: page.data.canNext },
      { month: vm.monthKey(now), label: `${now.getFullYear()}年${now.getMonth() + 1}月`, read: 1, tiles: [3, 9, 4, 5], today: '还差随施', loading: false, next: false })
  })

  await check('阅读记录: “上个月”取上个月, “下个月”取回来; 已经在当月时“下个月”不发请求', async () => {
    fixtures = { records: (params) => ({ month: params.month, days: [] }) }
    const page = open(recordsConfig)
    await settle()
    const current = vm.monthKey(new Date())
    const prev = vm.shiftMonth(current, -1)
    page.onNextMonth(); await settle()
    assert.equal(recordsCalls().length, 1, '当月不能再往后翻')
    page.onPrevMonth(); await settle()
    assert.deepEqual({ asked: recordsCalls().at(-1).data.month, shown: page.data.monthKey, next: page.data.canNext }, { asked: prev, shown: prev, next: true })
    page.onNextMonth(); await settle()
    assert.deepEqual({ asked: recordsCalls().at(-1).data.month, shown: page.data.monthKey, next: page.data.canNext }, { asked: current, shown: current, next: false })
  })

  await check('阅读记录: 连着点两次“上个月”, 第一次的应答晚到不会盖掉第二次的', async () => {
    fixtures = { records: (params) => ({ month: params.month, days: [{ date: `${params.month}-03`, count: 1 }] }), delay: (url) => (/\/fabushi\/records$/.test(url) && fixtures.slow ? 30 : 0) }
    const page = open(recordsConfig)
    await settle()
    const current = vm.monthKey(new Date())
    fixtures.slow = true
    page.onPrevMonth()
    fixtures.slow = false
    page.onPrevMonth()
    await settle(); await settle()
    assert.equal(page.data.monthKey, vm.shiftMonth(current, -2), '显示的是两个月前(最后一次点的), 不是第一次的')
  })

  await check('阅读记录: 接口报错时提示、不再转圈', async () => {
    fixtures = {}
    const originalRequest = global.wx.request
    global.wx.request = (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); if (/\/fabushi\/records$/.test(opts.url)) opts.fail({ errMsg: 'request:fail' }); else route(opts) }
    const page = open(recordsConfig)
    await settle()
    global.wx.request = originalRequest
    assert.deepEqual({ loading: page.data.loading, toasted: toasts.length }, { loading: false, toasted: 1 })
  })

  // ===== 每日提醒 =====
  await check('每日提醒: 进页面按已存的设置显示; 认不出的频率回到每天; 没存过按默认(关、每天 20:00、有音效)', async () => {
    fixtures = { settings: { reminderEnabled: 1, freqCode: 'weekend', remindTime: '09:30', soundEnabled: 0 } }
    let page = open(settingsConfig)
    await settle()
    assert.deepEqual({ on: page.data.reminderEnabled, freq: page.data.freqs[page.data.freqIndex].value, time: page.data.remindTime, sound: page.data.soundEnabled },
      { on: true, freq: 'weekend', time: '09:30', sound: false })
    fixtures = { settings: { freqCode: 'nope' } }
    page = open(settingsConfig)
    await settle()
    assert.deepEqual({ on: page.data.reminderEnabled, freq: page.data.freqs[page.data.freqIndex].value, time: page.data.remindTime, sound: page.data.soundEnabled },
      { on: false, freq: 'daily', time: '20:00', sound: true })
  })

  await check('每日提醒: 点频率按钮切换; 点超出范围的不动', async () => {
    fixtures = { settings: {} }
    const page = open(settingsConfig)
    await settle()
    page.onFreqTap({ currentTarget: { dataset: { index: 2 } } })
    assert.equal(page.data.freqs[page.data.freqIndex].value, 'weekend')
    page.onFreqTap({ currentTarget: { dataset: { index: 7 } } })
    page.onFreqTap({ currentTarget: { dataset: { index: -1 } } })
    assert.equal(page.data.freqIndex, 2, '越界的不动')
  })

  await check('每日提醒: 保存的载荷 = 开关(1/0)、频率码、时间、音效(1/0); 成功后提示并返回; 连点不重复保存; 失败时提示、还能再保存', async () => {
    fixtures = { settings: { reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 } }
    let page = open(settingsConfig)
    await settle()
    page.onReminder({ detail: { value: true } })
    page.onFreqTap({ currentTarget: { dataset: { index: 1 } } })
    page.onTime({ detail: { value: '21:15' } })
    page.onSound({ detail: { value: false } })
    page.save()
    page.save()
    await settle()
    assert.deepEqual(settingsPuts().map((r) => r.data), [{ reminderEnabled: 1, freqCode: 'weekday', remindTime: '21:15', soundEnabled: 0 }], '连点只保存一次')
    assert.equal(toasts[0], '已保存')
    await new Promise((resolve) => setTimeout(resolve, 600))       // 保存成功后过半秒才返回
    assert.equal(backs.length, 1, '成功后返回上一页')

    fixtures = { saveFail: true }
    page = open(settingsConfig)
    await settle()
    const toastsBefore = toasts.length
    page.save()
    await settle()
    assert.deepEqual({ submitting: page.data.submitting, backs: backs.length, toasted: toasts.length - toastsBefore }, { submitting: false, backs: 0, toasted: 1 }, '失败: 提示一次、不返回、不再卡在保存中')
    fixtures.saveFail = false
    page.save()
    await settle()
    assert.equal(settingsPuts().length, 2, '可以再保存')
  })

  console.log(`PASS: test-miniprogram-fabushi-personal (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

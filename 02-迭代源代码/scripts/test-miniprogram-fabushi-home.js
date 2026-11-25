#!/usr/bin/env node
// 法布施主页(小程序)·页面逻辑·单元测试(2026-11-25, 先红后绿)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”, 主页(pages/fabushi/index.js)的取数与视图字段改动很大;
//   重做时还发现一个老缺陷 —— onShow 里加载提醒模块的路径多写了一级(11-19 起), 每次进页面都抛错, 提醒自检从没执行过。
// 做法: 在 node 下驱动真实的 pages/fabushi/index.js(参照 test-miniprogram-fabushi-detail.js 的 Page 捕获模式), wx.* 都是桩, 不联网。
// 抓不到: WXML 是否把这些字段画在正确位置(由开发者工具里逐屏截图与原型并排来核对)。
const assert = require('node:assert/strict')

// 测试用的假下载票据(片段拼接: 不是任何真实票据)
const FAKE_DOWNLOAD_TOKEN = ['dl-token', 'for-test'].join('-')
const requests = []        // wx.request 的调用: { url, method, data }
const toasts = []
const modals = []
const navigations = []     // navigateTo / switchTab / redirectTo
const actionSheets = []
const storage = {}
let fixtures = {}          // 每个场景自己设

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const pick = (name, fallback) => (Object.prototype.hasOwnProperty.call(fixtures, name) ? fixtures[name] : fallback)
  if (/\/fabushi\/awaken\/status/.test(url)) return opts.success(ok(pick('awake', {})))
  if (/\/fabushi\/stats\/personal/.test(url)) return opts.success(ok(pick('personal', {})))
  if (/\/fabushi\/settings/.test(url)) return opts.success(ok(pick('settings', {})))
  if (/\/fabushi\/books(\?|$)/.test(url)) return opts.success(ok(pick('books', { records: [], total: 0 })))
  if (/\/fabushi\/posts(\?|$)/.test(url)) return opts.success(ok(pick('posts', { records: [], total: 0 })))
  if (/\/fabushi\/posts\/\d+\/like$/.test(url)) return opts.success(ok(pick('like', { liked: true, likeCount: 5 })))
  if (/\/fabushi\/shelf\/groups/.test(url)) return opts.success(ok(pick('groups', [])))
  if (/\/fabushi\/shelf(\?|$)/.test(url)) return opts.success(ok(pick('shelf', { records: [], groups: [] })))
  if (/\/fabushi\/records/.test(url)) return opts.success(ok({ days: [], readDays: 0, readCount: 0 }))
  if (/\/fabushi\/stats\/dashboard/.test(url)) return opts.success(ok(pick('dashboard', {})))
  if (/\/fabushi\/stats\/monthly/.test(url)) return opts.success(ok(pick('monthly', {})))
  if (/download-token/.test(url)) return opts.success(ok({ token: FAKE_DOWNLOAD_TOKEN }))
  return opts.success(ok(true))
}

global.wx = {
  request: (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); route(opts) },
  getStorageSync: (key) => (Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : 'tok'),
  setStorageSync: (key, value) => { storage[key] = value },
  removeStorageSync: (key) => { delete storage[key] },
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: (opts) => modals.push(opts),
  showActionSheet: (opts) => actionSheets.push(opts),
  navigateTo: (opts) => navigations.push(opts.url),
  switchTab: (opts) => navigations.push(opts.url),
  redirectTo: (opts) => navigations.push(opts.url),
  navigateBack: () => {},
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
}
const app = { globalData: { demoMode: false, apiBaseUrl: 'https://api.test', currentUser: { userId: 1, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/index.js')
assert.ok(pageConfig, '主页配置已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 30))   // 让“取令牌 → 整理条目”这类多步的 Promise 链都走完
const tap = (value) => ({ currentTarget: { dataset: { value } } })
// “今天 / 昨天”是相对运行当天算的: 夹具里的时间与期望都按运行当天生成, 结果不随日期变。
// (2026-11-26 修正: 原来写死了 11-25 的日期, 门禁跨过零点后就红了)
const pad2 = (n) => String(n).padStart(2, '0')
const dayKey = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
const TODAY = dayKey(new Date())
const YESTERDAY = dayKey(new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() - 1))

function open() {
  requests.length = 0
  toasts.length = 0
  modals.length = 0
  navigations.length = 0
  actionSheets.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad()
  return page
}

async function run() {
  let passed = 0
  const check = async (name, fn) => { await fn(); passed++; console.log('  ok  ' + name) }

  await check('进页面不再抛错; 提醒自检真的会跑: 当天没发布、提醒开着 → 弹一次; 当天再进不重复弹', async () => {
    fixtures = { settings: { reminderEnabled: 1, freqCode: 'daily', remindTime: '00:00', soundEnabled: 1 }, awake: { todayPosts: 0, todayRecitations: 0, todayGifts: 0 } }
    const page = open()
    await settle()
    page.onShow()                                   // 修复前: 这里抛 MODULE_NOT_FOUND
    await settle()
    assert.equal(modals.filter((m) => m.title === '法布施提醒').length, 1, '弹了一次提醒')
    page.onShow()
    await settle()
    assert.equal(modals.filter((m) => m.title === '法布施提醒').length, 1, '同一天不重复弹')
  })

  await check('提醒关着, 或今天已经发布过: 不弹', async () => {
    fixtures = { settings: { reminderEnabled: 0 }, awake: { todayPosts: 0 } }
    let page = open()
    await settle()
    page.onShow()
    await settle()
    assert.equal(modals.length, 0, '提醒关着')
    fixtures = { settings: { reminderEnabled: 1, freqCode: 'daily', remindTime: '00:00' }, awake: { todayPosts: 2 } }
    page = open()
    await settle()
    page.onShow()
    await settle()
    assert.equal(modals.length, 0, '今天已发布')
  })

  await check('主卡与三板块: 数据到了之后重算 —— 三件都做了: 3/3、没有下一步按钮; 只做了随施: 下一步仍是去诵读', async () => {
    fixtures = { settings: { reminderEnabled: 0 }, awake: { todayRecitations: 2, todayGifts: 1, todayPosts: 4 }, personal: { myStreakDays: 4 } }
    let page = open()
    await settle()
    assert.deepEqual(
      { done: page.data.awakening.doneToday, next: page.data.awakening.nextScope, sub: page.data.awakening.songduSub },
      { done: 3, next: '', sub: '今天已诵 2 条 · 连续 4 天' })
    fixtures = { ...fixtures, awake: { todayRecitations: 0, todayGifts: 1, todayPosts: 1 } }
    page = open()
    await settle()
    assert.deepEqual({ done: page.data.awakening.doneToday, next: page.data.awakening.nextScope, label: page.data.awakening.nextLabel }, { done: 1, next: 'songdu', label: '去诵读' })

    // 不重开页面, 只把今天的诵读补上再回一次觉醒页签(重新取今日状态): 主卡要跟着变
    fixtures = { ...fixtures, awake: { todayRecitations: 2, todayGifts: 1, todayPosts: 4 } }
    page.loadAwaken()
    await settle()
    assert.deepEqual({ done: page.data.awakening.doneToday, next: page.data.awakening.nextScope }, { done: 3, next: '' }, '重新取数后重算')
  })

  await check('提醒卡: 接口的开关与时间 → “工作日 20:30 / 已开”', async () => {
    fixtures = { settings: { reminderEnabled: 1, freqCode: 'weekday', remindTime: '20:30' }, awake: {} }
    const page = open()
    await settle()
    assert.deepEqual(page.data.reminder, { on: true, text: '工作日 20:30' })
  })

  await check('在读的书: 进度 15/29 → 52%; 封面文字、带书名号的标题; 没有电子书进书目页, 有电子书直接进阅读器', async () => {
    fixtures = { shelf: { records: [{ id: 5, bookId: 12, title: '《了凡四训》', status: 'reading', lastPage: 15, totalPages: 29, pdfFileId: null }] } }
    let page = open()
    await settle()
    const book = page.data.readingBook
    assert.deepEqual({ progress: book.progress, cover: book.coverText, title: book.titleText, text: book.progressText, note: book.progressNote },
      { progress: 52, cover: '了凡四训', title: '《了凡四训》', text: '第 15/29 页 · 52%', note: '已读 52%' })
    page.goReadReading()
    assert.equal(navigations.at(-1), '/pages/fabushi/book/index?id=12', '没有电子书: 进书目页')
    fixtures = { shelf: { records: [{ id: 5, bookId: 12, title: '了凡四训', status: 'reading', lastPage: 15, totalPages: 29, pdfFileId: 77 }] } }
    page = open()
    await settle()
    assert.equal(page.data.readingBook.titleText, '《了凡四训》', '书名本身不带书名号时补上, 不重复套')
    page.goReadReading()
    assert.equal(navigations.at(-1), '/pages/fabushi/reader/index?bookId=12&pdfFileId=77', '有电子书: 直接进阅读器')

    // 书架里想读的排在前面: “当前精读”仍挑“在读”的那本; 一本在读的都没有时才退到第一本
    fixtures = { shelf: { records: [{ id: 7, bookId: 20, title: '活法', status: 'wish' }, { id: 5, bookId: 12, title: '了凡四训', status: 'reading', lastPage: 3 }] } }
    page = open()
    await settle()
    assert.deepEqual({ bookId: page.data.readingBook.bookId, text: page.data.readingBook.progressText }, { bookId: 12, text: '读到第 3 页' })
    fixtures = { shelf: { records: [{ id: 7, bookId: 20, title: '活法', status: 'wish' }] } }
    page = open()
    await settle()
    assert.equal(page.data.readingBook.bookId, 20, '没有在读的: 退到第一本')
  })

  await check('信息流: “全部”不传类型; 图片带下载令牌的地址; 类型名与时间; 搜索词原样传给接口', async () => {
    fixtures = { posts: { records: [
      { id: 9, scopeType: 'suishi', content: '下班路上的晚霞', createdAt: TODAY + 'T09:30:00', likeCount: 3, commentCount: 1,
        attachments: [{ id: 1, fileId: 101, fileType: 'image' }, { id: 2, fileId: 102, fileType: 'image' }, { id: 3, fileId: 103, fileType: 'image' }, { id: 4, fileId: 104, fileType: 'image' }] },
      { id: 10, scopeType: 'zaidu', title: '上善若水', createdAt: YESTERDAY + 'T07:50:00', attachments: [{ id: 5, fileId: 105, fileType: 'audio' }] }
    ], total: 2 } }
    const page = open()
    await settle()
    // GET 的查询参数是放在 data 里由 wx.request 带上的, 地址本身不带问号
    const listCalls = () => requests.filter((r) => /\/fabushi\/posts(\?|$)/.test(r.url))
    assert.equal(Object.prototype.hasOwnProperty.call(listCalls()[0].data, 'scopeType'), false, '“全部”不带类型参数')
    const [first, second] = page.data.posts
    assert.deepEqual({ type: first.typeLabel, time: first.timeText, pics: first.pics.length, url: first.pics[0].url, picCount: first.picCount },
      { type: '每日随施', time: '今天 09:30', pics: 3, url: 'https://api.test/api/file/download/101?token=' + FAKE_DOWNLOAD_TOKEN, picCount: 4 })
    assert.deepEqual({ type: second.typeLabel, time: second.timeText, audio: second.audioCount, pics: second.pics.length }, { type: '再读一本', time: '昨天 07:50', audio: 1, pics: 0 })

    page.onSearchInput({ detail: { value: ' 晚霞 ' } })
    page.onFeedTab(tap('zaidu'))
    assert.deepEqual({ tab: page.data.feedTab, keyword: page.data.searchKeyword }, { tab: 'zaidu', keyword: '' }, '切换类型清空搜索词')
    page.onSearchInput({ detail: { value: ' 晚霞 ' } })
    page.onSearch()
    await settle()
    assert.deepEqual(listCalls().at(-1).data, { current: 1, size: 20, scopeType: 'zaidu', keyword: '晚霞' }, '搜索词去掉首尾空格后传给接口')
  })

  await check('广场页签点“木鱼”: 调与详情页同一个接口, 就地只改这一条的木鱼数与“我点没点”; 没有发布权限时不发请求', async () => {
    fixtures = { like: { liked: true, likeCount: 5 }, posts: { records: [
      { id: 9, scopeType: 'suishi', content: '晚霞', likeCount: 4, likedByMe: false },
      { id: 10, scopeType: 'zaidu', title: '上善若水', likeCount: 1 }
    ], total: 2 } }
    let page = open()
    await settle()
    assert.deepEqual(page.data.posts.map((p) => [p.likeCount, p.likedByMe]), [[4, false], [1, false]])
    page.onFeedLike({ currentTarget: { dataset: { id: 9 } } })
    await settle()
    const writes = requests.filter((r) => r.method === 'POST')
    assert.deepEqual(writes.map((r) => r.url), ['https://api.test/api/fabushi/posts/9/like'], '对第 9 条发一次木鱼请求(请求层会在前面拼上接口域名)')
    assert.deepEqual(page.data.posts.map((p) => [p.likeCount, p.likedByMe]), [[5, true], [1, false]], '接口返回后只改了第 9 条')
    page.setData({ canCreate: false })
    page.onFeedLike({ currentTarget: { dataset: { id: 9 } } })
    await settle()
    assert.equal(requests.filter((r) => r.method === 'POST').length, 1, '没有发布权限: 不发请求')
  })

  await check('书库页签: 书目带上封面文字(小方块里只放两个字)与底色', async () => {
    fixtures = { books: { records: [{ id: 3, title: '唐诗', categoryName: '诗歌类', memberCount: 0 }, { id: 4, title: '《竞争三部曲》', categoryName: '管理类' }], total: 2 } }
    const page = open()
    await settle()                                     // 先让首屏那次取数落定, 下面看到的才是“切到书库页签”那一次的结果
    page.onTab(tap('lib'))
    await settle()
    assert.deepEqual(page.data.books.map((b) => b.coverText), ['唐诗', '竞争'])
    assert.deepEqual(page.data.books.map((b) => b.coverTone), [3, 4].map((n) => require('../aigicrm-miniprogram/utils/fabushi-view-model').coverTone(n)))
  })

  await check('书架页签: 状态名、封面、小字; 管理模式下点封面按“书架记录编号”勾选, 再点取消; 平时点进书目页', async () => {
    fixtures = { shelf: { records: [
      { id: 5, bookId: 12, title: '《了凡四训》', status: 'reading', lastPage: 15, totalPages: 100, progressPct: 15 },
      { id: 6, bookId: 13, title: '活法', status: 'wish' }
    ] }, groups: [] }
    const page = open()
    page.onTab(tap('shelf'))
    await settle()
    assert.deepEqual(page.data.visibleShelf.map((r) => [r.statusText, r.coverText, r.titleText, r.subText]),
      [['在读', '了凡四训', '了凡四训', '第 15/100 页 · 15%'], ['想读', '活法', '活法', '想读']])

    page.onShelfTap({ currentTarget: { dataset: { id: 5, bookId: 12 } } })
    assert.equal(navigations.at(-1), '/pages/fabushi/book/index?id=12', '平时: 进书目页')

    page.toggleManage()
    assert.equal(page.data.manageMode, true, '进入管理模式')
    page.onShelfTap({ currentTarget: { dataset: { id: 5, bookId: 12 } } })
    assert.deepEqual({ selected: page.data.selected, first: page.data.visibleShelf[0].checked, second: page.data.visibleShelf[1].checked }, { selected: [5], first: true, second: false }, '按记录编号 5 勾选第一本')
    page.onShelfTap({ currentTarget: { dataset: { id: 5, bookId: 12 } } })
    assert.deepEqual({ selected: page.data.selected, first: page.data.visibleShelf[0].checked }, { selected: [], first: false }, '再点一次取消')
    page.onBatch({ currentTarget: { dataset: { action: 'pin' } } })
    assert.ok(toasts.includes('请先选择书目'), '没选就点批量: 提示先选')
    assert.equal(requests.filter((r) => r.method !== 'GET').length, 0, '没有发出任何写请求')
    page.onShelfTap({ currentTarget: { dataset: { id: 6, bookId: 13 } } })
    page.toggleManage()
    assert.deepEqual({ manage: page.data.manageMode, selected: page.data.selected }, { manage: false, selected: [] }, '退出管理模式时清空选择')
  })

  await check('点书架格子下面的状态: 弹出四个状态可选', async () => {
    const page = open()
    page.onTab(tap('shelf'))
    await settle()
    page.changeShelfStatus({ currentTarget: { dataset: { id: 6 } } })
    assert.equal(actionSheets.length, 1)
    assert.deepEqual(actionSheets[0].itemList, ['想读', '在读', '已读完', '收藏'])
  })

  await check('统计页签(团队): 月份、三类合计与复诵(按成员榜全量逐行加)、环比角标、榜单条宽按最大值折成百分比; 负数的人数不当人数显示; 可切到个人', async () => {
    // 成员榜有 9 行: 页面上只列前 8 行, 但四格的合计要按全量 9 行算
    const ranking = [
      { userId: 1, userName: '甲', postCount: 6, songduCount: 4, suishiCount: 2, zaiduCount: 0, recitationsReceived: 3 },
      { userId: 2, userName: '乙', postCount: 3, songduCount: 1, suishiCount: 1, zaiduCount: 1, recitationsReceived: 0 }
    ].concat(Array.from({ length: 7 }, (_, i) => ({ userId: 10 + i, userName: '成员' + i, postCount: 1, songduCount: 1, suishiCount: 0, zaiduCount: 0, recitationsReceived: 0 })))
    fixtures = {
      monthly: { month: '2026-11', posts: 6, likes: 29, comments: 6, readUsers: 3, memberCount: -1, prev: { postsPct: 12, likesPct: -5, commentsPct: 0, readUsersPct: null },
        ranking, categoryReads: [{ categoryCode: 'a', categoryName: '国学', readCount: 4 }, { categoryCode: 'b', categoryName: '管理', readCount: 1 }],
        bookRanking: [{ bookId: 1, title: '了凡四训', readCount: 2 }] },
      personal: { monthSongdu: 1, monthSuishi: 2, monthZaidu: 3, myStreakDays: 7 }
    }
    const page = open()
    await settle()
    page.onTab(tap('stats'))
    await settle()
    assert.deepEqual({ can: page.data.canViewStats, scope: page.data.stats.scope, month: page.data.stats.monthLabel, people: page.data.stats.memberCount },
      { can: true, scope: 'team', month: '11月', people: 0 })
    assert.deepEqual({ rows: page.data.monthlyRanking.length, totals: page.data.teamTotals }, { rows: 8, totals: { songdu: 12, suishi: 3, zaidu: 1, recitations: 3 } })
    assert.deepEqual(page.data.trend, { posts: '↑ 12%', likes: '↓ 5%', comments: '', readUsers: '' }, '环比: 没有数或正好是 0 时不显示角标')
    assert.deepEqual(page.data.monthlyRanking.slice(0, 2).map((r) => [r.userName, r.barPct]), [['甲', 100], ['乙', 50]], '成员榜只列前 8 行, 条宽按最大值折算')
    assert.ok(page.data.monthlyRanking.slice(2).every((r) => r.barPct === 17), '其余各 1 条: 1/6 ≈ 17%')
    assert.deepEqual(page.data.categoryBars.map((r) => [r.categoryName, r.barPct]), [['国学', 100], ['管理', 25]])
    assert.deepEqual(page.data.bookRows.map((r) => [r.title, r.barPct]), [['了凡四训', 100]])
    page.onStatsScope({ currentTarget: { dataset: { scope: 'personal' } } })
    assert.equal(page.data.stats.scope, 'personal')
    assert.deepEqual({ a: page.data.personal.monthSongdu, b: page.data.personal.monthZaidu }, { a: 1, b: 3 })
  })

  console.log(`PASS: test-miniprogram-fabushi-home (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

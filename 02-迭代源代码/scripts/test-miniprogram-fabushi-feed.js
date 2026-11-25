#!/usr/bin/env node
// 认知广场(小程序)·页面逻辑·单元测试(2026-11-25, 先红后绿)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”, 认知广场页(pages/fabushi/feed/index.js)按原型 A2 重排后多了几样逻辑:
//   “全部”分区、服务端搜索、“只看未读”(前端过滤 + 连取)、⋯ 菜单(木鱼 / 评论 / 复诵)、按书浏览的选书面板。
// 做法: 在 node 下驱动真实的页面 JS(参照 test-miniprogram-fabushi-home.js 的做法), wx.* 都是桩, 不联网。
// 抓不到: 画面(由开发者工具里截图与原型并排核对)。
const assert = require('node:assert/strict')

// 测试用的假下载票据(片段拼接: 不是任何真实票据)
const FAKE_DOWNLOAD_TOKEN = ['dl-token', 'for-test'].join('-')
const requests = []        // wx.request 的调用: { url, method, data }
const toasts = []
const navigations = []     // navigateTo
const actionSheets = []
let fixtures = {}          // 每个场景自己设

// (名字, 地址匹配, 没给夹具时的默认返回)。夹具可以给固定值, 也可以给函数 (请求参数) => 返回值
const ROUTES = [
  ['token', /\/download-token/, () => ({ token: FAKE_DOWNLOAD_TOKEN })],
  ['like', /\/fabushi\/posts\/\d+\/like$/, () => ({})],
  ['books', /\/fabushi\/books(\?|$)/, () => ({ records: [], total: 0 })],
  ['posts', /\/fabushi\/posts(\?|$)/, () => ({ records: [], total: 0 })]
]

function route(opts) {
  const hit = ROUTES.find(([, pattern]) => pattern.test(opts.url))
  const given = hit && Object.prototype.hasOwnProperty.call(fixtures, hit[0]) ? fixtures[hit[0]] : undefined
  const data = typeof given === 'function' ? given(opts.data || {}, opts.url) : (given !== undefined ? given : (hit ? hit[2]() : true))
  const respond = () => opts.success({ statusCode: 200, data: { code: 200, data } })
  const delay = fixtures.delay ? Number(fixtures.delay(opts.url)) || 0 : 0
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
  showActionSheet: (opts) => actionSheets.push(opts),
  navigateTo: (opts) => navigations.push(opts.url),
  navigateBack: () => {},
  stopPullDownRefresh: () => {},
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
}
const app = { globalData: { demoMode: false, apiBaseUrl: 'https://api.test', currentUser: { userId: 1, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/feed/index.js')
assert.ok(pageConfig, '页面配置已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 40))   // 让“取令牌 → 整理条目”“连取下一页”这类多步的 Promise 链都走完
const tap = (value) => ({ currentTarget: { dataset: { value } } })
const postCalls = () => requests.filter((r) => /\/fabushi\/posts(\?|$)/.test(r.url))
// “今天 / 昨天”是相对运行当天算的: 夹具里的时间与期望都按运行当天生成, 结果不随日期变。
// (2026-11-26 修正: 原来写死了 11-24 的日期, 门禁跨过零点后就红了)
const pad2 = (n) => String(n).padStart(2, '0')
const runDate = new Date()
const yesterday = new Date(runDate.getFullYear(), runDate.getMonth(), runDate.getDate() - 1)
const YESTERDAY = `${yesterday.getFullYear()}-${pad2(yesterday.getMonth() + 1)}-${pad2(yesterday.getDate())}`
const post = (id, extra) => ({ id, scopeType: 'suishi', content: '内容' + id, createdAt: YESTERDAY + 'T18:40:00', readByMe: false, likeCount: 0, commentCount: 0, ...(extra || {}) })
// 第 page 页: 每页 PAGE_SIZE(10) 条, 共 total 条
const paged = (total, make) => (params) => {
  const current = Number(params.current) || 1
  const size = Number(params.size) || 10
  const start = (current - 1) * size
  const records = []
  for (let i = start; i < Math.min(total, start + size); i++) records.push(make ? make(i) : post(i + 1))
  return { records, total }
}

function open() {
  requests.length = 0
  toasts.length = 0
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

  await check('进页面默认“全部”: 不带类型参数; 条目带类型名、时间、头像首字与底色、图片地址', async () => {
    fixtures = { posts: { records: [
      post(9, { scopeType: 'zaidu', title: '上善若水', bookTitle: '道德经', sectionTitle: '第八章', userId: 12, userName: '王晓兰', attachments: [{ id: 1, fileId: 101, fileType: 'image' }], isNew: true }),
      post(10, { scopeType: 'suishi', userName: '陈慧', userId: 13 })
    ], total: 2 } }
    const page = open()
    await settle()
    assert.deepEqual(postCalls()[0].data, { current: 1, size: 10 }, '“全部”不带类型参数')
    const [first, second] = page.data.posts
    assert.deepEqual({ type: first.typeLabel, time: first.timeText, avatar: first.avatarText, tone: first.avatarTone, isNew: first.isNew, url: first.pics[0].url },
      { type: '再读一本', time: '昨天 18:40', avatar: '王', tone: 'navy', isNew: true, url: 'https://api.test/api/file/download/101?token=' + FAKE_DOWNLOAD_TOKEN })
    assert.deepEqual({ type: second.typeLabel, avatar: second.avatarText, tone: second.avatarTone, pics: second.pics.length }, { type: '每日随施', avatar: '陈', tone: 'cyan', pics: 0 })
    assert.deepEqual({ active: page.data.active, more: page.data.noMore, total: page.data.total }, { active: 'all', more: true, total: 2 })
  })

  await check('分页: 每页 10 条, 触底接着取, 取够总数就停, 再触底不发请求', async () => {
    fixtures = { posts: paged(25) }
    const page = open()
    await settle()
    assert.deepEqual({ n: page.data.posts.length, more: page.data.noMore }, { n: 10, more: false })
    page.onReachBottom(); await settle()
    assert.deepEqual({ n: page.data.posts.length, last: postCalls().at(-1).data.current }, { n: 20, last: 2 })
    page.onReachBottom(); await settle()
    assert.deepEqual({ n: page.data.posts.length, last: postCalls().at(-1).data.current, more: page.data.noMore }, { n: 25, last: 3, more: true })
    const calls = postCalls().length
    page.onReachBottom(); await settle()
    assert.equal(postCalls().length, calls, '到底之后不再请求')
  })

  await check('分页: 接口给了一页空的(与总数对不上)也算到底, 不无限触底重试', async () => {
    // 接口说一共 60 条, 但第 3 页开始给空页(总数与内容对不上): 不能因为“还没取够 60 条”就一直取下去
    fixtures = { posts: (params) => (Number(params.current) >= 3 ? { records: [], total: 60 } : paged(60)(params)) }
    const page = open()
    await settle()
    page.onReachBottom(); await settle()
    page.onReachBottom(); await settle()
    assert.deepEqual({ n: page.data.posts.length, more: page.data.noMore }, { n: 20, more: true }, '第 3 页是空的: 到底了')
    const calls = postCalls().length
    page.onReachBottom(); await settle()
    assert.equal(postCalls().length, calls, '到底之后不再请求')
  })

  await check('切换分区: 带类型参数、从头取; 旧的应答晚到不会盖掉新的', async () => {
    // 诵读分区的应答故意慢 30ms: 切过去后紧接着切到随施(应答立即回), 诵读的结果晚到必须被丢掉
    fixtures = {
      delay: (url) => (/\/fabushi\/posts/.test(url) && fixtures.slow ? 30 : 0),
      posts: (params) => (params.scopeType === 'songdu' ? { records: [post(1, { content: '诵读' })], total: 1 } : { records: [post(2, { content: '随施' })], total: 1 })
    }
    const page = open()
    await settle()
    fixtures.slow = true
    page.onFilterTap(tap('songdu'))
    fixtures.slow = false
    page.onFilterTap(tap('suishi'))
    await settle(); await settle()
    assert.deepEqual(postCalls().slice(1).map((r) => r.data.scopeType), ['songdu', 'suishi'])
    assert.deepEqual(page.data.posts.map((p) => p.content), ['随施'], '晚到的诵读结果没有盖掉随施')
    assert.equal(page.data.active, 'suishi')
  })

  await check('搜索: 搜索词去掉首尾空格后传给接口; 把搜索词删空了就回到不筛词的列表', async () => {
    fixtures = { posts: paged(3) }
    const page = open()
    await settle()
    page.onKeywordInput({ detail: { value: ' 晚霞 ' } })
    page.onSearch()
    await settle()
    assert.equal(postCalls().at(-1).data.keyword, '晚霞')
    page.onKeywordInput({ detail: { value: '晚霞 ' } })
    await settle()
    assert.equal(postCalls().length, 2, '搜索词没删空: 不重新取')
    page.onKeywordInput({ detail: { value: '  ' } })
    await settle()
    assert.equal(Object.prototype.hasOwnProperty.call(postCalls().at(-1).data, 'keyword'), false, '删空了: 不带搜索词重取')
    assert.equal(postCalls().length, 3)
  })

  await check('只看未读: 已读的滤掉; 这一页滤完不剩而后面还有, 就接着取下一页', async () => {
    // 25 条里只有第 12、13 条未读(都在第 2 页)
    fixtures = { posts: paged(25, (i) => post(i + 1, { readByMe: ![11, 12].includes(i) })) }
    const page = open()
    await settle()
    page.onToggleUnread()
    await settle()
    assert.deepEqual({ shown: page.data.posts.map((p) => p.id), pages: postCalls().map((r) => r.data.current).slice(1), more: page.data.noMore, unreadOnly: page.data.unreadOnly },
      { shown: [12, 13], pages: [1, 2], more: false, unreadOnly: true })
    page.onReachBottom(); await settle()
    assert.deepEqual({ last: postCalls().at(-1).data.current, shown: page.data.posts.length, more: page.data.noMore }, { last: 3, shown: 2, more: true }, '接着从第 3 页取, 取完到底')
  })

  await check('只看未读: 一口气连取 5 页还没有未读就先停下(不无限取), 再触底从第 6 页接着取、取到底为止', async () => {
    fixtures = { posts: paged(80, (i) => post(i + 1, { readByMe: true })) }   // 80 条全部已读
    const page = open()
    await settle()
    page.onToggleUnread()
    await settle()
    assert.deepEqual({ shown: page.data.posts.length, fetched: postCalls().map((r) => r.data.current).slice(1), more: page.data.noMore },
      { shown: 0, fetched: [1, 2, 3, 4, 5], more: false })
    page.onReachBottom(); await settle()
    assert.deepEqual({ fetched: postCalls().map((r) => r.data.current).slice(6), shown: page.data.posts.length, more: page.data.noMore },
      { fetched: [6, 7, 8], shown: 0, more: true }, '从第 6 页接着取, 第 8 页取够总数就停')
  })

  await check('⋯ 菜单: 木鱼就地更新(与详情页同一个接口); 评论、复诵跳到详情页对应位置; 没有发布权限时不给菜单、直接进详情', async () => {
    fixtures = { like: { liked: true, likeCount: 5 }, posts: { records: [post(9, { likeCount: 4, likedByMe: false }), post(10, { likeCount: 1 })], total: 2 } }
    const page = open()
    await settle()
    const more = { currentTarget: { dataset: { id: 9 } } }
    page.onMore(more)
    assert.deepEqual(actionSheets.at(-1).itemList, ['点木鱼', '写评论', '我要复诵'])
    actionSheets.at(-1).success({ tapIndex: 0 })
    await settle()
    assert.deepEqual(requests.filter((r) => r.method === 'POST').map((r) => r.url), ['https://api.test/api/fabushi/posts/9/like'])
    assert.deepEqual(page.data.posts.map((p) => [p.likeCount, p.likedByMe]), [[5, true], [1, false]])
    page.onMore(more)
    assert.equal(actionSheets.at(-1).itemList[0], '取消木鱼', '点过之后菜单里是“取消木鱼”')
    actionSheets.at(-1).success({ tapIndex: 1 })
    actionSheets.at(-1).success({ tapIndex: 2 })
    assert.deepEqual(navigations.splice(0), ['/pages/fabushi/detail/index?id=9&focus=comment', '/pages/fabushi/detail/index?id=9&focus=recite'])
    page.setData({ canCreate: false })
    page.onMore(more)
    assert.equal(actionSheets.length, 2, '没有发布权限: 不弹菜单')
    assert.deepEqual(navigations, ['/pages/fabushi/detail/index?id=9'])
  })

  await check('按书浏览: 打开面板时取一次书库, 条目带封面文字(两个字)与底色; 点书 → 关面板、进按书浏览页; 再打开不重复取', async () => {
    fixtures = { books: { records: [{ id: 3, title: '《竞争三部曲》', categoryName: '管理类', memberCount: 2, postCount: 7 }, { id: 4, title: '活法' }], total: 2 }, posts: { records: [], total: 0 } }
    const page = open()
    await settle()
    page.onOpenPicker()
    await settle()
    const bookCalls = () => requests.filter((r) => /\/fabushi\/books(\?|$)/.test(r.url)).length
    assert.deepEqual({ open: page.data.pickerOpen, calls: bookCalls(), rows: page.data.books.map((b) => [b.coverText, b.title]) }, { open: true, calls: 1, rows: [['竞争', '《竞争三部曲》'], ['活法', '活法']] })
    assert.equal(page.data.books[0].coverTone, require('../aigicrm-miniprogram/utils/fabushi-view-model').coverTone(3))
    page.onBookTap({ currentTarget: { dataset: { id: 4 } } })
    assert.deepEqual({ open: page.data.pickerOpen, nav: navigations.at(-1) }, { open: false, nav: '/pages/fabushi/bybook/index?bookId=4' })
    page.onOpenPicker()
    await settle()
    assert.equal(bookCalls(), 1, '书库已经有了: 不重复取')
  })

  await check('从别的页面回来: 开着“只看未读”就重取一遍(看过的不再留着), 平时不重取', async () => {
    fixtures = { posts: paged(4) }
    const page = open()
    await settle()
    page.onHide(); page.onShow()
    await settle()
    assert.equal(postCalls().length, 1, '平时回到页面不重取')
    page.onToggleUnread()
    await settle()
    assert.equal(postCalls().length, 2)
    page.onHide(); page.onShow()
    await settle()
    assert.equal(postCalls().length, 3, '只看未读: 回到页面重取')
  })

  console.log(`PASS: test-miniprogram-fabushi-feed (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

#!/usr/bin/env node
// 按书浏览(小程序)·页面逻辑·单元测试(2026-11-25)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”, 按书浏览页(pages/fabushi/bybook/index.js)按原型 A3 重排;
//   这一页之前没有 node 测试(分页、三种排列、书头卡的数都只靠人工走过)。在 node 下驱动真实的页面 JS, wx.* 都是桩, 不联网。
// 抓不到: 画面(由开发者工具里截图与原型并排核对)。
const assert = require('node:assert/strict')

const BASE = 'https://api.test'
const requests = []
const toasts = []
const navigations = []
const titles = []
let fixtures = {}

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const isDetail = /\/fabushi\/books\/6$/.test(url)
  const isList = /\/fabushi\/books$/.test(url)
  if (isDetail) return opts.success(ok(fixtures.book || {}))
  if (isList) return opts.success(ok(fixtures.allBooks || { records: [], total: 0 }))
  // 列表的夹具可以给固定值, 也可以给函数 (请求参数) => 返回值
  const posts = fixtures.posts
  if (/\/fabushi\/posts$/.test(url)) return opts.success(ok(typeof posts === 'function' ? posts(opts.data || {}) : (posts || { records: [], total: 0 })))
  return opts.success(ok(true))
}

global.wx = {
  request: (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); route(opts) },
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: () => {},
  setNavigationBarTitle: (opts) => titles.push(opts && opts.title),
  navigateTo: (opts) => navigations.push(opts.url),
  navigateBack: () => {},
  stopPullDownRefresh: () => {},
}
const app = { globalData: { demoMode: false, apiBaseUrl: BASE, currentUser: { userId: 1, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/bybook/index.js')
assert.ok(pageConfig, '页面配置已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 40))
const tap = (value) => ({ currentTarget: { dataset: { value } } })
// 往年的日期: “今天 / 昨天”是相对当前日期算的, 用往年日期测试结果不随运行日期变
const row = (id, extra) => Object.assign({ id, scopeType: 'zaidu', userId: 10 + id, userName: '成员' + id, content: '正文' + id, createdAt: '2025-01-02T07:50:00' }, extra || {})

function open(query) {
  requests.length = 0
  toasts.length = 0
  navigations.length = 0
  titles.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad(query || { bookId: '6' })
  return page
}

async function run() {
  let passed = 0
  const check = async (name, fn) => { await fn(); passed++; console.log('  ok  ' + name) }

  await check('书头卡: 封面文字(去书名号)、底色、版本信息、参与人数、另有几个版本、导航标题带上书名; “再读内容”用列表自己的总数', async () => {
    fixtures = {
      book: { id: 6, title: '《道德经》', author: '陈鼓应', publisher: '中华书局 2009', editionType: 'annotated', pages: 512, pdfFileId: 77, memberCount: 8, postCount: 36 },
      allBooks: { records: [{ id: 6, title: '《道德经》' }, { id: 7, title: '《道德经》' }, { id: 8, title: '道德经' }, { id: 9, title: '论语' }] },
      posts: { records: [row(1)], total: 12 }
    }
    const page = open()
    await settle(); await settle()
    assert.deepEqual({ cover: page.data.coverText, tone: page.data.coverTone, version: page.data.versionText, members: page.data.memberCount, others: page.data.otherVersions },
      { cover: '道德经', tone: 'navy', version: '陈鼓应 · 中华书局 2009 · 注释本 · 512 页', members: 8, others: 2 },
      '封面文字去掉书名号; 版本信息按 作者 · 出版社 · 版本类型 · 页数; 同名的另一版本带不带书名号都算')
    assert.deepEqual(titles, ['按书浏览', '按书浏览 · 道德经'], '进页面先叫“按书浏览”, 书目信息到了之后标题带上书名')
    assert.equal(page.data.total, 12, '再读内容的条数 = 列表接口的总数(不是书目接口里含诵读、随施的 36)')
  })

  await check('成员行: 头像首字与底色、时间、形式(书页 / 语音 / 视频 / 文件)、摘要(标题优先, 否则正文前 40 字)、NEW', async () => {
    fixtures = { book: { id: 6, title: '道德经' }, posts: { records: [
      row(1, { title: '上善若水', content: '抄了三遍', isNew: 1, attachments: [{ fileType: 'image' }, { fileType: 'image' }, { fileType: 'image' }, { fileType: 'audio' }], sectionTitle: '第八章' }),
      row(2, { userName: '', content: '一二三四五六七八九十'.repeat(5), attachments: [{ fileType: 'video' }, { fileType: 'pdf' }] }),
      row(3, { content: '   ' })
    ], total: 3 } }
    const page = open()
    await settle(); await settle()
    const [a, b, c] = page.data.groups[0].items
    assert.deepEqual([a.avatarText, a.avatarTone, a.timeText, a.formText, a.summary, a.isNew], ['成', 'gray', '2025-01-02', '书页 3 张 + 语音', '上善若水', true])
    assert.deepEqual([b.avatarText, b.formText, b.summary], ['法', '视频 + 文件 1', '一二三四五六七八九十'.repeat(4) + '…'], '没有昵称时头像给“法”; 摘要截到 40 字加省略号')
    assert.deepEqual([c.formText, c.summary], ['', '（无正文）'])
  })

  await check('三种排列: 按时间平铺(不出组头); 按章节按章节名分组(没章节的归“未分章”); 按成员按昵称分组; 每组条数对得上', async () => {
    fixtures = { book: { id: 6, title: '道德经' }, posts: { records: [
      row(1, { userId: 20, userName: '王晓兰', sectionTitle: '第八章' }), row(2, { userId: 21, userName: '陈慧', sectionTitle: '序' }),
      row(3, { userId: 20, userName: '王晓兰' }), row(4, { userId: 21, userName: '陈慧', sectionTitle: '第八章' }),
      row(5, { userId: 22, userName: '王晓兰' })
    ], total: 5 } }
    const page = open()
    await settle(); await settle()
    const shape = () => page.data.groups.map((g) => [g.title, g.count, g.items.map((x) => x.id)])
    assert.deepEqual(shape(), [['', 5, [1, 2, 3, 4, 5]]], '按时间: 一组、不出组头、保持接口顺序')
    page.onSortTap(tap('section'))
    assert.deepEqual(shape(), [['第八章', 2, [1, 4]], ['序', 1, [2]], ['未分章', 2, [3, 5]]])
    page.onSortTap(tap('member'))
    assert.deepEqual(shape(), [['王晓兰', 2, [1, 3]], ['陈慧', 2, [2, 4]], ['王晓兰', 1, [5]]], '按成员: 同名但是不同的人, 分开列')
    page.onSortTap(tap('time'))
    assert.deepEqual(shape(), [['', 5, [1, 2, 3, 4, 5]]], '切回按时间')
  })

  await check('分页: 每页 20 条, 触底接着取, 取够总数就停; 末页恰好整除时不会再多打一次空请求', async () => {
    fixtures = { book: { id: 6, title: '道德经' }, posts: (params) => {
      const current = Number(params.current) || 1
      const start = (current - 1) * 20
      return { records: Array.from({ length: Math.max(0, Math.min(20, 40 - start)) }, (_, i) => row(start + i + 1)), total: 40 }
    } }
    const page = open()
    await settle(); await settle()
    assert.deepEqual({ shown: page.data.loadedCount, more: page.data.hasMore, query: requests.filter((r) => /\/fabushi\/posts$/.test(r.url))[0].data },
      { shown: 20, more: true, query: { scopeType: 'zaidu', bookId: 6, current: 1, size: 20 } }, '只取这本书的“再读一本”内容')
    page.onReachBottom(); await settle(); await settle()
    assert.deepEqual({ shown: page.data.loadedCount, more: page.data.hasMore }, { shown: 40, more: false })
    const calls = requests.filter((r) => /\/fabushi\/posts$/.test(r.url)).length
    page.onReachBottom(); await settle()
    assert.equal(requests.filter((r) => /\/fabushi\/posts$/.test(r.url)).length, calls, '到底之后不再请求')

    // 接口给了一页空的(与总数对不上): 也算到底, 不无限触底重试
    fixtures = { book: { id: 6, title: '道德经' }, posts: (params) => (Number(params.current) >= 3 ? { records: [], total: 45 } : { records: Array.from({ length: 20 }, (_, i) => row((Number(params.current) - 1) * 20 + i + 1)), total: 45 }) }
    const second = open()
    await settle(); await settle()
    second.onReachBottom(); await settle(); await settle()
    second.onReachBottom(); await settle(); await settle()
    assert.deepEqual({ shown: second.data.loadedCount, more: second.data.hasMore }, { shown: 40, more: false }, '第 3 页是空的: 到底了')
  })

  await check('在线阅读: 有电子书进阅读器(带书目、文件、书名、版式); 没有电子书只提示、不跳转; 我也传带着书目进发布页; 点行进详情页', async () => {
    fixtures = { book: { id: 6, title: '道德经', pdfFileId: 77, layoutType: 'reflow' }, posts: { records: [row(1)], total: 1 } }
    let page = open()
    await settle()
    page.openReader()
    assert.equal(navigations.at(-1), '/pages/fabushi/reader/index?bookId=6&pdfFileId=77&title=%E9%81%93%E5%BE%B7%E7%BB%8F&layoutType=' + encodeURIComponent('reflow'), '带书名(地址编码)与版式')
    page.goUpload()
    assert.equal(navigations.at(-1), '/pages/fabushi/upload/index?scope=zaidu&bookId=6')
    page.goDetail({ currentTarget: { dataset: { id: 42 } } })
    assert.equal(navigations.at(-1), '/pages/fabushi/detail/index?id=42')

    fixtures = { book: { id: 6, title: '道德经' }, posts: { records: [], total: 0 } }
    page = open()
    await settle(); await settle()
    navigations.length = 0
    page.openReader()
    assert.deepEqual({ nav: navigations.length, toast: toasts.at(-1) }, { nav: 0, toast: '该书未挂电子书' })
    assert.deepEqual({ groups: page.data.groups.length, more: page.data.hasMore }, { groups: 0, more: false }, '这本书还没有内容: 空列表、不再上拉')
  })

  await check('没有书目编号不让进: 提示后返回', async () => {
    const page = open({})
    assert.deepEqual({ toast: toasts[0], book: page.data.book }, { toast: '参数缺失', book: null })
  })

  console.log(`PASS: test-miniprogram-fabushi-bybook (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

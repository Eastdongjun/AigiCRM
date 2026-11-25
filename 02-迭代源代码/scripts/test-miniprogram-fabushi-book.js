#!/usr/bin/env node
// 书目浏览(小程序)·页面逻辑·单元测试(2026-11-25)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”, 书目页(pages/fabushi/book/index.js)按原型 A5 重排:
//   点一行在行内展开版本链(同名版本可切换)、页脚新增“提交书目”表单(此前小程序里没有提交书目的入口)。
//   这一页之前也没有 node 测试。在 node 下驱动真实的页面 JS, wx.* 都是桩, 不联网。
// 抓不到: 画面(由开发者工具里截图与原型并排核对)。
const assert = require('node:assert/strict')

const BASE = 'https://api.test'
const requests = []
const toasts = []
const navigations = []
const titles = []
const DICT = [
  { dictValue: 'team', dictLabel: '团队必读书目' },
  { dictValue: 'guoxue', dictLabel: '国学建议书目' }
]
let fixtures = {}

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
const pick = (name, fallback) => (Object.prototype.hasOwnProperty.call(fixtures, name) ? fixtures[name] : fallback)
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  if (/\/system\/dict\/data\//.test(url)) return opts.success(ok(pick('dict', DICT)))
  if (method === 'POST' && /\/fabushi\/books$/.test(url)) {
    if (fixtures.failSubmit) return opts.fail({ errMsg: 'request:fail timeout' })
    return opts.success(ok({ id: 900, status: 'pending' }))
  }
  if (method === 'POST' && /\/fabushi\/shelf$/.test(url)) return opts.success(ok(true))
  const detail = url.match(/\/fabushi\/books\/(\d+)$/)
  if (detail) {
    const book = (fixtures.books || []).find((b) => b.id === Number(detail[1]))
    return opts.success(ok({ book: book || { id: Number(detail[1]), title: '未知' } }))
  }
  const sections = url.match(/\/fabushi\/books\/(\d+)\/sections$/)
  if (sections) return opts.success(ok({ sections: pick('sections', []) }))
  if (/\/fabushi\/posts$/.test(url)) return opts.success(ok({ records: pick('posts', []), total: (fixtures.posts || []).length }))
  if (/\/fabushi\/books$/.test(url)) {
    const params = opts.data || {}
    const list = (fixtures.books || []).filter((b) => !params.categoryCode || b.categoryCode === params.categoryCode)
      .filter((b) => !params.keyword || String(b.title).indexOf(params.keyword) >= 0)
    const current = Number(params.current) || 1
    const size = Number(params.size) || 10
    return opts.success(ok({ records: list.slice((current - 1) * size, current * size), total: list.length }))
  }
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
  switchTab: (opts) => navigations.push(opts.url),
  stopPullDownRefresh: () => {},
}
const app = { globalData: { demoMode: false, apiBaseUrl: BASE, currentUser: { userId: 1, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/book/index.js')
assert.ok(pageConfig, '页面配置已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 50))   // 展开 = 详情 + 内容 + 章节 + 同名版本, 多步的 Promise 链都要走完
const tapCode = (code) => ({ currentTarget: { dataset: { code } } })
const tapId = (id) => ({ currentTarget: { dataset: { id } } })
const input = (field, value) => ({ currentTarget: { dataset: { field } }, detail: { value } })
const book = (id, extra) => Object.assign({ id, title: '书' + id, categoryCode: 'guoxue', categoryName: '国学建议书目', author: '作者' + id }, extra || {})
const listCalls = () => requests.filter((r) => /\/fabushi\/books$/.test(r.url) && r.method === 'GET')
const shelfPosts = () => requests.filter((r) => /\/fabushi\/shelf$/.test(r.url) && r.method === 'POST')
const bookPosts = () => requests.filter((r) => /\/fabushi\/books$/.test(r.url) && r.method === 'POST')

function open(query) {
  requests.length = 0
  toasts.length = 0
  navigations.length = 0
  titles.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad(query || {})
  return page
}

async function run() {
  let passed = 0
  const check = async (name, fn) => { await fn(); passed++; console.log('  ok  ' + name) }

  await check('列表: 只取已过审的; 每行带封面文字(两个字)、底色、带书名号的标题(不重复套书名号)', async () => {
    fixtures = { books: [book(1, { title: '《道德经》' }), book(2, { title: '活法', pdfFileId: 77 })] }
    const page = open()
    await settle()
    // 请求层会把空字符串去掉: 没选类别、没有搜索词时这两个参数不发
    assert.deepEqual(listCalls()[0].data, { status: 'approved', current: 1, size: 10 })
    assert.deepEqual(page.data.books.map((b) => [b.coverText, b.titleText, b.coverTone]), [['道德', '《道德经》', 'cyan'], ['活法', '《活法》', 'amber']])
    assert.deepEqual(page.data.categories.map((c) => c.dictLabel), ['团队必读书目', '国学建议书目'])
  })

  await check('类别切换: 带类别参数、从第一页取、导航标题跟着类别走; 再点“全部”回到不带类别', async () => {
    fixtures = { books: [book(1), book(2, { categoryCode: 'team', categoryName: '团队必读书目' })] }
    const page = open()
    await settle()
    page.onCategoryTap(tapCode('guoxue'))
    await settle()
    assert.deepEqual({ query: listCalls().at(-1).data.categoryCode, n: page.data.books.length, title: titles.at(-1) },
      { query: 'guoxue', n: 1, title: '书目 · 国学建议书目' })
    page.onCategoryTap(tapCode(''))
    await settle()
    assert.deepEqual({ hasCategory: Object.prototype.hasOwnProperty.call(listCalls().at(-1).data, 'categoryCode'), n: page.data.books.length, title: titles.at(-1) },
      { hasCategory: false, n: 2, title: '书目浏览' }, '“全部”不带类别参数')
  })

  await check('搜索与分页: 搜索词去掉首尾空格、删空了回到不筛词; 触底取下一页, 取够总数就停', async () => {
    fixtures = { books: Array.from({ length: 12 }, (_, i) => book(i + 1, { title: i < 5 ? '论语' + i : '大学' + i })) }
    const page = open()
    await settle()
    assert.equal(page.data.books.length, 10, '第一页 10 条')
    page.onReachBottom(); await settle()
    assert.deepEqual({ n: page.data.books.length, last: listCalls().at(-1).data.current, total: page.data.total }, { n: 12, last: 2, total: 12 })
    const calls = listCalls().length
    page.onReachBottom(); await settle()
    assert.equal(listCalls().length, calls, '取够总数后不再请求')
    page.onKeywordInput({ detail: { value: ' 论语 ' } }); page.onSearch(); await settle()
    assert.deepEqual({ keyword: listCalls().at(-1).data.keyword, n: page.data.books.length }, { keyword: '论语', n: 5 })
    page.onKeywordInput({ detail: { value: '论语' } }); await settle()
    assert.equal(listCalls().length, calls + 1, '搜索词没删空: 不重新取')
    page.onKeywordInput({ detail: { value: '' } }); await settle()
    assert.deepEqual({ hasKeyword: Object.prototype.hasOwnProperty.call(listCalls().at(-1).data, 'keyword'), n: page.data.books.length },
      { hasKeyword: false, n: 10 }, '删空了: 不带搜索词, 回到第一页')
  })

  await check('展开: 点一行展开这一本, 取这个版本的详情 / 内容 / 章节, 再按去掉书名号的书名找到同名的其他版本; 再点一次收起', async () => {
    fixtures = {
      books: [book(6, { title: '《道德经》', pdfFileId: 77 }), book(7, { title: '道德经', author: '陈鼓应', editionType: 'annotated' }), book(8, { title: '道德经注' }), book(9, { title: '论语' })],
      sections: [{ id: 1, title: '上篇', levelCode: 'part', children: [{ id: 2, title: '第一章', levelCode: 'chapter', children: [] }] }],
      posts: [{ id: 501, title: '上善若水', content: '正文', createdAt: '2025-01-02T07:50:00', userName: '王晓兰', isNew: true, likeCount: 3 }]
    }
    const page = open()
    await settle()
    page.openBook(tapId(6))
    await settle()
    const byKeyword = listCalls().filter((r) => r.data.keyword === '道德经')
    assert.equal(byKeyword.length >= 1, true, '按书名再取一次, 找其他版本')
    assert.deepEqual({ open: page.data.expandedId, selected: page.data.selectedId, versions: page.data.versions.map((v) => [v.id, v.label]), text: page.data.detailVersionText },
      { open: 6, selected: 6, versions: [[6, '作者6'], [7, '陈鼓应 · 注释本']], text: '作者6' }, '“道德经注”书名不同, 不算另一个版本')
    assert.deepEqual({ posts: page.data.detailPosts.map((p) => [p.id, p.timeText, p.isNew]), sections: page.data.sections.map((s) => [s.id, s.depth]) },
      { posts: [[501, '2025-01-02', true]], sections: [[1, 0], [2, 1]] }, '本书目内容带时间; 章节结构拍平并带层级')
    page.openBook(tapId(6))
    assert.equal(page.data.expandedId, null, '再点一次收起')
  })

  await check('切版本: 点另一个版本, 面板换成那个版本的详情与内容; 旧版本的应答晚到不会盖掉新的', async () => {
    fixtures = {
      books: [book(6, { title: '道德经', pdfFileId: 77, layoutType: 'reflow' }), book(7, { title: '《道德经》', author: '王弼' })],
      posts: []
    }
    const page = open()
    await settle()
    page.openBook(tapId(6))
    await settle()
    assert.deepEqual(page.data.versions.map((v) => v.id), [6, 7], '两个版本(一个带书名号一个不带)')
    page.pickVersion(tapId(7))
    await settle()
    assert.deepEqual({ selected: page.data.selectedId, book: page.data.detailBook.id, text: page.data.detailVersionText, pdf: page.data.detailBook.pdfFileId },
      { selected: 7, book: 7, text: '王弼', pdf: undefined })
    const detailCalls = () => requests.filter((r) => /\/fabushi\/books\/\d+$/.test(r.url)).length
    const before = detailCalls()
    page.pickVersion(tapId(7))
    await settle()
    assert.deepEqual({ book: page.data.detailBook.id, calls: detailCalls() - before }, { book: 7, calls: 0 }, '点的就是当前版本: 不重复取')
  })

  await check('面板里的操作: 在线阅读(带书名编码与版式)、去发布、按书浏览、加书架、点内容进详情; 没有发布权限时“去发布”只提示', async () => {
    fixtures = { books: [book(6, { title: '道德经 陈鼓应注', pdfFileId: 77, layoutType: 'reflow' })], posts: [{ id: 501, title: '序', content: '', createdAt: '2025-01-02T07:50:00' }] }
    const page = open()
    await settle()
    page.openBook(tapId(6)); await settle()
    navigations.length = 0
    page.openReader()
    assert.equal(navigations.at(-1), '/pages/fabushi/reader/index?bookId=6&pdfFileId=77&title=' + encodeURIComponent('道德经 陈鼓应注') + '&layoutType=reflow')
    page.goUploadForBook()
    assert.equal(navigations.at(-1), '/pages/fabushi/upload/index?bookId=6')
    page.goByBook()
    assert.equal(navigations.at(-1), '/pages/fabushi/bybook/index?bookId=6')
    page.openPost(tapId(501))
    assert.equal(navigations.at(-1), '/pages/fabushi/detail/index?id=501')
    page.addShelf()
    await settle()
    assert.deepEqual(shelfPosts().map((r) => r.data), [{ bookId: 6 }], '加书架: 带书目编号')
    assert.equal(toasts.at(-1), '已加入书架')
    page.setData({ canCreate: false })
    navigations.length = 0
    page.goUploadForBook()
    assert.deepEqual({ nav: navigations.length, toast: toasts.at(-1) }, { nav: 0, toast: '没有发布权限' })
  })

  await check('提交书目: 没有发布权限打不开; 类别、书名必填; 页数必须是正整数', async () => {
    fixtures = { books: [] }
    const page = open()
    await settle()
    // 换成一个没有发布权限的普通用户(admin 恒有权限): 打不开表单
    const admin = app.globalData.currentUser
    app.globalData.currentUser = { userId: 2, username: 'staff', roles: [] }
    page.openSubmit()
    assert.deepEqual({ open: page.data.submitOpen, toast: toasts.at(-1) }, { open: false, toast: '没有发布权限' })
    app.globalData.currentUser = admin

    page.openSubmit()
    assert.equal(page.data.submitOpen, true)
    page.submitBook()
    assert.equal(toasts.at(-1), '请选择类别')
    page.onCategoryPick({ detail: { value: '1' } })
    page.submitBook()
    assert.equal(toasts.at(-1), '请填写书名')
    assert.equal(bookPosts().length, 0, '没填完不发请求')
    page.onFormInput(input('title', ' 传习录 '))
    page.onFormInput(input('pages', '十二'))
    page.submitBook()
    assert.equal(toasts.at(-1), '页数要填正整数')
    assert.equal(bookPosts().length, 0)
  })

  await check('提交书目: 载荷 = 类别码、书名(去空格)、作者、出版社、版本类型、页数; 页数留空不带页数; 成功后收起、提示、重新取列表; 连点不重复提交', async () => {
    fixtures = { books: [] }
    const page = open()
    await settle()
    page.openSubmit()
    page.onCategoryPick({ detail: { value: '1' } })
    page.onFormInput(input('title', ' 传习录 '))
    page.onFormInput(input('author', ' 王阳明 '))
    page.onFormInput(input('publisher', ' 中华书局 '))
    page.onFormInput(input('pages', ' 350 '))
    const listsBefore = listCalls().length
    page.submitBook()
    page.submitBook()
    await settle()
    assert.deepEqual(bookPosts().map((r) => r.data), [{ categoryCode: 'guoxue', title: '传习录', author: '王阳明', publisher: '中华书局', editionType: 'original', pages: 350 }])
    assert.deepEqual({ open: page.data.submitOpen, submitting: page.data.submitting, toast: toasts.at(-1), reloaded: listCalls().length - listsBefore },
      { open: false, submitting: false, toast: '已提交，待管理员审核', reloaded: 1 })

    page.openSubmit()
    assert.deepEqual(page.data.form, { categoryIndex: -1, title: '', author: '', publisher: '', editionIndex: 0, pages: '' }, '再次打开是空表单')
    page.onCategoryPick({ detail: { value: '0' } })
    page.onFormInput(input('title', '活法'))
    page.onEditionPick({ detail: { value: '3' } })
    page.submitBook()
    await settle()
    assert.deepEqual(bookPosts().at(-1).data, { categoryCode: 'team', title: '活法', editionType: 'plain' }, '可选项留空时不发(请求层去掉空值); 版本类型按选的第 4 项')
  })

  await check('提交书目: 接口报错时提示、表单还开着、可以再提交(再提交成功)', async () => {
    fixtures = { books: [], failSubmit: true }
    const page = open()
    await settle()
    page.openSubmit()
    page.onCategoryPick({ detail: { value: '1' } })
    page.onFormInput(input('title', '传习录'))
    const toastsBefore = toasts.length
    page.submitBook()
    await settle()
    assert.deepEqual({ open: page.data.submitOpen, submitting: page.data.submitting, toasted: toasts.length - toastsBefore, posts: bookPosts().length },
      { open: true, submitting: false, toasted: 1, posts: 1 }, '提示了一次; 表单没关、不再卡在“提交中”')
    fixtures.failSubmit = false
    page.submitBook()
    await settle()
    assert.deepEqual({ open: page.data.submitOpen, toast: toasts.at(-1), posts: bookPosts().length }, { open: false, toast: '已提交，待管理员审核', posts: 2 })
  })

  await check('带着书目编号进来(书架 / 书库点进来): 第一页里有这本书就直接展开它', async () => {
    fixtures = { books: [book(3), book(4)] }
    const page = open({ id: '4' })
    await settle()
    assert.deepEqual({ open: page.data.expandedId, book: page.data.detailBook.id }, { open: 4, book: 4 })
  })

  console.log(`PASS: test-miniprogram-fabushi-book (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

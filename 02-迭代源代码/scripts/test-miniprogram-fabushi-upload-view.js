#!/usr/bin/env node
// 发布页(小程序)·导航标题 / 九宫格预览地址 / 成功页文字·单元测试(2026-11-25, 先红后绿)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”, 发布页(pages/fabushi/upload/index.js)按原型 B2–B5 重排。
//   重排时发现一个老缺陷: 九宫格预览的地址里, 下载令牌的位置放的是整个应答对象(拼出来是 object Object), 选完图预览一直是裂图、放大也看不了。
//   录音、图片编辑、H5 回传另有测试(test-miniprogram-upload-handback.js / test-miniprogram-image-editor.js), 这里只管这三样。
const assert = require('node:assert/strict')

const BASE = 'https://api.test'
// 测试用的假下载票据(片段拼接: 不是任何真实票据)
const FAKE_DOWNLOAD_TOKEN = ['dl-token', 'for-test'].join('-')
const requests = []
const toasts = []
const titles = []
let fixtures = {}

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  if (/\/download-token/.test(url)) {
    if (fixtures.tokenFail) return opts.fail({ errMsg: 'request:fail' })
    return opts.success(ok(fixtures.tokenShape === 'string' ? FAKE_DOWNLOAD_TOKEN : { token: FAKE_DOWNLOAD_TOKEN }))
  }
  if (/\/fabushi\/books\/\d+\/sections$/.test(url)) return opts.success(ok({ sections: fixtures.sections || [] }))
  if (method === 'POST' && /\/fabushi\/posts$/.test(url)) return opts.success(ok({ id: 7701 }))
  if (method === 'POST' && /\/attachments$/.test(url)) return opts.success(ok(true))
  if (/\/fabushi\/books$/.test(url)) return opts.success(ok({ records: fixtures.books || [], total: (fixtures.books || []).length }))
  return opts.success(ok(null))
}

global.wx = {
  request: (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); route(opts) },
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: () => {},
  setNavigationBarTitle: (opts) => titles.push(opts && opts.title),
  navigateTo: () => {},
  navigateBack: () => {},
  redirectTo: () => {},
  switchTab: () => {},
  vibrateShort: () => {},
  createInnerAudioContext: () => ({ stop() {}, play() {} }),
}
const app = { globalData: { demoMode: false, apiBaseUrl: BASE, currentUser: { id: 7, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/upload/index.js')
assert.ok(pageConfig, '页面配置已捕获')

const settle = () => new Promise((resolve) => setTimeout(resolve, 40))
const pick = (value) => ({ detail: { value: String(value) } })
const tapValue = (value) => ({ currentTarget: { dataset: { value } } })
const BOOKS = [
  { id: 1, title: '论语', categoryCode: 'guoxue', categoryName: '国学建议书目', author: '杨伯峻', editionType: 'translation' },
  { id: 2, title: '《道德经》', categoryCode: 'guoxue', categoryName: '国学建议书目', author: '陈鼓应', editionType: 'annotated' }
]

function open(query) {
  requests.length = 0
  toasts.length = 0
  titles.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad(query || {})
  return page
}

// 选到一本书: 类别 0 → 书目 titles 里的第 titlePos 本
async function chooseBook(page, title, extra) {
  page.onCategoryChange(pick(0))
  page.onTitleChange(pick(page.data.titles.indexOf(title)))
  await settle()
  if (extra) page.onEditionChange(pick(extra))
  await settle()
}

async function run() {
  let passed = 0
  const check = async (name, fn) => { await fn(); passed++; console.log('  ok  ' + name) }

  await check('导航标题随类型走: 进页面“发布 · 每日诵读”; 带类型进来、切换类型都会改', async () => {
    fixtures = { books: BOOKS }
    let page = open()
    await settle()
    assert.deepEqual(titles, ['发布 · 每日诵读'])
    page.onScope(tapValue('suishi'))
    assert.equal(titles.at(-1), '发布 · 每日随施')
    page.onScope(tapValue('zaidu'))
    assert.equal(titles.at(-1), '发布 · 再读一本')
    page = open({ scope: 'suishi' })
    await settle()
    assert.deepEqual({ scope: page.data.scopeType, title: titles.at(-1) }, { scope: 'suishi', title: '发布 · 每日随施' }, '从觉醒主卡“去随施”进来')
    page = open({ scope: 'nope' })
    await settle()
    assert.equal(page.data.scopeType, 'songdu', '认不出的类型回到每日诵读')
  })

  await check('九宫格预览地址 = 下载地址 + 下载令牌(不是整个应答对象); 令牌接口给字符串也认; 接口失败时地址不带令牌但不报错; 没有图片不取令牌', async () => {
    fixtures = { books: BOOKS }
    let page = open({ scope: 'suishi' })
    await settle()
    page.setData({ attachments: [{ fileId: 6001, fileType: 'image', name: 'a.jpg' }, { fileId: 6002, fileType: 'audio', name: 'b.mp3' }, { fileId: 6003, fileType: 'image', name: 'c.jpg' }] })
    page.syncImageAttachments()
    await settle()
    assert.deepEqual(page.data.imageAttachments.map((x) => x.preview),
      ['https://api.test/api/file/download/6001?token=' + FAKE_DOWNLOAD_TOKEN, 'https://api.test/api/file/download/6003?token=' + FAKE_DOWNLOAD_TOKEN],
      '只有图片进九宫格; 地址带的是令牌本身')
    assert.ok(!page.data.imageAttachments.some((x) => x.preview.includes('object')), '地址里没有 object Object')

    fixtures.tokenShape = 'string'
    page.syncImageAttachments()
    await settle()
    assert.equal(page.data.imageAttachments[0].preview, 'https://api.test/api/file/download/6001?token=' + FAKE_DOWNLOAD_TOKEN, '令牌接口直接给字符串')

    fixtures = { books: BOOKS, tokenFail: true }
    page = open({ scope: 'suishi' })
    await settle()
    page.setData({ attachments: [{ fileId: 6001, fileType: 'image', name: 'a.jpg' }] })
    page.syncImageAttachments()
    await settle()
    assert.deepEqual({ previews: page.data.imageAttachments, toasts: toasts.length }, { previews: [], toasts: 0 }, '取不到令牌: 不出裂图地址(列表留空), 不弹错')

    requests.length = 0
    page.setData({ attachments: [{ fileId: 6002, fileType: 'audio', name: 'b.mp3' }] })
    page.syncImageAttachments()
    await settle()
    assert.deepEqual({ previews: page.data.imageAttachments, tokenCalls: requests.filter((r) => /\/download-token/.test(r.url)).length }, { previews: [], tokenCalls: 0 }, '没有图片附件: 不取令牌')
  })

  await check('成功页的文字(每日诵读): “每日诵读已发布”, 说明里写书名、语音段数、图片张数', async () => {
    fixtures = { books: BOOKS }
    const page = open()
    await settle()
    await chooseBook(page, '论语')
    page.setData({ attachments: [{ fileId: 6001, fileType: 'audio', name: 'a.mp3' }, { fileId: 6002, fileType: 'audio', name: 'b.mp3' }, { fileId: 6003, fileType: 'image', name: 'c.jpg' }] })
    page.onContent({ detail: { value: '学而时习之' } })
    page.submit()
    await settle(); await settle()
    assert.deepEqual({ success: page.data.success, title: page.data.successTitle, summary: page.data.successSummary },
      { success: true, title: '每日诵读已发布', summary: '《论语》，语音 2 段，图片 1 张，已进认知广场，等书友来复诵你的声音吧。' })
  })

  await check('成功页的文字(再读一本 · 自录章节 / 每日随施 · 无附件)', async () => {
    fixtures = { books: BOOKS }
    let page = open({ scope: 'zaidu' })
    await settle()
    await chooseBook(page, '《道德经》')
    page.toggleManualSection()
    page.onManualSection({ detail: { value: '第八章 · 上善若水' } })
    page.onContent({ detail: { value: '抄了三遍' } })
    page.submit()
    await settle(); await settle()
    assert.deepEqual({ success: page.data.success, title: page.data.successTitle, summary: page.data.successSummary },
      { success: true, title: '再读一本已发布', summary: '《道德经》第八章 · 上善若水，已进认知广场，等大家来点木鱼吧。' }, '书名去掉已有的书名号, 不重复套')

    // 随施不关联书目: 即使之前选过一本书(数据里还留着), 成功页的说明里也不写书名
    page = open({ scope: 'suishi' })
    await settle()
    page.onCategoryChange(pick(0))
    page.onTitleChange(pick(0))
    await settle()
    assert.equal(page.data.titles[page.data.titleIndex], '论语', '前提: 数据里确实留着一本选过的书')
    page.onContent({ detail: { value: '下班路上的晚霞' } })
    page.submit()
    await settle(); await settle()
    assert.deepEqual({ title: page.data.successTitle, summary: page.data.successSummary },
      { title: '每日随施已发布', summary: '已进认知广场，等大家来点木鱼吧。' }, '随施不关联书目; 没有附件就不写附件')
  })

  await check('成功页的文字(每日诵读 B 通道): 说明里带选的段落', async () => {
    fixtures = { books: BOOKS, sections: [{ id: 11, title: '上篇', children: [{ id: 12, title: '第一章', children: [] }] }] }
    const page = open()
    await settle()
    page.onChannel(tapValue('B'))
    await chooseBook(page, '论语')
    assert.deepEqual(page.data.sectionNames, ['上篇', '上篇 / 第一章'], '章节拍平成路径')
    page.onSectionChange(pick(1))
    page.onContent({ detail: { value: '学而时习之，不亦说乎' } })
    page.setData({ attachments: [{ fileId: 6001, fileType: 'audio', name: 'a.mp3' }] })
    page.submit()
    await settle(); await settle()
    assert.equal(page.data.successSummary, '《论语》上篇 / 第一章，语音 1 段，已进认知广场，等书友来复诵你的声音吧。')
  })

  // 【2026-11-26 按小程序原型 v7】原型 B4 的选书是一行合并的选择器(“国学建议 ·《道德经》· 陈鼓应注” + “换版本 ›”),
  // 重做外观时这一处留了三行下拉, 用户说“还有小程序的”之后补上: 一个三列选择器(类别 / 书目 / 版本) + 右侧“换版本”。
  await check('选书是一行合并的选择器: 三列是类别 / 书目 / 版本; 滚动前两列时后面的列跟着换; 确认后选中的是那一版', async () => {
    fixtures = { books: BOOKS.concat([
      { id: 3, title: '论语', categoryCode: 'guoxue', categoryName: '国学建议书目', author: '杨伯峻', editionType: 'paper' },
      { id: 4, title: '活法', categoryCode: 'manage', categoryName: '管理类', author: '稻盛和夫' }
    ]) }
    const page = open({ scope: 'zaidu' })
    await settle()
    assert.deepEqual(page.data.bookPicker.map((col) => col.slice()), [
      ['国学建议书目', '管理类'], ['论语', '《道德经》'], ['translation', 'paper']
    ], '三列: 类别 / 第一个类别下的书目 / 第一本书的各个版本')
    assert.deepEqual(page.data.bookSummary, '', '还没选: 摘要是空的')
    assert.equal(page.currentBookId(), null)

    // 滚第一列到“管理类”: 第二列换成该类别下的书目, 第三列换成那本书的版本
    page.onBookColumnChange({ detail: { column: 0, value: 1 } })
    assert.deepEqual(page.data.bookPicker.map((col) => col.slice()), [['国学建议书目', '管理类'], ['活法'], ['默认版本']])
    // 滚第二列: 第三列换成那本书的版本
    page.onBookColumnChange({ detail: { column: 0, value: 0 } })
    page.onBookColumnChange({ detail: { column: 1, value: 1 } })
    assert.deepEqual(page.data.bookPicker[2].slice(), ['annotated'], '《道德经》只有一个版本')
    assert.deepEqual({ c: page.data.categoryIndex, t: page.data.titleIndex }, { c: -1, t: -1 }, '只滚动、没确认: 页面的选择不变')

    // 确认: 国学建议书目 → 论语 → 第 2 个版本
    requests.length = 0
    page.onBookPicked({ detail: { value: [0, 0, 1] } })
    await settle()
    assert.deepEqual(
      { c: page.data.categoryIndex, t: page.data.titleIndex, e: page.data.editionIndex, id: page.currentBookId() },
      { c: 0, t: 0, e: 1, id: 3 })
    assert.equal(page.data.bookSummary, '国学建议书目 ·《论语》· paper')
    assert.deepEqual(page.data.bookPickerIndex.slice(), [0, 0, 1])
    assert.ok(requests.some((r) => /\/fabushi\/books\/3\/sections$/.test(r.url)), '章节按选中的那一版取')

    // 右侧“换版本”: 回到同一本书的第 1 个版本, 三列选择器也跟着对齐
    page.onEditionChange(pick(0))
    await settle()
    assert.deepEqual({ id: page.currentBookId(), summary: page.data.bookSummary }, { id: 1, summary: '国学建议书目 ·《论语》· translation' })
    assert.deepEqual(page.data.bookPickerIndex.slice(), [0, 0, 0])

    // 确认时给的值不完整 / 不是数字: 按第 0 个处理, 不抛错
    page.onBookPicked({ detail: { value: ['x'] } })
    await settle()
    assert.deepEqual({ c: page.data.categoryIndex, t: page.data.titleIndex, e: page.data.editionIndex }, { c: 0, t: 0, e: 0 })
  })

  console.log(`PASS: test-miniprogram-fabushi-upload-view (${passed} 条)`)
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})

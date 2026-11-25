#!/usr/bin/env node
// 法布施详情页(小程序)·评论分页与复诵状态·单元测试(2026-11-24 GL-14, 先红后绿 + 变异自证)
// 在 node 下驱动真实的 pages/fabushi/detail/index.js(参照 test-miniprogram-customer-filter.js 的 Page 捕获模式),
// wx.request / wx.uploadFile / 录音管理器都是桩。
//
// 【已确认缝隙(缝由来: 已批准的《GO-LIVE-PLAN-20261124》GL-14 “小程序评论加载更多”与语音兜底)】
//   页面视图模型 —— 抓评论分页的推进与复位、复诵模式的状态流转;
//   抓不到: 触底事件是否真的接到 loadMoreComments(WXML 绑定, 由模板绑定契约与开发者工具走查负责)、真机录音。
const assert = require('node:assert/strict')

let post = { id: 501, userId: 7, title: '今日诵读', content: '内容' }   // 详情接口返回的作品(每个场景可以换)
// 测试用的假下载票据(片段拼接: 不是任何真实票据, 也不让凭据扫描把它当成字面量)
const FAKE_DOWNLOAD_TOKEN = ['dl-token', 'for-test'].join('-')
const requests = []      // wx.request 的调用: { url, method, data }
const uploads = []       // wx.uploadFile 的调用: { filePath, formData }
const toasts = []
const modals = []
const titles = []        // wx.setNavigationBarTitle 设过的标题
const pageScrolls = []   // wx.pageScrollTo 被调用的次数(应当一直是 0)
const previews = []      // wx.previewImage 放大的图片
let commentFeed = []     // 接口返回的评论列表(每个场景自己设)
let recitationFeed = []  // 接口返回的复诵列表(每个场景自己设)
let attachments = []     // 详情接口返回的附件(每个场景自己设)
let recorder = null
let uploadSeq = 0        // 上传序号(每个场景从 0 开始): 第 n 次上传得到的文件 id = 77000 + n

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  if (/\/fabushi\/posts\/501$/.test(url)) return opts.success(ok({ post, attachments, likeCount: 0 }))
  if (/\/fabushi\/posts\/501\/comments$/.test(url)) {
    // 服务端收到评论后, 新评论排在列表最前(下次拉取就能看到)
    if (method === 'POST') commentFeed = [comment(99, { content: (opts.data && opts.data.content) || '语音评论', type: (opts.data && opts.data.type) || 'text' })].concat(commentFeed)
    return opts.success(ok(commentFeed))
  }
  if (/\/fabushi\/posts\/501\/recitations$/.test(url)) return opts.success(ok(method === 'POST' ? true : recitationFeed))
  if (/download-token/.test(url)) return opts.success(ok({ token: FAKE_DOWNLOAD_TOKEN }))
  return opts.success(ok(true))
}
function makeRecorder() {
  const handlers = {}
  const calls = { start: [], stop: 0 }
  recorder = {
    handlers,
    calls,
    onStart(fn) { handlers.start = fn },
    onStop(fn) { handlers.stop = fn },
    onError(fn) { handlers.error = fn },
    start(options) { calls.start.push(options) },
    stop() { calls.stop += 1 },
  }
  return recorder
}

global.wx = {
  request: (opts) => { requests.push({ url: opts.url, method: opts.method || 'GET', data: opts.data }); route(opts) },
  uploadFile: (opts) => {
    uploads.push({ filePath: opts.filePath, formData: opts.formData })
    opts.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { id: 77000 + (++uploadSeq) } }) })
    return {}
  },
  getRecorderManager: () => recorder,
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: (opts) => toasts.push(opts && opts.title),
  showModal: (opts) => modals.push(opts && opts.title),
  setNavigationBarTitle: (opts) => titles.push(opts && opts.title),
  nextTick: (fn) => fn(),
  pageScrollTo: () => pageScrolls.push(1),   // 页面脚本不应该调用它(导航契约禁用): 调用了这里会记下来
  previewImage: (opts) => previews.push(opts),
  navigateBack: () => {},
  navigateTo: () => {},
  redirectTo: () => {},
  switchTab: () => {},
}
// 接口域名给一个非空的值: 可播地址必须是完整的网络地址(小程序里相对路径放不出声音), 空域名下看不出差别
const app = { globalData: { demoMode: false, apiBaseUrl: 'https://api.test', currentUser: { userId: 7, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/detail/index.js')
assert.ok(pageConfig, '详情页配置已捕获')

const drain = () => new Promise((resolve) => setImmediate(resolve))
const settle = () => new Promise((resolve) => setTimeout(resolve, 40))   // 多步的 Promise 链(取令牌 → 取复诵列表 → 整理)都走完
const comment = (n, extra) => Object.assign({ id: n, type: 'text', content: `评论 ${n}`, userName: '同学' }, extra || {})
const commentPosts = () => requests.filter((r) => /\/comments$/.test(r.url) && r.method === 'POST')
const recitationPosts = () => requests.filter((r) => /\/recitations$/.test(r.url) && r.method === 'POST')

async function openPage(comments, options, seed) {
  commentFeed = comments
  recitationFeed = (seed && seed.recitationFeed) || []
  attachments = (seed && seed.attachments) || []
  post = (seed && seed.post) || { id: 501, userId: 7, title: '今日诵读', content: '内容' }
  makeRecorder()
  uploadSeq = 0
  requests.length = 0
  uploads.length = 0
  toasts.length = 0
  modals.length = 0
  titles.length = 0
  pageScrolls.length = 0
  previews.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch, done) { Object.assign(this.data, patch); if (typeof done === 'function') done() }
  page.onLoad(Object.assign({ id: '501' }, options || {}))
  await drain()
  return page
}

async function run() {
  // ---- 1. 首屏 10 条; 逐页加载到尾; 到尾后再点不变 ----
  let page = await openPage(Array.from({ length: 25 }, (_, i) => comment(i + 1)))
  assert.deepStrictEqual(
    { shown: page.data.comments.length, total: page.data.commentsTotal, more: page.data.hasMoreComments },
    { shown: 10, total: 25, more: true },
    '首屏 10 条, 共 25 条, 还有更多')
  page.loadMoreComments()
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 20, more: true }, '第二次: 20 条')
  page.loadMoreComments()
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 25, more: false }, '第三次: 25 条, 没有更多了')
  page.loadMoreComments()
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 25, more: false }, '到尾后再点: 不变')
  assert.deepStrictEqual(page.data.comments.map((c) => c.id), Array.from({ length: 25 }, (_, i) => i + 1), '顺序与接口一致, 没有重复')

  // ---- 2. 不足一页: 一开始就没有“更多” ----
  page = await openPage(Array.from({ length: 4 }, (_, i) => comment(i + 1)))
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 4, more: false }, '只有 4 条: 全部显示')
  page = await openPage([])
  assert.deepStrictEqual({ shown: page.data.comments.length, total: page.data.commentsTotal, more: page.data.hasMoreComments }, { shown: 0, total: 0, more: false }, '没有评论')

  // ---- 3. 发完评论重新拉取: 回到第一页, 新评论在列表里; 之后的“加载更多”从第二页接着走 ----
  page = await openPage(Array.from({ length: 35 }, (_, i) => comment(i + 1)))
  page.loadMoreComments()
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 20, more: true }, '先加载到 20 条')
  page.setData({ commentText: '新写的一条' })
  const before = commentPosts().length
  page.submitComment()
  await drain()
  assert.strictEqual(commentPosts().length, before + 1, '发出一条评论')
  assert.deepStrictEqual(commentPosts().at(-1).data, { content: '新写的一条', type: 'text' }, '文字评论的载荷')
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 10, more: true }, '重新拉取后回到第一页 10 条')
  assert.strictEqual(page.data.comments[0].content, '新写的一条', '新评论排在最前')
  assert.strictEqual(page.data.commentText, '', '输入框已清空')
  page.loadMoreComments()
  assert.deepStrictEqual({ shown: page.data.comments.length, more: page.data.hasMoreComments }, { shown: 20, more: true }, '加载更多: 第二页 20 条(不是跳到第三页)')

  // ---- 4. 音频评论补可播地址(带下载票据); 文字评论不补 ----
  page = await openPage([comment(1), comment(2, { type: 'audio', audioFileId: 8801 })])
  assert.ok(page.data.comments[1].audioUrl.includes('/api/file/download/8801'), '音频评论有可播地址')
  assert.ok(page.data.comments[1].audioUrl.includes('token=' + FAKE_DOWNLOAD_TOKEN), '可播地址带下载票据')
  assert.strictEqual(page.data.comments[0].audioUrl, undefined, '文字评论不补')

  // ---- 5. 复诵成功: mp3 / 60 秒上限; 停录后上传并提交到复诵接口; 状态复位 ----
  page = await openPage([])
  assert.strictEqual(page.data.canCreate, true, '管理员可以互动')
  page.toggleRecite()
  assert.deepStrictEqual({ mode: page.data.reciteMode, flag: page._reciteMode }, { mode: true, flag: true }, '进入复诵模式')
  assert.strictEqual(recorder.calls.start.length, 1, '开始录音')
  assert.strictEqual(recorder.calls.start[0].format, 'mp3', '小程序端录 mp3')
  assert.strictEqual(recorder.calls.start[0].duration, 60000, '单段上限 60 秒')
  recorder.handlers.stop({ tempFilePath: 'wxfile://tmp/recite.mp3', duration: 5200 })
  await drain()
  assert.deepStrictEqual(uploads.map((u) => u.filePath), ['wxfile://tmp/recite.mp3'], '录完即上传')
  assert.deepStrictEqual(recitationPosts().map((r) => r.data), [{ audioFileId: 77001, durationSeconds: 5 }], '提交到复诵接口, 时长取整到秒')
  assert.deepStrictEqual({ mode: page.data.reciteMode, flag: page._reciteMode }, { mode: false, flag: false }, '提交后退出复诵模式')
  assert.ok(toasts.includes('复诵已挂到本内容下'), '提示成功')

  // ---- 6. 复诵不足 1 秒: 丢弃, 不上传 ----
  page = await openPage([])
  page.toggleRecite()
  recorder.handlers.stop({ tempFilePath: 'wxfile://tmp/short.mp3', duration: 400 })
  await drain()
  assert.strictEqual(uploads.length, 0, '不上传')
  assert.ok(toasts.includes('时长太短，已丢弃'), '提示已丢弃')
  assert.strictEqual(page.data.reciteMode, false, '退出复诵模式')

  // ---- 7. 复诵录音出错(没有麦克风权限): 状态复位; 之后录的语音评论仍按评论提交, 不会被当成复诵 ----
  page = await openPage([])
  page.toggleRecite()
  recorder.handlers.error({ errMsg: 'operateRecorder:fail auth deny' })
  assert.deepStrictEqual({ mode: page.data.reciteMode, flag: page._reciteMode, voice: page.data.voiceMode }, { mode: false, flag: false, voice: false }, '出错后三个状态都复位')
  assert.deepStrictEqual(modals, ['需要麦克风权限'], '引导去设置里开权限')

  page.enterVoice()
  assert.strictEqual(page.data.voiceMode, true, '接着录语音评论')
  recorder.handlers.stop({ tempFilePath: 'wxfile://tmp/voice-comment.mp3', duration: 3000 })
  await drain()
  assert.strictEqual(recitationPosts().length, 0, '没有提交到复诵接口')
  assert.deepStrictEqual(commentPosts().map((r) => r.data), [{ type: 'audio', audioFileId: 77001 }], '按语音评论提交')
  assert.ok(toasts.includes('已评论'), '提示已评论')

  // ---- 8. 【2026-11-25 修正】复诵列表的可播地址: 下载地址 + 下载令牌 ----
  //      原来是相对路径 "/api/file/download/…", 令牌的位置放的是整个应答对象(拼出来是 [object Object]) —— 一直播不出来。
  page = await openPage([], null, { recitationFeed: [
    { id: 1, userName: '李长青', audioFileId: 8802, durationSeconds: 58 },
    { id: 2, userName: '陈慧', audioFileId: 8803, durationSeconds: 62 }
  ] })
  await settle()
  assert.strictEqual(page.data.recitationCount, 2, '复诵条数')
  assert.deepStrictEqual(page.data.recitations.map((r) => r.url), [
    'https://api.test/api/file/download/8802?token=' + FAKE_DOWNLOAD_TOKEN,
    'https://api.test/api/file/download/8803?token=' + FAKE_DOWNLOAD_TOKEN
  ], '每条复诵都是可播地址(完整的下载地址 + 下载令牌)')
  assert.strictEqual(page.data.reciteText, '李长青 58s · 陈慧 62s —— 他人可直接诵读本段', '复诵条的小字: 前两位是谁、各多长')

  // ---- 9. 落点(认知广场的 ⋯ 菜单跳过来时可以指定): 滚到评论输入框 / 复诵条; 没指定就不滚 ----
  //      滚动靠页面里滚动容器的 scroll-into-view(页面数据里的 intoView), 不用 wx.pageScrollTo(导航契约禁用)。
  page = await openPage([], { focus: 'comment' })
  await settle()
  assert.strictEqual(page.data.intoView, 'comment-box', 'focus=comment: 滚到评论输入框')
  page = await openPage([], { focus: 'recite' })
  await settle()
  assert.strictEqual(page.data.intoView, 'recite-strip', 'focus=recite: 滚到复诵条')
  page = await openPage([])
  await settle()
  assert.strictEqual(page.data.intoView, '', '没指定落点: 不滚')
  page.focusComments()
  assert.strictEqual(page.data.intoView, 'comment-box', '点“评论”按钮: 滚到评论输入框')
  page.focusComments()
  assert.strictEqual(page.data.intoView, 'comment-box', '再点一次: 仍然滚到评论输入框(先清空再设)')
  assert.strictEqual(pageScrolls.length, 0, '页面脚本没有调用 wx.pageScrollTo(导航契约禁用它)')

  // ---- 10. 书页点击放大查看(原型 A4): 只把图片附件放进九宫格; 点第几张就从第几张开始看 ----
  page = await openPage([], null, { attachments: [
    { id: 1, fileId: 6001, fileType: 'image' },
    { id: 2, fileId: 6002, fileType: 'audio' },
    { id: 3, fileId: 6003, fileType: 'image' }
  ] })
  await settle()
  assert.deepStrictEqual(page.data.imageUrls.map((u) => u.split('?')[0]), ['https://api.test/api/file/download/6001', 'https://api.test/api/file/download/6003'], '九宫格里只有图片')
  assert.ok(page.data.imageUrls[0].endsWith('?token=' + FAKE_DOWNLOAD_TOKEN), '地址带下载令牌')
  page.previewImage({ currentTarget: { dataset: { index: 1 } } })
  assert.deepStrictEqual(previews.map((p) => p.current), [page.data.imageUrls[1]], '点第二张: 从第二张开始看')
  assert.deepStrictEqual(previews[0].urls, page.data.imageUrls, '左右翻的是全部书页')

  // ---- 11. 评论行: 头像首字与类型名(重点描述 / 语音诵读); 文字评论不带类型 ----
  page = await openPage([
    comment(1, { userName: '陈慧', userId: 3, type: 'highlight' }),
    comment(2, { userName: '李长青', userId: 4, type: 'audio', audioFileId: 8801 }),
    comment(3, { userName: '王晓兰', userId: 5 })
  ])
  await settle()
  assert.deepStrictEqual(
    page.data.comments.map((c) => [c.avatarText, c.typeLabel, c.typeClass]),
    [['陈', '重点描述', 'cyan'], ['李', '语音诵读', 'songdu'], ['王', '', '']], '头像首字与类型名')

  // ---- 12. 标题与来源条按类型: 再读一本 → “再读作品”; 没有类型 → “作品详情” ----
  //      (时间用往年的日期: 不管哪天跑, 结果都一样)
  page = await openPage([])
  assert.deepStrictEqual({ titles, label: page.data.typeLabel }, { titles: ['作品详情'], label: '内容' }, '没有类型: 通用标题')
  page = await openPage([], null, { post: { id: 501, userId: 7, scopeType: 'zaidu', title: '上善若水', content: '抄了三遍', bookTitle: '道德经', sectionTitle: '第八章', createdAt: '2025-01-01T07:50:00', isNew: true } })
  assert.deepStrictEqual(
    { titles, label: page.data.typeLabel, time: page.data.timeText, isNew: page.data.isNew },
    { titles: ['再读作品'], label: '再读一本', time: '2025-01-01', isNew: true }, '再读一本: 标题、类型名、时间、NEW')
}

run().then(() => {
  console.log('PASS: test-miniprogram-fabushi-detail')
}, (err) => {
  console.error(err)
  process.exit(1)
})

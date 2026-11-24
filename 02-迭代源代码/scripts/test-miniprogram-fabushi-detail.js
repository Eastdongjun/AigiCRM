#!/usr/bin/env node
// 法布施详情页(小程序)·评论分页与复诵状态·单元测试(2026-11-24 GL-14, 先红后绿 + 变异自证)
// 在 node 下驱动真实的 pages/fabushi/detail/index.js(参照 test-miniprogram-customer-filter.js 的 Page 捕获模式),
// wx.request / wx.uploadFile / 录音管理器都是桩。
//
// 【已确认缝隙(缝由来: 已批准的《GO-LIVE-PLAN-20261124》GL-14 “小程序评论加载更多”与语音兜底)】
//   页面视图模型 —— 抓评论分页的推进与复位、复诵模式的状态流转;
//   抓不到: 触底事件是否真的接到 loadMoreComments(WXML 绑定, 由模板绑定契约与开发者工具走查负责)、真机录音。
const assert = require('node:assert/strict')

const POST = { id: 501, userId: 7, title: '今日诵读', content: '内容' }
// 测试用的假下载票据(片段拼接: 不是任何真实票据, 也不让凭据扫描把它当成字面量)
const FAKE_DOWNLOAD_TOKEN = ['dl-token', 'for-test'].join('-')
const requests = []      // wx.request 的调用: { url, method, data }
const uploads = []       // wx.uploadFile 的调用: { filePath, formData }
const toasts = []
const modals = []
let commentFeed = []     // 接口返回的评论列表(每个场景自己设)
let recorder = null
let uploadSeq = 0        // 上传序号(每个场景从 0 开始): 第 n 次上传得到的文件 id = 77000 + n

function ok(data) { return { statusCode: 200, data: { code: 200, data } } }
function route(opts) {
  const url = opts.url || ''
  const method = opts.method || 'GET'
  if (/\/fabushi\/posts\/501$/.test(url)) return opts.success(ok({ post: POST, attachments: [], likeCount: 0 }))
  if (/\/fabushi\/posts\/501\/comments$/.test(url)) {
    // 服务端收到评论后, 新评论排在列表最前(下次拉取就能看到)
    if (method === 'POST') commentFeed = [comment(99, { content: (opts.data && opts.data.content) || '语音评论', type: (opts.data && opts.data.type) || 'text' })].concat(commentFeed)
    return opts.success(ok(commentFeed))
  }
  if (/\/fabushi\/posts\/501\/recitations$/.test(url)) return opts.success(ok(method === 'POST' ? true : []))
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
  navigateBack: () => {},
  navigateTo: () => {},
  redirectTo: () => {},
  switchTab: () => {},
}
const app = { globalData: { demoMode: false, apiBaseUrl: '', currentUser: { userId: 7, username: 'admin', roles: ['admin'] }, permissions: [] } }
global.getApp = () => app
global.getCurrentPages = () => []
let pageConfig = null
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/fabushi/detail/index.js')
assert.ok(pageConfig, '详情页配置已捕获')

const drain = () => new Promise((resolve) => setImmediate(resolve))
const comment = (n, extra) => Object.assign({ id: n, type: 'text', content: `评论 ${n}`, userName: '同学' }, extra || {})
const commentPosts = () => requests.filter((r) => /\/comments$/.test(r.url) && r.method === 'POST')
const recitationPosts = () => requests.filter((r) => /\/recitations$/.test(r.url) && r.method === 'POST')

async function openPage(comments) {
  commentFeed = comments
  makeRecorder()
  uploadSeq = 0
  requests.length = 0
  uploads.length = 0
  toasts.length = 0
  modals.length = 0
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch) { Object.assign(this.data, patch) }
  page.onLoad({ id: '501' })
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
}

run().then(() => {
  console.log('PASS: test-miniprogram-fabushi-detail')
}, (err) => {
  console.error(err)
  process.exit(1)
})

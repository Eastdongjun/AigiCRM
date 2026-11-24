#!/usr/bin/env node
// 【GL-12 2026-11-24】小程序图片编辑页 · 开发者工具页面级冒烟
// 真实的画布、触摸事件与导出——补 node 单测里画布是桩、抓不到的那一段。
// 口径: 连到已经打开的微信开发者工具(自动化端口 9424, 与 walk-all-pages.mjs 相同); 样例图片在运行时
//       写进模拟器的用户目录(得到的 wxfile:// 路径与选图结果同类, 不改源码目录); 只读, 不调用任何后端接口。
//       判定只认退出码: 0 全部通过 / 1 有断言不成立 / 2 环境不具备(没连上、页面没编进去、页面栈已满)。
// 用法: node scripts/miniprogram/mp_image_editor_smoke.mjs <样例 jpg> [截图输出目录]
//       样例: 竖图 2400 × 3200(断言里的数值按这个尺寸推算)。
import { createRequire } from 'node:module'
import fs from 'node:fs'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const EDITOR = 'pages/fabushi/image-editor/index'
const SAMPLE = { width: 2400, height: 3200 }
const sampleFile = process.argv[2]
const shotDir = process.argv[3] || '/tmp'
if (!sampleFile) {
  console.error('BLOCKED: 用法 node scripts/miniprogram/mp_image_editor_smoke.mjs <样例 jpg> [截图输出目录]')
  process.exit(2)
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const failures = []
const check = (label, ok, detail) => {
  console.log((ok ? 'PASS ' : 'FAIL ') + label + (detail ? ' — ' + detail : ''))
  if (!ok) failures.push(label)
}
const near = (a, b, tol = 1e-6) => Math.abs(a - b) <= tol

async function waitFor(label, probe, timeoutMs = 15000) {
  const end = Date.now() + timeoutMs
  while (Date.now() < end) {
    try {
      const value = await probe()
      if (value) return value
    } catch (e) { /* 页面切换中的瞬时读数失败: 继续等到超时 */ }
    await sleep(250)
  }
  throw new Error('等待超时: ' + label)
}

let mini
try {
  mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9424' })
} catch (e) {
  console.error('BLOCKED: 连不上微信开发者工具的自动化端口 9424 — ' + (e && e.message))
  process.exit(2)
}

const stackDepth = () => mini.evaluate(() => getCurrentPages().length)
const geometry = () => mini.evaluate(() => {
  const stack = getCurrentPages()
  const page = stack[stack.length - 1]
  return { route: page.route, transform: page._transform, fit: page._fit, frame: page._frame, view: page._view, source: page._source }
})
// 图片完整盖住裁剪框
const covers = (g) => {
  const width = g.fit.width * g.transform.scale
  const height = g.fit.height * g.transform.scale
  const { frame, transform } = g
  return transform.x <= frame.x + 1e-6 && transform.y <= frame.y + 1e-6
    && transform.x + width >= frame.x + frame.width - 1e-6
    && transform.y + height >= frame.y + frame.height - 1e-6
}

async function openEditor(samplePath, requestId, total) {
  const depth = await stackDepth()
  const query = '?src=' + encodeURIComponent(samplePath) + '&requestId=' + requestId + '&position=1&total=' + (total || 1)
  await mini.navigateTo('/' + EDITOR + query)
  const page = await waitFor('编辑页就绪', async () => {
    const current = await mini.currentPage()
    if (!current || current.path !== EDITOR) return null
    const data = await current.data()
    return data.ready ? current : null
  })
  return { page, depth }
}

async function waitLeftEditor(depth) {
  await waitFor('离开编辑页', async () => (await stackDepth()) === depth, 8000)
  const result = await mini.evaluate(() => getApp().globalData.fabushiImageEditResult)
  await mini.evaluate(() => { getApp().globalData.fabushiImageEditResult = null })
  return result
}

let samplePath = ''
try {
  if ((await stackDepth()) >= 10) {
    console.error('BLOCKED: 模拟器页面栈已满(10 层), 没法再打开编辑页')
    process.exit(2)
  }
  // 报错收集器: console.error / onError / 未处理的 Promise 拒绝
  await mini.evaluate(() => {
    const app = getApp()
    app.__gl12Log = []
    if (!app.__gl12Hooked) {
      app.__gl12Hooked = true
      const push = (tag) => (...args) => {
        try { app.__gl12Log.push(tag + ': ' + args.map((a) => String(a)).join(' ').slice(0, 300)) } catch (e) { /* 记不下不影响结论 */ }
      }
      const original = console.error
      console.error = function (...args) { push('console.error')(...args); try { original.apply(console, args) } catch (e) { /* 原样输出失败不处理 */ } }
      if (wx.onError) wx.onError(push('onError'))
      if (wx.onUnhandledRejection) wx.onUnhandledRejection((res) => push('unhandledRejection')(res && res.reason))
    }
  })
  // 样例图片写进模拟器的用户目录
  samplePath = await mini.evaluate((base64) => {
    const filePath = wx.env.USER_DATA_PATH + '/gl12-smoke-sample.jpg'
    wx.getFileSystemManager().writeFileSync(filePath, base64, 'base64')
    return filePath
  }, fs.readFileSync(sampleFile).toString('base64'))
  console.log('样例图片: ' + samplePath)

  // ---- A. 进页: 整图显示, 原比例, 画布按真实尺寸解码 ----
  let opened = await openEditor(samplePath, 'smoke-1', 1)
  let page = opened.page
  let data = await page.data()
  check('A1 默认原比例、显示第几张/共几张、单张不显示“其余用原图”', data.ratioKey === 'original' && data.position === 1 && data.total === 1 && data.showUseOriginalForRest === false, JSON.stringify({ ratioKey: data.ratioKey, position: data.position, total: data.total }))
  let g = await geometry()
  check('A2 画布解码出的尺寸 = 2400 × 3200', g.source && g.source.width === SAMPLE.width && g.source.height === SAMPLE.height, JSON.stringify(g.source))
  check('A3 舞台宽度是模拟器的逻辑宽度, 高度为正', g.view.width > 0 && g.view.height > 0, JSON.stringify(g.view))
  check('A4 等比适配(宽高比 0.75), 整图缩放为 1', near(g.fit.width / g.fit.height, 0.75) && near(g.transform.scale, 1), JSON.stringify({ fit: g.fit, transform: g.transform }))
  check('A5 原比例的裁剪框与整张图重合', near(g.frame.width, g.fit.width) && near(g.frame.height, g.fit.height) && covers(g), JSON.stringify(g.frame))
  check('A6 初始可以放大、不能缩小', data.canZoomIn === true && data.canZoomOut === false)
  if (shotDir) await mini.screenshot({ path: shotDir + '/gl12-mp-editor-a-original.png' }).catch(() => {})

  // ---- B. 切到 1:1: 框为正方形、居中在舞台内; 图片仍盖住框 ----
  await page.callMethod('onRatio', { currentTarget: { dataset: { key: '1:1' } } })
  g = await geometry()
  data = await page.data()
  check('B1 切换到 1:1', data.ratioKey === '1:1')
  check('B2 1:1 的框是正方形、宽为舞台宽度的 86%、居中', near(g.frame.width, g.frame.height) && near(g.frame.width, g.view.width * 0.86) && near(g.frame.x, (g.view.width - g.frame.width) / 2) && near(g.frame.y, (g.view.height - g.frame.height) / 2), JSON.stringify(g.frame))
  check('B3 切换后图片盖住裁剪框', covers(g))

  // ---- C. 放大按钮: 以框中心放大 1.5 倍 ----
  const before = g.transform
  const buttons = await page.$$('.act-btn')
  await buttons[0].tap()
  await sleep(300)
  g = await geometry()
  data = await page.data()
  check('C1 点“放大”: 缩放 × 1.5, 放大后可以缩小', near(g.transform.scale, before.scale * 1.5, 1e-9) && data.canZoomOut === true, JSON.stringify(g.transform))
  check('C2 放大后图片仍盖住裁剪框', covers(g))

  // ---- D. 单指拖动: 图片跟着手指移动 30 ----
  // 自动化库(v0.12.1)的触摸事件字段名是 touches / changeTouches; 页面只读 touches
  const stage = await page.$('#stage')
  const at = (x, y) => ({ pageX: x, pageY: y, clientX: x, clientY: y })
  const beforePan = g.transform
  await stage.touchstart({ touches: [at(150, 150)], changeTouches: [at(150, 150)] })
  await stage.touchmove({ touches: [at(180, 150)], changeTouches: [at(180, 150)] })
  await stage.touchend({ touches: [], changeTouches: [at(180, 150)] })
  await sleep(300)
  g = await geometry()
  check('D1 单指向右拖 30: 图片右移 30、纵坐标不变', near(g.transform.x - beforePan.x, 30, 1e-6) && near(g.transform.y, beforePan.y, 1e-6), JSON.stringify({ before: beforePan, after: g.transform }))
  check('D2 拖动后图片仍盖住裁剪框', covers(g))

  // ---- E. 双指张开: 间距从 100 到 200, 以中点放大一倍 ----
  const beforePinch = g.transform
  await stage.touchstart({ touches: [at(100, 300), at(200, 300)], changeTouches: [at(100, 300), at(200, 300)] })
  await stage.touchmove({ touches: [at(50, 300), at(250, 300)], changeTouches: [at(50, 300), at(250, 300)] })
  await stage.touchend({ touches: [], changeTouches: [at(50, 300), at(250, 300)] })
  await sleep(300)
  g = await geometry()
  check('E1 双指间距 × 2: 缩放 × 2', near(g.transform.scale, beforePinch.scale * 2, 1e-9), JSON.stringify({ before: beforePinch, after: g.transform }))
  check('E2 双指后图片仍盖住裁剪框', covers(g))
  if (shotDir) await mini.screenshot({ path: shotDir + '/gl12-mp-editor-e-zoomed.png' }).catch(() => {})

  // ---- F. 确定: 导出的是正方形(1:1), 边长 = 框宽 / (适配宽 × 缩放) × 2400 = 688 ----
  const scaleAtExport = g.transform.scale
  const depth = opened.depth
  await (await page.$('.decide-btn.primary')).tap()
  let result = await waitLeftEditor(depth)
  check('F1 回传: 请求号对上、已编辑、带临时文件路径', !!result && result.requestId === 'smoke-1' && result.edited === true && typeof result.path === 'string' && result.path.length > 0, JSON.stringify(result))
  if (result && result.path) {
    const info = await mini.callWxMethod('getImageInfo', { src: result.path })
    const expected = Math.round((0.86 / scaleAtExport) * SAMPLE.width)
    check('F2 导出文件是 JPG、正方形、边长符合裁剪框(' + expected + ')', info && info.type === 'jpeg' && info.width === info.height && Math.abs(info.width - expected) <= 2 && info.width <= 1600, JSON.stringify({ type: info && info.type, width: info && info.width, height: info && info.height }))
  }
  check('F3 导出后页面栈回到打开前', (await stackDepth()) === depth)

  // ---- G. 原比例不动手直接确定: 整张图压到长边 1600 = 1200 × 1600 ----
  opened = await openEditor(samplePath, 'smoke-2', 1)
  page = opened.page
  await (await page.$('.decide-btn.primary')).tap()
  result = await waitLeftEditor(opened.depth)
  check('G1 整图导出已回传', !!result && result.requestId === 'smoke-2' && result.edited === true)
  if (result && result.path) {
    const info = await mini.callWxMethod('getImageInfo', { src: result.path })
    check('G2 导出尺寸 1200 × 1600(长边不超过 1600)', info && info.width === 1200 && info.height === 1600, JSON.stringify({ width: info && info.width, height: info && info.height }))
  }

  // ---- H. 使用原图: 不编辑 ----
  opened = await openEditor(samplePath, 'smoke-3', 1)
  page = opened.page
  await (await page.$$('.decide-btn.ghost'))[0].tap()
  result = await waitLeftEditor(opened.depth)
  check('H1 点“使用原图”: 回传未编辑、不带路径', !!result && result.requestId === 'smoke-3' && result.edited === false && !result.path, JSON.stringify(result))
  check('H2 页面栈回到打开前', (await stackDepth()) === opened.depth)

  // ---- I. 多张图: 显示“其余用原图” ----
  opened = await openEditor(samplePath, 'smoke-4', 2)
  page = opened.page
  data = await page.data()
  check('I1 两张图时显示“其余用原图”、第 1 / 2 张', data.showUseOriginalForRest === true && data.position === 1 && data.total === 2)
  const ghosts = await page.$$('.decide-btn.ghost')
  await ghosts[ghosts.length - 1].tap()
  result = await waitLeftEditor(opened.depth)
  check('I2 点“其余用原图”: 回传带 useOriginalForRest', !!result && result.requestId === 'smoke-4' && result.edited === false && result.useOriginalForRest === true, JSON.stringify(result))

  // ---- J. 直接返回(导航栏返回): 这一张按原图处理, 且不会多弹一层 ----
  opened = await openEditor(samplePath, 'smoke-5', 1)
  await mini.navigateBack()
  result = await waitLeftEditor(opened.depth)
  check('J1 直接返回: 回传未编辑', !!result && result.requestId === 'smoke-5' && result.edited === false, JSON.stringify(result))
  check('J2 页面栈只少一层(没有多弹)', (await stackDepth()) === opened.depth)

  // ---- K. 全程没有报错 ----
  const logs = (await mini.evaluate(() => getApp().__gl12Log)) || []
  check('K1 全程没有 console.error / onError / 未处理的 Promise 拒绝', logs.length === 0, logs.slice(0, 3).join(' | '))
} catch (e) {
  const message = e && e.message ? e.message : String(e)
  const notCompiled = /navigateTo:fail|page not found|等待超时: 编辑页就绪|WebviewPositionLimit/i.test(message)
  console.error((notCompiled ? 'BLOCKED' : 'FAIL') + ': ' + message)
  failures.push(notCompiled ? 'BLOCKED' : 'EXCEPTION')
}

try {
  if (samplePath) await mini.evaluate((file) => { try { wx.getFileSystemManager().unlinkSync(file) } catch (e) { /* 清不掉样例不影响结论 */ } }, samplePath)
} catch (e) { /* 已断开 */ }
try { mini.disconnect() } catch (e) { /* 已断开 */ }

if (failures.length) {
  console.log('GL12_MP_SMOKE_' + (failures.includes('BLOCKED') ? 'BLOCKED' : 'RED') + ' — ' + failures.length + ' 项: ' + failures.join('; '))
  process.exit(failures.includes('BLOCKED') ? 2 : 1)
}
console.log('GL12_MP_SMOKE_GREEN')
process.exit(0)

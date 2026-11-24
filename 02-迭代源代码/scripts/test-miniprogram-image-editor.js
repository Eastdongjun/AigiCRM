#!/usr/bin/env node
// 小程序图片编辑(放大查看 + 裁剪)·单元测试(2026-11-24 GL-12, 先写本测试(红)→实现(绿)→变异自证)
// 被测模块:
//   aigicrm-miniprogram/utils/fabushi-image-editor.js —— 纯函数, 零 wx 依赖(几何 / 手势 / 编辑队列)
//   aigicrm-miniprogram/pages/fabushi/upload/index.js  —— 发布页选图后进编辑器的接线(Page 视图模型)
//   aigicrm-miniprogram/pages/fabushi/image-editor/index.js —— 编辑页(Page 视图模型 + 画布调用)
//
// 【已确认缝隙(缝由来: 已批准的《GO-LIVE-PLAN-20261124》GL-12 验证口径)】
//   1. 纯函数模块导出面(本文件 1–3 节) —— 抓几何算错(裁剪越界 / 比例失真 / 导出超长边 / 图片盖不住裁剪框)、
//      手势状态机、编辑队列的推进; 抓不到页面接线
//   2. 页面视图模型(本文件 4–5 节, 沿用 test-miniprogram-*.js 在 node 下驱动真实页面模块的做法) ——
//      抓接线类问题(选图后没进编辑器 / 回传没人消费 / 改过的图没替换原图 / 附件上限被改动);
//      画布与触摸事件是桩, 抓不到真机上的触摸手感、大图性能、带旋转信息的照片
//   3. 页面登记与可达性(check-mobile-navigation-contract / check-feature-audit-contract, 不在本文件) ——
//      抓死页面与漏登记
//   真机手工项(触摸绘制、横竖图、相机竖拍)进真机清单(GL-25)。
const assert = require('node:assert/strict')

const editor = require('../aigicrm-miniprogram/utils/fabushi-image-editor.js')

const near = (actual, expected, tol = 1e-6, label = '') =>
  assert.ok(Math.abs(actual - expected) <= tol, `${label} 期望 ${expected}, 实际 ${actual}`)

// =====================================================================
// 1. 几何: 适配、裁剪框、缩放下限、初始位置、平移边界、定点缩放
//    期望值全部手工推算。例 A: 横图 4000×3000, 舞台 375×600; 例 B: 竖图 3000×4000, 同一舞台。
// =====================================================================

// ---- 1.1 等比适配(短边贴舞台, 不变形) ----
{
  const fit = editor.fitContain(4000, 3000, 375, 600)
  near(fit.width, 375, 1e-6, '横图适配宽')
  near(fit.height, 281.25, 1e-6, '横图适配高')
  const fitB = editor.fitContain(3000, 4000, 375, 600)
  near(fitB.width, 375, 1e-6, '竖图适配宽')
  near(fitB.height, 500, 1e-6, '竖图适配高')
}

// ---- 1.2 裁剪框: 原比例=整张图; 固定比例=舞台内缩 7% 后的最大同比例矩形, 居中 ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(4000, 3000, 375, 600)
  const original = editor.makeFrame('original', 4000, 3000, view)
  assert.deepStrictEqual(
    { x: original.x, y: original.y, width: original.width, height: original.height },
    { x: 0, y: 159.375, width: 375, height: 281.25 },
    '原比例裁剪框应与适配后的整张图重合'
  )
  const square = editor.makeFrame('1:1', 4000, 3000, view)
  near(square.width, 322.5, 1e-6, '1:1 宽')
  near(square.height, 322.5, 1e-6, '1:1 高')
  near(square.x, 26.25, 1e-6, '1:1 左')
  near(square.y, 138.75, 1e-6, '1:1 上')
  const wide = editor.makeFrame('16:9', 4000, 3000, view)
  near(wide.width, 322.5, 1e-6, '16:9 宽')
  near(wide.height, 181.40625, 1e-6, '16:9 高')
  near(wide.y, 209.296875, 1e-6, '16:9 上')
  const tall = editor.makeFrame('3:4', 4000, 3000, view)
  near(tall.width, 322.5, 1e-6, '3:4 宽')
  near(tall.height, 430, 1e-6, '3:4 高')
  // 每个预设都要有自己的比例
  for (const preset of editor.RATIOS) {
    if (preset.value == null) continue
    const frame = editor.makeFrame(preset.key, 4000, 3000, view)
    near(frame.width / frame.height, preset.value, 1e-9, `预设 ${preset.key} 的宽高比`)
  }
  // 未知预设不发明比例, 按原比例处理
  assert.strictEqual(editor.makeFrame('9:16', 4000, 3000, view).width, original.width, '未知预设应回落到原比例')
}

// ---- 1.3 缩放下限: 图片必须盖住裁剪框; 盖不住就放大到刚好盖住并居中 ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(4000, 3000, 375, 600)
  const square = editor.makeFrame('1:1', 4000, 3000, view)
  // 适配高 281.25 < 框高 322.5 → 至少放大 322.5/281.25 = 86/75
  near(editor.minScale(fit, square), 86 / 75, 1e-9, '1:1 的缩放下限')
  const original = editor.makeFrame('original', 4000, 3000, view)
  near(editor.minScale(fit, original), 1, 1e-9, '原比例的缩放下限是 1')
  const initial = editor.initialTransform(fit, square)
  near(initial.scale, 86 / 75, 1e-9, '初始缩放')
  near(initial.x, -27.5, 1e-6, '初始横坐标(整图 430 宽, 在框内居中)')
  near(initial.y, 138.75, 1e-6, '初始纵坐标')
  const initialB = editor.initialTransform(editor.fitContain(3000, 4000, 375, 600), editor.makeFrame('original', 3000, 4000, view))
  near(initialB.scale, 1, 1e-9, '竖图原比例初始缩放')
  near(initialB.x, 0, 1e-9, '竖图初始横坐标')
  near(initialB.y, 50, 1e-9, '竖图初始纵坐标')
}

// ---- 1.4 平移: 图片始终盖住裁剪框, 最多拖到边缘对齐 ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(4000, 3000, 375, 600)
  const square = editor.makeFrame('1:1', 4000, 3000, view)
  const start = editor.initialTransform(fit, square)
  const right = editor.applyPan(start, 1000, 1000, fit, square)
  near(right.x, 26.25, 1e-6, '向右拖到头: 图片左边贴框左边')
  near(right.y, 138.75, 1e-6, '向下拖到头: 图片上边贴框上边')
  const left = editor.applyPan(start, -1000, 0, fit, square)
  near(left.x, -81.25, 1e-6, '向左拖到头: 图片右边贴框右边')
  near(left.y, 138.75, 1e-6, '只横向拖动, 纵坐标不变')
  const small = editor.applyPan(start, 3, -4, fit, square)
  near(small.x, start.x + 3, 1e-6, '框内小幅拖动照常移动')
  // 这张横图在 1:1 框下纵向刚好盖满, 没有上下余量
  near(small.y, start.y, 1e-6, '纵向刚好盖满时上下拖不动')
  // 缩放到下限以下: 拉回下限, 图片中心保持不动, 再夹回框内
  const shrunk = editor.clampTransform({ x: start.x, y: start.y, scale: 0.5 }, fit, square)
  near(shrunk.scale, 86 / 75, 1e-9, '低于下限被拉回下限')
}

// ---- 1.5 定点缩放: 手指下的那一点在缩放前后不动; 结果仍盖住裁剪框 ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(3000, 4000, 375, 600)
  const original = editor.makeFrame('original', 3000, 4000, view)
  const start = editor.initialTransform(fit, original)
  const anchor = { x: 187.5, y: 300 }
  const zoomed = editor.applyZoom(start, 2, anchor, fit, original)
  near(zoomed.scale, 2, 1e-9, '放大一倍')
  near(zoomed.x, -187.5, 1e-6, '缩放后横坐标')
  near(zoomed.y, -200, 1e-6, '缩放后纵坐标')
  // 锚点处的图片内容点在缩放前后是同一个
  const before = (anchor.x - start.x) / (fit.width * start.scale)
  const after = (anchor.x - zoomed.x) / (fit.width * zoomed.scale)
  near(before, after, 1e-9, '锚点横向对应图片的同一位置')
  // 缩小不会低于下限
  const shrunk = editor.applyZoom(zoomed, 0.1, anchor, fit, original)
  near(shrunk.scale, 1, 1e-9, '缩小到下限为止')
  // 放大后两个方向都有余量, 小幅拖动照常移动
  const nudged = editor.applyPan(zoomed, 5, 7, fit, original)
  near(nudged.x, -182.5, 1e-6, '放大后小幅拖动(横)')
  near(nudged.y, -193, 1e-6, '放大后小幅拖动(纵)')
}

// =====================================================================
// 2. 裁剪与导出: 裁剪框映射回原图像素; 导出长边不超过 1600; 不变量
// =====================================================================

// ---- 2.1 裁剪框映射回原图(整数像素, 落在原图内) ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(3000, 4000, 375, 600)
  const original = editor.makeFrame('original', 3000, 4000, view)
  const start = editor.initialTransform(fit, original)
  assert.deepStrictEqual(editor.cropSourceRect(start, fit, original, 3000, 4000), { x: 0, y: 0, width: 3000, height: 4000 }, '原比例不动手直接确定 = 整张图')
  const zoomed = editor.applyZoom(start, 2, { x: 187.5, y: 300 }, fit, original)
  assert.deepStrictEqual(editor.cropSourceRect(zoomed, fit, original, 3000, 4000), { x: 750, y: 1000, width: 1500, height: 2000 }, '放大一倍后取正中的四分之一')
  const square = editor.makeFrame('1:1', 3000, 4000, view)
  const squareStart = editor.initialTransform(fit, square)
  assert.deepStrictEqual(editor.cropSourceRect(squareStart, fit, square, 3000, 4000), { x: 210, y: 710, width: 2580, height: 2580 }, '竖图 1:1 框映射到原图')
  const fitA = editor.fitContain(4000, 3000, 375, 600)
  const squareA = editor.makeFrame('1:1', 4000, 3000, view)
  const startA = editor.initialTransform(fitA, squareA)
  assert.deepStrictEqual(editor.cropSourceRect(startA, fitA, squareA, 4000, 3000), { x: 500, y: 0, width: 3000, height: 3000 }, '横图 1:1 初始取正中的正方形')
  // 未知预设回落到原比例, 映射结果也应与原比例一致
  assert.deepStrictEqual(
    editor.cropSourceRect(start, fit, editor.makeFrame('9:16', 3000, 4000, view), 3000, 4000),
    editor.cropSourceRect(start, fit, original, 3000, 4000),
    '未知预设的裁剪结果与原比例一致'
  )
}

// ---- 2.2 导出尺寸: 长边不超过 1600; 小图不放大; 宽高比保持 ----
{
  assert.deepStrictEqual(editor.exportSize(1500, 2000), { width: 1200, height: 1600 }, '超过上限按长边缩到 1600')
  assert.deepStrictEqual(editor.exportSize(2000, 1500), { width: 1600, height: 1200 }, '横图超过上限')
  assert.deepStrictEqual(editor.exportSize(800, 600), { width: 800, height: 600 }, '小图保持原尺寸, 不放大')
  assert.deepStrictEqual(editor.exportSize(1600, 1600), { width: 1600, height: 1600 }, '正好等于上限')
  assert.deepStrictEqual(editor.exportSize(3333, 1000), { width: 1600, height: 480 }, '缩放后取整')
  assert.deepStrictEqual(editor.exportSize(3, 1), { width: 3, height: 1 }, '极小的裁剪至少 1 像素')
  assert.deepStrictEqual(editor.exportSize(3000, 4000, 1000), { width: 750, height: 1000 }, '上限可以作为参数传入')
}

// ---- 2.3 不变量: 任意图片 × 舞台 × 预设 × 拖动缩放序列 ----
// 随机数用固定种子的线性同余序列, 每次运行完全相同(失败可复现)。
{
  function seededRandom(seed) {
    let state = seed >>> 0
    return () => {
      state = (Math.imul(state, 1664525) + 1013904223) >>> 0
      return state / 4294967296
    }
  }
  const rand = seededRandom(20261124)
  let checked = 0
  for (let i = 0; i < 300; i++) {
    const image = { width: 1 + Math.floor(rand() * 9000), height: 1 + Math.floor(rand() * 9000) }
    const view = { width: 200 + rand() * 500, height: 200 + rand() * 900 }
    const preset = editor.RATIOS[Math.floor(rand() * editor.RATIOS.length)]
    const fit = editor.fitContain(image.width, image.height, view.width, view.height)
    const frame = editor.makeFrame(preset.key, image.width, image.height, view)
    assert.ok(frame.x >= -1e-6 && frame.y >= -1e-6, `裁剪框不越出舞台(左上) #${i}`)
    assert.ok(frame.x + frame.width <= view.width + 1e-6 && frame.y + frame.height <= view.height + 1e-6, `裁剪框不越出舞台(右下) #${i}`)
    assert.ok(frame.width > 0 && frame.height > 0, `裁剪框不为空 #${i}`)
    let transform = editor.initialTransform(fit, frame)
    for (let step = 0; step < 25; step++) {
      if (rand() < 0.45) transform = editor.applyPan(transform, (rand() - 0.5) * 2000, (rand() - 0.5) * 2000, fit, frame)
      else transform = editor.applyZoom(transform, 0.05 + rand() * 40, { x: rand() * view.width, y: rand() * view.height }, fit, frame)
      const shownW = fit.width * transform.scale
      const shownH = fit.height * transform.scale
      assert.ok(transform.x <= frame.x + 1e-6 && transform.x + shownW >= frame.x + frame.width - 1e-6, `图片横向盖住裁剪框 #${i}.${step}`)
      assert.ok(transform.y <= frame.y + 1e-6 && transform.y + shownH >= frame.y + frame.height - 1e-6, `图片纵向盖住裁剪框 #${i}.${step}`)
      assert.ok(transform.scale >= editor.minScale(fit, frame) - 1e-9, `缩放不低于下限 #${i}.${step}`)
      const rect = editor.cropSourceRect(transform, fit, frame, image.width, image.height)
      assert.ok(rect.x >= 0 && rect.y >= 0, `裁剪起点不在原图外 #${i}.${step}`)
      assert.ok(rect.width >= 1 && rect.height >= 1, `裁剪至少 1 像素 #${i}.${step}`)
      assert.ok(rect.x + rect.width <= image.width && rect.y + rect.height <= image.height, `裁剪不越出原图 #${i}.${step}`)
      const size = editor.exportSize(rect.width, rect.height)
      assert.ok(size.width >= 1 && size.height >= 1 && Math.max(size.width, size.height) <= editor.MAX_EXPORT_EDGE, `导出长边不超过上限 #${i}.${step}`)
      assert.ok(size.width <= rect.width && size.height <= rect.height, `导出不放大 #${i}.${step}`)
      checked++
    }
  }
  assert.strictEqual(checked, 7500, '不变量检查的步数')
}

// =====================================================================
// 3. 手势状态机: 把触摸点序列翻译成位移与缩放; 点击 / 双击判定
// =====================================================================

// ---- 3.1 单指拖动只报位移; 手指数变化的那一帧重新锚定、不产生位移 ----
{
  const g = editor.createGesture()
  editor.gestureStart(g, [{ x: 100, y: 100 }], 1000)
  const first = editor.gestureMove(g, [{ x: 110, y: 95 }], 1010)
  assert.deepStrictEqual(first, { panX: 10, panY: -5, factor: 1, anchor: { x: 110, y: 95 } }, '单指拖动一帧')
  const second = editor.gestureMove(g, [{ x: 130, y: 95 }], 1020)
  assert.deepStrictEqual(second, { panX: 20, panY: 0, factor: 1, anchor: { x: 130, y: 95 } }, '单指拖动下一帧按相邻两帧算位移')
  assert.strictEqual(editor.gestureMove(g, [{ x: 130, y: 95 }, { x: 200, y: 160 }], 1030), null, '加上第二根手指的一帧不动')
  const pinch = editor.gestureMove(g, [{ x: 100, y: 80 }, { x: 240, y: 170 }], 1040)
  // 上一帧两点 (130,95)(200,160): 间距 √(70²+65²), 中点 (165,127.5); 本帧两点 (100,80)(240,170): 间距 √(140²+90²), 中点 (170,125)
  near(pinch.factor, Math.hypot(140, 90) / Math.hypot(70, 65), 1e-12, '双指缩放倍率 = 本帧间距 / 上帧间距')
  assert.deepStrictEqual(pinch.anchor, { x: 170, y: 125 }, '缩放锚点 = 本帧两指中点')
  near(pinch.panX, 5, 1e-12, '双指中点横移')
  near(pinch.panY, -2.5, 1e-12, '双指中点纵移')
  assert.strictEqual(editor.gestureEnd(g, [{ x: 240, y: 170 }], 1050), null, '抬起一根手指: 重新锚定')
  const third = editor.gestureMove(g, [{ x: 250, y: 170 }], 1060)
  assert.deepStrictEqual(third, { panX: 10, panY: 0, factor: 1, anchor: { x: 250, y: 170 } }, '剩一根手指后按单指拖动算')
  assert.deepStrictEqual(editor.gestureEnd(g, [], 1070), { doubleTap: false }, '全部抬起')
  // 手势进行中传入空数组(异常情况)不抛错, 之后重新锚定
  assert.strictEqual(editor.gestureMove(g, [], 1080), null, '空触摸点不产生位移')
  const g2 = editor.createGesture()
  editor.gestureStart(g2, [{ x: 0, y: 0 }, { x: 100, y: 0 }], 0)
  assert.deepStrictEqual(editor.gestureMove(g2, [{ x: 0, y: 0 }, { x: 200, y: 0 }], 16), { panX: 50, panY: 0, factor: 2, anchor: { x: 100, y: 0 } }, '两指同时按下直接产生缩放')
}

// ---- 3.2 点击与双击: 短按且几乎没移动算点击; 两次点击又快又近算双击 ----
{
  const t = editor.createGesture()
  editor.gestureStart(t, [{ x: 50, y: 50 }], 0)
  editor.gestureMove(t, [{ x: 53, y: 54 }], 80)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 120), { doubleTap: false }, '移动 5 像素、120 毫秒: 是一次单击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 250)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 290), { doubleTap: true }, '距上一次点击 170 毫秒且位置很近: 双击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 700)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 740), { doubleTap: false }, '距上一次点击 450 毫秒: 重新算单击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 900)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 940), { doubleTap: true }, '紧接着再点一次又是双击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 2000)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 2400), { doubleTap: false }, '按住 400 毫秒不算点击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 3000)
  editor.gestureMove(t, [{ x: 80, y: 50 }], 3050)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 3080), { doubleTap: false }, '拖动过不算点击')
  editor.gestureStart(t, [{ x: 52, y: 50 }], 5000)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 5040), { doubleTap: false })
  editor.gestureStart(t, [{ x: 350, y: 500 }], 5100)
  assert.deepStrictEqual(editor.gestureEnd(t, [], 5140), { doubleTap: false }, '第二次点在很远的地方不算双击')
  editor.gestureStart(t, [{ x: 60, y: 55 }], 6000)
  assert.strictEqual(editor.gestureEnd(t, [{ x: 60, y: 55 }], 6040), null, '还有手指按着时不判定点击')
}

// ---- 3.3 手势落到图片上; 双击在“整图”与“放大”之间切换 ----
{
  const view = { width: 375, height: 600 }
  const fit = editor.fitContain(3000, 4000, 375, 600)
  const original = editor.makeFrame('original', 3000, 4000, view)
  const start = editor.initialTransform(fit, original)
  const zoomed = editor.applyGesture(start, { panX: 0, panY: 0, factor: 2, anchor: { x: 187.5, y: 300 } }, fit, original)
  assert.deepStrictEqual(zoomed, { x: -187.5, y: -200, scale: 2 }, '不位移、放大一倍')
  // 整图状态下图片与裁剪框重合, 没有可移动的余量: 位移被夹住, 图片不动
  assert.deepStrictEqual(editor.applyGesture(start, { panX: 100, panY: -30, factor: 1, anchor: { x: 10, y: 10 } }, fit, original), start, '整图状态下拖不动')
  const panned = editor.applyGesture(zoomed, { panX: 0, panY: -30, factor: 1, anchor: { x: 10, y: 10 } }, fit, original)
  assert.deepStrictEqual(panned, { x: -187.5, y: -230, scale: 2 }, '放大后只位移')
  // 先位移再缩放: 位移后左上角 (-237.5,-200), 锚点 (100,100) 处内容不动 → 左上角移到 (100-337.5×1.5, 100-300×1.5)
  const both = editor.applyGesture(zoomed, { panX: -50, panY: 0, factor: 1.5, anchor: { x: 100, y: 100 } }, fit, original)
  assert.deepStrictEqual(both, { x: -406.25, y: -350, scale: 3 }, '先位移再以锚点缩放')
  assert.strictEqual(editor.isZoomedIn(start, fit, original), false, '初始状态没有放大')
  assert.strictEqual(editor.isZoomedIn(zoomed, fit, original), true, '放大后')
  const toggled = editor.toggleZoom(start, fit, original)
  assert.deepStrictEqual(toggled, { x: -281.25, y: -325, scale: 2.5 }, '双击: 以裁剪框中心放大到 2.5 倍')
  assert.deepStrictEqual(editor.toggleZoom(toggled, fit, original), start, '再双击: 回到整图')
  // 1:1 框下初始缩放本来就是下限的 86/75, 仍算“没有放大”
  const square = editor.makeFrame('1:1', 4000, 3000, view)
  const fitA = editor.fitContain(4000, 3000, 375, 600)
  const startA = editor.initialTransform(fitA, square)
  assert.strictEqual(editor.isZoomedIn(startA, fitA, square), false, '刚好盖住裁剪框不算放大')
  const zoomedA = editor.toggleZoom(startA, fitA, square)
  near(zoomedA.scale, (86 / 75) * 2.5, 1e-9, '1:1 下双击放大到下限的 2.5 倍')
  assert.deepStrictEqual(editor.toggleZoom(zoomedA, fitA, square), startA, '1:1 下再双击回到初始')
  // 竖图配 1:1: 初始缩放是 1, 盖住框只需 0.86 —— 初始状态不算放大; 缩到 0.86 也不算放大
  const fitB = editor.fitContain(3000, 4000, 375, 600)
  const squareB = editor.makeFrame('1:1', 3000, 4000, view)
  const startB = editor.initialTransform(fitB, squareB)
  assert.deepStrictEqual(startB, { x: 0, y: 50, scale: 1 }, '竖图配 1:1 的初始状态是整图')
  assert.strictEqual(editor.isZoomedIn(startB, fitB, squareB), false, '竖图配 1:1: 初始状态不算放大')
  const doubleTapped = editor.toggleZoom(startB, fitB, squareB)
  assert.deepStrictEqual(doubleTapped, { x: -281.25, y: -325, scale: 2.5 }, '竖图配 1:1: 双击放大到 2.5 倍(以初始缩放为基线)')
  assert.strictEqual(editor.isZoomedIn(doubleTapped, fitB, squareB), true)
  assert.deepStrictEqual(editor.toggleZoom(doubleTapped, fitB, squareB), startB, '再双击回到初始')
  const shrunkB = editor.applyZoom(startB, 0.86, { x: 187.5, y: 300 }, fitB, squareB)
  assert.deepStrictEqual(shrunkB, { x: 26.25, y: 85, scale: 0.86 }, '缩到刚好盖住裁剪框')
  assert.strictEqual(editor.isZoomedIn(shrunkB, fitB, squareB), false, '比初始还小, 不算放大')
  assert.strictEqual(editor.toggleZoom(shrunkB, fitB, squareB).scale, 2.5, '从缩小状态双击同样放大到 2.5 倍')
}

// =====================================================================
// 4. 编辑队列: 选图后逐张进编辑器, 结果替换原图; 开关与回传
// =====================================================================

// ---- 4.1 纯函数 ----
{
  const files = [
    { path: 'wxfile://a.jpg', type: 'image', name: '图片' },
    { path: 'wxfile://v.mp4', type: 'video', name: '视频' },
    { path: 'wxfile://b.jpg', type: 'image', name: '图片' }
  ]
  const off = editor.createEditQueue(files, false)
  assert.deepStrictEqual(off.targets, [], '开关关闭: 没有图要进编辑器')
  assert.strictEqual(editor.currentEditTarget(off), -1, '开关关闭: 队列一开始就是空的')
  const on = editor.createEditQueue(files, true)
  assert.deepStrictEqual(on.targets, [0, 2], '开关打开: 只排图片, 按选中的先后顺序; 视频不进编辑器')
  assert.strictEqual(editor.currentEditTarget(on), 0, '第一张')
  assert.deepStrictEqual(editor.createEditQueue([], true).targets, [], '没有选图时队列为空')

  const cropped = editor.applyEditResult(on, { edited: true, path: 'wxfile://a-cropped.jpg' })
  assert.strictEqual(cropped.files[0].path, 'wxfile://a-cropped.jpg', '裁剪结果替换原图路径')
  assert.deepStrictEqual({ type: cropped.files[0].type, name: cropped.files[0].name }, { type: 'image', name: '图片' }, '类型和名称不变')
  assert.strictEqual(cropped.files[1], files[1], '视频那一条原样保留')
  assert.strictEqual(cropped.files.length, 3, '数量不变')
  assert.strictEqual(editor.currentEditTarget(cropped), 2, '轮到下一张')
  assert.strictEqual(files[0].path, 'wxfile://a.jpg', '传入的文件列表不被改动')

  const second = editor.applyEditResult(cropped, { edited: false })
  assert.strictEqual(second.files[2].path, 'wxfile://b.jpg', '使用原图: 路径不变')
  assert.strictEqual(editor.currentEditTarget(second), -1, '队列走完')

  const quit = editor.applyEditResult(on, {})
  assert.strictEqual(quit.files[0].path, 'wxfile://a.jpg', '中途退出(没有结果)按使用原图处理')
  assert.strictEqual(editor.currentEditTarget(quit), 2, '中途退出后队列照常推进')

  const rest = editor.applyEditResult(on, { edited: true, path: 'wxfile://a-cropped.jpg', useOriginalForRest: true })
  assert.strictEqual(rest.files[0].path, 'wxfile://a-cropped.jpg', '“其余用原图”不影响已经裁好的这一张')
  assert.strictEqual(rest.files[2].path, 'wxfile://b.jpg', '其余的保持原图')
  assert.strictEqual(editor.currentEditTarget(rest), -1, '其余的不再进编辑器')

  const broken = editor.applyEditResult(on, { edited: true, path: '' })
  assert.strictEqual(broken.files[0].path, 'wxfile://a.jpg', '声称已编辑却没有给出路径: 保留原图, 不把路径换成空的')
  assert.strictEqual(editor.applyEditResult(on, null).files[0].path, 'wxfile://a.jpg', '结果为空同样保留原图')

  const globalData = {}
  assert.strictEqual(editor.takeEditResult(globalData, 'r1'), null, '没有回传')
  globalData[editor.EDIT_RESULT_KEY] = { requestId: 'r2', edited: true, path: 'x' }
  assert.strictEqual(editor.takeEditResult(globalData, 'r1'), null, '请求号对不上: 不取')
  assert.deepStrictEqual(globalData[editor.EDIT_RESULT_KEY], { requestId: 'r2', edited: true, path: 'x' }, '请求号对不上: 别人的回传留着不动')
  assert.deepStrictEqual(editor.takeEditResult(globalData, 'r2'), { requestId: 'r2', edited: true, path: 'x' }, '请求号对上: 取走')
  assert.ok(!globalData[editor.EDIT_RESULT_KEY], '取走后清空')

  assert.strictEqual(editor.readEditOnPick(''), true, '从未设置过: 默认选图后进编辑器')
  assert.strictEqual(editor.readEditOnPick(undefined), true)
  assert.strictEqual(editor.readEditOnPick(false), false, '关过就是关')
  assert.strictEqual(editor.readEditOnPick(true), true)
}

// =====================================================================
// 5. 发布页接线: 选图后进编辑器 → 回传替换原图 → 整批走原有上传
//    在 node 下驱动真实的 pages/fabushi/upload/index.js; 网络层的桩记录上传与跳转。
// =====================================================================
const image = (name) => ({ tempFilePath: 'wxfile://' + name, fileType: 'image' })
const video = (name) => ({ tempFilePath: 'wxfile://' + name, fileType: 'video' })
const paramOf = (url, key) => new URL(url, 'https://local.invalid').searchParams.get(key)
const flush = () => new Promise((resolve) => setImmediate(resolve))

async function testUploadPageWiring() {
  const uploaded = []          // wx.uploadFile 收到的文件路径(按调用顺序)
  const navigations = []       // wx.navigateTo 的目标
  const toasts = []
  let storedEditOnPick = ''    // 本机存储里的开关
  let pendingPicks = []        // wx.chooseMedia 成功时交出的文件
  let chooseMediaOptions = null
  let navigateFails = false
  let uploadPageConfig = null
  const app = {
    globalData: { demoMode: false, currentUser: { id: 7, username: 'admin', roles: ['admin'] }, apiBaseUrl: '', permissions: [] }
  }

  global.wx = {
    request: (opts) => { if (opts.success) opts.success({ statusCode: 200, data: { code: 200, data: null } }) },
    uploadFile: (opts) => {
      uploaded.push(opts.filePath)
      opts.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { id: 1000 + uploaded.length } }) })
      return {}
    },
    getStorageSync: (key) => (key === editor.EDIT_ON_PICK_KEY ? storedEditOnPick : 'tok'),
    setStorageSync: (key, value) => { if (key === editor.EDIT_ON_PICK_KEY) storedEditOnPick = value },
    removeStorageSync: () => {},
    showToast: (opts) => toasts.push(opts && opts.title),
    showModal: () => {},
    navigateTo: (opts) => {
      navigations.push(opts.url)
      if (navigateFails && opts.fail) opts.fail({ errMsg: 'navigateTo:fail webview count limit exceed' })
    },
    navigateBack: () => {},
    redirectTo: () => {},
    switchTab: () => {},
    chooseMedia: (opts) => { chooseMediaOptions = opts; opts.success({ tempFiles: pendingPicks }) },
    chooseMessageFile: () => {}
  }
  global.getApp = () => app
  global.Page = (config) => { uploadPageConfig = config }
  require('../aigicrm-miniprogram/pages/fabushi/upload/index.js')
  assert.ok(uploadPageConfig, '发布页配置已捕获')

  const createPage = () => {
    const page = Object.create(uploadPageConfig)
    page.data = JSON.parse(JSON.stringify(uploadPageConfig.data))
    page.setData = function (patch, done) { Object.assign(this.data, patch); if (done) done() }
    page.onLoad({})
    return page
  }
  const pick = (page, files) => { pendingPicks = files; page.pickMedia(['image', 'video'], ['album']) }
  // 模拟编辑页回传: 写 globalData, 再触发发布页 onShow
  const handback = (page, requestId, result) => {
    app.globalData[editor.EDIT_RESULT_KEY] = Object.assign({ requestId }, result)
    page.onShow()
  }

  // ---- 5.1 默认开着: 选 2 张图 + 1 段视频 → 先不传, 第一张图进编辑器 ----
  let page = createPage()
  assert.strictEqual(page.data.editOnPick, true, '开关默认开')
  pick(page, [image('a.jpg'), video('v.mp4'), image('b.jpg')])
  assert.deepStrictEqual(uploaded, [], '进了编辑器就不该先把原图传上去')
  assert.strictEqual(navigations.length, 1, '只跳第一张')
  assert.ok(navigations[0].startsWith('/pages/fabushi/image-editor/index?'), '进的是图片编辑页')
  assert.strictEqual(paramOf(navigations[0], 'src'), 'wxfile://a.jpg', '编辑的是第一张图')
  assert.ok(paramOf(navigations[0], 'requestId'), '带请求号')
  assert.ok(navigations[0].indexOf('position=1') >= 0 && navigations[0].indexOf('total=2') >= 0, '带第几张 / 共几张')
  assert.strictEqual(page.data.uploading, false, '编辑期间不在上传')

  // ---- 5.2 请求号对不上的回传不取、不推进 ----
  const foreign = { requestId: 'not-mine', edited: true, path: 'wxfile://evil.jpg' }
  app.globalData[editor.EDIT_RESULT_KEY] = foreign
  page.onShow()
  assert.strictEqual(navigations.length, 1, '别人的回传不推进队列')
  assert.deepStrictEqual(uploaded, [], '别人的回传不触发上传')
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY], foreign, '别人的回传留着不动')

  // ---- 5.3 第一张裁好了 → 进第二张 ----
  handback(page, paramOf(navigations[0], 'requestId'), { edited: true, path: 'wxfile://a-crop.jpg' })
  assert.strictEqual(navigations.length, 2, '进第二张')
  assert.strictEqual(paramOf(navigations[1], 'src'), 'wxfile://b.jpg')
  assert.notStrictEqual(paramOf(navigations[1], 'requestId'), paramOf(navigations[0], 'requestId'), '每张图一个请求号')
  assert.deepStrictEqual(uploaded, [], '第二张还没处理, 仍不上传')

  // ---- 5.4 第二张用原图 → 整批按原顺序上传: 裁过的图替换原图, 视频照传 ----
  handback(page, paramOf(navigations[1], 'requestId'), { edited: false })
  await flush()
  assert.deepStrictEqual(uploaded, ['wxfile://a-crop.jpg', 'wxfile://v.mp4', 'wxfile://b.jpg'], '整批按原顺序上传, 只有裁过的换了路径')
  assert.deepStrictEqual(page.data.attachments.map((a) => a.fileType), ['image', 'video', 'image'], '附件类型不变')
  assert.deepStrictEqual(page.data.attachments.map((a) => a.name), ['图片', '视频', '图片'], '附件名称不变')
  assert.strictEqual(page.data.attachments.map((a) => a.fileId).join(','), '1001,1002,1003', '附件记录齐全')
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY], null, '回传取走后清空')
  assert.strictEqual(navigations.length, 2, '队列走完不再进编辑器')
  assert.strictEqual(page.data.attachHint, '图片 2/5 · 视频 1/5 · 语音 0/10', '附件计数提示照常更新')

  // ---- 5.5 开关: 关掉后记到本机存储, 重新进页面保持; 关着时原图直接上传 ----
  navigations.length = 0
  uploaded.length = 0
  page = createPage()
  page.toggleEditOnPick()
  assert.strictEqual(page.data.editOnPick, false, '开关关掉')
  assert.strictEqual(storedEditOnPick, false, '开关状态记到本机存储')
  pick(page, [image('c.jpg')])
  await flush()
  assert.deepStrictEqual(navigations, [], '关掉后不进编辑器')
  assert.deepStrictEqual(uploaded, ['wxfile://c.jpg'], '关掉后原图直接上传')
  page = createPage()
  assert.strictEqual(page.data.editOnPick, false, '重新进页面, 开关状态保持')
  page.toggleEditOnPick()
  assert.strictEqual(storedEditOnPick, true, '再点一下打开')

  // ---- 5.6 “其余用原图”: 后面的都不再进编辑器 ----
  navigations.length = 0
  uploaded.length = 0
  page = createPage()
  pick(page, [image('d.jpg'), image('e.jpg'), image('f.jpg')])
  handback(page, paramOf(navigations[0], 'requestId'), { edited: true, path: 'wxfile://d-crop.jpg', useOriginalForRest: true })
  await flush()
  assert.strictEqual(navigations.length, 1, '后面的图不再进编辑器')
  assert.deepStrictEqual(uploaded, ['wxfile://d-crop.jpg', 'wxfile://e.jpg', 'wxfile://f.jpg'])

  // ---- 5.7 编辑页打不开(例如页面栈已满): 这一批都退回原图, 只提示一次, 发布不卡住 ----
  navigations.length = 0
  uploaded.length = 0
  toasts.length = 0
  page = createPage()
  navigateFails = true
  pick(page, [image('g.jpg'), image('h.jpg')])
  await flush()
  navigateFails = false
  assert.strictEqual(navigations.length, 1, '打不开就不再为后面的图重试')
  assert.deepStrictEqual(uploaded, ['wxfile://g.jpg', 'wxfile://h.jpg'], '整批按原图上传')
  assert.strictEqual(toasts.filter((t) => t && t.indexOf('原图') >= 0).length, 1, '只提示一次')
  // 下一批选图照常进编辑器(不会因为上一次打不开就一直跳过)
  navigations.length = 0
  page = createPage()
  pick(page, [image('i.jpg')])
  assert.strictEqual(navigations.length, 1, '重新选图后照常进编辑器')

  // ---- 5.8 中途退出编辑页(返回键, 没按任何按钮): 按使用原图处理 ----
  navigations.length = 0
  uploaded.length = 0
  page = createPage()
  pick(page, [image('k.jpg')])
  handback(page, paramOf(navigations[0], 'requestId'), { edited: false })
  await flush()
  assert.deepStrictEqual(uploaded, ['wxfile://k.jpg'])

  // ---- 5.9 附件上限与原来一样 ----
  pendingPicks = []
  page = createPage()
  page.setData({
    attachments: [{ fileId: 1, fileType: 'image' }, { fileId: 2, fileType: 'image' }, { fileId: 3, fileType: 'image' }]
  })
  page.pickMedia(['image'], ['album'])
  assert.strictEqual(chooseMediaOptions.count, 2, '非随施图片上限 5, 已有 3 张: 最多再选 2 张')
  page.setData({ scopeType: 'suishi' })
  page.pickMedia(['image'], ['album'])
  assert.strictEqual(chooseMediaOptions.count, 6, '随施图片上限 9, 已有 3 张: 最多再选 6 张')

  // ---- 5.10 H5 手机文件回传不受影响 ----
  page = createPage()
  app.globalData.fabushiUploadFiles = [{ fileId: 9, fileType: 'image', fileName: '手机里的.jpg' }]
  page.onShow()
  assert.deepStrictEqual(page.data.attachments, [{ fileId: 9, fileType: 'image', name: '手机里的.jpg' }], 'H5 回传照常入列')
  assert.strictEqual(app.globalData.fabushiUploadFiles, null)
}

// =====================================================================
// 6. 编辑页: 在 node 下驱动真实的 pages/fabushi/image-editor/index.js
//    画布节点、触摸事件、导出接口都是桩; 记录画布上实际画了什么。
// =====================================================================
async function testEditorPage() {
  const app = { globalData: {} }
  // 取 6 位小数并去掉负零, 避免浮点尾数让断言失真
  const round = (value) => Math.round(value * 1e6) / 1e6 + 0
  const stageDraws = []      // 舞台画布上的 drawImage 参数: [x, y, 宽, 高]
  const stageFills = []      // 舞台画布上的 fillRect 参数: [x, y, 宽, 高]
  const stageScales = []     // 舞台画布坐标系缩放
  const exportDraws = []     // 导出画布上的 drawImage 参数
  const exportFills = []     // 导出画布上的铺底: { style, rect }
  const exportCalls = []     // wx.canvasToTempFilePath 的参数
  const toasts = []
  const backs = []
  let selector = ''
  let stageRect = { left: 0, top: 0 }
  let queryBroken = false
  let imageSizes = {}        // 图片地址 → [宽, 高]; 不在其中 = 加载失败
  let exportResult = 'success' // success | fail | manual(由测试决定何时回调)
  let fillStyle = ''
  const stageNode = { width: 0, height: 0 }
  const exportNode = { width: 0, height: 0 }
  const stageCtx = {
    get fillStyle() { return fillStyle },
    set fillStyle(value) { fillStyle = value },
    strokeStyle: '', lineWidth: 0,
    scale: (x, y) => stageScales.push([x, y]),
    clearRect: () => {},
    drawImage: (img, ...args) => stageDraws.push(args.map(round)),
    fillRect: (...args) => stageFills.push(args.map(round)),
    strokeRect: () => {}
  }
  const exportCtx = {
    get fillStyle() { return fillStyle },
    set fillStyle(value) { fillStyle = value },
    clearRect: () => {},
    fillRect: (...args) => exportFills.push({ style: fillStyle, rect: args.map(round) }),
    drawImage: (img, ...args) => exportDraws.push(args.map(round))
  }
  stageNode.getContext = () => stageCtx
  exportNode.getContext = () => exportCtx
  stageNode.createImage = () => {
    const image = { onload: null, onerror: null, width: 0, height: 0 }
    Object.defineProperty(image, 'src', {
      set(value) {
        setImmediate(() => {
          const size = imageSizes[value]
          if (size) {
            image.width = size[0]
            image.height = size[1]
            if (image.onload) image.onload()
          } else if (image.onerror) {
            image.onerror()
          }
        })
      }
    })
    return image
  }
  // 节点查询的回调: 舞台画布返回节点 + 尺寸 + 位置, 导出画布只返回节点
  const queryRun = (done) => {
    if (queryBroken) return done([null])
    done(selector === '#stage' ? [{ node: stageNode, width: 375, height: 600, left: stageRect.left, top: stageRect.top }] : [{ node: exportNode }])
  }
  let editorPageConfig = null
  global.wx = {
    getWindowInfo: () => ({ pixelRatio: 2, windowWidth: 375, windowHeight: 667 }),
    createSelectorQuery: () => ({
      select(target) { selector = target; return this },
      fields() { return this },
      exec: queryRun
    }),
    canvasToTempFilePath: (opts) => {
      exportCalls.push(opts)
      if (exportResult === 'success') opts.success({ tempFilePath: 'wxfile://cropped-' + exportCalls.length + '.jpg' })
      else if (exportResult === 'fail') opts.fail({ errMsg: 'canvasToTempFilePath:fail' })
    },
    showToast: (opts) => toasts.push(opts && opts.title),
    navigateBack: () => { backs.push(1) }
  }
  global.getApp = () => app
  global.Page = (config) => { editorPageConfig = config }
  require('../aigicrm-miniprogram/pages/fabushi/image-editor/index.js')
  assert.ok(editorPageConfig, '编辑页配置已捕获')

  const open = async (overrides) => {
    const page = Object.create(editorPageConfig)
    page.data = JSON.parse(JSON.stringify(editorPageConfig.data))
    page.setData = function (patch, done) { Object.assign(this.data, patch); if (done) done() }
    const query = Object.assign({ src: encodeURIComponent('wxfile://p.jpg'), requestId: 'r-1', position: '1', total: '1' }, overrides || {})
    page.onLoad(query)
    page.onReady()
    await flush()
    return page
  }
  const touch = (points, at) => ({ touches: points.map(([x, y]) => ({ clientX: x, clientY: y })), changedTouches: [], timeStamp: at })
  const tap = (page, x, y, at) => {
    page.onTouchStart(touch([[x, y]], at))
    page.onTouchEnd(touch([], at + 40))
  }
  const pick = (object, keys) => keys.reduce((out, key) => { out[key] = object[key]; return out }, {})
  const WHOLE = [0, 50, 375, 500] // 竖图 3000×4000 在 375×600 舞台上的整图位置

  // ---- 6.1 进页: 图片加载后整图显示; 画布按像素比放大; 只有裁剪框外压暗 ----
  imageSizes = { 'wxfile://p.jpg': [3000, 4000] }
  let page = await open()
  assert.strictEqual(page.data.ready, true, '图片加载完成后就绪')
  assert.strictEqual(page.data.ratioKey, 'original', '默认原比例')
  assert.deepStrictEqual([page.data.position, page.data.total], [1, 1], '第几张 / 共几张')
  assert.strictEqual(page.data.showUseOriginalForRest, false, '只有一张图时不显示“其余用原图”')
  assert.deepStrictEqual([stageNode.width, stageNode.height], [750, 1200], '舞台画布的像素尺寸 = 逻辑尺寸 × 像素比')
  assert.deepStrictEqual(stageScales.at(-1), [2, 2], '绘制前把坐标系按像素比缩放')
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '竖图整图显示、水平垂直居中')
  assert.deepStrictEqual({ canZoomIn: page.data.canZoomIn, canZoomOut: page.data.canZoomOut }, { canZoomIn: true, canZoomOut: false }, '初始可以放大、不能缩小')
  assert.deepStrictEqual(stageFills.slice(-2), [[0, 0, 375, 50], [0, 550, 375, 50]], '原比例时裁剪框就是整张图: 只压暗图片上下的空白')
  assert.strictEqual(stageFills.length, 2, '左右没有空白就不画')

  // ---- 6.2 比例与按钮 ----
  page.onRatio({ currentTarget: { dataset: { key: '1:1' } } })
  assert.strictEqual(page.data.ratioKey, '1:1', '切换到 1:1')
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '换成 1:1 后图片回到整图位置(刚好盖住)')
  assert.deepStrictEqual(stageFills.slice(-4), [[0, 0, 375, 138.75], [0, 461.25, 375, 138.75], [0, 138.75, 26.25, 322.5], [348.75, 138.75, 26.25, 322.5]], '裁剪框外的上、下、左、右压暗')
  page.onZoomIn()
  assert.deepStrictEqual(stageDraws.at(-1), [-93.75, -75, 562.5, 750], '放大按钮: 以裁剪框中心放大 1.5 倍')
  assert.strictEqual(page.data.canZoomOut, true, '放大后可以缩小')
  page.onZoomOut()
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '缩小按钮: 回到不放大')
  // 竖图比 1:1 框宽, 整图状态下还能再缩, 缩到图片刚好盖住裁剪框(322.5 × 430)为止
  assert.strictEqual(page.data.canZoomOut, true, '还能再缩')
  page.onZoomOut()
  assert.deepStrictEqual(stageDraws.at(-1), [26.25, 85, 322.5, 430], '缩到刚好盖住裁剪框')
  assert.strictEqual(page.data.canZoomOut, false, '缩到下限就不能再缩')
  page.onZoomIn()
  page.onReset()
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '重置回到初始')
  assert.strictEqual(page.data.canZoomOut, true, '重置后回到整图, 仍可再缩到刚好盖住')

  // ---- 6.3 触摸: 单指拖动; 双指张开 ----
  page.onZoomIn()
  page.onTouchStart(touch([[300, 300]], 1000))
  page.onTouchMove(touch([[320, 300]], 1016))
  assert.deepStrictEqual(stageDraws.at(-1), [-73.75, -75, 562.5, 750], '单指向右拖 20: 图片右移 20')
  page.onTouchEnd(touch([], 1040))
  assert.deepStrictEqual(stageDraws.at(-1), [-73.75, -75, 562.5, 750], '拖动后抬起不改变')
  page.onTouchStart(touch([[100, 100], [200, 100]], 2000))
  page.onTouchMove(touch([[50, 100], [250, 100]], 2016))
  assert.deepStrictEqual(stageDraws.at(-1), [-297.5, -250, 1125, 1500], '双指间距从 100 张到 200: 以中点放大一倍')
  page.onTouchEnd(touch([], 2060))
  page.onTouchCancel(touch([], 2061))
  assert.deepStrictEqual(stageDraws.at(-1), [-297.5, -250, 1125, 1500], '取消事件不改变')
  page.onReset()

  // ---- 6.4 双击: 没放大 → 放大; 已放大 → 回到整图 ----
  // 此页仍是 1:1; 双击的基线是初始缩放 1, 放大到 2.5 倍 → 图片 937.5 × 1250, 以框中心(187.5, 300)居中
  tap(page, 100, 200, 3000)
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '单击不改变')
  tap(page, 102, 203, 3150)
  assert.deepStrictEqual(stageDraws.at(-1), [-281.25, -325, 937.5, 1250], '双击: 以裁剪框中心放大到 2.5 倍')
  tap(page, 102, 203, 6000)
  assert.deepStrictEqual(stageDraws.at(-1), [-281.25, -325, 937.5, 1250], '隔了很久的单击不改变')
  tap(page, 102, 203, 6150)
  assert.deepStrictEqual(stageDraws.at(-1), WHOLE, '再双击: 回到整图')
  // ---- 6.5 触摸坐标要先减去画布在页面里的位置 ----
  // 单指拖动只用到位移量, 偏移在相减时抵消, 测不出“没减偏移”; 双指缩放的锚点抵消不掉, 用它来验。
  // 画布在页面里位于 (8, 10): 页面坐标的两指 (108,110)(208,110) 即画布坐标 (100,100)(200,100),
  // 张开到画布坐标 (50,100)(250,100) → 倍率 2, 锚点 (150,100): 左上角 (150-243.75×2, 100-175×2)
  stageRect = { left: 8, top: 10 }
  page = await open({ requestId: 'r-offset' })
  page.onRatio({ currentTarget: { dataset: { key: '1:1' } } })
  page.onZoomIn()
  page.onTouchStart(touch([[108, 110], [208, 110]], 0))
  page.onTouchMove(touch([[58, 110], [258, 110]], 16))
  assert.deepStrictEqual(stageDraws.at(-1), [-337.5, -250, 1125, 1500], '按画布内的坐标计算缩放锚点')
  stageRect = { left: 0, top: 0 }

  // ---- 6.6 确定: 从原图取裁剪框对应的区域, 画到导出画布, 导出 JPG, 回传后返回 ----
  page = await open()
  page.onRatio({ currentTarget: { dataset: { key: '1:1' } } })
  page.onZoomIn()
  exportResult = 'manual'
  exportCalls.length = 0
  exportDraws.length = 0
  exportFills.length = 0
  backs.length = 0
  app.globalData[editor.EDIT_RESULT_KEY] = null
  page.onConfirm()
  assert.strictEqual(page.data.exporting, true, '导出中')
  // 1:1 放大 1.5 倍: 裁剪框对应原图 (640,1140) 起 1720×1720 → 超过长边 1600, 压到 1600×1600
  assert.deepStrictEqual([page.data.exportWidth, page.data.exportHeight], [1600, 1600], '导出画布的显示尺寸')
  assert.deepStrictEqual([exportNode.width, exportNode.height], [1600, 1600], '导出画布的像素尺寸')
  assert.deepStrictEqual(exportFills, [{ style: '#ffffff', rect: [0, 0, 1600, 1600] }], '先铺白底(透明区域导出成 JPG 时不发黑)')
  assert.deepStrictEqual(exportDraws, [[640, 1140, 1720, 1720, 0, 0, 1600, 1600]], '从原图取裁剪区域, 画满导出画布')
  assert.strictEqual(exportCalls.length, 1, '调用一次导出')
  assert.deepStrictEqual(pick(exportCalls[0], ['x', 'y', 'width', 'height', 'destWidth', 'destHeight']), { x: 0, y: 0, width: 1600, height: 1600, destWidth: 1600, destHeight: 1600 }, '导出整块画布, 输出 1600×1600')
  assert.deepStrictEqual(pick(exportCalls[0], ['fileType', 'quality']), { fileType: 'jpg', quality: 0.9 }, '导出 JPG')
  assert.strictEqual(exportCalls[0].canvas, exportNode, '导出的是导出画布')
  page.onConfirm()
  assert.strictEqual(exportCalls.length, 1, '导出进行中不重复导出')
  assert.strictEqual(backs.length, 0, '导出完成前不返回')
  exportCalls[0].success({ tempFilePath: 'wxfile://cropped.jpg' })
  assert.strictEqual(page.data.exporting, false, '导出结束')
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-1', edited: true, path: 'wxfile://cropped.jpg' }, '回传裁剪结果')
  assert.deepStrictEqual(backs, [1], '返回发布页')
  page.onUnload()
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY].edited, true, '返回后的卸载不覆盖已有结果')
  assert.deepStrictEqual(backs, [1], '不重复返回')

  // ---- 6.7 原比例不动手直接确定 = 整张图压到长边 1600 ----
  exportResult = 'success'
  page = await open({ requestId: 'r-full' })
  exportDraws.length = 0
  page.onConfirm()
  assert.deepStrictEqual(exportDraws.at(-1), [0, 0, 3000, 4000, 0, 0, 1200, 1600], '整张原图画到 1200×1600')
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-full', edited: true, path: 'wxfile://cropped-' + exportCalls.length + '.jpg' })

  // ---- 6.8 使用原图: 不导出、不带路径 ----
  exportCalls.length = 0
  backs.length = 0
  app.globalData[editor.EDIT_RESULT_KEY] = null
  page = await open({ requestId: 'r-orig' })
  page.onUseOriginal()
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-orig', edited: false }, '使用原图: 只报“没编辑”')
  assert.strictEqual(exportCalls.length, 0, '使用原图不导出')
  assert.deepStrictEqual(backs, [1], '直接返回')

  // ---- 6.9 多张图: 显示“其余用原图” ----
  page = await open({ requestId: 'r-2', position: '1', total: '3' })
  assert.strictEqual(page.data.showUseOriginalForRest, true, '多张图时显示“其余用原图”')
  assert.deepStrictEqual([page.data.position, page.data.total], [1, 3])
  page.onUseOriginalForRest()
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-2', edited: false, useOriginalForRest: true }, '其余用原图')

  // ---- 6.10 直接返回(导航栏返回键 / 侧滑): 这一张按原图处理 ----
  app.globalData[editor.EDIT_RESULT_KEY] = null
  backs.length = 0
  page = await open({ requestId: 'r-3' })
  page.onUnload()
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-3', edited: false }, '直接返回 = 使用原图')
  assert.deepStrictEqual(backs, [], '卸载时不再调用返回')

  // ---- 6.11 导出失败: 按钮恢复、可以重试; 不回传、不返回 ----
  app.globalData[editor.EDIT_RESULT_KEY] = null
  backs.length = 0
  toasts.length = 0
  exportResult = 'fail'
  page = await open({ requestId: 'r-4' })
  page.onConfirm()
  assert.strictEqual(page.data.exporting, false, '导出失败后恢复可操作')
  assert.ok(toasts.some((title) => title && title.indexOf('失败') >= 0), '提示失败')
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY], null, '导出失败不回传')
  assert.deepStrictEqual(backs, [], '导出失败不返回')
  exportResult = 'success'
  page.onConfirm()
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY].edited, true, '重试成功')

  // ---- 6.12 图片读不出来: 这一张用原图并返回, 发布页不卡住 ----
  app.globalData[editor.EDIT_RESULT_KEY] = null
  backs.length = 0
  toasts.length = 0
  page = await open({ src: encodeURIComponent('wxfile://broken.jpg'), requestId: 'r-5' })
  assert.strictEqual(page.data.ready, false, '没有就绪')
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-5', edited: false }, '读不出来 = 使用原图')
  assert.deepStrictEqual(backs, [1], '返回发布页')
  assert.strictEqual(toasts.length, 1, '提示一次')

  // ---- 6.13 画布节点取不到: 同样按原图处理并返回 ----
  queryBroken = true
  app.globalData[editor.EDIT_RESULT_KEY] = null
  backs.length = 0
  page = await open({ requestId: 'r-6' })
  queryBroken = false
  assert.deepStrictEqual(app.globalData[editor.EDIT_RESULT_KEY], { requestId: 'r-6', edited: false }, '画布取不到 = 使用原图')
  assert.deepStrictEqual(backs, [1])

  // ---- 6.14 缺少图片地址或请求号: 不写回传, 直接返回 ----
  app.globalData[editor.EDIT_RESULT_KEY] = null
  backs.length = 0
  await open({ src: '', requestId: 'r-7' })
  await open({ src: encodeURIComponent('wxfile://p.jpg'), requestId: '' })
  await new Promise((resolve) => setTimeout(resolve, 450))
  assert.strictEqual(app.globalData[editor.EDIT_RESULT_KEY], null, '没有请求号就不写回传')
  assert.deepStrictEqual(backs, [1, 1], '各返回一次')
}

testUploadPageWiring()
  .then(() => testEditorPage())
  .then(() => {
    console.log('PASS: test-miniprogram-image-editor')
  }, (err) => {
    console.error(err)
    process.exit(1)
  })

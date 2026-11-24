// 【GL-12 2026-11-24】法布施图片编辑(放大查看 + 裁剪 + 标注): 几何、手势、编辑队列
// 纯函数, 零 wx 依赖; 画布与触摸事件由 pages/fabushi/image-editor 接线。
//
// 坐标约定(全部是舞台内的逻辑像素):
//   fit        图片等比适配舞台后的尺寸(scale = 1 时的显示尺寸)
//   frame      裁剪框在舞台里的位置, 固定不动
//   transform  { x, y, scale }: 图片左上角画在 (x, y), 实际显示尺寸 = fit × scale
// 不变量(由 clampTransform 保证): 图片始终完整盖住裁剪框, scale 不低于盖住所需的最小值。

const MAX_EXPORT_EDGE = 1600
const MAX_SCALE = 5
// 裁剪框四周留白占舞台的比例(每侧)
const FRAME_INSET = 0.07
// 判定“点了一下”的最大位移与最长按压; 两次点击小于这个间隔算双击
const TAP_SLOP = 10
const TAP_MAX_MS = 300
const DOUBLE_TAP_MS = 350

const RATIOS = [
  { key: 'original', label: '原比例', value: null },
  { key: '1:1', label: '1:1', value: 1 },
  { key: '4:3', label: '4:3', value: 4 / 3 },
  { key: '3:4', label: '3:4', value: 3 / 4 },
  { key: '16:9', label: '16:9', value: 16 / 9 }
]

function ratioValue(ratioKey) {
  const preset = RATIOS.find((item) => item.key === ratioKey)
  return preset ? preset.value : null
}

function fitContain(imageWidth, imageHeight, boxWidth, boxHeight) {
  const scale = Math.min(boxWidth / imageWidth, boxHeight / imageHeight)
  return { width: imageWidth * scale, height: imageHeight * scale }
}

function makeFrame(ratioKey, imageWidth, imageHeight, view) {
  const value = ratioValue(ratioKey)
  const fit = fitContain(imageWidth, imageHeight, view.width, view.height)
  // 原比例: 裁剪框就是整张图, 不动手直接确定 = 保留全图(只压到导出上限)
  if (value == null) {
    return { x: (view.width - fit.width) / 2, y: (view.height - fit.height) / 2, width: fit.width, height: fit.height }
  }
  const availW = view.width * (1 - FRAME_INSET * 2)
  const availH = view.height * (1 - FRAME_INSET * 2)
  let width = availW
  if (width / value > availH) width = availH * value
  const height = width / value
  return { x: (view.width - width) / 2, y: (view.height - height) / 2, width, height }
}

function minScale(fit, frame) {
  return Math.max(frame.width / fit.width, frame.height / fit.height)
}

// 初始缩放: 整图显示; 整图盖不住裁剪框时才放大到刚好盖住
function baseScale(fit, frame) {
  return Math.max(1, minScale(fit, frame))
}

// 沿一个轴摆放图片: 图片比框长就夹在 [贴右沿, 贴左沿] 之间; 比框短(只在缩放被夹住的过程中出现)就居中
function placeAxis(pos, imageExtent, frameStart, frameExtent) {
  if (imageExtent < frameExtent) return frameStart + (frameExtent - imageExtent) / 2
  const low = frameStart + frameExtent - imageExtent
  const high = frameStart
  return Math.min(Math.max(pos, low), high)
}

function clampTransform(transform, fit, frame) {
  const min = minScale(fit, frame)
  // 上限至少取到盖住裁剪框所需的倍数(全景图配 1:1 时需要的放大倍数可能超过 5)
  const scale = Math.min(Math.max(transform.scale, min), Math.max(MAX_SCALE, min))
  const oldW = fit.width * transform.scale
  const oldH = fit.height * transform.scale
  const width = fit.width * scale
  const height = fit.height * scale
  return {
    x: placeAxis(transform.x + (oldW - width) / 2, width, frame.x, frame.width),
    y: placeAxis(transform.y + (oldH - height) / 2, height, frame.y, frame.height),
    scale
  }
}

function initialTransform(fit, frame) {
  const scale = baseScale(fit, frame)
  return clampTransform({
    x: frame.x + frame.width / 2 - (fit.width * scale) / 2,
    y: frame.y + frame.height / 2 - (fit.height * scale) / 2,
    scale
  }, fit, frame)
}

function applyPan(transform, dx, dy, fit, frame) {
  return clampTransform({ x: transform.x + dx, y: transform.y + dy, scale: transform.scale }, fit, frame)
}

// 以 anchor(舞台坐标)为不动点缩放: 该点压着的图片内容缩放前后是同一处
function applyZoom(transform, factor, anchor, fit, frame) {
  const scale = transform.scale * factor
  const ratio = scale / transform.scale
  return clampTransform({
    x: anchor.x - (anchor.x - transform.x) * ratio,
    y: anchor.y - (anchor.y - transform.y) * ratio,
    scale
  }, fit, frame)
}

// 裁剪框映射回原图像素。返回整数矩形, 保证落在原图内且至少 1 像素。
function cropSourceRect(transform, fit, frame, imageWidth, imageHeight) {
  const shownW = fit.width * transform.scale
  const shownH = fit.height * transform.scale
  const x = Math.min(Math.max(Math.round((frame.x - transform.x) / shownW * imageWidth), 0), imageWidth - 1)
  const y = Math.min(Math.max(Math.round((frame.y - transform.y) / shownH * imageHeight), 0), imageHeight - 1)
  const right = Math.min(Math.max(Math.round((frame.x + frame.width - transform.x) / shownW * imageWidth), x + 1), imageWidth)
  const bottom = Math.min(Math.max(Math.round((frame.y + frame.height - transform.y) / shownH * imageHeight), y + 1), imageHeight)
  return { x, y, width: right - x, height: bottom - y }
}

// 导出尺寸: 长边超过上限才缩, 小图不放大
function exportSize(srcWidth, srcHeight, maxEdge) {
  const limit = maxEdge == null ? MAX_EXPORT_EDGE : maxEdge
  const longest = Math.max(srcWidth, srcHeight)
  const scale = longest > limit ? limit / longest : 1
  return { width: Math.max(1, Math.round(srcWidth * scale)), height: Math.max(1, Math.round(srcHeight * scale)) }
}

// ---------------- 手势 ----------------
// 页面把每次触摸事件的触摸点([{ x, y }], 舞台坐标)按序交给这里; 手势对象由页面持有。
// 返回的 delta 是“相邻两帧之间”的变化, 交给 applyGesture 落到图片上。

const DOUBLE_TAP_SLOP = 40
const DOUBLE_TAP_SCALE = 2.5

function snapshot(points) {
  return (points || []).map((point) => ({ x: point.x, y: point.y }))
}

function createGesture() {
  return { points: [], startedAt: 0, travelled: 0, lastX: 0, lastY: 0, lastTapAt: -Infinity, lastTapX: 0, lastTapY: 0 }
}

// 按下或中途又按下一根手指: 重新锚定
function gestureStart(gesture, points, now) {
  gesture.points = snapshot(points)
  gesture.startedAt = now
  gesture.travelled = 0
  if (gesture.points.length !== 1) {
    gesture.travelled = Infinity
    return
  }
  // 原地按下就抬起(没有 move)也要有位置, 双击判定靠它
  gesture.lastX = gesture.points[0].x
  gesture.lastY = gesture.points[0].y
}

function gestureMove(gesture, points, now) {
  const prev = gesture.points
  const cur = snapshot(points)
  if (prev.length > 1 || cur.length > 1) gesture.travelled = Infinity
  // 手指数变了(多一根 / 少一根 / 全抬起): 两帧的点对不上, 重新锚定且这一帧不产生位移
  if (!cur.length || cur.length !== prev.length) {
    gesture.points = cur
    return null
  }
  let delta
  if (cur.length === 1) {
    gesture.travelled += Math.hypot(cur[0].x - prev[0].x, cur[0].y - prev[0].y)
    delta = { panX: cur[0].x - prev[0].x, panY: cur[0].y - prev[0].y, factor: 1, anchor: { x: cur[0].x, y: cur[0].y } }
  } else {
    const prevDist = Math.hypot(prev[1].x - prev[0].x, prev[1].y - prev[0].y)
    const curDist = Math.hypot(cur[1].x - cur[0].x, cur[1].y - cur[0].y)
    delta = {
      panX: (cur[0].x + cur[1].x - prev[0].x - prev[1].x) / 2,
      panY: (cur[0].y + cur[1].y - prev[0].y - prev[1].y) / 2,
      factor: prevDist > 0 ? curDist / prevDist : 1,
      anchor: { x: (cur[0].x + cur[1].x) / 2, y: (cur[0].y + cur[1].y) / 2 }
    }
  }
  gesture.points = cur
  if (cur.length === 1) {
    gesture.lastX = cur[0].x
    gesture.lastY = cur[0].y
  }
  return delta
}

// 全部手指抬起时返回 { doubleTap }; 还有手指按着则重新锚定并返回 null
function gestureEnd(gesture, points, now) {
  const cur = snapshot(points)
  if (gesture.points.length > 1 || cur.length > 1) gesture.travelled = Infinity
  gesture.points = cur
  if (cur.length) return null
  // 只有一根手指、没怎么动、按得很快, 才算一次点击
  if (gesture.travelled > TAP_SLOP || now - gesture.startedAt > TAP_MAX_MS) return { doubleTap: false }
  const nearLastTap = now - gesture.lastTapAt <= DOUBLE_TAP_MS
    && Math.abs(gesture.lastX - gesture.lastTapX) <= DOUBLE_TAP_SLOP
    && Math.abs(gesture.lastY - gesture.lastTapY) <= DOUBLE_TAP_SLOP
  if (nearLastTap) {
    gesture.lastTapAt = -Infinity
    return { doubleTap: true }
  }
  gesture.lastTapAt = now
  gesture.lastTapX = gesture.lastX
  gesture.lastTapY = gesture.lastY
  return { doubleTap: false }
}

function applyGesture(transform, delta, fit, frame) {
  const panned = applyPan(transform, delta.panX, delta.panY, fit, frame)
  return applyZoom(panned, delta.factor, delta.anchor, fit, frame)
}

// 是否处于放大状态: 以初始缩放为基线(不是盖住框的下限——竖图配 1:1 时下限 0.86, 初始是 1)
function isZoomedIn(transform, fit, frame) {
  return transform.scale > baseScale(fit, frame) * 1.01
}

// 双击: 没放大就以裁剪框中心放大到初始的 2.5 倍; 已放大就回到初始
function toggleZoom(transform, fit, frame) {
  if (isZoomedIn(transform, fit, frame)) return initialTransform(fit, frame)
  const anchor = { x: frame.x + frame.width / 2, y: frame.y + frame.height / 2 }
  return applyZoom(transform, (baseScale(fit, frame) * DOUBLE_TAP_SCALE) / transform.scale, anchor, fit, frame)
}

// ---------------- 标注(划线 / 方框 / 圈) ----------------
// 标注记在“原图像素坐标”里, 与查看时的缩放、平移、裁剪比例都无关: 换比例、放大之后位置照旧跟着图片走;
// 导出时跟着裁剪区域一起平移、缩放, 完全落在裁剪区域之外的标注不导出。
//   free     徒手线: points 是原图像素坐标的折线
//   rect     方框:   points 是对角两点
//   ellipse  圈:     points 是外接矩形的对角两点
// size 是线宽, 同样用原图像素表示(按原图长边的比例取): 图大线粗, 导出之后粗细关系不变。
// 工具 none = 浏览(只拖动、缩放, 不画)。工具、颜色、线宽的清单只有这里一份, 页面放进 data 供模板渲染。
// 【GL-12 2026-11-25 补】原型 v7 把 MP 标注的范围写作“待裁(Q9)”, 已批准的计划正文定为划线 / 圈 / 框。

const MARK_TOOLS = [
  { key: 'none', label: '浏览' },
  { key: 'free', label: '划线' },
  { key: 'rect', label: '方框' },
  { key: 'ellipse', label: '圈' }
]
const MARK_COLORS = [
  { key: 'red', label: '红', value: '#e8412c' },
  { key: 'yellow', label: '黄', value: '#f7c948' },
  { key: 'green', label: '绿', value: '#2fbf71' },
  { key: 'blue', label: '蓝', value: '#2f80ed' },
  { key: 'white', label: '白', value: '#ffffff' }
]
const MARK_WIDTHS = [
  { key: 'thin', label: '细', ratio: 0.004 },
  { key: 'medium', label: '中', ratio: 0.008 },
  { key: 'thick', label: '粗', ratio: 0.016 }
]
// 一条徒手线最多记多少个点(防止长按乱画拖慢重绘)
const MAX_FREE_POINTS = 400
// 徒手线相邻两点的最小间距(舞台逻辑像素): 比它近的不记
const MIN_POINT_GAP = 1.5
// 成笔的最小长度(舞台逻辑像素): 更短的是点了一下或手抖, 不算标注
const MIN_STROKE_EXTENT = 6

function markColorValue(colorKey) {
  const preset = MARK_COLORS.find((item) => item.key === colorKey)
  return preset ? preset.value : MARK_COLORS[0].value
}

// 线宽的原图像素数; 认不出的线宽按“中”, 再小也至少 1 像素
function markWidthSize(widthKey, imageLongEdge) {
  const preset = MARK_WIDTHS.find((item) => item.key === widthKey) || MARK_WIDTHS[1]
  return Math.max(1, Math.round(preset.ratio * imageLongEdge))
}

function toolLabel(tool) {
  const preset = MARK_TOOLS.find((item) => item.key === tool)
  return preset ? preset.label : ''
}

function isMarkTool(tool) {
  return tool === 'free' || tool === 'rect' || tool === 'ellipse'
}

// ---------------- 坐标换算: 舞台 ↔ 原图像素 ----------------

// 舞台 1 逻辑像素等于多少原图像素(随缩放变化)
function imagePixelsPerStagePixel(transform, fit, source) {
  return source.width / (fit.width * transform.scale)
}

// 两个方向分开算: 图片显示出来是 fit × scale, 竖图的宽和高不一样, 纵轴要用 fit.height。
// (第一版纵轴也用了 fit.width: 舞台上画的时候一来一回正好抵消看不出来, 导出的标注却是偏的。)
function stageToImage(point, transform, fit, source) {
  return {
    x: (point.x - transform.x) / (fit.width * transform.scale) * source.width,
    y: (point.y - transform.y) / (fit.height * transform.scale) * source.height
  }
}

function imageToStage(point, transform, fit, source) {
  return {
    x: transform.x + point.x / source.width * fit.width * transform.scale,
    y: transform.y + point.y / source.height * fit.height * transform.scale
  }
}

function pointInRect(point, rect) {
  return point.x >= rect.x && point.x <= rect.x + rect.width && point.y >= rect.y && point.y <= rect.y + rect.height
}

// ---------------- 一笔标注 ----------------

// 坐标保持精确, 不取整: 取整会让标注落笔的位置偏一点点, 换算回舞台时对不上手指的位置。
function startStroke(tool, color, size, point) {
  return { tool, color, size, points: [{ x: point.x, y: point.y }] }
}

// 徒手线追加点: 离上一点不足 gap(原图像素)的不记, 记满上限为止; 方框与圈只有“起点 + 当前点”。
// 没有变化时原样返回传入的那个对象(页面据此跳过重绘)。
function extendStroke(stroke, point, gap) {
  if (!stroke || !point || !stroke.points.length) return stroke
  const last = stroke.points[stroke.points.length - 1]
  const next = { x: point.x, y: point.y }
  if (stroke.tool !== 'free') {
    if (last.x === next.x && last.y === next.y) return stroke
    return Object.assign({}, stroke, { points: [stroke.points[0], next] })
  }
  if (stroke.points.length >= MAX_FREE_POINTS || Math.hypot(next.x - last.x, next.y - last.y) < gap) return stroke
  return Object.assign({}, stroke, { points: stroke.points.concat([next]) })
}

// 标注的外包矩形(原图像素); 方框与圈就是它本身
function shapeOf(stroke) {
  const points = (stroke && stroke.points) || []
  if (points.length === 1) return { x: points[0].x, y: points[0].y, width: 0, height: 0 }
  if (!points.length) return { x: 0, y: 0, width: 0, height: 0 }
  if (stroke.tool !== 'free') {
    return {
      x: Math.min(points[0].x, points[1].x),
      y: Math.min(points[0].y, points[1].y),
      width: Math.abs(points[1].x - points[0].x),
      height: Math.abs(points[1].y - points[0].y)
    }
  }
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  points.forEach((item) => {
    minX = Math.min(minX, item.x)
    minY = Math.min(minY, item.y)
    maxX = Math.max(maxX, item.x)
    maxY = Math.max(maxY, item.y)
  })
  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY }
}

// 徒手线量总长, 方框与圈量长边: 都用来判断“这一笔够不够长”
function strokeLength(stroke) {
  if (!stroke || !stroke.points) return 0
  if (stroke.tool !== 'free') return Math.max(shapeOf(stroke).width, shapeOf(stroke).height)
  let total = 0
  for (let i = 1; i < stroke.points.length; i++) {
    total += Math.hypot(stroke.points[i].x - stroke.points[i - 1].x, stroke.points[i].y - stroke.points[i - 1].y)
  }
  return total
}

// 抬笔: 够长的原样返回, 否则 null(不算标注)
function finishStroke(stroke, minExtent) {
  if (!stroke || !isMarkTool(stroke.tool) || !stroke.points || stroke.points.length < 2) return null
  return strokeLength(stroke) >= minExtent ? stroke : null
}

// ---------------- 标注的增、删、平移、筛(都返回新数组, 不改动传入的) ----------------

function addStroke(strokes, stroke) {
  return (strokes || []).concat([stroke])
}

function undoStroke(strokes) {
  return (strokes || []).slice(0, -1)
}

// 导出前的平移: 原图坐标 → 以裁剪区域左上角为原点
function shiftStrokes(strokes, dx, dy) {
  return (strokes || []).map((stroke) => Object.assign({}, stroke, {
    points: stroke.points.map((point) => ({ x: point.x - dx, y: point.y - dy }))
  }))
}

// 与裁剪区域有交集的标注(刚好贴边也算)
function strokesInRect(strokes, rect) {
  return (strokes || []).filter((stroke) => {
    const box = shapeOf(stroke)
    return box.x <= rect.x + rect.width && box.x + box.width >= rect.x
      && box.y <= rect.y + rect.height && box.y + box.height >= rect.y
  })
}

// ---------------- 画出来 ----------------

// 画一个圈: 画布有 ellipse 接口就用; 没有(旧基础库)用四段三阶贝塞尔曲线逼近
function ellipsePath(ctx, cx, cy, rx, ry) {
  if (typeof ctx.ellipse === 'function') {
    ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2)
    return
  }
  const k = 0.5522847498307936
  ctx.moveTo(cx + rx, cy)
  ctx.bezierCurveTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
  ctx.bezierCurveTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
  ctx.bezierCurveTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
  ctx.bezierCurveTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
}

// 把一条标注画到画布上。toCanvas: 原图像素 → 画布坐标; scale: 画布像素 / 原图像素(线宽按它换算)。
// 只负责这一条的路径与描边, 不处理裁剪与状态保存。
function drawStroke(ctx, stroke, toCanvas, scale) {
  if (!stroke || !isMarkTool(stroke.tool) || !stroke.points || !stroke.points.length) return
  ctx.strokeStyle = markColorValue(stroke.color)
  ctx.lineWidth = Math.max(1, stroke.size * scale)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  if (stroke.tool === 'free') {
    const first = toCanvas(stroke.points[0])
    ctx.moveTo(first.x, first.y)
    // 只有一个点时线段两端重合, 配上圆头就是一个点
    if (stroke.points.length === 1) ctx.lineTo(first.x, first.y)
    for (let i = 1; i < stroke.points.length; i++) {
      const at = toCanvas(stroke.points[i])
      ctx.lineTo(at.x, at.y)
    }
  } else {
    const shape = shapeOf(stroke)
    const a = toCanvas({ x: shape.x, y: shape.y })
    const b = toCanvas({ x: shape.x + shape.width, y: shape.y + shape.height })
    if (stroke.tool === 'ellipse') {
      ellipsePath(ctx, (a.x + b.x) / 2, (a.y + b.y) / 2, (b.x - a.x) / 2, (b.y - a.y) / 2)
    } else {
      ctx.rect(a.x, a.y, b.x - a.x, b.y - a.y)
    }
  }
  ctx.stroke()
}

// 导出时的换算: 裁剪区域 rect(原图像素)画满 size(导出像素)
function exportMapper(rect, size) {
  const scale = size.width / rect.width
  return {
    scale,
    toCanvas: (point) => ({ x: (point.x - rect.x) * scale, y: (point.y - rect.y) * scale })
  }
}

// ---------------- 编辑队列 ----------------
// 发布页选完图 → createEditQueue 排出要进编辑器的图片 → 逐张打开编辑页 →
// 编辑页把结果写进 app.globalData[EDIT_RESULT_KEY] 并返回 → 发布页 onShow 用 takeEditResult 取走 →
// applyEditResult 推进队列。队列走完后发布页把整批文件(含没进编辑器的视频)交给原有的上传。

// 本机存储键: “选图后裁剪”开关
const EDIT_ON_PICK_KEY = 'fabushi_image_edit_on_pick'
// 回传所用的 globalData 键(与 H5 手机文件回传 fabushiUploadFiles 同一做法)
const EDIT_RESULT_KEY = 'fabushiImageEditResult'

function createEditQueue(files, editOnPick) {
  const list = files || []
  const targets = editOnPick
    ? list.map((file, index) => (file.type === 'image' ? index : -1)).filter((index) => index >= 0)
    : []
  return { files: list.slice(), targets, cursor: 0 }
}

// 当前该进编辑器的文件下标; 队列走完返回 -1
function currentEditTarget(queue) {
  const target = queue.targets[queue.cursor]
  return target === undefined ? -1 : target
}

// 返回推进后的新队列, 不改动传入的队列与文件对象
function applyEditResult(queue, result) {
  const files = queue.files.slice()
  const target = currentEditTarget(queue)
  let cursor = queue.cursor
  if (target >= 0 && result && result.edited && result.path) {
    files[target] = Object.assign({}, files[target], { path: result.path })
  }
  if (result && result.useOriginalForRest) cursor = queue.targets.length
  else if (target >= 0) cursor += 1
  return { files, targets: queue.targets, cursor }
}

// 取走属于本次请求的回传并清空; 请求号对不上则不取、不动
function takeEditResult(globalData, requestId) {
  const result = globalData[EDIT_RESULT_KEY]
  if (!result || !requestId || result.requestId !== requestId) return null
  globalData[EDIT_RESULT_KEY] = null
  return result
}

// 从存储读回“选图后裁剪”开关; 从未设置过默认开
function readEditOnPick(stored) {
  if (stored === '' || stored == null) return true
  return stored === true
}

module.exports = {
  RATIOS,
  MAX_EXPORT_EDGE,
  MAX_SCALE,
  EDIT_ON_PICK_KEY,
  EDIT_RESULT_KEY,
  fitContain,
  makeFrame,
  minScale,
  clampTransform,
  initialTransform,
  applyPan,
  applyZoom,
  cropSourceRect,
  exportSize,
  createGesture,
  gestureStart,
  gestureMove,
  gestureEnd,
  applyGesture,
  isZoomedIn,
  toggleZoom,
  MARK_TOOLS,
  MARK_COLORS,
  MARK_WIDTHS,
  MAX_FREE_POINTS,
  MIN_POINT_GAP,
  MIN_STROKE_EXTENT,
  markColorValue,
  markWidthSize,
  toolLabel,
  isMarkTool,
  imagePixelsPerStagePixel,
  stageToImage,
  imageToStage,
  pointInRect,
  startStroke,
  extendStroke,
  shapeOf,
  strokeLength,
  finishStroke,
  addStroke,
  undoStroke,
  shiftStrokes,
  strokesInRect,
  drawStroke,
  exportMapper,
  createEditQueue,
  currentEditTarget,
  applyEditResult,
  takeEditResult,
  readEditOnPick
}

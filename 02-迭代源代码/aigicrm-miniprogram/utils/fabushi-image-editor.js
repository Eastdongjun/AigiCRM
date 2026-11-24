// 【GL-12 2026-11-24】法布施图片编辑(放大查看 + 裁剪): 几何、手势、编辑队列
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
  createEditQueue,
  currentEditTarget,
  applyEditResult,
  takeEditResult,
  readEditOnPick
}

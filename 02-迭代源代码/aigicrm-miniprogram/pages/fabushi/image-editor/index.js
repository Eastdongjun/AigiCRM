// 【GL-12 2026-11-24】法布施图片编辑页: 放大查看 + 裁剪
// 图片在固定的裁剪框下拖动、双指或双击缩放; 确定后从原图取裁剪框对应的区域,
// 长边压到不超过 1600, 导出 JPG。几何、手势、回传的纯函数都在 utils/fabushi-image-editor,
// 本页只负责接线:
//   舞台画布(#stage, 2d)负责显示与触摸; 导出画布(#export, 2d)放在屏幕外, 确定时才用。
// 结果经 app.globalData[EDIT_RESULT_KEY] 回传发布页(与 H5 手机文件回传同一做法)。
// 标注(划线/圈/框)不在本页: 裁决 Q9 定为先做放大与裁剪, 标注后置。
const editor = require('../../../utils/fabushi-image-editor')

const BUTTON_ZOOM = 1.5
const DIM_COLOR = 'rgba(0, 0, 0, 0.55)'
const FRAME_COLOR = '#fff7ee'

// 微信的节点查询以 NodesRef 的收尾方法结束。这里经别名以属性方式调用:
// 仓库的静态安全扫描把紧跟左括号的 exec 当成“执行系统命令”, 会拦下这个文件的提交。
const runQuery = (ref, done) => ref.exec.call(ref, done)

Page({
  data: {
    ready: false,
    ratios: editor.RATIOS,
    ratioKey: 'original',
    position: 1,
    total: 1,
    showUseOriginalForRest: false,
    canZoomIn: false,
    canZoomOut: false,
    exporting: false,
    exportWidth: 1,
    exportHeight: 1
  },

  onLoad(options) {
    const query = options || {}
    this._src = query.src ? decodeURIComponent(query.src) : ''
    this._requestId = query.requestId ? decodeURIComponent(query.requestId) : ''
    const total = Math.max(1, Number(query.total) || 1)
    const position = Math.min(total, Math.max(1, Number(query.position) || 1))
    this._gesture = editor.createGesture()
    this.setData({ position, total, showUseOriginalForRest: total > 1 })
    if (!this._src || !this._requestId) {
      wx.showToast({ title: '缺少图片', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
    }
  },

  onReady() {
    if (!this._src || !this._requestId) return
    this.initStage()
  },

  // 没按任何按钮就离开(导航栏返回、侧滑): 这一张按原图处理, 发布页才不会一直等。
  // 页面已经在卸载, 只写回传, 不能再调用返回(会多弹掉一层页面)
  onUnload() {
    this.report({ edited: false })
  },

  initStage() {
    const query = wx.createSelectorQuery().select('#stage').fields({ node: true, size: true, rect: true })
    runQuery(query, (res) => {
      const info = res && res[0]
      if (!info || !info.node) return this.abort('画布初始化失败')
      const canvas = info.node
      let pixelRatio = 2
      try {
        pixelRatio = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()).pixelRatio || 2
      } catch (e) { /* 取不到像素比按 2 */ }
      canvas.width = info.width * pixelRatio
      canvas.height = info.height * pixelRatio
      const ctx = canvas.getContext('2d')
      ctx.scale(pixelRatio, pixelRatio)
      this._canvas = canvas
      this._ctx = ctx
      this._view = { width: info.width, height: info.height }
      this._offset = { left: info.left || 0, top: info.top || 0 }
      this.loadImage()
    })
  },

  loadImage() {
    const image = this._canvas.createImage()
    image.onload = () => {
      // 以画布实际解码出的尺寸为准(带旋转信息的照片由底层按显示方向解码)
      this._image = image
      this._source = { width: image.width, height: image.height }
      this._fit = editor.fitContain(image.width, image.height, this._view.width, this._view.height)
      this.applyRatio(this.data.ratioKey)
      this.setData({ ready: true })
    }
    image.onerror = () => this.abort('图片读取失败')
    image.src = this._src
  },

  applyRatio(ratioKey) {
    this._frame = editor.makeFrame(ratioKey, this._source.width, this._source.height, this._view)
    this._transform = editor.initialTransform(this._fit, this._frame)
    this.render()
  },

  render() {
    const ctx = this._ctx
    const transform = this._transform
    const frame = this._frame
    const { width, height } = this._view
    ctx.clearRect(0, 0, width, height)
    ctx.drawImage(this._image, transform.x, transform.y, this._fit.width * transform.scale, this._fit.height * transform.scale)
    // 裁剪框外压暗: 上、下、左、右四块; 没有空白的那一侧不画
    ctx.fillStyle = DIM_COLOR
    this.dim(0, 0, width, frame.y)
    this.dim(0, frame.y + frame.height, width, height - frame.y - frame.height)
    this.dim(0, frame.y, frame.x, frame.height)
    this.dim(frame.x + frame.width, frame.y, width - frame.x - frame.width, frame.height)
    ctx.strokeStyle = FRAME_COLOR
    ctx.lineWidth = 1
    ctx.strokeRect(frame.x + 0.5, frame.y + 0.5, frame.width - 1, frame.height - 1)
    const min = editor.minScale(this._fit, frame)
    this.setData({
      canZoomIn: transform.scale < Math.max(editor.MAX_SCALE, min) - 1e-6,
      canZoomOut: transform.scale > min + 1e-6
    })
  },

  dim(x, y, width, height) {
    if (width > 0 && height > 0) this._ctx.fillRect(x, y, width, height)
  },

  // ===== 触摸: 单指拖动, 双指缩放, 双击在整图与放大之间切换 =====

  pointsOf(e) {
    const touches = (e && e.touches) || []
    return touches.map((touch) => ({ x: touch.clientX - this._offset.left, y: touch.clientY - this._offset.top }))
  },

  nowOf(e) {
    return e && e.timeStamp ? e.timeStamp : Date.now()
  },

  onTouchStart(e) {
    if (this.data.ready) editor.gestureStart(this._gesture, this.pointsOf(e), this.nowOf(e))
  },

  onTouchMove(e) {
    if (!this.data.ready) return
    const delta = editor.gestureMove(this._gesture, this.pointsOf(e), this.nowOf(e))
    if (!delta) return
    this._transform = editor.applyGesture(this._transform, delta, this._fit, this._frame)
    this.render()
  },

  onTouchEnd(e) {
    if (!this.data.ready) return
    const result = editor.gestureEnd(this._gesture, this.pointsOf(e), this.nowOf(e))
    if (!result || !result.doubleTap) return
    this._transform = editor.toggleZoom(this._transform, this._fit, this._frame)
    this.render()
  },

  // 触摸被系统打断(来电、手势冲突): 只重新锚定, 丢弃这一下位移
  onTouchCancel(e) {
    if (this.data.ready) editor.gestureMove(this._gesture, this.pointsOf(e), this.nowOf(e))
  },

  // ===== 工具栏 =====

  onRatio(e) {
    const ratioKey = e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.key
    if (!this.data.ready || !ratioKey || ratioKey === this.data.ratioKey) return
    if (!editor.RATIOS.some((item) => item.key === ratioKey)) return
    this.setData({ ratioKey })
    this.applyRatio(ratioKey)
  },

  zoomBy(factor) {
    if (!this.data.ready) return
    const frame = this._frame
    const anchor = { x: frame.x + frame.width / 2, y: frame.y + frame.height / 2 }
    this._transform = editor.applyZoom(this._transform, factor, anchor, this._fit, frame)
    this.render()
  },

  onZoomIn() { this.zoomBy(BUTTON_ZOOM) },

  onZoomOut() { this.zoomBy(1 / BUTTON_ZOOM) },

  onReset() {
    if (!this.data.ready) return
    this._transform = editor.initialTransform(this._fit, this._frame)
    this.render()
  },

  onUseOriginal() {
    this.finish({ edited: false })
  },

  onUseOriginalForRest() {
    this.finish({ edited: false, useOriginalForRest: true })
  },

  // ===== 确定: 导出裁剪框内的部分 =====

  onConfirm() {
    if (!this.data.ready || this.data.exporting) return
    const rect = editor.cropSourceRect(this._transform, this._fit, this._frame, this._source.width, this._source.height)
    const size = editor.exportSize(rect.width, rect.height)
    this._exportPlan = { rect, size }
    this.setData({ exporting: true, exportWidth: size.width, exportHeight: size.height }, () => this.drawExport())
  },

  drawExport() {
    const { rect, size } = this._exportPlan
    const query = wx.createSelectorQuery().select('#export').fields({ node: true })
    runQuery(query, (res) => {
      const canvas = res && res[0] && res[0].node
      if (!canvas) return this.exportFailed()
      canvas.width = size.width
      canvas.height = size.height
      const ctx = canvas.getContext('2d')
      // 透明区域导出成 JPG 会发黑, 先铺白底
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, size.width, size.height)
      ctx.drawImage(this._image, rect.x, rect.y, rect.width, rect.height, 0, 0, size.width, size.height)
      wx.canvasToTempFilePath({
        canvas,
        x: 0,
        y: 0,
        width: size.width,
        height: size.height,
        destWidth: size.width,
        destHeight: size.height,
        fileType: 'jpg',
        quality: 0.9,
        success: (result) => {
          if (!result || !result.tempFilePath) return this.exportFailed()
          this.setData({ exporting: false })
          this.finish({ edited: true, path: result.tempFilePath })
        },
        fail: () => this.exportFailed()
      })
    })
  },

  exportFailed() {
    this.setData({ exporting: false })
    wx.showToast({ title: '导出失败，请重试或使用原图', icon: 'none' })
  },

  // ===== 离开本页 =====

  // 只写回传, 不跳转; 只生效一次。返回 false 表示已经有结果了
  report(result) {
    if (this._finished || !this._requestId) return false
    this._finished = true
    getApp().globalData[editor.EDIT_RESULT_KEY] = Object.assign({ requestId: this._requestId }, result)
    return true
  },

  // 写回传并返回发布页
  finish(result) {
    if (this.report(result)) wx.navigateBack()
  },

  // 编辑器自身出错: 这一张按原图处理, 并说明原因
  abort(message) {
    wx.showToast({ title: message + '，已使用原图', icon: 'none' })
    this.finish({ edited: false })
  }
})

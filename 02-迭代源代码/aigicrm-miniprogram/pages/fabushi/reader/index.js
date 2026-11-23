// 【2026-11-16 P2 方案A → F3a-2 2026-11-19 换票】法布施 H5 PDF 阅读器 web-view 容器页:
// openDocument 无页码回传/定位能力, 本页内嵌 H5(/mp-fabushi-reader, PDF.js 单页模式)。
// 【MP-TOKEN-001 法布施侧闭环】URL 不再携带主 JWT——改带短时阅读票(scope=download∧
// scene=fabushi-reader, 60min); 进度回传改容器代传(H5 postMessage 页码 → 本页持主 JWT
// 调 /api/fabushi/progress), 主 JWT 完全不离开小程序(QC 批准的方案 A)。
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { getFabushiReaderTicket, getFabushiProgress, saveFabushiProgress } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')

Page({
  data: {
    src: ''
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有阅读权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
    }
  },

  onLoad(options) {
    if (!requireLogin()) return
    const globalData = getApp().globalData
    if (globalData.demoMode) {
      wx.showToast({ title: '演示模式暂不支持在线阅读', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    // 与 A4 书目详情在线阅读入口同闸: fabushi:view 码兜底(入口已查, 防深链直入)
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有阅读权限', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    if (!options || !options.bookId || !options.pdfFileId) {
      wx.showToast({ title: '参数缺失', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    this._bookId = options.bookId
    // 阅读票 + 服务端续读页 并行取; 进度回读失败不阻断(从第 1 页开始), 票失败走共享反馈
    Promise.all([
      getFabushiReaderTicket(),
      getFabushiProgress(options.bookId).catch(() => null)
    ]).then(([ticketRes, progressRes]) => {
      const ticket = ticketRes && (ticketRes.ticket || (ticketRes.data && ticketRes.data.ticket))
      if (!ticket) {
        showError(null, '阅读票获取失败')
        return setTimeout(() => wx.navigateBack(), 600)
      }
      const prog = progressRes && (progressRes.lastPage !== undefined ? progressRes : progressRes.data)
      const lastPage = prog && Number(prog.lastPage) > 0 ? Number(prog.lastPage) : ''
      const q = [
        'ticket=' + encodeURIComponent(ticket),
        'bookId=' + encodeURIComponent(options.bookId),
        'pdfFileId=' + encodeURIComponent(options.pdfFileId),
        'title=' + encodeURIComponent(options.title || '')
      ]
      if (lastPage) q.push('lastPage=' + lastPage)
      // 【MP-PARITY 2026-11-23】把书目版式标记带给 H5：文本版默认纸白、影印版默认米黄增强
      if (options.layoutType) q.push('layoutType=' + encodeURIComponent(options.layoutType))
      this.setData({ src: globalData.apiBaseUrl + '/mp-fabushi-reader?' + q.join('&') })
    }).catch((err) => {
      showError(err, '在线阅读打开失败')
      setTimeout(() => wx.navigateBack(), 600)
    })
  },

  // H5 翻页时持续 postMessage(平台仅在回退/销毁时送达)——只留最新一条页码
  onWebViewMessage(e) {
    const list = (e.detail && e.detail.data) || []
    for (let i = list.length - 1; i >= 0; i--) {
      const msg = list[i]
      if (msg && msg.bookId && msg.lastPage) {
        this._lastProgress = { lastPage: msg.lastPage, totalPages: msg.totalPages || 0 }
        return
      }
    }
  },

  // 离开阅读器时以主 JWT(本页原生态)代传进度——H5 全程不接触主 JWT
  onUnload() {
    if (this._lastProgress && this._bookId) {
      saveFabushiProgress(this._bookId, this._lastProgress).catch(() => {})
    }
  }
})

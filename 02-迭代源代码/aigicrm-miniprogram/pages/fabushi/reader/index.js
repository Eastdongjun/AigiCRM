// 【2026-11-16 P2 方案A】法布施 H5 PDF 阅读器 web-view 容器页:
// openDocument 无页码回传/定位能力(交接单 P2 红线"不允许冒充页码级进度"),
// 本页内嵌 H5(/mp-fabushi-reader, PDF.js 单页模式)翻页自动回传进度。
// 鉴权与 pages/files/picker 同构: URL 携带小程序登录 token。
const { getToken, requireLogin, hasPermission } = require('../../../utils/auth')

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
    const token = getToken()
    if (!token) {
      wx.showToast({ title: '登录状态缺失', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    const q = [
      'token=' + encodeURIComponent(token),
      'bookId=' + encodeURIComponent(options.bookId),
      'pdfFileId=' + encodeURIComponent(options.pdfFileId),
      'title=' + encodeURIComponent(options.title || '')
    ]
    this.setData({ src: globalData.apiBaseUrl + '/mp-fabushi-reader?' + q.join('&') })
  }
})

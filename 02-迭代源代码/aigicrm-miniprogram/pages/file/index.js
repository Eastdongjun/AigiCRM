const { hasPermission, requireLogin } = require('../../utils/auth')

Page({
  data: { redirecting: false },

  onLoad() {
    this.redirectToFiles()
  },

  onShow() {
    this.redirectToFiles()
  },

  redirectToFiles() {
    if (this.data.redirecting) return
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ redirecting: true })
    wx.switchTab({ url: '/pages/files/index' })
  }
})

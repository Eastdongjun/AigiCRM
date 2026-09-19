const { requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: { redirecting: false },
  onLoad(options) {
    this.redirectToIncentive(options || {})
  },
  redirectToIncentive(options) {
    if (this.data.redirecting) return
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项激励查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const id = options.id || options.recordId
    this.setData({ redirecting: true })
    if (id) {
      wx.redirectTo({ url: `/pages/incentive/detail/index?id=${encodeURIComponent(id)}` })
      return
    }
    wx.switchTab({ url: '/pages/incentive/index' })
  },
  goBack() { wx.navigateBack() }
})

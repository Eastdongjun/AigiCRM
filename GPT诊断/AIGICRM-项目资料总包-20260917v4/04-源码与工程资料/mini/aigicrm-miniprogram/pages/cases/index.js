const { hasPermission, requireLogin } = require('../../utils/auth')

Page({
  data: { redirecting: false },

  onLoad() {
    this.redirectToIncentive()
  },

  onShow() {
    this.redirectToIncentive()
  },

  redirectToIncentive() {
    if (this.data.redirecting) return
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项激励查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ redirecting: true })
    wx.switchTab({ url: '/pages/incentive/index' })
  }
})

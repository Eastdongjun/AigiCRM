const { requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: { title: '捞取申请', status: '', createdAt: '', reason: '' },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('sea:view')) {
      wx.showToast({ title: '没有公海查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ title: decodeURIComponent(options.title || '捞取申请'), status: decodeURIComponent(options.status || ''), createdAt: decodeURIComponent(options.createdAt || ''), reason: decodeURIComponent(options.reason || '') })
  },
  goBack() { wx.navigateBack() }
})

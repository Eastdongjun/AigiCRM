const { requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: { title: '', customer: '', category: '', status: '', score: '', owner: '', updatedAt: '' },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有案例查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ title: decodeURIComponent(options.title || ''), customer: decodeURIComponent(options.customer || ''), category: decodeURIComponent(options.category || ''), status: decodeURIComponent(options.status || ''), score: decodeURIComponent(options.score || ''), owner: decodeURIComponent(options.owner || ''), updatedAt: decodeURIComponent(options.updatedAt || '') })
  },
  goBack() { wx.navigateBack() }
})

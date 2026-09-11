const { requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: { name: '', score: 0, dept: '', period: '' },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('score:view')) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ name: decodeURIComponent(options.name || ''), score: decodeURIComponent(options.score || '0'), dept: decodeURIComponent(options.dept || ''), period: decodeURIComponent(options.period || '当前周期') })
  },
  goBack() { wx.navigateBack() }
})

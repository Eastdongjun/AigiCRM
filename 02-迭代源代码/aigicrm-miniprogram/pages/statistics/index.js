const { getDashboardData } = require('../../api/statistics')
const { requireLogin, hasPermission } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

Page({
  data: { loading: false, stats: {}, expiringDetails: [] },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadStats()
  },

  onPullDownRefresh() {
    this.loadStats().finally(() => wx.stopPullDownRefresh())
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ stats: { myCustomers: 128, inProtection: 86, expiring: 8, expired: 5, todayExpiring: 2, monthNew: 12, seaStats: {}, approvalStats: { pending: 3, approved: 8, rejected: 1 } }, expiringDetails: [] })
      return Promise.resolve()
    }
    this.setData({ loading: true })
    return getDashboardData().then((data) => this.setData({ stats: data || {}, expiringDetails: (data && data.expiringDetails) || [] }))
      .catch((error) => showError(error, '统计加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  refresh() { this.loadStats() },
  openDetail() { wx.navigateTo({ url: '/pages/statistics/detail/index' }) }
})

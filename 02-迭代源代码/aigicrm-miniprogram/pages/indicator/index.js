const { getIndicatorStats } = require('../../api/statistics')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

Page({
  data: { loading: false, stats: {}, ranking: [] },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      return wx.switchTab({ url: '/pages/home/index' })
    }
    this.loadStats()
  },

  onShow() {
    // Refresh after returning from group/context changes without duplicating the initial load.
    if (!getApp().globalData.demoMode && Object.keys(this.data.stats || {}).length > 0) this.loadStats()
  },

  onPullDownRefresh() {
    if (getApp().globalData.demoMode) return wx.stopPullDownRefresh()
    this.loadStats().finally(() => wx.stopPullDownRefresh())
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      return this.setData({ stats: { coveredCount: 0, coreCount: 0, avgScore: 0, qualifyRate: 0 }, ranking: [] })
    }
    this.setData({ loading: true })
    getIndicatorStats().then((data) => {
      const stats = data || {}
      this.setData({ stats, ranking: (stats.customerRanking || []).slice(0, 30) })
    }).catch((error) => showError(error, '指标加载失败')).finally(() => this.setData({ loading: false }))
  }
})

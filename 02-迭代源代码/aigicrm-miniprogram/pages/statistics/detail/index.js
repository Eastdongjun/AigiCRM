const { getDashboardData, getTeamActivities, getDailyStats, getWeeklyStats } = require('../../../api/statistics')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { loading: false, stats: {}, activities: [], daily: {}, weekly: {}, section: 'customer' },
  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.loadStats()
  },
  onPullDownRefresh() { this.loadStats().finally(() => wx.stopPullDownRefresh()) },
  loadStats() {
    this.setData({ loading: true })
    if (getApp().globalData.demoMode) {
      this.setData({
        stats: { myCustomers: 128, inProtection: 86, expiring: 8, expired: 5, todayExpiring: 2, monthNew: 12, seaStats: {}, approvalStats: { pending: 3, approved: 8, rejected: 1 } },
        activities: [
          { type: '客户', content: '完成客户跟进记录', actor: '当前用户', createdAt: '今天 09:30' },
          { type: '审批', content: '提交一条审批申请', actor: '当前用户', createdAt: '昨天 16:20' }
        ],
        daily: {
          customerDays: [{ date: '本月 01 日', value: 2 }, { date: '本月 02 日', value: 1 }],
          fileDays: [{ date: '本月 01 日', value: 3 }, { date: '本月 02 日', value: 2 }]
        },
        weekly: {
          customerWeeks: [{ label: 'W1', value: 6 }, { label: '本周', value: 3 }],
          fileWeeks: [{ label: 'W1', value: 8 }, { label: '本周', value: 5 }]
        },
        loading: false
      })
      return Promise.resolve()
    }
    return Promise.all([getDashboardData(), getTeamActivities(), getDailyStats(), getWeeklyStats()])
      .then(([stats, activities, daily, weekly]) => this.setData({ stats: stats || {}, activities: activities || [], daily: daily || {}, weekly: weekly || {} }))
      .catch((error) => showError(error, '统计明细加载失败'))
      .finally(() => this.setData({ loading: false }))
  },
  switchSection(e) { this.setData({ section: e.currentTarget.dataset.section }) },
  refresh() { this.loadStats() },
  goBack() { wx.navigateBack() }
})

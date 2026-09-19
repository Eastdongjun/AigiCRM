const { getDashboardData, getTeamActivities, getDailyStats, getWeeklyStats } = require('../../../api/statistics')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function percent(value, total) {
  if (!total) return 0
  return Math.round((Number(value || 0) / total) * 1000) / 10
}

function displayText(value, fallback = '-') {
  return value === undefined || value === null || value === '' ? fallback : String(value)
}

function normalizeStats(stats = {}) {
  const myCustomers = Number(stats.myCustomers || 0)
  const inProtection = Number(stats.inProtection || 0)
  const expiring = Number(stats.expiring || 0)
  const todayExpiring = Number(stats.todayExpiring || 0)
  const expired = Number(stats.expired || 0)
  return {
    ...stats,
    myCustomers,
    inProtection,
    expiring,
    todayExpiring,
    expired,
    monthNew: Number(stats.monthNew || 0),
    approvalStats: stats.approvalStats || {},
    seaStats: stats.seaStats || {},
    protectionRate: percent(inProtection, myCustomers),
    riskTotal: expiring + todayExpiring + expired,
    healthText: percent(inProtection, myCustomers) >= 60 ? '健康度良好' : '需关注'
  }
}

function normalizeDaily(daily = {}) {
  return {
    ...daily,
    customerDays: Array.isArray(daily.customerDays) ? daily.customerDays : [],
    fileDays: Array.isArray(daily.fileDays) ? daily.fileDays : []
  }
}

function normalizeWeekly(weekly = {}) {
  return {
    ...weekly,
    customerWeeks: Array.isArray(weekly.customerWeeks) ? weekly.customerWeeks : [],
    fileWeeks: Array.isArray(weekly.fileWeeks) ? weekly.fileWeeks : []
  }
}

function normalizeActivities(activities = []) {
  return (Array.isArray(activities) ? activities : []).map((item, index) => ({
    id: item.id || `${item.type || 'activity'}-${index}`,
    typeDisplay: displayText(item.type, '动态'),
    contentDisplay: displayText(item.content || item.description),
    actorDisplay: displayText(item.actor || item.userName),
    timeDisplay: displayText(item.createdAt || item.time)
  }))
}

function trendRow(item, prefix) {
  return {
    id: item.id || item.date || item.day || item.label || item.week || `${prefix}-${item.value || 0}`,
    label: displayText(item.date || item.day || item.label || item.week),
    valueText: `${prefix} ${Number(item.value || 0)}`
  }
}

function buildDetailView(state) {
  const stats = state.stats || normalizeStats({})
  const approvalStats = stats.approvalStats || {}
  const seaStats = stats.seaStats || {}
  const activities = normalizeActivities(state.activities)
  const customerDays = (state.daily && state.daily.customerDays ? state.daily.customerDays : []).map((item) => trendRow(item, '新增客户'))
  const fileDays = (state.daily && state.daily.fileDays ? state.daily.fileDays : []).map((item) => trendRow(item, '文件'))
  const customerWeeks = (state.weekly && state.weekly.customerWeeks ? state.weekly.customerWeeks : []).map((item) => trendRow(item, '新增客户'))
  const fileWeeks = (state.weekly && state.weekly.fileWeeks ? state.weekly.fileWeeks : []).map((item) => trendRow(item, '文件'))
  const section = state.section || 'customer'
  return {
    refreshLoading: Boolean(state.loading),
    refreshText: state.loading ? '刷新中' : '刷新',
    backDisabled: Boolean(state.loading),
    tabs: [
      { key: 'customer', label: '客户', className: section === 'customer' ? 'stats-tab active' : 'stats-tab' },
      { key: 'approval', label: '审批', className: section === 'approval' ? 'stats-tab active' : 'stats-tab' },
      { key: 'sea', label: '公海', className: section === 'sea' ? 'stats-tab active' : 'stats-tab' },
      { key: 'activity', label: '团队动态', className: section === 'activity' ? 'stats-tab active' : 'stats-tab' },
      { key: 'trend', label: '趋势', className: section === 'trend' ? 'stats-tab active' : 'stats-tab' }
    ],
    showLoading: Boolean(state.loading),
    showCustomer: !state.loading && section === 'customer',
    showApproval: !state.loading && section === 'approval',
    showSea: !state.loading && section === 'sea',
    showActivity: !state.loading && section === 'activity',
    showTrend: !state.loading && section === 'trend',
    summaryTiles: [
      { label: '保护率', value: `${stats.protectionRate || 0}%` },
      { label: '风险合计', value: stats.riskTotal || 0 },
      { label: '健康度', value: stats.healthText || '需关注' }
    ],
    customerRows: [
      { label: '我的客户', value: stats.myCustomers || 0 },
      { label: '保护期客户', value: stats.inProtection || 0 },
      { label: '即将到期', value: stats.expiring || 0 },
      { label: '今日到期', value: stats.todayExpiring || 0 },
      { label: '已过期', value: stats.expired || 0 },
      { label: '本月新增', value: stats.monthNew || 0 }
    ],
    approvalRows: [
      { label: '待审批', value: approvalStats.pending || 0 },
      { label: '已通过', value: approvalStats.approved || 0 },
      { label: '已驳回', value: approvalStats.rejected || 0 }
    ],
    seaRows: [
      { label: '公海客户', value: seaStats.total || seaStats.count || 0 },
      { label: '可捞取', value: seaStats.available || 0 }
    ],
    activities,
    showActivityEmpty: activities.length === 0,
    dailyRows: [...customerDays, ...fileDays],
    weeklyRows: [...customerWeeks, ...fileWeeks],
    showDailyEmpty: customerDays.length === 0 && fileDays.length === 0,
    showWeeklyEmpty: customerWeeks.length === 0 && fileWeeks.length === 0
  }
}

Page({
  data: {
    loading: false,
    stats: normalizeStats({}),
    activities: [],
    daily: normalizeDaily({}),
    weekly: normalizeWeekly({}),
    section: 'customer',
    detailView: buildDetailView({
      loading: false,
      stats: normalizeStats({}),
      activities: [],
      daily: normalizeDaily({}),
      weekly: normalizeWeekly({}),
      section: 'customer'
    })
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, detailView: buildDetailView(next) })
  },
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
    this.applyState({ loading: true })
    if (getApp().globalData.demoMode) {
      this.applyState({
        stats: normalizeStats({ myCustomers: 128, inProtection: 86, expiring: 8, expired: 5, todayExpiring: 2, monthNew: 12, seaStats: {}, approvalStats: { pending: 3, approved: 8, rejected: 1 } }),
        activities: [
          { type: '客户', content: '完成客户跟进记录', actor: '当前用户', createdAt: '今天 09:30' },
          { type: '审批', content: '提交一条审批申请', actor: '当前用户', createdAt: '昨天 16:20' }
        ],
        daily: normalizeDaily({
          customerDays: [{ date: '本月 01 日', value: 2 }, { date: '本月 02 日', value: 1 }],
          fileDays: [{ date: '本月 01 日', value: 3 }, { date: '本月 02 日', value: 2 }]
        }),
        weekly: normalizeWeekly({
          customerWeeks: [{ label: 'W1', value: 6 }, { label: '本周', value: 3 }],
          fileWeeks: [{ label: 'W1', value: 8 }, { label: '本周', value: 5 }]
        }),
        loading: false
      })
      return Promise.resolve()
    }
    return Promise.all([getDashboardData(), getTeamActivities(), getDailyStats(), getWeeklyStats()])
      .then(([stats, activities, daily, weekly]) => this.applyState({
        stats: normalizeStats(stats || {}),
        activities: activities || [],
        daily: normalizeDaily(daily || {}),
        weekly: normalizeWeekly(weekly || {})
      }))
      .catch((error) => showError(error, '统计明细加载失败'))
      .finally(() => this.applyState({ loading: false }))
  },
  switchSection(e) { this.applyState({ section: e.currentTarget.dataset.section }) },
  refresh() { this.loadStats() },
  goBack() {
    if (this.data.loading) return
    wx.navigateBack()
  }
})

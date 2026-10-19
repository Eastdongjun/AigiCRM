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
  // 【工作流复核确认修复】字段契约对齐: /statistics/team-activities 实际返回 {title,user,dept,time},
  // 原读 type/content/actor 三键均不存在 → 每行显示"动态 / - / - / 时间", 面板内容不可用
  return (Array.isArray(activities) ? activities : []).map((item, index) => ({
    id: item.id || `activity-${index}`,
    typeDisplay: '动态',
    contentDisplay: displayText(item.title),
    actorDisplay: displayText([item.user, item.dept].filter(Boolean).join(' · ')),
    timeDisplay: displayText(item.time)
  }))
}

function trendRow(item, prefix, index) {
  // 客户/文件两路日报(周报)会拼进同一个 wx:for, 原 id 只取 date/label → 同日两行 key 冲突;
  // 序列前缀 + 序号保证拼接后全局唯一, 渲染结果不变
  return {
    id: `${prefix}-${item.id || item.date || item.day || item.label || item.week || index}`,
    label: displayText(item.date || item.day || item.label || item.week),
    valueText: `${prefix} ${Number(item.value || 0)}`
  }
}

function buildDetailView(state) {
  const stats = state.stats || normalizeStats({})
  const approvalStats = stats.approvalStats || {}
  const seaStats = stats.seaStats || {}
  const activities = normalizeActivities(state.activities)
  const customerDays = (state.daily && state.daily.customerDays ? state.daily.customerDays : []).map((item, index) => trendRow(item, '新增客户', index))
  const fileDays = (state.daily && state.daily.fileDays ? state.daily.fileDays : []).map((item, index) => trendRow(item, '文件', index))
  const customerWeeks = (state.weekly && state.weekly.customerWeeks ? state.weekly.customerWeeks : []).map((item, index) => trendRow(item, '新增客户', index))
  const fileWeeks = (state.weekly && state.weekly.fileWeeks ? state.weekly.fileWeeks : []).map((item, index) => trendRow(item, '文件', index))
  const section = state.section || 'customer'
  // 四路数据各自独立失败: 失败面板展示错误态, 其余面板照常展示成功数据
  const loadErrors = state.loadErrors || {}
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
      { label: '已超期', value: stats.expired || 0 },
      { label: '本月新增', value: stats.monthNew || 0 }
    ],
    approvalRows: [
      { label: '待审批', value: approvalStats.pending || 0 },
      // 【体检 P1 2026-10-12】后端统计键为 approvedTotal/rejectedTotal, approved/rejected 从不写→恒 0
      { label: '已通过', value: approvalStats.approvedTotal ?? (approvalStats.approvedThisMonth || 0) },
      { label: '已驳回', value: approvalStats.rejectedTotal ?? (approvalStats.rejectedThisMonth || 0) }
    ],
    seaRows: [
      { label: '公海客户', value: seaStats.total || seaStats.count || 0 },
      // 【工作流复核确认修复】原"可捞取"读 seaStats.available(后端无此字段, 恒 0); 换成真实键 pendingApproval
      { label: '待捞取审批', value: seaStats.pendingApproval || 0 }
    ],
    activities,
    showActivityEmpty: !loadErrors.activities && activities.length === 0,
    errors: {
      customer: loadErrors.stats ? '客户数据加载失败，请下拉刷新重试' : '',
      approval: loadErrors.stats ? '审批数据加载失败，请下拉刷新重试' : '',
      sea: loadErrors.stats ? '公海数据加载失败，请下拉刷新重试' : '',
      activity: loadErrors.activities ? '团队动态加载失败，请下拉刷新重试' : '',
      daily: loadErrors.daily ? '日报数据加载失败，请下拉刷新重试' : '',
      weekly: loadErrors.weekly ? '周报数据加载失败，请下拉刷新重试' : ''
    },
    dailyRows: [...customerDays, ...fileDays],
    weeklyRows: [...customerWeeks, ...fileWeeks],
    showDailyEmpty: !loadErrors.daily && customerDays.length === 0 && fileDays.length === 0,
    showWeeklyEmpty: !loadErrors.weekly && customerWeeks.length === 0 && fileWeeks.length === 0
  }
}

Page({
  data: {
    loading: false,
    stats: normalizeStats({}),
    activities: [],
    daily: normalizeDaily({}),
    weekly: normalizeWeekly({}),
    loadErrors: {},
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
        stats: normalizeStats({ myCustomers: 128, inProtection: 86, expiring: 8, expired: 5, todayExpiring: 2, monthNew: 12, seaStats: {}, approvalStats: { pending: 3, approvedTotal: 8, rejectedTotal: 1 } }),
        activities: [
          { title: '完成客户跟进记录', user: '当前用户', dept: '华东办', time: '今天 09:30' },
          { title: '提交一条审批申请', user: '当前用户', dept: '华东办', time: '昨天 16:20' }
        ],
        daily: normalizeDaily({
          customerDays: [{ date: '本月 01 日', value: 2 }, { date: '本月 02 日', value: 1 }],
          fileDays: [{ date: '本月 01 日', value: 3 }, { date: '本月 02 日', value: 2 }]
        }),
        weekly: normalizeWeekly({
          customerWeeks: [{ label: 'W1', value: 6 }, { label: '本周', value: 3 }],
          fileWeeks: [{ label: 'W1', value: 8 }, { label: '本周', value: 5 }]
        }),
        loadErrors: {},
        loading: false
      })
      return Promise.resolve()
    }
    // 【并发失败隔离】四路各自 catch: 单路失败只标记该面板错误并保留其余成功结果/旧数据,
    // 不再因 Promise.all 整体 reject 让全页归零
    const loadErrors = {}
    let toastShown = false
    const settle = (promise, key, fallback) => promise.catch((error) => {
      loadErrors[key] = true
      if (!toastShown) { toastShown = true; showError(error, fallback) }
      return null
    })
    return Promise.all([
      settle(getDashboardData(), 'stats', '客户统计加载失败'),
      settle(getTeamActivities(), 'activities', '团队动态加载失败'),
      settle(getDailyStats(), 'daily', '日报加载失败'),
      settle(getWeeklyStats(), 'weekly', '周报加载失败')
    ])
      .then(([stats, activities, daily, weekly]) => this.applyState({
        stats: loadErrors.stats ? this.data.stats : normalizeStats(stats || {}),
        activities: loadErrors.activities ? this.data.activities : (activities || []),
        daily: loadErrors.daily ? this.data.daily : normalizeDaily(daily || {}),
        weekly: loadErrors.weekly ? this.data.weekly : normalizeWeekly(weekly || {}),
        loadErrors
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

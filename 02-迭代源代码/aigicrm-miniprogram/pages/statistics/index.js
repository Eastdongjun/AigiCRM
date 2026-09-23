const { getAnalysis, getDashboardData } = require('../../api/statistics')
const { getScoreRanking } = require('../../api/score')
const { getDepts } = require('../../api/system')
const { requireLogin, hasPermission } = require('../../utils/auth')
const { showErrorWithRetry } = require('../../utils/feedback')

const MONTH_LABELS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2].map(String)
const FALLBACK_DEPTS = [
  { id: 1, deptName: '内蒙古大区', deptType: 2, parentId: 0 },
  { id: 11, deptName: '内蒙办', deptType: 3, parentId: 1 },
  { id: 2, deptName: '山西大区', deptType: 2, parentId: 0 },
  { id: 21, deptName: '山西办', deptType: 3, parentId: 2 }
]

function sum(values = []) {
  return values.reduce((total, value) => total + Number(value || 0), 0)
}

function maxValue(values = []) {
  return Math.max(0, ...values.map((value) => Number(value || 0)))
}

function buildMonthlySummary(values = []) {
  const normalized = Array.from({ length: 12 }, (_, index) => Number(values[index] || 0))
  const max = maxValue(normalized)
  const peakIndex = normalized.findIndex((value) => value === max)
  return {
    total: sum(normalized),
    average: normalized.length ? Math.round(sum(normalized) / normalized.length) : 0,
    peak: peakIndex >= 0 && max > 0 ? MONTH_LABELS[peakIndex] : '-',
    bars: normalized.map((value, index) => ({
      label: MONTH_LABELS[index],
      value,
      height: max ? Math.max(10, Math.round((value / max) * 100)) : 10
    }))
  }
}

function displayNumber(value) {
  return Number(value || 0)
}

function percent(value, total) {
  if (!total) return 0
  return Math.round((Number(value || 0) / total) * 1000) / 10
}

function buildOfficeRows(rows = []) {
  return (Array.isArray(rows) ? rows : []).map((item, index) => ({
    id: item.id || item.officeName || `office-${index}`,
    officeName: item.officeName || item.name || '未命名办事处',
    summaryText: `本月新增 ${displayNumber(item.monthNew)} · 保护率 ${displayNumber(item.activeRate)}%`,
    customerText: `${displayNumber(item.customerCount)} 家`
  }))
}

function buildScoreRows(rows = []) {
  return (Array.isArray(rows) ? rows : []).map((item, index) => ({
    id: item.id || item.userId || item.realName || `score-${index}`,
    rank: index + 1,
    rankClass: `score-rank rank-${index + 1}`,
    realName: item.realName || item.userName || '未命名人员',
    scoreText: `${displayNumber(item.totalScore)} 分`
  }))
}

function buildExpiringRows(rows = []) {
  return (Array.isArray(rows) ? rows : []).map((item, index) => ({
    id: item.id || item.customerId || item.customerName || `expiring-${index}`,
    customerName: item.customerName || item.name || '未命名客户',
    countdownText: `剩 ${displayNumber(item.daysLeft)} 天`
  }))
}

function enrichDashboardStats(stats = {}, analysis = {}) {
  const myCustomers = Number(stats.myCustomers || analysis.totalCustomers || 0)
  const inProtection = Number(stats.inProtection || analysis.inProtection || 0)
  const expiring = Number(stats.expiring || analysis.expiring || 0)
  const todayExpiring = Number(stats.todayExpiring || analysis.todayExpiring || 0)
  const expired = Number(stats.expired || analysis.expired || 0)
  return {
    ...stats,
    myCustomers,
    inProtection,
    expiring,
    todayExpiring,
    expired,
    protectionRate: percent(inProtection, myCustomers),
    riskTotal: expiring + todayExpiring + expired
  }
}

function buildStatusRows(analysis = {}) {
  const total = Number(analysis.totalCustomers || 0)
  return [
    { key: 'inProtection', label: '保护中', value: Number(analysis.inProtection || 0), pct: percent(analysis.inProtection, total), tone: 'green' },
    { key: 'expiring', label: '即将到期', value: Number(analysis.expiring || 0), pct: percent(analysis.expiring, total), tone: 'orange' },
    { key: 'expired', label: '已超期', value: Number(analysis.expired || 0), pct: percent(analysis.expired, total), tone: 'red' }
  ]
}

function buildIndustryRows(analysis = {}) {
  const entries = Object.entries(analysis.industryStats || {})
    .sort((left, right) => Number(right[1] || 0) - Number(left[1] || 0))
  const total = entries.reduce((count, [, value]) => count + Number(value || 0), 0)
  return entries.map(([name, count], index) => ({
    name,
    count: Number(count || 0),
    pct: percent(count, total),
    rank: index + 1
  }))
}

function buildTrendState(analysis = {}) {
  return {
    monthlyCustomerSummary: buildMonthlySummary(analysis.monthlyCustomers || []),
    monthlyFileSummary: buildMonthlySummary(analysis.monthlyFiles || []),
    statusRows: buildStatusRows(analysis),
    industryRows: buildIndustryRows(analysis)
  }
}

function buildStatsView(state = {}) {
  const stats = state.stats || {}
  const analysis = state.analysis || {}
  const approvalStats = stats.approvalStats || {}
  const yearOptions = state.yearOptions || YEAR_OPTIONS
  const deptOptions = state.deptOptions || []
  const loading = Boolean(state.loading)
  const yearLabel = yearOptions[state.selectedYearIndex] || '本年'
  const deptLabel = (deptOptions[state.selectedDeptIndex] && deptOptions[state.selectedDeptIndex].label) || '全部部门'
  const riskTotal = Number(stats.riskTotal || 0)
  const protectionRate = Number(stats.protectionRate || 0)
  const pendingApprovals = Number(approvalStats.pending || 0)
  const signalTone = riskTotal > 0 ? 'warning' : (protectionRate >= 70 ? 'stable' : 'watch')
  return {
    refreshLoading: loading,
    refreshText: loading ? '刷新中' : '刷新',
    showLoading: loading,
    loadingText: '经营统计加载中...',
    heroMetrics: [
      `本月新增 ${stats.monthNew || 0}`,
      `待审批 ${approvalStats.pending || 0}`
    ],
    yearLabel,
    deptLabel,
    filterSummary: `${yearLabel} · ${deptLabel}`,
    filterChips: [
      { label: '统计年度', value: yearLabel },
      { label: '组织范围', value: deptLabel }
    ],
    signalSummary: {
      className: `stats-signal-card ${signalTone}`,
      title: riskTotal > 0 ? '风险优先处理' : (protectionRate >= 70 ? '经营状态稳定' : '保护率待提升'),
      subtitle: `客户 ${stats.myCustomers || 0} 家 · 保护率 ${protectionRate}% · 待审批 ${pendingApprovals}`,
      rows: [
        { key: 'monthNew', label: '本月新增', value: stats.monthNew || 0, tone: 'blue' },
        { key: 'protectionRate', label: '保护率', value: `${protectionRate}%`, tone: protectionRate >= 70 ? 'green' : 'orange' },
        { key: 'riskTotal', label: '风险合计', value: riskTotal, tone: riskTotal > 0 ? 'red' : 'green' },
        { key: 'pending', label: '待审批', value: pendingApprovals, tone: pendingApprovals > 0 ? 'orange' : 'green' }
      ]
    },
    customerAsset: {
      total: stats.myCustomers || 0,
      protectionRate: stats.protectionRate || 0,
      protectionWidth: `${stats.protectionRate || 0}%`,
      riskClass: riskTotal > 0 ? 'stats-risk-note warning' : 'stats-risk-note stable',
      riskTitle: riskTotal > 0 ? `风险客户 ${riskTotal} 家` : '暂无保护期风险',
      riskText: riskTotal > 0 ? `即将到期 ${stats.expiring || 0} · 今日到期 ${stats.todayExpiring || 0} · 已超期 ${stats.expired || analysis.expired || 0}` : '当前筛选范围内保护期状态稳定',
      meta: [
        `保护期 ${stats.inProtection || 0}`,
        `即将到期 ${stats.expiring || 0}`,
        `今日到期 ${stats.todayExpiring || 0}`
      ],
      insights: [
        { label: '风险合计', value: stats.riskTotal || 0 },
        { label: '已超期', value: stats.expired || analysis.expired || 0 },
        { label: '年度新增', value: analysis.yearNew || 0 }
      ]
    },
    riskTiles: [
      { tone: 'blue', value: stats.inProtection || 0, label: '保护中' },
      { tone: 'orange', value: stats.expiring || 0, label: '即将到期' },
      { tone: 'red', value: stats.todayExpiring || 0, label: '今日到期' },
      { tone: 'green', value: stats.monthNew || 0, label: '本月新增' }
    ],
    analysisTiles: [
      { tone: 'blue', value: analysis.totalCustomers || 0, label: '客户总数' },
      { tone: 'green', value: analysis.yearNew || 0, label: '年度新增' },
      { tone: 'orange', value: analysis.expiring || 0, label: '即将到期' },
      { tone: 'red', value: analysis.expired || 0, label: '已超期' }
    ],
    approvalRows: [
      { className: 'pending', value: approvalStats.pending || 0, label: '待审批' },
      { className: 'approved', value: approvalStats.approved || 0, label: '已通过' },
      { className: 'rejected', value: approvalStats.rejected || 0, label: '已驳回' }
    ],
    monthlyCustomer: {
      ...state.monthlyCustomerSummary,
      totalText: `全年 ${displayNumber(state.monthlyCustomerSummary && state.monthlyCustomerSummary.total)}`,
      footText: `月均 ${displayNumber(state.monthlyCustomerSummary && state.monthlyCustomerSummary.average)} · 峰值 ${(state.monthlyCustomerSummary && state.monthlyCustomerSummary.peak) || '-'}`
    },
    monthlyFile: {
      ...state.monthlyFileSummary,
      totalText: `全年 ${displayNumber(state.monthlyFileSummary && state.monthlyFileSummary.total)}`,
      footText: `月均 ${displayNumber(state.monthlyFileSummary && state.monthlyFileSummary.average)} · 峰值 ${(state.monthlyFileSummary && state.monthlyFileSummary.peak) || '-'}`
    },
    industryRows: state.industryRows || [],
    showIndustryEmpty: !(state.industryRows || []).length,
    officeRows: buildOfficeRows(state.officeComparison),
    showOfficeEmpty: !(state.officeComparison || []).length,
    scoreRows: buildScoreRows(state.scoreTop5),
    showScoreEmpty: !(state.scoreTop5 || []).length,
    expiringRows: buildExpiringRows(state.expiringDetails),
    showExpiringEmpty: !(state.expiringDetails || []).length
  }
}

Page({
  data: {
    loading: false,
    stats: {},
    yearOptions: YEAR_OPTIONS,
    selectedYearIndex: 0,
    deptOptions: [{ id: '', deptName: '全部部门', label: '全部部门' }],
    selectedDeptIndex: 0,
    deptId: '',
    analysis: { totalCustomers: 0, yearNew: 0, inProtection: 0, expiring: 0, expired: 0, todayExpiring: 0 },
    monthlyCustomerSummary: buildMonthlySummary([]),
    monthlyFileSummary: buildMonthlySummary([]),
    statusRows: buildStatusRows({}),
    industryRows: [],
    officeComparison: [],
    scoreTop5: [],
    expiringDetails: [],
    statsView: buildStatsView({
      yearOptions: YEAR_OPTIONS,
      selectedYearIndex: 0,
      deptOptions: [{ id: '', deptName: '全部部门', label: '全部部门' }],
      selectedDeptIndex: 0,
      monthlyCustomerSummary: buildMonthlySummary([]),
      monthlyFileSummary: buildMonthlySummary([]),
      industryRows: [],
      officeComparison: [],
      scoreTop5: [],
      expiringDetails: []
    })
  },

  setStatisticsData(payload) {
    const next = {
      ...payload,
      statsView: buildStatsView({
        ...this.data,
        ...payload
      })
    }
    this.setData(next)
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadDepts()
    this.loadStats()
  },

  onPullDownRefresh() {
    this.loadStats().finally(() => wx.stopPullDownRefresh())
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      const demoAnalysis = {
        totalCustomers: 128,
        yearNew: 36,
        inProtection: 86,
        expiring: 8,
        expired: 5,
        todayExpiring: 2,
        industryStats: { 制造业: 52, 能源: 31, 金融: 18, 政务: 12 },
        monthlyCustomers: [2, 4, 3, 5, 4, 6, 3, 5, 4, 2, 1, 0],
        monthlyFiles: [8, 12, 9, 14, 11, 16, 10, 13, 12, 7, 5, 3]
      }
      this.setStatisticsData({
        stats: enrichDashboardStats({ myCustomers: 128, inProtection: 86, expiring: 8, expired: 5, todayExpiring: 2, monthNew: 12, seaStats: {}, approvalStats: { pending: 3, approved: 8, rejected: 1 } }, demoAnalysis),
        analysis: demoAnalysis,
        ...buildTrendState(demoAnalysis),
        officeComparison: [
          { officeName: '华东办事处', customerCount: 46, monthNew: 7, activeRate: 82.6 },
          { officeName: '北方办事处', customerCount: 31, monthNew: 4, activeRate: 77.4 },
          { officeName: '华南办事处', customerCount: 24, monthNew: 3, activeRate: 70.8 }
        ],
        scoreTop5: [
          { realName: '张三', totalScore: 96 },
          { realName: '李四', totalScore: 88 },
          { realName: '王五', totalScore: 76 },
          { realName: '赵六', totalScore: 63 },
          { realName: '钱七', totalScore: 58 }
        ],
        expiringDetails: []
      })
      return Promise.resolve()
    }
    this.setStatisticsData({ loading: true })
    return Promise.all([
      getDashboardData(),
      getAnalysis(this.buildAnalysisParams()).catch((error) => {
        showErrorWithRetry(error, '经营分析加载失败', () => this.loadStats())
        return {}
      }),
      getScoreRanking({ type: 'personal' }).catch((error) => {
        showErrorWithRetry(error, '积分排行加载失败', () => this.loadStats())
        return []
      })
    ]).then(([data, analysis, ranking]) => this.setStatisticsData({
      stats: enrichDashboardStats(data || {}, analysis || {}),
      analysis: analysis || {},
      ...buildTrendState(analysis || {}),
      officeComparison: ((analysis && analysis.officeComparison) || []).slice(0, 3),
      scoreTop5: (ranking || []).slice(0, 5),
      expiringDetails: (data && data.expiringDetails) || []
    }))
      .catch((error) => showErrorWithRetry(error, '统计加载失败', () => this.loadStats()))
      .finally(() => this.setStatisticsData({ loading: false }))
  },

  buildAnalysisParams() {
    const year = this.data.yearOptions[this.data.selectedYearIndex] || String(CURRENT_YEAR)
    const dept = this.data.deptOptions[this.data.selectedDeptIndex]
    const params = { year }
    if (dept && dept.id) params.deptId = dept.id
    return params
  },

  loadDepts() {
    if (getApp().globalData.demoMode) {
      this.applyDepts(FALLBACK_DEPTS)
      return
    }
    // 【PR-04】真实模式接口失败：报错并只保留"全部部门"，不回退演示部门
    getDepts().then((rows) => {
      this.applyDepts(rows || [])
    }).catch((error) => {
      showErrorWithRetry(error, '部门加载失败，请重试', () => this.loadDepts())
      this.applyDepts([])
    })
  },

  applyDepts(list) {
    const source = list || []
    const filtered = source.filter((item) => item && item.deptType !== 1)
    const byParent = {}
    filtered.forEach((item) => {
      const parentId = filtered.some((node) => String(node.id) === String(item.parentId)) ? item.parentId : 0
      const key = String(parentId || 0)
      if (!byParent[key]) byParent[key] = []
      byParent[key].push(item)
    })
    const result = [{ id: '', deptName: '全部部门', label: '全部部门' }]
    const walk = (parentId, level) => {
      ;(byParent[String(parentId || 0)] || [])
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
        .forEach((item) => {
          result.push({ ...item, label: `${'  '.repeat(level)}${item.deptName || item.name || '未命名部门'}` })
          walk(item.id, level + 1)
        })
    }
    walk(0, 0)
    const selectedDeptIndex = Math.min(this.data.selectedDeptIndex, result.length - 1)
    this.setData({ deptOptions: result, selectedDeptIndex }, () => {
      this.setData({ statsView: buildStatsView({ ...this.data, deptOptions: result, selectedDeptIndex }) })
    })
  },

  chooseYear(e) {
    this.setData({ selectedYearIndex: Number(e.detail.value) || 0 })
    this.loadStats()
  },

  chooseDept(e) {
    const index = Number(e.detail.value) || 0
    const dept = this.data.deptOptions[index]
    this.setData({ selectedDeptIndex: index, deptId: dept && dept.id ? dept.id : '' })
    this.loadStats()
  },

  refresh() { this.loadStats() },
  openDetail() { wx.navigateTo({ url: '/pages/statistics/detail/index' }) }
})

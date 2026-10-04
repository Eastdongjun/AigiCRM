const { getIndicatorStats } = require('../../api/statistics')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function normalizeNumber(value) {
  return Number(value || 0)
}

// 【D32 2026-10-04】指标满分口径: 客情分(0-3) + 四项核心指标(各 0-3) 共 5 项合计 15 分,
// 后端 StatisticsQueryService#indicatorScore 即这 5 项求和; 与 PC 客户详情「总分 x / 15」同口径
const INDICATOR_FULL_SCORE = 15

function buildIndicatorView(state = {}) {
  const stats = state.stats || {}
  const ranking = (state.ranking || []).map((item, index) => ({
    id: item.customerId || item.id || `rank-${index}`,
    rank: index + 1,
    customerName: item.customerName || item.name || '未命名客户',
    coreText: item.core ? '核心' : '非核心',
    totalScore: normalizeNumber(item.totalScore)
  }))
  return {
    showLoading: Boolean(state.loading),
    showMetrics: !state.loading,
    metrics: [
      { label: '覆盖客户', value: normalizeNumber(stats.coveredCount), className: 'metric-value' },
      { label: '核心客户', value: normalizeNumber(stats.coreCount), className: 'metric-value success' },
      { label: '平均分', value: `${normalizeNumber(stats.avgScore)} / ${INDICATOR_FULL_SCORE}`, className: 'metric-value' },
      { label: '财务达标率', value: `${normalizeNumber(stats.qualifyRate)}%`, className: 'metric-value' }
    ],
    ranking,
    showRankingEmpty: ranking.length === 0
  }
}

Page({
  data: {
    loading: false,
    stats: {},
    ranking: [],
    indicatorView: buildIndicatorView({ loading: false, stats: {}, ranking: [] })
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, indicatorView: buildIndicatorView(next) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      return wx.switchTab({ url: '/pages/home/index' })
    }
    this.loadStats()
  },

  onShow() {
    // 【D33 2026-10-04】回流刷新: 从客户详情等页面修改核心指标后返回时保持最新;
    // 首次进入由 onLoad 负责首拉(stats 仍为空时跳过, 避免与 onLoad 重复加载)
    if (!getApp().globalData.demoMode && Object.keys(this.data.stats || {}).length > 0) this.loadStats()
  },

  onPullDownRefresh() {
    if (getApp().globalData.demoMode) return wx.stopPullDownRefresh()
    this.loadStats().finally(() => wx.stopPullDownRefresh())
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      return this.applyState({ stats: { coveredCount: 0, coreCount: 0, avgScore: 0, qualifyRate: 0 }, ranking: [] })
    }
    this.applyState({ loading: true })
    getIndicatorStats().then((data) => {
      const stats = data || {}
      this.applyState({ stats, ranking: (stats.customerRanking || []).slice(0, 30) })
    }).catch((error) => showError(error, '指标加载失败')).finally(() => this.applyState({ loading: false }))
  }
})

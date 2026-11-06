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
    // 【核心三态对齐】后端 customerRanking 同时返回 coreType(core/quasi/空), 与排行页/PC 同口径;
    // 仅看 core 布尔会把争核心(有阶段数据未双达标)误显示成非核心, 空值(无阶段数据)显示 —
    coreText: item.coreType === 'core'
      ? '真核心'
      : item.coreType === 'quasi'
        ? '争核心'
        : (item.core ? '真核心' : '—'),
    totalScore: normalizeNumber(item.totalScore)
  }))
  // 【2026-11-06 用户令"注意翻页"】客户排行客户端分页(10/页, 与排行页/统计页同款 pager);
  // rank 在全量映射时已按全局名次编号, 切页不重排
  const pagerState = paginateRanking(ranking, state.indicatorPage, state.indicatorPageSize)
  return {
    showLoading: Boolean(state.loading),
    showMetrics: !state.loading,
    metrics: [
      { label: '覆盖客户', value: normalizeNumber(stats.coveredCount), className: 'metric-value' },
      { label: '核心客户', value: normalizeNumber(stats.coreCount), className: 'metric-value success' },
      { label: '平均分', value: `${normalizeNumber(stats.avgScore)} / ${INDICATOR_FULL_SCORE}`, className: 'metric-value' },
      { label: '财务达标率', value: `${normalizeNumber(stats.qualifyRate)}%`, className: 'metric-value' }
    ],
    ranking: pagerState.rows,
    rankingTotal: pagerState.total,
    rankingPage: pagerState.page,
    showRankingEmpty: ranking.length === 0
  }
}

function paginateRanking(list, page, pageSize) {
  const total = (list || []).length
  const safeSize = Math.max(1, Number(pageSize) || 10)
  // 【2026-11-06 整体检查 O1】页码双向收拢: 下限 1, 上限末页——刷新后数据缩水不停留越界空页
  const maxPage = Math.max(1, Math.ceil(total / safeSize))
  const safePage = Math.min(Math.max(1, Number(page) || 1), maxPage)
  const start = (safePage - 1) * safeSize
  return { rows: (list || []).slice(start, start + safeSize), page: safePage, pageSize: safeSize, total }
}

Page({
  data: {
    loading: false,
    stats: {},
    ranking: [],
    indicatorPage: 1,
    indicatorPageSize: 10,
    indicatorTotal: 0,
    indicatorView: buildIndicatorView({ loading: false, stats: {}, ranking: [] })
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    const indicatorView = buildIndicatorView(next)
    // 【2026-11-06 整体检查 O1】回写收拢后的页码: 刷新致数据缩水时页面回到末页, 不卡越界空页(与排行页同口径)
    this.setData({ ...patch, indicatorPage: indicatorView.rankingPage, indicatorTotal: indicatorView.rankingTotal, indicatorView })
  },

  // 【2026-11-06 翻页】<pager> 回传 {page,pageSize}: 页大小变化时回第 1 页(与排行页同口径)
  onIndicatorPagerChange(e) {
    const detail = e.detail || {}
    const pageSize = Number(detail.pageSize) || this.data.indicatorPageSize
    const page = pageSize === this.data.indicatorPageSize ? (Number(detail.page) || 1) : 1
    this.applyState({ indicatorPage: page, indicatorPageSize: pageSize })
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

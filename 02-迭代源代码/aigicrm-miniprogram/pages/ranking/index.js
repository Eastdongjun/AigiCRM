const { getCustomerRankings } = require('../../api/statistics')
const { getScoreRanking, getRankingStats } = require('../../api/score')
const { getScoreboard } = require('../../api/incentive')
const { requireLogin, hasPermission } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function buildPeriodOptions() {
  const year = new Date().getFullYear()
  const periods = []
  for (let current = year + 4; current >= year - 1; current -= 1) {
    periods.push({ label: `${current}全年`, value: `${current}全年` })
    periods.push({ label: `${current}下半年`, value: `${current}下半年` })
    periods.push({ label: `${current}上半年`, value: `${current}上半年` })
  }
  return periods
}

function buildCustomerPeriodOptions() {
  return [{ label: '全部', value: '' }, ...buildPeriodOptions()]
}

function defaultRecordPeriod() {
  return `${new Date().getFullYear()}全年`
}

function defaultPeriodIndex(periods) {
  return Math.max(0, periods.findIndex((item) => item.value === defaultRecordPeriod()))
}

const SCORE_PERIOD_OPTIONS = buildPeriodOptions()
const CUSTOMER_PERIOD_OPTIONS = buildCustomerPeriodOptions()

function scoreListView(rows, activeTab) {
  const isDept = activeTab === 'dept'
  return (rows || []).map((item, index) => ({
    ...item,
    displayRank: item.rank || index + 1,
    title: isDept ? (item.deptName || '未命名部门') : (item.realName || '未命名人员'),
    meta: isDept
      ? `${item.participantCount || 0} 人参与 · 人均 ${item.avgScore || 0} 分`
      : `${item.officeName || '未填写办事处'} · ${item.itemCount || 0} 个文件 · ${item.trend || '追赶'}`,
    totalScoreText: item.totalScore || 0
  }))
}

function parentStatsText(item, parents) {
  return (parents || []).map((parent) => {
    const stat = item.byParent && item.byParent[parent.id] ? item.byParent[parent.id] : {}
    return `${stat.count || 0} 条/${stat.score || 0} 分 ${parent.categoryName}`
  }).join(' · ')
}

function customerListView(rows, activeTab, parents) {
  return (rows || []).map((item, index) => ({
    ...item,
    displayRank: index + 1,
    title: item.customerName || '未命名客户',
    scoreText: activeTab === 'relationship' ? (item.relationshipScore || 0) : (item.totalScore || 0),
    ownerIndustryText: `${item.ownerName || '未分配负责人'} · ${item.industry || '未填写行业'}`,
    showIncentive: activeTab === 'incentive',
    recordCountText: `上传 ${item.recordCount || 0} 条`,
    parentStatsText: parentStatsText(item, parents),
    showCore: activeTab === 'core',
    coreScoreText: `高层 ${item.relationshipScore || 0} · 渠道 ${item.indicator02 || 0} · 基层 ${item.indicator03 || 0} · 产品 ${item.indicator04 || 0} · 项目 ${item.indicator05 || 0}`,
    coreFinanceText: `开票 ${item.invoiceTotal || 0} 万 · 回款 ${item.paymentTotal || 0} 万 · 核心 ${item.core ? '是' : '否'}`
  }))
}

function tabView(tabs, activeKey) {
  return (tabs || []).map((item) => ({
    ...item,
    className: `ranking-tab${activeKey === item.key ? ' active' : ''}`
  }))
}

function buildSummaryCards(stats = {}) {
  return [
    { key: 'participantCount', label: '参与人数', valueText: String(stats.participantCount || 0) },
    { key: 'deptCount', label: '参与部门', valueText: String(stats.deptCount || 0) },
    { key: 'topScore', label: '最高积分', valueText: String(stats.topScore || 0) },
    { key: 'totalItemCount', label: '文件总数', valueText: String(stats.totalItemCount || 0) }
  ]
}

function pickLabel(options = [], index, fallback) {
  const option = options[Number(index) || 0] || options[0] || {}
  return option.label || fallback
}

function buildRankingView(data) {
  const loading = Boolean(data.loading)
  const scoreList = data.scoreListView || []
  const customerList = data.customerListView || []
  const stats = data.scoreStats || {}
  const scorePeriodText = pickLabel(data.periodOptions, data.periodIndex, defaultRecordPeriod())
  const customerPeriodText = pickLabel(data.customerPeriodOptions, data.customerPeriodIndex, '全部')
  const scoreTab = (data.scoreTabs || []).find((item) => item.key === data.scoreActiveTab) || {}
  const customerTab = (data.tabs || []).find((item) => item.key === data.activeTab) || {}
  return {
    refreshLoading: loading,
    refreshText: loading ? '刷新中' : '刷新',
    showLoading: loading,
    loadingText: '加载中...',
    summaryCards: buildSummaryCards(stats),
    topScoreText: `最高积分：${stats.topScoreName || '-'} · ${stats.topScore || 0} 分 · ${stats.topItemCount || 0} 个文件`,
    scorePeriodText,
    customerPeriodText,
    contextSummary: `${scorePeriodText} · ${scoreTab.title || '个人排行'} · 客户${customerPeriodText}`,
    contextChips: [
      { label: '评分周期', value: scorePeriodText },
      { label: '积分榜单', value: scoreTab.title || '个人排行' },
      { label: '客户周期', value: customerPeriodText },
      { label: '客户榜单', value: customerTab.title || '四项激励' }
    ],
    showScoreEmpty: !loading && scoreList.length === 0,
    showCustomerEmpty: !loading && customerList.length === 0
  }
}

Page({
  data: {
    activeTab: 'incentive',
    scoreActiveTab: 'personal',
    tabs: [
      { key: 'incentive', title: '四项激励' },
      { key: 'relationship', title: '客情关系' },
      { key: 'core', title: '核心指标' }
    ],
    scoreTabs: [
      { key: 'personal', title: '个人排行' },
      { key: 'dept', title: '部门排行' }
    ],
    periodOptions: SCORE_PERIOD_OPTIONS,
    periodIndex: defaultPeriodIndex(SCORE_PERIOD_OPTIONS),
    customerPeriodOptions: CUSTOMER_PERIOD_OPTIONS,
    customerPeriodIndex: 0,
    incentiveParents: [],
    rankings: { incentive: [], relationship: [], core: [] },
    scoreStats: { participantCount: 0, deptCount: 0, topScore: 0, topScoreName: '-', topItemCount: 0, totalItemCount: 0 },
    scoreRankings: { personal: [], dept: [] },
    scoreList: [],
    scoreListView: [],
    list: [],
    customerListView: [],
    scoreTabsView: tabView([
      { key: 'personal', title: '个人排行' },
      { key: 'dept', title: '部门排行' }
    ], 'personal'),
    tabsView: tabView([
      { key: 'incentive', title: '四项激励' },
      { key: 'relationship', title: '客情关系' },
      { key: 'core', title: '核心指标' }
    ], 'incentive'),
    loading: false,
    // 【2026-09-23 需求同步】四项激励个人排行(按人员聚合, 与 PC 激励统计个人排行同口径)
    incentiveBoard: [],
    rankingView: buildRankingView({
      loading: false,
      scoreStats: { participantCount: 0, deptCount: 0, topScore: 0, topScoreName: '-', topItemCount: 0, totalItemCount: 0 },
      scoreListView: [],
      customerListView: [],
      periodOptions: SCORE_PERIOD_OPTIONS,
      periodIndex: defaultPeriodIndex(SCORE_PERIOD_OPTIONS),
      customerPeriodOptions: CUSTOMER_PERIOD_OPTIONS,
      customerPeriodIndex: 0
    })
  },

  setRankingData(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, rankingView: buildRankingView(next) })
  },

  refreshRankingView() {
    this.setData({ rankingView: buildRankingView(this.data) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !(hasPermission('score:view') || hasPermission('ranking:customer:view') || hasPermission('statistics:view'))) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadRanking()
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key
    const list = this.data.rankings[activeTab] || []
    this.setRankingData({
      activeTab,
      list,
      tabsView: tabView(this.data.tabs, activeTab),
      customerListView: customerListView(list, activeTab, this.data.incentiveParents)
    })
  },

  switchScoreTab(e) {
    const scoreActiveTab = e.currentTarget.dataset.key
    const scoreList = this.data.scoreRankings[scoreActiveTab] || []
    this.setRankingData({
      scoreActiveTab,
      scoreList,
      scoreTabsView: tabView(this.data.scoreTabs, scoreActiveTab),
      scoreListView: scoreListView(scoreList, scoreActiveTab)
    })
  },

  onPeriodChange(e) {
    this.setRankingData({ periodIndex: Number(e.detail.value) || 0 })
    this.loadRanking()
  },

  onCustomerPeriodChange(e) {
    this.setRankingData({ customerPeriodIndex: Number(e.detail.value) || 0 })
    this.loadRanking()
  },

  currentPeriod() {
    return (this.data.periodOptions[this.data.periodIndex] || {}).value || defaultRecordPeriod()
  },

  currentCustomerPeriodParam() {
    return (this.data.customerPeriodOptions[this.data.customerPeriodIndex] || {}).value || undefined
  },

  loadRanking() {
    if (getApp().globalData.demoMode) {
      const incentiveParents = [
        { id: 'pre', categoryName: '安装前' },
        { id: 'mid', categoryName: '安装中' },
        { id: 'post', categoryName: '安装后' },
        { id: 'track', categoryName: '跟踪' }
      ]
      const rankings = {
        incentive: [{
          customerName: '华东智能制造集团',
          ownerName: '张三',
          industry: '制造业',
          totalScore: 36,
          recordCount: 4,
          byParent: {
            pre: { count: 1, score: 8 },
            mid: { count: 1, score: 10 },
            post: { count: 1, score: 12 },
            track: { count: 1, score: 6 }
          }
        }],
        relationship: [{ customerName: '华东智能制造集团', ownerName: '张三', industry: '制造业', relationshipScore: 3 }],
        core: [{ customerName: '华东智能制造集团', ownerName: '张三', industry: '制造业', relationshipScore: 3, indicator02: 3, indicator03: 2, indicator04: 2, indicator05: 2, totalScore: 12, invoiceTotal: 120, paymentTotal: 110, core: true }]
      }
      const scoreRankings = {
        personal: [
          { rank: 1, realName: '张三', officeName: '华东办', totalScore: 96, itemCount: 42, trend: '领先' },
          { rank: 2, realName: '李四', officeName: '华南办', totalScore: 88, itemCount: 37, trend: '上升' }
        ],
        dept: [
          { rank: 1, deptName: '华东办', totalScore: 386, participantCount: 6, avgScore: 64 },
          { rank: 2, deptName: '华南办', totalScore: 342, participantCount: 5, avgScore: 68 }
        ]
      }
      const scoreList = scoreRankings[this.data.scoreActiveTab] || []
      const list = rankings[this.data.activeTab] || []
      // 【2026-09-23 需求同步】demo 档四项激励个人排行
      const incentiveBoard = [
        { rank: 1, realName: '张三', officeName: '华东办', totalScore: 128, recordCount: 9 },
        { rank: 2, realName: '李四', officeName: '华南办', totalScore: 96, recordCount: 7 }
      ]
      this.setRankingData({
        incentiveParents,
        rankings,
        incentiveBoard,
        scoreRankings,
        scoreStats: { participantCount: 18, deptCount: 4, topScore: 96, topScoreName: '张三', topItemCount: 42, totalItemCount: 42 },
        list,
        customerListView: customerListView(list, this.data.activeTab, incentiveParents),
        scoreList,
        scoreListView: scoreListView(scoreList, this.data.scoreActiveTab),
        scoreTabsView: tabView(this.data.scoreTabs, this.data.scoreActiveTab),
        tabsView: tabView(this.data.tabs, this.data.activeTab)
      })
      return
    }
    this.setRankingData({ loading: true })
    const period = this.currentPeriod()
    Promise.all([
      getCustomerRankings({ period: this.currentCustomerPeriodParam() }),
      getScoreRanking({ period, type: 'personal' }).catch((error) => {
        showError(error, '个人积分排行加载失败')
        return []
      }),
      getScoreRanking({ period, type: 'dept' }).catch((error) => {
        showError(error, '部门积分排行加载失败')
        return []
      }),
      getRankingStats({ period }).catch((error) => {
        showError(error, '学分统计加载失败')
        return {}
      }),
      // 【2026-09-23 需求同步】四项激励个人排行(按人员聚合, 与 PC 激励统计同口径)
      getScoreboard({ period }).catch((error) => {
        showError(error, '激励个人排行加载失败')
        return []
      })
    ]).then(([rankings, personalScores, deptScores, scoreStats, incentiveBoard]) => {
      const safeRankings = {
        incentive: rankings && rankings.incentiveRanking || [],
        relationship: rankings && rankings.relationshipRanking || [],
        core: rankings && rankings.indicatorRanking || []
      }
      const scoreRankings = { personal: personalScores || [], dept: deptScores || [] }
      const list = safeRankings[this.data.activeTab] || []
      const scoreList = scoreRankings[this.data.scoreActiveTab] || []
      this.setRankingData({
        incentiveParents: rankings && rankings.incentiveParents || [],
        rankings: safeRankings,
        incentiveBoard: incentiveBoard || [],
        scoreRankings,
        scoreStats: {
          participantCount: scoreStats && scoreStats.participantCount || 0,
          deptCount: scoreStats && scoreStats.deptCount || 0,
          topScore: scoreStats && scoreStats.topScore || 0,
          topScoreName: scoreStats && scoreStats.topScoreName || '-',
          topItemCount: scoreStats && scoreStats.topItemCount || 0,
          totalItemCount: scoreStats && scoreStats.totalItemCount || 0
        },
        list,
        customerListView: customerListView(list, this.data.activeTab, rankings && rankings.incentiveParents || []),
        scoreList,
        scoreListView: scoreListView(scoreList, this.data.scoreActiveTab),
        scoreTabsView: tabView(this.data.scoreTabs, this.data.scoreActiveTab),
        tabsView: tabView(this.data.tabs, this.data.activeTab)
      })
    }).catch((error) => showError(error, '客户排行加载失败')).finally(() => this.setRankingData({ loading: false }))
  },

  refresh() { this.loadRanking() },

  openDetail(e) {
    const item = this.data.list[Number(e.currentTarget.dataset.index)]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=ranking` })
  },
  openScoreDetail(e) {
    const item = (this.data.scoreList || [])[Number(e.currentTarget.dataset.index)]
    if (!item) return
    const period = (this.data.rankingView && this.data.rankingView.scorePeriodText) || ''
    const query = [
      `name=${encodeURIComponent(item.realName || item.deptName || '')}`,
      `score=${encodeURIComponent(item.totalScore || 0)}`,
      `dept=${encodeURIComponent(item.deptName || item.officeName || '')}`,
      `period=${encodeURIComponent(period)}`
    ].join('&')
    wx.navigateTo({ url: `/pages/ranking/detail/index?${query}` })
  }
})

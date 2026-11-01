const { getCustomerRankings } = require('../../api/statistics')
const { getScoreRanking, getRankingStats } = require('../../api/score')
const { getScoreboard } = require('../../api/incentive')
const { requireLogin, hasPermission } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { paginate } = require('../../utils/paging')

// 排行榜客户端分页默认页大小（<pager> 可选 10/30）
const DEFAULT_PAGE_SIZE = 10

// 【2026-11-01 审计修复】学分周期只留「全年」(与 PC 对齐, 存量半年已并入全年; 选半年必查0条);
// 年份窗对齐 PC buildPeriodOptions(当前+1~-5)。客户排行仍用全周期选项(客户模块多周期口径)。
function buildScorePeriodOptions() {
  const year = new Date().getFullYear()
  const periods = []
  for (let current = year + 1; current >= year - 5; current -= 1) {
    periods.push({ label: `${current}全年`, value: `${current}全年` })
  }
  return periods
}

function buildCustomerPeriodOptions() {
  const year = new Date().getFullYear()
  // 客户排行保留多周期口径(含"全部"); 学分榜见 buildScorePeriodOptions(只留全年)
  const opts = [{ label: '全部', value: '' }]
  for (let current = year + 1; current >= year - 5; current -= 1) {
    opts.push({ label: `${current}全年`, value: `${current}全年` })
    opts.push({ label: `${current}下半年`, value: `${current}下半年` })
    opts.push({ label: `${current}上半年`, value: `${current}上半年` })
  }
  return opts
}

function defaultRecordPeriod() {
  return `${new Date().getFullYear()}全年`
}

function defaultPeriodIndex(periods) {
  return Math.max(0, periods.findIndex((item) => item.value === defaultRecordPeriod()))
}

const SCORE_PERIOD_OPTIONS = buildScorePeriodOptions()
const CUSTOMER_PERIOD_OPTIONS = buildCustomerPeriodOptions()

function scoreListView(rows, activeTab, startIndex = 0) {
  const isDept = activeTab === 'dept'
  return (rows || []).map((item, index) => ({
    ...item,
    // 分页切片后仍按全局名次编号：优先后端 rank，其次用切片起始偏移补全
    displayRank: item.rank || (startIndex + index + 1),
    title: isDept ? (item.deptName || '未命名部门') : (item.realName || '未命名人员'),
    meta: isDept
      ? `${item.participantCount || 0} 人参与 · 人均 ${item.avgScore || 0} 分`
      : `${item.officeName || '未填写办事处'} · ${item.itemCount || 0} 个文件 · ${item.trend || '追赶'}`,
    totalScoreText: item.totalScore || 0
  }))
}

function parentStatsRows(item, parents) {
  return (parents || []).map((parent) => {
    const stat = item.byParent && item.byParent[parent.id] ? item.byParent[parent.id] : {}
    return { id: parent.id, name: parent.categoryName, count: stat.count || 0, score: stat.score || 0 }
  })
}

function customerListView(rows, activeTab, parents, startIndex = 0) {
  return (rows || []).map((item, index) => ({
    ...item,
    // 分页切片后仍按全局名次编号，不能从 1 重新开始
    displayRank: item.rank || (startIndex + index + 1),
    title: item.customerName || '未命名客户',
    scoreText: activeTab === 'relationship' ? (item.relationshipScore || 0) : (item.totalScore || 0),
    ownerIndustryText: `${item.ownerName || '未分配负责人'} · ${item.industry || '未填写行业'}`,
    // 【#8 2026-10-12】核心客情卡片三行长文本改小表格: 负责人/行业两列表 + 五项指标五格 + 财务两列带核心徽章
    ownerIndustryRows: [
      { label: '负责人', value: item.ownerName || '未分配' },
      { label: '行业', value: item.industry || '未填写' }
    ],
    showIncentive: activeTab === 'incentive',
    // 【#12 2026-10-13】激励明细改三列表格: 四分类行 + 合计行(替代"上传N条"+"·串联"两行长文本)
    incentiveRows: parentStatsRows(item, parents),
    incentiveTotal: { count: item.recordCount || 0, score: item.totalScore || 0 },
    showCore: activeTab === 'core',
    coreScoreText: `高层 ${item.relationshipScore || 0} · 渠道 ${item.indicator02 || 0} · 基层 ${item.indicator03 || 0} · 产品 ${item.indicator04 || 0} · 项目 ${item.indicator05 || 0}`,
    indicatorCells: [
      { key: 'rel', label: '高层', value: item.relationshipScore || 0 },
      { key: 'ch', label: '渠道', value: item.indicator02 || 0 },
      { key: 'base', label: '基层', value: item.indicator03 || 0 },
      { key: 'prod', label: '产品', value: item.indicator04 || 0 },
      { key: 'proj', label: '项目', value: item.indicator05 || 0 }
    ],
    financeCells: [
      { key: 'invoice', label: '年度开票', value: `${item.invoiceTotal || 0} 万` },
      { key: 'payment', label: '年度回款', value: `${item.paymentTotal || 0} 万` }
    ],
    // 【裁决 2026-10-10】核心三态: 年度开票/回款双>=100万=真核心, 有数据未双达标=争核心(后端 coreType)
    coreFinanceText: `年度开票 ${item.invoiceTotal || 0} 万 · 年度回款 ${item.paymentTotal || 0} 万 · ${item.coreType === 'core' ? '真核心' : item.coreType === 'quasi' ? '争核心' : '非核心'}`,
    coreBadge: item.coreType === 'core'
      ? { text: '真核心', className: 'core' }
      : item.coreType === 'quasi'
        ? { text: '争核心', className: 'quasi' }
        : { text: '非核心', className: 'none' }
  }))
}

// 客户端分页：先对完整数组切片，再用切片起始偏移生成视图，保证全局名次连续
function pagedScoreView(rows, activeTab, page, pageSize) {
  const paged = paginate(rows, page, pageSize)
  const startIndex = (paged.page - 1) * paged.pageSize
  return {
    rows: paged.rows,
    view: scoreListView(paged.rows, activeTab, startIndex),
    page: paged.page,
    pageSize: paged.pageSize,
    total: paged.total
  }
}

function pagedCustomerView(rows, activeTab, parents, page, pageSize) {
  const paged = paginate(rows, page, pageSize)
  const startIndex = (paged.page - 1) * paged.pageSize
  return {
    rows: paged.rows,
    view: customerListView(paged.rows, activeTab, parents, startIndex),
    page: paged.page,
    pageSize: paged.pageSize,
    total: paged.total
  }
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
    { key: 'topScore', label: '最高学分', valueText: String(stats.topScore || 0) },
    { key: 'totalItemCount', label: '文件总数', valueText: String(stats.totalItemCount || 0) }
  ]
}

function pickLabel(options = [], index, fallback) {
  const option = options[Number(index) || 0] || options[0] || {}
  return option.label || fallback
}

function buildRankingView(data) {
  const loading = Boolean(data.loading)
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
    topScoreText: `最高学分：${stats.topScoreName || '-'} · ${stats.topScore || 0} 分 · ${stats.topItemCount || 0} 个文件`,
    scorePeriodText,
    customerPeriodText,
    contextSummary: `${scorePeriodText} · ${scoreTab.title || '个人排行'} · 客户${customerPeriodText}`,
    contextChips: [
      { label: '评分周期', value: scorePeriodText },
      { label: '学分榜单', value: scoreTab.title || '个人排行' },
      { label: '客户周期', value: customerPeriodText },
      { label: '客户榜单', value: customerTab.title || '四项服务' }
    ],
    showScoreEmpty: !loading && Number(data.scoreTotal || 0) === 0,
    showCustomerEmpty: !loading && Number(data.customerTotal || 0) === 0
  }
}

Page({
  data: {
    activeTab: 'incentive',
    scoreActiveTab: 'personal',
    tabs: [
      { key: 'incentive', title: '四项服务' },
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
    // 客户端分页字段：得分榜与客户榜各自独立页码/页大小，默认 10 条
    scorePage: 1,
    scorePageSize: DEFAULT_PAGE_SIZE,
    scoreTotal: 0,
    customerPage: 1,
    customerPageSize: DEFAULT_PAGE_SIZE,
    customerTotal: 0,
    incentivePage: 1,
    incentivePageSize: DEFAULT_PAGE_SIZE,
    incentiveTotal: 0,
    scoreList: [],
    scoreListView: [],
    list: [],
    customerListView: [],
    scoreTabsView: tabView([
      { key: 'personal', title: '个人排行' },
      { key: 'dept', title: '部门排行' }
    ], 'personal'),
    tabsView: tabView([
      { key: 'incentive', title: '四项服务' },
      { key: 'relationship', title: '客情关系' },
      { key: 'core', title: '核心指标' }
    ], 'incentive'),
    loading: false,
    // 【2026-09-23 需求同步】四项服务个人排行(按人员聚合, 与 PC 激励统计个人排行同口径)
    // incentiveBoard 保留接口完整榜，incentiveBoardView 为当前页切片
    incentiveBoard: [],
    incentiveBoardView: [],
    rankingView: buildRankingView({
      loading: false,
      scoreStats: { participantCount: 0, deptCount: 0, topScore: 0, topScoreName: '-', topItemCount: 0, totalItemCount: 0 },
      scoreListView: [],
      customerListView: [],
      scoreTotal: 0,
      customerTotal: 0,
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

  onLoad() {
    if (!requireLogin()) return
    // 【mp-3 2026-10-17】页面首个必发请求 /statistics/customer-rankings 后端只认 statistics:view；
    // 入口宫格也按 statistics:view 展示。此前页面放行 score:view/ranking:customer:view 会造成
    // 有入口必有权限、有权限却无入口的误拦截/死链。学分榜区块仍单独按 score:view 门控。
    if (!getApp().globalData.demoMode && !hasPermission('statistics:view')) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadRanking()
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key
    const list = this.data.rankings[activeTab] || []
    // 切换客户榜 tab 回到第 1 页，名次仍按全局顺序生成
    const paged = pagedCustomerView(list, activeTab, this.data.incentiveParents, 1, this.data.customerPageSize)
    this.setRankingData({
      activeTab,
      tabsView: tabView(this.data.tabs, activeTab),
      list: paged.rows,
      customerPage: paged.page,
      customerPageSize: paged.pageSize,
      customerTotal: paged.total,
      customerListView: paged.view
    })
  },

  switchScoreTab(e) {
    const scoreActiveTab = e.currentTarget.dataset.key
    const scoreList = this.data.scoreRankings[scoreActiveTab] || []
    // 切换个人/部门榜回到第 1 页
    const paged = pagedScoreView(scoreList, scoreActiveTab, 1, this.data.scorePageSize)
    this.setRankingData({
      scoreActiveTab,
      scoreTabsView: tabView(this.data.scoreTabs, scoreActiveTab),
      scoreList: paged.rows,
      scorePage: paged.page,
      scorePageSize: paged.pageSize,
      scoreTotal: paged.total,
      scoreListView: paged.view
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
      // 【2026-09-23 需求同步】demo 档四项服务个人排行
      const incentiveBoard = [
        { rank: 1, realName: '张三', officeName: '华东办', totalScore: 128, recordCount: 9 },
        { rank: 2, realName: '李四', officeName: '华南办', totalScore: 96, recordCount: 7 }
      ]
      // 演示数据同样走客户端分页，刷新/切周期后回到第 1 页
      const customerPaged = pagedCustomerView(rankings[this.data.activeTab] || [], this.data.activeTab, incentiveParents, 1, this.data.customerPageSize)
      const scorePaged = pagedScoreView(scoreRankings[this.data.scoreActiveTab] || [], this.data.scoreActiveTab, 1, this.data.scorePageSize)
      const incentivePaged = paginate(incentiveBoard, 1, this.data.incentivePageSize)
      this.setRankingData({
        incentiveParents,
        rankings,
        incentiveBoard,
        incentiveBoardView: incentivePaged.rows,
        incentivePage: incentivePaged.page,
        incentivePageSize: incentivePaged.pageSize,
        incentiveTotal: incentivePaged.total,
        scoreRankings,
        scoreStats: { participantCount: 18, deptCount: 4, topScore: 96, topScoreName: '张三', topItemCount: 42, totalItemCount: 42 },
        list: customerPaged.rows,
        customerPage: customerPaged.page,
        customerPageSize: customerPaged.pageSize,
        customerTotal: customerPaged.total,
        customerListView: customerPaged.view,
        scoreList: scorePaged.rows,
        scorePage: scorePaged.page,
        scorePageSize: scorePaged.pageSize,
        scoreTotal: scorePaged.total,
        scoreListView: scorePaged.view,
        scoreTabsView: tabView(this.data.scoreTabs, this.data.scoreActiveTab),
        tabsView: tabView(this.data.tabs, this.data.activeTab)
      })
      return
    }
    // 刷新/切换时间范围：两个榜单各自回到第 1 页（页大小保留用户选择）
    this.setRankingData({ loading: true, scorePage: 1, customerPage: 1, incentivePage: 1 })
    const period = this.currentPeriod()
    // 【体检 P1 2026-10-12】按权限门控请求: 页面入口是三码 OR, 但学分榜三路要 score:view、
    // 激励个人榜要 incentive:view——只持部分权限的角色此前每次进页必吃 4 条 403 弹错。
    // 无对应权限的区块直接置空(空态), 不再发起必败请求; demo 模式恒放开
    const demo = Boolean(getApp().globalData.demoMode)
    const canScore = demo || hasPermission('score:view')
    const canIncentive = demo || hasPermission('incentive:view')
    Promise.all([
      getCustomerRankings({ period: this.currentCustomerPeriodParam() }),
      canScore
        ? getScoreRanking({ period, type: 'personal' }).catch((error) => {
            showError(error, '个人学分排行加载失败')
            return []
          })
        : Promise.resolve([]),
      canScore
        ? getScoreRanking({ period, type: 'dept' }).catch((error) => {
            showError(error, '部门学分排行加载失败')
            return []
          })
        : Promise.resolve([]),
      canScore
        ? getRankingStats({ period }).catch((error) => {
            showError(error, '学分统计加载失败')
            return {}
          })
        : Promise.resolve({}),
      // 【2026-09-23 需求同步】四项服务个人排行(按人员聚合, 与 PC 激励统计同口径)
      canIncentive
        ? getScoreboard({ period }).catch((error) => {
            showError(error, '激励个人排行加载失败')
            return []
          })
        : Promise.resolve([])
    ]).then(([rankings, personalScores, deptScores, scoreStats, incentiveBoard]) => {
      const safeRankings = {
        incentive: rankings && rankings.incentiveRanking || [],
        relationship: rankings && rankings.relationshipRanking || [],
        core: rankings && rankings.indicatorRanking || []
      }
      const scoreRankings = { personal: personalScores || [], dept: deptScores || [] }
      const parents = rankings && rankings.incentiveParents || []
      const incentiveBoardRaw = incentiveBoard || []
      // 接口返回完整数组后先做客户端分页，再生成列表视图（名次按全局偏移保留）
      const customerPaged = pagedCustomerView(safeRankings[this.data.activeTab] || [], this.data.activeTab, parents, 1, this.data.customerPageSize)
      const scorePaged = pagedScoreView(scoreRankings[this.data.scoreActiveTab] || [], this.data.scoreActiveTab, 1, this.data.scorePageSize)
      const incentivePaged = paginate(incentiveBoardRaw, 1, this.data.incentivePageSize)
      this.setRankingData({
        incentiveParents: parents,
        rankings: safeRankings,
        incentiveBoard: incentiveBoardRaw,
        incentiveBoardView: incentivePaged.rows,
        incentivePage: incentivePaged.page,
        incentivePageSize: incentivePaged.pageSize,
        incentiveTotal: incentivePaged.total,
        scoreRankings,
        scoreStats: {
          participantCount: scoreStats && scoreStats.participantCount || 0,
          deptCount: scoreStats && scoreStats.deptCount || 0,
          topScore: scoreStats && scoreStats.topScore || 0,
          topScoreName: scoreStats && scoreStats.topScoreName || '-',
          topItemCount: scoreStats && scoreStats.topItemCount || 0,
          totalItemCount: scoreStats && scoreStats.totalItemCount || 0
        },
        list: customerPaged.rows,
        customerPage: customerPaged.page,
        customerPageSize: customerPaged.pageSize,
        customerTotal: customerPaged.total,
        customerListView: customerPaged.view,
        scoreList: scorePaged.rows,
        scorePage: scorePaged.page,
        scorePageSize: scorePaged.pageSize,
        scoreTotal: scorePaged.total,
        scoreListView: scorePaged.view,
        scoreTabsView: tabView(this.data.scoreTabs, this.data.scoreActiveTab),
        tabsView: tabView(this.data.tabs, this.data.activeTab)
      })
    }).catch((error) => showError(error, '客户排行加载失败')).finally(() => this.setRankingData({ loading: false }))
  },

  refresh() { this.loadRanking() },

  // <pager> 事件 detail={page,pageSize}：各榜单独立翻页，页大小切换由组件回传 page=1
  onScorePagerChange(e) {
    const detail = e.detail || {}
    const pageSize = Number(detail.pageSize) || this.data.scorePageSize
    const page = Number(detail.page) || 1
    const paged = pagedScoreView(
      this.data.scoreRankings[this.data.scoreActiveTab] || [],
      this.data.scoreActiveTab,
      page,
      pageSize
    )
    this.setRankingData({
      scorePage: paged.page,
      scorePageSize: paged.pageSize,
      scoreTotal: paged.total,
      scoreList: paged.rows,
      scoreListView: paged.view
    })
  },

  onCustomerPagerChange(e) {
    const detail = e.detail || {}
    const pageSize = Number(detail.pageSize) || this.data.customerPageSize
    const page = Number(detail.page) || 1
    const paged = pagedCustomerView(
      this.data.rankings[this.data.activeTab] || [],
      this.data.activeTab,
      this.data.incentiveParents,
      page,
      pageSize
    )
    this.setRankingData({
      customerPage: paged.page,
      customerPageSize: paged.pageSize,
      customerTotal: paged.total,
      list: paged.rows,
      customerListView: paged.view
    })
  },

  onIncentivePagerChange(e) {
    const detail = e.detail || {}
    const pageSize = Number(detail.pageSize) || this.data.incentivePageSize
    const page = Number(detail.page) || 1
    const paged = paginate(this.data.incentiveBoard || [], page, pageSize)
    this.setRankingData({
      incentivePage: paged.page,
      incentivePageSize: paged.pageSize,
      incentiveTotal: paged.total,
      incentiveBoardView: paged.rows
    })
  },

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

const { getCustomerCounts, getCustomerList } = require('../../api/customer')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { readDemoCustomers } = require('../../utils/demo-customers')
// 【D24/D27 2026-10-04】状态文案、筛选草稿读取/参数拼装统一引用组内共享模块
const { customerStatusLabel, customerStatusTone, emptyAdvancedFilter, readFilterDraft, clearFilterDraft, parseFilterDraft, buildCustomerFilterParams } = require('./shared')

/** 列表分页大小（与服务端 page 接口约定 current/size，total 由服务端返回） */
const PAGE_SIZE = 20

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function normalizeCustomer(item) {
  const status = item.status || '正常'
  return {
    id: item.id,
    name: item.customerName || item.name || '未命名客户',
    industry: item.industry || '其他',
    ownerName: item.ownerName || item.salesperson || '未分配',
    officeName: item.officeName || item.deptName || '未设置办事处',
    status,
    statusLabel: customerStatusLabel(status),
    statusTone: customerStatusTone(status),
    devType: item.devType || '未分类',
    // 【对齐 PC 高级筛选 2026-09-21】保护/省市维度字段透传(服务端过滤 + 演示模式本地过滤共用)
    protectionStatus: item.protectionStatus || '',
    province: item.province || '',
    city: item.city || ''
  }
}

function buildCustomerRowView(item) {
  return {
    ...item,
    nameText: item.name || '未命名客户',
    idText: item.id ? `C${item.id}` : '-',
    industryText: item.industry || '其他',
    ownerOfficeText: `● ${item.ownerName || '未分配'} · ${item.officeName || '未设置办事处'}`,
    statusText: item.statusLabel || customerStatusLabel(item.status),
    statusToneClass: item.statusTone || customerStatusTone(item.status),
    devTypeText: item.devType || '未分类',
    metaText: `${item.id ? `C${item.id}` : '-'} · ${item.industry || '其他'} · 产能信息`
  }
}

function buildCustomerView(data) {
  const counts = data.countCards || []
  const customers = Array.isArray(data.customers) ? data.customers : []
  const isLoading = Boolean(data.loading)
  const isLoadingMore = Boolean(data.loadingMore)
  // 【P6 2026-10-04】总数优先用接口返回的 total(此前误把已加载条数当总数);
  // 演示模式无接口,由 loadCustomers 以本地过滤结果数兜底
  const total = Number(data.total) || customers.length
  return {
    summaryText: `全部 ${(counts[0] && counts[0].value) || '-'} · 我的 ${(counts[1] && counts[1].value) || '-'} · 共享 ${(counts[2] && counts[2].value) || '-'}`,
    filterText: `${data.filter || '全部'}⌄`,
    statusFilterText: `${(((data.statusFilters || []).find((item) => item.value === (data.statusFilter || '')) || {}).label) || '全部状态'}⌄`,
    showReset: Boolean(data.keyword || data.filter !== '全部' || data.statusFilter || data.hasAdvancedFilter),
    countText: `共 ${total} 家`,
    showLoading: isLoading,
    loadingText: '加载中...',
    customers,
    showEmpty: !isLoading && customers.length === 0,
    // 【P6 2026-10-04】触底加载更多/没有更多 的底部提示
    showLoadMore: isLoadingMore,
    loadMoreText: '加载更多中...',
    showNoMore: !isLoading && !isLoadingMore && customers.length > 0 && !data.hasMore
  }
}

Page({
  data: {
    keyword: '',
    customers: [],
    // 【P6 2026-10-04】分页状态：当前页/服务端总数/是否还有下一页/加载更多中
    currentPage: 1,
    total: 0,
    hasMore: false,
    loadingMore: false,
    countCards: [
      { label: '全部', value: '-' },
      { label: '我的', value: '-' },
      { label: '共享', value: '-' },
      { label: '公海', value: '-' }
    ],
    loading: false,
    canAdd: false,
    canViewSea: false,
    filter: '全部',
    filters: ['全部', '成熟创新', '突破开拓', '铺垫宣传'],
    // 【对齐 PC 2026-09-20】保护状态快捷筛选(PC 高级筛选「保护」同参数: active/expiring/expired)
    statusFilter: '',
    statusFilters: [
      { label: '全部状态', value: '' },
      { label: '保护中', value: 'active' },
      { label: '即将到期', value: 'expiring' },
      { label: '已到期', value: 'expired' }
    ],
    advancedFilter: emptyAdvancedFilter(),
    hasAdvancedFilter: false,
    customerView: buildCustomerView({ customers: [], countCards: [], filter: '全部', keyword: '', hasAdvancedFilter: false, loading: false })
  },

  setCustomerData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, customerView: buildCustomerView(nextData) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setCustomerData({
      canAdd: getApp().globalData.demoMode || hasPermission('customer:add'),
      canViewSea: getApp().globalData.demoMode || hasPermission('sea:view')
    })
    this.refresh()
  },

  onShow() {
    if (!this.didInitialShow) {
      this.didInitialShow = true
      return
    }
    // 【D27 2026-10-04】草稿 key/字段清单/恢复逻辑统一走 shared(此前三处字段清单各写一份,漏改即静默丢条件)
    const draft = readFilterDraft()
    if (draft) {
      clearFilterDraft()
      this.setCustomerData(parseFilterDraft(draft))
    }
    if (this.hasLoaded) this.loadCustomers()
  },

  onKeywordInput(e) {
    this.setCustomerData({ keyword: e.detail.value })
  },

  search() {
    this.loadCustomers()
  },

  openAdvancedFilter() { wx.navigateTo({ url: '/pages/customer/filter/index' }) },

  resetFilters() {
    this.setCustomerData({ keyword: '', filter: '全部', statusFilter: '', advancedFilter: emptyAdvancedFilter(), hasAdvancedFilter: false })
    this.loadCustomers()
  },

  chooseFilter(e) {
    this.setCustomerData({ filter: this.data.filters[e.detail.value] || '全部' })
    this.loadCustomers()
  },

  chooseStatusFilter(e) {
    const option = this.data.statusFilters[Number(e.detail.value) || 0] || this.data.statusFilters[0]
    this.setCustomerData({ statusFilter: option.value })
    this.loadCustomers()
  },

  refresh() {
    this.hasLoaded = true
    this.loadCounts()
    this.loadCustomers()
  },

  loadCounts() {
    if (getApp().globalData.demoMode) return
    getCustomerCounts().then((counts) => {
      this.setCustomerData({
        countCards: [
          { label: '全部', value: counts.all || 0 },
          { label: '我的', value: counts.mine || counts.self || 0 },
          { label: '共享', value: counts.shared || 0 },
          { label: '公海', value: counts.sea || 0 }
        ]
      })
    }).catch((error) => showError(error, '客户数量加载失败'))
  },

  /** 【P6 2026-10-04】分页加载：page=1 重置列表(筛选/搜索/刷新走这里)，
   *  page>1 追加(触底 onReachBottom)。此前写死 current:1/size:20，第 21 条起不可见。 */
  loadCustomers(page = 1) {
    if (getApp().globalData.demoMode) {
      const savedCustomers = readDemoCustomers()
      const rows = [
          normalizeCustomer({ id: 1, customerName: '山西晋能集团中阳煤业', industry: '煤矿', ownerName: '张三', officeName: '晋蒙业务部', status: '保护期', devType: '成熟创新' }),
          normalizeCustomer({ id: 2, customerName: '内蒙古鄂尔多斯化工', industry: '化工', ownerName: '李四', officeName: '北方业务部', status: '正常', devType: '突破开拓' })
        ].concat(savedCustomers.map(normalizeCustomer))
      const keyword = this.data.keyword.trim().toLowerCase()
      const demoStatusLabels = { active: ['保护期', '预警'], expiring: ['预警'], expired: ['超期'] }
      const statusLabels = demoStatusLabels[this.data.statusFilter]
      // 高级筛选(演示模式本地过滤, 与 PC 客户端 filterCustomers 同语义)
      const advanced = this.data.advancedFilter || {}
      const demoProtectLabels = { active: ['保护期'], expiring: ['预警'], expired: ['超期'] }
      const protectLabels = demoProtectLabels[this.data.statusFilter || advanced.protect]
      const filtered = rows.filter((item) => (!keyword || `${item.name}${item.industry}${item.ownerName}`.toLowerCase().includes(keyword)) && (this.data.filter === '全部' || item.devType === this.data.filter) && (!statusLabels || statusLabels.includes(item.statusLabel || item.status || '')) && (!advanced.industry || item.industry === advanced.industry) && (!protectLabels || protectLabels.includes(item.statusLabel || item.status || '')) && (!advanced.province || (item.province || '').includes(advanced.province)) && (!advanced.city || (item.city || '').includes(advanced.city)))
      this.setCustomerData({
        customers: filtered,
        // 演示模式一次性本地过滤完，无分页：总数=过滤结果数，无更多页
        currentPage: 1,
        total: filtered.length,
        hasMore: false,
        loadingMore: false,
        countCards: [
          { label: '全部', value: 128 + savedCustomers.length },
          { label: '我的', value: 48 + savedCustomers.length },
          { label: '共享', value: 16 },
          { label: '公海', value: 24 }
        ]
      })
      return
    }

    // 加载更多触发的请求与前一个请求并发保护（首屏 loading 由按钮/筛选入口天然防重）
    if (page > 1 && (this.data.loading || this.data.loadingMore)) return
    this.setCustomerData(page === 1 ? { loading: true, loadingMore: false } : { loadingMore: true })
    const params = { current: page, size: PAGE_SIZE, ...buildCustomerFilterParams(this.data) }
    getCustomerList(params).then((payload) => {
      const rows = normalizePage(payload).map(normalizeCustomer)
      const visible = this.data.filter === '全部' ? rows : rows.filter((item) => item.devType === this.data.filter)
      const total = Number(payload && payload.total) || 0
      // hasMore 优先以服务端 total 判定(后端已按同样条件过滤)；total 缺失时按本页是否取满兜底
      const hasMore = total ? page * PAGE_SIZE < total : rows.length >= PAGE_SIZE
      this.setCustomerData({
        customers: page === 1 ? visible : this.data.customers.concat(visible),
        currentPage: page,
        total,
        hasMore
      })
    }).catch((error) => showError(error, page === 1 ? '客户加载失败' : '加载更多失败')).finally(() => {
      this.setCustomerData({ loading: false, loadingMore: false })
    })
  },

  /** 【P6 2026-10-04】触底加载下一页 */
  onReachBottom() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.loadCustomers(this.data.currentPage + 1)
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${id}&from=customer` })
  },

  addCustomer() {
    if (!hasPermission('customer:add') && !getApp().globalData.demoMode) return wx.showToast({ title: '没有新增客户权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/customer/form/index' })
  },

  openSea() {
    if (!this.data.canViewSea) return wx.showToast({ title: '没有公海权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/sea/index' })
  }
})

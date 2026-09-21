const { getCustomerCounts, getCustomerList } = require('../../api/customer')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { readDemoCustomers } = require('../../utils/demo-customers')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function normalizeStatusKey(status) {
  return status === undefined || status === null ? '' : String(status).trim().toLowerCase()
}

function customerStatusLabel(status) {
  const labels = {
    normal: '正常',
    warning: '预警',
    protected: '保护期',
    expired: '超期',
    sea: '公海',
    active: '正常',
    available: '公海'
  }
  return labels[normalizeStatusKey(status)] || status || '正常'
}

function customerStatusTone(status) {
  const label = customerStatusLabel(status)
  if (label === '保护期' || label === '预警') return 'blue-accent'
  if (label === '超期') return 'red-accent'
  return 'green-accent'
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
  const customers = (data.customers || []).map(buildCustomerRowView)
  const isLoading = Boolean(data.loading)
  return {
    summaryText: `全部 ${(counts[0] && counts[0].value) || '-'} · 我的 ${(counts[1] && counts[1].value) || '-'} · 共享 ${(counts[2] && counts[2].value) || '-'}`,
    filterText: `${data.filter || '全部'}⌄`,
    statusFilterText: `${(((data.statusFilters || []).find((item) => item.value === (data.statusFilter || '')) || {}).label) || '全部状态'}⌄`,
    showReset: Boolean(data.keyword || data.filter !== '全部' || data.statusFilter || data.hasAdvancedFilter),
    countText: `共 ${customers.length} 家`,
    showLoading: isLoading,
    loadingText: '加载中...',
    customers,
    showEmpty: !isLoading && customers.length === 0
  }
}

Page({
  data: {
    keyword: '',
    customers: [],
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
    advancedFilter: { industry: '', status: '', deptId: '', ownerId: '', protect: '', province: '', city: '' },
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
    const draft = wx.getStorageSync('customerFilterDraft')
    if (draft) {
      wx.removeStorageSync('customerFilterDraft')
      this.setCustomerData({
        keyword: draft.keyword || '',
        filter: draft.devType || '全部',
        advancedFilter: {
          industry: draft.industry || '',
          status: draft.status || '',
          deptId: draft.deptId || '',
          ownerId: draft.ownerId || '',
          protect: draft.protect || '',
          province: draft.province || '',
          city: draft.city || ''
        },
        hasAdvancedFilter: Boolean(draft.industry || draft.status || draft.deptId || draft.ownerId || draft.protect || draft.province || draft.city)
      })
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
    this.setCustomerData({ keyword: '', filter: '全部', statusFilter: '', advancedFilter: { industry: '', status: '', deptId: '', ownerId: '', protect: '', province: '', city: '' }, hasAdvancedFilter: false })
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

  loadCustomers() {
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
        countCards: [
          { label: '全部', value: 128 + savedCustomers.length },
          { label: '我的', value: 48 + savedCustomers.length },
          { label: '共享', value: 16 },
          { label: '公海', value: 24 }
        ]
      })
      return
    }

    this.setCustomerData({ loading: true })
    getCustomerList(this.buildCustomerParams()).then((payload) => {
      const rows = normalizePage(payload).map(normalizeCustomer)
      this.setCustomerData({ customers: this.data.filter === '全部' ? rows : rows.filter((item) => item.devType === this.data.filter) })
    }).catch((error) => showError(error, '客户加载失败')).finally(() => {
      this.setCustomerData({ loading: false })
    })
  },

  buildCustomerParams() {
    const params = {
      current: 1,
      size: 20,
      keyword: this.data.keyword.trim() || undefined,
      devType: this.data.filter === '全部' ? undefined : this.data.filter
    }
    const advanced = this.data.advancedFilter || {}
    if (advanced.industry) params.industry = advanced.industry
    // 【对齐 PC 2026-09-21】status 域(normal/warning)与 protectionStatus 域(active/expiring/expired)是两个不同字段,
    // 此前快捷保护筛选误传 status=active → 后端按 status 等值匹配恒空; 现分开传参
    if (advanced.status) params.status = advanced.status
    if (advanced.deptId) params.deptId = advanced.deptId
    if (advanced.ownerId) params.ownerId = advanced.ownerId
    const protects = [this.data.statusFilter, advanced.protect].filter(Boolean)
    if (protects.length) params.protectionStatus = [...new Set(protects)].join(',')
    if (advanced.province) params.province = advanced.province.trim()
    if (advanced.city) params.city = advanced.city.trim()
    return params
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

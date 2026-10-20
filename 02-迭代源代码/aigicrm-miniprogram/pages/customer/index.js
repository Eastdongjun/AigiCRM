const { getCustomerCounts, getCustomerList } = require('../../api/customer')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { readDemoCustomers } = require('../../utils/demo-customers')
// 【D24/D27 2026-10-04】状态文案、筛选草稿读取/参数拼装统一引用组内共享模块
const { customerStatusLabel, customerStatusTone, emptyAdvancedFilter, readFilterDraft, clearFilterDraft, parseFilterDraft, buildCustomerFilterParams } = require('./shared')
// 【分页 2026-10-18】演示模式对本地过滤结果做客户端分页
const { paginate } = require('../../utils/paging')

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
  // WXML 消费 nameText/metaText/ownerOfficeText/statusText 等派生字段，必须在视图层统一派生；
  // 直接把接口行透传给视图会导致接口有数据但卡片文字全空（看起来像"加载不出来"）。
  const customers = Array.isArray(data.customers) ? data.customers.map(buildCustomerRowView) : []
  const isLoading = Boolean(data.loading)
  // 【P6 2026-10-04】总数优先用接口返回的 total；演示模式无接口,由 loadCustomers 以本地过滤结果数兜底
  const total = Number(data.total) || customers.length
  // 【C1 2026-10-19】计数卡片有值(含 0)即展示数字,仅缺卡片时用 '-' 占位;
  // 旧写法 `|| '-'` 会把真实 0 也显示成 '-',与「没有字段时明确显示 0」不一致
  const countValue = (index) => (counts[index] && counts[index].value !== undefined && counts[index].value !== null ? counts[index].value : '-')
  return {
    summaryText: `全部 ${countValue(0)} · 我的 ${countValue(1)} · 共享 ${countValue(2)}`,
    filterText: `${data.filter || '全部'}⌄`,
    statusFilterText: `${(((data.statusFilters || []).find((item) => item.value === (data.statusFilter || '')) || {}).label) || '全部状态'}⌄`,
    showReset: Boolean(data.keyword || data.filter !== '全部' || data.statusFilter || data.hasAdvancedFilter),
    countText: `共 ${total} 家`,
    showLoading: isLoading,
    pagerLoading: isLoading,
    loadingText: '加载中...',
    customers,
    showEmpty: !isLoading && customers.length === 0
  }
}

Page({
  data: {
    keyword: '',
    customers: [],
    // 【分页 2026-10-18】显式分页：当前页 / 每页条数(10,30 可切) / 服务端总数
    page: 1,
    pageSize: 10,
    total: 0,
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
    // 【A4 2026-10-08 二轮】补齐第 4 分类「新建改建扩建大修」：PC 端 2026-09-23 已加(V2026.09.23.6)，
    // 此前 MP 硬编码列表未同步，两端分类口径不一致
    filters: ['全部', '成熟创新', '突破开拓', '铺垫宣传', '新建改建扩建大修'],
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
      // 【C1 2026-10-19】/customer/list/counts 只返回 all/mine/sharedByMe/sharedToMe,
      // 不存在 shared/sea 键(旧代码恒读成 0)。共享取 sharedToMe(分享给我,即当前用户可见的共享客户),
      // 与后端 counts 同一口径;该接口不返回公海数,显式显示 0,不再读不存在的键。
      this.setCustomerData({
        countCards: [
          { label: '全部', value: counts.all || 0 },
          { label: '我的', value: counts.mine || counts.self || 0 },
          { label: '共享', value: counts.sharedToMe || 0 },
          { label: '公海', value: 0 }
        ]
      })
    }).catch((error) => showError(error, '客户数量加载失败'))
  },

  /** 【分页 2026-10-18】显式分页：任何页码都"替换"当前页数据（不再触底追加）。
   *  pageSize 支持 10/30 切换；筛选/搜索/状态/排序/刷新一律 page=1。 */
  loadCustomers(page = 1, pageSize = this.data.pageSize) {
    const size = pageSize > 0 ? pageSize : 10
    const targetPage = Math.max(1, Number(page) || 1)
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
      // 演示模式无服务端分页：对本地过滤结果做客户端分页（total=过滤结果数）
      const paged = paginate(filtered, targetPage, size)
      this.setCustomerData({
        customers: paged.rows,
        page: paged.page,
        pageSize: paged.pageSize,
        total: paged.total,
        loading: false,
        countCards: [
          { label: '全部', value: 128 + savedCustomers.length },
          { label: '我的', value: 48 + savedCustomers.length },
          { label: '共享', value: 16 },
          { label: '公海', value: 24 }
        ]
      })
      return
    }

    // 【PM5 2026-10-07】请求序号守卫(照 files 页 _filesReqSeq 模式): 筛选/搜索/翻页触发新请求时,
    // 在途旧响应不得再写列表 —— 否则慢请求迟归会覆盖新列表/把旧页数据拼进来
    const requestSeq = (this._custReqSeq = (this._custReqSeq || 0) + 1)
    // 先同步页码/页大小，分页条立即反映用户操作；总数等响应回来再更新
    this.setCustomerData({ loading: true, page: targetPage, pageSize: size })
    const params = { current: targetPage, size, ...buildCustomerFilterParams(this.data) }
    getCustomerList(params).then((payload) => {
      if (requestSeq !== this._custReqSeq) return // 已被更新请求取代, 旧响应整体丢弃
      const rows = normalizePage(payload).map(normalizeCustomer)
      const visible = this.data.filter === '全部' ? rows : rows.filter((item) => item.devType === this.data.filter)
      const rawTotal = Number(payload && payload.total)
      // total 优先用服务端总数；缺失时按"本页是否取满"兜底，保证 pager 能前进且不停在空页
      const total = rawTotal > 0
        ? rawTotal
        : (rows.length >= size ? targetPage * size + 1 : (targetPage - 1) * size + rows.length)
      const totalPages = Math.max(1, Math.ceil(total / size))
      // 页码越界（删除/筛选后总数变少）：回到最后一页，避免停在空页
      if (targetPage > totalPages) {
        this.loadCustomers(totalPages, size)
        return
      }
      this.setCustomerData({ customers: visible, page: targetPage, total })
    }).catch((error) => {
      // 【PM5】已被更新请求取代的旧失败不再弹错(新请求的结果会接管界面)
      if (requestSeq === this._custReqSeq) showError(error, '客户加载失败')
    }).finally(() => {
      if (requestSeq !== this._custReqSeq) return // loading 由最新请求的 finally 复位
      this.setCustomerData({ loading: false })
    })
  },

  /** 显式分页后不再触底追加；保留空实现避免旧触底逻辑回流 */
  onReachBottom() {},

  /** 分页条事件：切换页码/每页条数；pageSize 变化时回到第 1 页 */
  onPagerChange(e) {
    const detail = e.detail || {}
    const nextPageSize = Number(detail.pageSize) || this.data.pageSize || 10
    const nextPage = nextPageSize === this.data.pageSize ? (Number(detail.page) || 1) : 1
    this.loadCustomers(nextPage, nextPageSize)
  },

  /** 列表请求参数拼装(含分页)。筛选条件拼装同源 shared.buildCustomerFilterParams(D27 单一出处)；
   *  保留为页面方法：DevTools 自动化 verify-customer-center.mjs 通过 callMethod('buildCustomerParams') 驱动。 */
  buildCustomerParams() {
    return { current: this.data.page, size: this.data.pageSize, ...buildCustomerFilterParams(this.data) }
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

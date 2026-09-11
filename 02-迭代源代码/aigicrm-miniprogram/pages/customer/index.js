const { getCustomerCounts, getCustomerList } = require('../../api/customer')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { readDemoCustomers } = require('../../utils/demo-customers')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function normalizeCustomer(item) {
  return {
    id: item.id,
    name: item.customerName || item.name || '未命名客户',
    industry: item.industry || '其他',
    ownerName: item.ownerName || item.salesperson || '未分配',
    officeName: item.officeName || item.deptName || '未设置办事处',
    status: item.status || '正常',
    devType: item.devType || '未分类'
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
    filters: ['全部', '成熟创新', '突破开拓', '铺垫宣传']
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({
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
      this.setData({ keyword: draft.keyword || '', filter: draft.devType || '全部' })
    }
    if (this.hasLoaded) this.loadCustomers()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  search() {
    this.loadCustomers()
  },

  openAdvancedFilter() { wx.navigateTo({ url: '/pages/customer/filter/index' }) },

  resetFilters() {
    this.setData({ keyword: '', filter: '全部' })
    this.loadCustomers()
  },

  chooseFilter(e) {
    this.setData({ filter: this.data.filters[e.detail.value] || '全部' })
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
      this.setData({
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
      const filtered = rows.filter((item) => (!keyword || `${item.name}${item.industry}${item.ownerName}`.toLowerCase().includes(keyword)) && (this.data.filter === '全部' || item.devType === this.data.filter))
      this.setData({
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

    this.setData({ loading: true })
    getCustomerList({ current: 1, size: 20, keyword: this.data.keyword, devType: this.data.filter === '全部' ? '' : this.data.filter }).then((payload) => {
      const rows = normalizePage(payload).map(normalizeCustomer)
      this.setData({ customers: this.data.filter === '全部' ? rows : rows.filter((item) => item.devType === this.data.filter) })
    }).catch((error) => showError(error, '客户加载失败')).finally(() => {
      this.setData({ loading: false })
    })
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${id}` })
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

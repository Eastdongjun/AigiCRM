const { getCustomerRecords } = require('../../api/incentive')
const { showError } = require('../../utils/feedback')
const { hasPermission, requireLogin } = require('../../utils/auth')

const demoCases = [
  { id: 'case-001', customerName: '华东智能制造集团', title: '数字化供应链协同项目', category: '成熟创新', status: '已完成', score: 96, ownerName: '林晓', updatedAt: '2026-08-28', tags: ['战略咨询', '资源对接'] },
  { id: 'case-002', customerName: '远航医疗科技', title: 'AI 运营增长方案', category: '突破开拓', status: '进行中', score: 88, ownerName: '周宁', updatedAt: '2026-08-26', tags: ['增长运营', '产品共创'] },
  { id: 'case-003', customerName: '青禾消费品牌', title: '品牌渠道升级服务', category: '铺垫宣传', status: '待复盘', score: 82, ownerName: '陈默', updatedAt: '2026-08-21', tags: ['品牌升级', '渠道拓展'] }
]

Page({
  data: { keyword: '', activeCategory: '全部', categories: ['全部', '成熟创新', '突破开拓', '铺垫宣传'], cases: [], filteredCases: [], selectedCase: null, summary: { total: 0, active: 0, completed: 0, score: 0 } },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有激励查询权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadCases()
  },

  async loadCases() {
    let rows = []
    try {
      const response = await getCustomerRecords({ current: 1, size: 50 })
      rows = response?.data?.records || response?.records || []
    } catch (error) {
      rows = []
      showError(error, '案例加载失败，已显示演示数据')
    }
    const cases = rows.length ? rows.map((item, index) => this.normalizeCase(item, index)) : demoCases
    this.setData({ cases, filteredCases: this.filterCases(cases), summary: this.buildSummary(cases) })
  },

  normalizeCase(item, index) {
    return {
      id: item.id || item.recordId || `case-${index}`,
      customerName: item.customerName || item.customer?.name || '未命名客户',
      title: item.title || item.serviceName || item.productName || '客户价值服务案例',
      category: item.category || item.serviceType || '成熟创新',
      status: item.statusName || item.status || '进行中',
      score: item.score || item.totalScore || 0,
      ownerName: item.ownerName || item.createUserName || '待分配',
      updatedAt: item.updatedAt || item.updateTime || item.createTime || '',
      tags: item.tags || []
    }
  },

  buildSummary(cases) {
    const totalScore = cases.reduce((sum, item) => sum + Number(item.score || 0), 0)
    return { total: cases.length, active: cases.filter(item => item.status === '进行中').length, completed: cases.filter(item => item.status === '已完成').length, score: cases.length ? Math.round(totalScore / cases.length) : 0 }
  },

  filterCases(cases = this.data.cases) {
    const keyword = this.data.keyword.trim().toLowerCase()
    return cases.filter(item => {
      const matchesCategory = this.data.activeCategory === '全部' || item.category === this.data.activeCategory
      const text = `${item.customerName}${item.title}${item.ownerName}`.toLowerCase()
      return matchesCategory && (!keyword || text.includes(keyword))
    })
  },
  onKeywordInput(event) {
    const keyword = event.detail.value
    this.setData({ keyword, filteredCases: this.filterCases(this.data.cases) })
  },
  onCategoryTap(event) {
    const activeCategory = event.currentTarget.dataset.category
    this.setData({ activeCategory, filteredCases: this.filterCases(this.data.cases) })
  },
  openDetail(event) {
    const item = event.currentTarget.dataset.item
    if (item) wx.navigateTo({ url: `/pages/cases/detail/index?title=${encodeURIComponent(item.title)}&customer=${encodeURIComponent(item.customerName)}&category=${encodeURIComponent(item.category)}&status=${encodeURIComponent(item.status)}&score=${encodeURIComponent(item.score)}&owner=${encodeURIComponent(item.ownerName)}&updatedAt=${encodeURIComponent(item.updatedAt)}` })
  },
  closeDetail() { this.setData({ selectedCase: null }) },
  noop() {},
  openAdd() { wx.showToast({ title: '新增案例请在 PC 端操作', icon: 'none' }) },
  switchTab(event) { wx.switchTab({ url: event.currentTarget.dataset.url }) }
})

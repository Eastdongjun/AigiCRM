const { getSeaList, getSeaStatistics, getVisibleSeaConfigs, getClaimRecords } = require('../../api/sea')
const { submitApproval } = require('../../api/approval')
const { seaRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function normalizeSea(item) {
  return {
    id: item.id,
    customerId: item.customerId,
    customerName: item.customerName || '未命名客户',
    customerCode: item.customerCode || '',
    industry: item.industry || '其他',
    region: item.region || '',
    ownerName: item.originalOwnerName || '',
    enterReason: item.enterReason || '保护期满',
    enterDate: item.enterDate || '',
    status: item.status || 'available',
    canClaim: Boolean(item.canClaim),
    canEdit: Boolean(item.canEdit),
    canDelete: Boolean(item.canDelete)
  }
}

Page({
  data: {
    records: [],
    stats: {},
    pools: [],
    poolId: '',
    status: '',
    keyword: '',
    loading: false,
    claimingId: '',
    claimRecords: []
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('sea:view')) {
      wx.showToast({ title: '没有公海查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadPools()
    this.loadStats()
    this.loadRecords()
    this.loadClaimRecords()
  },

  loadPools() {
    if (getApp().globalData.demoMode) {
      this.setData({ pools: [{ id: 1, poolName: '晋蒙公海池' }] })
      return
    }
    getVisibleSeaConfigs().then((rows) => {
      this.setData({ pools: rows || [] })
    }).catch((error) => {
      this.setData({ pools: [] })
      showError(error, '公海配置加载失败')
    })
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ stats: { total: 12, newThisMonth: 4, pendingApproval: 2, claimedThisMonth: 5 } })
      return
    }
    getSeaStatistics().then((stats) => {
      this.setData({ stats: stats || {} })
    }).catch((error) => {
      this.setData({ stats: {} })
      showError(error, '公海统计加载失败')
    })
  },

  loadRecords() {
    if (getApp().globalData.demoMode) {
      this.setData({ records: seaRecords })
      return
    }

    this.setData({ loading: true })
    getSeaList({
      current: 1,
      size: 20,
      keyword: this.data.keyword,
      poolId: this.data.poolId || undefined,
      status: this.data.status || undefined
    }).then((page) => {
      const rows = page && (page.records || page.list || page.rows)
      this.setData({ records: (rows || []).map(normalizeSea) })
    }).catch((error) => showError(error, '公海加载失败')).finally(() => {
      this.setData({ loading: false })
    })
  },

  search(e) {
    this.setData({ keyword: e.detail.value || '' })
    this.loadRecords()
  },

  chooseStatus(e) {
    const statusMap = ['', 'available', 'pending_approval', 'claimed']
    this.setData({ status: statusMap[e.detail.value] || '' })
    this.loadRecords()
  },

  choosePool(e) {
    const pool = this.data.pools[e.detail.value]
    this.setData({ poolId: pool ? pool.id : '' })
    this.loadRecords()
  },

  openCustomer(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=sea` })
  },

  openClaimDetail(e) {
    const item = this.data.claimRecords[Number(e.currentTarget.dataset.index)]
    if (!item) return
    wx.navigateTo({ url: `/pages/sea/claim-detail/index?title=${encodeURIComponent(item.customerName || item.title || '捞取申请')}&status=${encodeURIComponent(item.status || '')}&createdAt=${encodeURIComponent(item.createdAt || item.createTime || '')}&reason=${encodeURIComponent(item.reason || item.comment || '')}` })
  },

  claim(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || item.status !== 'available' || !item.canClaim) return
    wx.showModal({
      title: '捞取确认',
      content: `确定捞取「${item.customerName}」？将发起审批申请`,
      success: (res) => {
        if (!res.confirm) return
        this.submitClaim(item)
      }
    })
  },

  submitClaim(item) {
    if (getApp().globalData.demoMode) {
      wx.showToast({ title: '捞取申请已提交', icon: 'success' })
      return
    }
    this.setData({ claimingId: item.id })
    submitApproval({
      type: 'claim',
      customerId: String(item.customerId),
      actionAfterApprove: '客户归属→本人'
    }).then(() => {
      wx.showToast({ title: '捞取申请已提交', icon: 'success' })
      this.loadRecords()
      this.loadStats()
      this.loadClaimRecords()
    }).catch((err) => {
      showError(err, '捞取失败')
    }).finally(() => {
      this.setData({ claimingId: '' })
    })
  },

  refresh() {
    this.loadPools()
    this.loadStats()
    this.loadRecords()
    this.loadClaimRecords()
  },

  loadClaimRecords() {
    if (getApp().globalData.demoMode) return
    getClaimRecords({ current: 1, size: 10 }).then((page) => {
      this.setData({ claimRecords: page.records || page.list || [] })
    }).catch((error) => {
      this.setData({ claimRecords: [] })
      showError(error, '认领记录加载失败')
    })
  }
})

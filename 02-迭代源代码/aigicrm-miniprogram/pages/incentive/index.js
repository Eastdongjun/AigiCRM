const { getCustomerRecords, getReadStats, markIncentiveRead, updateRecord } = require('../../api/incentive')
const { uploadFile } = require('../../api/file')
const { incentiveRecords } = require('../../data/demo')
const { hasPermission, isAdmin, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function normalizeRecords(customers) {
  return (customers || []).map((customer) => {
    const records = customer.records || []
    const latest = records.reduce((best, item) => {
      if (!best) return item
      return String(item.createdAt || '') > String(best.createdAt || '') ? item : best
    }, null)
    return {
      id: latest ? latest.id : customer.customerId,
      customerId: customer.customerId,
      recordIds: records.map((item) => item.id).filter(Boolean),
      name: customer.customerName,
      industry: customer.industry || '其他',
      score: customer.totalScore || 0,
      count: customer.count || records.length,
      category: latest ? latest.category || latest.type || latest.categoryName || '未分类' : '未分类',
      isNew: Boolean(customer.isNew),
      unread: Boolean(customer.unread || records.some((item) => item.unread)),
      summary: latest ? latest.description || latest.remark || latest.product || '暂无详情描述' : '暂无详情描述'
      ,detail: latest
    }
  })
}

Page({
  data: {
    order: 'desc',
    category: '全部',
    categories: ['全部'],
    allRecords: [],
    records: [],
    loading: false,
    readStats: [],
    canViewReadStats: false,
    canUploadEvidence: false,
    selected: null
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有激励查询权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({
      canViewReadStats: getApp().globalData.demoMode || hasPermission('incentive:read-stats') || isAdmin(),
      canUploadEvidence: !getApp().globalData.demoMode && hasPermission('incentive:add')
    })
    this.loadRecords()
    this.loadReadStats()
  },

  loadRecords() {
    const app = getApp()
    if (app.globalData.demoMode) {
      const records = incentiveRecords.map((item) => ({ category: item.category || item.type || '未分类', ...item }))
      this.setData({ allRecords: records, records, categories: ['全部', ...new Set(records.map((item) => item.category))] })
      this.sort()
      return
    }

    this.setData({ loading: true })
    getCustomerRecords().then((rows) => {
      const records = normalizeRecords(rows)
      this.setData({ allRecords: records, records, categories: ['全部', ...new Set(records.map((item) => item.category))] })
      this.sort()
    }).catch((error) => showError(error, '激励加载失败')).finally(() => {
      this.setData({ loading: false })
    })
  },

  loadReadStats() {
    if (!this.data.canViewReadStats) return
    if (getApp().globalData.demoMode) {
      this.setData({ readStats: [{ userId: 1, realName: '张三', readCount: 6, unreadCount: 2 }] })
      return
    }
    getReadStats().then((rows) => {
      this.setData({ readStats: rows || [] })
    }).catch((error) => {
      this.setData({ readStats: [] })
      showError(error, '已读统计加载失败')
    })
  },

  sort() {
    let records = this.data.allRecords.filter((item) => this.data.category === '全部' || item.category === this.data.category)
    records = [...records]
    records.sort((a, b) => this.data.order === 'asc' ? a.score - b.score : b.score - a.score)
    this.setData({ records })
  },

  chooseCategory(e) {
    this.setData({ category: this.data.categories[e.detail.value] || '全部' })
    this.sort()
  },

  choose(e) {
    const orderMap = ['', 'desc', 'asc']
    this.setData({ order: orderMap[e.detail.value] || '' })
    this.sort()
  },

  markRead(e) {
    const index = e.currentTarget.dataset.index
    const record = this.data.records[index]
    this.setData({ ['records[' + index + '].unread']: false })

    if (!getApp().globalData.demoMode && record && record.recordIds && record.recordIds.length) {
      Promise.all(record.recordIds.map((id) => markIncentiveRead(id))).then(() => {
        this.loadReadStats()
      }).catch((error) => {
        this.setData({ ['records[' + index + '].unread']: true })
        showError(error, '标记失败')
      })
    }
  },

  showDetail(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record) return
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${record.id}` })
  },

  openCustomer(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record || !record.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${record.customerId}` })
  },

  closeDetail() { this.setData({ selected: null }) },

  uploadEvidence() {
    const selected = this.data.selected
    if (!this.data.canUploadEvidence || !selected || !selected.id || getApp().globalData.demoMode) {
      wx.showToast({ title: '演示记录不可上传', icon: 'none' }); return
    }
    wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (!file) return
      wx.showLoading({ title: '上传中...' })
      uploadFile(file.path, null, 'incentive').then((uploaded) => {
        const fileId = uploaded && uploaded.id
        if (!fileId) throw new Error('上传结果缺少文件 ID')
        return updateRecord(selected.id, { fileId: JSON.stringify([fileId]) })
      }).then(() => {
        wx.showToast({ title: '上传成功', icon: 'success' })
        this.closeDetail(); this.loadRecords()
      }).catch((error) => showError(error, '上传失败')).finally(() => wx.hideLoading())
    } })
  },

  refresh() {
    this.loadRecords()
    this.loadReadStats()
  }
})

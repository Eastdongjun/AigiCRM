const { getCustomerRecords, markIncentiveRead } = require('../../../api/incentive')
const { incentiveRecords } = require('../../../data/demo')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function flatten(rows) {
  return (rows || []).flatMap((customer) => (customer.records || []).map((record) => ({ ...record, customerName: customer.customerName, industry: customer.industry || '其他', category: record.category || record.type || '未分类', score: record.score || record.points || 0 })))
}

Page({
  data: { id: '', record: null, loading: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有激励查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ id: options.id || '' })
    this.loadDetail()
  },
  loadDetail() {
    this.setData({ loading: true })
    if (getApp().globalData.demoMode) {
      const item = incentiveRecords.find((row) => String(row.id) === String(this.data.id)) || incentiveRecords[0]
      this.setData({ record: item, loading: false })
      return Promise.resolve()
    }
    return getCustomerRecords().then((rows) => {
      const record = flatten(rows).find((item) => String(item.id) === String(this.data.id))
      if (!record) throw new Error('找不到价值服务记录')
      this.setData({ record })
      if (record.unread) return markIncentiveRead(record.id)
    }).catch((error) => showError(error, '价值服务详情加载失败')).finally(() => this.setData({ loading: false }))
  },
  goBack() { wx.navigateBack() }
})

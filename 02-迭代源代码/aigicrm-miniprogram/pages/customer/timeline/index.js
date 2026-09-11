const { getCustomer, getCustomerLogs } = require('../../../api/customer')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { id: '', customerName: '客户跟进记录', logs: [], loading: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ id: options.id || '' })
    this.loadTimeline()
  },
  onPullDownRefresh() { this.loadTimeline().finally(() => wx.stopPullDownRefresh()) },
  loadTimeline() {
    if (!this.data.id) return Promise.resolve()
    this.setData({ loading: true })
    if (getApp().globalData.demoMode) {
      this.setData({ customerName: '山西晋能集团中阳煤业', logs: [{ id: 1, content: '完成现场技术交流，确认下一步合作计划。', createdAt: '2026-09-08 10:30' }, { id: 2, content: '电话跟进项目进展，客户反馈良好。', createdAt: '2026-09-05 15:20' }], loading: false })
      return Promise.resolve()
    }
    return Promise.all([getCustomer(this.data.id), getCustomerLogs(this.data.id)]).then(([customer, logs]) => {
      this.setData({ customerName: customer.customerName || customer.name || '客户跟进记录', logs: logs || [] })
    }).catch((error) => showError(error, '跟进记录加载失败')).finally(() => this.setData({ loading: false }))
  },
  goBack() { wx.navigateBack() }
})

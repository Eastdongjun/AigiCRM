const { processReminder } = require('../../../api/reminder')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { markProcessed } = require('../../../utils/demo-reminders')

Page({
  data: { id: '', title: '', content: '', customer: '', customerId: '', processing: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('reminder:view')) {
      wx.showToast({ title: '没有提醒查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ id: options.id || '', title: decodeURIComponent(options.title || '业务通知'), content: decodeURIComponent(options.content || ''), customer: decodeURIComponent(options.customer || ''), customerId: decodeURIComponent(options.customerId || '') })
  },
  openCustomer() { if (this.data.customerId) wx.navigateTo({ url: `/pages/customer/detail/index?id=${this.data.customerId}&from=notification` }) },
  process() {
    if (!this.data.id || this.data.processing) return
    if (getApp().globalData.demoMode) {
      markProcessed(this.data.id)
      return wx.showToast({ title: '已处理', icon: 'success' })
    }
    this.setData({ processing: true })
    processReminder(this.data.id).then(() => { wx.showToast({ title: '已处理', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '处理失败')).finally(() => this.setData({ processing: false }))
  },
  goBack() { wx.navigateBack() }
})

const { processReminder } = require('../../../api/reminder')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { markProcessed } = require('../../../utils/demo-reminders')

function buildNotificationDetailView(data) {
  return {
    titleText: data.title || '业务通知',
    contentText: data.content || '暂无通知内容',
    customerText: data.customer || '-',
    showCustomerAction: Boolean(data.customerId),
    processText: data.processing ? '处理中...' : '标记已处理',
    processLoading: Boolean(data.processing)
  }
}

Page({
  data: {
    id: '',
    title: '',
    content: '',
    customer: '',
    customerId: '',
    processing: false,
    notificationDetailView: buildNotificationDetailView({ processing: false })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('reminder:view')) {
      wx.showToast({ title: '没有提醒查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.applyState({ id: options.id || '', title: decodeURIComponent(options.title || '业务通知'), content: decodeURIComponent(options.content || ''), customer: decodeURIComponent(options.customer || ''), customerId: decodeURIComponent(options.customerId || '') })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, notificationDetailView: buildNotificationDetailView(next) }, callback)
  },
  openCustomer() { if (this.data.customerId) wx.navigateTo({ url: `/pages/customer/detail/index?id=${this.data.customerId}&from=notification` }) },
  process() {
    if (!this.data.id || this.data.processing) return
    if (getApp().globalData.demoMode) {
      markProcessed(this.data.id)
      return wx.showToast({ title: '已处理', icon: 'success' })
    }
    this.applyState({ processing: true })
    processReminder(this.data.id).then(() => { wx.showToast({ title: '已处理', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '处理失败')).finally(() => this.applyState({ processing: false }))
  },
  goBack() { wx.navigateBack() }
})

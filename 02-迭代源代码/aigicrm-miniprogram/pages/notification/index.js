const { getReminderList, processReminder } = require('../../api/reminder')
const { reminderRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { isProcessed, markProcessed } = require('../../utils/demo-reminders')

function normalize(item) {
  return { ...item, title: item.title || item.type || '业务提醒', content: item.content || item.message || '暂无通知内容', customerName: item.customerName || item.customer || '关联客户' }
}

Page({
  data: { notifications: [], loading: false, processing: false },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('reminder:view')) {
      wx.showToast({ title: '暂无权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.loadNotifications()
  },

  onPullDownRefresh() {
    this.loadNotifications().finally(() => wx.stopPullDownRefresh())
  },

  loadNotifications() {
    this.setData({ loading: true })
    if (getApp().globalData.demoMode) {
      this.setData({ notifications: reminderRecords.filter((item) => item.status !== 'done' && !isProcessed(item.id)).map(normalize), loading: false })
      return Promise.resolve()
    }
    return getReminderList({ page: 1, size: 50, status: 'pending' }).then((payload) => {
      const rows = Array.isArray(payload) ? payload : (payload.records || payload.list || [])
      this.setData({ notifications: rows.map(normalize) })
    }).catch((error) => showError(error, '消息加载失败')).finally(() => this.setData({ loading: false }))
  },

  openCustomer(event) {
    const item = this.data.notifications[Number(event.currentTarget.dataset.index)]
    if (item && item.customerId) wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=notification` })
  },

  openDetail(event) {
    const item = this.data.notifications[Number(event.currentTarget.dataset.index)]
    if (!item) return
    wx.navigateTo({ url: `/pages/notification/detail/index?id=${encodeURIComponent(item.id || '')}&title=${encodeURIComponent(item.title)}&content=${encodeURIComponent(item.content)}&customer=${encodeURIComponent(item.customerName)}&customerId=${encodeURIComponent(item.customerId || '')}` })
  },

  process(event) {
    const index = Number(event.currentTarget.dataset.index)
    const item = this.data.notifications[index]
    if (!item || !item.id || this.data.processing) return
    if (getApp().globalData.demoMode) {
      markProcessed(item.id)
      this.setData({ notifications: this.data.notifications.filter((_, current) => current !== index) })
      return
    }
    this.setData({ processing: true })
    processReminder(item.id).then(() => {
      this.setData({ notifications: this.data.notifications.filter((_, current) => current !== index) })
      wx.showToast({ title: '已处理', icon: 'success' })
    }).catch((error) => showError(error, '处理失败')).finally(() => this.setData({ processing: false }))
  }
})

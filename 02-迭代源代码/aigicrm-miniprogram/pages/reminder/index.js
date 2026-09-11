const { getReminderList, getExpiringList, getPendingCount, processReminder } = require('../../api/reminder')
const { reminderRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { isProcessed, markProcessed } = require('../../utils/demo-reminders')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function normalizeReminder(item) {
  return {
    id: item.id,
    customerId: item.customerId,
    customerName: item.customerName || item.title || '未命名提醒',
    type: item.type || item.reminderType || 'follow',
    status: item.status || 'pending',
    expireDate: item.expireDate || item.dueDate || '',
    content: item.content || item.remark || item.reason || '请及时处理该提醒'
  }
}

Page({
  data: {
    reminders: [],
    pendingCount: 0,
    keyword: '',
    viewMode: 'pending',
    loading: false,
    selectedReminder: null
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('reminder:view')) {
      wx.showToast({ title: '没有提醒权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.refresh()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  refresh() {
    this.loadPendingCount()
    this.loadReminders()
  },

  loadPendingCount() {
    if (getApp().globalData.demoMode) {
      this.setData({ pendingCount: reminderRecords.filter((item) => item.status === 'pending' && !isProcessed(item.id)).length })
      return
    }
    getPendingCount().then((count) => {
      this.setData({ pendingCount: count || 0 })
    }).catch((error) => showError(error, '提醒数量加载失败'))
  },

  loadReminders() {
    if (getApp().globalData.demoMode) {
      this.setData({ reminders: reminderRecords.filter((item) => !isProcessed(item.id)).map(normalizeReminder) })
      return
    }
    this.setData({ loading: true })
    const loader = this.data.viewMode === 'expiring' ? getExpiringList : getReminderList
    const params = this.data.viewMode === 'expiring'
      ? { current: 1, size: 30, keyword: this.data.keyword }
      : { current: 1, size: 30, keyword: this.data.keyword, status: 'pending' }
    loader(params).then((payload) => {
      this.setData({ reminders: normalizePage(payload).map(normalizeReminder) })
    }).catch((error) => showError(error, '提醒加载失败')).finally(() => {
      this.setData({ loading: false })
    })
  },

  search() {
    this.loadReminders()
  },

  switchView(e) {
    const viewMode = e.currentTarget.dataset.mode
    if (viewMode === this.data.viewMode) return
    this.setData({ viewMode })
    this.loadReminders()
  },

  openCustomer(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=reminder` })
  },

  openDetail(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (item) this.setData({ selectedReminder: item })
  },

  closeDetail() { this.setData({ selectedReminder: null }) },

  process(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (!item || !item.id) return
    if (getApp().globalData.demoMode) {
      markProcessed(item.id)
      this.setData({ reminders: this.data.reminders.filter((row) => row.id !== item.id), selectedReminder: null })
      this.loadPendingCount()
      wx.showToast({ title: '已处理', icon: 'success' })
      return
    }
    processReminder(item.id).then(() => {
      wx.showToast({ title: '已处理', icon: 'success' })
      this.refresh()
    }).catch((error) => showError(error, '处理失败'))
  }
})

const { requireLogin } = require('../../../utils/auth')
const { getFeedbackDraftKey } = require('../../../utils/feedback-draft')

function draftKey() { return getFeedbackDraftKey(getApp()) }

Page({
  data: { content: '', saved: false },
  onLoad() {
    if (!requireLogin()) return
    this.setData({ content: wx.getStorageSync(draftKey()) || '' })
  },
  onInput(e) { this.setData({ content: e.detail.value, saved: false }) },
  saveDraft() {
    const content = this.data.content.trim()
    if (!content) return wx.showToast({ title: '请先填写反馈内容', icon: 'none' })
    wx.setStorageSync(draftKey(), content)
    this.setData({ saved: true })
    wx.showToast({ title: '已保存草稿', icon: 'success' })
  },
  clearDraft() {
    wx.removeStorageSync(draftKey())
    this.setData({ content: '', saved: false })
  },
  goBack() { wx.navigateBack() }
})

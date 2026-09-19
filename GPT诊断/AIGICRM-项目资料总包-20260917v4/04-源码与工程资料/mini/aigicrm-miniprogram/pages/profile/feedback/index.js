const { requireLogin } = require('../../../utils/auth')
const { getFeedbackDraftKey } = require('../../../utils/feedback-draft')

function draftKey() { return getFeedbackDraftKey(getApp()) }

function buildFeedbackView(data) {
  return {
    contentValue: data.content || '',
    contentMaxLength: 2000,
    contentPlaceholder: '请描述遇到的问题、复现步骤或改进建议',
    showSavedNotice: Boolean(data.saved),
    savedNoticeText: '草稿已保存到本机，尚未提交到服务器。'
  }
}

Page({
  data: {
    content: '',
    saved: false,
    feedbackView: buildFeedbackView({ content: '', saved: false })
  },
  onLoad() {
    if (!requireLogin()) return
    this.applyState({ content: wx.getStorageSync(draftKey()) || '' })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, feedbackView: buildFeedbackView(next) }, callback)
  },
  onInput(e) { this.applyState({ content: e.detail.value, saved: false }) },
  saveDraft() {
    const content = this.data.content.trim()
    if (!content) return wx.showToast({ title: '请先填写反馈内容', icon: 'none' })
    wx.setStorageSync(draftKey(), content)
    this.applyState({ saved: true })
    wx.showToast({ title: '已保存草稿', icon: 'success' })
  },
  clearDraft() {
    wx.removeStorageSync(draftKey())
    this.applyState({ content: '', saved: false })
  },
  goBack() { wx.navigateBack() }
})

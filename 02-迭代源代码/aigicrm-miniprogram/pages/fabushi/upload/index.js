const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiBooks, createFabushiPost } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')

// 【法布施一期 2026-11-15】发布页: 心得/诵读(文字版)/正能量; 书目可选挂靠
// (附件图/音/视二期; 提交成功回书目列表/工作台)
const SCOPE_OPTIONS = [
  { value: 'heart', label: '心得' },
  { value: 'recitation', label: '诵读' },
  { value: 'znl', label: '正能量' }
]

Page({
  data: {
    scopeOptions: SCOPE_OPTIONS,
    scopeType: 'heart',
    books: [],
    bookIndex: -1,
    title: '',
    content: '',
    submitting: false,
    canCreate: false
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:create')) {
      wx.showToast({ title: '没有法布施发布权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.loadBooks()
  },

  onLoad() {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:create')) {
      wx.showToast({ title: '无发布权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
      return
    }
    this.setData({ canCreate: true })
    this.loadBooks()
  },

  loadBooks() {
    getFabushiBooks({ status: 'approved', current: 1, size: 100 }).then((res) => {
      const data = res || {}
      this.setData({ books: data.records || [] })
    })
  },

  onScope(e) {
    this.setData({ scopeType: e.currentTarget.dataset.value })
  },

  onBookChange(e) {
    this.setData({ bookIndex: Number(e.detail.value) })
  },

  onTitle(e) { this.setData({ title: e.detail.value }) },
  onContent(e) { this.setData({ content: e.detail.value }) },

  submit() {
    const { scopeType, books, bookIndex, title, content, submitting } = this.data
    if (submitting) return
    if (!title.trim() && !content.trim()) {
      wx.showToast({ title: '内容不能为空', icon: 'none' })
      return
    }
    const payload = {
      scopeType,
      title: title.trim() || null,
      content: content.trim() || null,
      bookId: bookIndex >= 0 && books[bookIndex] ? books[bookIndex].id : null
    }
    this.setData({ submitting: true })
    createFabushiPost(payload).then(() => {
      wx.showToast({ title: '发布成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 900)
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '发布失败')
    })
  }
})

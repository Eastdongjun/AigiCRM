const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead } = require('../../../api/fabushi')
const { getDownloadToken, getDownloadUrl } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')

Page({
  data: {
    id: '',
    post: {},
    attachments: [],
    comments: [],
    likeCount: 0,
    liked: false,
    canCreate: false,
    commentText: '',
    submitting: false,
    loading: true,
    dlToken: ''
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ id: options.id || '', canCreate: hasPermission('fabushi:create') })
    this.loadDetail()
  },

  loadDetail() {
    const id = this.data.id
    if (!id) return
    this.setData({ loading: true })
    getFabushiPostDetail(id).then((res) => {
      const data = res || {}
      this.setData({
        post: data.post || {},
        attachments: data.attachments || [],
        likeCount: Number(data.likeCount) || 0,
        liked: Boolean(data.likedByMe),
        loading: false
      })
      this.loadAttachmentUrls()
      markFabushiRead(id).catch(() => {})
      this.loadComments()
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '内容加载失败')
    })
  },

  loadAttachmentUrls() {
    const rows = this.data.attachments || []
    if (!rows.length) return
    getDownloadToken().then((res) => {
      const token = res && (res.token || (res.data && res.data.token)) || ''
      const attachments = rows.map((item) => ({ ...item, url: getDownloadUrl({ id: item.fileId }) + (token ? '?token=' + encodeURIComponent(token) : '') }))
      this.setData({ dlToken: token, attachments })
    }).catch(() => {})
  },

  loadComments() {
    getFabushiComments(this.data.id).then((res) => {
      this.setData({ comments: res || [] })
    }).catch(() => {})
  },

  toggleLike() {
    if (!this.data.canCreate) return
    toggleFabushiLike(this.data.id).then((res) => {
      const data = res || {}
      this.setData({ liked: Boolean(data.liked), likeCount: Number(data.likeCount) || 0 })
    }).catch((err) => showError(err, '操作失败'))
  },

  onCommentInput(e) {
    this.setData({ commentText: e.detail.value })
  },

  submitComment() {
    const content = (this.data.commentText || '').trim()
    if (!this.data.canCreate || this.data.submitting) return
    if (!content) {
      wx.showToast({ title: '评论不能为空', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    addFabushiComment(this.data.id, content).then(() => {
      this.setData({ commentText: '', submitting: false })
      wx.showToast({ title: '已评论', icon: 'success' })
      this.loadComments()
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '评论失败')
    })
  }
})

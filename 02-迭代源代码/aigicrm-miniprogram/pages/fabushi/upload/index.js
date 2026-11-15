const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiBooks, createFabushiPost, addFabushiAttachment } = require('../../../api/fabushi')
const { uploadFile } = require('../../../api/file')
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
    canCreate: false,
    success: false,
    publishedPostId: null,
    muyuAnim: false,
    attachments: [],
    uploading: false
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

  onLoad(query) {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:create')) {
      wx.showToast({ title: '无发布权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
      return
    }
    this._presetBookId = query && query.bookId ? Number(query.bookId) : null
    this.setData({ canCreate: true })
    this.loadBooks()
  },

  loadBooks() {
    getFabushiBooks({ status: 'approved', current: 1, size: 100 }).then((res) => {
      const data = res || {}
      const books = data.records || []
      const preset = this._presetBookId
      const bookIndex = preset ? books.findIndex((book) => Number(book.id) === preset) : -1
      this.setData({ books, bookIndex })
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

  chooseMedia() {
    if (this.data.uploading) return
    wx.chooseMedia({ count: 9 - this.data.attachments.length, mediaType: ['image', 'video'], success: (res) => {
      const files = (res.tempFiles || []).map((f) => ({ path: f.tempFilePath, type: f.fileType === 'video' ? 'video' : 'image', name: f.fileType === 'video' ? '视频' : '图片' }))
      this.uploadFiles(files)
    } })
  },

  chooseAudio() {
    if (this.data.uploading) return
    wx.chooseMessageFile({ count: 1, type: 'file', extension: ['mp3', 'm4a', 'wav', 'aac'], success: (res) => {
      const files = (res.tempFiles || []).map((f) => ({ path: f.path, type: 'audio', name: f.name || '音频' }))
      this.uploadFiles(files)
    } })
  },

  uploadFiles(files) {
    if (!files.length) return
    this.setData({ uploading: true })
    Promise.all(files.map((f) => uploadFile(f.path, null, 'fabushi'))).then((results) => {
      const added = results.map((res, i) => ({ fileId: res && (res.id || (res.data && res.data.id)), fileType: files[i].type, name: files[i].name })).filter((x) => x.fileId)
      this.setData({ attachments: this.data.attachments.concat(added), uploading: false })
    }).catch((err) => { this.setData({ uploading: false }); showError(err, '附件上传失败') })
  },

  removeAttachment(e) {
    const index = Number(e.currentTarget.dataset.index)
    const attachments = this.data.attachments.slice(); attachments.splice(index, 1); this.setData({ attachments })
  },

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
    createFabushiPost(payload).then(async (res) => {
      const id = res && (res.id || (res.data && res.data.id))
      if (id && this.data.attachments.length) {
        await Promise.all(this.data.attachments.map((a) => addFabushiAttachment(id, { fileId: a.fileId, fileType: a.fileType })))
      }
      this.setData({ submitting: false, success: true, publishedPostId: id || null, muyuAnim: true })
      if (wx.vibrateShort) wx.vibrateShort({ type: 'light' })
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '发布失败')
    })
  },

  replayMuyu() {
    this.setData({ muyuAnim: false })
    setTimeout(() => {
      this.setData({ muyuAnim: true })
      if (wx.vibrateShort) wx.vibrateShort({ type: 'light' })
    }, 40)
  },

  goDetail() {
    if (this.data.publishedPostId) {
      wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${this.data.publishedPostId}` })
    } else {
      wx.navigateBack()
    }
  },

  goBack() { wx.navigateBack() }
})

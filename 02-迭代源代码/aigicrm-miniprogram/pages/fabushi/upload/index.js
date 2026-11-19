const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiBooks, createFabushiPost, addFabushiAttachment, getFabushiSettings } = require('../../../api/fabushi')
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
    uploading: false,
    soundEnabled: true,
    attachHint: '图片 0/5 · 视频 0/5 · 语音 0/10'
  },

  onShow() {
    // 【F1-3 木鱼音效】拉设置开关(失败保持默认开)
    getFabushiSettings().then((s) => {
      if (s && s.soundEnabled !== undefined) this.setData({ soundEnabled: Number(s.soundEnabled) !== 0 })
    }).catch(() => {})
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
    // 【F1 限额】图/视各≤5, 按剩余量分型截断(spec v2: 图1-5张/视1-5段)
    const counts = this.attachmentCounts()
    const imgLeft = 5 - counts.image
    const vidLeft = 5 - counts.video
    if (imgLeft <= 0 && vidLeft <= 0) {
      wx.showToast({ title: '图片/视频已达上限', icon: 'none' })
      return
    }
    const count = Math.max(Math.min(9 - this.data.attachments.length, Math.max(imgLeft, vidLeft)), 1)
    wx.chooseMedia({ count, mediaType: ['image', 'video'], success: (res) => {
      const picked = (res.tempFiles || []).map((f) => ({ path: f.tempFilePath, type: f.fileType === 'video' ? 'video' : 'image', name: f.fileType === 'video' ? '视频' : '图片' }))
      const files = []
      let imgAdded = 0
      let vidAdded = 0
      let imgCut = false
      let vidCut = false
      picked.forEach((f) => {
        if (f.type === 'image') {
          if (imgAdded < imgLeft) { files.push(f); imgAdded++ } else { imgCut = true }
        } else if (vidAdded < vidLeft) { files.push(f); vidAdded++ } else { vidCut = true }
      })
      if (imgCut) wx.showToast({ title: '图片最多5张，已保留前5', icon: 'none' })
      else if (vidCut) wx.showToast({ title: '视频最多5段，已保留前5', icon: 'none' })
      this.uploadFiles(files)
    } })
  },

  chooseAudio() {
    if (this.data.uploading) return
    if (this.attachmentCounts().audio >= 10) {
      wx.showToast({ title: '语音已达上限10段', icon: 'none' })
      return
    }
    wx.chooseMessageFile({ count: 1, type: 'file', extension: ['mp3', 'm4a', 'wav', 'aac'], success: (res) => {
      const files = (res.tempFiles || []).map((f) => ({ path: f.path, type: 'audio', name: f.name || '音频' }))
      this.uploadFiles(files)
    } })
  },

  attachmentCounts() {
    const counts = { image: 0, video: 0, audio: 0 }
    this.data.attachments.forEach((a) => { if (counts[a.fileType] !== undefined) counts[a.fileType]++ })
    return counts
  },

  syncAttachHint() {
    const c = this.attachmentCounts()
    this.setData({ attachHint: `图片 ${c.image}/5 · 视频 ${c.video}/5 · 语音 ${c.audio}/10` })
  },

  uploadFiles(files) {
    if (!files.length) return
    this.setData({ uploading: true })
    Promise.all(files.map((f) => uploadFile(f.path, null, 'fabushi'))).then((results) => {
      const added = results.map((res, i) => ({ fileId: res && (res.id || (res.data && res.data.id)), fileType: files[i].type, name: files[i].name })).filter((x) => x.fileId)
      this.setData({ attachments: this.data.attachments.concat(added), uploading: false })
      this.syncAttachHint()
    }).catch((err) => { this.setData({ uploading: false }); showError(err, '附件上传失败') })
  },

  removeAttachment(e) {
    const index = Number(e.currentTarget.dataset.index)
    const attachments = this.data.attachments.slice(); attachments.splice(index, 1); this.setData({ attachments })
    this.syncAttachHint()
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
      this.playMuyuSound()
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
      this.playMuyuSound()
    }, 40)
  },

  // 【F1-3 木鱼音效】懒建单例; 开关=设置页 soundEnabled(onShow 拉取)
  playMuyuSound() {
    if (!this.data.soundEnabled) return
    try {
      if (!this._muyuAudio) {
        this._muyuAudio = wx.createInnerAudioContext()
        this._muyuAudio.src = '/assets/audio/muyu.mp3'
      }
      this._muyuAudio.stop()
      this._muyuAudio.play()
    } catch (e) { /* 音效失败不阻断主流程 */ }
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

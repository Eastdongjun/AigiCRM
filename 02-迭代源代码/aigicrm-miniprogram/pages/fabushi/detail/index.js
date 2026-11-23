const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead, updateFabushiPost, deleteFabushiPost, getFabushiRecitations, addFabushiRecitation } = require('../../../api/fabushi')
const { getDownloadToken, getDownloadUrl, uploadFile } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')

Page({
  data: {
    id: '',
    post: {},
    attachments: [],
    comments: [],
    commentsTotal: 0,
    hasMoreComments: false,
    likeCount: 0,
    liked: false,
    canCreate: false,
    commentText: '',
    submitting: false,
    highlightChecked: false,
    voiceMode: false,
    recSeconds: 0,
    isOwner: false,
    editing: false,
    editContent: '',
    reciteMode: false,
    recitations: [],
    recitationCount: 0,
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

  onHide() { this.discardRecording() },
  onUnload() { this.discardRecording() },

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
        loading: false,
        // 【D13 计划切片 2】仅作者本人显示"管理"（修改/删除）；admin 不代编辑
        isOwner: Boolean((data.post || {}).userId) && String((data.post || {}).userId) === String((getApp().globalData.currentUser || {}).userId || '')
      })
      this.loadAttachmentUrls()
      this.setData({ editing: false, editContent: '' })
      this.loadRecitations()
      markFabushiRead(id).then(() => {
        // 【F4a】读过了 → 清法布施 tabBar 红点
        const { setUnreadDot } = require('../../../utils/fabushi-reminder')
        setUnreadDot(false)
      }).catch(() => {})
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
      const comments = res || []
      // 【F2】audio 评论补可播 URL(与附件同 download-token 通道)
      const needUrl = comments.some((c) => c.type === 'audio' && c.audioFileId)
      if (!needUrl) { this._allComments = comments; this.showFirstComments(comments); return }
      getDownloadToken().then((r) => {
        const token = r && (r.token || (r.data && r.data.token)) || ''
        const rows = comments.map((c) => (c.type === 'audio' && c.audioFileId
          ? { ...c, audioUrl: getDownloadUrl({ id: c.audioFileId }) + (token ? '?token=' + encodeURIComponent(token) : '') }
          : c))
        this._allComments = rows
        this.showFirstComments(rows)
      }).catch(() => { this._allComments = comments; this.showFirstComments(comments) })
    }).catch(() => {})
  },

  showFirstComments(rows) {
    const pageSize = 10
    this._commentPage = 1
    this.setData({
      comments: rows.slice(0, pageSize),
      commentsTotal: rows.length,
      hasMoreComments: rows.length > pageSize
    })
  },

  loadMoreComments() {
    const all = this._allComments || []
    const pageSize = 10
    const page = (this._commentPage || 1) + 1
    this._commentPage = page
    const shown = all.slice(0, page * pageSize)
    this.setData({ comments: shown, hasMoreComments: shown.length < all.length })
  },

  toggleLike() {
    if (!this.data.canCreate) return
    toggleFabushiLike(this.data.id).then((res) => {
      const data = res || {}
      this.setData({ liked: Boolean(data.liked), likeCount: Number(data.likeCount) || 0 })
    }).catch((err) => showError(err, '操作失败'))
  },

  // ===== D13 计划切片 2：本人修改/删除（仅作者本人；软删不断文件） =====
  startEdit() {
    if (!this.data.isOwner) return
    this.setData({ editing: true, editContent: (this.data.post && this.data.post.content) || '' })
  },

  cancelEdit() {
    this.setData({ editing: false, editContent: '' })
  },

  onEditInput(e) {
    this.setData({ editContent: e.detail.value })
  },

  saveEdit() {
    if (!this.data.isOwner || this.data.savingEdit) return
    const content = (this.data.editContent || '').trim()
    const title = (this.data.post && this.data.post.title) || null
    if (!content && !title) {
      wx.showToast({ title: '内容不能为空', icon: 'none' })
      return
    }
    if ((this.data.post || {}).scopeType === 'suishi' && title) {
      // suishi 无标题（后端分型校验保持）
    }
    this.setData({ savingEdit: true })
    updateFabushiPost(this.data.id, { title: title, content: content || null }).then(() => {
      this.setData({ savingEdit: false, editing: false })
      wx.showToast({ title: '已保存', icon: 'success' })
      this.loadDetail()
    }).catch((err) => {
      this.setData({ savingEdit: false })
      showError(err, '保存失败')
    })
  },

  removePost() {
    if (!this.data.isOwner) return
    wx.showModal({
      title: '删除内容',
      content: '删除后可在 PC 内容治理（已删除）中找回，附件保留在文件中心。确认删除？',
      confirmColor: '#e11d48',
      success: (res) => {
        if (!res.confirm) return
        deleteFabushiPost(this.data.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          setTimeout(() => wx.navigateBack(), 400)
        }).catch((err) => showError(err, '删除失败'))
      }
    })
  },

  // ===== D14 计划切片 3：复诵（同人同帖可多次；录音复用 initRecorder，mp3 ≤60s） =====
  loadRecitations() {
    const id = this.data.id
    if (!id) return
    Promise.resolve(getDownloadToken()).then((token) => {
      const tk = token || this.data.dlToken || ''
      return getFabushiRecitations(id).then((res) => {
        const rows = res || []
        // 复诵音频回填 download-token URL（与附件同机制）
        rows.forEach((r) => { r.url = `/api/file/download/${r.audioFileId}?token=${encodeURIComponent(tk)}` })
        this.setData({ recitations: rows, recitationCount: rows.length })
      })
    }).catch(() => this.setData({ recitations: [], recitationCount: 0 }))
  },

  toggleRecite() {
    if (!this.data.canCreate) return
    if (this.data.reciteMode) {
      if (this._recorder) this._recorder.stop()
      return
    }
    this.initRecorder()
    this._reciteMode = true
    this.setData({ reciteMode: true, voiceMode: false, highlightChecked: false })
    this._recorder.start({ format: 'mp3', duration: 60000, sampleRate: 44100, numberOfChannels: 1, encodeBitRate: 96000 })
    wx.showToast({ title: '复诵录音中…', icon: 'none' })
  },

  submitRecitation(path, seconds) {
    uploadFile(path, null, 'fabushi').then((res) => {
      const fileId = res && (res.id || (res.data && res.data.id))
      if (!fileId) throw new Error('upload no fileId')
      return addFabushiRecitation(this.data.id, { audioFileId: fileId, durationSeconds: seconds || null })
    }).then(() => {
      this.setData({ reciteMode: false })
      this._reciteMode = false
      wx.showToast({ title: '复诵已挂到本内容下', icon: 'success' })
      this.loadRecitations()
    }).catch((err) => {
      this.setData({ reciteMode: false })
      this._reciteMode = false
      showError(err, '复诵失败')
    })
  },

  onCommentInput(e) {
    this.setData({ commentText: e.detail.value })
  },

  toggleHighlight() {
    if (this.data.voiceMode) return
    this.setData({ highlightChecked: !this.data.highlightChecked })
  },

  submitComment() {
    const content = (this.data.commentText || '').trim()
    if (!this.data.canCreate || this.data.submitting) return
    if (!content) {
      wx.showToast({ title: '评论不能为空', icon: 'none' })
      return
    }
    // 【F2 拍板③】重点描述=评论类型 highlight(全员可设), 其余默认 text
    const payload = { content, type: this.data.highlightChecked ? 'highlight' : 'text' }
    this.setData({ submitting: true })
    addFabushiComment(this.data.id, payload).then(() => {
      this.setData({ commentText: '', submitting: false, highlightChecked: false })
      wx.showToast({ title: '已评论', icon: 'success' })
      this.loadComments()
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '评论失败')
    })
  },

  // ===== 【F2 语音诵读评论】单段 ≤60s; 停止即上传+提交, <1s 丢弃; 页面隐藏丢弃不自动发 =====

  initRecorder() {
    if (this._recorderInited) return
    this._recorderInited = true
    const rm = wx.getRecorderManager()
    rm.onStart(() => {
      this.setData({ recSeconds: 0 })
      this._recTimer = setInterval(() => this.setData({ recSeconds: this.data.recSeconds + 1 }), 1000)
    })
    rm.onStop((res) => {
      if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
      const discard = this._discardOnStop
      this._discardOnStop = false
      if (discard) { this.setData({ voiceMode: false, reciteMode: false }); this._reciteMode = false; return }
      const durationMs = (res && res.duration) || 0
      if (durationMs < 1000) {
        this.setData({ voiceMode: false, reciteMode: false })
        this._reciteMode = false
        wx.showToast({ title: '时长太短，已丢弃', icon: 'none' })
        return
      }
      if (this._reciteMode) {
        this.submitRecitation(res.tempFilePath, Math.round(durationMs / 1000))
        return
      }
      this.submitVoiceComment(res.tempFilePath)
    })
    rm.onError((err) => {
      if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
      this.setData({ voiceMode: false, reciteMode: false })
      this._reciteMode = false
      const msg = (err && err.errMsg) || ''
      if (msg.indexOf('auth') >= 0 || msg.indexOf('deny') >= 0) {
        wx.showModal({
          title: '需要麦克风权限',
          content: '语音诵读需授权麦克风，请在设置中开启',
          confirmText: '去设置',
          success: (r) => { if (r.confirm) wx.openSetting() }
        })
      } else {
        wx.showToast({ title: '录音失败', icon: 'none' })
      }
    })
    this._recorder = rm
  },

  enterVoice() {
    if (!this.data.canCreate || this.data.submitting) return
    this.initRecorder()
    this.setData({ voiceMode: true, highlightChecked: false, recSeconds: 0 })
    this._recorder.start({ format: 'mp3', duration: 60000, sampleRate: 44100, numberOfChannels: 1, encodeBitRate: 96000 })
  },

  stopVoice() {
    if (this._recorder) this._recorder.stop()
  },

  discardRecording() {
    if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
    if ((this.data.voiceMode || this.data.reciteMode) && this._recorder) {
      this._discardOnStop = true
      this._recorder.stop()
    }
  },

  submitVoiceComment(path) {
    this.setData({ submitting: true })
    uploadFile(path, null, 'fabushi').then((res) => {
      const fileId = res && (res.id || (res.data && res.data.id))
      if (!fileId) throw new Error('upload no fileId')
      const content = (this.data.commentText || '').trim()
      return addFabushiComment(this.data.id, { type: 'audio', audioFileId: fileId, content: content || undefined })
    }).then(() => {
      this.setData({ commentText: '', submitting: false, voiceMode: false })
      wx.showToast({ title: '已评论', icon: 'success' })
      this.loadComments()
    }).catch((err) => {
      this.setData({ submitting: false, voiceMode: false })
      showError(err, '语音评论失败')
    })
  }
})

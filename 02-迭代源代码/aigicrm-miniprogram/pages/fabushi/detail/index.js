const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead } = require('../../../api/fabushi')
const { getDownloadToken, getDownloadUrl, uploadFile } = require('../../../api/file')
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
    highlightChecked: false,
    voiceMode: false,
    recSeconds: 0,
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
      const comments = res || []
      // 【F2】audio 评论补可播 URL(与附件同 download-token 通道)
      const needUrl = comments.some((c) => c.type === 'audio' && c.audioFileId)
      if (!needUrl) { this.setData({ comments }); return }
      getDownloadToken().then((r) => {
        const token = r && (r.token || (r.data && r.data.token)) || ''
        this.setData({
          comments: comments.map((c) => (c.type === 'audio' && c.audioFileId
            ? { ...c, audioUrl: getDownloadUrl({ id: c.audioFileId }) + (token ? '?token=' + encodeURIComponent(token) : '') }
            : c))
        })
      }).catch(() => this.setData({ comments }))
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
      if (discard) { this.setData({ voiceMode: false }); return }
      const durationMs = (res && res.duration) || 0
      if (durationMs < 1000) {
        this.setData({ voiceMode: false })
        wx.showToast({ title: '时长太短，已丢弃', icon: 'none' })
        return
      }
      this.submitVoiceComment(res.tempFilePath)
    })
    rm.onError((err) => {
      if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
      this.setData({ voiceMode: false })
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
    if (this.data.voiceMode && this._recorder) {
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

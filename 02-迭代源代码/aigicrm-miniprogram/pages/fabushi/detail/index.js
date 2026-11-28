const { hasPermission, requireLogin } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead, updateFabushiPost, deleteFabushiPost, getFabushiRecitations, addFabushiRecitation, removeFabushiRecitation } = require('../../../api/fabushi')
const { getDownloadToken, getDownloadUrl, uploadFile } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')
// 【2026-11-25 用户裁决】界面按原型 v7 重做外观: 页面上的文字(类型名、时间、谁复诵了)由这个纯函数模块从接口数据算出来;
//   下载令牌的取法(downloadTokenOf)也在那里 —— 令牌接口回来的可能是 {token} / {data:{token}} / 字符串
const vm = require('../../../utils/fabushi-view-model')
const tokenOf = vm.downloadTokenOf

Page({
  data: {
    id: '',
    post: {},
    typeLabel: '',
    timeText: '',
    isNew: false,
    attachments: [],
    imageUrls: [],
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
    intoView: '',
    // 复诵条上的一行小字: 前两位是谁、各多长
    reciteText: '',
    loading: true,
    dlToken: ''
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127 第二批】冷启动回填也会触发本回调: 页面未初始化时跳过, 避免误踢/双拉。
    if (!this._loadedOnce) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
  },

  onLoad(options) {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!getApp().globalData.demoMode && !hasPermission('fabushi:view')) {
        wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 300)
        return
      }
      this.setData({ id: options.id || '', canCreate: hasPermission('fabushi:create') })
      // 认知广场的 ⋯ 菜单跳过来时可以指定落点: comment = 评论输入框, recite = 复诵条
      this._focus = options.focus || ''
      this.loadDetail()
      this._loadedOnce = true
    })
  },

  onHide() { this.discardRecording() },
  onUnload() { this.discardRecording() },

  loadDetail() {
    const id = this.data.id
    if (!id) return
    this.setData({ loading: true })
    getFabushiPostDetail(id).then((res) => {
      const data = res || {}
      const post = data.post || {}
      // 标题按类型写(原型 A4 的例子是“再读作品”)
      wx.setNavigationBarTitle({ title: vm.WORK_TITLE[post.scopeType] || '作品详情' })
      this.setData({
        post,
        // WXML 里的 typeLabel / timeText / isNew: 类型名、时间(今天 09:30 / 昨天 18:40)、30 天内标 NEW
        typeLabel: vm.SCOPE_LABEL[post.scopeType] || '内容',
        timeText: vm.timeText(post.createdAt),
        isNew: Boolean(post.isNew),
        attachments: data.attachments || [],
        imageUrls: [],
        likeCount: Number(data.likeCount) || 0,
        liked: Boolean(data.likedByMe),
        loading: false,
        // 【D13 计划切片 2】仅作者本人显示"管理"（修改/删除）；admin 不代编辑
        isOwner: Boolean(post.userId) && String(post.userId) === String((getApp().globalData.currentUser || {}).userId || '')
      })
      this.loadAttachmentUrls()
      this.setData({ editing: false, editContent: '' })
      this.loadRecitations()
      this.scrollToFocus()
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
      const token = tokenOf(res)
      const attachments = rows.map((item) => ({ ...item, url: getDownloadUrl({ id: item.fileId }) + (token ? '?token=' + encodeURIComponent(token) : '') }))
      // 书页(图片)单独列一份地址: 九宫格里按顺序显示, 点击放大查看
      this.setData({ dlToken: token, attachments, imageUrls: attachments.filter((item) => item.fileType === 'image').map((item) => item.url) })
    }).catch(() => {})
  },

  loadComments() {
    getFabushiComments(this.data.id).then((res) => {
      const comments = res || []
      // 【F2】audio 评论补可播 URL(与附件同 download-token 通道)
      const needUrl = comments.some((c) => c.type === 'audio' && c.audioFileId)
      const decorate = (token) => comments.map((c) => {
        // 头像首字与底色、评论类型名是 WXML 按 item.xxx 读的派生字段, 逐个写出(视图绑定契约要的就是这个“生产处”)
        const view = {
          avatarText: String(c.userName || '同').slice(0, 1),
          avatarTone: vm.coverTone(c.userId),
          typeLabel: c.type === 'highlight' ? '重点描述' : (c.type === 'audio' ? '语音诵读' : ''),
          typeClass: c.type === 'highlight' ? 'cyan' : (c.type === 'audio' ? 'songdu' : '')
        }
        if (c.type === 'audio' && c.audioFileId) {
          return { ...c, ...view, audioUrl: getDownloadUrl({ id: c.audioFileId }) + (token ? '?token=' + encodeURIComponent(token) : '') }
        }
        return { ...c, ...view }
      })
      if (!needUrl) { this._allComments = decorate(''); this.showFirstComments(this._allComments); return }
      getDownloadToken().then((r) => {
        this._allComments = decorate(tokenOf(r))
        this.showFirstComments(this._allComments)
      }).catch(() => { this._allComments = decorate(''); this.showFirstComments(this._allComments) })
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
      content: '删除后可在 PC 内容管理（已删除）中找回，附件保留在文件中心。确认删除？',
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
    getDownloadToken().catch(() => null).then((res) => {
      const token = tokenOf(res) || this.data.dlToken || ''
      return getFabushiRecitations(id).then((list) => {
        const rows = list || []
        // 复诵音频回填可播地址: 与附件同一条通道(下载地址 + 下载令牌)。
        // 【2026-11-25 修正】原来只有域名后面那一段路径(前面没有接口域名, 小程序里放不出声音), 而且令牌的位置放的是
        //   整个应答对象(拼出来是 object Object) —— 复诵列表里的音频一直播不出来。
        // 【FABUSHI-DELTA-20261128 R5】自己的复诵行才给「移除」入口(服务端仍强校验发起人本人)。
        const myId = String((getApp().globalData.currentUser || {}).userId || '')
        rows.forEach((r) => {
          r.url = r.audioFileId ? getDownloadUrl({ id: r.audioFileId }) + (token ? '?token=' + encodeURIComponent(token) : '') : ''
          r.canRemove = !!myId && String(r.userId) === myId
        })
        this.setData({ recitations: rows, recitationCount: rows.length, reciteText: vm.reciteText(rows) })
      })
    }).catch(() => this.setData({ recitations: [], recitationCount: 0, reciteText: '' }))
  },

  // 落点(认知广场的 ⋯ 菜单跳过来时可以指定): 滚到评论输入框或复诵条。
  // 用页面里滚动容器的 scroll-into-view, 不用 wx.pageScrollTo 这个接口 —— 导航契约禁用它(会破坏返回时的滚动位置保持)。
  scrollToFocus() {
    const target = this._focus === 'comment' ? 'comment-box' : (this._focus === 'recite' ? 'recite-strip' : '')
    this._focus = ''
    if (!target) return
    wx.nextTick(() => this.scrollInto(target))
  },

  // 先清空再设: 连续两次滚到同一处也能触发
  scrollInto(target) {
    this.setData({ intoView: '' }, () => this.setData({ intoView: target }))
  },

  focusComments() {
    this.scrollInto('comment-box')
  },

  // 书页点击放大查看(原型 A4 · D12)
  previewImage(e) {
    const urls = (this.data.attachments || []).filter((item) => item.fileType === 'image' && item.url).map((item) => item.url)
    const current = urls[Number(e.currentTarget.dataset.index) || 0]
    if (!current) return
    wx.previewImage({ current, urls })
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
    // 【FABUSHI-DELTA-20261128 R1】单段上限 5 分钟(300000ms), 到时自动停走 onStop
    this._recorder.start({ format: 'mp3', duration: 300000, sampleRate: 44100, numberOfChannels: 1, encodeBitRate: 96000 })
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

  // 【FABUSHI-DELTA-20261128 R5】移除自己的复诵（服务端强校验发起人；音频随行软删进回收站）
  onRemoveRecitation(e) {
    const rid = Number(e.currentTarget.dataset.id)
    const row = (this.data.recitations || []).find((r) => Number(r.id) === rid)
    if (!row || !row.canRemove) return
    wx.showModal({
      title: '移除复诵',
      content: '移除后这段复诵录音将进入文件回收站，确定移除？',
      confirmText: '移除',
      confirmColor: '#c0392b',
      success: (res) => {
        if (!res.confirm) return
        removeFabushiRecitation(this.data.id, rid).then(() => {
          wx.showToast({ title: '已移除', icon: 'success' })
          this.loadRecitations()
        }).catch((err) => showError(err, '移除失败'))
      }
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
    // 【FABUSHI-DELTA-20261128 R1】单段上限 5 分钟(300000ms)
    this._recorder.start({ format: 'mp3', duration: 300000, sampleRate: 44100, numberOfChannels: 1, encodeBitRate: 96000 })
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

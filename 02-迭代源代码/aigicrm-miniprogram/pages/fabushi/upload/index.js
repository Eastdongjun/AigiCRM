const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiBooks, createFabushiPost, addFabushiAttachment, getFabushiSettings, getFabushiUploadTicket, getFabushiSections, createFabushiSectionSelf } = require('../../../api/fabushi')
const { uploadFile, getDownloadToken, getDownloadUrl } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')
// 【GL-12 2026-11-24】选图后进图片编辑器(放大查看 + 裁剪): 队列、开关、回传的纯函数
const imageEditor = require('../../../utils/fabushi-image-editor')
// 【2026-11-25 用户裁决】界面按原型 v7 重做外观: 导航标题、成功页的文字、下载令牌的取法由这个纯函数模块统一
const vm = require('../../../utils/fabushi-view-model')

// 【法布施一期 2026-11-15】发布页: 心得/诵读(文字版)/正能量; 书目可选挂靠
// (附件图/音/视二期; 提交成功回书目列表/工作台)
const SCOPE_OPTIONS = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]

Page({
  data: {
    scopeOptions: SCOPE_OPTIONS,
    scopeType: 'songdu',
    // 【PROTO-AUDIT-20261123 P6/P8】通道 A/B + 三级选书（类别→书目→版本）+ 章节自录
    channel: 'A',
    books: [],
    categories: [],
    categoryIndex: -1,
    titles: [],
    titleIndex: -1,
    editions: [],
    editionIndex: -1,
    bookIndex: -1,
    // 【2026-11-26 按原型 v7(B4)】选书是一行合并的三列选择器: bookPicker = [类别列, 书目列, 版本列],
    // bookPickerIndex = 三列各自的位置, bookSummary = 选好后那一行上的文字“类别 ·《书目》· 版本”
    bookPicker: [[], [], []],
    bookPickerIndex: [0, 0, 0],
    bookSummary: '',
    manualSection: '',
    useManualSection: false,
    sections: [],
    sectionNames: [],
    sectionIndex: -1,
    title: '',
    content: '',
    submitting: false,
    canCreate: false,
    success: false,
    publishedPostId: null,
    muyuAnim: false,
    // 成功页的文字(原型 B5): “每日诵读已发布” + 一句说明
    successTitle: '',
    successSummary: '',
    attachments: [],
    imageAttachments: [],
    uploading: false,
    soundEnabled: true,
    attachHint: '图片 0/5 · 视频 0/5 · 语音 0/10',
    recording: false,
    recordSeconds: 0,
    playingIndex: -1,
    // 【GL-12】选图后裁剪的开关(记在本机存储, 默认开)
    editOnPick: true
  },

  onShow() {
    // 【F3a】消费 H5 上传回传清单(容器页 bindmessage → globalData 中转)
    const app = getApp()
    const manifest = app.globalData.fabushiUploadFiles
    if (manifest && manifest.length) {
      app.globalData.fabushiUploadFiles = null
      this.consumeH5Files(manifest)
    }
    // 【GL-12】从图片编辑页回来: 取走这一张的结果, 继续下一张或开始上传
    this.consumeImageEditResult()
    // 【F1-3 木鱼音效】拉设置开关(失败保持默认开)
    getFabushiSettings().then((s) => {
      if (s && s.soundEnabled !== undefined) this.setData({ soundEnabled: Number(s.soundEnabled) !== 0 })
    }).catch(() => {})
  },

  onHide() {
    // 【F2 录音】页面隐藏时保段: 停录且 ≥1s 段走 onStop 正常入库
    if (this.data.recording && this._recorder) this._recorder.stop()
  },

  onUnload() {
    if (this._recTimer) clearInterval(this._recTimer)
    if (this.data.recording && this._recorder) this._recorder.stop()
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
    // 【D7 切片 6】觉醒三板块直达对应发布类型
    const presetScope = query && query.scope
    const scopeType = SCOPE_OPTIONS.some((item) => item.value === presetScope) ? presetScope : 'songdu'
    this.setData({ canCreate: true, scopeType, editOnPick: this.readEditOnPick() })
    this.syncNavTitle(scopeType)
    // 三列选择器里第一列(类别)当前停在哪个类别、第二列列出的是哪个类别下的书目
    this._pickerCategory = 0
    this._pickerTitles = null
    this.loadBooks()
  },

  // 导航标题随类型走(原型 B2/B3/B4: “发布 · 每日诵读”)
  syncNavTitle(scopeType) {
    const option = SCOPE_OPTIONS.find((item) => item.value === scopeType)
    wx.setNavigationBarTitle({ title: option ? '发布 · ' + option.label : '发布' })
  },

  readEditOnPick() {
    let stored = ''
    try { stored = wx.getStorageSync(imageEditor.EDIT_ON_PICK_KEY) } catch (e) { /* 读不到存储按默认开 */ }
    return imageEditor.readEditOnPick(stored)
  },

  toggleEditOnPick() {
    const editOnPick = !this.data.editOnPick
    try { wx.setStorageSync(imageEditor.EDIT_ON_PICK_KEY, editOnPick) } catch (e) { /* 存储失败只影响下次进页面的默认值 */ }
    this.setData({ editOnPick })
  },

  // ===== 【GL-12】选图后进图片编辑器: 逐张排队; 队列走完再把整批(含视频)交给原有上传 =====

  startImageEdit(files) {
    if (!files.length) return
    this._editQueue = imageEditor.createEditQueue(files, this.data.editOnPick)
    this._editNavigateFailed = false
    this.advanceImageEdit()
  },

  advanceImageEdit() {
    const queue = this._editQueue
    if (!queue) return
    const target = this._editNavigateFailed ? -1 : imageEditor.currentEditTarget(queue)
    if (target < 0) {
      this._editQueue = null
      this._editRequest = null
      this.uploadFiles(queue.files)
      return
    }
    const requestId = 'img-' + Date.now() + '-' + ((this._editSeq || 0) + 1)
    this._editSeq = (this._editSeq || 0) + 1
    this._editRequest = requestId
    const url = '/pages/fabushi/image-editor/index?src=' + encodeURIComponent(queue.files[target].path)
      + '&requestId=' + encodeURIComponent(requestId)
      + '&position=' + (queue.cursor + 1) + '&total=' + queue.targets.length
    // 打不开编辑页(页面栈已满等): 这一批都按原图上传, 不为后面的图反复重试
    wx.navigateTo({
      url,
      fail: () => {
        this._editNavigateFailed = true
        this.consumeImageEditResult({ requestId, edited: false }, '图片编辑页打不开，已使用原图')
      }
    })
  },

  // result 由编辑页失败兜底时直接传入; 否则从 globalData 取属于本次请求的回传
  consumeImageEditResult(result, toast) {
    const queue = this._editQueue
    if (!queue || !this._editRequest) return
    const taken = result || imageEditor.takeEditResult(getApp().globalData, this._editRequest)
    if (!taken) return
    this._editQueue = imageEditor.applyEditResult(queue, taken)
    if (toast) wx.showToast({ title: toast, icon: 'none' })
    this.advanceImageEdit()
  },

  loadBooks() {
    getFabushiBooks({ status: 'approved', current: 1, size: 200 }).then((res) => {
      const data = res || {}
      const books = data.records || []
      const preset = this._presetBookId
      const presetBook = preset ? books.find((book) => Number(book.id) === preset) : null
      const categories = []
      const seen = {}
      books.forEach((b) => {
        if (!b.categoryCode || seen[b.categoryCode]) return
        seen[b.categoryCode] = true
        categories.push({ code: b.categoryCode, name: b.categoryName || b.categoryCode })
      })
      const categoryIndex = presetBook ? categories.findIndex((c) => c.code === presetBook.categoryCode) : -1
      const categoryCode = categoryIndex >= 0 ? categories[categoryIndex].code : null
      const titles = this.titleList(books, categoryCode)
      const titleIndex = presetBook ? titles.indexOf(presetBook.title) : -1
      const title = titleIndex >= 0 ? titles[titleIndex] : null
      const editions = this.editionList(books, categoryCode, title)
      const editionIndex = presetBook ? editions.findIndex((b) => Number(b.id) === Number(presetBook.id)) : -1
      this.setData({
        books, categories, categoryIndex, titles, titleIndex, editions, editionIndex,
        bookIndex: editions.length && editionIndex >= 0 ? editionIndex : -1
      })
      this.syncBookPicker()
    })
  },

  titleList(books, categoryCode) {
    const out = []
    const seen = {}
    books.forEach((b) => {
      if (!b.title) return
      if (categoryCode && b.categoryCode !== categoryCode) return
      if (seen[b.title]) return
      seen[b.title] = true
      out.push(b.title)
    })
    return out
  },

  editionList(books, categoryCode, title) {
    return books.filter((b) => {
      if (categoryCode && b.categoryCode !== categoryCode) return false
      if (title && b.title !== title) return false
      return true
    }).map((b) => Object.assign({}, b, { editionName: this.editionLabel(b) }))
  },

  editionLabel(book) {
    return [book.publisher, book.editionType, book.pages ? book.pages + ' 页' : ''].filter(Boolean).join(' · ') || '默认版本'
  },

  currentBookId() {
    const { editions, editionIndex } = this.data
    return editionIndex >= 0 && editions[editionIndex] ? editions[editionIndex].id : null
  },

  onCategoryChange(e) {
    const categoryIndex = Number(e.detail.value)
    const categoryCode = this.data.categories[categoryIndex] ? this.data.categories[categoryIndex].code : null
    const titles = this.titleList(this.data.books, categoryCode)
    this.setData({ categoryIndex, titles, titleIndex: -1, editions: [], editionIndex: -1, bookIndex: -1, sections: [], sectionNames: [], sectionIndex: -1 })
    this.syncBookPicker()
  },

  onTitleChange(e) {
    const titleIndex = Number(e.detail.value)
    const categoryCode = this.data.categories[this.data.categoryIndex] ? this.data.categories[this.data.categoryIndex].code : null
    const title = this.data.titles[titleIndex] || null
    const editions = this.editionList(this.data.books, categoryCode, title)
    this.setData({ titleIndex, editions, editionIndex: editions.length ? 0 : -1, bookIndex: editions.length ? 0 : -1, sections: [], sectionNames: [], sectionIndex: -1 })
    this.syncBookPicker()
    if (editions.length) this.loadSectionsFor(editions[0].id)
  },

  onEditionChange(e) {
    const editionIndex = Number(e.detail.value)
    this.setData({ editionIndex, bookIndex: editionIndex, sections: [], sectionNames: [], sectionIndex: -1 })
    this.syncBookPicker()
    const book = this.data.editions[editionIndex]
    if (book) this.loadSectionsFor(book.id)
  },

  // ===== 【2026-11-26 按原型 v7(B4)】一行合并的三列选择器(类别 / 书目 / 版本) + 右侧“换版本” =====
  // 选书的逻辑还是上面三个 onXxxChange(它们仍被“换版本”和单元测试直接用); 这里只管三列选择器的列内容、
  // 滚动某一列时后面的列跟着换、确认后把三个位置交给上面三个处理。
  syncBookPicker() {
    const { books, categories, titles, categoryIndex, titleIndex, editions, editionIndex } = this.data
    if (!categories.length) {
      this.setData({ bookPicker: [[], [], []], bookPickerIndex: [0, 0, 0], bookSummary: '' })
      return
    }
    // 已经选了类别就以它为准; 没选过就停在第一列当前滚到的类别
    const catIndex = categoryIndex >= 0 ? categoryIndex : (this._pickerCategory || 0)
    const cat = categories[catIndex] || categories[0]
    const columnTitles = categoryIndex >= 0 ? titles : (this._pickerTitles || this.titleList(books, cat.code))
    const chosenTitle = titleIndex >= 0 ? titles[titleIndex] : null
    const shownTitle = chosenTitle || columnTitles[0] || null
    // 选了书目就列这本书的版本; 没选过就列第二列第一本书的版本
    const columnEditions = chosenTitle ? editions : this.editionList(books, cat.code, shownTitle)
    const chosenEdition = editionIndex >= 0 ? editions[editionIndex] : null
    this._pickerCategory = categories.indexOf(cat)
    this._pickerTitles = columnTitles
    this.setData({
      bookPicker: [categories.map((c) => c.name), columnTitles, columnEditions.map((x) => x.editionName)],
      bookPickerIndex: [Math.max(catIndex, 0), Math.max(titleIndex, 0), Math.max(editionIndex, 0)],
      // 与原型 B4 那一行同一个写法: “国学建议 ·《道德经》· 陈鼓应注”
      bookSummary: cat && chosenTitle && chosenEdition ? cat.name + ' ·《' + chosenTitle + '》· ' + chosenEdition.editionName : ''
    })
  },

  onBookColumnChange(e) {
    const column = Number(e.detail && e.detail.column)
    const value = Number(e.detail && e.detail.value) || 0
    const { books, categories } = this.data
    if (!categories.length) return
    if (column === 0) {
      this._pickerCategory = Math.min(Math.max(value, 0), categories.length - 1)
      const cat = categories[this._pickerCategory]
      this._pickerTitles = this.titleList(books, cat ? cat.code : null)
    }
    const cat = categories[this._pickerCategory]
    const columnTitles = this._pickerTitles || []
    // 第三列跟着第二列当前停的那本书走
    const title = column === 1 ? (columnTitles[value] || null) : (columnTitles[0] || null)
    const columnEditions = this.editionList(books, cat ? cat.code : null, title)
    this.setData({ bookPicker: [categories.map((c) => c.name), columnTitles, columnEditions.map((x) => x.editionName)] })
  },

  onBookPicked(e) {
    const value = (e && e.detail && e.detail.value) || []
    // 认不出的位置按第 0 个处理
    const categoryIndex = Number(value[0]) || 0
    const titleIndex = Number(value[1]) || 0
    const editionIndex = Number(value[2]) || 0
    this.onCategoryChange({ detail: { value: categoryIndex } })
    this.onTitleChange({ detail: { value: titleIndex } })
    if (editionIndex) this.onEditionChange({ detail: { value: editionIndex } })
  },

  loadSectionsFor(bookId) {
    getFabushiSections(bookId).then((res) => {
      // 连着换了两版时, 前一版的章节可能后到: 只认当前选中的那一版
      if (Number(this.currentBookId()) !== Number(bookId)) return
      const sections = (res && res.sections) || []
      const flat = []
      const walk = (nodes, prefix) => {
        ;(nodes || []).forEach((n) => {
          const label = (n.title || ('#' + n.id))
          const path = prefix ? prefix + ' / ' + label : label
          flat.push({ id: n.id, path })
          if (n.children && n.children.length) walk(n.children, path)
        })
      }
      walk(sections, '')
      this.setData({ sections: flat, sectionNames: flat.map((x) => x.path), sectionIndex: -1 })
    }).catch(() => {})
  },

  onChannel(e) {
    this.setData({ channel: e.currentTarget.dataset.value })
  },

  onManualSection(e) {
    this.setData({ manualSection: e.detail.value })
  },

  toggleManualSection() {
    this.setData({ useManualSection: !this.data.useManualSection, sectionIndex: -1 })
  },

  // ===== 【F2 录音】getRecorderManager 分段录制: mp3/44.1k/96k, 60s 自动停, <1s 丢弃 =====

  initRecorder() {
    if (this._recorderInited) return
    this._recorderInited = true
    const rm = wx.getRecorderManager()
    rm.onStart(() => {
      this.setData({ recording: true, recordSeconds: 0 })
      this._recTimer = setInterval(() => this.setData({ recordSeconds: this.data.recordSeconds + 1 }), 1000)
    })
    rm.onStop((res) => {
      if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
      this.setData({ recording: false })
      const durationMs = (res && res.duration) || 0
      if (durationMs < 1000) {
        wx.showToast({ title: '时长太短，已丢弃', icon: 'none' })
        return
      }
      if (this.attachmentCounts().audio >= 10) {
        wx.showToast({ title: '语音已达上限10段', icon: 'none' })
        return
      }
      this.uploadRecordSegment(res.tempFilePath, Math.round(durationMs / 1000))
    })
    rm.onError((err) => {
      if (this._recTimer) { clearInterval(this._recTimer); this._recTimer = null }
      this.setData({ recording: false })
      const msg = (err && err.errMsg) || ''
      if (msg.indexOf('auth') >= 0 || msg.indexOf('deny') >= 0) {
        wx.showModal({
          title: '需要麦克风权限',
          content: '录制语音需授权麦克风，请在设置中开启',
          confirmText: '去设置',
          success: (r) => { if (r.confirm) wx.openSetting() }
        })
      } else {
        wx.showToast({ title: '录音失败', icon: 'none' })
      }
    })
    this._recorder = rm
  },

  toggleRecord() {
    if (this.data.uploading) return
    this.initRecorder()
    if (this.data.recording) {
      this._recorder.stop()
      return
    }
    if (this.attachmentCounts().audio >= 10) {
      wx.showToast({ title: '语音已达上限10段', icon: 'none' })
      return
    }
    // mp3 三端(iOS/Android/微信H5播放)兼容最好; duration=60s 到时自动停走 onStop
    this._recorder.start({ format: 'mp3', duration: 60000, sampleRate: 44100, numberOfChannels: 1, encodeBitRate: 96000 })
  },

  uploadRecordSegment(path, seconds) {
    this.setData({ uploading: true })
    uploadFile(path, null, 'fabushi').then((res) => {
      const fileId = res && (res.id || (res.data && res.data.id))
      if (!fileId) throw new Error('upload no fileId')
      const added = { fileId, fileType: 'audio', name: `录音 ${seconds}s`, durationSeconds: seconds }
      this.setData({ attachments: this.data.attachments.concat(added), uploading: false })
      this.syncAttachHint(); this.syncImageAttachments()
    }).catch((err) => { this.setData({ uploading: false }); showError(err, '录音上传失败') })
  },

  playAttachment(e) {
    const index = Number(e.currentTarget.dataset.index)
    const item = this.data.attachments[index]
    if (!item || item.fileType !== 'audio') return
    if (this._playingIndex === index && this._segAudio) {
      this._segAudio.stop()
      this._playingIndex = -1
      this.setData({ playingIndex: -1 })
      return
    }
    Promise.resolve(this._segToken ? this._segToken : getDownloadToken().then((r) => {
      this._segToken = r && (r.token || (r.data && r.data.token)) || ''
      return this._segToken
    })).then((token) => {
      if (this._segAudio) this._segAudio.destroy()
      const a = wx.createInnerAudioContext()
      a.src = getDownloadUrl({ id: item.fileId }) + (token ? '?token=' + encodeURIComponent(token) : '')
      a.onEnded(() => { this._playingIndex = -1; this.setData({ playingIndex: -1 }) })
      a.play()
      this._segAudio = a
      this._playingIndex = index
      this.setData({ playingIndex: index })
    }).catch((err) => showError(err, '试听失败'))
  },

  // ===== 【F3a】三通道之三: 手机本地文件(H5 票据通道) =====

  choosePhoneFile() {
    if (this.data.uploading) return
    getFabushiUploadTicket().then((res) => {
      const ticket = res && (res.ticket || (res.data && res.data.ticket))
      if (!ticket) throw new Error('no ticket')
      wx.navigateTo({ url: '/pages/fabushi/uploader/index?ticket=' + encodeURIComponent(ticket) })
    }).catch((err) => showError(err, '上传票获取失败'))
  },

  consumeH5Files(manifest) {
    const added = []
    let pdfCount = 0
    let skipped = 0
    const counts = this.attachmentCounts()
    manifest.forEach((f) => {
      if (f.fileType === 'image' || f.fileType === 'video' || f.fileType === 'audio') {
        const cap = f.fileType === 'audio' ? 10 : 5
        if (counts[f.fileType] >= cap) { skipped++; return }
        counts[f.fileType]++
        added.push({ fileId: f.fileId, fileType: f.fileType, name: f.fileName || '手机文件' })
      } else if (f.fileType === 'pdf') {
        pdfCount++
      }
    })
    if (added.length) {
      this.setData({ attachments: this.data.attachments.concat(added) })
      this.syncAttachHint(); this.syncImageAttachments()
    }
    if (skipped) wx.showToast({ title: skipped + ' 个文件超出分型上限未添加', icon: 'none' })
    if (pdfCount) wx.showToast({ title: 'PDF 已传至法布施空间，请在 PC 书目管理挂载', icon: 'none', duration: 2500 })
  },

  onSectionChange(e) {
    this.setData({ sectionIndex: Number(e.detail.value) })
  },

  onScope(e) {
    const scopeType = e.currentTarget.dataset.value
    // 【计划切片 4】随施=免标题免书；songdu/zaidu 换类型时拉章节（zaidu）
    const patch = { scopeType, sections: [], sectionIndex: -1 }
    if (scopeType === 'suishi') patch.title = ''
    this.setData(patch)
    this.syncNavTitle(scopeType)
    const bookId = this.currentBookId()
    if ((scopeType === 'zaidu' || scopeType === 'songdu') && bookId) this.loadSectionsFor(bookId)
    this.syncAttachHint(); this.syncImageAttachments()
  },

  onTitle(e) { this.setData({ title: e.detail.value }) },
  onContent(e) { this.setData({ content: e.detail.value }) },

  chooseMedia(e) {
    const source = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.source) || 'all'
    this.pickMedia(['image', 'video'], source === 'camera' ? ['camera'] : source === 'album' ? ['album'] : ['album', 'camera'])
  },

  // 【B3 原型】独立“视频”入口：相机/相册都可选，限额 5 段
  chooseVideo() {
    this.pickMedia(['video'], ['album', 'camera'])
  },

  pickMedia(mediaType, sourceType) {
    if (this.data.uploading) return
    const counts = this.attachmentCounts()
    // Q14 已定：随施九宫格 9 张，其余 5 张；视频始终 5 段
    const imgMax = this.data.scopeType === 'suishi' ? 9 : 5
    const imgLeft = mediaType.indexOf('image') >= 0 ? Math.max(0, imgMax - counts.image) : 0
    const vidLeft = mediaType.indexOf('video') >= 0 ? Math.max(0, 5 - counts.video) : 0
    if (imgLeft <= 0 && vidLeft <= 0) {
      wx.showToast({ title: mediaType.indexOf('video') >= 0 ? '视频已达上限' : '图片已达上限', icon: 'none' })
      return
    }
    const count = Math.max(Math.min(9, Math.max(imgLeft, vidLeft)), 1)
    wx.chooseMedia({ count, mediaType, sourceType, success: (res) => {
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
      if (imgCut) wx.showToast({ title: '图片最多' + imgMax + '张，已保留前' + imgMax, icon: 'none' })
      else if (vidCut) wx.showToast({ title: '视频最多5段，已保留前5', icon: 'none' })
      // 【GL-12】图片先过编辑器(可放大查看、裁剪), 结果替换原图; 开关关着时等同于直接 uploadFiles
      this.startImageEdit(files)
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

  // 【PROTO-AUDIT-20261123 P7】九宫格预览数据：把图片附件映射成可直接 <image src> 的 URL
  syncImageAttachments() {
    const images = this.data.attachments.filter((a) => a.fileType === 'image')
    if (!images.length) { this.setData({ imageAttachments: [] }); return }
    getDownloadToken().then((res) => {
      // 【2026-11-25 修正】令牌接口回来的是 {token}; 原来直接把整个应答对象拼进地址(成了 object Object), 九宫格预览一直是裂图
      const token = vm.downloadTokenOf(res)
      const list = images.map((a) => ({
        fileId: a.fileId,
        preview: getDownloadUrl({ id: a.fileId }) + (token ? '?token=' + encodeURIComponent(token) : '')
      }))
      this.setData({ imageAttachments: list })
    }).catch(() => {})
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.imageAttachments.map((x) => x.preview)
    if (urls.length) wx.previewImage({ current: urls[index] || urls[0], urls })
  },

  syncAttachHint() {
    const c = this.attachmentCounts()
    // 【Q14】随施图片上限 9，其他类型 5
    const imgMax = this.data.scopeType === 'suishi' ? 9 : 5
    this.setData({ attachHint: `图片 ${c.image}/${imgMax} · 视频 ${c.video}/5 · 语音 ${c.audio}/10` })
  },

  uploadFiles(files) {
    if (!files.length) return
    this.setData({ uploading: true })
    Promise.all(files.map((f) => uploadFile(f.path, null, 'fabushi'))).then((results) => {
      const added = results.map((res, i) => ({ fileId: res && (res.id || (res.data && res.data.id)), fileType: files[i].type, name: files[i].name })).filter((x) => x.fileId)
      this.setData({ attachments: this.data.attachments.concat(added), uploading: false })
      this.syncAttachHint(); this.syncImageAttachments()
    }).catch((err) => { this.setData({ uploading: false }); showError(err, '附件上传失败') })
  },

  removeAttachment(e) {
    const index = Number(e.currentTarget.dataset.index)
    const attachments = this.data.attachments.slice(); attachments.splice(index, 1); this.setData({ attachments })
    this.syncAttachHint(); this.syncImageAttachments()
  },

  submit() {
    // 【D8/D9/D10 计划切片 4】三类型分型提交（与后端分型校验同口径）：
    // songdu/zaidu：书目必选（如同四项激励先选客户），songdu 免标题；zaidu 章节可选/必选=选择已有
    // suishi：免书目免标题，纯内容/附件；songdu 语音必填（每日诵读=朗读/背诵）
    const { scopeType, title, content, submitting, attachments, sectionIndex, sections, channel, useManualSection, manualSection } = this.data
    if (submitting) return
    const isSuishi = scopeType === 'suishi'
    const isZaidu = scopeType === 'zaidu'
    const isSongduB = scopeType === 'songdu' && channel === 'B'
    const bookId = this.currentBookId()
    if (!isSuishi && bookId == null) {
      wx.showToast({ title: '请先选择具体书目', icon: 'none' })
      return
    }
    if (isZaidu && useManualSection && !String(manualSection || '').trim()) {
      wx.showToast({ title: '请填写章节名称', icon: 'none' })
      return
    }
    if (isZaidu && !useManualSection && sectionIndex < 0) {
      wx.showToast({ title: '请选择章节', icon: 'none' })
      return
    }
    if (isSongduB && sectionIndex < 0) {
      wx.showToast({ title: 'B 通道请选择具体段落', icon: 'none' })
      return
    }
    if (isSuishi && !content.trim()) {
      wx.showToast({ title: '随施写点内容吧（一句话也行）', icon: 'none' })
      return
    }
    if (!isSuishi && !title.trim() && !content.trim()) {
      wx.showToast({ title: '内容不能为空', icon: 'none' })
      return
    }
    if (scopeType === 'songdu' && !attachments.some((a) => a.fileType === 'audio')) {
      wx.showToast({ title: '每日诵读请至少录制一段语音', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    const resolveSectionId = async () => {
      if (isZaidu && useManualSection) {
        const res = await createFabushiSectionSelf(bookId, String(manualSection).trim())
        return res && res.id ? res.id : null
      }
      if ((isZaidu || isSongduB) && sectionIndex >= 0 && sections[sectionIndex]) return sections[sectionIndex].id
      return null
    }
    resolveSectionId().catch(() => null).then((sectionId) => createFabushiPost({
      scopeType,
      title: isSuishi ? null : (title.trim() || null),
      content: content.trim() || null,
      bookId: isSuishi ? null : bookId,
      sectionId
    })).then(async (res) => {
      const id = res && (res.id || (res.data && res.data.id))
      if (id && this.data.attachments.length) {
        await Promise.all(this.data.attachments.map((a) => addFabushiAttachment(id, {
          fileId: a.fileId, fileType: a.fileType, durationSeconds: a.durationSeconds || undefined
        })))
      }
      // 成功页的一句话(原型 B5): 挂了哪本书、哪个章节、带了几段语音 / 几张图。章节在这里是拍平后的路径(如“上篇 / 第八章”)
      const counts = this.attachmentCounts()
      const pickedSection = sectionIndex >= 0 && sections[sectionIndex] ? String(sections[sectionIndex].path || '') : ''
      const view = vm.publishSuccessView(scopeType, {
        bookTitle: isSuishi ? '' : (this.data.titles[this.data.titleIndex] || ''),
        sectionTitle: isZaidu ? (useManualSection ? String(manualSection).trim() : pickedSection) : (isSongduB ? pickedSection : ''),
        audioCount: counts.audio,
        imageCount: counts.image
      })
      this.setData({ submitting: false, success: true, publishedPostId: id || null, muyuAnim: true, successTitle: view.title, successSummary: view.summary })
      wx.setNavigationBarTitle({ title: '提交成功' })
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

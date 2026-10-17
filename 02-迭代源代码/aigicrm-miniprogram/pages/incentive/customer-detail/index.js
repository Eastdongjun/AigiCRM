// 【需求C 2026-10-16】客户激励详情页: 列表页封面卡点入,承接该客户全部明细——
// 分类锚点chips(原红圈①)+分组照片墙纯缩略图(原③按钮删除,点图即看)+历史记录默认展开+吸底操作栏(原④)。
// 数据零后端改动: 列表页经 eventChannel 传整卡聚合数据(record 视图对象 + 已构建的子记录视图)。
const { deleteRecord, markIncentiveRead } = require('../../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../../api/file')
const { fetchRemoteAttachment } = require('../../../utils/attachment')
const { showError } = require('../../../utils/feedback')
const { requireLogin } = require('../../../utils/auth')

Page({
  data: { loaded: false, record: null, children: [], apiBase: '', dlToken: '', previewing: false, previewVideoPath: '', scrollAnchor: '' },

  onLoad() {
    if (!requireLogin()) return
    const app = getApp()
    this.setData({ apiBase: app.globalData.apiBaseUrl || '' })
    // eventChannel 缺失/无载荷(异常进入)时 800ms 后落空态,不悬在"加载中"
    this._payloadTimer = setTimeout(() => { if (!this.data.loaded) this.setData({ loaded: true, record: null }) }, 800)
    const channel = typeof this.getOpenerEventChannel === 'function' ? this.getOpenerEventChannel() : null
    if (channel && typeof channel.on === 'function') {
      channel.on('payload', (payload) => {
        clearTimeout(this._payloadTimer)
        this.initWithPayload(payload || {})
      })
    }
    this.ensureDlToken()
  },

  onUnload() { if (this._payloadTimer) clearTimeout(this._payloadTimer) },

  initWithPayload({ record, children }) {
    if (!record) return
    this.setData({ record, children: Array.isArray(children) ? children : [], loaded: true })
    if (record.name) wx.setNavigationBarTitle({ title: record.name, fail: () => {} })
    this.markCustomerRead()
  },

  // 进入即清除未读(与列表页点卡同口径): 本地即时置灰 + 逐条上报 + 通知列表页更新卡片
  markCustomerRead() {
    const record = this.data.record
    if (!record || !record.unread) return
    this.setData({ 'record.unread': false, 'record.readStatusText': '已读', 'record.readStatusClass': 'tag-read' })
    if (getApp().globalData.demoMode || !Array.isArray(record.recordIds) || !record.recordIds.length) return
    Promise.all(record.recordIds.map((id) => markIncentiveRead(id))).catch((error) => showError(error, '已读标记失败'))
    const channel = typeof this.getOpenerEventChannel === 'function' ? this.getOpenerEventChannel() : null
    if (channel && typeof channel.emit === 'function') channel.emit('read', { id: record.id })
  },

  // 照片墙缩略图凭证: 详情页自取一次(列表页的模块级缓存不跨页共享,一次请求成本可接受)
  ensureDlToken() {
    if (getApp().globalData.demoMode) return
    getDownloadToken().then((result) => {
      const token = (result && (result.token || (result.data && result.data.token))) || ''
      if (token) this.setData({ dlToken: token })
    }).catch(() => { /* token 缺失时图片退化面卡,不阻断 */ })
  },

  // 锚点定位走 scroll-view 的 scroll-into-view(先清空再设目标, 重复点同一锚点也能再次触发)
  scrollToGroup(event) {
    const index = Number(event.currentTarget.dataset.idx) || 0
    const anchor = `cd-group-${index}`
    if (this.data.scrollAnchor === anchor) {
      this.setData({ scrollAnchor: '' }, () => this.setData({ scrollAnchor: anchor }))
    } else {
      this.setData({ scrollAnchor: anchor })
    }
  },

  findWallFile(event) {
    const group = (this.data.record.fileGroupsView || [])[Number(event.currentTarget.dataset.groupIndex)]
    return group ? (group.files || [])[Number(event.currentTarget.dataset.fileIndex)] : null
  },

  onTileTap(event) {
    const file = this.findWallFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    this.fetchAttachment(file, 'preview')
  },

  // 【需求B 口径】下载能力不丢: 长按缩略图=下载(与微信图片长按保存同心智)
  onTileLongPress(event) {
    const file = this.findWallFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    this.fetchAttachment(file, 'download')
  },

  fetchAttachment(file, mode) {
    return fetchRemoteAttachment({
      url: getDownloadUrl(file),
      mode,
      file,
      isBusy: this.data.previewing,
      setBusy: (busy) => this.setData({ previewing: busy }),
      isImage: (item) => item.type === 'image',
      onVideo: (tempFilePath) => this.setData({ previewVideoPath: tempFilePath })
    })
  },

  onChildDetail(event) {
    const index = Number(event.currentTarget.dataset.index)
    const child = this.data.children[index]
    if (!child || !child.id) return
    this.markChildRead(index)
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${child.id}` })
  },

  markChildRead(index) {
    const child = this.data.children[index]
    if (!child || !child.unread) return
    this.setData({ [`children[${index}].unread`]: false, [`children[${index}].readStatusText`]: '已读', [`children[${index}].readStatusClass`]: 'tag-read inline-read-status' })
    if (!getApp().globalData.demoMode && child.id) markIncentiveRead(child.id).catch(() => {})
  },

  // 【需求C】单条记录操作收进行尾「···」: 详情/追加/删除 按权限显隐(原生面板,层级稳定)
  onChildMore(event) {
    const index = Number(event.currentTarget.dataset.index)
    const child = this.data.children[index]
    if (!child) return
    const actions = ['详情']
    if (child.showAppendAction) actions.push('追加')
    if (child.showDeleteAction) actions.push('删除')
    wx.showActionSheet({
      itemList: actions,
      success: (result) => {
        const picked = actions[result.tapIndex]
        if (picked === '详情') return this.onChildDetail({ currentTarget: { dataset: { index } } })
        if (picked === '追加') return this.goAppend(child.id)
        if (picked === '删除') return this.removeChild(index)
      },
      fail: () => {}
    })
  },

  goAppend(recordId) {
    wx.navigateTo({ url: `/pages/incentive/upload/index?id=${recordId}` })
  },

  removeChild(index) {
    const child = this.data.children[index]
    if (!child || !child.canDelete) return wx.showToast({ title: '没有四项激励删除权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可删除', icon: 'none' })
    wx.showModal({
      title: '删除四项激励记录',
      content: `确认删除“${child.titleText || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(child.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          getApp().globalData.forceRefresh.incentive = true
          wx.navigateBack()
        }).catch((error) => showError(error, '删除四项激励记录失败'))
      }
    })
  },

  onAppend() {
    const record = this.data.record
    if (!record || !record.canEdit) return wx.showToast({ title: '没有四项激励追加权限', icon: 'none' })
    this.goAppend(record.id)
  },

  onOpenCustomer() {
    const record = this.data.record
    if (!record || !record.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${record.customerId}&from=incentive` })
  }
})

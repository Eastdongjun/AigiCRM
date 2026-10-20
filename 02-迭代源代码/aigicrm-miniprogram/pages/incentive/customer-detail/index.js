// 【2026-10-20 一客一册】客户四项激励档案：封面 → 打开 → 左侧时间轴 + 右侧按时间一页一条记录。
// 数据仍由列表页 eventChannel 传入（record 客户聚合 + children 子记录），零后端改动。
// 附件走现有文件能力：图片 wx.previewImage、视频 wx.previewMedia、文档/文本 fetchRemoteAttachment（下载后 openDocument/文本查看页）。
const { deleteRecord, markIncentiveRead } = require('../../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../../api/file')
const { fetchRemoteAttachment } = require('../../../utils/attachment')
const { showError } = require('../../../utils/feedback')
const { requireLogin } = require('../../../utils/auth')

const SKELETON_MIN_MS = 260
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(value) {
  return String(value).padStart(2, '0')
}

function parseDateParts(value) {
  const raw = String(value || '').trim().replace('T', ' ')
  const match = raw.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/)
  if (match) {
    const month = Number(match[2])
    const day = Number(match[3])
    const year = match[1]
    return { year, month, day, key: `${year}-${pad(month)}` }
  }
  const date = new Date(raw.replace(/-/g, '/'))
  if (!Number.isNaN(date.getTime())) {
    const year = String(date.getFullYear())
    const month = date.getMonth() + 1
    return { year, month, day: date.getDate(), key: `${year}-${pad(month)}` }
  }
  return { year: '未标注', month: 0, day: 0, key: 'unknown' }
}

function formatDate(value) {
  const parts = parseDateParts(value)
  if (parts.year === '未标注') return { ...parts, dateText: '未标注日期', weekday: '' }
  const date = new Date(Number(parts.year), parts.month - 1, parts.day)
  return {
    ...parts,
    dateText: `${pad(parts.month)}月${pad(parts.day)}日`,
    weekday: WEEKDAYS[date.getDay()] || ''
  }
}

function isImageFile(file) {
  return file.type === 'image' || file.fileKind === 'image'
}

function isVideoFile(file) {
  return file.type === 'video' || file.fileKind === 'video' || /\.(mp4|mov|m4v|webm)$/i.test(file.name || file.label || '')
}

function collectFiles(record) {
  const groups = record.fileGroupsView || []
  const files = []
  groups.forEach((group, groupIndex) => {
    ;(group.files || []).forEach((file, fileIndex) => {
      files.push({ ...file, groupIndex, fileIndex, label: file.name || file.label || '附件' })
    })
  })
  return files
}

/** 上传链路可能把 UUID 当文件名落库, 附件区展示为「附件 1 · PDF」而不是一串哈希 */
function displayFileName(file, index) {
  const name = String(file.name || '').trim()
  const isHash = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\.[a-z0-9]+)?$/i.test(name)
  if (!isHash) return name || `附件 ${index + 1}`
  const ext = name.includes('.') ? name.split('.').pop().toUpperCase() : ''
  return `附件 ${index + 1}${ext ? ` · ${ext}` : ''}`
}

/** 大标题取分类下的具体产品(如「密封类-静密封」→「静密封」); 与分类同名时不重复展示 */
function headlineOf(record, category) {
  const related = String(record.relatedProducts || '').trim()
  if (!related) return ''
  const stripped = related.startsWith(category)
    ? related.slice(category.length).replace(/^[-—·\s]+/, '')
    : related
  return stripped && stripped !== category ? stripped : ''
}

/** 接口时间 2026-09-13T23:22:40 → 2026-09-13 23:22；列表接口返回 ISO 时间，档案里统一格式化 */
function formatDateTime(value) {
  const raw = String(value || '').trim()
  const match = raw.match(/^(\d{4})-(\d{1,2})-(\d{1,2})[T\s](\d{1,2}):(\d{2})/)
  if (match) return `${match[1]}-${pad(match[2])}-${pad(match[3])} ${pad(match[4])}:${match[5]}`
  return raw.replace('T', ' ').slice(0, 16) || '-'
}

/** 客户名形如「xx公司（陈晓彬）」时，封面标题去掉与负责人重复的括号名；非负责人括号（如「（集团）」）保留 */
function stripOwnerSuffix(name, owner) {
  const raw = String(name || '').trim()
  const person = String(owner || '').trim()
  if (!raw || !person) return raw
  const suffixes = [`（${person}）`, `(${person})`]
  for (let i = 0; i < suffixes.length; i += 1) {
    if (raw.endsWith(suffixes[i])) {
      return raw.slice(0, -suffixes[i].length).trim() || raw
    }
  }
  return raw
}

function decorateRecord(record, index) {
  const files = collectFiles(record)
  const parts = formatDate(record.createdAt || record.createTime || '')
  const score = Number(record.score || record.points || 0)
  const category = record.categoryName || record.category || record.type || '未分类'
  const photos = files.filter(isImageFile)
  const videos = files.filter(isVideoFile)
  // 照片/视频在版面内单独展示, 附件区只留需要点开阅读的文档, 避免同一张图重复出现两次
  const attachments = files
    .filter((file) => !isImageFile(file) && !isVideoFile(file))
    .map((file, index) => ({ ...file, displayName: displayFileName(file, index) }))
  return {
    ...record,
    childIndex: index,
    dateText: parts.dateText,
    weekday: parts.weekday,
    year: parts.year,
    month: parts.month,
    day: parts.day,
    monthKey: parts.key,
    titleText: category,
    headline: headlineOf(record, category),
    scoreText: `${score > 0 ? '+' : ''}${score} 分`,
    metaText: `周期 ${record.period || '未设周期'} · 上传人 ${record.userName || record.updatedByName || '未命名'} · ${formatDateTime(record.createdAt || record.createTime)}`,
    bodyText: record.preInstallQuestion || record.remark || record.summary || record.description || '暂无详情描述',
    photos,
    videos,
    attachments,
    fileCountText: files.length ? `${files.length} 个附件` : ''
  }
}

function buildGroups(children) {
  const map = {}
  children.forEach((record) => {
    const key = record.monthKey || 'unknown'
    if (!map[key]) {
      map[key] = {
        key,
        year: record.year,
        month: record.month,
        label: record.month ? `${record.year}年${record.month}月` : '未标注日期',
        monthLabel: record.month ? `${record.month}月` : '未标注',
        records: []
      }
    }
    map[key].records.push(record)
  })
  const groups = Object.keys(map)
    .sort((a, b) => (a === 'unknown' ? 1 : b === 'unknown' ? -1 : b.localeCompare(a)))
    .map((key) => {
      const group = map[key]
      group.records.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      group.count = group.records.length
      group.unreadCount = group.records.filter((record) => record.unread).length
      return group
    })
  let previousYear = null
  groups.forEach((group) => {
    group.showYear = group.year !== previousYear
    previousYear = group.year
  })
  return groups
}

function groupMeta(group) {
  return {
    key: group.key,
    year: group.year,
    month: group.month,
    label: group.label,
    monthLabel: group.monthLabel,
    count: group.count,
    unreadCount: group.unreadCount,
    showYear: group.showYear
  }
}

Page({
  data: {
    loaded: false,
    record: null,
    inside: false,
    groups: [],
    selectedKey: '',
    currentGroup: null,
    currentIndex: 0,
    currentRecord: null,
    apiBase: '',
    dlToken: '',
    totalUnread: 0,
    unreadOpen: false,
    unreadRows: [],
    coverYear: '',
    coverTitle: '',
    coverLatest: '-',
    coverSummary: '',
    coverUnread: 0,
    coverOwner: '未设置',
    coverCode: '-',
    coverRecent: [],
    paneAnchor: '',
    previewing: false
  },

  onLoad() {
    if (!requireLogin()) return
    const app = getApp()
    this._openedAt = Date.now()
    this._groups = []
    this.setData({ apiBase: app.globalData.apiBaseUrl || '' })
    this._payloadTimer = setTimeout(() => {
      if (!this.data.loaded) this.setData({ loaded: true, record: null })
    }, 800)
    const channel = typeof this.getOpenerEventChannel === 'function' ? this.getOpenerEventChannel() : null
    if (channel && typeof channel.on === 'function') {
      channel.on('payload', (payload) => {
        clearTimeout(this._payloadTimer)
        this.initWithPayload(payload || {})
      })
    }
    this.loadDlToken()
  },

  onUnload() {
    this._unloaded = true
    if (this._payloadTimer) clearTimeout(this._payloadTimer)
    if (this._payloadApplyTimer) clearTimeout(this._payloadApplyTimer)
  },

  initWithPayload(payload) {
    const record = payload && payload.record
    if (!record) return
    const children = (Array.isArray(payload.children) ? payload.children : []).map(decorateRecord)
    const apply = () => {
      if (this._unloaded) return
      const groups = buildGroups(children)
      this._groups = groups
      const sorted = children.slice().sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      const latest = sorted[0] || null
      const totalUnread = groups.reduce((sum, group) => sum + group.unreadCount, 0)
      const first = groups[0] || null
      const currentIndex = 0
      const currentRecord = first && first.records.length ? first.records[0] : null
      this.setData({
        loaded: true,
        record,
        groups: groups.map(groupMeta),
        selectedKey: first ? first.key : '',
        currentGroup: first ? groupMeta(first) : null,
        currentIndex,
        currentRecord,
        totalUnread,
        coverUnread: totalUnread,
        coverYear: first ? first.year : String(new Date().getFullYear()),
        coverTitle: stripOwnerSuffix(record.name, record.ownerName || (latest && latest.userName)),
        coverLatest: latest ? latest.dateText : '-',
        coverOwner: record.ownerName || (latest && latest.userName) || '未设置',
        coverCode: record.customerCode || (record.customerId ? `C-${record.customerId}` : `C-${record.id || '-'}`),
        coverRecent: sorted.slice(0, 2).map((item, index) => ({
          recentKey: `${item.monthKey || 'unknown'}-${item.childIndex}-${index}`,
          dateText: item.dateText,
          titleText: item.titleText
        })),
        coverSummary: latest
          ? `共 ${record.countText || `${children.length} 条`}记录，最近一次为 ${latest.dateText} ${latest.titleText}。`
          : '尚未建立激励记录，上传第一条佐证后会自动成册。'
      })
      wx.setNavigationBarTitle({ title: '四项激励档案', fail: () => {} })
    }
    const elapsed = Date.now() - (this._openedAt || 0)
    this._payloadApplyTimer = setTimeout(apply, Math.max(0, SKELETON_MIN_MS - elapsed))
  },

  openArchive() {
    this.setData({ inside: true })
  },

  closeArchive() {
    this.setData({ inside: false })
  },

  selectMonth(event) {
    this.setCurrentGroup(event.currentTarget.dataset.key, 0)
  },

  latestMonth() {
    const first = this._groups && this._groups[0]
    if (first) this.setCurrentGroup(first.key, 0)
  },

  setCurrentGroup(key, index) {
    const group = (this._groups || []).find((item) => item.key === key)
    if (!group) return
    const safeIndex = Math.max(0, Math.min(index, Math.max(0, group.records.length - 1)))
    this.setData({
      selectedKey: key,
      currentGroup: groupMeta(group),
      currentIndex: safeIndex,
      currentRecord: group.records[safeIndex] || null,
      paneAnchor: ''
    })
    wx.nextTick(() => this.setData({ paneAnchor: 'cd-pane-top' }))
  },

  prevRecord() {
    if (this.data.currentIndex <= 0) return
    this.setCurrentGroup(this.data.selectedKey, this.data.currentIndex - 1)
  },

  nextRecord() {
    const group = this.data.currentGroup
    if (!group || this.data.currentIndex >= group.count - 1) return
    this.setCurrentGroup(this.data.selectedKey, this.data.currentIndex + 1)
  },

  loadDlToken() {
    if (getApp().globalData.demoMode) return Promise.resolve('')
    if (this._dlTokenPromise) return this._dlTokenPromise
    this._dlTokenPromise = getDownloadToken().then((result) => {
      const token = (result && (result.token || (result.data && result.data.token))) || ''
      if (token) this.setData({ dlToken: token })
      return token
    }).catch(() => '')
    return this._dlTokenPromise
  },

  fullUrl(file) {
    const base = getDownloadUrl(file)
    if (!base) return ''
    const token = this.data.dlToken
    if (!token) return base
    return base + (base.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token)
  },

  previewPhoto(event) {
    const record = this.data.currentRecord
    if (!record || !record.photos.length) return
    const index = Number(event.currentTarget.dataset.index) || 0
    const open = () => {
      const urls = record.photos.map((file) => this.fullUrl(file)).filter(Boolean)
      if (!urls.length) return wx.showToast({ title: '图片地址缺失', icon: 'none' })
      wx.previewImage({ urls, current: urls[index] || urls[0], fail: (error) => showError(error, '图片预览失败') })
    }
    if (!this.data.dlToken) this.loadDlToken().then(open).catch(open)
    else open()
  },

  previewVideo() {
    const record = this.data.currentRecord
    if (!record || !record.videos.length) return
    const url = this.fullUrl(record.videos[0])
    if (!url) return wx.showToast({ title: '视频地址缺失', icon: 'none' })
    wx.previewMedia({
      sources: [{ url, type: 'video' }],
      fail: () => this.previewAttachmentByFile(record.videos[0])
    })
  },

  previewAttachment(event) {
    const record = this.data.currentRecord
    if (!record) return
    const groupIndex = Number(event.currentTarget.dataset.group)
    const fileIndex = Number(event.currentTarget.dataset.file)
    const file = record.attachments.find((item) => item.groupIndex === groupIndex && item.fileIndex === fileIndex)
    if (file) this.previewAttachmentByFile(file)
  },

  previewAttachmentByFile(file) {
    const url = getDownloadUrl(file)
    if (!url) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
    return fetchRemoteAttachment({
      url,
      mode: 'preview',
      file,
      isBusy: this.data.previewing,
      setBusy: (busy) => this.setData({ previewing: busy }),
      isImage: (item) => item.type === 'image' || item.fileKind === 'image',
      onVideo: (tempFilePath) => wx.previewMedia({ sources: [{ url: tempFilePath, type: 'video' }] })
    })
  },

  onRecordMore() {
    const record = this.data.currentRecord
    if (!record) return
    const actions = ['详情']
    if (record.canEdit) actions.push('追加')
    if (record.canDelete) actions.push('删除')
    wx.showActionSheet({
      itemList: actions,
      success: (result) => {
        const picked = actions[result.tapIndex]
        if (picked === '详情') return this.openRecordDetail(record)
        if (picked === '追加') return this.goAppend(record.id)
        if (picked === '删除') return this.removeRecord(record)
      },
      fail: () => {}
    })
  },

  openRecordDetail(record) {
    if (!record || !record.id) return
    this.markRecordReadByKey(this.data.selectedKey, this.data.currentIndex)
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${record.id}` })
  },

  goAppend(recordId) {
    wx.navigateTo({ url: `/pages/incentive/upload/index?id=${recordId}` })
  },

  removeRecord(record) {
    if (!record || !record.canDelete) return wx.showToast({ title: '没有四项激励删除权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可删除', icon: 'none' })
    wx.showModal({
      title: '删除四项激励记录',
      content: `确认删除“${record.titleText || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(record.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          getApp().globalData.forceRefresh.incentive = true
          wx.navigateBack()
        }).catch((error) => showError(error, '删除四项激励记录失败'))
      }
    })
  },

  onAppend() {
    const record = this.data.currentRecord
    if (!record || !record.canEdit) return wx.showToast({ title: '没有四项激励追加权限', icon: 'none' })
    this.goAppend(record.id)
  },

  onOpenCustomer() {
    const record = this.data.record
    if (!record || !record.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${record.customerId}&from=incentive` })
  },

  openUnread() {
    const rows = []
    ;(this._groups || []).forEach((group) => {
      group.records.forEach((record, index) => {
        if (!record.unread) return
        rows.push({
          rowKey: `${group.key}-${index}`,
          key: group.key,
          index,
          dateText: record.dateText,
          titleText: record.titleText,
          groupLabel: group.label,
          scoreText: record.scoreText
        })
      })
    })
    this.setData({ unreadOpen: true, unreadRows: rows })
  },

  closeUnread() {
    this.setData({ unreadOpen: false })
  },

  jumpUnread(event) {
    const key = event.currentTarget.dataset.key
    const index = Number(event.currentTarget.dataset.index) || 0
    this.setCurrentGroup(key, index)
    this.markRecordReadByKey(key, index)
    this.setData({ unreadOpen: false })
  },

  markRecordReadByKey(key, index) {
    const group = (this._groups || []).find((item) => item.key === key)
    if (!group) return
    const record = group.records[index]
    if (!record || !record.unread) return
    record.unread = false
    group.unreadCount = Math.max(0, group.unreadCount - 1)
    const totalUnread = Math.max(0, this.data.totalUnread - 1)
    const groups = this.data.groups.map((item) => item.key === key ? { ...item, unreadCount: group.unreadCount } : item)
    const currentGroup = this.data.currentGroup && this.data.currentGroup.key === key
      ? { ...this.data.currentGroup, unreadCount: group.unreadCount }
      : this.data.currentGroup
    const currentRecord = this.data.currentRecord && this.data.currentRecord.id === record.id
      ? { ...this.data.currentRecord, unread: false }
      : this.data.currentRecord
    this.setData({ groups, currentGroup, currentRecord, totalUnread, coverUnread: totalUnread })
    if (!getApp().globalData.demoMode && record.id) markIncentiveRead(record.id).catch(() => {})
  },

  markAllRead() {
    ;(this._groups || []).forEach((group) => {
      group.records.forEach((record) => {
        if (!record.unread) return
        record.unread = false
        if (!getApp().globalData.demoMode && record.id) markIncentiveRead(record.id).catch(() => {})
      })
      group.unreadCount = 0
    })
    const currentRecord = this.data.currentRecord ? { ...this.data.currentRecord, unread: false } : null
    this.setData({
      groups: this.data.groups.map((item) => ({ ...item, unreadCount: 0 })),
      currentGroup: this.data.currentGroup ? { ...this.data.currentGroup, unreadCount: 0 } : null,
      currentRecord,
      totalUnread: 0,
      coverUnread: 0,
      unreadRows: []
    })
    wx.showToast({ title: '已全部标为已读', icon: 'success' })
  },

  noop() {}
})

// 【需求C 2026-10-16】客户激励详情页: 列表页封面卡点入,承接该客户全部明细。
// 【2026-10-19 对齐 PC 详情】主视图改为「左日期三联拨盘 + 右当前日期内容」：
// 年/月/日三列由 picker-view 承载（iOS 滚轮质感），右侧只渲染所选月份/日期记录并按 6 条分页；
// 照片墙保留为次要视图；加载期展示骨架屏。数据仍由列表页 eventChannel 传入，零后端改动。
const { deleteRecord, markIncentiveRead } = require('../../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../../api/file')
const { fetchRemoteAttachment } = require('../../../utils/attachment')
const { showError } = require('../../../utils/feedback')
const { requireLogin } = require('../../../utils/auth')

const PAGE_SIZE = 6
const SKELETON_MIN_MS = 280

function parseDateParts(value) {
  const raw = String(value || '').trim().replace('T', ' ')
  const match = raw.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/)
  if (match) {
    const month = Number(match[2])
    return { year: match[1], month, day: Number(match[3]), key: `${match[1]}-${String(month).padStart(2, '0')}` }
  }
  const date = new Date(raw.replace(/-/g, '/'))
  if (!Number.isNaN(date.getTime())) {
    const month = date.getMonth() + 1
    return { year: String(date.getFullYear()), month, day: date.getDate(), key: `${date.getFullYear()}-${String(month).padStart(2, '0')}` }
  }
  return { year: '未标注', month: 0, day: 0, key: 'unknown' }
}

function decorateChildren(children) {
  return (children || []).map((child, index) => {
    const files = (child.fileGroupsView || []).reduce((all, group) => all.concat(group.files || []), [])
    const cover = files.find((file) => file.type === 'image' && file.id)
    const parts = parseDateParts(child.createdAt || child.createTime || '')
    return {
      ...child,
      childIndex: index,
      dateYear: parts.year,
      dateMonth: parts.month,
      dateDay: parts.day,
      coverFile: cover ? { id: cover.id } : null,
      fileCountText: files.length ? `${files.length} 个附件` : ''
    }
  })
}

function buildYears(children, fallbackYear) {
  const nowYear = new Date().getFullYear()
  const values = {}
  for (const child of children) {
    if (child.dateYear && child.dateYear !== '未标注') values[Number(child.dateYear)] = true
  }
  values[nowYear] = true
  values[Number(fallbackYear) || nowYear] = true
  const years = Object.keys(values).map(Number).sort((a, b) => a - b)
  const out = []
  for (const year of years) out.push({ value: String(year), label: String(year), hasData: children.some((child) => child.dateYear === String(year)) })
  return out
}

function buildMonths(children, year) {
  const out = []
  for (let month = 1; month <= 12; month += 1) {
    const rows = children.filter((child) => child.dateYear === year && child.dateMonth === month)
    out.push({ value: month, label: `${month}月`, hasData: rows.length > 0, unread: rows.filter((row) => row.unread).length })
  }
  return out
}

function buildDays(children, year, month) {
  const rows = children.filter((child) => child.dateYear === year && child.dateMonth === month)
  const total = new Date(Number(year), Number(month), 0).getDate()
  const withData = {}
  for (const row of rows) withData[row.dateDay] = true
  const out = [{ value: 0, label: '整月', hasData: rows.length > 0 }]
  for (let day = 1; day <= total; day += 1) out.push({ value: day, label: `${day}日`, hasData: Boolean(withData[day]) })
  return out
}

function filterChildren(children, year, month, day) {
  return children
    .filter((child) => child.dateYear === year && child.dateMonth === month && (!day || child.dateDay === day))
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
}

function pageRows(rows, page) {
  return { shown: rows.slice(0, page * PAGE_SIZE), hasMore: rows.length > page * PAGE_SIZE }
}

Page({
  data: {
    loaded: false,
    record: null,
    children: [],
    dialYears: [],
    dialMonths: [],
    dialDays: [],
    dialValue: [0, 0, 0],
    selectedYear: '',
    selectedMonth: 1,
    selectedDay: 0,
    paneTitle: '',
    paneTotal: 0,
    paneRows: [],
    page: 1,
    hasMore: false,
    nowDateText: '',
    nowTimeText: '',
    viewMode: 'date',
    apiBase: '',
    dlToken: '',
    previewing: false,
    previewVideoPath: '',
    scrollAnchor: ''
  },

  onLoad() {
    if (!requireLogin()) return
    const app = getApp()
    this._openedAt = Date.now()
    this.setData({ apiBase: app.globalData.apiBaseUrl || '' })
    this.startClock()
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

  onUnload() {
    this._unloaded = true
    if (this._payloadTimer) clearTimeout(this._payloadTimer)
    if (this._payloadApplyTimer) clearTimeout(this._payloadApplyTimer)
    if (this._clockTimer) clearInterval(this._clockTimer)
    if (this._dialSyncTimer) clearTimeout(this._dialSyncTimer)
  },

  initWithPayload(payload) {
    const record = payload && payload.record
    if (!record) return
    const children = decorateChildren(Array.isArray(payload.children) ? payload.children : [])
    const apply = () => {
      if (this._unloaded) return
      const now = new Date()
      const latest = children.slice().sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))[0]
      const base = latest ? parseDateParts(latest.createdAt || '') : null
      const hasBase = base && base.year !== '未标注' && base.month
      const year = hasBase ? base.year : String(now.getFullYear())
      const month = hasBase ? base.month : now.getMonth() + 1
      this.setData({ record, children, loaded: true })
      this.rebuildDial(year, month, 0, 1, false)
      if (record.name) wx.setNavigationBarTitle({ title: record.name, fail: () => {} })
      this.markCustomerRead()
    }
    // 骨架至少展示一小段时间, 避免 eventChannel 同步回包造成闪一下
    const elapsed = Date.now() - (this._openedAt || 0)
    this._payloadApplyTimer = setTimeout(apply, Math.max(0, SKELETON_MIN_MS - elapsed))
  },

  startClock() {
    const tick = () => {
      if (this._unloaded) return
      const now = new Date()
      const pad = (value) => String(value).padStart(2, '0')
      const week = '日一二三四五六'[now.getDay()]
      this.setData({
        nowDateText: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} 周${week}`,
        nowTimeText: `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
      })
    }
    tick()
    this._clockTimer = setInterval(tick, 1000)
  },

  rebuildDial(year, month, day, page, append) {
    const children = this.data.children || []
    const years = buildYears(children, Number(year) || new Date().getFullYear())
    const months = buildMonths(children, year)
    const days = buildDays(children, year, month)
    const yearIndex = Math.max(0, years.findIndex((item) => item.value === year))
    const monthIndex = Math.max(0, Math.min(11, Number(month) - 1))
    let dayIndex = days.findIndex((item) => item.value === day)
    if (dayIndex < 0) dayIndex = 0
    const selectedDay = days[dayIndex] ? days[dayIndex].value : 0
    this.setData({
      dialYears: years,
      dialMonths: months,
      dialDays: days,
      dialValue: [yearIndex, monthIndex, dayIndex],
      selectedYear: year,
      selectedMonth: month,
      selectedDay
    })
    // picker-view 在列数据同一次 setData 时可能忽略 value; 下一拍再同步一次选中位置
    if (this._dialSyncTimer) clearTimeout(this._dialSyncTimer)
    this._dialSyncTimer = setTimeout(() => {
      if (!this._unloaded) this.setData({ dialValue: [yearIndex, monthIndex, dayIndex] })
    }, 60)
    this.applyRecords(year, month, selectedDay, page, append)
  },

  applyRecords(year, month, day, page, append) {
    const rows = filterChildren(this.data.children || [], year, month, day)
    const paged = pageRows(rows, page)
    const nextRows = append ? this.data.paneRows.concat(paged.shown.slice((page - 1) * PAGE_SIZE)) : paged.shown
    this.setData({
      paneTitle: `${year}年${month}月${day ? ` · ${day}日` : ''}`,
      paneTotal: rows.length,
      paneRows: nextRows,
      page,
      hasMore: paged.hasMore
    })
  },

  onDialChange(event) {
    const indexes = event.detail.value || []
    const yearItem = this.data.dialYears[indexes[0]] || this.data.dialYears[0]
    const monthItem = this.data.dialMonths[indexes[1]] || this.data.dialMonths[this.data.selectedMonth - 1]
    const dayItem = this.data.dialDays[indexes[2]] || this.data.dialDays[0]
    if (!yearItem || !monthItem || !dayItem) return
    this.rebuildDial(yearItem.value, monthItem.value, dayItem.value, 1, false)
  },

  goToday() {
    const now = new Date()
    this.rebuildDial(String(now.getFullYear()), now.getMonth() + 1, now.getDate(), 1, false)
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.applyRecords(this.data.selectedYear, this.data.selectedMonth, this.data.selectedDay, this.data.page + 1, true)
  },

  switchViewMode(event) {
    const mode = event.currentTarget.dataset.mode
    if (mode === 'date' || mode === 'wall') this.setData({ viewMode: mode })
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

  // 照片墙锚点定位走 scroll-view 的 scroll-into-view(先清空再设目标, 重复点同一锚点也能再次触发)
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
    const paneRows = this.data.paneRows.map((item) => item.childIndex === index
      ? { ...item, unread: false, readStatusText: '已读', readStatusClass: 'tag-read inline-read-status' }
      : item)
    this.setData({
      [`children[${index}].unread`]: false,
      [`children[${index}].readStatusText`]: '已读',
      [`children[${index}].readStatusClass`]: 'tag-read inline-read-status',
      paneRows
    })
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

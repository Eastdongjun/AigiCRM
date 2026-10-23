// 四项服务客户详情(2026-09-20 小程序同步轮):对齐 PC search.vue 定稿的
// 「三联拨盘 + 位置语义 + 记录卡」设计 —— 年/月/日用原生 picker-view(iOS 滚轮),
// 拨到哪显示哪、无数据置暗仍可选、整月兜底、每月完整天数、年份自增、回到今天、实时时钟。
const { deleteRecord, getCustomerRecords, getGrantCheck, getScoreByCustomer, markIncentiveRead } = require('../../../api/incentive')
const { incentiveRecords } = require('../../../data/demo')
const { getDownloadUrl, getDownloadToken } = require('../../../api/file')
const { hasPermission, isAdmin, requireLogin } = require('../../../utils/auth')
const { fetchRemoteAttachment } = require('../../../utils/attachment')
const { showError } = require('../../../utils/feedback')
const { buildIncentiveFiles } = require('../../../utils/incentive-files')

// 阶段色:与 PC scoreColor 同一张表(外出参观橙/交流拜访蓝/两谈两做紫/产品试点绿)
const STAGE_COLORS = { visit: '#d97706', exchange: '#2563eb', plan: '#7c3aed', trial: '#059669' }
// 附件槽位标签色(安装前蓝/安装中绿/安装后橙/跟踪紫/佐证灰)
const TAG_COLORS = { 安装前: '#2563eb', 安装中: '#059669', 安装后: '#f59e0b', 跟踪: '#7c3aed', 佐证: '#64748b' }
const WEEK_LABELS = '日一二三四五六'

function currentUserId() {
  const user = getApp().globalData.currentUser || {}
  return user.id || user.userId || ''
}

function incentiveCodeOf(record) {
  const value = [
    record && record.categoryCode,
    record && record.categoryType,
    record && record.type,
    record && record.category,
    record && record.categoryName
  ].filter(Boolean).join(' ').toLowerCase()
  if (/plan|方案|两谈两做/.test(value)) return 'plan'
  if (/visit|参观|外出/.test(value)) return 'visit'
  if (/exchange|交流|拜访/.test(value)) return 'exchange'
  if (/trial|试点|安装|涂层|防滑|机封|密封|润滑/.test(value)) return 'trial'
  return ''
}

function detailLabelOf(record) {
  const code = incentiveCodeOf(record)
  const sub = (record && (record.categoryName || record.category || '')) || ''
  if (code === 'plan') return `${sub}文件详情描述`
  if (code === 'visit' || code === 'exchange') return `${sub}详情描述`
  return `${sub || '试点'}安装详情`
}

function remarkLabelOf(record) {
  const sub = (record && (record.categoryName || record.category || '')) || ''
  return `${sub || '试点'}安装备注`
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

// 兼容 'YYYY-MM-DD HH:mm' / ISO 'T' 分隔两种后端口径
function parseDate(value) {
  if (!value) return null
  const normalized = String(value).replace('T', ' ').replace(/-/g, '/')
  const date = new Date(normalized)
  return isNaN(date.getTime()) ? null : date
}

function monthKeyOf(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

function relativeDayText(value, now) {
  const target = parseDate(value)
  if (!target) return ''
  const targetStart = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime()
  const nowStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const days = Math.round((nowStart - targetStart) / 86400000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  if (days <= 30) return `${days} 天前`
  return ''
}

// 记录按月倒序分组(PC groupRecordsByMonth desc 口径;无有效时间不进组,拨盘按数字年定位)
function groupRecordsByMonth(records) {
  const sorted = (records || []).slice().sort((a, b) => {
    const left = String((a && a.createdAt) || '')
    const right = String((b && b.createdAt) || '')
    return left < right ? 1 : left > right ? -1 : 0
  })
  const groups = []
  const indexOf = new Map()
  sorted.forEach((record) => {
    const date = parseDate(record && record.createdAt)
    if (!date) return
    const key = monthKeyOf(date)
    if (!indexOf.has(key)) {
      indexOf.set(key, groups.length)
      groups.push({ key, year: String(date.getFullYear()), month: date.getMonth() + 1, items: [] })
    }
    groups[indexOf.get(key)].items.push(record)
  })
  return groups
}

// 年份盘自增:最早数据年..当前系统年连续(跨年自动多一年),无数据年份置暗
function buildDialYears(groups, now) {
  const nowYear = String(now.getFullYear())
  const years = groups.map((group) => group.year).filter((year) => /^\d{4}$/.test(year))
  const has = new Set(years)
  const numbers = years.map(Number)
  const min = numbers.length ? Math.min(...numbers, Number(nowYear)) : Number(nowYear)
  const max = numbers.length ? Math.max(...numbers, Number(nowYear)) : Number(nowYear)
  const out = []
  for (let year = min; year <= max; year += 1) {
    out.push({ key: String(year), label: String(year), dim: !has.has(String(year)), today: String(year) === nowYear })
  }
  return out
}

// 月盘固定 12 个月,无数据置暗仍可选;有未读的月份带红点
function buildDialMonths(groups, selYear, now) {
  const yearGroups = new Map(groups.filter((group) => group.year === String(selYear)).map((group) => [group.month, group]))
  const isCurrentYear = String(now.getFullYear()) === String(selYear)
  return Array.from({ length: 12 }, (_, index) => {
    const month = index + 1
    const group = yearGroups.get(month) || null
    return {
      m: month,
      label: `${month}月`,
      dim: !group,
      hasUnread: Boolean(group && group.items.some((record) => record.unread)),
      today: isCurrentYear && month === now.getMonth() + 1
    }
  })
}

// 日历盘:整月 + 1..月末(按拨盘年+月生成完整日历,闰年自动;记录只决定哪天亮起)
function buildDialDays(groups, selYear, selMonth, now) {
  const year = Number(selYear)
  const month = Number(selMonth)
  if (!year || !month) return [{ d: 0, label: '整月', dim: false, today: false }]
  const group = groups.find((item) => item.year === String(year) && item.month === month) || null
  const withData = new Set((group ? group.items : []).map((record) => {
    const date = parseDate(record.createdAt)
    return date ? date.getDate() : NaN
  }).filter((day) => !isNaN(day)))
  const total = new Date(year, month, 0).getDate()
  const todayD = now.getFullYear() === year && now.getMonth() + 1 === month ? now.getDate() : 0
  const out = [{ d: 0, label: '整月', dim: false, today: false }]
  for (let day = 1; day <= total; day += 1) {
    out.push({ d: day, label: `${day}日`, dim: !withData.has(day), today: day === todayD })
  }
  return out
}

function thumbUrlOf(file, token) {
  if (!token || !file || file.type !== 'image' || file.id == null) return ''
  const base = getDownloadUrl(file, true)
  if (!base) return ''
  const sep = base.indexOf('?') >= 0 ? '&' : '?'
  return base + sep + 'token=' + encodeURIComponent(token)
}

// 记录卡视图:单行元信息条(时间蓝色加大 + 阶段色点 + 彩色分值 + 灰标签内联) → 正文大字 → 备注 → 附件凭证
function buildItemView(record, canDelete, canEdit, now, token) {
  const code = incentiveCodeOf(record)
  const stageColor = STAGE_COLORS[code] || '#303133'
  const date = parseDate(record && record.createdAt)
  const files = buildIncentiveFiles(record).map((file, fileIndex) => ({
    ...file,
    fileIndex,
    tagText: file.tag || '',
    tagColor: TAG_COLORS[file.tag] || '#64748b',
    showTag: Boolean(file.tag),
    showThumb: file.type === 'image',
    nameDisplay: file.name || '附件'
  }))
  files.forEach((file) => { file.thumbUrl = thumbUrlOf(file, token) })
  const metaChips = [
    { label: '上传人', text: record && (record.userName || record.updatedByName) },
    { label: '周期', text: record && record.period },
    { label: '行业', text: record && record.industry },
    { label: '相关产品', text: record && record.relatedProducts },
    { label: '具体产品', text: record && record.specificProduct }
  ].filter((chip) => chip.text !== undefined && chip.text !== null && chip.text !== '')
    .map((chip) => ({ label: chip.label, text: String(chip.text) }))
  return {
    id: record && record.id,
    showUnreadDot: Boolean(record && record.unread),
    dayText: date ? `${pad2(date.getMonth() + 1)}月${pad2(date.getDate())}日` : '',
    timeText: date ? `${pad2(date.getHours())}:${pad2(date.getMinutes())}` : (record && record.createdAt) || '',
    relativeText: relativeDayText(record && record.createdAt, now),
    stageColor,
    stageText: (record && (record.category || record.categoryName)) || '四项服务',
    stageSub: (record && record.categoryName) || '',
    scoreText: `+${(record && (record.score || record.points)) || 0} 分`,
    scoreColor: stageColor,
    chips: metaChips,
    showDetailBlock: Boolean(record && record.preInstallQuestion),
    detailLabel: detailLabelOf(record),
    preInstallQuestionText: (record && record.preInstallQuestion) || '',
    showTrialRemark: Boolean(record && record.remark && code === 'trial'),
    remarkLabel: remarkLabelOf(record),
    remarkText: (record && record.remark) || '',
    showFilesBlock: files.length > 0,
    countText: `附件凭证 · ${files.length} 项`,
    files,
    showDeleteAction: Boolean(canDelete),
    showEditAction: Boolean(canEdit)
  }
}

function safeApp() {
  return typeof getApp === 'function' ? getApp() : null
}

function buildDetailView(data) {
  const app = safeApp()
  const demoMode = Boolean(app && app.globalData && app.globalData.demoMode)
  const user = (app && app.globalData && app.globalData.currentUser) || {}
  const userId = user.id || user.userId || ''
  const canDeleteRecords = Boolean(data.canDeleteRecords)
  const canDeleteOf = (record) => {
    if (!record || demoMode) return false
    const ownerId = record.userId || record.salespersonId
    return Boolean(canDeleteRecords || (userId && ownerId && String(userId) === String(ownerId)))
  }
  // 编辑/追加文件与删除同口径: admin ∨ can_edit 授权 ∨ 本人记录(后端 updateRecord 同守卫)
  const canEditRecords = Boolean(data.canEditRecords)
  const canEditOf = (record) => {
    if (!record || demoMode) return false
    const ownerId = record.userId || record.salespersonId
    return Boolean(canEditRecords || (userId && ownerId && String(userId) === String(ownerId)))
  }
  const now = new Date()
  const loading = Boolean(data.loading)
  const records = data.records || []
  const groups = groupRecordsByMonth(records)
  const dialYears = buildDialYears(groups, now)
  const selYear = data.selYear || (dialYears.length ? dialYears[dialYears.length - 1].key : String(now.getFullYear()))
  const selMonth = Number(data.selMonth) || now.getMonth() + 1
  const selDay = Number(data.selDay) || 0
  const dialMonths = buildDialMonths(groups, selYear, now)
  const dialDays = buildDialDays(groups, selYear, selMonth, now)
  const yearIndex = Math.max(0, dialYears.findIndex((item) => item.key === String(selYear)))
  const dayIndex = Math.min(Math.max(selDay, 0), dialDays.length - 1)
  const activeGroup = groups.find((group) => group.key === `${selYear}-${pad2(selMonth)}`) || null
  const displayRecords = !activeGroup ? [] : (selDay ? activeGroup.items.filter((record) => {
    const date = parseDate(record.createdAt)
    return date ? date.getDate() === selDay : false
  }) : activeGroup.items)
  const items = displayRecords.map((record) => buildItemView(record, canDeleteOf(record), canEditOf(record), now, data.dlToken || ''))
  const customer = data.customer || {}
  const customerName = customer.customerName || (data.record && (data.record.customerName || data.record.name)) || ''
  const latest = records.reduce((best, item) => {
    if (!best) return item
    return String(item.createdAt || '') > String(best.createdAt || '') ? item : best
  }, null)
  const latestDate = latest ? parseDate(latest.createdAt) : null
  const unreadCount = records.filter((record) => record.unread).length
  const paneLabelText = `${selYear}年${selMonth}月${selDay ? ` · ${selDay}日` : ''}`
  const scoreValue = data.customerScore !== null && data.customerScore !== undefined
    ? data.customerScore
    : customer.totalScore || 0
  return {
    showLoading: loading,
    loadingText: '加载中...',
    hasRecord: Boolean(data.record),
    showEmpty: !loading && !data.record,
    heroInitial: (customerName || '?').charAt(0),
    customerName,
    industryText: customer.industry || '',
    lastUpdateText: latestDate ? `${latestDate.getFullYear()}-${pad2(latestDate.getMonth() + 1)}-${pad2(latestDate.getDate())} ${pad2(latestDate.getHours())}:${pad2(latestDate.getMinutes())}` : '—',
    scoreText: String(scoreValue),
    scoreCaption: '客户累计积分',
    recordCountText: `${records.length} 条记录`,
    monthCountText: `${groups.length} 个月份`,
    showUnread: unreadCount > 0,
    unreadCountText: `${unreadCount} 未读`,
    showDial: records.length > 0,
    dialYears,
    dialMonths,
    dialDays,
    dialValue: [yearIndex, selMonth - 1, dayIndex],
    nowDateText: `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())} 周${WEEK_LABELS[now.getDay()]}`,
    nowTimeText: `${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}`,
    paneLabelText,
    paneCountText: `${items.length} 条`,
    showPaneEmpty: !loading && records.length > 0 && items.length === 0,
    paneEmptyTitle: `${paneLabelText}，此时间无数据`,
    paneEmptyHint: '拨动上方年 / 月 / 日转盘，深色选项才有激励记录',
    items,
    previewLoading: Boolean(data.previewing)
  }
}

// 演示数据补 createdAt(最近数天铺开),让拨盘在演示模式也有真实层次
function decorateDemoRecords() {
  const base = Date.now()
  return (incentiveRecords || []).map((item, index) => {
    if (item.createdAt) return item
    const date = new Date(base - index * 3 * 86400000 - 5400000)
    return { ...item, createdAt: `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}` }
  })
}

Page({
  data: {
    id: '',
    customer: null,
    records: [],
    record: null,
    selYear: '',
    selMonth: 0,
    selDay: 0,
    customerScore: null,
    dlToken: '',
    detailView: buildDetailView({}),
    loading: false,
    previewing: false,
    previewVideoPath: '',
    canDeleteRecords: false,
    canEditRecords: false
  },
  setDetailData(patch) {
    const next = { ...this.data, ...patch }
    const detailView = buildDetailView(next)
    this.setData({
      ...patch,
      detailView
    })
    // 【picker-view 修复 2026-09-20】列数据与 value 同帧 setData 时,列不按 value 滚动
    // (实测:多年份时年轮停在首项,月份列恰好对位是巧合)。下一拍重申同值 value 即触发对位;
    // 连续重建只保留最后一次,用户滚动引发的 change 也会走到这里并带上最新值,不会互相打架
    if (this._dialValueTimer) clearTimeout(this._dialValueTimer)
    this._dialValueTimer = setTimeout(() => {
      this._dialValueTimer = null
      this.setData({ 'detailView.dialValue': detailView.dialValue })
    }, 50)
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项服务查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setDetailData({ id: options.id || '', canDeleteRecords: !getApp().globalData.demoMode && isAdmin(), canEditRecords: !getApp().globalData.demoMode && isAdmin() })
    this.loadGrantCheck()
    this.loadDetail()
    this.startClock()
  },
  onShow() {
    this.startClock()
  },
  onHide() {
    this.stopClock()
  },
  onUnload() {
    this.stopClock()
    if (this._dialValueTimer) {
      clearTimeout(this._dialValueTimer)
      this._dialValueTimer = null
    }
  },
  // 实时日历:每秒跳动时钟;跨天时整体重建让三盘"今天"蓝点跟进
  startClock() {
    if (this._nowTimer) return
    this._nowDateKey = this.nowDateKey()
    this._nowTimer = setInterval(() => {
      const dateKey = this.nowDateKey()
      if (dateKey !== this._nowDateKey) {
        this._nowDateKey = dateKey
        this.setDetailData({})
        return
      }
      const now = new Date()
      this.setData({ 'detailView.nowTimeText': `${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}` })
    }, 1000)
  },
  stopClock() {
    if (this._nowTimer) {
      clearInterval(this._nowTimer)
      this._nowTimer = null
    }
  },
  nowDateKey() {
    const now = new Date()
    return `${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate()}`
  },
  loadGrantCheck() {
    if (getApp().globalData.demoMode) return
    getGrantCheck().then((grant) => {
      this.setDetailData({ canDeleteRecords: Boolean(grant && grant.canDelete) || isAdmin(), canEditRecords: Boolean(grant && grant.canEdit) || isAdmin() })
    }).catch(() => this.setDetailData({}))
  },
  loadDetail() {
    this.setDetailData({ loading: true })
    if (getApp().globalData.demoMode) {
      const rows = decorateDemoRecords()
      const focused = rows.find((row) => String(row.id) === String(this.data.id)) || rows[0]
      if (!focused) {
        this.setDetailData({ loading: false })
        return Promise.resolve()
      }
      const name = focused.name || focused.customerName
      const siblings = rows.filter((row) => (row.name || row.customerName) === name)
      this.applyCustomer({
        customerId: '',
        customerName: name,
        industry: focused.industry || '其他',
        totalScore: siblings.reduce((sum, row) => sum + (Number(row.score) || 0), 0)
      }, siblings, focused)
      this.setDetailData({ loading: false, customerScore: siblings.reduce((sum, row) => sum + (Number(row.score) || 0), 0) })
      return Promise.resolve()
    }
    return getCustomerRecords().then((rows) => {
      const row = (rows || []).find((customer) => (customer.records || []).some((item) => String(item.id) === String(this.data.id)))
      const record = row && row.records.find((item) => String(item.id) === String(this.data.id))
      if (!record) throw new Error('找不到四项服务记录')
      this.applyCustomer(row, row.records, record)
      if (record.unread) this.markFocusedRead(record)
    }).catch((error) => showError(error, '四项服务详情加载失败')).finally(() => this.setDetailData({ loading: false }))
  },
  // 建立客户台账态:该客户全部记录 + 拨盘初始对位(聚焦记录所在月,无则最新月)
  applyCustomer(row, records, focused) {
    const enriched = (records || []).map((item) => ({
      ...item,
      customerName: (row && row.customerName) || item.customerName || '',
      industry: item.industry || (row && row.industry) || '其他'
    }))
    const groups = groupRecordsByMonth(enriched)
    const focusDate = parseDate(focused && focused.createdAt)
    const focusGroup = focusDate ? groups.find((group) => group.key === monthKeyOf(focusDate)) : null
    const initial = focusGroup || groups[0]
    const now = new Date()
    this.setDetailData({
      customer: {
        customerId: (row && row.customerId) || '',
        customerName: (row && row.customerName) || focused.customerName || focused.name || '',
        industry: (row && row.industry) || focused.industry || '其他',
        totalScore: (row && row.totalScore) || 0
      },
      records: enriched,
      record: focused,
      customerScore: null,
      selYear: initial ? initial.year : String(now.getFullYear()),
      selMonth: initial ? initial.month : now.getMonth() + 1,
      selDay: 0,
      previewVideoPath: ''
    })
    this.loadCustomerScore(focused)
    this.loadThumbnails()
  },
  // 与列表页同口径的乐观已读(失败回滚)
  markFocusedRead(record) {
    const index = this.data.records.findIndex((item) => String(item.id) === String(record.id))
    if (index < 0) return
    this.setData({ [`records[${index}].unread`]: false })
    this.setDetailData({})
    if (getApp().globalData.demoMode) return
    markIncentiveRead(record.id).catch(() => {
      this.setData({ [`records[${index}].unread`]: true })
      this.setDetailData({})
    })
  },
  loadCustomerScore(record) {
    if (!record || !record.customerId || getApp().globalData.demoMode) return
    getScoreByCustomer(record.customerId).then((score) => {
      this.setDetailData({ customerScore: Number(score || 0) })
    }).catch((error) => showError(error, '客户累计积分加载失败'))
  },
  // 缩略图凭证:取一次复用;之后每次拨盘重建记录卡都会直接带上签名 URL
  loadThumbnails() {
    if (getApp().globalData.demoMode) return
    if (this.data.dlToken || this._dlTokenPromise) return this._dlTokenPromise
    this._dlTokenPromise = getDownloadToken().then((result) => {
      this._dlTokenPromise = null
      const token = result && (result.token || (result.data && result.data.token)) || ''
      if (!token) throw new Error('缩略图凭证为空')
      this.setDetailData({ dlToken: token })
    }).catch((error) => {
      this._dlTokenPromise = null
      showError(error, '缩略图加载失败')
    })
    return this._dlTokenPromise
  },
  // 位置语义:picker 停稳即选中;年/月变化级联重排日盘并回"整月"
  onDialChange(event) {
    const value = event.detail.value || []
    const dial = this.data.detailView
    const yearItem = dial.dialYears[value[0]] || dial.dialYears[dial.dialYears.length - 1]
    const monthItem = dial.dialMonths[value[1]] || dial.dialMonths[0]
    const dayItem = dial.dialDays[value[2]] || dial.dialDays[0]
    if (!yearItem || !monthItem || !dayItem) return
    const yearChanged = yearItem.key !== String(this.data.selYear)
    const monthChanged = monthItem.m !== Number(this.data.selMonth)
    this.setDetailData({
      selYear: yearItem.key,
      selMonth: monthItem.m,
      selDay: yearChanged || monthChanged ? 0 : dayItem.d
    })
  },
  goToday() {
    const now = new Date()
    this.setDetailData({ selYear: String(now.getFullYear()), selMonth: now.getMonth() + 1, selDay: now.getDate() })
  },
  findAttachment(event) {
    const dataset = event.currentTarget.dataset
    const item = this.data.detailView.items[Number(dataset.itemIndex)]
    return item && item.files ? item.files[Number(dataset.fileIndex)] : null
  },
  previewAttachment(event) {
    const file = this.findAttachment(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    return this.fetchAttachment(file, 'preview')
  },
  downloadAttachment(event) {
    const file = this.findAttachment(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    return this.fetchAttachment(file, 'download')
  },
  // 【E5 去重】下载/预览/保存共用 utils/attachment.js; 本页差异: busy=previewing(经 setDetailData
  // 联动重建 detailView)、视频写 previewVideoPath、保存失败文案"保存失败"、success 内先收 loading
  fetchAttachment(file, mode) {
    return fetchRemoteAttachment({
      url: getDownloadUrl(file),
      mode,
      file,
      isBusy: this.data.previewing,
      setBusy: (busy) => this.setDetailData({ previewing: busy }),
      isImage: (item) => item.type === 'image',
      onVideo: (tempFilePath) => this.setData({ previewVideoPath: tempFilePath })
    })
  },
  closeVideoPreview() {
    this.setData({ previewVideoPath: '' })
  },
  canDeleteRecord(record) {
    if (!record || getApp().globalData.demoMode) return false
    const userId = currentUserId()
    const ownerId = record.userId || record.salespersonId
    return Boolean(this.data.canDeleteRecords || (userId && ownerId && String(userId) === String(ownerId)))
  },
  canEditRecord(record) {
    if (!record || getApp().globalData.demoMode) return false
    const userId = currentUserId()
    const ownerId = record.userId || record.salespersonId
    return Boolean(this.data.canEditRecords || (userId && ownerId && String(userId) === String(ownerId)))
  },
  // 【真机UAT补齐 2026-09-22】编辑/追加文件入口:上传页本就支持编辑态(options.id→editId,
  // 回填客户/分类/周期/五槽位附件,提交走 updateRecord 与 PC 端编辑同口径),但此前全端
  // 无任何页面带 id 进入 —— 能力建成却是死入口。编辑守卫与删除同口径(admin ∨ can_edit ∨ 本人)。
  editRecord(event) {
    const item = this.data.detailView.items[Number(event.currentTarget.dataset.itemIndex)]
    const record = item && this.data.records.find((row) => String(row.id) === String(item.id))
    if (!record || !record.id || !this.canEditRecord(record)) return wx.showToast({ title: '没有四项服务编辑权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/incentive/upload/index?id=' + record.id })
  },
  removeRecord(event) {
    const item = this.data.detailView.items[Number(event.currentTarget.dataset.itemIndex)]
    const record = item && this.data.records.find((row) => String(row.id) === String(item.id))
    if (!record || !record.id || !this.canDeleteRecord(record)) return wx.showToast({ title: '没有四项服务删除权限', icon: 'none' })
    wx.showModal({
      title: '删除四项服务记录',
      content: `确认删除“${record.customerName || record.categoryName || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(record.id).then(() => {
          // 【PR-12 契约修复 2026-10-07】删除成功置强制刷新标记: 返回列表时 onShow 绕过 30s 节流一次,
          // 立刻见删除结果;节流只拦纯回流,不拦写后回流
          getApp().globalData.forceRefresh.incentive = true
          wx.showToast({ title: '已删除', icon: 'success' })
          const rest = this.data.records.filter((row) => String(row.id) !== String(record.id))
          if (!rest.length) return setTimeout(() => wx.navigateBack(), 300)
          // 删掉某年后唯一记录时,拨盘年份自增区间收缩 —— 选中年悬空则对位到最新一组
          const groups = groupRecordsByMonth(rest)
          const patch = { records: rest }
          if (groups.length && !groups.some((group) => group.year === String(this.data.selYear))) {
            patch.selYear = groups[0].year
            patch.selMonth = groups[0].month
            patch.selDay = 0
          }
          this.setDetailData(patch)
        }).catch((error) => showError(error, '删除四项服务记录失败'))
      }
    })
  },
  goBack() { wx.navigateBack() }
})

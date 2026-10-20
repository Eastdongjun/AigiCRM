const { getReminderList, getExpiringList, getPendingCount, processReminder, ensureExpiring } = require('../../api/reminder')
// 【S3】提醒详情接口（GET /api/reminder/{id}）在 api/reminder.js 中无封装；
// 该文件不在本域可改范围，页面直接复用既有请求层，避免越权修改共享 api 模块
const request = require('../../utils/request')
const { submitApproval } = require('../../api/approval')
const { getDepts, getScopeUsers } = require('../../api/system')
const { reminderRecords } = require('../../data/demo')
const { hasPermission, requireLogin, isAdmin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { isProcessed, markProcessed } = require('../../utils/demo-reminders')
// 【E6 去重】部门树扁平化原与 approval/index 复制一份, 收敛到 utils/tree
const { flattenDepts } = require('../../utils/tree')
// 【分页 2026-10-18】演示模式对本地过滤结果做客户端分页
const { paginate } = require('../../utils/paging')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function pageTotal(payload) {
  return Number(payload && payload.total) || 0
}

const PROCESSED_STATUSES = new Set(['processed', 'read', 'done', 'followed'])
const MODE_BUTTONS = {
  pending: 'reminder-mode active',
  expiring: 'reminder-mode active',
  inactive: 'reminder-mode'
}

function normalizeKey(value) {
  return value === undefined || value === null ? '' : String(value).trim().toLowerCase()
}

function reminderTypeLabel(type) {
  const key = normalizeKey(type)
  const labels = {
    expiring: '到期提醒',
    expire: '到期提醒',
    protect_expiring: '到期提醒',
    invoice: '开票提醒',
    invoice_overdue: '开票超期提醒',
    invoice_never: '待首次开票提醒',
    share_to: '客户已分享给你',
    share_by: '你分享了客户',
    share_owner: '你的客户被分享',
    follow: '跟进提醒'
  }
  return labels[key] || (type ? String(type) : '提醒')
}

// 【A4 三轮 2026-10-09】与 PC 端状态词对齐：分享类未处理=「待查看」(只读,无需跟进)、其余未处理=「待处理」。
// 此前 MP 只有「待处理/已处理」，同一条分享提醒在 PC 显示「待查看」、MP 显示「待处理」，两端状态叫法不同
function reminderStatusText(status, type) {
  if (!status) return '—'
  if (!PROCESSED_STATUSES.has(normalizeKey(status)) && type && normalizeKey(type).startsWith('share')) return '待查看'
  return PROCESSED_STATUSES.has(normalizeKey(status)) ? '已处理' : '待处理'
}

function isReadOnlyReminder(type) {
  const key = normalizeKey(type)
  return key === 'invoice_overdue' || key === 'invoice_never' || key.startsWith('share')
}

function reminderProcessActionLabel(type) {
  return isReadOnlyReminder(type) ? '已处理' : '已跟进'
}

function reminderTimingText(item, remainingDays) {
  const type = normalizeKey(item.type || item.reminderType)
  if (type === 'invoice_overdue') return `已超期 ${item.overdueDays ?? '—'} 天未开票`
  if (type === 'invoice_never') return '待首次开票'
  if (type.startsWith('share')) return '客户分享提醒'
  if (remainingDays === undefined || remainingDays === null || remainingDays === '') return '—'
  const days = Number(remainingDays)
  if (!Number.isFinite(days)) return '—'
  return days < 0 ? `已过期 ${Math.abs(days)} 天` : `剩余 ${days} 天到期`
}

function displayText(value, fallback = '—') {
  return value === undefined || value === null || value === '' ? fallback : String(value)
}

function remainingDaysText(value) {
  return value === undefined || value === null || value === '' ? '—' : `${value} 天`
}

// 详情接口只回客户 expireDate（无 remainingDays），到期类提醒按自然日补齐剩余天数
function daysUntil(dateText) {
  const parts = String(dateText || '').trim().slice(0, 10).split('-')
  if (parts.length !== 3) return undefined
  const year = Number(parts[0])
  const month = Number(parts[1])
  const day = Number(parts[2])
  if (!year || !month || !day) return undefined
  const now = new Date()
  const todayUtc = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  const targetUtc = Date.UTC(year, month - 1, day)
  return Math.round((targetUtc - todayUtc) / 86400000)
}

function normalizeReminder(item, options = {}) {
  const customer = item.customer || {}
  const region = customer.region || [customer.province, customer.city, customer.district].filter(Boolean).join('')
  const type = item.type || item.reminderType || options.defaultType || 'follow'
  const status = item.status || 'pending'
  // 【S3】到期时间取值补客户到期日（详情接口把 expireDate 放在 customer 下）；
  // 到期类提醒缺 remainingDays 时按 expireDate 计算，两个来源都缺失则保持空并由显示层给明确占位
  const expireDate = item.expireDate || item.dueDate || customer.expireDate || ''
  let remainingDays = item.remainingDays ?? customer.remainingDays
  if ((remainingDays === undefined || remainingDays === null || remainingDays === '')
    && normalizeKey(type) === 'expiring') {
    remainingDays = daysUntil(expireDate)
  }
  const normalized = {
    // 【工作流复核确认修复】expiring-list 行无 id 只有 reminderId: id 用 reminderId 兜底,
    // 原 process/processAll 以 item.id 守卫 → 到期行"已跟进/一键清空"点击后静默失效
    id: item.id || item.reminderId,
    reminderId: item.reminderId || item.id,
    customerId: item.customerId,
    customerName: item.customerName || customer.customerName || item.title || '未命名提醒',
    customerCode: item.customerCode || customer.customerCode || '',
    customerStatus: item.customerStatus || customer.status || '',
    industry: item.industry || customer.industry || '',
    region: item.region || region || '',
    type,
    typeLabel: reminderTypeLabel(type),
    processActionLabel: reminderProcessActionLabel(type),
    canExtend: !isReadOnlyReminder(type),
    status,
    statusText: reminderStatusText(status, type),
    timingText: reminderTimingText({ ...item, type }, remainingDays),
    expireDate,
    lastInvoiceDate: item.lastInvoiceDate || customer.lastInvoiceDate || '',
    content: item.content || item.remark || item.reason || '请及时处理该提醒',
    ownerName: item.ownerName || '',
    officeName: item.officeName || item.deptName || '',
    createdAt: item.createdAt || item.createTime || '',
    processedAt: item.processedAt || '',
    updatedAt: item.updatedAt || item.updateTime || '',
    remainingDays,
    overdueDays: item.overdueDays
  }
  return {
    ...normalized,
    typeDisplay: displayText(normalized.typeLabel || normalized.type, '提醒'),
    ownerDisplay: displayText(normalized.ownerName),
    officeDisplay: displayText(normalized.officeName),
    statusDisplay: displayText(normalized.statusText || normalized.status, '待处理'),
    // 【S3】无到期数据来源时给明确占位，而非与真实日期混淆的 "-"
    expireDateDisplay: displayText(normalized.expireDate, '暂无到期日期'),
    customerCodeDisplay: displayText(normalized.customerCode),
    customerStatusDisplay: displayText(normalized.customerStatus),
    industryDisplay: displayText(normalized.industry),
    regionDisplay: displayText(normalized.region),
    reminderIdDisplay: displayText(normalized.reminderId),
    timingDisplay: displayText(normalized.timingText),
    remainingDaysDisplay: remainingDaysText(normalized.remainingDays),
    lastInvoiceDateDisplay: displayText(normalized.lastInvoiceDate),
    createdAtDisplay: displayText(normalized.createdAt),
    processedAtDisplay: displayText(normalized.processedAt),
    updatedAtDisplay: displayText(normalized.updatedAt),
    processActionDisplay: displayText(normalized.processActionLabel, '已处理')
  }
}

function buildReminderView(state) {
  const reminders = state.reminders || []
  const dept = (state.deptOptions || [])[state.selectedDeptIndex] || {}
  const owner = (state.ownerOptions || [])[state.selectedOwnerIndex] || {}
  const loading = Boolean(state.loading)
  return {
    refreshLoading: loading,
    refreshText: loading ? '刷新中' : '刷新',
    pendingModeClass: state.viewMode === 'pending' ? MODE_BUTTONS.pending : MODE_BUTTONS.inactive,
    expiringModeClass: state.viewMode === 'expiring' ? MODE_BUTTONS.expiring : MODE_BUTTONS.inactive,
    deptLabel: dept.name || '全部部门',
    ownerLabel: owner.name || '全部负责人',
    listCountText: `当前列表 ${reminders.length} 条`,
    disableProcessAll: Boolean(loading || state.processingAll || reminders.length === 0),
    showLoading: loading && reminders.length === 0,
    pagerLoading: Boolean(loading),
    loadingText: '加载中...',
    showEmpty: !loading && reminders.length === 0,
    hasSelectedReminder: Boolean(state.selectedReminder)
  }
}

Page({
  data: {
    reminders: [],
    pendingCount: 0,
    keyword: '',
    viewMode: 'pending',
    loading: false,
    // 【分页 2026-10-18】显式分页：当前页 / 每页条数(10,30 可切) / 服务端总数
    page: 1,
    pageSize: 10,
    total: 0,
    processingAll: false,
    selectedReminder: null,
    selectedReminderIndex: -1,
    deptOptions: [{ id: '', name: '全部部门' }],
    ownerOptions: [{ id: '', name: '全部负责人' }],
    selectedDeptIndex: 0,
    selectedOwnerIndex: 0,
    reminderView: buildReminderView({
      reminders: [],
      loading: false,
      processingAll: false,
      viewMode: 'pending',
      deptOptions: [{ id: '', name: '全部部门' }],
      ownerOptions: [{ id: '', name: '全部负责人' }],
      selectedDeptIndex: 0,
      selectedOwnerIndex: 0,
      selectedReminder: null
    })
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, reminderView: buildReminderView(next) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('reminder:view')) {
      wx.showToast({ title: '没有提醒权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadFilters()
    // 【2026-09-22 缺陷修复】ensure-expiring 后端已收紧为 admin-only(P1-09 批次): 普通用户调用必 403,
    // 且此前 403 也会触发 request.js 踢登录——非 admin 打开提醒页会被登出。到期提醒由服务端
    // CustomerExpireTask 每晚生成兜底, 客户端补生成仅 admin 有意义; demo 档不发真实请求
    const app = getApp()
    const catchUp = app.globalData.demoMode || !isAdmin() ? Promise.resolve() : ensureExpiring().catch(() => {})
    catchUp.finally(() => this.refresh())
  },

  onKeywordInput(e) {
    this.applyState({ keyword: e.detail.value })
  },

  refresh() {
    this.loadPendingCount()
    this.loadReminders()
  },

  loadPendingCount() {
    if (getApp().globalData.demoMode) {
      this.applyState({ pendingCount: reminderRecords.filter((item) => item.status === 'pending' && !isProcessed(item.id)).length })
      return
    }
    getPendingCount().then((count) => {
      this.applyState({ pendingCount: count || 0 })
    }).catch((error) => showError(error, '提醒数量加载失败'))
  },

  /** 【分页 2026-10-18】显式分页：任何页码都"替换"当前页数据（不再触底追加）。
   *  视图/筛选/搜索后统一 page=1；请求序号守卫防止慢请求覆盖新列表。 */
  loadReminders(page = 1, pageSize = this.data.pageSize) {
    const size = pageSize > 0 ? pageSize : 10
    const targetPage = Math.max(1, Number(page) || 1)
    if (getApp().globalData.demoMode) {
      const filtered = this.filterReminders(reminderRecords
        .filter((item) => !isProcessed(item.id))
        .map((item) => normalizeReminder(item, this.reminderNormalizeOptions())))
      // 演示模式无服务端分页：对本地过滤结果做客户端分页
      const paged = paginate(filtered, targetPage, size)
      this.applyState({ reminders: paged.rows, loading: false, page: paged.page, pageSize: paged.pageSize, total: paged.total })
      return
    }
    const requestSeq = (this._remindersReqSeq = (this._remindersReqSeq || 0) + 1)
    const loader = this.data.viewMode === 'expiring' ? getExpiringList : getReminderList
    const params = this.data.viewMode === 'expiring'
      ? this.buildReminderParams(targetPage, size)
      : { ...this.buildReminderParams(targetPage, size), status: 'pending' }
    // 先同步页码/页大小，分页条立即反映用户操作
    this.applyState({ loading: true, page: targetPage, pageSize: size })
    loader(params).then((payload) => {
      if (requestSeq !== this._remindersReqSeq) return
      // 【S6】到期客户行接口不返回 type，按当前视图补默认类型，避免统一兜底成"跟进提醒"
      const rows = normalizePage(payload).map((item) => normalizeReminder(item, this.reminderNormalizeOptions()))
      const rawTotal = pageTotal(payload)
      const reminders = this.filterReminders(rows)
      // total 优先用服务端总数；缺失时按"本页是否取满"兜底，保证 pager 能前进且不停在空页
      const total = rawTotal > 0
        ? rawTotal
        : (rows.length >= size ? targetPage * size + 1 : (targetPage - 1) * size + rows.length)
      const totalPages = Math.max(1, Math.ceil(total / size))
      // 页码越界（处理后总数变少）：回到最后一页，避免停在空页
      if (targetPage > totalPages) {
        this.loadReminders(totalPages, size)
        return
      }
      this.applyState({ reminders, page: targetPage, total })
    }).catch((error) => {
      // 旧请求失败不弹错（新请求会接管界面）
      if (requestSeq === this._remindersReqSeq) showError(error, '提醒加载失败')
    }).finally(() => {
      if (requestSeq !== this._remindersReqSeq) return
      this.applyState({ loading: false })
    })
  },

  // 【体检 P1 2026-10-12】待处理模式的 keyword 后端 /reminder/list 不消费(形参零引用),
  // PC 已知情改客户端过滤而 MP 未同步——搜索框可见但恒无效; 此处同款客户端兜底。
  // 到期模式走 /expiring-list, 后端已消费 keyword, 不重复过滤。
  // 【分页 2026-10-18】过滤作用于当前页数据；搜索会重置回第 1 页重新请求。
  reminderNormalizeOptions() {
    return { defaultType: this.data.viewMode === 'expiring' ? 'expiring' : '' }
  },

  filterReminders(rows) {
    if (this.data.viewMode === 'expiring') return rows
    const kw = this.data.keyword.trim().toLowerCase()
    if (!kw) return rows
    return rows.filter((item) =>
      String(item.customerName || '').toLowerCase().includes(kw)
      || String(item.customerCode || '').toLowerCase().includes(kw)
      || String(item.typeLabel || '').toLowerCase().includes(kw))
  },

  /** 显式分页后不再触底追加；保留空实现避免旧触底逻辑回流 */
  onReachBottom() {},

  /** 分页条事件：切换页码/每页条数；pageSize 变化时回到第 1 页 */
  onPagerChange(e) {
    const detail = e.detail || {}
    const nextPageSize = Number(detail.pageSize) || this.data.pageSize || 10
    const nextPage = nextPageSize === this.data.pageSize ? (Number(detail.page) || 1) : 1
    this.loadReminders(nextPage, nextPageSize)
  },

  buildReminderParams(page = this.data.page, pageSize = this.data.pageSize) {
    const size = pageSize > 0 ? pageSize : 10
    const dept = this.data.deptOptions[this.data.selectedDeptIndex] || {}
    const owner = this.data.ownerOptions[this.data.selectedOwnerIndex] || {}
    const params = {
      current: page || 1,
      size,
      keyword: this.data.keyword.trim() || undefined
    }
    if (dept.id) params.deptId = dept.id
    if (owner.id) params.ownerId = owner.id
    return params
  },

  async loadFilters() {
    if (getApp().globalData.demoMode) return
    try {
      const [deptRows, userRows] = await Promise.all([
        getDepts().catch((error) => {
          showError(error, '部门筛选加载失败')
          return []
        }),
        // 负责人筛选=范围感知人员(2026-09-23:选人接口改集团边界后,筛选用它会列出范围外人→空列表)
        getScopeUsers().catch((error) => {
          showError(error, '负责人筛选加载失败')
          return []
        })
      ])
      const depts = [{ id: '', name: '全部部门' }, ...flattenDepts(deptRows && (deptRows.data || deptRows.records || deptRows))]
      const owners = [{ id: '', name: '全部负责人' }, ...(Array.isArray(userRows) ? userRows : []).map((user) => ({ id: user.id, name: user.realName || user.username || `用户${user.id}` }))]
      this.applyState({ deptOptions: depts, ownerOptions: owners })
    } catch (error) {
      showError(error, '筛选项加载失败')
    }
  },

  search() {
    this.loadReminders()
  },

  chooseDept(e) {
    this.applyState({ selectedDeptIndex: Number(e.detail.value || 0) })
    this.loadReminders()
  },

  chooseOwner(e) {
    this.applyState({ selectedOwnerIndex: Number(e.detail.value || 0) })
    this.loadReminders()
  },

  switchView(e) {
    const viewMode = e.currentTarget.dataset.mode
    if (viewMode === this.data.viewMode) return
    this.applyState({ viewMode })
    this.loadReminders()
  },

  openCustomer(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=reminder` })
  },

  openSelectedCustomer() {
    const item = this.data.selectedReminder
    if (!item || !item.customerId) return wx.showToast({ title: '未关联客户', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=reminder` })
  },

  openDetail(e) {
    const index = Number(e.currentTarget.dataset.index)
    const item = this.data.reminders[index]
    if (!item) return
    this.applyState({ selectedReminder: item, selectedReminderIndex: index })
    // 【S3】列表接口只回填客户名/负责人/部门/最近开票日，客户编号/状态/行业/地区/到期日等
    // 只在详情接口 /api/reminder/{id} 上；打开详情时补拉并合并，失败保持列表行字段可读
    this.loadReminderDetail(item)
  },

  loadReminderDetail(item) {
    if (getApp().globalData.demoMode) return
    const reminderId = item.reminderId || item.id
    if (!reminderId) return
    const requestSeq = (this._detailReqSeq = (this._detailReqSeq || 0) + 1)
    request({ url: '/api/reminder/' + reminderId }).then((payload) => {
      if (requestSeq !== this._detailReqSeq || !payload) return
      const current = this.data.selectedReminder
      // 用户已关闭/切换详情时丢弃迟到的详情响应
      if (!current || String(current.id) !== String(item.id)) return
      const merged = normalizeReminder({
        ...payload,
        id: payload.id || item.id,
        remainingDays: payload.remainingDays ?? item.remainingDays
      })
      this.applyState({ selectedReminder: { ...item, ...merged } })
    }).catch((error) => {
      // 已关闭/切换详情后，旧请求失败不再打扰用户
      if (requestSeq === this._detailReqSeq) showError(error, '提醒详情加载失败')
    })
  },

  closeDetail() { this.applyState({ selectedReminder: null, selectedReminderIndex: -1 }) },

  process(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    // 【体检 P2 2026-10-12】未物化行(id 空)给出明确反馈而非静默 return——与 PC 端同场景
    // 的告警口径一致(此前点击「已跟进」无任何反应)
    if (!item || !item.id) {
      return wx.showToast({ title: '该提醒实体尚未生成，请联系管理员补生成或明日再处理', icon: 'none', duration: 2500 })
    }
    if (getApp().globalData.demoMode) {
      markProcessed(item.id)
      this.applyState({ selectedReminder: null, selectedReminderIndex: -1 })
      // 演示模式重新做本地分页，total/页码随剩余数据收敛（当前页清空时 paginate 会回退到末页）
      this.loadReminders(this.data.page, this.data.pageSize)
      this.loadPendingCount()
      wx.showToast({ title: item.processActionLabel === '已处理' ? '已标记处理' : '已标记今日跟进', icon: 'success' })
      return
    }
    processReminder(item.id).then(() => {
      wx.showToast({ title: item.processActionLabel === '已处理' ? '已标记处理' : '已标记今日跟进', icon: 'success' })
      this.refresh()
    }).catch((error) => showError(error, '处理失败'))
  },

  extend(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return
    if (getApp().globalData.demoMode) {
      wx.showToast({ title: '延期申请已提交', icon: 'success' })
      return
    }
    submitApproval({
      type: 'extend',
      customerId: String(item.customerId),
      actionAfterApprove: '保护期延长360天'
    }).then(() => {
      wx.showToast({ title: '延期申请已提交', icon: 'success' })
    }).catch((error) => showError(error, '延期申请失败'))
  },

  processAll() {
    const rows = (this.data.reminders || []).filter((item) => item && item.id)
    if (!rows.length || this.data.processingAll) return
    wx.showModal({
      title: '清空当前提醒',
      content: `确认将当前筛选出的 ${rows.length} 条提醒全部标记为已处理？`,
      success: (result) => {
        if (!result.confirm) return
        if (getApp().globalData.demoMode) {
          rows.forEach((item) => markProcessed(item.id))
          this.applyState({ reminders: [], selectedReminder: null, page: 1, total: 0 })
          this.loadPendingCount()
          wx.showToast({ title: `已处理 ${rows.length} 条`, icon: 'success' })
          return
        }
        this.applyState({ processingAll: true })
        Promise.all(rows.map((item) => processReminder(item.id).then(() => true).catch(() => false))).then((results) => {
          const done = results.filter(Boolean).length
          wx.showToast({ title: `已处理 ${done} 条`, icon: 'success' })
          this.applyState({ selectedReminder: null })
          this.refresh()
        }).catch((error) => showError(error, '批量处理失败')).finally(() => {
          this.applyState({ processingAll: false })
        })
      }
    })
  }
})

const { getReminderList, getExpiringList, getPendingCount, processReminder, ensureExpiring } = require('../../api/reminder')
const { submitApproval } = require('../../api/approval')
const { getDepts } = require('../../api/system')
const { getCustomerSimpleUsers } = require('../../api/customer')
const { reminderRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { isProcessed, markProcessed } = require('../../utils/demo-reminders')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
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
    protect_expiring: '到期提醒',
    invoice: '开票提醒',
    invoice_overdue: '开票超期提醒',
    invoice_never: '待首次开票提醒',
    share_to: '客户分享给你',
    share_by: '你分享了客户',
    share_owner: '你的客户被分享',
    follow: '跟进提醒'
  }
  return labels[key] || (type ? String(type) : '提醒')
}

function reminderStatusText(status) {
  if (!status) return '—'
  return PROCESSED_STATUSES.has(normalizeKey(status)) ? '已处理' : '待处理'
}

function isReadOnlyReminder(type) {
  const key = normalizeKey(type)
  return key === 'invoice_overdue' || key === 'invoice_never' || key.startsWith('share')
}

function reminderProcessActionLabel(type) {
  return isReadOnlyReminder(type) ? '已读' : '已跟进'
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

function normalizeReminder(item) {
  const customer = item.customer || {}
  const region = customer.region || [customer.province, customer.city, customer.district].filter(Boolean).join('')
  const remainingDays = item.remainingDays ?? customer.remainingDays
  const type = item.type || item.reminderType || 'follow'
  const status = item.status || 'pending'
  const normalized = {
    id: item.id,
    reminderId: item.reminderId || item.id,
    customerId: item.customerId,
    customerName: item.customerName || item.title || '未命名提醒',
    customerCode: item.customerCode || customer.customerCode || '',
    customerStatus: item.customerStatus || customer.status || '',
    industry: item.industry || customer.industry || '',
    region: item.region || region || '',
    type,
    typeLabel: reminderTypeLabel(type),
    processActionLabel: reminderProcessActionLabel(type),
    canExtend: !isReadOnlyReminder(type),
    status,
    statusText: reminderStatusText(status),
    timingText: reminderTimingText({ ...item, type }, remainingDays),
    expireDate: item.expireDate || item.dueDate || '',
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
    expireDateDisplay: displayText(normalized.expireDate, '-'),
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

function flattenDepts(nodes = [], depth = 0) {
  const rows = []
  for (const node of Array.isArray(nodes) ? nodes : []) {
    rows.push({ id: node.id, name: `${'　'.repeat(depth)}${node.deptName || node.name || '未命名部门'}` })
    rows.push(...flattenDepts(node.children || [], depth + 1))
  }
  return rows
}

function buildReminderView(state) {
  const reminders = state.reminders || []
  const dept = (state.deptOptions || [])[state.selectedDeptIndex] || {}
  const owner = (state.ownerOptions || [])[state.selectedOwnerIndex] || {}
  return {
    refreshLoading: Boolean(state.loading),
    refreshText: state.loading ? '刷新中' : '刷新',
    pendingModeClass: state.viewMode === 'pending' ? MODE_BUTTONS.pending : MODE_BUTTONS.inactive,
    expiringModeClass: state.viewMode === 'expiring' ? MODE_BUTTONS.expiring : MODE_BUTTONS.inactive,
    deptLabel: dept.name || '全部部门',
    ownerLabel: owner.name || '全部负责人',
    listCountText: `当前列表 ${reminders.length} 条`,
    disableProcessAll: Boolean(state.loading || state.processingAll || reminders.length === 0),
    showLoading: Boolean(state.loading),
    loadingText: '加载中...',
    showEmpty: !state.loading && reminders.length === 0,
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
    ensureExpiring().finally(() => this.refresh())
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

  loadReminders() {
    if (getApp().globalData.demoMode) {
      this.applyState({ reminders: reminderRecords.filter((item) => !isProcessed(item.id)).map(normalizeReminder) })
      return
    }
    this.applyState({ loading: true })
    const loader = this.data.viewMode === 'expiring' ? getExpiringList : getReminderList
    const params = this.data.viewMode === 'expiring'
      ? this.buildReminderParams()
      : { ...this.buildReminderParams(), status: 'pending' }
    loader(params).then((payload) => {
      this.applyState({ reminders: normalizePage(payload).map(normalizeReminder) })
    }).catch((error) => showError(error, '提醒加载失败')).finally(() => {
      this.applyState({ loading: false })
    })
  },

  buildReminderParams() {
    const dept = this.data.deptOptions[this.data.selectedDeptIndex] || {}
    const owner = this.data.ownerOptions[this.data.selectedOwnerIndex] || {}
    const params = {
      current: 1,
      size: 30,
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
        getCustomerSimpleUsers().catch((error) => {
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
    if (item) this.applyState({ selectedReminder: item, selectedReminderIndex: index })
  },

  closeDetail() { this.applyState({ selectedReminder: null, selectedReminderIndex: -1 }) },

  process(e) {
    const item = this.data.reminders[e.currentTarget.dataset.index]
    if (!item || !item.id) return
    if (getApp().globalData.demoMode) {
      markProcessed(item.id)
      this.applyState({ reminders: this.data.reminders.filter((row) => row.id !== item.id), selectedReminder: null, selectedReminderIndex: -1 })
      this.loadPendingCount()
      wx.showToast({ title: item.processActionLabel === '已读' ? '已标记已读' : '已标记今日跟进', icon: 'success' })
      return
    }
    processReminder(item.id).then(() => {
      wx.showToast({ title: item.processActionLabel === '已读' ? '已标记已读' : '已标记今日跟进', icon: 'success' })
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
      actionAfterApprove: '保护期延长'
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
        this.applyState({ reminders: [], selectedReminder: null })
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

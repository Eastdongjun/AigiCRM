const { deleteSeaPool, getSeaList, getSeaStatistics, getVisibleSeaConfigs, getClaimRecords } = require('../../api/sea')
const { submitApproval } = require('../../api/approval')
const { getDepts, getDictData } = require('../../api/system')
const { seaRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

const FALLBACK_INDUSTRIES = ['煤矿', '化工', '电力', '洗煤厂', '有色', '制造业', '能源', '其他']
const FALLBACK_DEPTS = [
  { id: 1, deptName: '内蒙古大区', deptType: 2, parentId: 0 },
  { id: 11, deptName: '内蒙办', deptType: 3, parentId: 1 },
  { id: 2, deptName: '山西大区', deptType: 2, parentId: 0 },
  { id: 21, deptName: '山西办', deptType: 3, parentId: 2 }
]
const ENTER_REASON_OPTIONS = [
  { label: '全部原因', value: '' },
  { label: '保护期满', value: '保护期满' },
  { label: '放弃客户', value: '放弃客户' },
  { label: '离职交接', value: '离职交接' }
]

function normalizeSea(item) {
  const status = item.status || 'available'
  const enterReason = item.enterReason || '保护期满'
  const canClaim = Boolean(item.canClaim)
  const canEdit = Boolean(item.canEdit)
  const canDelete = Boolean(item.canDelete)
  const available = seaStatusKey(status) === 'available'
  const permissionLabels = []
  if (available && canClaim) permissionLabels.push('可捞取')
  if (canEdit) permissionLabels.push('可编辑')
  if (available && canDelete) permissionLabels.push('可删除')
  return {
    id: item.id,
    customerId: item.customerId,
    customerName: item.customerName || '未命名客户',
    customerCode: item.customerCode || '',
    industry: item.industry || '其他',
    region: item.region || '',
    ownerName: item.originalOwnerName || '',
    enterReason,
    enterReasonLabel: seaReasonText(enterReason),
    enterDate: item.enterDate || '',
    status,
    statusKey: seaStatusKey(status),
    statusLabel: seaStatusText(status),
    canClaim,
    canEdit,
    canDelete,
    canClaimNow: available && canClaim,
    canDeleteNow: available && canDelete,
    canShowActions: (available && canClaim) || canEdit || (available && canDelete),
    permissionText: permissionLabels.length ? permissionLabels.join(' ') : '仅查看'
  }
}

function seaReasonText(reason) {
  const map = { protection_expired: '保护期满', abandoned: '放弃客户', resignation: '离职交接' }
  return map[reason] || reason || '-'
}

function seaStatusText(status) {
  const map = {
    available: '待捞取',
    AVAILABLE: '待捞取',
    pending_approval: '审批中',
    PENDING_APPROVAL: '审批中',
    claimed: '已捞取',
    CLAIMED: '已捞取'
  }
  return map[status] || status || '-'
}

function seaStatusKey(status) {
  const key = String(status || '').toLowerCase()
  if (key === 'available') return 'available'
  if (key === 'pending_approval') return 'pending_approval'
  if (key === 'claimed') return 'claimed'
  return key
}

function claimStatusText(status) {
  const map = {
    approved: '已通过',
    APPROVED: '已通过',
    rejected: '已驳回',
    REJECTED: '已驳回',
    reviewing: '待审批',
    REVIEWING: '待审批',
    pending: '待审批',
    PENDING: '待审批',
    WAITING: '待审批',
    '待审批': '待审批'
  }
  return map[status] || status || '-'
}

function normalizeClaimRecord(item) {
  const status = item.status || ''
  return {
    ...item,
    titleText: item.customerName || item.title || '捞取申请',
    status,
    statusLabel: claimStatusText(status),
    applicantName: item.applicantName || item.createByName || item.applicant || '',
    approverName: item.approverName || item.approveByName || '',
    applyDate: item.applyDate || item.createdAt || item.createTime || '',
    approveTime: item.approveTime || item.updatedAt || item.updateTime || ''
  }
}

function buildSeaRecordView(item, state = {}) {
  return {
    ...item,
    customerNameText: item.customerName || '未命名客户',
    statusText: item.statusLabel || seaStatusText(item.status),
    industryText: item.industry || '其他',
    regionText: item.region || '-',
    customerCodeText: item.customerCode || '-',
    ownerNameText: item.ownerName || '-',
    enterReasonText: item.enterReasonLabel || seaReasonText(item.enterReason),
    enterDateText: item.enterDate || '-',
    permissionText: item.permissionText || '仅查看',
    claimLoading: String(state.claimingId || '') === String(item.id || ''),
    deleteLoading: String(state.deletingId || '') === String(item.id || '')
  }
}

function buildClaimRecordView(item) {
  return {
    ...item,
    titleText: item.customerName || item.title || '捞取申请',
    statusText: item.statusLabel || claimStatusText(item.status)
  }
}

function buildSeaView(data) {
  const stats = data.stats || {}
  const records = (data.records || []).map((item) => buildSeaRecordView(item, data))
  const claimRecords = (data.claimRecords || []).map(buildClaimRecordView)
  const isLoading = Boolean(data.loading)
  const statusTextMap = {
    available: '待捞取',
    pending_approval: '审批中',
    claimed: '已捞取'
  }
  const dept = data.deptOptions[Number(data.selectedDeptIndex) || 0] || data.deptOptions[0] || {}
  const reason = (data.enterReasonOptions || []).find((item) => item.value === data.enterReason) || ENTER_REASON_OPTIONS[0]
  return {
    statsCards: [
      { key: 'total', label: '池内待捞', valueText: String(stats.total || 0) },
      { key: 'newThisMonth', label: '本月入池', valueText: String(stats.newThisMonth || 0) },
      { key: 'pendingApproval', label: '待审批捞取', valueText: String(stats.pendingApproval || 0) },
      { key: 'claimedThisMonth', label: '本月成功', valueText: String(stats.claimedThisMonth || 0) }
    ],
    statusText: statusTextMap[data.status] || '全部状态',
    poolText: data.poolId ? '已选公海池' : '全部公海池',
    deptText: dept.label || '全部部门',
    industryText: data.industry || '全部行业',
    enterReasonText: reason.label || data.enterReason || '全部原因',
    refreshLoading: isLoading,
    refreshText: isLoading ? '刷新中' : '刷新',
    showLoading: isLoading,
    loadingText: '加载中...',
    records,
    showEmpty: !isLoading && records.length === 0,
    claimRecords,
    showLatestClaim: claimRecords.length > 0,
    showClaimEmpty: claimRecords.length === 0
  }
}

Page({
  data: {
    records: [],
    stats: {},
    pools: [],
    poolId: '',
    deptId: '',
    deptOptions: [{ id: '', deptName: '全部部门', label: '全部部门' }],
    selectedDeptIndex: 0,
    status: '',
    industry: '',
    enterReason: '',
    industryOptions: FALLBACK_INDUSTRIES,
    industryPickerOptions: ['全部行业', ...FALLBACK_INDUSTRIES],
    enterReasonOptions: ENTER_REASON_OPTIONS,
    keyword: '',
    loading: false,
    claimingId: '',
    deletingId: '',
    claimRecords: [],
    seaView: buildSeaView({
      records: [],
      stats: {},
      deptOptions: [{ id: '', label: '全部部门' }],
      selectedDeptIndex: 0,
      status: '',
      industry: '',
      enterReason: '',
      enterReasonOptions: ENTER_REASON_OPTIONS,
      loading: false,
      claimRecords: []
    })
  },

  setSeaData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, seaView: buildSeaView(nextData) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('sea:view')) {
      wx.showToast({ title: '没有公海查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadPools()
    this.loadDepts()
    this.loadIndustryOptions()
    this.loadStats()
    this.loadRecords()
    this.loadClaimRecords()
  },

  loadIndustryOptions() {
    if (getApp().globalData.demoMode) {
      this.setSeaData({
        industryOptions: FALLBACK_INDUSTRIES,
        industryPickerOptions: ['全部行业', ...FALLBACK_INDUSTRIES]
      })
      return
    }
    getDictData('industry').then((list) => {
      const dictValues = (list || []).map((item) => item.dictValue || item.dictLabel).filter(Boolean)
      const industryOptions = dictValues.length ? dictValues : FALLBACK_INDUSTRIES
      this.setSeaData({ industryOptions, industryPickerOptions: ['全部行业', ...industryOptions] })
    }).catch(() => {
      this.setSeaData({
        industryOptions: FALLBACK_INDUSTRIES,
        industryPickerOptions: ['全部行业', ...FALLBACK_INDUSTRIES]
      })
    })
  },

  loadPools() {
    if (getApp().globalData.demoMode) {
      this.setSeaData({ pools: [{ id: 1, poolName: '晋蒙公海池' }] })
      return
    }
    getVisibleSeaConfigs().then((rows) => {
      this.setSeaData({ pools: rows || [] })
    }).catch((error) => {
      this.setSeaData({ pools: [] })
      showError(error, '公海配置加载失败')
    })
  },

  loadDepts() {
    if (getApp().globalData.demoMode) {
      this.applyDepts(FALLBACK_DEPTS)
      return
    }
    getDepts().then((rows) => {
      this.applyDepts(rows || [])
    }).catch(() => this.applyDepts(FALLBACK_DEPTS))
  },

  applyDepts(list) {
    const source = (list && list.length) ? list : FALLBACK_DEPTS
    const filtered = source.filter((item) => item && item.deptType !== 1)
    const byParent = {}
    filtered.forEach((item) => {
      const parentId = filtered.some((node) => String(node.id) === String(item.parentId)) ? item.parentId : 0
      const key = String(parentId || 0)
      if (!byParent[key]) byParent[key] = []
      byParent[key].push(item)
    })
    const result = [{ id: '', deptName: '全部部门', label: '全部部门' }]
    const walk = (parentId, level) => {
      ;(byParent[String(parentId || 0)] || [])
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
        .forEach((item) => {
          result.push({ ...item, label: `${'  '.repeat(level)}${item.deptName || item.name || '未命名部门'}` })
          walk(item.id, level + 1)
        })
    }
    walk(0, 0)
    this.setSeaData({ deptOptions: result, selectedDeptIndex: Math.min(this.data.selectedDeptIndex, result.length - 1) })
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      this.setSeaData({ stats: { total: 12, newThisMonth: 4, pendingApproval: 2, claimedThisMonth: 5 } })
      return
    }
    getSeaStatistics().then((stats) => {
      this.setSeaData({ stats: stats || {} })
    }).catch((error) => {
      this.setSeaData({ stats: {} })
      showError(error, '公海统计加载失败')
    })
  },

  loadRecords() {
    if (getApp().globalData.demoMode) {
      this.setSeaData({ records: seaRecords.map(normalizeSea) })
      return
    }

    this.setSeaData({ loading: true })
    getSeaList({
      current: 1,
      size: 20,
      keyword: this.data.keyword,
      poolId: this.data.poolId || undefined,
      deptId: this.data.deptId || undefined,
      status: this.data.status || undefined,
      industry: this.data.industry || undefined,
      enterReason: this.data.enterReason || undefined
    }).then((page) => {
      const rows = page && (page.records || page.list || page.rows)
      this.setSeaData({ records: (rows || []).map(normalizeSea) })
    }).catch((error) => showError(error, '公海加载失败')).finally(() => {
      this.setSeaData({ loading: false })
    })
  },

  search(e) {
    this.setSeaData({ keyword: e.detail.value || '' })
    this.loadRecords()
  },

  chooseStatus(e) {
    const statusMap = ['', 'available', 'pending_approval', 'claimed']
    this.setSeaData({ status: statusMap[e.detail.value] || '' })
    this.loadRecords()
  },

  choosePool(e) {
    const pool = this.data.pools[e.detail.value]
    this.setSeaData({ poolId: pool ? pool.id : '' })
    this.loadRecords()
  },

  chooseDept(e) {
    const index = Number(e.detail.value) || 0
    const dept = this.data.deptOptions[index]
    this.setSeaData({ selectedDeptIndex: index, deptId: dept && dept.id ? dept.id : '' })
    this.loadRecords()
  },

  chooseIndustry(e) {
    const index = Number(e.detail.value)
    this.setSeaData({ industry: index > 0 ? this.data.industryPickerOptions[index] : '' })
    this.loadRecords()
  },

  chooseEnterReason(e) {
    const selectedReason = this.data.enterReasonOptions[Number(e.detail.value)] || ENTER_REASON_OPTIONS[0]
    this.setSeaData({ enterReason: selectedReason.value })
    this.loadRecords()
  },

  openCustomer(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=sea` })
  },

  editSeaCustomer(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || !item.customerId || !item.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/form/index?id=${item.customerId}&from=sea` })
  },

  deleteSeaCustomer(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || !item.canDeleteNow) return wx.showToast({ title: '没有公海删除权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可删除', icon: 'none' })
    wx.showModal({
      title: '删除公海客户',
      content: `确认删除「${item.customerName}」？删除后不可恢复。`,
      success: (res) => {
        if (!res.confirm) return
        this.submitDelete(item)
      }
    })
  },

  submitDelete(item) {
    this.setSeaData({ deletingId: item.id })
    deleteSeaPool(item.id).then(() => {
      wx.showToast({ title: '已删除', icon: 'success' })
      this.loadRecords()
      this.loadStats()
    }).catch((error) => {
      showError(error, '删除公海客户失败')
    }).finally(() => {
      this.setSeaData({ deletingId: '' })
    })
  },

  openClaimDetail(e) {
    const item = this.data.claimRecords[Number(e.currentTarget.dataset.index)]
    if (!item) return
    wx.navigateTo({ url: `/pages/sea/claim-detail/index?title=${encodeURIComponent(item.customerName || item.title || '捞取申请')}&status=${encodeURIComponent(item.status || '')}&statusLabel=${encodeURIComponent(item.statusLabel || claimStatusText(item.status))}&createdAt=${encodeURIComponent(item.applyDate || item.createdAt || item.createTime || '')}&applicantName=${encodeURIComponent(item.applicantName || '')}&approverName=${encodeURIComponent(item.approverName || '')}&approveTime=${encodeURIComponent(item.approveTime || '')}&reason=${encodeURIComponent(item.reason || item.comment || '')}` })
  },

  claim(e) {
    const item = this.data.records[e.currentTarget.dataset.index]
    if (!item || !item.canClaimNow) return
    wx.showModal({
      title: '捞取确认',
      content: `确定捞取「${item.customerName}」？将发起审批申请`,
      success: (res) => {
        if (!res.confirm) return
        this.submitClaim(item)
      }
    })
  },

  submitClaim(item) {
    if (getApp().globalData.demoMode) {
      wx.showToast({ title: '捞取申请已提交', icon: 'success' })
      return
    }
    this.setSeaData({ claimingId: item.id })
    submitApproval({
      type: 'claim',
      customerId: String(item.customerId),
      actionAfterApprove: '客户归属→本人'
    }).then(() => {
      wx.showToast({ title: '捞取申请已提交', icon: 'success' })
      this.loadRecords()
      this.loadStats()
      this.loadClaimRecords()
    }).catch((err) => {
      showError(err, '捞取失败')
    }).finally(() => {
      this.setSeaData({ claimingId: '' })
    })
  },

  refresh() {
    this.loadPools()
    this.loadDepts()
    this.loadStats()
    this.loadRecords()
    this.loadClaimRecords()
  },

  loadClaimRecords() {
    if (getApp().globalData.demoMode) return
    getClaimRecords({ current: 1, size: 10 }).then((page) => {
      this.setSeaData({ claimRecords: (page.records || page.list || []).map(normalizeClaimRecord) })
    }).catch((error) => {
      this.setSeaData({ claimRecords: [] })
      showError(error, '认领记录加载失败')
    })
  }
})

const { deleteSeaPool, getSeaList, getSeaStatistics, getVisibleSeaConfigs, getClaimRecords } = require('../../api/sea')

const { submitApproval } = require('../../api/approval')
const { getDepts, getDictData } = require('../../api/system')
const { seaRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
// 【E6 去重】捞取申请状态映射原与 sea/claim-detail 复制一份; 组织树构建原与 incentive/statistics 同构, 均收敛到共享模块
const { claimStatusText } = require('../../utils/label-maps')
const { paginate } = require('../../utils/paging')
const { buildDeptLevelOptions } = require('../../utils/tree')

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

// 【2026-11-06 骨架】入池时间去 ISO 原始 'T' 格式, 统一 'YYYY-MM-DD HH:mm'
function formatEnterDate(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
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
    enterDateText: formatEnterDate(item.enterDate),
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
    // 【2026-11-06 骨架】筛选胶囊激活态条件化(原五枚写死 active 全蓝, 选没选看不出);
    // 注意默认项本身 label='全部部门'/'全部原因'(非空), 须与默认文案比较而非判空
    statusText: statusTextMap[data.status] || '全部状态',
    statusActive: Boolean(data.status),
    poolText: data.poolId ? '已选公海池' : '全部公海池',
    poolActive: Boolean(data.poolId),
    deptText: dept.label || '全部部门',
    deptActive: (dept.label || '全部部门') !== '全部部门',
    industryText: data.industry || '全部行业',
    industryActive: Boolean(data.industry),
    enterReasonText: reason.label || data.enterReason || '全部原因',
    reasonActive: (reason.label || data.enterReason || '全部原因') !== '全部原因',
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
    // 显式分页：page/pageSize 由 <pager> 双向驱动，total 为服务端返回的总条数
    page: 1,
    pageSize: 10,
    total: 0,
    stats: {},
    pools: [],
    poolId: '',
    deptId: '',
    deptOptions: [{ id: '', deptName: '全部部门', label: '全部部门' }],
    selectedDeptIndex: 0,
    status: '',
    industry: '',
    enterReason: '',
    // 【PR-04】初始为空：真实模式等接口返回，失败报错；演示模式由 loadIndustryOptions 回填
    industryOptions: [],
    industryPickerOptions: ['全部行业'],
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
    // 【PR-04】真实模式：字典为空就是空选项，接口失败报错；均不回退演示行业
    getDictData('industry').then((list) => {
      const dictValues = (list || []).map((item) => item.dictValue || item.dictLabel).filter(Boolean)
      this.setSeaData({ industryOptions: dictValues, industryPickerOptions: ['全部行业', ...dictValues] })
    }).catch((error) => {
      // 【P7】重试回调须指向真实存在的方法名 loadIndustryOptions（原误写 loadIndustryDict，点重试必抛 TypeError）
      showErrorWithRetry(error, '行业字典加载失败，请重试', () => this.loadIndustryOptions())
      this.setSeaData({ industryOptions: [], industryPickerOptions: ['全部行业'] })
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
      showErrorWithRetry(error, '公海配置加载失败', () => this.loadPools())
    })
  },

  loadDepts() {
    if (getApp().globalData.demoMode) {
      this.applyDepts(FALLBACK_DEPTS)
      return
    }
    // 【PR-04】真实模式接口失败：报错并只保留"全部部门"，不回退演示部门
    getDepts().then((rows) => {
      this.applyDepts(rows || [])
    }).catch((error) => {
      showErrorWithRetry(error, '部门加载失败，请重试', () => this.loadDepts())
      this.applyDepts([])
    })
  },

  applyDepts(list) {
    // 组织树构建共用 utils/tree.js(incentive/sea/statistics 三处同构), sea 侧用默认"全部部门"口径
    const result = buildDeptLevelOptions(list)
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
      // 【P7】重试回调须指向真实存在的方法名 loadStats（原误写 loadStatistics，点重试必抛 TypeError）
      showErrorWithRetry(error, '公海统计加载失败', () => this.loadStats())
    })
  },

  loadRecords(pageArg) {
    // 显式分页：pageArg 只接受正整数页码；筛选/搜索/刷新/认领后统一取第 1 页，当前页整体替换而非追加
    const page = Number(pageArg) > 0 ? Math.floor(Number(pageArg)) : 1
    const pageSize = this.data.pageSize || 10

    if (getApp().globalData.demoMode) {
      // 演示模式无服务端分页：对完整演示数据做本地切片
      const paged = paginate(seaRecords.map(normalizeSea), page, pageSize)
      this.setSeaData({
        records: paged.rows,
        page: paged.page,
        pageSize: paged.pageSize,
        total: paged.total
      })
      return
    }

    const requestSeq = (this._recordsReqSeq = (this._recordsReqSeq || 0) + 1)
    // 【M20】请求序号守卫：筛选/搜索/快速翻页时，慢返回的旧响应直接丢弃，防止覆盖新列表
    this.setSeaData({ loading: true, page, pageSize })
    getSeaList({
      current: page,
      size: pageSize,
      keyword: this.data.keyword,
      poolId: this.data.poolId || undefined,
      deptId: this.data.deptId || undefined,
      status: this.data.status || undefined,
      industry: this.data.industry || undefined,
      enterReason: this.data.enterReason || undefined
    }).then((pageData) => {
      if (requestSeq !== this._recordsReqSeq) return
      const rows = (pageData && (pageData.records || pageData.list || pageData.rows)) || []
      const total = Number(pageData && pageData.total) || 0
      this.setSeaData({
        records: rows.map(normalizeSea),
        page,
        total
      })
    }).catch((error) => {
      if (requestSeq !== this._recordsReqSeq) return
      showErrorWithRetry(error, '公海加载失败', () => this.loadRecords(page))
    }).finally(() => {
      // 旧请求不清理新请求的 loading 态（由最新请求自己的 finally 收尾）
      if (requestSeq !== this._recordsReqSeq) return
      this.setSeaData({ loading: false })
    })
  },

  // <pager> 事件 detail={page,pageSize}：页大小变化时回到第 1 页（组件已按此约定触发）
  onPagerChange(e) {
    const detail = e.detail || {}
    const pageSize = Number(detail.pageSize) || this.data.pageSize
    const page = Number(detail.page) || 1
    if (pageSize !== this.data.pageSize) this.setSeaData({ pageSize })
    this.loadRecords(page)
  },

  // 【L15】搜索输入双绑（对齐 reminder/approval 页写法），否则点搜索按钮时用的仍是旧 keyword
  onKeywordInput(e) {
    this.setSeaData({ keyword: e.detail.value || '' })
  },

  search(e) {
    this.setSeaData({ keyword: e.detail.value || '' })
    this.loadRecords()
  },

  // 【S1】搜索按钮 click 事件无 keyword（e.detail 为空），不能复用 search() 否则会清空已输入关键字；
  // 明确表达"搜索 = 第 1 页、替换列表"
  searchTap() {
    this.loadRecords(1)
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

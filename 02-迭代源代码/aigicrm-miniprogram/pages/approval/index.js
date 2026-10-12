const { approveApproval, getApprovalList, getApprovalStatistics, rejectApproval } = require('../../api/approval')
const { getDepts } = require('../../api/system')
const { approvalRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
// 【E6 去重】审批类型/状态映射与部门树扁平化原与 approval/detail、reminder 各复制一份, 收敛到共享模块
const { approvalStatusText, approvalTypeLabel, normalizeApprovalNode, pendingStatus } = require('../../utils/label-maps')
const { flattenDepts } = require('../../utils/tree')

function normalizeApproval(item) {
  const type = item.type || item.approvalType || 'unknown'
  const status = item.status || item.approvalStatus || 'pending'
  return {
    id: item.id,
    approvalCode: item.approvalCode || item.code || item.id || '',
    title: item.title || item.customerName || item.approvalType || item.type || '审批事项',
    type,
    typeLabel: approvalTypeLabel(type),
    customerId: item.customerId || item.customer_id || '',
    customerName: item.customerName || '',
    customerIndustry: item.customerIndustry || '',
    status,
    statusLabel: approvalStatusText(status),
    isPending: pendingStatus(status),
    applicant: item.applicantName || item.createByName || item.creatorName || '申请人',
    currentNode: item.currentNode || '',
    actionAfterApprove: item.actionAfterApprove || '',
    createdAt: item.createdAt || item.createTime || '',
    updatedAt: item.updatedAt || item.updateTime || '',
    reason: item.reason || item.comment || item.extraData || '暂无说明',
    nodes: (item.nodes || []).map(normalizeApprovalNode)
  }
}

function isPending(item) {
  return Boolean(item && item.isPending)
}

function buildApprovalItemView(item, state = {}) {
  const customerNameText = item.customerName || '-'
  const customerIndustryText = item.customerIndustry || '-'
  const showRejectAction = Boolean(state.canReject && item.isPending)
  const showApproveAction = Boolean(state.canApprove && item.isPending)
  return {
    ...item,
    titleText: item.title || '审批事项',
    statusText: item.statusLabel || approvalStatusText(item.status),
    typeText: item.typeLabel || approvalTypeLabel(item.type),
    codeText: item.approvalCode || item.id || '-',
    applicantText: item.applicant || '申请人',
    currentNodeText: item.currentNode || '-',
    actionAfterApproveText: item.actionAfterApprove || '-',
    showCustomer: Boolean(item.customerName || item.customerIndustry),
    customerNameText,
    customerIndustryText,
    reasonText: item.reason || '暂无说明',
    createdAtText: item.createdAt || '-',
    updatedAtText: item.updatedAt || '-',
    timeText: `发起 ${item.createdAt || '-'} · 更新 ${item.updatedAt || '-'}`,
    showCustomerAction: Boolean(item.customerId),
    showRejectAction,
    showApproveAction,
    showActions: showRejectAction || showApproveAction
  }
}

function buildApprovalStatsView(stats = {}) {
  return [
    { key: 'pending', label: '待审批', valueText: String(stats.pending || 0) },
    { key: 'mine', label: '我发起的', valueText: String(stats.mine || 0) },
    { key: 'approvedThisMonth', label: '本月通过', valueText: String(stats.approvedThisMonth || 0) },
    { key: 'rejectedThisMonth', label: '本月驳回', valueText: String(stats.rejectedThisMonth || 0) }
  ]
}

function buildApprovalTabs(tabs = [], tab, stats = {}) {
  return tabs.map((item) => ({
    ...item,
    activeClass: tab === item.key ? 'active' : '',
    // 【体检 2026-10-12】已通过/已驳回角标换全量口径 approvedTotal/rejectedTotal(INV-12 同款,
    // PC 已改 MP 漏改): 角标用 ThisMonth 而列表 tab 返回全量历史, 数字必不一致; 旧后端回退 ThisMonth
    countText: String(stats[item.countKey] ?? (stats[item.fallbackKey] || 0))
  }))
}

function pickOption(options = [], index, field, fallback) {
  const option = options[Number(index) || 0] || options[0] || {}
  return option[field] || fallback
}

function buildApprovalView(data) {
  const list = (data.approvals || []).map((item) => buildApprovalItemView(item, data))
  const isLoading = Boolean(data.loading)
  const activeTab = (data.tabs || []).find((item) => item.key === data.tab) || {}
  const typeText = pickOption(data.typeOptions, data.selectedTypeIndex, 'label', '全部类型')
  const deptText = pickOption(data.deptOptions, data.selectedDeptIndex, 'name', '全部部门')
  const keywordText = (data.keyword || '').trim() || '未输入'
  return {
    statsCards: buildApprovalStatsView(data.stats),
    tabs: buildApprovalTabs(data.tabs, data.tab, data.stats),
    typeText,
    deptText,
    queueSummary: `${activeTab.label || '全部'} · ${typeText} · ${deptText}`,
    queueChips: [
      { key: 'tab', label: '队列', value: activeTab.label || '全部' },
      { key: 'type', label: '类型', value: typeText },
      { key: 'dept', label: '部门', value: deptText },
      { key: 'keyword', label: '关键词', value: keywordText }
    ],
    queueResultText: isLoading ? '正在刷新' : `当前 ${list.length} 条`,
    refreshLoading: isLoading,
    refreshText: isLoading ? '刷新中' : '刷新',
    showLoading: isLoading,
    loadingText: '加载中...',
    list,
    showEmpty: !isLoading && list.length === 0
  }
}

Page({
  data: {
    approvals: [],
    stats: {},
    tab: 'pending',
    tabs: [
      { key: 'pending', label: '待审批', countKey: 'pending' },
      { key: 'mine', label: '我发起的', countKey: 'mine' },
      { key: 'reviewing', label: '审批中', countKey: 'reviewing' },
      { key: 'approved', label: '已通过', countKey: 'approvedTotal', fallbackKey: 'approvedThisMonth' },
      { key: 'rejected', label: '已驳回', countKey: 'rejectedTotal', fallbackKey: 'rejectedThisMonth' },
      { key: 'all', label: '全部', countKey: 'all' }
    ],
    keyword: '',
    typeOptions: [
      { value: '', label: '全部类型' },
      { value: 'claim', label: '捞取' },
      { value: 'transfer', label: '转移' },
      { value: 'extend', label: '延期' },
      { value: 'storage', label: '存储空间' }
    ],
    selectedTypeIndex: 0,
    deptOptions: [{ id: '', name: '全部部门' }],
    selectedDeptIndex: 0,
    loading: false,
    canApprove: false,
    canReject: false
    ,approvalView: buildApprovalView({
      approvals: [],
      stats: {},
      tabs: [],
      tab: 'pending',
      typeOptions: [],
      selectedTypeIndex: 0,
      deptOptions: [],
      selectedDeptIndex: 0,
      loading: false
    })
  },

  setApprovalData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, approvalView: buildApprovalView(nextData) })
  },

  refreshApprovalView() {
    this.setData({ approvalView: buildApprovalView(this.data) })
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) {
      wx.showToast({ title: '没有审批查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }

    this.setApprovalData({
      canApprove: getApp().globalData.demoMode || hasPermission('approval:approve'),
      canReject: getApp().globalData.demoMode || hasPermission('approval:reject')
    })
    this.loadFilters()
    this.loadApprovals()
    this.loadStats()
    this._loadedOnce = true
  },

  // 【PR-12 2026-09-19 首批】审批/驳回后返回列表即见结果:每次回到页面刷新待办与统计
  onShow() {
    if (this._loadedOnce) {
      this.loadApprovals()
      this.loadStats()
    }
  },

  loadApprovals() {
    if (getApp().globalData.demoMode) {
      this.setApprovalData({ approvals: approvalRecords.map(normalizeApproval) })
      return
    }

    this.setApprovalData({ loading: true })
    getApprovalList(this.buildApprovalParams()).then((page) => {
      const rows = page && (page.records || page.list || page.rows)
      this.setApprovalData({ approvals: (rows || []).map(normalizeApproval) })
    }).catch((error) => showError(error, '审批加载失败')).finally(() => {
      this.setApprovalData({ loading: false })
    })
  },

  buildApprovalParams() {
    const type = this.data.typeOptions[this.data.selectedTypeIndex] || {}
    const dept = this.data.deptOptions[this.data.selectedDeptIndex] || {}
    const params = {
      current: 1,
      size: 20,
      tab: this.data.tab,
      keyword: this.data.keyword.trim() || undefined
    }
    if (type.value) params.type = type.value
    if (dept.id) params.deptId = dept.id
    return params
  },

  loadFilters() {
    if (getApp().globalData.demoMode) return
    getDepts().then((rows) => {
      const depts = rows && (rows.data || rows.records || rows)
      this.setApprovalData({ deptOptions: [{ id: '', name: '全部部门' }, ...flattenDepts(depts)] })
    }).catch((error) => showError(error, '部门筛选加载失败'))
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      this.setApprovalData({ stats: { pending: 3, mine: 2, reviewing: 1, approvedThisMonth: 8, rejectedThisMonth: 1, all: 12 } })
      return
    }

    getApprovalStatistics().then((stats) => {
      this.setApprovalData({ stats: stats || {} })
    }).catch((error) => {
      this.setApprovalData({ stats: {} })
      showError(error, '审批统计加载失败')
    })
  },

  changeTab(e) {
    this.setApprovalData({ tab: e.currentTarget.dataset.tab || 'all' })
    this.loadApprovals()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  search() {
    this.loadApprovals()
  },

  chooseType(e) {
    this.setApprovalData({ selectedTypeIndex: Number(e.detail.value || 0) })
    this.loadApprovals()
  },

  chooseDept(e) {
    this.setApprovalData({ selectedDeptIndex: Number(e.detail.value || 0) })
    this.loadApprovals()
  },

  // 【D35】selectedApproval 详情弹窗为永假死代码已删除：selectedApproval 从未被赋非空值，
  // 弹窗（wxml）与 approve/reject/openCustomer 里的 selectedApproval 回退分支一并移除
  approve(e) {
    const item = e.currentTarget.dataset.id ? this.data.approvals.find((row) => String(row.id) === String(e.currentTarget.dataset.id)) : this.data.approvals[e.currentTarget.dataset.index]
    if (!item || !isPending(item) || !this.data.canApprove) return
    this.handleApprovalAction(item.id, 'approve')
  },

  reject(e) {
    const item = e.currentTarget.dataset.id ? this.data.approvals.find((row) => String(row.id) === String(e.currentTarget.dataset.id)) : this.data.approvals[e.currentTarget.dataset.index]
    if (!item || !isPending(item) || !this.data.canReject) return
    this.handleApprovalAction(item.id, 'reject')
  },

  openDetail(e) {
    const item = this.data.approvals[e.currentTarget.dataset.index]
    if (!item) return
    wx.navigateTo({ url: `/pages/approval/detail/index?id=${item.id}` })
  },

  openCustomer(e) {
    const item = this.data.approvals[e.currentTarget.dataset.index]
    if (!item || !item.customerId) return wx.showToast({ title: '未关联客户', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}&from=approval` })
  },

  handleApprovalAction(id, action) {
    if (getApp().globalData.demoMode) {
      const status = action === 'approve' ? 'approved' : 'rejected'
      const approvals = this.data.approvals.map((item) => String(item.id) === String(id) ? { ...item, status, statusLabel: approvalStatusText(status), isPending: pendingStatus(status) } : item)
      this.setApprovalData({ approvals })
      wx.showToast({ title: action === 'approve' ? '处理完成' : '已驳回', icon: 'success' })
      return
    }
    const request = action === 'approve' ? approveApproval : rejectApproval
    // 【工作流复核确认修复】意见收集对齐 PC 口径(通过默认"同意", 驳回必填原因):
    // 原固定提交"移动端处理"——列表与详情两入口校验不同, 且审批留痕无信息量
    const collectComment = () => new Promise((resolve) => {
      if (action === 'approve') { resolve('同意'); return }
      wx.showModal({
        title: '驳回原因',
        editable: true,
        placeholderText: '请填写驳回原因（必填）',
        success: (res) => {
          if (!res.confirm) { resolve(null); return }
          const comment = String(res.content || '').trim()
          if (!comment) {
            wx.showToast({ title: '请填写驳回原因', icon: 'none' })
            resolve(null); return
          }
          resolve(comment)
        },
        fail: () => resolve(null)
      })
    })
    collectComment().then((comment) => {
      if (!comment) return
      wx.showLoading({ title: action === 'approve' ? '审批通过中' : '审批驳回中' })
      return request(id, { comment }).then(() => {
      // 【L17】loading 与 toast 共用原生单例：先 hideLoading 再 showToast，否则成功提示被后置 hideLoading 一并关掉
      wx.hideLoading()
      wx.showToast({ title: '处理完成', icon: 'success' })
      this.loadApprovals()
      this.loadStats()
      }).catch((error) => {
        wx.hideLoading()
        showError(error, '处理失败')
      })
    })
  },

  refresh() {
    this.loadApprovals()
    this.loadStats()
  }
})

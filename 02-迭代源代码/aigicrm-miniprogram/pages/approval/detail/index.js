const { approveApproval, getApprovalDetail, rejectApproval } = require('../../../api/approval')
const { approvalRecords } = require('../../../data/demo')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
// 【E6 去重】审批类型/状态映射原与 approval/index 各复制一份, 收敛到 utils/label-maps
const { approvalStatusText, approvalTypeLabel, nodeStatusText, normalizeApprovalNode, pendingStatus } = require('../../../utils/label-maps')

function formatApprovalSupplement(value) {
  if (!value) return ''
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  const text = String(value)
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch (e) {
    return text
  }
}

function normalize(item) {
  const type = item.type || item.approvalType || 'unknown'
  const status = item.status || item.approvalStatus || 'pending'
  const supplement = formatApprovalSupplement(item.extraData || item.reason || item.comment)
  return {
    ...item,
    approvalCode: item.approvalCode || item.code || item.id || '',
    title: item.title || item.customerName || item.approvalType || item.type || '审批事项',
    type,
    typeLabel: approvalTypeLabel(type),
    customerId: item.customerId || item.customer_id || '',
    customerName: item.customerName || '',
    customerIndustry: item.customerIndustry || '',
    status,
    isPending: pendingStatus(status),
    statusLabel: approvalStatusText(status),
    applicant: item.applicantName || item.createByName || item.creatorName || '申请人',
    currentNode: item.currentNode || '',
    actionAfterApprove: item.actionAfterApprove || '',
    createdAt: item.createdAt || item.createTime || '',
    updatedAt: item.updatedAt || item.updateTime || '',
    reason: supplement || '暂无说明',
    nodes: (item.nodes || []).map(normalizeApprovalNode)
  }
}

function buildNodeView(node, index) {
  const order = node.nodeOrder || index + 1
  const approverName = node.approverName || '审批人'
  const statusText = node.statusLabel || nodeStatusText(node.status, node.action)
  const actionText = node.action ? ` ${node.action}` : ''
  const operatedAtText = node.operatedAt ? ` ${node.operatedAt}` : ''
  return {
    ...node,
    key: node.id || `${order}-${approverName}`,
    titleText: `${order}. ${approverName}`,
    statusText: `${statusText}${actionText}${operatedAtText}`,
    // 【PR-03 残余 2026-09-19】审批人已停用/删除的待办节点:卡单可见性提示(后端 approverInvalid)
    showApproverInvalid: Boolean(node.approverInvalid),
    commentText: node.comment || '暂无意见'
  }
}

function buildApprovalView(approval, state = {}) {
  const loading = Boolean(state.loading)
  if (!approval) {
    return {
      showLoading: loading,
      loadingText: '加载中...',
      hasApproval: false,
      showEmpty: !loading,
      showNodes: false,
      showCustomer: false,
      showRejectReason: false,
      showRejectAction: false,
      showApproveAction: false,
      showActions: false,
      nodes: []
    }
  }
  const customerNameText = approval.customerName || '-'
  const customerIndustryText = approval.customerIndustry || ''
  const showRejectAction = Boolean(approval.isPending && state.canReject)
  const showApproveAction = Boolean(approval.isPending && state.canApprove)
  return {
    showLoading: loading,
    loadingText: '加载中...',
    showEmpty: false,
    hasApproval: true,
    titleText: approval.title || '审批事项',
    statusText: approval.statusLabel || approvalStatusText(approval.status),
    codeText: approval.approvalCode || approval.id || '-',
    applicantText: approval.applicant || '申请人',
    currentNodeText: approval.currentNode || '-',
    actionAfterApproveText: approval.actionAfterApprove || '-',
    showCustomer: Boolean(approval.customerName || approval.customerIndustry),
    customerText: `${customerNameText} ${customerIndustryText}`.trim(),
    createdAtText: approval.createdAt || '-',
    updatedAtText: approval.updatedAt || '-',
    typeText: approval.typeLabel || approvalTypeLabel(approval.type),
    showCustomerAction: Boolean(approval.customerId),
    reasonText: approval.reason || '暂无说明',
    nodes: (approval.nodes || []).map(buildNodeView),
    showNodes: Boolean(approval.nodes && approval.nodes.length),
    showRejectReason: showRejectAction,
    showRejectAction,
    showApproveAction,
    showActions: showRejectAction || showApproveAction
  }
}

Page({
  data: {
    id: '',
    approval: null,
    approvalView: buildApprovalView(null),
    loading: false,
    canApprove: false,
    canReject: false,
    comment: ''
  },
  setApprovalData(patch) {
    const nextApproval = Object.prototype.hasOwnProperty.call(patch, 'approval') ? patch.approval : this.data.approval
    this.setData({ ...patch, approvalView: buildApprovalView(nextApproval, { ...this.data, ...patch }) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) {
      wx.showToast({ title: '没有审批查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setApprovalData({ id: options.id || '', canApprove: getApp().globalData.demoMode || hasPermission('approval:approve'), canReject: getApp().globalData.demoMode || hasPermission('approval:reject') })
    this.loadDetail()
  },
  loadDetail() {
    this.setApprovalData({ loading: true })
    if (getApp().globalData.demoMode) {
      const item = approvalRecords.find((row) => String(row.id) === String(this.data.id)) || approvalRecords[0]
      this.setApprovalData({ approval: normalize(item), loading: false })
      return Promise.resolve()
    }
    return getApprovalDetail(this.data.id).then((detail) => this.setApprovalData({ approval: normalize({ ...(detail.approval || detail), nodes: detail.nodes || [] }) })).catch((error) => showError(error, '详情加载失败')).finally(() => this.setApprovalData({ loading: false }))
  },
  onCommentInput(e) { this.setData({ comment: e.detail.value }) },
  action(type) {
    const { approval } = this.data
    if (!approval || !pendingStatus(approval.status) || (type === 'approve' ? !this.data.canApprove : !this.data.canReject)) return
    if (type === 'reject' && !this.data.comment.trim()) return wx.showToast({ title: '请填写驳回原因', icon: 'none' })
    if (getApp().globalData.demoMode) {
      const status = type === 'approve' ? 'approved' : 'rejected'
      this.setApprovalData({ approval: normalize({ ...approval, status }) })
      wx.showToast({ title: type === 'approve' ? '已通过' : '已驳回', icon: 'success' })
      return
    }
    const request = type === 'approve' ? approveApproval : rejectApproval
    request(approval.id, { comment: this.data.comment.trim() || '移动端处理' }).then(() => {
      wx.showToast({ title: type === 'approve' ? '已通过' : '已驳回', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    }).catch((error) => showError(error, '处理失败'))
  },
  approve() { this.action('approve') },
  reject() { this.action('reject') },
  openCustomer() {
    const approval = this.data.approval
    if (!approval || !approval.customerId) return wx.showToast({ title: '未关联客户', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${approval.customerId}&from=approval` })
  },
  goBack() { wx.navigateBack() }
})

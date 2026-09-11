const { approveApproval, getApprovalList, getApprovalStatistics, rejectApproval, submitApproval } = require('../../api/approval')
const { approvalRecords } = require('../../data/demo')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function normalizeApproval(item) {
  return {
    id: item.id,
    title: item.title || item.customerName || item.approvalType || item.type || '审批事项',
    type: item.type || item.approvalType || 'unknown',
    status: item.status || item.approvalStatus || 'pending',
    applicant: item.applicantName || item.createByName || item.creatorName || '申请人',
    createdAt: item.createdAt || item.createTime || '',
    reason: item.reason || item.comment || item.extraData || '暂无说明'
    ,nodes: item.nodes || []
  }
}

function isPending(item) {
  return ['pending', 'PENDING', 'WAITING', '待审批'].includes(item.status)
}

Page({
  data: {
    approvals: [],
    stats: {},
    tab: 'pending',
    loading: false,
    canApprove: false,
    canReject: false
    ,selectedApproval: null
    ,comment: ''
    ,submitCustomerId: ''
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) {
      wx.showToast({ title: '没有审批查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }

    this.setData({
      canApprove: getApp().globalData.demoMode || hasPermission('approval:approve'),
      canReject: getApp().globalData.demoMode || hasPermission('approval:reject')
    })
    this.loadApprovals()
    this.loadStats()
  },

  loadApprovals() {
    if (getApp().globalData.demoMode) {
      this.setData({ approvals: approvalRecords })
      return
    }

    this.setData({ loading: true })
    getApprovalList({ current: 1, size: 20, tab: this.data.tab }).then((page) => {
      const rows = page && (page.records || page.list || page.rows)
      this.setData({ approvals: (rows || []).map(normalizeApproval) })
    }).catch((error) => showError(error, '审批加载失败')).finally(() => {
      this.setData({ loading: false })
    })
  },

  loadStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ stats: { pending: 3, approved: 8, rejected: 1 } })
      return
    }

    getApprovalStatistics().then((stats) => {
      this.setData({ stats: stats || {} })
    }).catch((error) => {
      this.setData({ stats: {} })
      showError(error, '审批统计加载失败')
    })
  },

  changeTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab || 'all' })
    this.loadApprovals()
  },

  approve(e) {
    const item = this.data.selectedApproval || (e.currentTarget.dataset.id ? this.data.approvals.find((row) => String(row.id) === String(e.currentTarget.dataset.id)) : this.data.approvals[e.currentTarget.dataset.index])
    if (!item || !isPending(item) || !this.data.canApprove) return
    this.handleApprovalAction(item.id, 'approve')
  },

  reject(e) {
    const item = this.data.selectedApproval || (e.currentTarget.dataset.id ? this.data.approvals.find((row) => String(row.id) === String(e.currentTarget.dataset.id)) : this.data.approvals[e.currentTarget.dataset.index])
    if (!item || !isPending(item) || !this.data.canReject) return
    this.handleApprovalAction(item.id, 'reject')
  },

  openDetail(e) {
    const item = this.data.approvals[e.currentTarget.dataset.index]
    if (!item) return
    wx.navigateTo({ url: `/pages/approval/detail/index?id=${item.id}` })
  },

  closeDetail() { this.setData({ selectedApproval: null, comment: '' }) },

  onCommentInput(e) { this.setData({ comment: e.detail.value }) },

  submitReleaseApproval() {
    const customerId = this.data.submitCustomerId
    if (!getApp().globalData.demoMode && !customerId) return wx.showToast({ title: '请先输入客户ID', icon: 'none' })
    if (getApp().globalData.demoMode) {
      wx.showToast({ title: '已提交审批', icon: 'success' })
      return
    }
    submitApproval({ type: 'customer_release', customerId: customerId || '1', actionAfterApprove: 'release' }).then(() => {
      wx.showToast({ title: '已提交审批', icon: 'success' })
      this.loadApprovals(); this.loadStats()
    }).catch((error) => showError(error, '提交失败'))
  },

  onSubmitCustomerId(e) { this.setData({ submitCustomerId: e.detail.value }) },

  handleApprovalAction(id, action) {
    if (getApp().globalData.demoMode) {
      const status = action === 'approve' ? 'approved' : 'rejected'
      const approvals = this.data.approvals.map((item) => String(item.id) === String(id) ? { ...item, status } : item)
      this.setData({ approvals, selectedApproval: null, comment: '' })
      wx.showToast({ title: action === 'approve' ? '处理完成' : '已驳回', icon: 'success' })
      return
    }
    const request = action === 'approve' ? approveApproval : rejectApproval
    wx.showLoading({ title: action === 'approve' ? '审批通过中' : '审批驳回中' })
    request(id, { comment: this.data.comment.trim() || '移动端处理' }).then(() => {
      wx.showToast({ title: '处理完成', icon: 'success' })
      this.setData({ selectedApproval: null, comment: '' })
      this.loadApprovals()
      this.loadStats()
    }).catch((error) => showError(error, '处理失败')).finally(() => {
      wx.hideLoading()
    })
  },

  refresh() {
    this.loadApprovals()
    this.loadStats()
  }
})

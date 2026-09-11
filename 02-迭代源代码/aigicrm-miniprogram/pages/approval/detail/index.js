const { approveApproval, getApprovalDetail, rejectApproval } = require('../../../api/approval')
const { approvalRecords } = require('../../../data/demo')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function normalize(item) {
  return { ...item, title: item.title || item.customerName || item.approvalType || item.type || '审批事项', status: item.status || item.approvalStatus || 'pending', applicant: item.applicantName || item.createByName || item.creatorName || '申请人', reason: item.reason || item.comment || item.extraData || '暂无说明', nodes: item.nodes || [] }
}

function pending(status) { return ['pending', 'PENDING', 'WAITING', '待审批'].includes(status) }

Page({
  data: { id: '', approval: null, loading: false, canApprove: false, canReject: false, comment: '' },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) {
      wx.showToast({ title: '没有审批查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ id: options.id || '', canApprove: getApp().globalData.demoMode || hasPermission('approval:approve'), canReject: getApp().globalData.demoMode || hasPermission('approval:reject') })
    this.loadDetail()
  },
  loadDetail() {
    this.setData({ loading: true })
    if (getApp().globalData.demoMode) {
      const item = approvalRecords.find((row) => String(row.id) === String(this.data.id)) || approvalRecords[0]
      this.setData({ approval: normalize(item), loading: false })
      return Promise.resolve()
    }
    return getApprovalDetail(this.data.id).then((detail) => this.setData({ approval: normalize({ ...(detail.approval || detail), nodes: detail.nodes || [] }) })).catch((error) => showError(error, '详情加载失败')).finally(() => this.setData({ loading: false }))
  },
  onCommentInput(e) { this.setData({ comment: e.detail.value }) },
  action(type) {
    const { approval } = this.data
    if (!approval || !pending(approval.status) || (type === 'approve' ? !this.data.canApprove : !this.data.canReject)) return
    if (type === 'reject' && !this.data.comment.trim()) return wx.showToast({ title: '请填写驳回原因', icon: 'none' })
    if (getApp().globalData.demoMode) {
      this.setData({ 'approval.status': type === 'approve' ? 'approved' : 'rejected' })
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
  goBack() { wx.navigateBack() }
})

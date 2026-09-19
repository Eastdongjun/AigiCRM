const { requireLogin, hasPermission } = require('../../../utils/auth')

function buildClaimView(data) {
  return {
    titleText: data.title || '捞取申请',
    statusText: data.statusLabel || claimStatusText(data.status),
    createdAtText: data.createdAt || '-',
    applicantNameText: data.applicantName || '-',
    approverNameText: data.approverName || '-',
    approveTimeText: data.approveTime || '-',
    reasonText: data.reason || '暂无审批说明'
  }
}

Page({
  data: {
    title: '捞取申请',
    status: '',
    statusLabel: '',
    createdAt: '',
    applicantName: '',
    approverName: '',
    approveTime: '',
    reason: '',
    claimView: buildClaimView({})
  },
  setClaimData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, claimView: buildClaimView(nextData) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('sea:view')) {
      wx.showToast({ title: '没有公海查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const status = decodeURIComponent(options.status || '')
    this.setClaimData({
      title: decodeURIComponent(options.title || '捞取申请'),
      status,
      statusLabel: decodeURIComponent(options.statusLabel || '') || claimStatusText(status),
      createdAt: decodeURIComponent(options.createdAt || ''),
      applicantName: decodeURIComponent(options.applicantName || ''),
      approverName: decodeURIComponent(options.approverName || ''),
      approveTime: decodeURIComponent(options.approveTime || ''),
      reason: decodeURIComponent(options.reason || '')
    })
  },
  goBack() { wx.navigateBack() }
})

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

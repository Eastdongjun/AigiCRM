const { requireLogin, hasPermission } = require('../../../utils/auth')
// 【E6 去重】捞取申请状态映射原与 sea/index 复制一份, 收敛到 utils/label-maps
const { claimStatusText } = require('../../../utils/label-maps')

function buildClaimView(data) {
  return {
    titleText: data.title || '捞取申请',
    statusText: data.statusLabel || claimStatusText(data.status),
    createdAtText: data.createdAt || '-',
    applicantNameText: data.applicantName || '-',
    approverNameText: data.approverName || '-',
    approveTimeText: data.approveTime || '-',
    reasonText: data.reason || '',
    // 【S5】/sea/claim-records 不返回 reason（后端行字段仅 id/日期/客户/申请人/审批人），
    // 无来源时整块隐藏，避免恒显示"暂无审批说明"造成"已填写为空"的误读
    showReason: Boolean(data.reason)
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

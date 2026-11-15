const { requireLogin } = require('../../../utils/auth')
const { dataScopeLabel } = require('../../../utils/label-maps')

Page({
  data: { scopeName: '按权限范围', moduleScopeText: '' },
  onPermissionChanged() {
    this.onLoad()
  },

  onLoad() {
    if (!requireLogin()) return
    const user = getApp().globalData.currentUser || {}
    const labels = { customer: '客户', incentive: '激励', file: '文件', sea: '公海', approval: '审批', reminder: '提醒', statistics: '统计' }
    const scopes = user.moduleScopes || {}
    const moduleScopeText = Object.keys(labels).filter((k) => scopes[k]).map((k) => `${labels[k]} ${dataScopeLabel(scopes[k])}`).join(' · ')
    this.setData({ scopeName: dataScopeLabel(user.dataScope) + '（基础范围）', moduleScopeText })
  }
})

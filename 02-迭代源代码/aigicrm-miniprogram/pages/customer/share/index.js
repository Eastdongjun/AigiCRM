const { getCustomerShares, getCustomerSimpleUsers, addCustomerShares, removeCustomerShare } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { id: '', shares: [], users: [], selectedUserIds: [], loading: false, saving: false, canEdit: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') })
    this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) return this.setData({ shares: [{ id: 1, sharedToUserName: '李经理', createdAt: '2026-09-01 10:20' }], users: [{ id: 2, realName: '王工' }, { id: 3, realName: '赵经理' }] })
    this.setData({ loading: true })
    Promise.all([getCustomerShares(this.data.id), this.data.canEdit ? getCustomerSimpleUsers() : Promise.resolve([])]).then(([shares, users]) => this.setData({ shares: shares || [], users: users || [] })).catch((error) => showError(error, '分享记录加载失败')).finally(() => this.setData({ loading: false }))
  },
  selectUsers(e) { this.setData({ selectedUserIds: (e.detail.value || []).map((id) => Number(id)) }) },
  addShares() {
    if (!this.data.selectedUserIds.length || this.data.saving) return wx.showToast({ title: '请选择分享对象', icon: 'none' })
    this.setData({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve() : addCustomerShares(this.data.id, this.data.selectedUserIds)
    action.then(() => { wx.showToast({ title: '已分享', icon: 'success' }); this.setData({ selectedUserIds: [] }); this.load() }).catch((error) => showError(error, '分享失败')).finally(() => this.setData({ saving: false }))
  },
  removeShare(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    const item = this.data.shares[e.currentTarget.dataset.index]
    const userId = item && (item.sharedToUserId || item.userId)
    if (!userId) return wx.showToast({ title: '缺少共享对象信息', icon: 'none' })
    wx.showModal({ title: '撤回分享', content: `确认撤回对 ${item.sharedToUserName || '该用户'} 的客户分享？`, success: (result) => {
      if (!result.confirm || this.data.saving) return
      this.setData({ saving: true })
      const action = getApp().globalData.demoMode ? Promise.resolve() : removeCustomerShare(this.data.id, userId)
      action.then(() => { wx.showToast({ title: '已撤回', icon: 'success' }); this.load() }).catch((error) => showError(error, '撤回失败')).finally(() => this.setData({ saving: false }))
    } })
  },
  goBack() { wx.navigateBack() }
})

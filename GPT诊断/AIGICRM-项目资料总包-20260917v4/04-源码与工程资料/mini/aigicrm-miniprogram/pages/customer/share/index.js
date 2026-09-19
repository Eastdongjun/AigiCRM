const { getCustomerShares, getCustomerSimpleUsers, addCustomerShares, removeCustomerShare } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function buildShareItem(item, index) {
  return {
    ...item,
    viewId: item.id || item.sharedToUserId || item.userId || `share-${index}`,
    userNameText: item.sharedToUserName || item.userName || '已授权用户',
    createdAtText: item.createdAt || item.createTime || '-'
  }
}

function buildUserOption(user, index) {
  return {
    ...user,
    viewId: user.id || `user-${index}`,
    nameText: user.realName || user.username || '用户'
  }
}

function buildShareView(state = {}) {
  const shares = (state.shares || []).map(buildShareItem)
  const users = (state.users || []).map(buildUserOption)
  return {
    showLoading: Boolean(state.loading),
    submitLoading: Boolean(state.saving),
    submitText: state.saving ? '分享中...' : '确认分享',
    cancelText: '取消分享',
    cancelDisabled: Boolean(state.saving),
    shares,
    users,
    showEmpty: !state.loading && shares.length === 0,
    showForm: Boolean(state.canEdit && users.length)
  }
}

Page({
  data: {
    id: '',
    shares: [],
    users: [],
    selectedUserIds: [],
    loading: false,
    saving: false,
    canEdit: false,
    shareView: buildShareView({})
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, shareView: buildShareView(next) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.applyState({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') })
    this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) return this.applyState({ shares: [{ id: 1, sharedToUserName: '李经理', createdAt: '2026-09-01 10:20' }], users: this.markSelectedUsers([{ id: 2, realName: '王工' }, { id: 3, realName: '赵经理' }]) })
    this.applyState({ loading: true })
    Promise.all([getCustomerShares(this.data.id), this.data.canEdit ? getCustomerSimpleUsers() : Promise.resolve([])]).then(([shares, users]) => this.applyState({ shares: shares || [], users: this.markSelectedUsers(users || []) })).catch((error) => showError(error, '分享记录加载失败')).finally(() => this.applyState({ loading: false }))
  },
  markSelectedUsers(users) {
    const selected = new Set(this.data.selectedUserIds.map((id) => String(id)))
    return (users || []).map((user) => ({ ...user, checked: selected.has(String(user.id)) }))
  },
  selectUsers(e) {
    const selectedUserIds = (e.detail.value || []).map((id) => Number(id))
    this.applyState({ selectedUserIds, users: (this.data.users || []).map((user) => ({ ...user, checked: selectedUserIds.includes(Number(user.id)) })) })
  },
  addShares() {
    if (!this.data.selectedUserIds.length || this.data.saving) return wx.showToast({ title: '请选择分享对象', icon: 'none' })
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve() : addCustomerShares(this.data.id, this.data.selectedUserIds)
    action.then(() => { wx.showToast({ title: '已分享', icon: 'success' }); this.applyState({ selectedUserIds: [], users: this.markSelectedUsers(this.data.users) }); this.load() }).catch((error) => showError(error, '分享失败')).finally(() => this.applyState({ saving: false }))
  },
  removeShare(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    const item = this.data.shares[e.currentTarget.dataset.index]
    const userId = item && (item.sharedToUserId || item.userId)
    if (!userId) return wx.showToast({ title: '缺少共享对象信息', icon: 'none' })
    wx.showModal({ title: '撤回分享', content: `确认撤回对 ${buildShareItem(item).userNameText} 的客户分享？`, success: (result) => {
      if (!result.confirm || this.data.saving) return
      this.applyState({ saving: true })
      const action = getApp().globalData.demoMode ? Promise.resolve() : removeCustomerShare(this.data.id, userId)
      action.then(() => { wx.showToast({ title: '已撤回', icon: 'success' }); this.load() }).catch((error) => showError(error, '撤回失败')).finally(() => this.applyState({ saving: false }))
    } })
  },
  goBack() {
    if (this.data.saving) return
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    if (pages.length > 1) {
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${this.data.id}` })
  }
})

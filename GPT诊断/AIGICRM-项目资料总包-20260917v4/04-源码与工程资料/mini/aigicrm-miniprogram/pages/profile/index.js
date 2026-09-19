const { loadCurrentUser, switchGroup: switchSessionGroup } = require('../../api/auth')
const { logout, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function buildProfileView(data) {
  const groupIds = Array.isArray(data.groupIds) ? data.groupIds : []
  return {
    userInitialText: data.userInitial || '用',
    userNameText: data.userName || '当前用户',
    heroSubText: `${data.orgName || '未设置组织'} · ${data.roleName || '未设置岗位'}`,
    positionText: data.positionNames || '未设置岗位',
    showGroupCard: groupIds.length > 0,
    currentGroupText: data.currentGroupId || '选择集团',
    groupNoteText: data.switchingGroup ? '切换中...' : '切换后刷新权限',
    groupDisabled: Boolean(data.switchingGroup),
    scopeText: data.scopeName || '按权限范围',
    wechatStatusText: '说明'
  }
}

Page({
  data: {
    userName: '演示用户',
    userInitial: '演',
    roleName: '业务员',
    orgName: '晋蒙业务部',
    scopeName: '本部门及以下',
    positionNames: '业务员',
    groupIds: [],
    currentGroupId: null,
    groupIndex: 0,
    switchingGroup: false,
    profileView: buildProfileView({
      userName: '演示用户',
      userInitial: '演',
      roleName: '业务员',
      orgName: '晋蒙业务部',
      scopeName: '本部门及以下',
      positionNames: '业务员',
      groupIds: [],
      currentGroupId: null,
      switchingGroup: false
    })
  },

  onLoad() {
    if (!requireLogin()) return
    if (getApp().globalData.demoMode) return
    loadCurrentUser().then((user) => {
      const userName = user.realName || user.username || '当前用户'
      this.applyState({
        userName,
        userInitial: userName ? userName.slice(0, 1) : '用',
        roleName: user.roleName || '未设置岗位',
        orgName: user.deptName || '未设置组织',
        scopeName: user.dataScopeName || '按权限范围',
        positionNames: (user.positions || []).map((position) => position.name).filter(Boolean).join('、') || '未设置岗位',
        groupIds: user.groupIds || [],
        currentGroupId: user.currentGroupId || null,
        groupIndex: Math.max(0, (user.groupIds || []).indexOf(user.currentGroupId))
      })
    }).catch((error) => showError(error, '用户信息加载失败'))
  },

  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, profileView: buildProfileView(next) }, callback)
  },

  changeGroup(event) {
    const groupId = this.data.groupIds[Number(event.detail.value)]
    if (!groupId || groupId === this.data.currentGroupId || this.data.switchingGroup) return
    this.applyState({ switchingGroup: true })
    switchSessionGroup(groupId).then(() => loadCurrentUser()).then((user) => {
      const groupIds = user.groupIds || []
      this.applyState({ groupIds, currentGroupId: user.currentGroupId || null, groupIndex: Math.max(0, groupIds.indexOf(user.currentGroupId)) })
      wx.showToast({ title: '集团已切换', icon: 'success' })
    }).catch((error) => {
      this.applyState({ groupIndex: Math.max(0, this.data.groupIds.indexOf(this.data.currentGroupId)) })
      showError(error, '集团切换失败')
    }).finally(() => this.applyState({ switchingGroup: false }))
  },

  openCustomers() { wx.navigateTo({ url: '/pages/customer/index' }) },
  openIncentive() { wx.navigateTo({ url: '/pages/incentive/index' }) },
  openClaims() { wx.navigateTo({ url: '/pages/sea/index' }) },
  openPassword() { wx.navigateTo({ url: '/pages/profile/password/index' }) },
  openScope() { wx.navigateTo({ url: '/pages/profile/scope/index' }) },
  openAbout() { wx.navigateTo({ url: '/pages/profile/about/index' }) },
  openAgreement() { wx.navigateTo({ url: '/pages/profile/agreement/index' }) },
  openPrivacy() { wx.navigateTo({ url: '/pages/profile/privacy/index' }) },
  openWechat() { wx.navigateTo({ url: '/pages/profile/wechat/index' }) },
  openFeedback() { wx.navigateTo({ url: '/pages/profile/feedback/index' }) },
  clearCache() {
    wx.showModal({ title: '清除缓存', content: '将清除本地登录会话和页面缓存，需要重新登录。', success: (result) => {
      if (!result.confirm) return
      wx.clearStorageSync()
      wx.showToast({ title: '缓存已清除', icon: 'success' })
      setTimeout(() => wx.redirectTo({ url: '/pages/login/index' }), 700)
    } })
  },
  logout
})

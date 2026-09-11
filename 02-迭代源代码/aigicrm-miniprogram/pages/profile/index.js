const { loadCurrentUser, switchGroup: switchSessionGroup } = require('../../api/auth')
const { logout, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

Page({
  data: { userName: '演示用户', roleName: '业务员', orgName: '晋蒙业务部', scopeName: '本部门及以下', positionNames: '业务员', groupIds: [], currentGroupId: null, groupIndex: 0, switchingGroup: false },

  onLoad() {
    if (!requireLogin()) return
    if (getApp().globalData.demoMode) return
    loadCurrentUser().then((user) => {
      this.setData({
        userName: user.realName || user.username || '当前用户',
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

  changeGroup(event) {
    const groupId = this.data.groupIds[Number(event.detail.value)]
    if (!groupId || groupId === this.data.currentGroupId || this.data.switchingGroup) return
    this.setData({ switchingGroup: true })
    switchSessionGroup(groupId).then(() => loadCurrentUser()).then((user) => {
      const groupIds = user.groupIds || []
      this.setData({ groupIds, currentGroupId: user.currentGroupId || null, groupIndex: Math.max(0, groupIds.indexOf(user.currentGroupId)) })
      wx.showToast({ title: '集团已切换', icon: 'success' })
    }).catch((error) => {
      this.setData({ groupIndex: Math.max(0, this.data.groupIds.indexOf(this.data.currentGroupId)) })
      showError(error, '集团切换失败')
    }).finally(() => this.setData({ switchingGroup: false }))
  },

  openCustomers() { wx.navigateTo({ url: '/pages/customer/index' }) },
  openCases() { wx.navigateTo({ url: '/pages/incentive/index' }) },
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

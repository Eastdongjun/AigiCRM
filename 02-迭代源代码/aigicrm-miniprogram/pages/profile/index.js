const { loadCurrentUser, switchGroup: switchSessionGroup } = require('../../api/auth')
const { logout, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function buildProfileView(data) {
  const groupIds = Array.isArray(data.groupIds) ? data.groupIds : []
  const groupNames = data.groupNames || {}
  const groupNamesText = groupIds.map((id) => groupNames[id] || ('集团 ' + id))
  // 【2026-10-08】当前集团显示名字而非裸 ID(此前渲染 currentGroupId="1"); 名称缺失回退"集团 <id>"
  const currentGroupText = (data.currentGroupId != null && (groupNames[data.currentGroupId] || '集团 ' + data.currentGroupId)) || '选择集团'
  return {
    userInitialText: data.userInitial || '用',
    userNameText: data.userName || '当前用户',
    // 【用户裁决C 2026-10-13】岗位并进 hero 副标题(原「组织·角色」与岗位信息重复):
    // 岗位缺失时回退角色名, 再回退组织
    heroSubText: `${data.positionNames || data.roleName || data.orgName || '未设置岗位'}`,
    positionText: data.positionNames || '未设置岗位',
    showGroupCard: groupIds.length > 0,
    currentGroupText,
    groupNamesText,
    groupNoteText: data.switchingGroup ? '切换中...' : '切换后刷新权限',
    groupDisabled: Boolean(data.switchingGroup),
    scopeText: data.scopeName || '按权限范围',
    wechatStatusText: '说明'
  }
}

// 【E9 去重】演示档初始态单一出处: 此前 data 散字段与 profileView 构造参数整段
// 复制两份, 改任意一份都会造成初始展示不一致, 故收敛为同一常量。onLoad 拉到真实
// 用户后经 applyState 覆盖, 初始演示值仅 demo 档可见, 行为不变。
const DEMO_PROFILE_STATE = {
  userName: '演示用户',
  userInitial: '演',
  roleName: '业务员',
  orgName: '晋蒙业务部',
  scopeName: '本部门及以下',
  positionNames: '业务员',
  groupIds: [],
  currentGroupId: null,
  switchingGroup: false
}

Page({
  data: {
    ...DEMO_PROFILE_STATE,
    groupIndex: 0,
    profileView: buildProfileView(DEMO_PROFILE_STATE)
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
        groupNames: user.groupNames || {},
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
      this.applyState({ groupIds, groupNames: user.groupNames || {}, currentGroupId: user.currentGroupId || null, groupIndex: Math.max(0, groupIds.indexOf(user.currentGroupId)) })
      wx.showToast({ title: '集团已切换', icon: 'success' })
    }).catch((error) => {
      this.applyState({ groupIndex: Math.max(0, this.data.groupIds.indexOf(this.data.currentGroupId)) })
      showError(error, '集团切换失败')
    }).finally(() => this.applyState({ switchingGroup: false }))
  },

  openCustomers() { wx.navigateTo({ url: '/pages/customer/index' }) },
  openIncentive() { wx.switchTab({ url: '/pages/incentive/home/index' }) }, // tabBar 页必须 switchTab(navigateTo 被拒=死按钮)【2026-10-23 拆分: tabBar=轻量客户记录】
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
      // 【质量评审 L10】对齐 utils/auth.js logout 的完整会话清理:
      // 只清 Storage 不清 globalData 会残留 currentUser/permissions/groupIds/currentGroupId,
      // 与退出登录/修改密码后的行为不一致(内存态权限继续生效)
      const globalData = getApp().globalData
      globalData.currentUser = null
      globalData.permissions = []
      globalData.groupIds = []
      globalData.currentGroupId = null
      wx.showToast({ title: '缓存已清除', icon: 'success' })
      setTimeout(() => wx.redirectTo({ url: '/pages/login/index' }), 700)
    } })
  },
  logout
})

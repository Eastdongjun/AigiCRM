const { login, loadCurrentUser } = require('../../api/auth')
const { setToken } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function buildLoginView(state = {}) {
  const demoMode = Boolean(state.demoMode)
  return {
    copyText: demoMode ? '当前为演示模式，无需账号密码，可直接进入移动端原型。' : '使用 PC 端同一账号登录，移动端会同步客户、提醒与审批数据。',
    submitLoading: Boolean(state.loading),
    submitText: demoMode ? '进入原型' : '登录',
    showAccountFields: !demoMode,
    showForgotAction: !demoMode
  }
}

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    demoMode: false,
    loginView: buildLoginView({})
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, loginView: buildLoginView(next) })
  },

  onLoad() {
    this.applyState({ demoMode: getApp().globalData.demoMode })
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  submit() {
    if (getApp().globalData.demoMode) {
      getApp().globalData.currentUser = { username: 'demo', realName: '演示用户', roles: ['admin'], admin: true }
      getApp().globalData.permissions = ['*', 'customer:view', 'customer:add', 'customer:edit', 'file:view', 'incentive:view', 'incentive:read-stats', 'approval:view', 'approval:approve', 'approval:reject', 'sea:view']
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    if (!this.data.username || !this.data.password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.applyState({ loading: true })
    login({
      username: this.data.username,
      password: this.data.password,
      rememberMe: true
    }).then((res) => {
      setToken(res.token)
      return loadCurrentUser()
    }).then(() => {
      wx.switchTab({ url: '/pages/home/index' })
    }).catch((err) => {
      showError(err, '登录失败')
    }).finally(() => {
      this.applyState({ loading: false })
    })
  }
  ,openForgot() { wx.navigateTo({ url: '/pages/login/forgot/index' }) }
})

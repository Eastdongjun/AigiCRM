const { login, loadCurrentUser } = require('../../api/auth')
const { setToken, getToken } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

// 【审核合规 2026-10-09】当前协议/隐私政策版本号(内容实质变更时更新,触发重新征得同意)
const AGREEMENT_VERSION = 'v20261009'

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
    // 【审核合规 2026-10-09】收集用户信息(含手机号)前的授权同意状态,默认不勾选
    agreed: false,
    loginView: buildLoginView({})
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, loginView: buildLoginView(next) })
  },

  onLoad() {
    this.applyState({ demoMode: getApp().globalData.demoMode })
    this.resumeSessionIfPossible()
  },

  // 【真机UAT修复 2026-09-22】冷启入口页=登录页,已有有效令牌时自动回填会话并进入首页。
  // 此前仅靠 app.onShow 异步刷 auth/info,实测冷启竞态下 permissions 为空:
  // 用户带着 rememberMe 令牌仍被要求重新登录,且权限守卫会把误入的业务页弹回首页。
  // 失败(令牌失效/网络异常)则留在登录页,由用户手动登录;401 由 request.js 统一清令牌。
  resumeSessionIfPossible() {
    const app = getApp()
    if (app.globalData.demoMode || app.globalData.currentUser || !getToken()) return
    loadCurrentUser().then(() => {
      wx.switchTab({ url: '/pages/home/index' })
    }).catch(() => {})
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  submit() {
    // 【审核合规 2026-10-09】未取得协议/隐私授权同意前不允许登录(即不允许开始收集使用用户信息)
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意《用户服务协议》和《隐私政策》', icon: 'none', duration: 2500 })
      return
    }
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
      // 【体检 P1 2026-10-12】首登/被重置强制改密: 后端 JwtAuthFilter 对 pwdResetRequired=1 的非白名单
      // 路径一律 403, MP 此前忽略标记直进首页→业务全面 403 无出路; PC 已有完整闭环, 此处对齐——
      // 引导到修改密码页(该端点在服务端白名单), 改密成功后回登录页重新登录
      if (res && res.pwdResetRequired === 1) {
        wx.showToast({ title: '首次登录须先修改密码', icon: 'none', duration: 2500 })
        setTimeout(() => wx.redirectTo({ url: '/pages/profile/password/index' }), 600)
        return null
      }
      return loadCurrentUser()
    }).then((user) => {
      if (!user) return
      // 同意留痕: 记录用户已同意的协议版本,便于后续版本升级时重新征得同意
      try { wx.setStorageSync('user_agreement_version', AGREEMENT_VERSION) } catch (e) { /* 存储失败不阻断登录 */ }
      wx.switchTab({ url: '/pages/home/index' })
    }).catch((err) => {
      showError(err, '登录失败')
    }).finally(() => {
      this.applyState({ loading: false })
    })
  },
  // 【审核合规 2026-10-09】协议/隐私授权同意交互
  toggleAgreed() {
    this.setData({ agreed: !this.data.agreed })
  },
  openAgreement() { wx.navigateTo({ url: '/pages/profile/agreement/index' }) },
  openPrivacyDoc() { wx.navigateTo({ url: '/pages/profile/privacy/index' }) },
  openForgot() { wx.navigateTo({ url: '/pages/login/forgot/index' }) },
  // ICP 备案号点击:小程序不允许直接跳转外部网页,复制备案系统网址供浏览器打开
  copyBeianUrl() {
    wx.setClipboardData({
      data: 'https://beian.miit.gov.cn/',
      success: () => wx.showToast({ title: '备案网址已复制', icon: 'success' })
    })
  }
})

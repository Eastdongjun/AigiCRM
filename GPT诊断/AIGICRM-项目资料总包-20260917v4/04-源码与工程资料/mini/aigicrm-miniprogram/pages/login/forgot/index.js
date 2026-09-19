const { forgotPasswordLookup, forgotPasswordSendCode, forgotPasswordReset } = require('../../../api/auth')
const { showError } = require('../../../utils/feedback')

function buildStepView(step, current) {
  return {
    text: `${step} ${current === 1 ? '验证账号' : current === 2 ? '验证身份' : '重置密码'}`,
    className: step <= current ? 'step active' : 'step'
  }
}

function buildForgotView(state = {}) {
  const countdown = Number(state.countdown) || 0
  return {
    steps: [buildStepView(1, state.step), buildStepView(2, state.step), buildStepView(3, state.step)],
    showAccountStep: state.step === 1,
    showCodeStep: state.step === 2,
    showResetStep: state.step === 3,
    codeText: countdown > 0 ? `${countdown}s` : '获取验证码',
    submitLoading: Boolean(state.loading),
    submitText: state.step === 3 ? '确认重置' : '下一步',
    backText: state.step === 1 ? '返回登录' : '上一步',
    maskedPhoneText: state.maskedPhone || '已绑定手机'
  }
}

Page({
  data: { step: 1, username: '', method: 'phone', maskedPhone: '', code: '', newPassword: '', confirmPassword: '', loading: false, countdown: 0, forgotView: buildForgotView({ step: 1 }) },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, forgotView: buildForgotView(next) })
  },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  next() {
    if (this.data.step === 1) return this.lookup()
    if (this.data.step === 2) return this.verifyCode()
    this.reset()
  },
  lookup() {
    if (!this.data.username.trim() || this.data.loading) return wx.showToast({ title: '请输入账号', icon: 'none' })
    this.applyState({ loading: true })
    forgotPasswordLookup({ username: this.data.username.trim() }).then((result) => {
      const lookup = result || {}
      this.applyState({ maskedPhone: lookup.maskedPhone || lookup.phone || '', step: 2 })
    }).catch((error) => showError(error, '账号查询失败')).finally(() => this.applyState({ loading: false }))
  },
  sendCode() {
    if (this.data.countdown || this.data.loading) return
    this.applyState({ loading: true })
    forgotPasswordSendCode({ username: this.data.username.trim(), method: this.data.method }).then(() => {
      wx.showToast({ title: '验证码已发送', icon: 'success' })
      this.applyState({ countdown: 60 })
      this.tick()
    }).catch((error) => showError(error, '验证码发送失败')).finally(() => this.applyState({ loading: false }))
  },
  tick() { if (this.data.countdown <= 0) return; setTimeout(() => { this.applyState({ countdown: this.data.countdown - 1 }); this.tick() }, 1000) },
  verifyCode() { if (!this.data.code.trim()) return wx.showToast({ title: '请输入验证码', icon: 'none' }); this.applyState({ step: 3 }) },
  reset() {
    if (this.data.newPassword.length < 6 || this.data.newPassword !== this.data.confirmPassword || this.data.loading) return wx.showToast({ title: '请检查新密码', icon: 'none' })
    this.applyState({ loading: true })
    forgotPasswordReset({ username: this.data.username.trim(), method: this.data.method, code: this.data.code.trim(), newPassword: this.data.newPassword }).then(() => { wx.showToast({ title: '密码已重置', icon: 'success' }); setTimeout(() => wx.navigateBack(), 700) }).catch((error) => showError(error, '密码重置失败')).finally(() => this.applyState({ loading: false }))
  },
  back() { if (this.data.step > 1) this.applyState({ step: this.data.step - 1 }); else wx.navigateBack() }
})

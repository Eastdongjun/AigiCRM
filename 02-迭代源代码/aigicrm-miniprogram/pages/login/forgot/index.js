const { forgotPasswordLookup, forgotPasswordSendCode, forgotPasswordReset } = require('../../../api/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { step: 1, username: '', method: 'phone', maskedPhone: '', code: '', newPassword: '', confirmPassword: '', loading: false, countdown: 0 },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  next() {
    if (this.data.step === 1) return this.lookup()
    if (this.data.step === 2) return this.verifyCode()
    this.reset()
  },
  lookup() {
    if (!this.data.username.trim() || this.data.loading) return wx.showToast({ title: '请输入账号', icon: 'none' })
    this.setData({ loading: true })
    forgotPasswordLookup({ username: this.data.username.trim() }).then((result) => {
      const lookup = result || {}
      this.setData({ maskedPhone: lookup.maskedPhone || lookup.phone || '', step: 2 })
    }).catch((error) => showError(error, '账号查询失败')).finally(() => this.setData({ loading: false }))
  },
  sendCode() {
    if (this.data.countdown || this.data.loading) return
    this.setData({ loading: true })
    forgotPasswordSendCode({ username: this.data.username.trim(), method: this.data.method }).then(() => {
      wx.showToast({ title: '验证码已发送', icon: 'success' })
      this.setData({ countdown: 60 })
      this.tick()
    }).catch((error) => showError(error, '验证码发送失败')).finally(() => this.setData({ loading: false }))
  },
  tick() { if (this.data.countdown <= 0) return; setTimeout(() => { this.setData({ countdown: this.data.countdown - 1 }); this.tick() }, 1000) },
  verifyCode() { if (!this.data.code.trim()) return wx.showToast({ title: '请输入验证码', icon: 'none' }); this.setData({ step: 3 }) },
  reset() {
    if (this.data.newPassword.length < 6 || this.data.newPassword !== this.data.confirmPassword || this.data.loading) return wx.showToast({ title: '请检查新密码', icon: 'none' })
    this.setData({ loading: true })
    forgotPasswordReset({ username: this.data.username.trim(), method: this.data.method, code: this.data.code.trim(), newPassword: this.data.newPassword }).then(() => { wx.showToast({ title: '密码已重置', icon: 'success' }); setTimeout(() => wx.navigateBack(), 700) }).catch((error) => showError(error, '密码重置失败')).finally(() => this.setData({ loading: false }))
  },
  back() { if (this.data.step > 1) this.setData({ step: this.data.step - 1 }); else wx.navigateBack() }
})

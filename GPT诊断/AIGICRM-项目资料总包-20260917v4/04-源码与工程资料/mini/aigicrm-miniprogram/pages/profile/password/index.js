const request = require('../../../utils/request')
const { clearToken } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function buildPasswordView(data) {
  return {
    fields: [
      { field: 'oldPassword', placeholder: '原密码' },
      { field: 'newPassword', placeholder: '新密码（至少 6 位）' },
      { field: 'confirmPassword', placeholder: '确认新密码' }
    ],
    submitText: data.loading ? '修改中...' : '确认修改',
    submitLoading: Boolean(data.loading)
  }
}

Page({
  data: {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    loading: false,
    passwordView: buildPasswordView({ loading: false })
  },

  onInput(event) {
    this.applyState({ [event.currentTarget.dataset.field]: event.detail.value })
  },

  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, passwordView: buildPasswordView(next) }, callback)
  },

  submit() {
    const { oldPassword, newPassword, confirmPassword } = this.data
    if (!oldPassword || !newPassword || !confirmPassword) {
      wx.showToast({ title: '请完整填写密码', icon: 'none' })
      return
    }
    if (newPassword.length < 6) {
      wx.showToast({ title: '新密码至少 6 位', icon: 'none' })
      return
    }
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: '两次新密码不一致', icon: 'none' })
      return
    }
    this.applyState({ loading: true })
    request({ url: '/api/system/users/change-password', method: 'POST', data: { oldPassword, newPassword } })
      .then(() => {
        clearToken()
        getApp().globalData.currentUser = null
        getApp().globalData.permissions = []
        getApp().globalData.groupIds = []
        getApp().globalData.currentGroupId = null
        wx.showToast({ title: '修改成功，请重新登录', icon: 'success' })
        setTimeout(() => wx.redirectTo({ url: '/pages/login/index' }), 900)
      })
      .catch((error) => showError(error, '修改失败'))
      .finally(() => this.applyState({ loading: false }))
  }
})

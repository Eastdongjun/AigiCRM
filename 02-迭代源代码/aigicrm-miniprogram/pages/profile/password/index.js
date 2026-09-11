const request = require('../../../utils/request')
const { clearToken } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { oldPassword: '', newPassword: '', confirmPassword: '', loading: false },

  onInput(event) {
    this.setData({ [event.currentTarget.dataset.field]: event.detail.value })
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
    this.setData({ loading: true })
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
      .finally(() => this.setData({ loading: false }))
  }
})

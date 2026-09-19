const { requireLogin } = require('../../../utils/auth')

Page({
  data: { scopeName: '按权限范围' },
  onLoad() {
    if (!requireLogin()) return
    const user = getApp().globalData.currentUser || {}
    this.setData({ scopeName: user.dataScopeName || '按权限范围' })
  }
})

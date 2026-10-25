const { requireLogin } = require('../../../utils/auth')
const { dataScopeLabel } = require('../../../utils/label-maps')

Page({
  data: { scopeName: '按权限范围' },
  onLoad() {
    if (!requireLogin()) return
    const user = getApp().globalData.currentUser || {}
    this.setData({ scopeName: dataScopeLabel(user.dataScope) })
  }
})

const { requireLogin } = require('../../../utils/auth')

Page({
  data: { status: '待接入' },
  onLoad() { requireLogin() },
  bindWechat() {
    wx.showModal({ title: '微信绑定暂未接入', content: '当前版本已保留页面和状态展示，待后端微信 OAuth、openid/unionid 绑定接口接入后开放。', showCancel: false })
  }
})

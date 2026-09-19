const { requireLogin } = require('../../../utils/auth')

Page({
  data: { status: '待后端接入' },
  onLoad() { requireLogin() },
  bindWechat() {
    wx.showModal({ title: '微信绑定暂未接入', content: '当前小程序仅展示绑定状态与接入说明，不会请求微信身份信息。待后端微信 OAuth、openid/unionid 绑定接口接入后再开放绑定。', showCancel: false })
  }
})

// 【手机文件上传 2026-11-02 用户令"调用上传手机文件的接口"】web-view 容器页:
// 微信小程序无直接访问手机文件系统的 API(官方仅有 chooseMessageFile 从聊天选/chooseMedia 相册),
// 本页内嵌 H5(/mp-file-picker)用 <input type="file"> 调起手机文件选择器(iOS「文件」App/安卓文件管理器),
// H5 内直传后端后自动 navigateBack; 鉴权=URL 携带小程序登录 token。
const { getToken, requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: {
    src: ''
  },

  onLoad(options) {
    if (!requireLogin()) return
    // 与文件页上传入口同闸: file:upload 码兜底(入口已查, 防深链直入)
    const globalData = getApp().globalData
    if (globalData.demoMode) {
      wx.showToast({ title: '演示模式暂不上传文件', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    if (!hasPermission('file:upload')) {
      wx.showToast({ title: '没有文件上传权限', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    const token = getToken()
    if (!token) {
      wx.showToast({ title: '登录状态缺失', icon: 'none' })
      return setTimeout(() => wx.navigateBack(), 600)
    }
    const q = ['token=' + encodeURIComponent(token)]
    if (options && options.folderId) q.push('folderId=' + encodeURIComponent(options.folderId))
    if (options && options.scope) q.push('scope=' + encodeURIComponent(options.scope))
    this.setData({ src: globalData.apiBaseUrl + '/mp-file-picker?' + q.join('&') })
  },

  // 【PR-12 写后回流】H5 上传完成后本页卸载即强制刷新文件页列表(绕 30s 节流, 机制现成)
  onUnload() {
    getApp().globalData.forceRefresh.files = true
  }
})

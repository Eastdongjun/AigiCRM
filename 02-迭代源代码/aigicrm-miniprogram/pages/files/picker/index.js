// 【手机文件上传 2026-11-02 用户令"调用上传手机文件的接口"】web-view 容器页:
// 微信小程序无直接访问手机文件系统的 API(官方仅有 chooseMessageFile 从聊天选/chooseMedia 相册),
// 本页内嵌 H5(/mp-file-picker)用 <input type="file"> 调起手机文件选择器(iOS「文件」App/安卓文件管理器),
// H5 内直传后端后自动 navigateBack。
// 【F3b 2026-11-19 换票】鉴权从"URL 携带小程序登录 token"(MP-TOKEN-001 弱点, 全仓最后一处)
// 改为短时上传票(5min/scope=upload/scene=files)——主 JWT 不再进 URL。
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getH5UploadTicket } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')

Page({
  data: {
    src: ''
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127 第二批】冷启动回填也会触发本回调: 页面未初始化时跳过, 避免误踢/双拉。
    if (!this._loadedOnce) return
    if (!hasPermission('file:view')) {
      wx.showToast({ title: '没有操作权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
    }
  },

  onLoad(options) {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
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
      // 【F3b】取短时上传票(主 JWT 走 header, 只把票带给 H5)
      getH5UploadTicket().then((res) => {
        const ticket = res && (res.ticket || (res.data && res.data.ticket))
        if (!ticket) throw new Error('no ticket')
        const q = ['ticket=' + encodeURIComponent(ticket)]
        if (options && options.folderId) q.push('folderId=' + encodeURIComponent(options.folderId))
        if (options && options.scope) q.push('scope=' + encodeURIComponent(options.scope))
        this.setData({ src: globalData.apiBaseUrl + '/mp-file-picker?' + q.join('&') })
      }).catch((err) => {
        showError(err, '上传票获取失败')
        setTimeout(() => wx.navigateBack(), 600)
      })
      this._loadedOnce = true
    })
  },

  // 【PR-12 写后回流】H5 上传完成后本页卸载即强制刷新文件页列表(绕 30s 节流, 机制现成)
  onUnload() {
    getApp().globalData.forceRefresh.files = true
  }
})

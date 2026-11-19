// 【F3a 2026-11-19 spec v2 GAP-B】法布施 H5 上传容器页(web-view):
// 手机本地任意文件(PDF 等)MP 原生不可浏览 → H5 /mp-fabushi-uploader <input type=file> 调起。
// 鉴权红线(MP-TOKEN-001 教训): URL 只带短时上传票(5min/scope=upload/scene=fabushi),
// 主 JWT 绝不进 URL——H5 直传后端票据端点, 回传清单经 bindmessage→globalData→上传页 onShow。
Page({
  data: {
    src: ''
  },

  onLoad(options) {
    const ticket = options && options.ticket
    if (!ticket) {
      wx.showToast({ title: '上传票缺失', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 600)
      return
    }
    this.setData({ src: getApp().globalData.apiBaseUrl + '/mp-fabushi-uploader?ticket=' + encodeURIComponent(ticket) })
  },

  // web-view postMessage 仅在回退/组件销毁时送达——取最后一条含 files 的消息中转给上传页
  onWebViewMessage(e) {
    const list = (e.detail && e.detail.data) || []
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i] && Array.isArray(list[i].files)) {
        getApp().globalData.fabushiUploadFiles = list[i].files
        return
      }
    }
  }
})

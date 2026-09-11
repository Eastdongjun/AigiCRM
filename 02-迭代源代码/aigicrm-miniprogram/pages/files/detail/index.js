const { getToken, requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { replaceFile } = require('../../../api/file')

Page({
  data: { id: '', name: '', type: '', size: '', folder: '', previewing: false, previewVideoPath: '', canReplace: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ id: options.id || '', name: decodeURIComponent(options.name || ''), type: options.type || '', size: decodeURIComponent(options.size || ''), folder: decodeURIComponent(options.folder || ''), canReplace: !getApp().globalData.demoMode && hasPermission('file:edit') })
  },
  preview() {
    if (this.data.canReplace) {
      wx.showActionSheet({ itemList: ['打开并预览', '替换文件'], success: (result) => result.tapIndex === 1 ? this.replace() : this.openPreview() })
      return
    }
    this.openPreview()
  },
  openPreview() {
    if (!this.data.id || this.data.previewing) return
    const app = getApp()
    this.setData({ previewing: true })
    wx.showLoading({ title: '准备预览' })
    wx.downloadFile({ url: (app.globalData.apiBaseUrl || '') + '/api/file/download/' + this.data.id, header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {}, success: (result) => {
      if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
      const suffix = this.data.name.toLowerCase().split('.').pop()
      if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(suffix)) {
        wx.openDocument({ filePath: result.tempFilePath, showMenu: true, fail: (error) => this.showDownloadFallback(error) })
      } else if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(suffix)) {
        wx.previewImage({ urls: [result.tempFilePath], fail: (error) => this.showDownloadFallback(error) })
      } else if (['mp4', 'mov', 'm4v', 'webm'].includes(suffix)) {
        this.setData({ previewVideoPath: result.tempFilePath })
      } else {
        this.showDownloadFallback(new Error('该格式暂不支持预览'))
      }
    }, fail: (error) => showError(error, '文件下载失败'), complete: () => { wx.hideLoading(); this.setData({ previewing: false }) } })
  },
  showDownloadFallback(error) {
    wx.showModal({
      title: '无法直接打开',
      content: '当前格式或设备环境暂不支持直接预览，可使用右上角菜单或稍后下载后查看。',
      showCancel: false,
      success: () => {
        if (error) showError(error, '预览失败，可下载后查看')
      }
    })
  },
  replace() {
    wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (!file) return
      wx.showLoading({ title: '替换中...' })
      replaceFile(this.data.id, file.path).then((result) => {
        const version = result && result.version ? `，版本 ${result.version}` : ''
        wx.showToast({ title: `替换成功${version}`, icon: 'success' })
      }).catch((error) => showError(error, '替换失败')).finally(() => wx.hideLoading())
    } })
  },
  goBack() { wx.navigateBack() }
})

const { getToken, requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { getDownloadUrl, replaceFile } = require('../../../api/file')
const { saveDownloadedFileToDisk, isTextFileName, openTextView } = require('../../../utils/attachment')

function buildDetailView(data) {
  return {
    typeText: data.type || 'FILE',
    nameText: data.name || '未命名文件',
    folderText: data.folder || '-',
    sizeText: data.size || '-',
    uploaderNameText: data.uploaderName || '-',
    updatedAtText: data.updatedAt || '-',
    showVideo: Boolean(data.previewVideoPath)
  }
}

Page({
  data: { id: '', path: '', name: '', type: '', size: '', folder: '', uploaderName: '', updatedAt: '', previewing: false, downloading: false, previewVideoPath: '', canReplace: false, detailView: buildDetailView({}) },
  setDetailData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, detailView: buildDetailView(nextData) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const id = options.id || ''
    this.setDetailData({
      id,
      path: decodeURIComponent(options.path || ''),
      name: decodeURIComponent(options.name || ''),
      type: options.type || '',
      size: decodeURIComponent(options.size || ''),
      folder: decodeURIComponent(options.folder || ''),
      uploaderName: decodeURIComponent(options.uploaderName || ''),
      updatedAt: decodeURIComponent(options.updatedAt || ''),
      canReplace: Boolean(id) && !getApp().globalData.demoMode && hasPermission('file:edit')
    })
  },
  preview() {
    if (this.data.canReplace) {
      wx.showActionSheet({ itemList: ['打开并预览', '替换文件'], success: (result) => result.tapIndex === 1 ? this.replace() : this.openPreview() })
      return
    }
    this.openPreview()
  },
  openPreview() {
    if ((!this.data.id && !this.data.path) || this.data.previewing) return
    const downloadUrl = getDownloadUrl({ id: this.data.id, ossKey: this.data.path })
    if (!downloadUrl) return showError(new Error('缺少下载地址'), '文件下载失败')
    this.setDetailData({ previewing: true })
    // 【下载进度 2026-10-17】百分比写进 loading; done 守卫防迟到回调(同 files 页口径)
    let done = false
    wx.showLoading({ title: '准备预览' })
    const task = wx.downloadFile({ url: downloadUrl, header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {}, success: (result) => {
      if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
      const suffix = this.data.name.toLowerCase().split('.').pop()
      if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(suffix)) {
        wx.openDocument({ filePath: result.tempFilePath, showMenu: true, fail: (error) => this.showDownloadFallback(error) })
      } else if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(suffix)) {
        wx.previewImage({ urls: [result.tempFilePath], fail: (error) => this.showDownloadFallback(error) })
      } else if (['mp4', 'mov', 'm4v', 'webm'].includes(suffix)) {
        this.setDetailData({ previewVideoPath: result.tempFilePath })
      } else if (isTextFileName(this.data.name)) {
        // 【2026-10-17】txt/csv/md/log/json 走文本查看页
        openTextView(result.tempFilePath, this.data.name)
      } else {
        this.showDownloadFallback(new Error('该格式暂不支持预览'))
      }
    }, fail: (error) => showError(error, '文件下载失败'), complete: () => { done = true; wx.hideLoading(); this.setDetailData({ previewing: false }) } })
    if (task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((res) => {
        if (done || !res || !res.progress) return
        wx.showLoading({ title: `加载中 ${Math.min(99, res.progress)}%` })
      })
    }
  },

  // 【2026-10-17】详情页补下载(此前只有预览): 保存到本机, 带进度
  download() {
    if (this.data.downloading) return
    const downloadUrl = getDownloadUrl({ id: this.data.id, ossKey: this.data.path })
    if (!downloadUrl) return showError(new Error('缺少下载地址'), '文件下载失败')
    this.setDetailData({ downloading: true })
    let done = false
    wx.showLoading({ title: '下载中' })
    const task = wx.downloadFile({ url: downloadUrl, header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {}, success: (result) => {
      if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
      saveDownloadedFileToDisk(result.tempFilePath, '保存失败')
    }, fail: (error) => showError(error, '文件下载失败'), complete: () => { done = true; wx.hideLoading(); this.setDetailData({ downloading: false }) } })
    if (task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((res) => {
        if (done || !res || !res.progress) return
        wx.showLoading({ title: `下载中 ${Math.min(99, res.progress)}%` })
      })
    }
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
        const name = file.name || this.data.name
        const type = name.includes('.') ? name.split('.').pop().toUpperCase() : this.data.type
        this.setDetailData({
          name,
          type,
          size: file.size ? `${Math.max(file.size / 1024 / 1024, 0.01).toFixed(2)} MB` : this.data.size,
          updatedAt: '刚刚'
        })
        wx.showToast({ title: `替换成功${version}`, icon: 'success' })
      }).catch((error) => showError(error, '替换失败')).finally(() => wx.hideLoading())
    } })
  },
  goBack() { wx.navigateBack() }
})

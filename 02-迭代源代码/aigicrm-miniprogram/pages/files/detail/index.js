const { getToken, requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { getDownloadUrl, replaceFile } = require('../../../api/file')
const { saveDownloadedFileToDisk, isTextFileName, openTextView } = require('../../../utils/attachment')
const { safeDecodeParam } = require('../../../utils/query')
const { whenAuthReady } = require('../permission-ready')

function buildDetailView(data) {
  return {
    typeText: data.type || 'FILE',
    nameText: data.name || '未命名文件',
    folderText: data.folder || '-',
    sizeText: data.size || '-',
    uploaderNameText: data.uploaderName || '-',
    updatedAtText: data.updatedAt || '-',
    // 【mp-4 2026-10-17】下载按钮按列表传入的文件夹 ACL/downloadable 结果显隐
    showDownloadAction: Boolean(data.canDownload),
    showVideo: Boolean(data.previewVideoPath)
  }
}

Page({
  data: { id: '', path: '', name: '', type: '', size: '', folder: '', uploaderName: '', updatedAt: '', scope: '', previewing: false, downloading: false, previewVideoPath: '', canReplace: false, canDownload: false, detailView: buildDetailView({}) },
  setDetailData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, detailView: buildDetailView(nextData) })
  },
  onLoad(options = {}) {
    if (!requireLogin()) return
    // 【F5】冷启动 permissions 异步回填: 等待在途 /auth/info 后再判权限, 避免误判无权限
    whenAuthReady().then(() => this.initPage(options))
  },
  initPage(options = {}) {
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const id = options.id || ''
    const scope = options.scope || ''
    this.setDetailData({
      id,
      scope,
      // 【mp-0 同类】列表导航统一 encodeURIComponent，这里安全解码一次；
      // 非法 % 转义回退原值，避免文件名/路径含 % 时整页 onLoad 抛错。
      path: safeDecodeParam(options.path || ''),
      name: safeDecodeParam(options.name || ''),
      type: options.type || '',
      size: safeDecodeParam(options.size || ''),
      folder: safeDecodeParam(options.folder || ''),
      uploaderName: safeDecodeParam(options.uploaderName || ''),
      updatedAt: safeDecodeParam(options.updatedAt || ''),
      // 【mp-4】替换入口必须同时满足：列表行已按文件夹 ACL 放行 + 全局 edit 权限 +
      // 非激励佐证 scope。后端 replaceFile 对 scope=incentive 的计分佐证直接拒绝，
      // 列表响应也没有 isEvidence 字段，故这里按 scope 保守隐藏（佐证文件保留查看/下载）。
      canReplace: Boolean(id) && !getApp().globalData.demoMode && hasPermission('file:edit') && options.canReplace === '1' && scope !== 'incentive',
      canDownload: options.canDownload === '1'
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
    if (!this.data.canDownload) return wx.showToast({ title: '该文件夹已关闭下载', icon: 'none' })
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
    if (!this.data.canReplace) return wx.showToast({ title: '没有文件替换权限', icon: 'none' })
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
        // 【F4/PR-12】替换成功置强制刷新标记: files 列表 onShow 命中即绕过节流重拉, 返回立刻见新名/大小
        getApp().globalData.forceRefresh.files = true
        wx.showToast({ title: `替换成功${version}`, icon: 'success' })
      }).catch((error) => showError(error, '替换失败')).finally(() => wx.hideLoading())
    } })
  },
  goBack() { wx.navigateBack() }
})

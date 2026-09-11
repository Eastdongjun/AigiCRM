const { getFileList, getFolders, getSharedToMe, shareFile, uploadFile } = require('../../api/file')
const { getCustomerSimpleUsers } = require('../../api/customer')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function normalizePage(payload) {
  if (Array.isArray(payload)) return payload
  return payload && Array.isArray(payload.records) ? payload.records : []
}

function fileType(name) {
  if (/\.(jpg|jpeg|png|gif|webp)$/i.test(name || '')) return 'image'
  if (/\.(pdf|doc|docx|xls|xlsx|ppt|pptx)$/i.test(name || '')) return 'document'
  if (/\.(mp4|mov|avi|webm)$/i.test(name || '')) return 'video'
  return 'file'
}

function sizeText(size) {
  const n = Number(size || 0)
  if (!n) return '未知大小'
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB'
  if (n >= 1024) return (n / 1024).toFixed(1) + 'KB'
  return n + 'B'
}

function normalizeFile(item) {
  const name = item.fileName || item.originalName || item.name || '未命名文件'
  return {
    id: item.id,
    name,
    type: fileType(name),
    folderName: item.folderName || item.scope || '文件库',
    sizeText: sizeText(item.fileSize || item.size),
    createdAt: String(item.createdAt || item.uploadTime || '').slice(0, 10)
  }
}

Page({
  data: {
    mode: 'all',
    keyword: '',
    files: [],
    folders: [],
    selectedFolderId: null,
    selectedFolderName: '全部文件',
    uploadVisible: false,
    loading: false,
    canUpload: false
    ,canShare: false
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({
      canUpload: getApp().globalData.demoMode || hasPermission('file:upload'),
      canShare: !getApp().globalData.demoMode && hasPermission('file:share'),
    })
    this.refresh()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  search() {
    this.loadFiles()
  },

  refresh() {
    if (this.data.mode === 'shared') this.loadShared()
    else { this.loadFolders(); this.loadFiles() }
  },

  clearFolder() {
    this.setData({ selectedFolderId: null, selectedFolderName: '全部文件' })
    this.loadFiles()
  },

  switchMode(e) {
    const mode = e.currentTarget.dataset.mode
    this.setData({ mode, selectedFolderId: null, selectedFolderName: mode === 'shared' ? '共享给我' : '全部文件' })
    this.refresh()
  },

  loadFolders() {
    if (getApp().globalData.demoMode) return
    getFolders().then((folders) => this.setData({ folders: Array.isArray(folders) ? folders : [] })).catch((error) => showError(error, '文件夹加载失败'))
  },

  selectFolder(e) {
    const folder = this.data.folders[e.currentTarget.dataset.index]
    if (!folder) return
    this.setData({ selectedFolderId: folder.id, selectedFolderName: folder.folderName || folder.name || '文件夹' })
    this.loadFiles()
  },

  loadShared() {
    if (getApp().globalData.demoMode) { this.setData({ files: [] }); return }
    this.setData({ loading: true })
    getSharedToMe({ current: 1, size: 20, keyword: this.data.keyword }).then((payload) => {
      this.setData({ files: normalizePage(payload).map(normalizeFile) })
    }).catch((error) => showError(error, '共享文件加载失败')).finally(() => this.setData({ loading: false }))
  },

  loadFiles() {
    if (getApp().globalData.demoMode) {
      this.setData({
        files: [
          normalizeFile({ id: 1, fileName: '密封技术手册.pdf', folderName: '公共文件', fileSize: 2890000, createdAt: '2026-09-01' }),
          normalizeFile({ id: 2, fileName: '现场照片.jpg', folderName: '客户案例', fileSize: 620000, createdAt: '2026-09-03' })
        ]
      })
      return
    }
    this.setData({ loading: true })
      getFileList({ current: 1, size: 20, folderId: this.data.selectedFolderId, keyword: this.data.keyword }).then((payload) => {
      this.setData({ files: normalizePage(payload).map(normalizeFile) })
    }).catch((error) => {
      showError(error, '文件加载失败')
    }).finally(() => {
      this.setData({ loading: false })
    })
  },

  chooseAndUpload() {
    if (this.data.mode === 'shared' || !this.data.canUpload) {
      wx.showToast({ title: '没有文件上传权限', icon: 'none' })
      return
    }
    wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (!file) return
      wx.showLoading({ title: '上传中...' })
      uploadFile(file.path, this.data.selectedFolderId, this.data.mode).then(() => {
        wx.showToast({ title: '上传成功', icon: 'success' }); this.loadFiles()
      }).catch((error) => showError(error, '上传失败')).finally(() => wx.hideLoading())
    } })
  },

  previewFile(e) {
    const file = this.data.files[e.currentTarget.dataset.index]
    if (!file || !file.id) return
    if (getApp().globalData.demoMode) {
      wx.showToast({ title: '演示文件不可打开', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/files/detail/index?id=${file.id}&name=${encodeURIComponent(file.name)}&type=${encodeURIComponent(file.type)}&size=${encodeURIComponent(file.sizeText)}&folder=${encodeURIComponent(file.folderName || '')}` })
  }
  ,shareFile(e) {
    if (!this.data.canShare) return
    const file = this.data.files[e.currentTarget.dataset.index]
    if (!file || !file.id) return
    getCustomerSimpleUsers().then((users) => {
      const list = Array.isArray(users) ? users.filter((item) => item.id != null) : []
      if (!list.length) { wx.showToast({ title: '暂无可分享用户', icon: 'none' }); return }
      wx.showActionSheet({
        itemList: list.map((item) => item.realName || item.username || `用户${item.id}`),
        success: (result) => shareFile(file.id, [list[result.tapIndex].id]).then(
          () => wx.showToast({ title: '分享成功', icon: 'success' }),
          (error) => showError(error, '分享失败')
        )
      })
    }).catch((error) => showError(error, '用户列表加载失败'))
  }
})

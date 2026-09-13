const { getFolders, getSharedToMe, getFileList, uploadFile, deleteFolder } = require('../../api/file')
const { showError } = require('../../utils/feedback')
const { hasPermission, requireLogin } = require('../../utils/auth')
const { readDemoFolders, removeDemoFolder } = require('../../utils/demo-folders')

const demoFiles = [
  { id: 'file-001', name: '华东智能制造集团项目方案.pdf', type: 'PDF', size: '8.6 MB', folder: '价值服务资料', updatedAt: '今天 10:24', tone: 'red' },
  { id: 'file-002', name: '客户需求调研记录.docx', type: 'DOC', size: '2.4 MB', folder: '客户文档', updatedAt: '昨天 16:40', tone: 'blue' },
  { id: 'file-003', name: 'Q3 价值服务复盘.xlsx', type: 'XLS', size: '1.1 MB', folder: '团队共享', updatedAt: '08-28 09:12', tone: 'green' },
  { id: 'file-004', name: '合作案例图片素材.zip', type: 'ZIP', size: '24.8 MB', folder: '价值服务资料', updatedAt: '08-26 18:05', tone: 'orange' }
]

Page({
  data: { keyword: '', activeTab: '我的文件', activeFolderId: '', activeFolderName: '', tabs: ['我的文件', '共享给我', '价值服务资料'], folders: [], files: [], filteredFiles: [], capacity: { used: '3.8', total: '10', percent: 38 }, canUpload: false, canManageFolder: false, canDevUploadSmoke: false, uploading: false, loaded: false },

  onLoad(options = {}) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    const globalData = getApp().globalData
    const apiBaseUrl = globalData.apiBaseUrl || ''
    // Dev-only smoke guard — runtime environment whitelist (fail-closed):
    // 1. non-demo mode
    // 2. local/intranet API address whitelist
    // 3. runtime envVersion === 'develop' only (via wx.getAccountInfoSync)
    //    trial / release / undefined / API missing → fail-closed (hidden)
    let isDevelopEnv = false
    try {
      const accountInfo = wx.getAccountInfoSync()
      isDevelopEnv = accountInfo && accountInfo.miniProgram && accountInfo.miniProgram.envVersion === 'develop'
    } catch (_) { /* API missing or unavailable → fail-closed */ }
    const isLocalApi = /^https?:\/\/(127\.0\.0\.1|localhost|192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(apiBaseUrl)
    const canDevUploadSmoke = !globalData.demoMode && isLocalApi && isDevelopEnv
    this.setData({ canUpload: globalData.demoMode || hasPermission('file:upload'), canManageFolder: globalData.demoMode || hasPermission('file:folder:manage'), canDevUploadSmoke })
    this.loadFiles()
  },

  onShow() {
    if (this.data.loaded) this.loadFiles()
  },

  async loadFiles() {
    if (getApp().globalData.demoMode) {
      const folders = readDemoFolders()
      const files = demoFiles
      this.setData({ folders, files, filteredFiles: this.filterFiles(files), loaded: true })
      return
    }
    let folders = []
    let files = []
    try {
      const folderResponse = await getFolders()
      folders = folderResponse?.data || folderResponse?.records || []
      const fileListParams = { current: 1, size: 50 }
      if (this.data.activeFolderId) fileListParams.folderId = this.data.activeFolderId
      const fileResponse = this.data.activeTab === '共享给我'
        ? await getSharedToMe({ current: 1, size: 50 })
        : await getFileList(fileListParams)
      files = fileResponse?.data?.records || fileResponse?.records || []
    } catch (error) {
      folders = []
      files = []
      showError(error, '文件加载失败，已显示演示数据')
    }
    const normalizedFiles = files.length ? files.map((item, index) => this.normalizeFile(item, index)) : demoFiles
    this.setData({ folders: folders.map(item => ({ id: item.id, name: item.name || item.folderName || '未命名文件夹' })), files: normalizedFiles, filteredFiles: this.filterFiles(normalizedFiles), loaded: true })
  },

  normalizeFile(item, index) {
    const name = item.name || item.fileName || '未命名文件'
    const extension = name.includes('.') ? name.split('.').pop().toUpperCase() : 'FILE'
    return { id: item.id || `file-${index}`, name, type: extension, size: item.fileSize || item.size || '-', folder: item.folderName || '未分类', updatedAt: item.updateTime || item.updatedAt || '', tone: extension === 'PDF' ? 'red' : extension === 'XLS' ? 'green' : 'blue' }
  },

  filterFiles(files = this.data.files) {
    const keyword = this.data.keyword.trim().toLowerCase()
    return files.filter(item => {
      const matchesTab = this.data.activeTab === '我的文件' || (this.data.activeTab === '价值服务资料' && item.folder === '价值服务资料') || this.data.activeTab === '共享给我'
      return matchesTab && (!keyword || `${item.name}${item.folder}`.toLowerCase().includes(keyword))
    })
  },

  onKeywordInput(event) { const keyword = event.detail.value; this.setData({ keyword, filteredFiles: this.filterFiles(this.data.files) }) },
  onTabTap(event) { const activeTab = event.currentTarget.dataset.tab; this.setData({ activeTab, filteredFiles: this.filterFiles(this.data.files) }) },
  openFolder(event) {
    const rawFolderId = event.currentTarget.dataset.id
    const folderId = rawFolderId && rawFolderId !== 'undefined' && rawFolderId !== 'null' ? rawFolderId : ''
    const folderName = event.currentTarget.dataset.name || ''
    if (getApp().globalData.demoMode) {
      const files = demoFiles.filter((item) => item.folder === folderName)
      return this.setData({ activeFolderId: folderId, activeFolderName: folderName, files, filteredFiles: this.filterFiles(files) })
    }
    this.setData({ activeFolderId: folderId, activeFolderName: folderName })
    getFileList({ current: 1, size: 50, folderId }).then((response) => {
      const records = response?.data?.records || response?.records || []
      const files = records.map((item, index) => this.normalizeFile(item, index))
      this.setData({ files, filteredFiles: this.filterFiles(files) })
    }).catch((error) => showError(error, '文件夹加载失败'))
  },
  clearFolder() { this.loadFiles() },
  openFolderForm() { wx.navigateTo({ url: '/pages/files/folder-form/index' }) },
  openFolderEdit(event) { wx.navigateTo({ url: `/pages/files/folder-form/index?id=${event.currentTarget.dataset.id}&name=${encodeURIComponent(event.currentTarget.dataset.name || '')}` }) },
  removeFolder(event) {
    if (!this.data.canManageFolder) return wx.showToast({ title: '没有文件夹管理权限', icon: 'none' })
    const id = event.currentTarget.dataset.id
    const name = event.currentTarget.dataset.name || '该文件夹'
    if (!id) return
    wx.showModal({ title: '删除文件夹', content: `确认删除“${name}”？请先确认其中没有需要保留的文件。`, success: (result) => {
      if (!result.confirm) return
      if (getApp().globalData.demoMode) {
        removeDemoFolder(id)
        wx.showToast({ title: '已删除', icon: 'success' })
        return this.loadFiles()
      }
      deleteFolder(id).then(() => { wx.showToast({ title: '已删除', icon: 'success' }); this.loadFiles() }).catch((error) => showError(error, '删除失败'))
    } })
  },
  uploadFile() {
    if (!this.data.canUpload) return wx.showToast({ title: '没有文件上传权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示模式暂不上传文件', icon: 'none' })
    if (this.data.uploading) return
    wx.chooseMessageFile({ count: 1, type: 'file', success: (result) => {
      const file = result.tempFiles && result.tempFiles[0]
      if (!file || !file.path) return
      this.setData({ uploading: true })
      wx.showLoading({ title: '上传中' })
      uploadFile(file.path, this.data.activeFolderId || null, 'private').then(() => { wx.showToast({ title: '上传成功', icon: 'success' }); this.data.activeFolderId ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } }) : this.loadFiles() }).catch((error) => showError(error, '上传失败')).finally(() => { this.setData({ uploading: false }); wx.hideLoading() })
    }, fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择文件失败') } })
  },
  runDevUploadSmoke() {
    if (!this.data.canDevUploadSmoke) return wx.showToast({ title: '仅本地开发可用', icon: 'none' })
    if (!this.data.canUpload) return wx.showToast({ title: '没有文件上传权限', icon: 'none' })
    if (this.data.uploading) return
    const fs = wx.getFileSystemManager()
    const filePath = `${wx.env.USER_DATA_PATH}/aigicrm-upload-smoke-${Date.now()}.txt`
    const content = `AIGICRM miniprogram upload smoke ${new Date().toISOString()}`
    this.setData({ uploading: true })
    wx.showLoading({ title: '自测上传中' })
    const cleanupTempFile = () => {
      try { fs.unlink({ filePath, fail: () => {} }) } catch (_) { /* ignore cleanup errors */ }
    }
    try {
      fs.writeFile({
        filePath,
        data: content,
        encoding: 'utf8',
        success: () => {
          uploadFile(filePath, this.data.activeFolderId || null, 'private')
            .then(() => { wx.showToast({ title: '自测上传成功', icon: 'success' }); this.data.activeFolderId ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } }) : this.loadFiles() })
            .catch((error) => showError(error, '自测上传失败'))
            .finally(() => { cleanupTempFile(); this.setData({ uploading: false }); wx.hideLoading() })
        },
        fail: (error) => { this.setData({ uploading: false }); wx.hideLoading(); showError(error, '自测文件创建失败') }
      })
    } catch (error) {
      this.setData({ uploading: false })
      wx.hideLoading()
      showError(error, '自测文件创建异常')
    }
  },
  openFile(event) {
    const item = this.data.filteredFiles[event.currentTarget.dataset.index]
    if (!item || !item.id || String(item.id).startsWith('file-')) return wx.showToast({ title: '演示文件暂不可预览', icon: 'none' })
    wx.navigateTo({ url: `/pages/files/detail/index?id=${item.id}&name=${encodeURIComponent(item.name)}&type=${item.type}&size=${encodeURIComponent(item.size)}&folder=${encodeURIComponent(item.folder)}` })
  },
  switchTab(event) { wx.switchTab({ url: event.currentTarget.dataset.url }) }
})

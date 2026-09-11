const { createFolder, renameFolder } = require('../../../api/file')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { saveDemoFolder } = require('../../../utils/demo-folders')

Page({
  data: { id: '', name: '', saving: false },
  onLoad(options = {}) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:folder:manage')) { wx.showToast({ title: '没有文件夹管理权限', icon: 'none' }); wx.navigateBack(); return }
    this.setData({ id: options.id || '', name: options.name ? decodeURIComponent(options.name) : '' })
  },
  input(e) { this.setData({ name: e.detail.value }) },
  submit() {
    if (!this.data.name.trim() || this.data.saving) return wx.showToast({ title: '请输入文件夹名称', icon: 'none' })
    this.setData({ saving: true })
    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoFolder(this.data.name.trim(), this.data.id))
      : (this.data.id ? renameFolder(this.data.id, this.data.name.trim()) : createFolder({ folderName: this.data.name.trim(), name: this.data.name.trim(), scope: 'private' }))
    action.then(() => { wx.showToast({ title: this.data.id ? '已重命名' : '已创建', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, this.data.id ? '重命名失败' : '创建失败')).finally(() => this.setData({ saving: false }))
  },
  back() { wx.navigateBack() }
})

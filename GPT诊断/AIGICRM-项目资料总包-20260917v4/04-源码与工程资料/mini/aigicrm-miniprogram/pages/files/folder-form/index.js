const { createFolder, renameFolder } = require('../../../api/file')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { saveDemoFolder } = require('../../../utils/demo-folders')

function buildFolderFormView(data) {
  const isEdit = Boolean(data.id)
  return {
    titleText: isEdit ? '重命名文件夹' : '新建文件夹',
    nameValue: data.name || '',
    nameMaxLength: 60,
    namePlaceholder: '请输入文件夹名称',
    submitText: data.saving ? '保存中...' : (isEdit ? '保存名称' : '创建文件夹'),
    submitLoading: Boolean(data.saving),
    successText: isEdit ? '已重命名' : '已创建',
    errorText: isEdit ? '重命名失败' : '创建失败'
  }
}

Page({
  data: {
    id: '',
    name: '',
    saving: false,
    folderFormView: buildFolderFormView({ id: '', name: '', saving: false })
  },
  onLoad(options = {}) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('file:folder:manage')) { wx.showToast({ title: '没有文件夹管理权限', icon: 'none' }); wx.navigateBack(); return }
    this.applyState({ id: options.id || '', name: options.name ? decodeURIComponent(options.name) : '' })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, folderFormView: buildFolderFormView(next) }, callback)
  },
  input(e) { this.applyState({ name: e.detail.value }) },
  submit() {
    if (!this.data.name.trim() || this.data.saving) return wx.showToast({ title: '请输入文件夹名称', icon: 'none' })
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoFolder(this.data.name.trim(), this.data.id))
      : (this.data.id ? renameFolder(this.data.id, this.data.name.trim()) : createFolder({ folderName: this.data.name.trim(), name: this.data.name.trim(), scope: 'private' }))
    action.then(() => { wx.showToast({ title: this.data.folderFormView.successText, icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, this.data.folderFormView.errorText)).finally(() => this.applyState({ saving: false }))
  },
  back() { wx.navigateBack() }
})

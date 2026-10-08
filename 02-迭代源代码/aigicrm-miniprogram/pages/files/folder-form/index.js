const { renameFolder } = require('../../../api/file')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { saveDemoFolder } = require('../../../utils/demo-folders')

function buildFolderFormView(data) {
  // 【D36】本页唯一入口是 files 列表"重命名"(必带 id)；"新建"走 files 首页 showModal 弹窗，
  // 原"新建文件夹"标题与创建分支属旧方案残留，已删除，页面固定为重命名身份
  return {
    titleText: '重命名文件夹',
    nameValue: data.name || '',
    nameMaxLength: 60,
    namePlaceholder: '请输入文件夹名称',
    submitText: data.saving ? '保存中...' : '保存名称',
    submitLoading: Boolean(data.saving),
    successText: '已重命名',
    errorText: '重命名失败'
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
    // 【D36】导航栏标题与页面模式保持一致（json 里为同名兜底）
    wx.setNavigationBarTitle({ title: this.data.folderFormView.titleText })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, folderFormView: buildFolderFormView(next) }, callback)
  },
  input(e) { this.applyState({ name: e.detail.value }) },
  submit() {
    if (!this.data.name.trim() || this.data.saving) return wx.showToast({ title: '请输入文件夹名称', icon: 'none' })
    // 【D36】原创建分支参数口径(scope)与后端 BizFileFolder(folderName/folderType/parentId)不一致且已无入口，删除；缺 id 直接拒绝
    if (!this.data.id) return wx.showToast({ title: '缺少文件夹ID，无法重命名', icon: 'none' })
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoFolder(this.data.name.trim(), this.data.id))
      : renameFolder(this.data.id, this.data.name.trim())
    action.then(() => {
      // 【PR-12 契约修复 2026-10-07】重命名成功置强制刷新标记: files 列表 onShow 命中即绕过 30s 节流一次,
      // 返回立刻见新名(该页无手动刷新入口);节流只拦纯回流,不拦写后回流
      getApp().globalData.forceRefresh.files = true
      wx.showToast({ title: this.data.folderFormView.successText, icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    }).catch((error) => showError(error, this.data.folderFormView.errorText)).finally(() => this.applyState({ saving: false }))
  },
  back() { wx.navigateBack() }
})
